package dev.wisdomit.screencap;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

/**
 * What the driver hears and sees. Sound first: a button press while driving should be
 * answered without looking. The notice is the car's own (shown over CarPlay and Android Auto).
 */
final class Feedback {
    enum Tone {
        SHUTTER(ToneGenerator.TONE_PROP_BEEP, 120),
        REC_START(ToneGenerator.TONE_PROP_BEEP2, 300),
        REC_STOP(ToneGenerator.TONE_PROP_ACK, 300),
        ERROR(ToneGenerator.TONE_PROP_NACK, 400);

        final int type, ms;
        Tone(int type, int ms) { this.type = type; this.ms = ms; }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Feedback() {}

    static void toast(Context c, String text) {
        c.sendBroadcast(new Intent("com.hkmc.intent.action.TOAST")
                .putExtra("com.hkmc.intent.extra.TOAST_MESSAGE", text)
                .putExtra("com.hkmc.intent.extra.TOAST_POPUPSHOWING", true));
    }

    static void tone(Tone t) {
        try {
            final ToneGenerator g = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            g.startTone(t.type, t.ms);
            MAIN.postDelayed(new Runnable() {
                @Override public void run() { g.release(); }
            }, t.ms + 200);
        } catch (Exception ignored) {
            // no audio route right now: the notice still shows
        }
    }

    /** a daemon error code in words the driver can act on */
    static String error(String code) {
        if ("no_usb".equals(code)) return "USB를 연결하세요";
        if ("usb_full".equals(code)) return "USB 공간이 부족합니다";
        if ("no_service".equals(code) || "no_reply".equals(code)) return "캡처 서비스가 응답하지 않습니다. HMG-API에서 5W Screencap이 켜져 있는지 확인하세요";
        if ("no_screen".equals(code)) return "화면을 가져오지 못했습니다";
        if ("write_failed".equals(code)) return "USB에 쓰지 못했습니다";
        if ("not_allowed".equals(code)) return "캡처 서비스가 이 앱을 알아보지 못했습니다. 재부팅 후 다시 시도하세요";
        return "실패했습니다 (" + code + ")";
    }
}
