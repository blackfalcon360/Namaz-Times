package com.blackfalcon.namaz;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements SensorEventListener {

    private static final int GREEN = 0xFF2ECC71, GRAY = 0xFF9E9E9E, CARD = 0xFF0C0C1E, EDGE = 0xFF3A3A60,
            AMBER = 0xFFFFB300, BLUE = 0xFF4FC3F7, GOLD = 0xFFFFC107, RED = 0xFFE57373;
    private static final int[] ORDER = {PrayerCalc.FAJR, PrayerCalc.SUNRISE, PrayerCalc.DHUHR, PrayerCalc.ASR, PrayerCalc.MAGHRIB, PrayerCalc.ISHA};

    private SharedPreferences prefs;
    private LocationManager lm;
    private SensorManager sm;
    private Sensor rotSensor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // settings
    private int lang, method, hijriAdj, tab = 0;
    private boolean hanafi, h24, compassOn;

    // location
    private double lat, lon;
    private boolean hasLoc = false, running = false;
    private String placeName = "";
    private double geoLat = 999, geoLon = 999;
    private boolean geoBusy = false;

    // times
    private final long[] pm = new long[6];
    private long tomorrowFajr = -1;
    private int computedDay = -1;
    private boolean dirty = true;

    // compass
    private float heading = 0f, declination = 0f, sinAvg = 0f, cosAvg = 1f;
    private boolean firstSample = true, wasAligned = false;
    private final float[] rotMat = new float[9], remapped = new float[9], orient = new float[3];

    // views
    private LinearLayout page0, page1, page2;
    private ScrollView page3;
    private final TextView[] tabs = new TextView[4];
    private TextView dateTv, hijriTv, placeTv, nextNameTv, nextLeftTv;
    private final TextView[] nameTvs = new TextView[6], timeTvs = new TextView[6];
    private final LinearLayout[] rows = new LinearLayout[6];
    private QiblaView qiblaView;
    private TextView qiblaTv, distTv, hintTv, compassBtn;
    private QiblaMapView mapView;
    private FrameLayout mapFrame;
    private WebView web;
    private boolean onlineMap = false;
    private TextView mapToggle;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String t(String k) { return Lang.t(lang, k); }

    // ------------------------------------------------------------ callbacks
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateTimes();
            handler.postDelayed(this, 1000);
        }
    };

    private final LocationListener locListener = new LocationListener() {
        @Override public void onLocationChanged(Location l) { setLocation(l.getLatitude(), l.getLongitude(), l.getAltitude()); }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) { }
        @Override public void onStatusChanged(String p, int s, Bundle e) { }
    };

    // ------------------------------------------------------------- lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("namaz", MODE_PRIVATE);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        String country = Locale.getDefault().getCountry();
        boolean southAsia = country.equals("PK") || country.equals("IN") || country.equals("BD") || country.equals("AF");
        lang = prefs.getInt("lang", Lang.UR);
        method = prefs.getInt("method", southAsia ? PrayerCalc.KARACHI : PrayerCalc.MWL);
        hanafi = prefs.getBoolean("hanafi", southAsia);
        h24 = prefs.getBoolean("h24", false);
        hijriAdj = prefs.getInt("hadj", 0);
        compassOn = prefs.getBoolean("compass", true) && rotSensor != null;
        tab = prefs.getInt("tab", 0);

        if (prefs.contains("lat")) {
            lat = Double.longBitsToDouble(prefs.getLong("lat", 0));
            lon = Double.longBitsToDouble(prefs.getLong("lon", 0));
            hasLoc = true;
            placeName = prefs.getString("place", "");
        }
        buildUi();
        if (!hasPerm()) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }

    @Override protected void onResume() { super.onResume(); start(); }
    @Override protected void onPause() { super.onPause(); stop(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (web != null) { web.destroy(); web = null; }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        start();
    }

    private boolean hasPerm() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void start() {
        if (running) return;
        running = true;
        handler.post(ticker);
        registerCompass();
        if (hasPerm()) requestLocation();
    }

    private void requestLocation() {
        try {
            Location a = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location b = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location best = a != null && (b == null || a.getTime() >= b.getTime()) ? a : b;
            if (best != null) setLocation(best.getLatitude(), best.getLongitude(), best.getAltitude());
            lm.removeUpdates(locListener);
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 500, locListener);
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 500, locListener);
        } catch (SecurityException | IllegalArgumentException e) { /* shown by the "waiting for location" text */ }
    }

    private void stop() {
        handler.removeCallbacks(ticker);
        try { lm.removeUpdates(locListener); } catch (Exception e) { }
        sm.unregisterListener(this);
        running = false;
    }

    private void setLocation(double la, double lo, double alt) {
        boolean first = !hasLoc;
        boolean moved = !hasLoc || PrayerCalc.angle(lat, lon, la, lo) * 6371.0 > 2.0;
        lat = la; lon = lo; hasLoc = true;
        declination = new GeomagneticField((float) la, (float) lo, (float) alt, System.currentTimeMillis()).getDeclination();
        if (moved || first) {
            dirty = true;
            prefs.edit().putLong("lat", Double.doubleToLongBits(la)).putLong("lon", Double.doubleToLongBits(lo)).apply();
            geocode();
            refreshQibla();
            refreshMap();
        }
        updateTimes();
    }

    private void geocode() {
        if (geoBusy || PrayerCalc.angle(lat, lon, geoLat, geoLon) * 6371.0 < 10.0) return;
        geoBusy = true;
        final double a = lat, b = lon;
        final Locale loc = new Locale(lang == Lang.UR ? "ur" : (lang == Lang.AR ? "ar" : "en"));
        io.execute(new Runnable() {
            @Override public void run() {
                String name = "";
                try {
                    if (Geocoder.isPresent()) {
                        List<Address> l = new Geocoder(MainActivity.this, loc).getFromLocation(a, b, 1);
                        if (l != null && !l.isEmpty()) {
                            Address ad = l.get(0);
                            String city = ad.getLocality() != null ? ad.getLocality() : (ad.getSubAdminArea() != null ? ad.getSubAdminArea() : ad.getAdminArea());
                            name = (city != null ? city : "") + (ad.getCountryName() != null ? (city != null ? ", " : "") + ad.getCountryName() : "");
                            geoLat = a; geoLon = b;
                        }
                    }
                } catch (Exception e) { /* no internet / no geocoder: coordinates are shown instead */ }
                final String n = name;
                geoBusy = false;
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (n.length() > 0) { placeName = n; prefs.edit().putString("place", n).apply(); }
                        updateTimes();
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------- prayer times
    private void recompute() {
        Calendar c = Calendar.getInstance();
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
        computedDay = y * 10000 + m * 100 + d;
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long mid = c.getTimeInMillis();
        double tz = TimeZone.getDefault().getOffset(mid + 12 * 3600000L) / 3600000.0;
        double[] h = PrayerCalc.times(y, m, d, lat, lon, tz, method, hanafi);
        for (int i = 0; i < 6; i++) pm[i] = Double.isNaN(h[i]) ? -1 : mid + Math.round(h[i] * 60) * 60000L;

        c.add(Calendar.DAY_OF_MONTH, 1);
        long mid2 = c.getTimeInMillis();
        double tz2 = TimeZone.getDefault().getOffset(mid2 + 12 * 3600000L) / 3600000.0;
        double[] h2 = PrayerCalc.times(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), lat, lon, tz2, method, hanafi);
        tomorrowFajr = Double.isNaN(h2[0]) ? -1 : mid2 + Math.round(h2[0] * 60) * 60000L;
        dirty = false;
    }

    private String fmtTime(long ms) {
        if (ms < 0) return "--:--";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        int hh = c.get(Calendar.HOUR_OF_DAY), mm = c.get(Calendar.MINUTE);
        if (h24) return String.format(Locale.US, "%02d:%02d", hh, mm);
        int h12 = hh % 12 == 0 ? 12 : hh % 12;
        return String.format(Locale.US, "%d:%02d %s", h12, mm, t(hh < 12 ? "am" : "pm"));
    }

    private String prayerName(int idx, boolean friday) {
        switch (idx) {
            case PrayerCalc.FAJR: return t("fajr");
            case PrayerCalc.SUNRISE: return t("sunrise");
            case PrayerCalc.DHUHR: return t(friday ? "jumuah" : "dhuhr");
            case PrayerCalc.ASR: return t("asr");
            case PrayerCalc.MAGHRIB: return t("maghrib");
            default: return t("isha");
        }
    }

    private String hijriText(long now) {
        try {
            long ms = now + hijriAdj * 86400000L;
            if (pm[PrayerCalc.MAGHRIB] > 0 && now >= pm[PrayerCalc.MAGHRIB]) ms += 86400000L;   // the Islamic day begins at Maghrib
            android.icu.util.IslamicCalendar ic = new android.icu.util.IslamicCalendar();
            ic.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
            ic.setTimeInMillis(ms);
            String[] mn = Lang.pick(lang, Lang.HIJRI_EN, Lang.HIJRI_UR, Lang.HIJRI_AR);
            return ic.get(android.icu.util.Calendar.DAY_OF_MONTH) + " " + mn[ic.get(android.icu.util.Calendar.MONTH)] + " " + ic.get(android.icu.util.Calendar.YEAR) + (lang == Lang.EN ? " AH" : " ھ");
        } catch (Exception e) { return ""; }
    }

    private void updateTimes() {
        if (page0 == null) return;
        Calendar now = Calendar.getInstance();
        long nowMs = now.getTimeInMillis();
        int today = now.get(Calendar.YEAR) * 10000 + (now.get(Calendar.MONTH) + 1) * 100 + now.get(Calendar.DAY_OF_MONTH);

        String[] wd = Lang.pick(lang, Lang.WEEK_EN, Lang.WEEK_UR, Lang.WEEK_AR);
        String[] mo = Lang.pick(lang, Lang.MON_EN, Lang.MON_UR, Lang.MON_AR);
        dateTv.setText(wd[now.get(Calendar.DAY_OF_WEEK) - 1] + "\u060C " + now.get(Calendar.DAY_OF_MONTH) + " " + mo[now.get(Calendar.MONTH)] + " " + now.get(Calendar.YEAR));

        if (!hasLoc) {
            placeTv.setText(hasPerm() ? t("wait_loc") : t("need_perm"));
            placeTv.setTextColor(hasPerm() ? AMBER : RED);
            hijriTv.setText(hijriText(nowMs));
            nextNameTv.setText("--");
            nextLeftTv.setText("");
            return;
        }
        if (dirty || computedDay != today) recompute();

        placeTv.setTextColor(GRAY);
        placeTv.setText((placeName.length() > 0 ? placeName + "  \u2022  " : "") + String.format(Locale.US, "%.4f, %.4f", lat, lon));
        hijriTv.setText(hijriText(nowMs));

        boolean friday = now.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
        int next = -1;
        long nextMs = -1;
        for (int i : new int[]{PrayerCalc.FAJR, PrayerCalc.DHUHR, PrayerCalc.ASR, PrayerCalc.MAGHRIB, PrayerCalc.ISHA}) {
            if (pm[i] > nowMs) { next = i; nextMs = pm[i]; break; }
        }
        if (next < 0) { next = PrayerCalc.FAJR; nextMs = tomorrowFajr; }

        for (int k = 0; k < 6; k++) {
            int idx = ORDER[k];
            nameTvs[k].setText(prayerName(idx, friday));
            timeTvs[k].setText(fmtTime(pm[idx]));
            boolean hl = idx == next && nextMs == pm[idx];
            nameTvs[k].setTextColor(hl ? GREEN : Color.WHITE);
            timeTvs[k].setTextColor(hl ? GREEN : Color.WHITE);
            nameTvs[k].setTypeface(hl ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            timeTvs[k].setTypeface(hl ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            rows[k].setBackground(box(hl ? 0xFF10281C : CARD, hl ? GREEN : EDGE, 12));
        }
        nextNameTv.setText(t("next") + ":  " + prayerName(next, friday) + "  " + fmtTime(nextMs));
        if (nextMs > 0) {
            long s = Math.max(0, (nextMs - nowMs) / 1000);
            nextLeftTv.setText(t("left") + "  " + String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        } else nextLeftTv.setText("");
    }

    // ------------------------------------------------------------- compass / qibla
    private void registerCompass() {
        firstSample = true;
        if (compassOn && rotSensor != null) sm.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_UI);
        refreshQibla();
    }

    private void toggleCompass() {
        if (rotSensor == null) {
            Toast.makeText(this, "No compass sensor", Toast.LENGTH_SHORT).show();
            return;
        }
        compassOn = !compassOn;
        prefs.edit().putBoolean("compass", compassOn).apply();
        if (compassOn) registerCompass(); else { sm.unregisterListener(this); refreshQibla(); }
        updateCompassBtn();
    }

    private void updateCompassBtn() {
        compassBtn.setText("\uD83E\uDDED  " + t(compassOn ? "on" : "off"));
        compassBtn.setTextColor(compassOn ? GREEN : GRAY);
        compassBtn.setBackground(box(CARD, compassOn ? GREEN : EDGE, 10));
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (!compassOn || e.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        SensorManager.getRotationMatrixFromVector(rotMat, e.values);
        float[] m = rotMat;
        if (Math.abs(rotMat[8]) < 0.7f) {      // phone held upright
            SensorManager.remapCoordinateSystem(rotMat, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped);
            m = remapped;
        }
        SensorManager.getOrientation(m, orient);
        double r = Math.toRadians(Math.toDegrees(orient[0]) + declination);
        if (firstSample) { sinAvg = (float) Math.sin(r); cosAvg = (float) Math.cos(r); firstSample = false; }
        else { sinAvg += 0.15f * ((float) Math.sin(r) - sinAvg); cosAvg += 0.15f * ((float) Math.cos(r) - cosAvg); }
        heading = (float) ((Math.toDegrees(Math.atan2(sinAvg, cosAvg)) + 360) % 360);
        refreshQibla();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void refreshQibla() {
        if (qiblaView == null) return;
        if (!hasLoc) {
            qiblaView.setState(heading, compassOn, 0, false, false);
            qiblaTv.setText("");
            distTv.setText(hasPerm() ? t("wait_loc") : t("need_perm"));
            hintTv.setText("");
            return;
        }
        float q = (float) PrayerCalc.qibla(lat, lon);
        float diff = ((q - heading + 540f) % 360f) - 180f;
        boolean aligned = compassOn && Math.abs(diff) <= 3f;
        qiblaView.setState(heading, compassOn, q, true, aligned);
        qiblaTv.setText(t("qibla_dir") + ":  " + Math.round(q) + "\u00B0 " + t("deg_from_n"));
        distTv.setText(t("dist") + ":  " + String.format(Locale.US, "%,d", Math.round(PrayerCalc.distanceKm(lat, lon))) + " " + t("km"));
        hintTv.setText(aligned ? t("aligned") : (compassOn ? t("turn") : t("static_hint")));
        hintTv.setTextColor(aligned ? GOLD : GRAY);
        if (aligned && !wasAligned) qiblaView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        wasAligned = aligned;
    }

    // ------------------------------------------------------------- map
    private void refreshMap() {
        if (mapView == null) return;
        mapView.setData(lat, lon, hasLoc, t("you"), t("kaaba"), t("km"));
        if (onlineMap) loadWeb();
    }

    private String mapHtml() {
        String user = hasLoc ? "[" + lat + "," + lon + "]" : "null";
        return "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css'/>"
                + "<script src='https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js'></script>"
                + "<style>html,body,#m{height:100%;margin:0;background:#06122A}.k{font-size:34px;line-height:34px;text-align:center}</style></head><body><div id='m'></div><script>"
                + "var u=" + user + ",k=[21.4225,39.8262];"
                + "var m=L.map('m').setView(k,4);"
                + "L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'&copy; OpenStreetMap'}).addTo(m);"
                + "L.marker(k,{icon:L.divIcon({className:'',html:'<div class=\"k\">\uD83D\uDD4B</div>',iconSize:[34,34],iconAnchor:[17,17]})}).addTo(m);"
                + "if(u){"
                + "var r=Math.PI/180,p1=u[0]*r,l1=u[1]*r,p2=k[0]*r,l2=k[1]*r;"
                + "var d=2*Math.asin(Math.sqrt(Math.pow(Math.sin((p1-p2)/2),2)+Math.cos(p1)*Math.cos(p2)*Math.pow(Math.sin((l1-l2)/2),2)));"
                + "var pts=[];if(d<1e-6){pts=[u,k];}else{for(var i=0;i<=40;i++){var f=i/40,A=Math.sin((1-f)*d)/Math.sin(d),B=Math.sin(f*d)/Math.sin(d);"
                + "var x=A*Math.cos(p1)*Math.cos(l1)+B*Math.cos(p2)*Math.cos(l2),y=A*Math.cos(p1)*Math.sin(l1)+B*Math.cos(p2)*Math.sin(l2),z=A*Math.sin(p1)+B*Math.sin(p2);"
                + "pts.push([Math.atan2(z,Math.sqrt(x*x+y*y))/r,Math.atan2(y,x)/r]);}}"
                + "L.polyline(pts,{color:'#2ECC71',weight:4,dashArray:'10,8'}).addTo(m);"
                + "L.circleMarker(u,{radius:8,color:'#fff',weight:3,fillColor:'#4FC3F7',fillOpacity:1}).addTo(m);"
                + "m.fitBounds([u,k],{padding:[50,50]});}"
                + "</script></body></html>";
    }

    private void loadWeb() {
        if (web == null) {
            web = new WebView(this);
            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            mapFrame.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        web.loadDataWithBaseURL("https://localhost/", mapHtml(), "text/html", "UTF-8", null);
    }

    private void setOnlineMap(boolean on) {
        onlineMap = on;
        if (on) loadWeb();
        if (web != null) web.setVisibility(on ? View.VISIBLE : View.GONE);
        mapView.setVisibility(on ? View.GONE : View.VISIBLE);
        mapToggle.setText(t(on ? "offline" : "online"));
    }

    // ------------------------------------------------------------- UI helpers
    private GradientDrawable box(int fill, int edge, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), edge);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView tv(String text, int size, int color) {
        TextView x = new TextView(this);
        x.setText(text);
        x.setTextSize(size);
        x.setTextColor(color);
        return x;
    }

    private TextView button(String text) {
        TextView b = tv(text, 15, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackground(box(0xFF1C1C3A, BLUE, 10));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout.LayoutParams weight(int left, int right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(left, 0, right, 0);
        return lp;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(10), dp(28), dp(10), dp(8));
        root.setLayoutDirection(lang == Lang.EN ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);

        TextView title = tv("\uD83D\uDD4B  " + t("app"), 20, GOLD);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding(0, dp(8), 0, dp(8));
        String[] names = {t("tab_times"), t("tab_qibla"), t("tab_map"), t("tab_set")};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView x = tv(names[i], 14, Color.WHITE);
            x.setGravity(Gravity.CENTER);
            x.setPadding(0, dp(9), 0, dp(9));
            x.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { tab = idx; prefs.edit().putInt("tab", tab).apply(); showTab(); } });
            tabRow.addView(x, weight(dp(2), dp(2)));
            tabs[i] = x;
        }
        root.addView(tabRow);

        FrameLayout frame = new FrameLayout(this);
        frame.addView(buildTimesPage());
        frame.addView(buildQiblaPage());
        frame.addView(buildMapPage());
        frame.addView(buildSettingsPage());
        root.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView credit = tv("By: Black Falcon \uD83E\uDD85", 12, GRAY);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(6), 0, 0);
        root.addView(credit);

        setContentView(root);
        showTab();
        updateTimes();
        refreshQibla();
        refreshMap();
        updateCompassBtn();
    }

    private View buildTimesPage() {
        ScrollView sv = new ScrollView(this);
        page0 = new LinearLayout(this);
        page0.setOrientation(LinearLayout.VERTICAL);
        sv.addView(page0);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setBackground(box(CARD, EDGE, 12));
        head.setPadding(dp(12), dp(10), dp(12), dp(10));
        dateTv = tv("", 16, Color.WHITE);
        dateTv.setTypeface(Typeface.DEFAULT_BOLD);
        hijriTv = tv("", 15, GOLD);
        placeTv = tv("", 12, GRAY);
        placeTv.setPadding(0, dp(4), 0, 0);
        head.addView(dateTv);
        head.addView(hijriTv);
        head.addView(placeTv);
        page0.addView(head);

        LinearLayout next = new LinearLayout(this);
        next.setOrientation(LinearLayout.VERTICAL);
        next.setGravity(Gravity.CENTER);
        next.setBackground(box(0xFF10281C, GREEN, 14));
        next.setPadding(dp(12), dp(14), dp(12), dp(14));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = dp(8);
        next.setLayoutParams(nl);
        nextNameTv = tv("", 18, Color.WHITE);
        nextNameTv.setTypeface(Typeface.DEFAULT_BOLD);
        nextNameTv.setGravity(Gravity.CENTER);
        nextLeftTv = tv("", 26, GREEN);
        nextLeftTv.setTypeface(Typeface.DEFAULT_BOLD);
        nextLeftTv.setGravity(Gravity.CENTER);
        next.addView(nextNameTv);
        next.addView(nextLeftTv);
        page0.addView(next);

        for (int k = 0; k < 6; k++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = dp(6);
            row.setLayoutParams(rl);
            row.setBackground(box(CARD, EDGE, 12));
            nameTvs[k] = tv("", 18, Color.WHITE);
            timeTvs[k] = tv("", 18, Color.WHITE);
            timeTvs[k].setGravity(Gravity.END);
            row.addView(nameTvs[k], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(timeTvs[k], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            rows[k] = row;
            page0.addView(row);
        }
        TextView note = tv(t("note"), 12, GRAY);
        note.setPadding(dp(4), dp(10), dp(4), dp(8));
        page0.addView(note);
        pageViews[0] = sv;
        return sv;
    }

    private final View[] pageViews = new View[4];

    private View buildQiblaPage() {
        page1 = new LinearLayout(this);
        page1.setOrientation(LinearLayout.VERTICAL);

        FrameLayout box = new FrameLayout(this);
        qiblaView = new QiblaView(this);
        box.addView(qiblaView);
        compassBtn = tv("", 14, Color.WHITE);
        compassBtn.setGravity(Gravity.CENTER);
        compassBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        compassBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleCompass(); } });
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        cl.setMargins(0, dp(2), dp(2), 0);
        box.addView(compassBtn, cl);
        page1.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        qiblaTv = tv("", 16, Color.WHITE);
        qiblaTv.setGravity(Gravity.CENTER);
        qiblaTv.setTypeface(Typeface.DEFAULT_BOLD);
        distTv = tv("", 14, GOLD);
        distTv.setGravity(Gravity.CENTER);
        hintTv = tv("", 13, GRAY);
        hintTv.setGravity(Gravity.CENTER);
        hintTv.setPadding(0, dp(4), 0, 0);
        page1.addView(qiblaTv);
        page1.addView(distTv);
        page1.addView(hintTv);
        pageViews[1] = page1;
        return page1;
    }

    private View buildMapPage() {
        page2 = new LinearLayout(this);
        page2.setOrientation(LinearLayout.VERTICAL);

        LinearLayout btns = new LinearLayout(this);
        mapToggle = tv(t("online"), 14, Color.WHITE);
        mapToggle.setGravity(Gravity.CENTER);
        mapToggle.setBackground(box(0xFF1C1C3A, BLUE, 10));
        mapToggle.setPadding(dp(8), dp(10), dp(8), dp(10));
        mapToggle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setOnlineMap(!onlineMap); } });
        TextView open = tv(t("open_maps"), 14, Color.WHITE);
        open.setGravity(Gravity.CENTER);
        open.setBackground(box(0xFF1C1C3A, GOLD, 10));
        open.setPadding(dp(8), dp(10), dp(8), dp(10));
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + PrayerCalc.KAABA_LAT + "," + PrayerCalc.KAABA_LON + "?q=" + PrayerCalc.KAABA_LAT + "," + PrayerCalc.KAABA_LON + "(" + Uri.encode(t("kaaba")) + ")")));
                } catch (Exception e) { Toast.makeText(MainActivity.this, "No maps app found", Toast.LENGTH_SHORT).show(); }
            }
        });
        btns.addView(mapToggle, weight(dp(2), dp(2)));
        btns.addView(open, weight(dp(2), dp(2)));
        page2.addView(btns);

        mapFrame = new FrameLayout(this);
        mapView = new QiblaMapView(this);
        mapFrame.addView(mapView);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        ml.topMargin = dp(6);
        page2.addView(mapFrame, ml);

        TextView note = tv(t("map_note"), 12, GRAY);
        note.setPadding(dp(4), dp(6), dp(4), 0);
        page2.addView(note);
        pageViews[2] = page2;
        return page2;
    }

    private TextView chip(String text, boolean selected, View.OnClickListener l) {
        TextView c = tv(text, 14, selected ? Color.WHITE : GRAY);
        c.setGravity(Gravity.CENTER);
        c.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        c.setBackground(box(selected ? 0xFF1C1C3A : CARD, selected ? GREEN : EDGE, 10));
        c.setPadding(dp(6), dp(10), dp(6), dp(10));
        c.setOnClickListener(l);
        return c;
    }

    private void applyAndRebuild() { recreate(); }

    private View buildSettingsPage() {
        page3 = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(4), dp(4), dp(12));
        page3.addView(box);

        // language
        box.addView(tv(t("lang"), 16, AMBER));
        LinearLayout lr = new LinearLayout(this);
        lr.setPadding(0, dp(4), 0, dp(12));
        String[] ln = {"English", "اردو", "العربية"};
        for (int i = 0; i < 3; i++) {
            final int li = i;
            lr.addView(chip(ln[i], lang == i, new View.OnClickListener() {
                @Override public void onClick(View v) { prefs.edit().putInt("lang", li).apply(); applyAndRebuild(); }
            }), weight(dp(2), dp(2)));
        }
        box.addView(lr);

        // method
        box.addView(tv(t("method"), 16, AMBER));
        TextView mb = button(t("m" + method));
        mb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String[] items = new String[5];
                for (int i = 0; i < 5; i++) items[i] = t("m" + i);
                new AlertDialog.Builder(MainActivity.this).setTitle(t("method")).setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { prefs.edit().putInt("method", which).apply(); applyAndRebuild(); }
                }).show();
            }
        });
        box.addView(mb);

        // asr
        TextView asrTitle = tv(t("asr_m"), 16, AMBER);
        asrTitle.setPadding(0, dp(14), 0, 0);
        box.addView(asrTitle);
        LinearLayout ar = new LinearLayout(this);
        ar.setPadding(0, dp(4), 0, dp(12));
        ar.addView(chip(t("asr_std"), !hanafi, new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.edit().putBoolean("hanafi", false).apply(); applyAndRebuild(); }
        }), weight(dp(2), dp(2)));
        ar.addView(chip(t("asr_han"), hanafi, new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.edit().putBoolean("hanafi", true).apply(); applyAndRebuild(); }
        }), weight(dp(2), dp(2)));
        box.addView(ar);

        // time format
        box.addView(tv(t("tf"), 16, AMBER));
        LinearLayout fr = new LinearLayout(this);
        fr.setPadding(0, dp(4), 0, dp(12));
        fr.addView(chip(t("h12"), !h24, new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.edit().putBoolean("h24", false).apply(); applyAndRebuild(); }
        }), weight(dp(2), dp(2)));
        fr.addView(chip(t("h24"), h24, new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.edit().putBoolean("h24", true).apply(); applyAndRebuild(); }
        }), weight(dp(2), dp(2)));
        box.addView(fr);

        // hijri adjustment
        box.addView(tv(t("hadj"), 16, AMBER));
        LinearLayout hr = new LinearLayout(this);
        hr.setGravity(Gravity.CENTER_VERTICAL);
        hr.setPadding(0, dp(4), 0, dp(12));
        TextView minus = chip("\u2212", false, new View.OnClickListener() {
            @Override public void onClick(View v) { if (hijriAdj > -2) { prefs.edit().putInt("hadj", hijriAdj - 1).apply(); applyAndRebuild(); } }
        });
        TextView plus = chip("+", false, new View.OnClickListener() {
            @Override public void onClick(View v) { if (hijriAdj < 2) { prefs.edit().putInt("hadj", hijriAdj + 1).apply(); applyAndRebuild(); } }
        });
        TextView val = tv((hijriAdj > 0 ? "+" : "") + hijriAdj + " " + t("days"), 16, Color.WHITE);
        val.setGravity(Gravity.CENTER);
        hr.addView(minus, weight(dp(2), dp(2)));
        hr.addView(val, weight(dp(2), dp(2)));
        hr.addView(plus, weight(dp(2), dp(2)));
        box.addView(hr);

        // refresh location
        TextView rl = button(t("refresh"));
        rl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                geoLat = 999; geoLon = 999;
                if (!hasPerm()) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                else { requestLocation(); geocode(); Toast.makeText(MainActivity.this, t("wait_loc"), Toast.LENGTH_SHORT).show(); }
            }
        });
        box.addView(rl);

        TextView credit = tv("Brought to you By: Black Falcon \uD83E\uDD85", 13, GRAY);
        credit.setPadding(0, dp(18), 0, 0);
        box.addView(credit);
        pageViews[3] = page3;
        return page3;
    }

    private void showTab() {
        for (int i = 0; i < 4; i++) {
            pageViews[i].setVisibility(i == tab ? View.VISIBLE : View.GONE);
            tabs[i].setBackground(box(i == tab ? 0xFF1C1C3A : CARD, i == tab ? BLUE : EDGE, 10));
            tabs[i].setTypeface(i == tab ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }
}
