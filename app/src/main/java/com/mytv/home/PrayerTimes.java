package com.mytv.home;

import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

/** Umm al-Qura prayer times: Fajr 18.5°, Isha 90 min after Maghrib (120 in Ramadan), Asr standard. */
public final class PrayerTimes {
    public static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    public static final boolean[] IS_PRAYER = {true, false, true, true, true, true};

    private final double lat, lon, tz;
    private double jDate;

    public PrayerTimes(double lat, double lon, double tz) {
        this.lat = lat; this.lon = lon; this.tz = tz;
    }

    /** Minutes after local midnight for each of NAMES on the given day. */
    public int[] minutesFor(LocalDate day) {
        jDate = julian(day.getYear(), day.getMonthValue(), day.getDayOfMonth()) - lon / (15.0 * 24.0);
        double[] t = {5, 6, 12, 13, 18, 18};
        for (int i = 0; i < 2; i++) {
            double[] p = new double[6];
            for (int k = 0; k < 6; k++) p[k] = t[k] / 24.0;
            t[0] = sunAngleTime(18.5, p[0], true);
            t[1] = sunAngleTime(0.833, p[1], true);
            t[2] = midDay(p[2]);
            t[3] = asrTime(1, p[3]);
            t[4] = sunAngleTime(0.833, p[4], false);
            t[5] = t[4];
        }
        int[] out = new int[6];
        for (int k = 0; k < 5; k++) {
            out[k] = (int) Math.round((t[k] + tz - lon / 15.0) * 60.0);
        }
        int ramadan = HijrahDate.from(day).get(ChronoField.MONTH_OF_YEAR) == 9 ? 120 : 90;
        out[5] = out[4] + ramadan;
        return out;
    }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double a = Math.floor(y / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** {declination, equation of time} */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0;
        double eqt = q / 15.0 - fixHour(ra);
        double decl = darcsin(dsin(e) * dsin(l));
        return new double[]{decl, eqt};
    }

    private double midDay(double t) {
        return fixHour(12 - sunPosition(jDate + t)[1]);
    }

    private double sunAngleTime(double angle, double t, boolean ccw) {
        double decl = sunPosition(jDate + t)[0];
        double noon = midDay(t);
        double x = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat));
        double tt = darccos(Math.max(-1, Math.min(1, x))) / 15.0;
        return noon + (ccw ? -tt : tt);
    }

    private double asrTime(double factor, double t) {
        double decl = sunPosition(jDate + t)[0];
        double angle = -Math.toDegrees(Math.atan(1.0 / (factor + dtan(Math.abs(lat - decl)))));
        return sunAngleTime(angle, t, false);
    }

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double fixAngle(double a) { a = a - 360.0 * Math.floor(a / 360.0); return a < 0 ? a + 360 : a; }
    private static double fixHour(double a) { a = a - 24.0 * Math.floor(a / 24.0); return a < 0 ? a + 24 : a; }
}
