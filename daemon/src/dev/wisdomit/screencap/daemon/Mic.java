package dev.wisdomit.screencap.daemon;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

/**
 * The cabin microphone into AAC, for a recording's sound track (the "마이크 소리 포함" option).
 *
 * Why the microphone: diag4 on a Niro found no way to record the car's own output (no r_submix
 * module; the call sources open only in a call). The microphone is the one input that opens:
 * mono, about 8 kHz of band, so it's recorded at 16 kHz mono, AAC-LC 48 kbit/s. It also hears
 * people talking, so the option is off unless the owner turns it on.
 *
 * Runs on its own thread; samples go to the recorder's MP4 with presentation times on the
 * recorder's clock (t0), so sound and picture line up. If the microphone or the encoder fails,
 * the recording carries on without sound.
 *
 * Output times are counted here, one AAC frame (1024 samples) apart from the first input's time,
 * not taken from the encoder: KitKat's software AAC encoder stamps every frame cut from one input
 * buffer with that buffer's time, and KitKat's MPEG4Writer drops the whole file's index (no moov:
 * an unplayable file) when a track's times don't move forward.
 */
final class Mic implements Runnable {
    static final String CODEC = "OMX.google.aac.encoder", MIME = "audio/mp4a-latm";
    static final int SOURCE_MIC = 1, AAC_LC = 2, BITRATE = 48000;
    /** samples in one AAC-LC frame */
    static final int AAC_FRAME = 1024;
    static final int[] RATES = { 16000, 44100, 48000 };

    interface Sink {
        void audioFormat(MediaFormat f);
        /** the encoder's configuration buffer (AudioSpecificConfig) */
        void audioCsd(ByteBuffer b, MediaCodec.BufferInfo info);
        void audioSample(ByteBuffer b, MediaCodec.BufferInfo info);
        /** no sound from here on (the microphone or encoder failed) */
        void audioGone(String why);
    }

    private final long t0;
    private final Sink sink;
    private volatile boolean stop;
    private Thread thread;
    volatile int rate;

    Mic(long t0Nanos, Sink sink) {
        this.t0 = t0Nanos;
        this.sink = sink;
    }

    void start() {
        thread = new Thread(this, "mic");
        thread.start();
    }

    /** stops recording and waits until the encoder's last samples are written */
    void stop(long timeoutMs) {
        stop = true;
        if (thread != null) try { thread.join(timeoutMs); } catch (InterruptedException ignored) { }
    }

    @Override
    public void run() {
        AudioRecord rec = null;
        MediaCodec aac = null;
        try {
            rec = open();
            if (rec == null) { sink.audioGone("mic_unavailable"); return; }
            aac = MediaCodec.createByCodecName(CODEC);
            MediaFormat f = MediaFormat.createAudioFormat(MIME, rate, 1);
            f.setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_LC);
            f.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
            aac.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            aac.start();
            ByteBuffer[] in = aac.getInputBuffers();
            ByteBuffer[] out = aac.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            rec.startRecording();

            byte[] pcm = new byte[4096];
            long frames = 0, baseUs = -1, aacFrames = 0;
            boolean eosSent = false;
            while (true) {
                if (!eosSent) {
                    int idx = aac.dequeueInputBuffer(10000);
                    if (idx >= 0) {
                        if (stop) {
                            aac.queueInputBuffer(idx, 0, 0, baseUs + frames * 1000000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            eosSent = true;
                        } else {
                            int want = Math.min(pcm.length, in[idx].capacity());
                            int n = rec.read(pcm, 0, want);
                            if (n < 0) throw new IllegalStateException("AudioRecord.read " + n);
                            // the first samples were taken just before now: put them on the recorder's clock
                            if (baseUs < 0) baseUs = (System.nanoTime() - t0) / 1000 - (n / 2) * 1000000L / rate;
                            in[idx].clear();
                            in[idx].put(pcm, 0, n);
                            aac.queueInputBuffer(idx, 0, n, baseUs + frames * 1000000L / rate, 0);
                            frames += n / 2;
                        }
                    }
                }
                long until = System.currentTimeMillis() + 2000;
                boolean ended = false;
                while (true) {
                    int idx = aac.dequeueOutputBuffer(info, eosSent ? 10000 : 0);
                    if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!eosSent || System.currentTimeMillis() > until) break;
                    } else if (idx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        out = aac.getOutputBuffers();
                    } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        sink.audioFormat(aac.getOutputFormat());
                    } else if (idx >= 0) {
                        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            sink.audioCsd(out[idx], info);
                        } else if (info.size > 0 && baseUs >= 0) {
                            ByteBuffer b = out[idx];
                            b.position(info.offset);
                            b.limit(info.offset + info.size);
                            info.presentationTimeUs = baseUs + aacFrames * AAC_FRAME * 1000000L / rate;
                            aacFrames++;
                            sink.audioSample(b, info);
                        }
                        aac.releaseOutputBuffer(idx, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) { ended = true; break; }
                    }
                }
                if (ended || (eosSent && System.currentTimeMillis() > until)) break;
            }
        } catch (Throwable t) {
            Sh.log("mic", t);
            sink.audioGone("mic_error");
        } finally {
            if (rec != null) {
                try { rec.stop(); } catch (Throwable ignored) { }
                rec.release();
            }
            if (aac != null) {
                try { aac.stop(); } catch (Throwable ignored) { }
                try { aac.release(); } catch (Throwable ignored) { }
            }
        }
    }

    /** the microphone at the lowest rate it opens at (it carries only ~8 kHz of band), or null */
    private AudioRecord open() {
        for (int r : RATES) {
            int min = AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) continue;
            AudioRecord a = new AudioRecord(SOURCE_MIC, r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 4, r));
            if (a.getState() == AudioRecord.STATE_INITIALIZED) {
                rate = r;
                Sh.log("mic open at " + r + " Hz");
                return a;
            }
            a.release();
        }
        return null;
    }
}
