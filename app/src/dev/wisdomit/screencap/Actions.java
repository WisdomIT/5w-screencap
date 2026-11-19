package dev.wisdomit.screencap;

import android.content.Context;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Capture and record, from any trigger (button, ★ menu, floating button, USB), with feedback. */
final class Actions {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();

    interface Done { void done(JSONObject result); }

    private Actions() {}

    static void capture(final Context c, final Done done) {
        final Context app = c.getApplicationContext();
        WORK.execute(new Runnable() {
            @Override public void run() {
                JSONObject r = Daemon.call("capture", 15000);
                if (r.optBoolean("ok")) {
                    Feedback.tone(Feedback.Tone.SHUTTER);
                    Feedback.toast(app, "캡처 저장됨");
                } else {
                    Feedback.tone(Feedback.Tone.ERROR);
                    Feedback.toast(app, "캡처하지 못했습니다: " + Feedback.error(r.optString("error")));
                }
                finish(app, r, done);
            }
        });
    }

    /** "toggle", "start" or "stop" */
    static void record(final Context c, final String how, final Done done) {
        final Context app = c.getApplicationContext();
        WORK.execute(new Runnable() {
            @Override public void run() {
                JSONObject set = Settings.cached(app);
                String q = set.optString(Settings.QUALITY, "standard");
                boolean mic = set.optBoolean(Settings.MIC);
                JSONObject r = Daemon.call("record " + how + " " + q + (mic ? " mic" : ""), 15000);
                if (!r.optBoolean("ok")) {
                    Feedback.tone(Feedback.Tone.ERROR);
                    Feedback.toast(app, "녹화하지 못했습니다: " + Feedback.error(r.optString("error")));
                } else if (r.optBoolean("stopped")) {
                    long s = r.optLong("seconds");
                    Feedback.tone(Feedback.Tone.REC_STOP);
                    Feedback.toast(app, "녹화 저장됨 · " + (s / 60) + "분 " + (s % 60) + "초");
                } else if (r.optBoolean("recording") && !"stop".equals(how)) {
                    Feedback.tone(Feedback.Tone.REC_START);
                    Feedback.toast(app, mic ? "녹화를 시작합니다 (마이크 소리 포함)" : "녹화를 시작합니다");
                }
                finish(app, r, done);
            }
        });
    }

    private static void finish(Context app, JSONObject r, Done done) {
        FloatingService.refresh(app);
        if (done != null) done.done(r);
    }
}
