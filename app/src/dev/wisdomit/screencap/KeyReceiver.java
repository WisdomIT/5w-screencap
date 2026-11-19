package dev.wisdomit.screencap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import org.json.JSONObject;

/**
 * Button presses: HMG-API's "input" bindings (extras hmgapi.key, hmgapi.press) and the
 * car's ★-key menu entries (no extras). The binding that counts is already the owner's
 * choice (mod.json "when"), so a press here is meant for us.
 *
 * "Double" (two presses within 0.4 s) is counted here: HMG-API only delivers single
 * presses, taken from the car ("stock": "replace"), so a lone press does nothing.
 *
 * While a recording runs, the capture button stops it instead of taking a photo.
 */
public final class KeyReceiver extends BroadcastReceiver {
    static final String CAPTURE = "dev.wisdomit.screencap.CAPTURE", RECORD = "dev.wisdomit.screencap.RECORD";
    static final long DOUBLE_MS = 400;

    private static final DoublePress captureTaps = new DoublePress(DOUBLE_MS), recordTaps = new DoublePress(DOUBLE_MS);

    @Override
    public void onReceive(Context c, Intent i) {
        boolean capture = CAPTURE.equals(i.getAction());
        if (!capture && !RECORD.equals(i.getAction())) return;
        JSONObject s = Settings.cached(c);
        String choice = s.optString(capture ? Settings.CAPTURE : Settings.RECORD);
        String press = i.getStringExtra("hmgapi.press");
        if ("short".equals(press) && choice.endsWith(".double")) {
            DoublePress taps = capture ? captureTaps : recordTaps;
            if (!taps.press(SystemClock.uptimeMillis())) return;
        }
        final PendingResult pr = goAsync();
        Actions.Done done = new Actions.Done() {
            @Override public void done(JSONObject r) { pr.finish(); }
        };
        if (capture) Actions.captureOrStop(c, done);
        else Actions.record(c, "toggle", done);
    }
}
