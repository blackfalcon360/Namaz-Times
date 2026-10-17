package com.blackfalcon.namaz;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Draws the Kaaba (black cube, gold belt, gold door) with no image files. */
public final class KaabaIcon {
    private KaabaIcon() { }

    private static void poly(Canvas cv, Paint p, int color, float... xy) {
        Path path = new Path();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
        path.close();
        p.setColor(color);
        cv.drawPath(path, p);
    }

    /** (cx, cy) = centre, s = icon width in pixels. */
    public static void draw(Canvas cv, Paint p, float cx, float cy, float s) {
        Paint.Style old = p.getStyle();
        p.setStyle(Paint.Style.FILL);
        float L = cx - 0.42f * s, F = cx + 0.18f * s, R = cx + 0.42f * s;          // left, front-right, back-right
        float T = cy - 0.28f * s, B = cy + 0.40f * s, tt = cy - 0.42f * s;           // front top, bottom, back top
        // front face, side face, top face
        poly(cv, p, 0xFF161616, L, T, F, T, F, B, L, B);
        poly(cv, p, 0xFF0A0A0A, F, T, R, tt, R, B - (T - tt), F, B);
        poly(cv, p, 0xFF3A3A3A, L, T, L + (R - F), tt, R, tt, F, T);
        // gold belt
        float b1 = T + 0.10f * s, b2 = T + 0.17f * s;
        poly(cv, p, 0xFFFFC107, L, b1, F, b1, F, b2, L, b2);
        poly(cv, p, 0xFFE0A800, F, b1, R, b1 - (T - tt), R, b2 - (T - tt), F, b2);
        // door
        poly(cv, p, 0xFFFFC107, cx - 0.12f * s, cy + 0.14f * s, cx + 0.06f * s, cy + 0.14f * s, cx + 0.06f * s, B, cx - 0.12f * s, B);
        p.setStyle(old);
    }
}
