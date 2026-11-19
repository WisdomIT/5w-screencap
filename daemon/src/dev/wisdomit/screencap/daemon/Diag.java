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
 *
 * It runs in the background, one stage at a time (STAGES), and status() says where it is, so
 * the Launch screen can show the stages as they go (as pretendard-systemfont's status screen
 * does): the stage running now, each finished stage's result and one line about it.
 */
final class Diag implements Runnable {
    /** shown by the Launch screen in this order */
    static final String[] STAGES = { "버전 정보", "화면 정보", "화면 캡처", "인코더", "녹화 시험", "USB", "로그", "저장" };

    private final Screen screen;
    private final Store store;
    private final String version, settingsJson;
    private final StringBuilder out = new StringBuilder();
    private final long startedAt = System.currentTimeMillis();

    // progress, read by status() from the socket threads
    private volatile String state = "running";   // running, done, error
    private volatile int stage = -1;               // index in STAGES, -1 before the first
    private volatile String detail = "", error, dirName;
    private volatile long endedAt;
    private final String[] result = new String[STAGES.length];   // "ok" / "fail", null while not run
    private final String[] note = new String[STAGES.length];     // one line about the stage
    private boolean failed;                                       // the stage running now

    Diag(Screen screen, Store store, String version, String settingsJson) {
        this.screen = screen;
        this.store = store;
        this.version = version;
        this.settingsJson = settingsJson;
    }

    void start() {
        new Thread(this, "diag").start();
    }

    boolean running() {
        return "running".equals(state);
    }

    synchronized JSONObject status() throws Exception {
        JSONObject j = Main.ok().put("state", state).put("stage", stage).put("total", STAGES.length)
                .put("detail", detail)
                .put("elapsed", ((endedAt > 0 ? endedAt : System.currentTimeMillis()) - startedAt) / 1000)
                .put("started", startedAt);
        org.json.JSONArray st = new org.json.JSONArray();
        for (int i = 0; i < STAGES.length; i++) {
            st.put(new JSONObject().put("name", STAGES[i])
                    .put("result", result[i] == null ? JSONObject.NULL : result[i])
                    .put("note", note[i] == null ? "" : note[i]));
        }
        j.put("stages", st);
        if (error != null) j.put("error", error);
        if (dirName != null) j.put("dir", "diag/" + dirName);
        return j;
    }

