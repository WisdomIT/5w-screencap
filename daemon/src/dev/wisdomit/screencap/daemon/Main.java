package dev.wisdomit.screencap.daemon;

import android.graphics.Bitmap;
import android.net.LocalServerSocket;
import android.net.LocalSocket;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * screencapd: the mod's root service (mod.json "services", run by HMG-API under app_process).
 *
 * It owns everything that needs root: grabbing the screen, the encoder, writing to the USB
 * stick. The app (buttons, sounds, floating button, Launch screen) asks it over a local
 * socket, one line per request, one JSON line back:
 *
 *   ping                    -> {"ok":true,"version":"0.1.0"}
 *   status                  -> recording state, USB folder, free space, overlay layer
 *   capture                 -> {"ok":true,"file":"Screenshots/…jpg","ms":480,...} or {"ok":false,"error":"no_usb"}
 *   record start <quality> [mic]  -> status; record stop -> status; record toggle <quality> [mic] -> status
 *                           ("mic": with the cabin microphone as the sound track; off unless the owner turns it on)
 *   overlay                 -> the floating button appeared or went: read its layer again
 *   diag <settings-json>    -> {"ok":true,"dir":"diag/…"}: SUMMARY.txt for an issue report
 *
 * Only root, system and the mod's own app may connect (checked by the peer's uid).
 */
public final class Main implements Recorder.Listener {
    static final String SOCKET = "dev.wisdomit.screencap";
    static String version = "?";

    private final Screen screen;
    private final Store store;
    private volatile int appUid;
    private Recorder recorder;
    private String lastError;
    private final Object lock = new Object();

    public static void main(String[] args) {
        String dir = System.getenv("HMGAPI_MOD_DIR");
        version = readVersion(dir);
        Sh.log("screencapd " + version + " starting (uid " + android.os.Process.myUid() + ")");
        try {
            new Main(new File(env("HMGAPI_DATA", "/data/local/tmp/5w-screencap"))).serve();
        } catch (Throwable t) {
            Sh.log("fatal", t);
            System.exit(1);
        }
    }

    Main(File dataDir) throws Exception {
        screen = new Screen();
        store = new Store(dataDir);
        appUid = uidOf(Screen.APP);
        Sh.log("boot " + store.boot + ", app uid " + appUid);
    }

    private void serve() throws Exception {
        LocalServerSocket server = new LocalServerSocket(SOCKET);
        // let the app put its floating button up now that we can answer it
        Sh.run(10000, "/system/bin/am", "startservice", "-n", Screen.APP + "/.FloatingService");
        while (true) {
            final LocalSocket s = server.accept();
            new Thread(new Runnable() {
                @Override public void run() { handle(s); }
            }, "client").start();
        }
    }

