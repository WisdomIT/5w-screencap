package dev.wisdomit.screencap.daemon;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Screen recording: SurfaceFlinger grab -> I420 -> OMX.google.mpeg4.encoder -> MP4.
 *
 * Why this path: the head unit's encoders can't take a Surface (so `screenrecord` fails), the
 * hardware AVC encoder doesn't start, and the software H.264 encoder stops at CIF. The software
 * MPEG-4 encoder works at every size (diag3 on a Niro, tcc897x).
 *
 * Files are cut into segments of SEGMENT_MS, each starting on a key frame: an MP4 is only
 * playable once it's closed, so a power cut costs at most the open segment.
 *
 * With the microphone on (Mic), each segment also gets an AAC track on the same clock. A segment
 * waits for both tracks' formats before it opens (MediaMuxer can't add a track once started);
 * if the microphone fails, recording goes on without sound.
 */
final class Recorder implements Runnable {
    static final long SEGMENT_MS = 2 * 60 * 1000;
    static final String CODEC = "OMX.google.mpeg4.encoder", MIME = "video/mp4v-es";

    interface Listener {
        /** the recording ended; error is null when it was asked to stop */
        void ended(Recorder r, String error);
    }

    final Quality quality;
    final File dir;
    final String base;
    final long startedAt = System.currentTimeMillis();
    final boolean withMic;
    private final Screen screen;
    private final Listener listener;
    private volatile boolean stop;
    private volatile int segments, frames;
    private volatile String currentFile;
    private volatile String micState = "off";
    private Thread thread;

    Recorder(Screen screen, Quality quality, File dir, String base, boolean withMic, Listener listener) {
        this.screen = screen;
        this.quality = quality;
        this.dir = dir;
        this.base = base;
        this.withMic = withMic;
        this.listener = listener;
    }

    void start() {
        thread = new Thread(this, "recorder");
        thread.start();
    }

    /** asks the recording to stop and waits until the last segment is closed (up to timeoutMs) */
    void stop(long timeoutMs) {
        stop = true;
        if (thread != null && thread != Thread.currentThread()) {
            try { thread.join(timeoutMs); } catch (InterruptedException ignored) { }
        }
    }

    int segments() { return segments; }
    int frames() { return frames; }
    String currentFile() { return currentFile; }
    /** "off" (not asked for), "starting", "on", or why it stopped ("mic_unavailable", "mic_error") */
    String micState() { return micState; }

