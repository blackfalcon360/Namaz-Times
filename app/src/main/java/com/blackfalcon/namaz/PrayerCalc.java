package com.blackfalcon.namaz;

/** Offline prayer-time and Qibla maths (same astronomical method as the well-known PrayTimes.org library). */
public final class PrayerCalc {
    private PrayerCalc() { }

    public static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    public static final int MWL = 0, ISNA = 1, EGYPT = 2, MAKKAH = 3, KARACHI = 4;

    /** {fajr angle, isha angle, isha minutes after maghrib (0 = use the angle)} */
    private static final double[][] METHODS = {
            {18, 17, 0}, {15, 15, 0}, {19.5, 17.5, 0}, {18.5, 0, 90}, {18, 18, 0}
    };

    public static final double KAABA_LAT = 21.4225, KAABA_LON = 39.8262;

    private static double rad(double d) { return Math.toRadians(d); }
    private static double sinD(double d) { return Math.sin(rad(d)); }
    private static double cosD(double d) { return Math.cos(rad(d)); }
    private static double fixAngle(double a) { a = a - 360.0 * Math.floor(a / 360.0); return a; }
    private static double fixHour(double a) { return a - 24.0 * Math.floor(a / 24.0); }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double a = Math.floor(y / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** {declination (deg), equation of time (hours)} */
    private static double[] sunPos(double jd) {
        double D = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * D);
        double q = fixAngle(280.459 + 0.98564736 * D);
        double L = fixAngle(q + 1.915 * sinD(g) + 0.020 * sinD(2 * g));
        double e = 23.439 - 0.00000036 * D;
        double ra = Math.toDegrees(Math.atan2(cosD(e) * sinD(L), cosD(L))) / 15.0;
        double eqt = q / 15.0 - fixHour(ra);
        if (eqt > 12) eqt -= 24;
        if (eqt < -12) eqt += 24;
        double decl = Math.toDegrees(Math.asin(sinD(e) * sinD(L)));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jd, double t) { return fixHour(12 - sunPos(jd + t)[1]); }

    private static double sunAngleTime(double jd, double angle, double t, double lat, boolean ccw) {
        double decl = sunPos(jd + t)[0];
        double noon = midDay(jd, t);
        double x = (-sinD(angle) - sinD(decl) * sinD(lat)) / (cosD(decl) * cosD(lat));
        if (x < -1 || x > 1) return Double.NaN;
        double tt = Math.toDegrees(Math.acos(x)) / 15.0;
        return noon + (ccw ? -tt : tt);
    }

    private static double asrTime(double jd, double factor, double t, double lat) {
        double decl = sunPos(jd + t)[0];
        double angle = -Math.toDegrees(Math.atan(1.0 / (factor + Math.tan(rad(Math.abs(lat - decl))))));
        return sunAngleTime(jd, angle, t, lat, false);
    }

    private static double or(double v, double def) { return Double.isNaN(v) ? def : v; }

    /**
     * Local clock hours (may be >24 or NaN) for {Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha}.
     * @param tz hours east of UTC for that date (daylight saving included)
     */
    public static double[] times(int y, int m, int d, double lat, double lon, double tz, int method, boolean hanafi) {
        double jd = julian(y, m, d) - lon / (15.0 * 24.0);
        double[] mp = METHODS[method];
        double fajr = 5, sunrise = 6, dhuhr = 12, asr = 13, sunset = 18, isha = 18;
        for (int it = 0; it < 2; it++) {
            fajr = sunAngleTime(jd, mp[0], or(fajr, 5) / 24.0, lat, true);
            sunrise = sunAngleTime(jd, 0.833, or(sunrise, 6) / 24.0, lat, true);
            dhuhr = midDay(jd, or(dhuhr, 12) / 24.0);
            asr = asrTime(jd, hanafi ? 2 : 1, or(asr, 13) / 24.0, lat);
            sunset = sunAngleTime(jd, 0.833, or(sunset, 18) / 24.0, lat, false);
            isha = mp[2] > 0 ? Double.NaN : sunAngleTime(jd, mp[1], or(isha, 18) / 24.0, lat, false);
        }
        double shift = tz - lon / 15.0;
        double maghrib = sunset + shift;
        double ishaOut = mp[2] > 0 ? maghrib + mp[2] / 60.0 : isha + shift;
        return new double[]{fajr + shift, sunrise + shift, dhuhr + shift, asr + shift, maghrib, ishaOut};
    }

    /** Initial great-circle bearing from the user to the Kaaba, degrees clockwise from true north. */
    public static double qibla(double lat, double lon) {
        double p1 = rad(lat), p2 = rad(KAABA_LAT), dl = rad(KAABA_LON - lon);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    /** Great-circle angle (radians) between two points. */
    public static double angle(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.sin(rad(lat2 - lat1) / 2), b = Math.sin(rad(lon2 - lon1) / 2);
        double h = a * a + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * b * b;
        return 2 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    public static double distanceKm(double lat, double lon) { return angle(lat, lon, KAABA_LAT, KAABA_LON) * 6371.0; }
}
