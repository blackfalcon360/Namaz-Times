# Namaz Times 🕋

Prayer times, Qibla arrow and Kaaba map — in **Urdu, English and Arabic** (Urdu/Arabic are right-to-left). Portrait only. Works offline.

## Tabs
- **Times / اوقات / المواقيت** — Fajr, Sunrise, Dhuhr (Jumu'ah on Fridays), Asr, Maghrib, Isha. The next prayer is highlighted with a live countdown. Gregorian date, Hijri date (Umm al-Qura; the Islamic day changes at Maghrib; ±2 day adjustment in Settings) and your place name.
- **Qibla / قبلہ / القبلة** — compass dial with a long green arrow pointing to the Kaaba icon on the ring. 🧭 ON: the dial turns with the phone, the arrow turns gold and the phone vibrates when you face the Qibla. OFF: static dial, North at the top.
- **Map / نقشہ / الخريطة** — offline world grid centred on you with the shortest path and distance to the Kaaba; or tap **Online map** for real OpenStreetMap streets with the Kaaba 🕋 and the great-circle line. **Open in Maps** opens the Kaaba in your maps app.
- **Settings** — language, calculation method (MWL, ISNA, Egyptian, Umm al-Qura, Karachi), Asr (Standard / Hanafi), 12/24-hour, Hijri adjustment, refresh location.

Defaults: Urdu, and Karachi method + Hanafi Asr if the phone region is Pakistan, India, Bangladesh or Afghanistan (otherwise Muslim World League + Standard). Change them in Settings.

Prayer times are calculated on the phone (no internet). For ihtiyat, also check your local masjid's timetable. Needs the Location permission; Android 7.0 (API 24) or newer.

Brought to you By: Black Falcon 🦅

## Build
Push to GitHub -> Actions -> "Build APK" -> download `NamazTimes-APK`.
Local: `gradle assembleDebug` (JDK 17, Gradle 8.7+).