    @Override
    public void run() {
        String error = null;
        MediaCodec codec = null;
        Segments out = new Segments();
        Mic mic = null;
        try {
            codec = MediaCodec.createByCodecName(CODEC);
            MediaFormat fmt = MediaFormat.createVideoFormat(MIME, quality.width, quality.height);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrate);
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE, quality.fps);
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, 19);   // COLOR_FormatYUV420Planar (I420)
            fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            ByteBuffer[] in = codec.getInputBuffers();
            ByteBuffer[] outBufs = codec.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            int w = quality.width, h = quality.height;
            ByteBuffer rgba = ByteBuffer.allocate(w * h * 4);
            byte[] yuv = new byte[w * h * 3 / 2];
            long frameNs = 1000000000L / quality.fps;
            long t0 = System.nanoTime(), next = t0;
            boolean eosSent = false;
            if (withMic) {
                micState = "starting";
                out.expectAudio = true;
                mic = new Mic(t0, out);
                mic.start();
            }

            while (true) {
                if (!stop) {
                    long now = System.nanoTime();
                    if (now < next) {
                        sleepNs(next - now);
                    } else {
                        next = Math.max(next + frameNs, now);
                        Bitmap b = screen.grab(w, h);
                        if (b != null) {
                            rgba.clear();
                            b.copyPixelsToBuffer(rgba);
                            b.recycle();
                            Yuv.rgbaToI420(rgba.array(), yuv, w, h);
                            int idx = codec.dequeueInputBuffer(20000);
                            if (idx >= 0) {
                                in[idx].clear();
                                in[idx].put(yuv);
                                codec.queueInputBuffer(idx, 0, yuv.length, (now - t0) / 1000, 0);
                                frames++;
                            }
                        }
                    }
                } else if (!eosSent) {
                    int idx = codec.dequeueInputBuffer(500000);
                    if (idx >= 0) {
                        codec.queueInputBuffer(idx, 0, 0, (System.nanoTime() - t0) / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        eosSent = true;
                    } else {
                        break;   // the encoder is stuck; close what we have
                    }
                }

                // drain: everything ready now, or until end of stream once it was sent
                long until = System.currentTimeMillis() + 3000;
                boolean ended = false;
                while (true) {
                    int idx = codec.dequeueOutputBuffer(info, eosSent ? 20000 : 0);
                    if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!eosSent || System.currentTimeMillis() > until) break;
                    } else if (idx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outBufs = codec.getOutputBuffers();
                    } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        out.format = codec.getOutputFormat();
                    } else if (idx >= 0) {
                        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            ByteBuffer bb = outBufs[idx];
                            bb.position(info.offset);
                            bb.limit(info.offset + info.size);
                            out.write(bb, info);
                        }
                        codec.releaseOutputBuffer(idx, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) { ended = true; break; }
                    }
                }
                if (ended || (eosSent && System.currentTimeMillis() > until)) break;
            }
        } catch (Throwable t) {
            Sh.log("recorder", t);
            error = t.getMessage() != null && t.getMessage().contains("ENOSPC") ? "usb_full"
                    : !Store.mounted(dir.getParentFile().getParentFile().getPath()) ? "usb_removed" : "encoder";
        } finally {
            if (mic != null) mic.stop(3000);   // its last samples go into the open segment
            if (codec != null) {
                try { codec.stop(); } catch (Throwable ignored) { }
                try { codec.release(); } catch (Throwable ignored) { }
            }
            out.close();
        }
        Sh.log("recording ended: " + segments + " segments, " + frames + " frames" + (error != null ? ", " + error : ""));
        listener.ended(this, error);
    }

    /**
     * One MP4 at a time; a new one starts on the first key frame after SEGMENT_MS. Written from
     * the recorder thread (video) and the Mic thread (sound): every method is synchronized.
     */
    private final class Segments implements Mic.Sink {
        MediaFormat format, audioFormat;
        boolean expectAudio, audioGone;
        MediaMuxer muxer;
        int track, audioTrack = -1;
        long firstPtsUs, openedAt;

        synchronized void write(ByteBuffer b, MediaCodec.BufferInfo info) throws Exception {
            boolean key = (info.flags & 1) != 0;   // BUFFER_FLAG_SYNC_FRAME
            if (muxer == null || (key && System.currentTimeMillis() - openedAt >= SEGMENT_MS)) {
                if (muxer == null && !key) return;   // a segment starts on a key frame
                if (format == null) return;
                if (expectAudio && audioFormat == null && !audioGone) return;   // wait for the sound track's format
                close();
                File f = Names.unique(dir, Names.segment(base, segments + 1), ".mp4");
                muxer = new MediaMuxer(f.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                track = muxer.addTrack(format);
                audioTrack = audioFormat != null && !audioGone ? muxer.addTrack(audioFormat) : -1;
                muxer.start();
                segments++;
                currentFile = f.getName();
                firstPtsUs = info.presentationTimeUs;
                openedAt = System.currentTimeMillis();
                Sh.log("segment " + f.getPath() + (audioTrack >= 0 ? " (with sound)" : ""));
            }
            long pts = info.presentationTimeUs;
            info.presentationTimeUs = pts - firstPtsUs;
            muxer.writeSampleData(track, b, info);
            info.presentationTimeUs = pts;
        }

        @Override
        public synchronized void audioFormat(MediaFormat f) {
            audioFormat = f;
            micState = "on";
        }

        @Override
        public synchronized void audioSample(ByteBuffer b, MediaCodec.BufferInfo info) {
            if (muxer == null || audioTrack < 0) return;
            long pts = info.presentationTimeUs - firstPtsUs;
            if (pts < 0) return;   // from before this segment's first frame
            long orig = info.presentationTimeUs;
            info.presentationTimeUs = pts;
            try {
                muxer.writeSampleData(audioTrack, b, info);
            } catch (Throwable t) {
                Sh.log("audio sample", t);
            } finally {
                info.presentationTimeUs = orig;
            }
        }

        @Override
        public synchronized void audioGone(String why) {
            audioGone = true;
            micState = why;
            Sh.log("recording goes on without sound: " + why);
        }

        synchronized void close() {
            if (muxer == null) return;
            try { muxer.stop(); } catch (Throwable t) { Sh.log("muxer stop", t); }
            try { muxer.release(); } catch (Throwable ignored) { }
            muxer = null;
            audioTrack = -1;
        }
    }

    private static void sleepNs(long ns) {
        try { Thread.sleep(ns / 1000000, (int) (ns % 1000000)); } catch (InterruptedException ignored) { }
    }
}
