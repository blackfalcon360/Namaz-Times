package com.blackfalcon.namaz;

import java.util.HashMap;
import java.util.Map;

/** All texts in English, Urdu and Arabic. */
public final class Lang {
    private Lang() { }

    public static final int EN = 0, UR = 1, AR = 2;
    private static final Map<String, String[]> T = new HashMap<>();

    private static void a(String k, String en, String ur, String ar) { T.put(k, new String[]{en, ur, ar}); }

    static {
        a("app", "Namaz Times", "اوقاتِ نماز", "مواقيت الصلاة");
        a("fajr", "Fajr", "فجر", "الفجر");
        a("sunrise", "Sunrise", "طلوعِ آفتاب", "الشروق");
        a("dhuhr", "Dhuhr", "ظہر", "الظهر");
        a("jumuah", "Jumu'ah", "جمعہ", "الجمعة");
        a("asr", "Asr", "عصر", "العصر");
        a("maghrib", "Maghrib", "مغرب", "المغرب");
        a("isha", "Isha", "عشاء", "العشاء");
        a("next", "Next prayer", "اگلی نماز", "الصلاة القادمة");
        a("left", "Time left", "باقی وقت", "الوقت المتبقي");
        a("tab_times", "Times", "اوقات", "المواقيت");
        a("tab_qibla", "Qibla", "قبلہ", "القبلة");
        a("tab_map", "Map", "نقشہ", "الخريطة");
        a("tab_set", "Settings", "ترتیبات", "الإعدادات");
        a("qibla_dir", "Qibla direction", "قبلہ کی سمت", "اتجاه القبلة");
        a("dist", "Distance to Kaaba", "کعبہ تک فاصلہ", "المسافة إلى الكعبة");
        a("kaaba", "Kaaba", "کعبہ", "الكعبة");
        a("you", "You", "آپ", "أنت");
        a("on", "ON", "آن", "تشغيل");
        a("off", "OFF", "آف", "إيقاف");
        a("aligned", "You are facing the Qibla \u2714", "آپ کا رخ قبلے کی طرف ہے \u2714", "أنت تواجه القبلة \u2714");
        a("turn", "Turn the phone until the arrow points up", "فون کو اس طرح گھمائیں کہ تیر اوپر کی طرف ہو", "أدر الهاتف حتى يشير السهم إلى الأعلى");
        a("static_hint", "Compass is off: the arrow shows the Qibla bearing from North", "کمپاس بند ہے: تیر شمال سے قبلے کا زاویہ دکھا رہا ہے", "البوصلة متوقفة: السهم يُظهر زاوية القبلة من الشمال");
        a("lang", "Language", "زبان", "اللغة");
        a("method", "Calculation method", "حساب کا طریقہ", "طريقة الحساب");
        a("asr_m", "Asr calculation", "عصر کا حساب", "حساب العصر");
        a("asr_std", "Standard (Shafi'i, Maliki, Hanbali)", "جمہور (شافعی، مالکی، حنبلی)", "الجمهور (ظل المثل)");
        a("asr_han", "Hanafi", "حنفی", "الحنفي (ظل المثلين)");
        a("tf", "Time format", "وقت کا انداز", "تنسيق الوقت");
        a("h12", "12-hour", "12 گھنٹے", "12 ساعة");
        a("h24", "24-hour", "24 گھنٹے", "24 ساعة");
        a("hadj", "Hijri date adjustment", "ہجری تاریخ میں ردوبدل", "تعديل التاريخ الهجري");
        a("days", "day(s)", "دن", "يوم");
        a("refresh", "Refresh location", "مقام ریفریش کریں", "تحديث الموقع");
        a("need_perm", "Location permission is needed to calculate prayer times", "نماز کے اوقات کے لیے مقام کی اجازت ضروری ہے", "إذن الموقع مطلوب لحساب أوقات الصلاة");
        a("wait_loc", "Waiting for your location\u2026", "آپ کے مقام کا انتظار ہے\u2026", "بانتظار تحديد موقعك\u2026");
        a("online", "\uD83C\uDF10 Online map", "\uD83C\uDF10 آن لائن نقشہ", "\uD83C\uDF10 خريطة متصلة");
        a("offline", "\uD83D\uDDFA Offline map", "\uD83D\uDDFA آف لائن نقشہ", "\uD83D\uDDFA خريطة بدون إنترنت");
        a("open_maps", "Open in Maps", "نقشوں میں کھولیں", "فتح في الخرائط");
        a("map_note", "Offline map: world grid centred on you, the straight line is the shortest way to the Kaaba. The online map shows real streets (needs internet).",
                "آف لائن نقشہ: آپ کے مقام کو مرکز بنا کر دنیا کا جال؛ سیدھی لکیر کعبہ تک مختصر ترین راستہ ہے۔ آن لائن نقشہ اصل سڑکیں دکھاتا ہے (انٹرنیٹ چاہیے)۔",
                "الخريطة بدون إنترنت: شبكة العالم مركزها موقعك، والخط المستقيم هو أقصر طريق إلى الكعبة. الخريطة المتصلة تعرض الشوارع (تحتاج إنترنت).");
        a("note", "Times are calculated on the phone, offline. For ihtiyat, also check your local masjid's timetable.",
                "اوقات فون پر آف لائن حساب ہوتے ہیں۔ احتیاطاً اپنی مقامی مسجد کے اوقات سے بھی ملا لیں۔",
                "تُحسب الأوقات على الهاتف دون إنترنت. للاحتياط طابقها مع جدول مسجدك المحلي.");
        a("m0", "Muslim World League", "رابطہ عالمِ اسلامی", "رابطة العالم الإسلامي");
        a("m1", "ISNA (North America)", "اسنا (شمالی امریکہ)", "الجمعية الإسلامية لأمريكا الشمالية");
        a("m2", "Egyptian General Authority of Survey", "مصری ہیئتِ عامہ برائے سروے", "الهيئة المصرية العامة للمساحة");
        a("m3", "Umm al-Qura (Makkah)", "ام القریٰ (مکہ مکرمہ)", "أم القرى (مكة المكرمة)");
        a("m4", "University of Islamic Sciences, Karachi", "جامعہ علومِ اسلامیہ، کراچی", "جامعة العلوم الإسلامية، كراتشي");
        a("am", "AM", "صبح", "ص");
        a("pm", "PM", "شام", "م");
        a("km", "km", "کلومیٹر", "كم");
        a("deg_from_n", "from North", "شمال سے", "من الشمال");
    }

    public static final String[] WEEK_EN = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    public static final String[] WEEK_UR = {"اتوار", "پیر", "منگل", "بدھ", "جمعرات", "جمعہ", "ہفتہ"};
    public static final String[] WEEK_AR = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};

    public static final String[] MON_EN = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
    public static final String[] MON_UR = {"جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون", "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر"};
    public static final String[] MON_AR = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};

    public static final String[] HIJRI_EN = {"Muharram", "Safar", "Rabi' al-awwal", "Rabi' al-thani", "Jumada al-ula", "Jumada al-akhira", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah"};
    public static final String[] HIJRI_UR = {"محرم", "صفر", "ربیع الاول", "ربیع الثانی", "جمادی الاول", "جمادی الثانی", "رجب", "شعبان", "رمضان", "شوال", "ذوالقعدہ", "ذوالحجہ"};
    public static final String[] HIJRI_AR = {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};

    public static String t(int lang, String key) {
        String[] v = T.get(key);
        return v == null ? key : v[lang];
    }

    public static String[] pick(int lang, String[] en, String[] ur, String[] ar) { return lang == UR ? ur : (lang == AR ? ar : en); }
}
