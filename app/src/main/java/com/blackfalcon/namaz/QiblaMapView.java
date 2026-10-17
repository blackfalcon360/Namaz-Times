package com.blackfalcon.namaz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Offline map: azimuthal-equidistant world grid centred on you; the straight line is the great-circle path to the Kaaba. */
public class QiblaMapView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double lat = 0, lon = 0;
    private boolean has = false;
    private String youLabel = "You", kaabaLabel = "Kaaba", kmLabel = "km";

    public QiblaMapView(Context c) { super(c); }

    public void setData(double lat, double lon, boolean has, String you, String kaaba, String km) {
        this.lat = lat; this.lon = lon; this.has = has; youLabel = you; kaabaLabel = kaaba; kmLabel = km;
        invalidate();
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    /** returns {x, y, c} where c is the angular distance (rad) from the centre; x to the east, y to the north */
    private double[] proj(double la, double lo) {
        double p0 = Math.toRadians(lat), p1 = Math.toRadians(la), dl = Math.toRadians(lo - lon);
        double cosc = Math.sin(p0) * Math.sin(p1) + Math.cos(p0) * Math.cos(p1) * Math.cos(dl);
        double c = Math.acos(Math.max(-1, Math.min(1, cosc)));
        double k = c < 1e-9 ? 1 : c / Math.sin(c);
        double x = k * Math.cos(p1) * Math.sin(dl);
        double y = k * (Math.cos(p0) * Math.sin(p1) - Math.sin(p0) * Math.cos(p1) * Math.cos(dl));
        return new double[]{x, y, c};
    }

    private static String km(double v) { return String.format("%,d", Math.round(v)); }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, R = Math.min(w, h) / 2f - dp(10);
        cv.drawColor(0xFF000000);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF06122A);
        cv.drawCircle(cx, cy, R, p);

        if (!has) {
            p.setColor(0xFF9E9E9E);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(15));
            cv.drawText("\u2026", cx, cy, p);
            return;
        }

        double dist = PrayerCalc.angle(lat, lon, PrayerCalc.KAABA_LAT, PrayerCalc.KAABA_LON);
        double maxC = Math.max(0.12, Math.min(Math.PI, dist * 1.25));
        float S = (float) (R / maxC);

        // graticule (parallels and meridians)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(1));
        p.setColor(0xFF1E3A6B);
        for (int la = -60; la <= 60; la += 30) drawLine(cv, cx, cy, S, maxC, la, true);
        for (int lo = -180; lo < 180; lo += 30) drawLine(cv, cx, cy, S, maxC, lo, false);
        // equator and prime meridian a bit brighter
        p.setColor(0xFF2F5BA8);
        drawLine(cv, cx, cy, S, maxC, 0, true);
        drawLine(cv, cx, cy, S, maxC, 0, false);

        // distance rings
        p.setColor(0xFF3A3A60);
        p.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(5)}, 0));
        for (int i = 1; i <= 3; i++) cv.drawCircle(cx, cy, R * i / 3f * 0.8f, p);
        p.setPathEffect(null);
        p.setStrokeWidth(dp(2));
        p.setColor(0xFF3A3A60);
        cv.drawCircle(cx, cy, R, p);

        double[] k = proj(PrayerCalc.KAABA_LAT, PrayerCalc.KAABA_LON);
        float kx = cx + (float) (k[0] * S), ky = cy - (float) (k[1] * S);

        // path to the Kaaba
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(3));
        p.setColor(0xFF2ECC71);
        p.setPathEffect(new DashPathEffect(new float[]{dp(10), dp(6)}, 0));
        cv.drawLine(cx, cy, kx, ky, p);
        p.setPathEffect(null);

        // arrow head at the Kaaba end
        double ang = Math.atan2(ky - cy, kx - cx);
        float ax = kx - (float) Math.cos(ang) * dp(26), ay = ky - (float) Math.sin(ang) * dp(26);
        Path head = new Path();
        head.moveTo(ax + (float) Math.cos(ang) * dp(2), ay + (float) Math.sin(ang) * dp(2));
        head.lineTo(ax - (float) Math.cos(ang) * dp(14) + (float) Math.sin(ang) * dp(9), ay - (float) Math.sin(ang) * dp(14) - (float) Math.cos(ang) * dp(9));
        head.lineTo(ax - (float) Math.cos(ang) * dp(14) - (float) Math.sin(ang) * dp(9), ay - (float) Math.sin(ang) * dp(14) + (float) Math.cos(ang) * dp(9));
        head.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF2ECC71);
        cv.drawPath(head, p);

        // Kaaba marker
        p.setColor(0xFF02030A);
        cv.drawCircle(kx, ky, dp(24), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(0xFFFFC107);
        cv.drawCircle(kx, ky, dp(24), p);
        p.setStyle(Paint.Style.FILL);
        KaabaIcon.draw(cv, p, kx, ky, dp(32));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(13));
        p.setColor(0xFFFFC107);
        cv.drawText(kaabaLabel, kx, ky + dp(40), p);

        // you
        p.setColor(0xFFFFFFFF);
        cv.drawCircle(cx, cy, dp(7), p);
        p.setColor(0xFF4FC3F7);
        cv.drawCircle(cx, cy, dp(4), p);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(dp(13));
        cv.drawText(youLabel, cx, cy + dp(24), p);

        // distance label
        p.setColor(0xFF2ECC71);
        p.setTextSize(dp(14));
        cv.drawText(km(dist * 6371.0) + " " + kmLabel, (cx + kx) / 2f, (cy + ky) / 2f - dp(10), p);

        // north
        p.setColor(0xFFE57373);
        p.setTextSize(dp(15));
        cv.drawText("N \u2191", cx, cy - R + dp(18), p);
    }

    private void drawLine(Canvas cv, float cx, float cy, float S, double maxC, int fixed, boolean parallel) {
        Path path = new Path();
        boolean pen = false;
        for (int t = -180; t <= 180; t += 3) {
            double la = parallel ? fixed : t / 180.0 * 88.0;
            double lo = parallel ? t : fixed;
            double[] q = proj(la, lo);
            if (q[2] > maxC) { pen = false; continue; }
            float x = cx + (float) (q[0] * S), y = cy - (float) (q[1] * S);
            if (!pen) { path.moveTo(x, y); pen = true; } else path.lineTo(x, y);
        }
        cv.drawPath(path, p);
    }
}
