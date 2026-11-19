package fivew.diag;

import android.media.AudioFormat;
import android.media.AudioRecord;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;

/**
 * Fourth-stage probe: can the head unit's sound be recorded? Run as root under app_process
 * (root passes AudioFlinger's RECORD_AUDIO and CAPTURE_AUDIO_OUTPUT checks):
 *   CLASSPATH=5w_diag4.jar app_process /system/bin fivew.diag.AudioProbe OUT_DIR [SECONDS_EACH]
 *
 * For each AudioRecord source, at 48000 and 44100 Hz, stereo then mono: open it, record a few
 * seconds while music plays, and report whether anything but silence came in (RMS, peak, the
 * share of non-zero samples). Each recording is kept as a WAV to listen to on a PC.
 *
 * Before each source the car shows a numbered notice ("소리 진단 3/9: REMOTE_SUBMIX") so the
 * owner can tell which test cut the speakers off, if one does: REMOTE_SUBMIX on KitKat may send
 * the music to the recording instead of the speakers while it runs.
 */
public final class AudioProbe {
    /** MediaRecorder.AudioSource values, gentlest first, REMOTE_SUBMIX last (it may take the speakers) */
    static final int[] SOURCES = { 0, 1, 5, 6, 7, 3, 2, 4, 8 };
    /** names by AudioSource value */
    static final String[] NAMES = { "DEFAULT", "MIC", "VOICE_UPLINK", "VOICE_DOWNLINK", "VOICE_CALL",
            "CAMCORDER", "VOICE_RECOGNITION", "VOICE_COMMUNICATION", "REMOTE_SUBMIX" };

    private static PrintWriter rep;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        rep = new PrintWriter(new FileWriter(new File(out, "audio.txt")));
        log("# audio probe, " + seconds + " s per source; play music (Bluetooth or CarPlay) during the test");
        log("source               rate  ch  state  read_bytes  rms_dbfs  peak   nonzero  file");

        int n = 0;
        for (int src : SOURCES) {
            n++;
            String name = NAMES[src];
            toast("소리 진단 " + n + "/" + SOURCES.length + ": " + name);
            sleep(1500);   // the notice shows before the test, so a cut in the music lines up with it
            boolean done = false;
            for (int rate : new int[] { 48000, 44100 }) {
                for (int ch : new int[] { 2, 1 }) {
                    if (done) break;
                    done = tryOne(out, src, name, rate, ch, seconds);
                }
            }
            if (!done) log(String.format("%-20s  -     -   FAIL   no rate/channel combination opened", name));
        }
        toast("소리 진단 끝");
        log("done");
        rep.close();
        System.exit(0);
    }

    /** true when the source opened at this rate/channel count (whatever it recorded) */
    static boolean tryOne(File out, int src, String name, int rate, int ch, int seconds) {
        int cfg = ch == 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        int min = AudioRecord.getMinBufferSize(rate, cfg, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return false;
        AudioRecord r = null;
        try {
            r = new AudioRecord(src, rate, cfg, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 4, rate * ch * 2 / 2));
            if (r.getState() != AudioRecord.STATE_INITIALIZED) {
                r.release();
                return false;
            }
            r.startRecording();
            if (r.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                log(String.format("%-20s %5d  %d   NOREC", name, rate, ch));
                return true;
            }
            int total = rate * ch * 2 * seconds;
            byte[] pcm = new byte[total];
            int got = 0;
            long until = System.currentTimeMillis() + seconds * 1000L + 2000;
            while (got < total && System.currentTimeMillis() < until) {
                int k = r.read(pcm, got, Math.min(8192, total - got));
                if (k < 0) {
                    log(String.format("%-20s %5d  %d   READ_ERROR %d", name, rate, ch, k));
                    break;
                }
                got += k;
            }
            r.stop();
            double sum = 0;
            int peak = 0, nonzero = 0, samples = got / 2;
            for (int i = 0; i + 1 < got; i += 2) {
                int v = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
                sum += (double) v * v;
                if (Math.abs(v) > peak) peak = Math.abs(v);
                if (v != 0) nonzero++;
            }
            double rms = samples == 0 ? 0 : Math.sqrt(sum / samples);
            String db = rms > 0 ? String.format("%.1f", 20 * Math.log10(rms / 32768.0)) : "-inf";
            String file = "audio_" + name + "_" + rate + "_" + ch + "ch.wav";
            wav(new File(out, file), pcm, got, rate, ch);
            log(String.format("%-20s %5d  %d   ok     %9d  %8s  %5d  %5.1f%%  %s", name, rate, ch, got, db, peak,
                    samples == 0 ? 0 : 100.0 * nonzero / samples, file));
            return true;
        } catch (Throwable t) {
            log(String.format("%-20s %5d  %d   ERROR  %s", name, rate, ch, t));
            return false;
        } finally {
            if (r != null) try { r.release(); } catch (Throwable ignored) { }
        }
    }

    static void wav(File f, byte[] pcm, int len, int rate, int ch) throws Exception {
        RandomAccessFile w = new RandomAccessFile(f, "rw");
        try {
            w.setLength(0);
            byte[] h = new byte[44];
            put(h, 0, "RIFF"); le32(h, 4, 36 + len); put(h, 8, "WAVE"); put(h, 12, "fmt ");
            le32(h, 16, 16); le16(h, 20, 1); le16(h, 22, ch); le32(h, 24, rate);
            le32(h, 28, rate * ch * 2); le16(h, 32, ch * 2); le16(h, 34, 16);
            put(h, 36, "data"); le32(h, 40, len);
            w.write(h);
            w.write(pcm, 0, len);
        } finally {
            w.close();
        }
    }

    static void put(byte[] a, int o, String s) { for (int i = 0; i < 4; i++) a[o + i] = (byte) s.charAt(i); }
    static void le16(byte[] a, int o, int v) { a[o] = (byte) v; a[o + 1] = (byte) (v >> 8); }
    static void le32(byte[] a, int o, int v) { a[o] = (byte) v; a[o + 1] = (byte) (v >> 8); a[o + 2] = (byte) (v >> 16); a[o + 3] = (byte) (v >> 24); }

    static void toast(String text) {
        try {
            new ProcessBuilder("/system/bin/am", "broadcast", "-a", "com.hkmc.intent.action.TOAST",
                    "-e", "com.hkmc.intent.extra.TOAST_MESSAGE", text,
                    "--ez", "com.hkmc.intent.extra.TOAST_POPUPSHOWING", "true").redirectErrorStream(true).start().waitFor();
        } catch (Exception ignored) { }
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    static void log(String s) {
        rep.println(s);
        rep.flush();
        System.out.println(s);
    }
}
