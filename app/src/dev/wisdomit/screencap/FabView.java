package dev.wisdomit.screencap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/**
 * The floating button's drawing: a round button, or a small tab at the screen edge when it
 * was pushed off. While recording it turns red and shows the time. The window it lives in
 * sits above the layers that get captured, so none of this ends up in photos or videos.
 */
final class FabView extends View {
    static final int CIRCLE = 0, TAB_LEFT = 1, TAB_RIGHT = 2;
    static final int FILL = 0xC7181B20, FILL_TAB = 0xE0181B20, REC = 0xEBD6282E, REC_DOT = 0xFFFF4D4F, WHITE = 0xFFFFFFFF;

    final float dp;
    int mode = CIRCLE;
    boolean recording;
    long startedAt;              // System.currentTimeMillis() when recording began (the service's clock)
    long holdSince = -1;         // uptime when a long press began, -1 when not holding
    long holdMs = 600;
    private long flashUntil;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    FabView(Context c, float dp) {
        super(c);
        this.dp = dp;
        stroke.setStyle(Paint.Style.STROKE);
        text.setColor(WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.MONOSPACE);
    }

    /** the window size for the current mode, in px: {width, height} */
    int[] size() {
        if (mode == CIRCLE) return new int[] { box(), box() };
        return new int[] { Math.round((recording ? 128 : 40) * dp), Math.round(64 * dp) };
    }

    int box() { return Math.round(88 * dp); }

    void flash() {
        flashUntil = SystemClock.uptimeMillis() + 180;
        invalidate();
        postInvalidateDelayed(200);
    }

    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (mode == CIRCLE) drawCircle(c);
        else drawTab(c);
        if (recording || holdSince >= 0) postInvalidateDelayed(holdSince >= 0 ? 30 : 500);
    }

    private void drawCircle(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, rad = 36 * dp;
        if (recording) {
            fill.setColor(0x38FF4D4F);
            c.drawCircle(cx, cy, rad + 8 * dp, fill);
        }
        fill.setColor(recording ? REC : FILL);
        c.drawCircle(cx, cy, rad, fill);
        stroke.setColor(0xD9FFFFFF);
        stroke.setStrokeWidth(2 * dp);
        c.drawCircle(cx, cy, rad - dp, stroke);

        if (recording) {
            text.setTextSize(17 * dp);
            c.drawText(clock(System.currentTimeMillis() - startedAt), cx, cy + 6 * dp, text);
        } else {
            stroke.setColor(WHITE);
            stroke.setStrokeWidth(2.5f * dp);
            c.drawCircle(cx, cy, 12 * dp, stroke);
            fill.setColor(WHITE);
            c.drawCircle(cx, cy, 6 * dp, fill);
        }

        if (holdSince >= 0) {   // long press: a ring fills up until recording starts
            float p = Math.min(1f, (SystemClock.uptimeMillis() - holdSince) / (float) holdMs);
            stroke.setColor(WHITE);
            stroke.setStrokeWidth(3 * dp);
            r.set(cx - rad - 5 * dp, cy - rad - 5 * dp, cx + rad + 5 * dp, cy + rad + 5 * dp);
            c.drawArc(r, -90, 360 * p, false, stroke);
        }
        if (SystemClock.uptimeMillis() < flashUntil) {
            fill.setColor(0xA6FFFFFF);
            c.drawCircle(cx, cy, rad, fill);
        }
    }

    private void drawTab(Canvas c) {
        float w = getWidth(), h = getHeight(), rad = 16 * dp;
        boolean right = mode == TAB_RIGHT;
        fill.setColor(FILL_TAB);
        // round only the corners facing into the screen
        r.set(right ? 0 : -rad, 0, right ? w + rad : w, h);
        c.drawRoundRect(r, rad, rad, fill);

        text.setTextSize(28 * dp);
        float chevronX = right ? 20 * dp : w - 20 * dp;
        c.drawText(right ? "‹" : "›", chevronX, h / 2 + 9 * dp, text);
        if (recording) {
            float dotX = right ? 44 * dp : w - 44 * dp;
            fill.setColor(REC_DOT);
            c.drawCircle(dotX, h / 2, 6 * dp, fill);
            text.setTextSize(16 * dp);
            float timeX = right ? 90 * dp : w - 90 * dp;
            c.drawText(clock(System.currentTimeMillis() - startedAt), timeX, h / 2 + 6 * dp, text);
        }
    }
}
