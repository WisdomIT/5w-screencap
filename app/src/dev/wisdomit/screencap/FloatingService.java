package dev.wisdomit.screencap;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import org.json.JSONObject;

/**
 * The floating button.
 *
 *   tap                  capture (while recording: stop)
 *   hold 0.6 s           start recording
 *   drag                 move; let go and it sticks to the nearer side
 *   drag half off-screen it folds into a small tab at that edge; tap the tab to bring it back
 *
 * It's on screen only while a USB stick is in (no stick: nowhere to save, so nothing to press);
 * the service keeps running and puts it back when one comes in (USB_FOLDER, or the next status).
 *
 * The window is TYPE_SYSTEM_ERROR (layer 22, above the status bar): screencapd grabs only the
 * layers below it, so the button and its timer stay out of captures. It's told when the
 * window comes and goes so it reads the layer again.
 */
public final class FloatingService extends Service {
    static final int TYPE_SYSTEM_ERROR = 2010;
    static final float TOP_INSET_DP = 56, MARGIN_DP = 8;

    private static volatile FloatingService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FabView view;
    private int screenW, screenH;
    private float dp;
    private boolean polling, attached;

    /** puts the button up or takes it down, as the "floating" setting says */
    static void sync(Context c, boolean on) {
        Intent i = new Intent(c, FloatingService.class);
        if (on) c.startService(i);
        else c.stopService(i);
    }

    /** something changed (recording started or stopped): ask screencapd now */
    static void refresh(Context c) {
        final FloatingService s = instance;
        if (s != null) s.main.post(new Runnable() {
            @Override public void run() { s.poll(); }
        });
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        if (view == null) show();
        return START_STICKY;
    }

    private void show() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics m = getResources().getDisplayMetrics();
        dp = m.density;
        screenW = m.widthPixels;
        screenH = m.heightPixels;
        view = new FabView(this, dp);

        SharedPreferences p = Settings.prefs(this);
        view.mode = p.getInt("fab_mode", FabView.CIRCLE);
        int box = view.box();
        lp = new WindowManager.LayoutParams(box, box, TYPE_SYSTEM_ERROR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = p.getInt("fab_x", screenW - box - px(MARGIN_DP));
        lp.y = clampY(p.getInt("fab_y", screenH / 2 - box / 2));
        view.setOnTouchListener(new Touch());
        instance = this;
        polling = true;
        poll();   // the button goes up once screencapd says a stick is in
    }

    /** puts the window up or takes it down; tells screencapd so it reads the layer again */
    private void attach(boolean on) {
        if (on == attached || view == null) return;
        try {
            if (on) {
                wm.addView(view, lp);
                attached = true;
                layout();
            } else {
                wm.removeView(view);
                attached = false;
            }
        } catch (Exception ignored) { }
        tellDaemonOverlay();
    }

    @Override
    public void onDestroy() {
        instance = null;
        polling = false;
        main.removeCallbacksAndMessages(null);
        if (view != null) {
            attach(false);
            view = null;
        }
        super.onDestroy();
    }

    // ---- recording state from screencapd: every 3 s, every 1 s while recording ----

    private void poll() {
        if (view == null) return;
        main.removeCallbacks(pollTask);
        new Thread(new Runnable() {
            @Override public void run() {
                final JSONObject s = Daemon.call("status", 3000);
                main.post(new Runnable() {
                    @Override public void run() { apply(s); }
                });
            }
        }, "fab-status").start();
    }

    private final Runnable pollTask = new Runnable() {
        @Override public void run() { poll(); }
    };

    private void apply(JSONObject s) {
        if (view == null) return;
        attach(s.optBoolean("ok") && !s.isNull("usb") && s.has("usb"));
        boolean rec = s.optBoolean("recording");
        if (rec != view.recording || s.optLong("startedAt") != view.startedAt) {
            view.recording = rec;
            view.startedAt = s.optLong("startedAt");
            layout();
            view.invalidate();
        }
        if (polling) main.postDelayed(pollTask, rec ? 1000 : 3000);
    }

    // ---- placement ----

    private void layout() {
        int[] sz = view.size();
        lp.width = sz[0];
        lp.height = sz[1];
        if (view.mode == FabView.TAB_LEFT) lp.x = 0;
        else if (view.mode == FabView.TAB_RIGHT) lp.x = screenW - sz[0];
        if (attached) try { wm.updateViewLayout(view, lp); } catch (Exception ignored) { }
    }

    private void save() {
        Settings.prefs(this).edit().putInt("fab_mode", view.mode).putInt("fab_x", lp.x).putInt("fab_y", lp.y).apply();
    }

    private int clampY(int y) {
        int top = px(TOP_INSET_DP), bottom = screenH - view.box();
        return Math.max(top, Math.min(bottom, y));
    }

    private int px(float dps) { return Math.round(dps * dp); }

    private void dock(int mode) {
        int box = view.box();
        view.mode = mode;
        lp.y = clampY(lp.y + (box - px(64)) / 2);
        layout();
        save();
    }

    private void undock() {
        int box = view.box();
        boolean left = view.mode == FabView.TAB_LEFT;
        view.mode = FabView.CIRCLE;
        lp.x = left ? px(MARGIN_DP) : screenW - box - px(MARGIN_DP);
        lp.y = clampY(lp.y - (box - px(64)) / 2);
        layout();
        save();
    }

    private void tellDaemonOverlay() {
        new Thread(new Runnable() {
            @Override public void run() { Daemon.call("overlay", 3000); }
        }, "fab-overlay").start();
    }

    private final class Touch implements View.OnTouchListener {
        float downX, downY;
        int startX, startY;
        boolean moved, longFired;

        private final Runnable longPress = new Runnable() {
            @Override public void run() {
                longFired = true;
                view.holdSince = -1;
                view.invalidate();
                Actions.record(FloatingService.this, "start", null);
            }
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    moved = false;
                    longFired = false;
                    if (view.mode == FabView.CIRCLE && !view.recording) {
                        view.holdSince = SystemClock.uptimeMillis();
                        view.invalidate();
                        main.postDelayed(longPress, view.holdMs);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!moved && Math.abs(dx) + Math.abs(dy) > px(10)) {
                        moved = true;
                        cancelHold();
                    }
                    if (moved && view.mode == FabView.CIRCLE) {
                        lp.x = startX + Math.round(dx);
                        lp.y = clampY(startY + Math.round(dy));
                        if (attached) try { wm.updateViewLayout(view, lp); } catch (Exception ignored) { }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    cancelHold();
                    if (view.mode != FabView.CIRCLE) {
                        if (!moved) undock();
                    } else if (moved) {
                        int box = view.box(), center = lp.x + box / 2;
                        if (center < 0) dock(FabView.TAB_LEFT);
                        else if (center > screenW) dock(FabView.TAB_RIGHT);
                        else {
                            lp.x = center < screenW / 2 ? px(MARGIN_DP) : screenW - box - px(MARGIN_DP);
                            layout();
                            save();
                        }
                    } else if (!longFired) {
                        if (view.recording) {
                            Actions.record(FloatingService.this, "stop", null);
                        } else {
                            view.flash();
                            Actions.capture(FloatingService.this, null);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelHold();
                    return true;
                default:
                    return false;
            }
        }

        private void cancelHold() {
            main.removeCallbacks(longPress);
            if (view.holdSince >= 0) {
                view.holdSince = -1;
                view.invalidate();
            }
        }
    }
}
