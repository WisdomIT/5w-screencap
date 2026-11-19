package dev.wisdomit.screencap.daemon;

import java.io.File;
import java.nio.file.Files;
import java.util.Calendar;

import static dev.wisdomit.screencap.T.check;
import static dev.wisdomit.screencap.T.eq;

public final class DaemonTests {
    public static void run() throws Exception {
        // Yuv: white and red, BT.601 limited range, I420 plane layout
        int w = 4, h = 2;
        byte[] rgba = new byte[w * h * 4];
        for (int i = 0; i < w * h; i++) {
            boolean white = (i % 4) < 2;
            rgba[i * 4] = (byte) 255;
            rgba[i * 4 + 1] = (byte) (white ? 255 : 0);
            rgba[i * 4 + 2] = (byte) (white ? 255 : 0);
            rgba[i * 4 + 3] = (byte) 255;
        }
        byte[] yuv = new byte[w * h * 3 / 2];
        Yuv.rgbaToI420(rgba, yuv, w, h);
        int[] want = { 235, 235, 82, 82, 235, 235, 82, 82, 128, 90, 128, 240 };
        for (int i = 0; i < want.length; i++) eq("yuv[" + i + "]", want[i], yuv[i] & 0xff);

        // Quality: every preset fits the MPEG-4 encoder's Core Profile Level 2 and the encoder's 16-pixel grid
        for (Quality q : Quality.ALL) {
            check(q.name + " macroblocks/s " + q.macroblocksPerSecond() + " <= " + Quality.MAX_MBPS, q.macroblocksPerSecond() <= Quality.MAX_MBPS);
            check(q.name + " bitrate", q.bitrate <= Quality.MAX_BITRATE);
            check(q.name + " size multiple of 16", q.width % 16 == 0 && q.height % 16 == 0);
            check(q.name + " is 8:3 like the screen", q.width * 3 == q.height * 8);
        }
        eq("unknown quality falls back", "standard", Quality.of("nope").name);
        eq("sharp", "sharp", Quality.of("sharp").name);

        // Names: real clock -> date stamp; the 2015 clock -> boot counter
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.NOVEMBER, 19, 20, 52, 22);
        eq("stamp", "20261119_205222", Names.stamp(c.getTimeInMillis(), 42, 3));
        c.set(2015, Calendar.JANUARY, 1, 0, 0, 5);
        eq("stamp before the clock is set", "boot0042_003", Names.stamp(c.getTimeInMillis(), 42, 3));
        eq("segment", "20261119_205222_002", Names.segment("20261119_205222", 2));
        File dir = Files.createTempDirectory("names").toFile();
        File a = Names.unique(dir, "x", ".jpg");
        a.createNewFile();
        eq("unique second", "x-2.jpg", Names.unique(dir, "x", ".jpg").getName());

        // Screen: the floating button's layer from `dumpsys window windows`
        String dump = "WINDOW MANAGER WINDOWS (dumpsys window windows)\n"
                + "  Window #5 Window{41a2c8 u0 StatusBar}:\n"
                + "    mBaseLayer=161000 mSubLayer=0 mAnimLayer=161000+0=161000 mLastLayer=161000\n"
                + "  Window #6 Window{42b9d0 u0 dev.wisdomit.screencap}:\n"
                + "    mOwnerUid=10061 mShowToOwnerOnly=true package=dev.wisdomit.screencap appop=SYSTEM_ALERT_WINDOW\n"
                + "    mBaseLayer=221000 mSubLayer=0 mAnimLayer=221000+0=221000 mLastLayer=221000\n";
        eq("overlay layer", 221000, Screen.findOverlayLayer(dump));
        eq("no overlay", -1, Screen.findOverlayLayer("  Window #5 Window{41a2c8 u0 StatusBar}:\n    mBaseLayer=161000 mSubLayer=0\n"));
        eq("no dump", -1, Screen.findOverlayLayer(null));
        eq("default layer is TYPE_SYSTEM_ERROR's", 22 * 10000 + 1000, Screen.DEFAULT_OVERLAY_LAYER);
    }
}
