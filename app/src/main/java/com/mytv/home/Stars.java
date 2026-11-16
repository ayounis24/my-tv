package com.mytv.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Prayer stars for the girls: one star per prayer (Fajr, Dhuhr, Asr, Maghrib, Isha) per day.
 * Stored on the TV only. The week starts on Saturday; 35 stars in a week = a crown.
 */
public final class Stars {
    public static final String[] PRAYERS = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
    /** index in PrayerTimes.NAMES for each of PRAYERS (skips sunrise). */
    public static final int[] PT_INDEX = {0, 2, 3, 4, 5};
    public static final int WEEK_GOAL = 35;
    static final String ACTION = "com.mytv.home.STARS";

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("stars", Context.MODE_PRIVATE); }

    public static LocalDate today() { return LocalDateTime.now(MainActivity.zone()).toLocalDate(); }

    public static LocalDate weekStart(LocalDate d) {
        int back = (d.getDayOfWeek().getValue() - DayOfWeek.SATURDAY.getValue() + 7) % 7;
        return d.minusDays(back);
    }

    /** The girls' names, from the family names typed on the phone page. */
    public static List<String> girls(Context c) {
        List<String> out = new ArrayList<>();
        JSONArray k = PhotoServer.family(c).optJSONArray("kids");
        if (k != null) for (int i = 0; i < k.length(); i++) if (!k.optString(i).trim().isEmpty()) out.add(k.optString(i).trim());
        return out;
    }

    private static JSONObject all(Context c) {
        try { return new JSONObject(sp(c).getString("data", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    public static boolean[] day(Context c, LocalDate d, String girl) {
        boolean[] b = new boolean[5];
        JSONArray a = all(c).optJSONObject(d.toString()) == null ? null : all(c).optJSONObject(d.toString()).optJSONArray(girl);
        if (a != null) for (int i = 0; i < 5 && i < a.length(); i++) b[i] = a.optBoolean(i);
        return b;
    }

    public static int count(boolean[] b) { int n = 0; for (boolean x : b) if (x) n++; return n; }

    public static int week(Context c, String girl) {
        LocalDate s = weekStart(today());
        int n = 0;
        for (int i = 0; i < 7; i++) n += count(day(c, s.plusDays(i), girl));
        return n;
    }

    /** Sets one star; celebrates when it completes the day (5/5) or the week (35). */
    public static synchronized void set(Context c, String girl, int prayer, boolean on) {
        LocalDate d = today();
        int dayBefore = count(day(c, d, girl)), weekBefore = week(c, girl);
        try {
            JSONObject a = all(c);
            JSONObject dd = a.optJSONObject(d.toString());
            if (dd == null) { dd = new JSONObject(); a.put(d.toString(), dd); }
            JSONArray arr = dd.optJSONArray(girl);
            if (arr == null) { arr = new JSONArray(); for (int i = 0; i < 5; i++) arr.put(false); dd.put(girl, arr); }
            arr.put(prayer, on);
            // keep about two months
            JSONArray names = a.names();
            if (names != null) for (int i = 0; i < names.length(); i++)
                if (names.getString(i).compareTo(d.minusDays(60).toString()) < 0) a.remove(names.getString(i));
            sp(c).edit().putString("data", a.toString()).apply();
        } catch (Exception ignored) { }
        c.sendBroadcast(new Intent(ACTION).setPackage(c.getPackageName()));
        int dayAfter = count(day(c, d, girl)), weekAfter = week(c, girl);
        if (on && weekBefore < WEEK_GOAL && weekAfter >= WEEK_GOAL) CelebrateActivity.show(c, girl, true);
        else if (on && dayBefore < 5 && dayAfter == 5) CelebrateActivity.show(c, girl, false);
    }

    /** Has this prayer's time come today? (a star can't be taken before the prayer) */
    public static boolean due(int prayer) {
        LocalDateTime now = LocalDateTime.now(MainActivity.zone());
        int[] t = new PrayerTimes(BuildConfig.CITY_LAT, BuildConfig.CITY_LON, BuildConfig.CITY_TZ).minutesFor(now.toLocalDate());
        return now.getHour() * 60 + now.getMinute() >= t[PT_INDEX[prayer]];
    }

    /** {girls:[...], today:{girl:[5 bools]}, week:{girl:n}, due:[5 bools]} for the phone page. */
    public static JSONObject status(Context c) {
        JSONObject o = new JSONObject();
        try {
            JSONArray gs = new JSONArray(), dueA = new JSONArray();
            JSONObject t = new JSONObject(), w = new JSONObject();
            for (String g : girls(c)) {
                gs.put(g);
                JSONArray a = new JSONArray();
                for (boolean b : day(c, today(), g)) a.put(b);
                t.put(g, a);
                w.put(g, week(c, g));
            }
            for (int i = 0; i < 5; i++) dueA.put(due(i));
            o.put("girls", gs).put("today", t).put("week", w).put("due", dueA).put("goal", WEEK_GOAL);
        } catch (Exception ignored) { }
        return o;
    }
}
