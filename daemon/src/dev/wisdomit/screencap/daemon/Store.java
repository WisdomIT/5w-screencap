package dev.wisdomit.screencap.daemon;

import android.os.StatFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Where files go: the mod's USB folder (HMG-API makes 5W-Screencap on each mounted stick;
 * `hmgapid usb` lists them), never the head unit's own storage.
 */
final class Store {
    static final String FOLDER = "5W-Screencap";
    static final String[] STICKS = { "/storage/usb0", "/storage/usb1", "/storage/usb2" };
    /** below this much free space nothing new is started */
    static final long MIN_FREE = 64L * 1024 * 1024;

    private final File dataDir;
    final int boot;
    private int counter;

    Store(File dataDir) {
        this.dataDir = dataDir;
        this.boot = nextBoot();
    }

    /** the 5W-Screencap folder on the first mounted stick, or null */
    File root() {
        String out = Sh.run(5000, "/system/bin/hmgapid", "usb");
        if (out != null) {
            for (String line : out.split("\n")) {
                String p = line.trim();
                int sp = p.indexOf(' ');
                if (sp > 0) p = p.substring(0, sp);
                if (p.startsWith("/") && p.endsWith("/" + FOLDER) && mounted(new File(p).getParent())) {
                    File f = new File(p);
                    if (f.isDirectory() || f.mkdirs()) return f;
                }
            }
        }
        for (String stick : STICKS) {   // hmgapid not answering: look ourselves
            if (!mounted(stick)) continue;
            File f = new File(stick, FOLDER);
            if (f.isDirectory() || f.mkdirs()) return f;
        }
        return null;
    }

    File dir(String sub) {
        File r = root();
        if (r == null) return null;
        File d = new File(r, sub);
        return d.isDirectory() || d.mkdirs() ? d : null;
    }

    static long free(File f) {
        try {
            StatFs s = new StatFs(f.getPath());
            return (long) s.getAvailableBlocks() * s.getBlockSize();
        } catch (Exception e) {
            return -1;
        }
    }

    synchronized String nextName() {
        return Names.stamp(System.currentTimeMillis(), boot, ++counter);
    }

    static boolean mounted(String path) {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/mounts"));
            try {
                for (String l; (l = r.readLine()) != null; ) if (l.contains(" " + path + " ")) return true;
            } finally {
                r.close();
            }
        } catch (Exception ignored) { }
        return false;
    }

    /** a number that grows by one each time the service starts (names before the clock is set) */
    private int nextBoot() {
        File f = new File(dataDir, "boot-count");
        int n = 0;
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));
            try { n = Integer.parseInt(r.readLine().trim()); } finally { r.close(); }
        } catch (Exception ignored) { }
        n = (n + 1) % 10000;
        try {
            dataDir.mkdirs();
            FileWriter w = new FileWriter(f);
            try { w.write(n + "\n"); } finally { w.close(); }
        } catch (Exception e) {
            Sh.log("boot-count", e);
        }
        return n;
    }
}