    private void handle(LocalSocket s) {
        try {
            int uid = s.getPeerCredentials().getUid();
            OutputStream out = s.getOutputStream();
            JSONObject reply;
            if (uid != 0 && uid != 1000 && uid != appUid && uid != (appUid = uidOf(Screen.APP))) {   // reread: the app may be newer than us
                reply = fail("not_allowed");
            } else {
                BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
                String line = r.readLine();
                reply = line == null ? fail("empty") : dispatch(line.trim());
            }
            out.write((reply.toString() + "\n").getBytes("UTF-8"));
            out.flush();
        } catch (Throwable t) {
            Sh.log("client", t);
        } finally {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    JSONObject dispatch(String line) throws Exception {
        String[] a = line.split(" ", 3);
        String cmd = a[0];
        if ("ping".equals(cmd)) return ok().put("version", version);
        if ("status".equals(cmd)) return status();
        if ("capture".equals(cmd)) return capture();
        if ("overlay".equals(cmd)) { screen.overlayChanged(); return ok(); }
        if ("record".equals(cmd) && a.length >= 2) {
            String[] opt = a.length >= 3 ? a[2].split(" ") : new String[0];
            String q = opt.length >= 1 ? opt[0] : "standard";
            boolean mic = opt.length >= 2 && "mic".equals(opt[1]);
            if ("start".equals(a[1])) return recordStart(q, mic);
            if ("stop".equals(a[1])) return recordStop();
            if ("toggle".equals(a[1])) return isRecording() ? recordStop() : recordStart(q, mic);
        }
        if ("diag".equals(cmd)) return new Diag(screen, store, version).run(a.length >= 2 ? line.substring(5) : "{}");
        return fail("unknown_command");
    }

    // ---- capture ----

    JSONObject capture() throws Exception {
        File dir = store.dir("Screenshots");
        if (dir == null) return fail("no_usb");
        if (Store.free(dir) < Store.MIN_FREE) return fail("usb_full");
        long t0 = System.currentTimeMillis();
        Bitmap b = screen.grab(1920, 720);
        if (b == null) return fail("no_screen");
        File f = Names.unique(dir, store.nextName(), ".jpg");
        File tmp = new File(dir, "." + f.getName() + ".part");
        FileOutputStream o = new FileOutputStream(tmp);
        try {
            b.compress(Bitmap.CompressFormat.JPEG, 90, o);
            o.getFD().sync();
        } finally {
            o.close();
            b.recycle();
        }
        if (!tmp.renameTo(f)) return fail("write_failed");
        long ms = System.currentTimeMillis() - t0;
        Sh.log("capture " + f.getPath() + " " + ms + " ms");
        return ok().put("file", "Screenshots/" + f.getName()).put("ms", ms)
                .put("width", 1920).put("height", 720).put("overlayExcluded", screen.excludesOverlay());
    }

    // ---- recording ----

    boolean isRecording() {
        synchronized (lock) { return recorder != null; }
    }

    JSONObject recordStart(String qualityName, boolean mic) throws Exception {
        synchronized (lock) {
            if (recorder != null) return status();
            File dir = store.dir("Recordings");
            if (dir == null) return fail("no_usb");
            if (Store.free(dir) < Store.MIN_FREE) return fail("usb_full");
            lastError = null;
            recorder = new Recorder(screen, Quality.of(qualityName), dir, store.nextName(), mic, this);
            recorder.start();
            Sh.log("recording " + recorder.quality.name + (mic ? " with the microphone" : "") + " into " + dir);
        }
        return status();
    }

    JSONObject recordStop() throws Exception {
        Recorder r;
        synchronized (lock) { r = recorder; }
        if (r != null) r.stop(8000);
        JSONObject s = status();
        if (r != null) s.put("stopped", true).put("seconds", (System.currentTimeMillis() - r.startedAt) / 1000)
                .put("segments", r.segments());
        return s;
    }

    @Override
    public void ended(Recorder r, String error) {
        synchronized (lock) {
            if (recorder == r) recorder = null;
            lastError = error;
        }
        // stopped by itself (stick pulled, full, encoder): say so, the app may not be looking
        if ("usb_removed".equals(error)) Sh.toast("녹화가 멈췄습니다: USB가 분리됐습니다");
        else if ("usb_full".equals(error)) Sh.toast("녹화가 멈췄습니다: USB 공간이 부족합니다");
        else if (error != null) Sh.toast("녹화가 멈췄습니다: 인코더 오류");
    }

    JSONObject status() throws Exception {
        JSONObject s = ok().put("version", version);
        Recorder r;
        synchronized (lock) { r = recorder; s.put("lastError", lastError == null ? JSONObject.NULL : lastError); }
        s.put("recording", r != null);
        if (r != null) {
            s.put("startedAt", r.startedAt).put("quality", r.quality.name)
                    .put("segments", r.segments()).put("frames", r.frames())
                    .put("file", r.currentFile() == null ? JSONObject.NULL : r.currentFile())
                    .put("mic", r.micState());
        }
        File root = store.root();
        s.put("usb", root == null ? JSONObject.NULL : root.getPath());
        s.put("freeBytes", root == null ? -1 : Store.free(root));
        s.put("overlayLayer", screen.overlayLayer());
        return s;
    }

    // ---- util ----

    static JSONObject ok() throws Exception { return new JSONObject().put("ok", true); }

    static JSONObject fail(String error) throws Exception { return new JSONObject().put("ok", false).put("error", error); }

    static String env(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isEmpty() ? def : v;
    }

    /** "version" from the mod's mod.json (HMGAPI_MOD_DIR), the one place versions come from */
    static String readVersion(String modDir) {
        if (modDir == null) return "?";
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new FileReader(new File(modDir, "mod.json")));
            try { for (String l; (l = r.readLine()) != null; ) sb.append(l).append('\n'); } finally { r.close(); }
            return new JSONObject(sb.toString()).optString("version", "?");
        } catch (Exception e) {
            return "?";
        }
    }

    /** the app's uid from /data/system/packages.list ("<package> <uid> <debug> <data dir> …"), or -1 */
    static int uidOf(String pkg) {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/data/system/packages.list"));
            try {
                for (String l; (l = r.readLine()) != null; ) {
                    String[] f = l.split(" ");
                    if (f.length > 1 && f[0].equals(pkg)) return Integer.parseInt(f[1]);
                }
            } finally {
                r.close();
            }
        } catch (Exception e) {
            Sh.log("packages.list", e);
        }
        return -1;
    }
}
