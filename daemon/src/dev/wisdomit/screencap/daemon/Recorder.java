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
    final long segmentMs;
    private final Screen screen;
    private final Listener listener;
    private volatile boolean stop;
    private volatile int segments, frames;
    private volatile String currentFile;
    private volatile String micState = "off";
    private Thread thread;

    Recorder(Screen screen, Quality quality, File dir, String base, boolean withMic, Listener listener) {
        this(screen, quality, dir, base, withMic, SEGMENT_MS, listener);
    }

    Recorder(Screen screen, Quality quality, File dir, String base, boolean withMic, long segmentMs, Listener listener) {
        this.segmentMs = segmentMs;
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
                        out.videoFormat(codec.getOutputFormat());
                    } else if (idx >= 0) {
                        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            out.videoCsd(outBufs[idx], info);   // the VOL header: our own copy for every segment
                        } else if (info.size > 0) {
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
     * One MP4 at a time; a new one starts on the first key frame after segmentMs. Written from
     * the recorder thread (video) and the Mic thread (sound): every method is synchronized.
     *
     * Each new MP4 gets the codecs' configuration (MPEG-4 VOL header, AAC config) from our own
     * copies, put into the track formats as a fresh csd-0 just before addTrack. Taken as it came
     * from the encoder a second or more earlier, the MPEG-4 track's csd-0 didn't reach the writer
     * on a Niro (MPEG4Writer "Missing codec specific data": no moov, an unplayable file); this
     * happened when the first MP4 had to wait for the sound track's format.
     */
    private final class Segments implements Mic.Sink {
        MediaFormat format, audioFormat;
        byte[] videoCsd, audioCsd;
        boolean expectAudio, audioGone;
        MediaMuxer muxer;
        int track, audioTrack = -1;
        long firstPtsUs, openedAt, lastVideoUs = -1, lastAudioUs = -1;

        synchronized void write(ByteBuffer b, MediaCodec.BufferInfo info) throws Exception {
            boolean key = (info.flags & 1) != 0;   // BUFFER_FLAG_SYNC_FRAME
            if (muxer == null || (key && System.currentTimeMillis() - openedAt >= segmentMs)) {
                if (muxer == null && !key) return;   // a segment starts on a key frame
                if (format == null) return;
                if (expectAudio && audioFormat == null && !audioGone) return;   // wait for the sound track's format
                close();
                File f = Names.unique(dir, Names.segment(base, segments + 1), ".mp4");
                muxer = new MediaMuxer(f.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                if (videoCsd != null) format.setByteBuffer("csd-0", ByteBuffer.wrap(videoCsd.clone()));
                track = muxer.addTrack(format);
                boolean withAudio = audioFormat != null && !audioGone;
                if (withAudio && audioCsd != null) audioFormat.setByteBuffer("csd-0", ByteBuffer.wrap(audioCsd.clone()));
                audioTrack = withAudio ? muxer.addTrack(audioFormat) : -1;
                muxer.start();
                segments++;
                currentFile = f.getName();
                firstPtsUs = info.presentationTimeUs;
                openedAt = System.currentTimeMillis();
                lastVideoUs = -1;
                lastAudioUs = -1;
                Sh.log("segment " + f.getPath() + (audioTrack >= 0 ? " (with sound)" : ""));
            }
            long pts = info.presentationTimeUs;
            long rel = pts - firstPtsUs;
            if (rel <= lastVideoUs) return;   // MPEG4Writer drops the file's index if a track's times go back
            lastVideoUs = rel;
            info.presentationTimeUs = rel;
            muxer.writeSampleData(track, b, info);
            info.presentationTimeUs = pts;
        }

        synchronized void videoFormat(MediaFormat f) {
            format = f;
            if (videoCsd == null) videoCsd = csd(f);
        }

        synchronized void videoCsd(ByteBuffer b, MediaCodec.BufferInfo info) {
            videoCsd = bytes(b, info);
        }

        @Override
        public synchronized void audioFormat(MediaFormat f) {
            audioFormat = f;
            if (audioCsd == null) audioCsd = csd(f);
            micState = "on";
        }

        @Override
        public synchronized void audioCsd(ByteBuffer b, MediaCodec.BufferInfo info) {
            audioCsd = bytes(b, info);
        }

        @Override
        public synchronized void audioSample(ByteBuffer b, MediaCodec.BufferInfo info) {
            if (muxer == null || audioTrack < 0) return;
            long pts = info.presentationTimeUs - firstPtsUs;
            if (pts < 0) return;   // from before this segment's first frame
            if (pts <= lastAudioUs) return;   // times must move forward (see Mic)
            lastAudioUs = pts;
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
            try {
                muxer.stop();
            } catch (Throwable t) {
                // the file has no index and won't play (MPEG4Writer refused: see logcat MPEG4Writer)
                Sh.log("muxer stop FAILED for " + currentFile + " (audio track " + audioTrack + ", last video "
                        + lastVideoUs + " us, last audio " + lastAudioUs + " us)", t);
                Sh.toast("녹화 파일을 마무리하지 못했습니다: " + currentFile + " (진단을 실행해 신고해 주세요)");
            }
            try { muxer.release(); } catch (Throwable ignored) { }
            muxer = null;
            audioTrack = -1;
        }
    }

    /** a copy of the format's csd-0, read from its start without moving the format's own buffer */
    static byte[] csd(MediaFormat f) {
        try {
            ByteBuffer b = f.getByteBuffer("csd-0");
            if (b == null) return null;
            ByteBuffer d = b.duplicate();
            d.position(0);
            byte[] out = new byte[d.remaining()];
            d.get(out);
            return out.length > 0 ? out : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** a copy of an output buffer's bytes */
    static byte[] bytes(ByteBuffer b, MediaCodec.BufferInfo info) {
        ByteBuffer d = b.duplicate();
        d.position(info.offset);
        d.limit(info.offset + info.size);
        byte[] out = new byte[info.size];
        d.get(out);
        return out;
    }

    private static void sleepNs(long ns) {
        try { Thread.sleep(ns / 1000000, (int) (ns % 1000000)); } catch (InterruptedException ignored) { }
    }
}
