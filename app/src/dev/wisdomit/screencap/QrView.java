package dev.wisdomit.screencap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import io.nayuki.qrcodegen.QrCode;

/** A black QR code on white, square, with the quiet zone phones need to read it off a dark screen. (From pretendard-systemfont.) */
final class QrView extends View {
    private QrCode qr;
    private final Paint black = new Paint();

    QrView(Context c) {
        super(c);
        black.setColor(Color.BLACK);
        black.setAntiAlias(false);
    }

    void setText(String text) {
        try { qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM); }
        catch (Exception e) { qr = null; }
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
        if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) size = MeasureSpec.getSize(w);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas c) {
        int size = Math.min(getWidth(), getHeight());
        c.drawColor(Color.WHITE);
        if (qr == null) return;
        int modules = qr.size + 8;                 // 4 modules of quiet zone on each side
        float cell = (float) size / modules;
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.getModule(x, y)) {
                    float l = (x + 4) * cell, t = (y + 4) * cell;
                    c.drawRect(l, t, l + cell + 0.5f, t + cell + 0.5f, black);
                }
            }
        }
    }
}
