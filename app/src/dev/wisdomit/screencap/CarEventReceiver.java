package dev.wisdomit.screencap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * HMG-API car events (mod.json "car_events"). At shutdown the open segment is closed so the
 * last minutes are playable; HMG-API waits for us (6 s for shutdown).
 */
public final class CarEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!"shutdown".equals(i.getStringExtra("event"))) return;
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Daemon.call("record stop", 5000);
                } finally {
                    pr.finish();
                }
            }
        }, "shutdown").start();
    }
}
