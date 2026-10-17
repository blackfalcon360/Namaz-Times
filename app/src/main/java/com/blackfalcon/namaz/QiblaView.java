package com.blackfalcon.namaz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Compass dial: long green arrow pointing at the Kaaba icon on the ring. Compass ON = dial turns with the phone. */
public class QiblaView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float heading = 0f, qibla = 0f;
    private boolean compassOn = false, hasQibla = false, aligned = false;

    public QiblaView(Context c) { super(c); }

    public void setState(float heading, boolean compassOn, float qibla, boolean hasQibla, boolean aligned) {
        this.heading = heading; this.compassOn = compassOn; this.qibla = qibla; this.hasQibla = hasQibla; this.aligned = aligned;
        invalidate();
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f - dp(44);
        float rot = compassOn ? heading : 0f;
        cv.drawColor(0xFF000000);

        int good = aligned ? 0xFFFFD54F : 0xFF2ECC71;

        // dial
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0C0C1E);
        cv.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(aligned ? 4 : 2));
        p.setColor(aligned ? 0xFF2ECC71 : 0xFF3A3A60);
        cv.drawCircle(cx, cy, r, p);

        // ticks turn with the dial
        p.setStrokeWidth(dp(1.5f));
        for (int deg = 0; deg < 360; deg += 10) {
            double a = Math.toRadians(deg - rot);
            float len = dp(deg % 90 == 0 ? 14 : (deg % 30 == 0 ? 10 : 5));
            float sx = (float) Math.sin(a), sy = -(float) Math.cos(a);
            p.setColor(deg == 0 ? 0xFFE57373 : 0xFF6A6A90);
            cv.drawLine(cx + sx * (r - len), cy + sy * (r - len), cx + sx * r, cy + sy * r, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(16));
        String[] card = {"N", "E", "S", "W"};
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(k * 90 - rot);
            p.setColor(k == 0 ? 0xFFE57373 : 0xFF9E9E9E);
            cv.drawText(card[k], cx + (float) (Math.sin(a) * (r - dp(30))), cy - (float) (Math.cos(a) * (r - dp(30))) + dp(5), p);
        }

        if (hasQibla) {
            // arrow (drawn pointing up, then rotated to the Qibla)
            cv.save();
            cv.rotate(qibla - rot, cx, cy);
            float tip = cy - r * 0.72f, headBase = tip + dp(34), half = dp(20), shaft = dp(6);
            Path arrow = new Path();
            arrow.moveTo(cx, tip);
            arrow.lineTo(cx + half, headBase);
            arrow.lineTo(cx + shaft, headBase);
            arrow.lineTo(cx + shaft, cy + dp(22));
            arrow.lineTo(cx - shaft, cy + dp(22));
            arrow.lineTo(cx - shaft, headBase);
            arrow.lineTo(cx - half, headBase);
            arrow.close();
            p.setColor(good);
            cv.drawPath(arrow, p);
            cv.restore();

            // Kaaba icon on the ring at the Qibla bearing
            double qa = Math.toRadians(qibla - rot);
            float kx = cx + (float) (Math.sin(qa) * r), ky = cy - (float) (Math.cos(qa) * r);
            p.setColor(0xFF02030A);
            cv.drawCircle(kx, ky, dp(25), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(0xFFFFC107);
            cv.drawCircle(kx, ky, dp(25), p);
            p.setStyle(Paint.Style.FILL);
            KaabaIcon.draw(cv, p, kx, ky, dp(34));
        }

        // hub
        p.setColor(0xFFFFFFFF);
        cv.drawCircle(cx, cy, dp(7), p);
        p.setColor(0xFF0C0C1E);
        cv.drawCircle(cx, cy, dp(3), p);

        // "this is where the phone points" marker
        if (compassOn) {
            Path tri = new Path();
            tri.moveTo(cx, cy - r - dp(8));
            tri.lineTo(cx - dp(9), cy - r - dp(30));
            tri.lineTo(cx + dp(9), cy - r - dp(30));
            tri.close();
            p.setColor(0xFFFFFFFF);
            cv.drawPath(tri, p);
        }
    }
}
