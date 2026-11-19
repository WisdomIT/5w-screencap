package dev.wisdomit.screencap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/**
 * Start-up and changes: settings saved (here or in the HMG-API app), a stick in or out.
 * With "USB 연결 시 자동 녹화" on, a stick that comes in starts a recording.
 */
public final class EventReceiver extends BroadcastReceiver {
    static final String STATE_CHANGED = "hmgapi.intent.action.STATE_CHANGED", USB_FOLDER = "hmgapi.intent.action.USB_FOLDER";

    @Override
    public void onReceive(final Context c, Intent i) {
        final Context app = c.getApplicationContext();
        final String action = i.getAction();
        final boolean mounted = i.getBooleanExtra("mounted", "true".equals(i.getStringExtra("mounted")));
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject s = Settings.sync(app, Settings.load(app));
                    if (USB_FOLDER.equals(action)) {
                        if (mounted && s.optBoolean(Settings.AUTO_RECORD)) autoRecord(app);
                        FloatingService.refresh(app);   // the button shows only while a stick is in
                    } else {
                        FloatingService.sync(app, s.optBoolean(Settings.FLOATING, true));
                    }
                } finally {
                    pr.finish();
                }
            }
        }, "events").start();
    }

    /** a stick just came in: give the mount a few seconds, then start (no-op if already recording) */
    private static void autoRecord(final Context app) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { Actions.record(app, "start", null); }
        }, 3000);
    }
}
