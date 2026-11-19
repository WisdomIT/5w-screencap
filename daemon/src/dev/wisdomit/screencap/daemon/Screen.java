package dev.wisdomit.screencap.daemon;

import android.graphics.Bitmap;

import java.lang.reflect.Method;

/**
 * Screen grabs through SurfaceFlinger (android.view.SurfaceControl, hidden API; we run as root).
 *
 * The floating button is a TYPE_SYSTEM_ERROR window (layer 22, above the status bar at 16 and
 * the navigation bar at 20), so a grab limited to the layers below it leaves the button and its
 * recording timer out of photos and videos. The button's real layer is read from
 * `dumpsys window windows` (a maker can change the order), refreshed at most every 10 s.
 */
final class Screen {
    static final String APP = "dev.wisdomit.screencap";
    /** KitKat: TYPE_SYSTEM_ERROR is type layer 22; WindowManager puts it at 22 * 10000 + 1000 */
    static final int DEFAULT_OVERLAY_LAYER = 221000;

    private final Method shot2, shot4;
    private volatile int overlayLayer = -1;     // -1: no floating button on screen
    private volatile long overlayCheckedAt;

    Screen() throws Exception {
        Class<?> sc = Class.forName("android.view.SurfaceControl");
        shot2 = sc.getMethod("screenshot", int.class, int.class);
        Method m4 = null;
        try { m4 = sc.getMethod("screenshot", int.class, int.class, int.class, int.class); } catch (NoSuchMethodException ignored) { }
        shot4 = m4;
    }

    /** the screen scaled to w x h, without the floating button; null if SurfaceFlinger gave nothing */
    Bitmap grab(int w, int h) throws Exception {
        int layer = overlayLayer();
        if (layer > 0 && shot4 != null) return (Bitmap) shot4.invoke(null, w, h, 0, layer - 1);
        return (Bitmap) shot2.invoke(null, w, h);
    }

    /** whether the last grab left the floating button out (for the capture test) */
    boolean excludesOverlay() {
        return overlayLayer > 0 && shot4 != null;
    }

    int overlayLayer() {
        long now = System.currentTimeMillis();
        if (now - overlayCheckedAt > 10000) {
            overlayCheckedAt = now;
            overlayLayer = findOverlayLayer(Sh.run(5000, "/system/bin/dumpsys", "window", "windows"));
        }
        return overlayLayer;
    }

    /** the app's overlay changed (shown, hidden): read the layer again at the next grab */
    void overlayChanged() {
        overlayCheckedAt = 0;
    }

    /**
     * The base layer of this app's window from `dumpsys window windows`, or -1 if it has none.
     * KitKat prints "Window #4 Window{41d2 u0 dev.wisdomit.screencap}:" and further down that
     * window's "mBaseLayer=221000 mSubLayer=0 ...".
     */
    static int findOverlayLayer(String dump) {
        if (dump == null) return -1;
        boolean inOurs = false;
        for (String line : dump.split("\n")) {
            String t = line.trim();
            if (t.startsWith("Window #")) inOurs = t.contains(" " + APP + "}") || t.contains(" " + APP + "/");
            else if (inOurs && t.startsWith("mBaseLayer=")) {
                String v = t.substring("mBaseLayer=".length());
                int sp = v.indexOf(' ');
                try {
                    return Integer.parseInt(sp < 0 ? v : v.substring(0, sp));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
