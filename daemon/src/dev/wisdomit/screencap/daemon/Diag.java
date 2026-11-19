package dev.wisdomit.screencap.daemon;

import android.graphics.Bitmap;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * The Launch screen's "진단 실행": a short SUMMARY.txt for an issue report, in
 * 5W-Screencap/diag/<name>/ on the stick. No serial numbers, addresses, file names
 * or screen images: only versions, sizes, timings and pass/fail.
 */
final class Diag {
    private final Screen screen;
    private final Store store;
    private final String version;
    private final StringBuilder out = new StringBuilder();

    Diag(Screen screen, Store store, String version) {
        this.screen = screen;
        this.store = store;
        this.version = version;
    }

    JSONObject run(String settingsJson) throws Exception {
        File base = store.dir("diag");
        if (base == null) return Main.fail("no_usb");
        File dir = Names.unique(base, store.nextName(), "");
        if (!dir.mkdirs()) return Main.fail("write_failed");

        section("versions");
        line("5w-screencap", version);
        line("firmware", Sh.prop("ro.build.product2"));
        line("platform", Sh.prop("ro.board.platform"));
        line("android", Sh.prop("ro.build.version.release"));
        line("hmgapi", first(Sh.run(5000, "/system/bin/hmgapid", "version")));
        line("nemesis", first(Sh.run(5000, "/system/xbin/nemesis", "version")));

        section("settings");
        out.append(settingsJson).append('\n');

        section("display");
        line("wm size", first(Sh.run(5000, "/system/bin/wm", "size")));
        int layer = screen.overlayLayer();
        line("overlay layer", layer > 0 ? String.valueOf(layer) : "none (floating button off or not found)");

        section("capture");
        for (int[] s : new int[][] { { 1920, 720 }, { 1024, 384 } }) {
            long t0 = System.nanoTime();
            Bitmap b = screen.grab(s[0], s[1]);
            long ms = (System.nanoTime() - t0) / 1000000;
            line(s[0] + "x" + s[1], b == null ? "FAIL (null)" : "ok " + ms + " ms" + (screen.excludesOverlay() ? ", overlay excluded" : ""));
            if (b != null) b.recycle();
        }

        section("encoder");
        line(Recorder.CODEC, encoderTest());

        section("recording self-test (every file must close with an index: moov)");
        line("1.5 s segments", recordTest(1500, 5000));

        section("usb");
        line("folder", String.valueOf(store.root()));
        File root = store.root();
        line("free", root == null ? "-" : (Store.free(root) / (1024 * 1024)) + " MB");
        String mounts = Sh.run(3000, "/system/bin/cat", "/proc/mounts");
        if (mounts != null) for (String l : mounts.split("\n")) if (l.contains("usb")) out.append(l).append('\n');

        section("service log (last 80 lines)");
        String log = Sh.run(3000, "/system/bin/cat", "/data/hmgapi/logs/5w-screencap/screencapd.log");
        if (log != null) {
            String[] lines = log.split("\n");
            for (int i = Math.max(0, lines.length - 80); i < lines.length; i++) out.append(lines[i]).append('\n');
        }

        section("media writer messages (logcat)");
        String lc = Sh.run(10000, "/system/bin/logcat", "-d", "-v", "time", "-s",
                "MPEG4Writer:*", "MediaMuxer:*", "MediaAdapter:*", "SoftMPEG4Encoder:*");
        if (lc != null) {
            String[] lines = lc.split("\n");
            for (int i = Math.max(0, lines.length - 80); i < lines.length; i++) out.append(lines[i]).append('\n');
        }

        File f = new File(dir, "SUMMARY.txt");
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try { w.write(out.toString()); } finally { w.close(); }
        Sh.log("diag " + f.getPath());
        return Main.ok().put("dir", "diag/" + dir.getName()).put("summary", out.toString());
    }

    /** 2 s of the standard preset into a throwaway file: frames in, and the fps it reached */
    private String encoderTest() {
        File tmp = new File(store.dir("diag"), ".enctest");
        tmp.mkdirs();
        final String[] err = { null };
        Recorder r = new Recorder(screen, Quality.STANDARD, tmp, "enctest", new Recorder.Listener() {
            @Override public void ended(Recorder rec, String error) { err[0] = error; }
        });
        r.start();
        try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
        r.stop(5000);
        int frames = r.frames();
        File[] files = tmp.listFiles();
        long bytes = 0;
        if (files != null) for (File x : files) { bytes += x.length(); x.delete(); }
        tmp.delete();
        if (err[0] != null) return "FAIL " + err[0];
        return (frames > 0 && bytes > 0 ? "ok " : "FAIL ") + frames + " frames in 2 s (" + (frames / 2.0) + " fps), " + bytes + " bytes";
    }

    /**
     * A short recording into a throwaway folder; then each MP4 is checked for its index (moov),
     * which KitKat's MPEG4Writer leaves out when it refuses a track. The files are deleted.
     */
    private String recordTest(long segmentMs, long ms) {
        File tmp = new File(store.dir("diag"), ".rectest");
        tmp.mkdirs();
        final String[] err = { null };
        Recorder r = new Recorder(screen, Quality.STANDARD, tmp, "rectest", segmentMs, new Recorder.Listener() {
            @Override public void ended(Recorder rec, String error) { err[0] = error; }
        });
        r.start();
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
        r.stop(8000);
        File[] files = tmp.listFiles();
        StringBuilder sb = new StringBuilder();
        int good = 0, all = 0;
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File x : files) {
                if (!x.getName().endsWith(".mp4")) { x.delete(); continue; }
                all++;
                boolean moov = hasMoov(x);
                if (moov) good++;
                sb.append(' ').append(x.getName().replace("rectest_", "#")).append(moov ? " ok" : " NO-MOOV").append(',');
                x.delete();
            }
        }
        tmp.delete();
        return (all > 0 && good == all && err[0] == null ? "ok " : "FAIL ") + good + "/" + all + " files closed,"
                + sb + " frames " + r.frames() + (err[0] != null ? ", error " + err[0] : "");
    }

    /** whether the MP4 has a top-level moov box (walks the box headers) */
    static boolean hasMoov(File f) {
        try {
            java.io.RandomAccessFile in = new java.io.RandomAccessFile(f, "r");
            try {
                long pos = 0, len = in.length();
                byte[] h = new byte[16];
                while (pos + 8 <= len) {
                    in.seek(pos);
                    in.readFully(h, 0, 8);
                    long size = ((h[0] & 0xffL) << 24) | ((h[1] & 0xff) << 16) | ((h[2] & 0xff) << 8) | (h[3] & 0xff);
                    String type = new String(h, 4, 4, "ISO-8859-1");
                    if (size == 1) { in.readFully(h, 8, 8); size = java.nio.ByteBuffer.wrap(h, 8, 8).getLong(); }
                    if ("moov".equals(type)) return true;
                    if (size < 8) return false;
                    pos += size;
                }
            } finally {
                in.close();
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void section(String s) { out.append("\n===== ").append(s).append('\n'); }

    private void line(String k, String v) { out.append(k).append(": ").append(v).append('\n'); }

    private static String first(String s) {
        if (s == null) return "-";
        String t = s.trim();
        int nl = t.indexOf('\n');
        return nl < 0 ? t : t.substring(0, nl);
    }
}