    @Override
    public void run() {
        try {
            File base = store.dir("diag");
            if (base == null) { finish("error", "no_usb"); return; }
            File dir = Names.unique(base, store.nextName(), "");
            if (!dir.mkdirs()) { finish("error", "write_failed"); return; }
            dirName = dir.getName();

            begin(0, "펌웨어와 HMG-API·NEMESIS 버전");
            section("versions");
            line("5w-screencap", version);
            line("firmware", Sh.prop("ro.build.product2"));
            line("platform", Sh.prop("ro.board.platform"));
            line("android", Sh.prop("ro.build.version.release"));
            String hmgapi = first(Sh.run(5000, "/system/bin/hmgapid", "version"));
            line("hmgapi", hmgapi);
            line("nemesis", first(Sh.run(5000, "/system/xbin/nemesis", "version")));
            section("settings");
            out.append(settingsJson).append('\n');
            end(0, hmgapi);

            begin(1, "화면 크기와 플로팅 버튼 레이어");
            section("display");
            String size = first(Sh.run(5000, "/system/bin/wm", "size"));
            line("wm size", size);
            int layer = screen.overlayLayer();
            line("overlay layer", layer > 0 ? String.valueOf(layer) : "none (floating button off or not found)");
            end(1, size.replace("Physical size: ", "") + (layer > 0 ? " · 버튼 레이어 " + layer : " · 플로팅 버튼 없음"));

            begin(2, "1920×720, 1024×384로 찍어 보는 중");
            section("capture");
            String cap = "";
            for (int[] sz : new int[][] { { 1920, 720 }, { 1024, 384 } }) {
                long t0 = System.nanoTime();
                Bitmap b = screen.grab(sz[0], sz[1]);
                long ms = (System.nanoTime() - t0) / 1000000;
                line(sz[0] + "x" + sz[1], b == null ? "FAIL (null)" : "ok " + ms + " ms" + (screen.excludesOverlay() ? ", overlay excluded" : ""));
                if (b == null) failed = true;
                else b.recycle();
                if (cap.isEmpty()) cap = b == null ? "화면을 가져오지 못함" : ms + "ms" + (screen.excludesOverlay() ? " · 버튼 제외" : "");
            }
            end(2, cap);

            begin(3, "2초 동안 인코딩해 보는 중");
            section("encoder");
            String enc = encoderTest();
            line(Recorder.CODEC, enc);
            if (enc.startsWith("FAIL")) failed = true;
            end(3, enc.startsWith("ok ") ? enc.substring(enc.indexOf('(') + 1, enc.indexOf(')')) : "실패");

            begin(4, "5초 녹화, 1.5초마다 파일을 나눠 모두 닫히는지 확인하는 중");
            section("recording self-test (every file must close with an index: moov)");
            String rec = recordTest(1500, 5000);
            line("1.5 s segments", rec);
            if (rec.startsWith("FAIL")) failed = true;
            end(4, rec.substring(rec.indexOf(' ') + 1, rec.indexOf(',')).replace(" files closed", " 파일 닫힘"));

            begin(5, "USB 마운트와 남은 공간");
            section("usb");
            File root = store.root();
            line("folder", String.valueOf(root));
            long freeMb = root == null ? -1 : Store.free(root) / (1024 * 1024);
            line("free", freeMb < 0 ? "-" : freeMb + " MB");
            String mounts = Sh.run(3000, "/system/bin/cat", "/proc/mounts");
            if (mounts != null) for (String l : mounts.split("\n")) if (l.contains("usb")) out.append(l).append('\n');
            if (root == null) failed = true;
            end(5, root == null ? "USB 폴더 없음" : String.format("%.1fGB 남음", freeMb / 1024.0));

            begin(6, "서비스 로그와 미디어 기록기 로그");
            section("service log (last 80 lines)");
            tail(Sh.run(3000, "/system/bin/cat", "/data/hmgapi/logs/5w-screencap/screencapd.log"), 80);
            section("media writer messages (logcat)");
            tail(Sh.run(10000, "/system/bin/logcat", "-d", "-v", "time", "-s",
                    "MPEG4Writer:*", "MediaMuxer:*", "MediaAdapter:*", "SoftMPEG4Encoder:*"), 80);
            end(6, "");

            begin(7, "SUMMARY.txt 저장");
            File f = new File(dir, "SUMMARY.txt");
            Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            try { w.write(out.toString()); } finally { w.close(); }
            Sh.log("diag " + f.getPath());
            end(7, "5W-Screencap/diag/" + dirName);
            finish("done", null);
        } catch (Throwable t) {
            Sh.log("diag", t);
            if (stage >= 0 && result[stage] == null) { result[stage] = "fail"; note[stage] = String.valueOf(t.getMessage()); }
            finish("error", Store.mounted("/storage/usb0") ? "failed" : "usb_removed");
        }
    }

    private synchronized void begin(int i, String what) {
        stage = i;
        detail = what;
        failed = false;
    }

    private synchronized void end(int i, String line) {
        result[i] = failed ? "fail" : "ok";
        note[i] = line;
    }

    private synchronized void finish(String st, String err) {
        error = err;
        endedAt = System.currentTimeMillis();
        detail = "";
        state = st;
    }

    private void tail(String text, int n) {
        if (text == null) return;
        String[] lines = text.split("\n");
        for (int i = Math.max(0, lines.length - n); i < lines.length; i++) out.append(lines[i]).append('\n');
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
