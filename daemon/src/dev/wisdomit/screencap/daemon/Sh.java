package dev.wisdomit.screencap.daemon;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Runs head-unit commands and writes the service log (stdout: HMG-API keeps it). */
final class Sh {
    private Sh() {}

    static void log(String s) {
        System.out.println(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + " " + s);
    }

    static void log(String s, Throwable t) {
        log(s + ": " + t);
    }

    /** stdout+stderr of the command, or null if it couldn't run or took longer than timeoutMs */
    static String run(int timeoutMs, String... cmd) {
        final Process p;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (Exception e) {
            return null;
        }
        final boolean[] done = { false };
        Thread killer = new Thread(new Runnable() {
            @Override public void run() {
                try { Thread.sleep(timeoutMs); } catch (InterruptedException e) { return; }
                synchronized (done) { if (!done[0]) p.destroy(); }
            }
        }, "sh-timeout");
        killer.setDaemon(true);
        killer.start();
        try {
            InputStream in = p.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
            p.waitFor();
            synchronized (done) { done[0] = true; }
            killer.interrupt();
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /** the car's own notice, over any screen (CarPlay and Android Auto too) */
    static void toast(String text) {
        run(5000, "/system/bin/am", "broadcast", "-a", "com.hkmc.intent.action.TOAST",
                "-e", "com.hkmc.intent.extra.TOAST_MESSAGE", text,
                "--ez", "com.hkmc.intent.extra.TOAST_POPUPSHOWING", "true");
    }

    static String prop(String name) {
        String v = run(3000, "/system/bin/getprop", name);
        return v == null ? "" : v.trim();
    }
}
