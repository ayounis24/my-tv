package com.mytv.home;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

public class MainActivity extends Activity {
    static final String ACTION_ADHAN_NOW = "com.mytv.home.ADHAN_NOW";

    private static final int GOLD = 0xFFE8C35A;
    private static final int TEXT = 0xFFF5F1E6;
    private static final int MUTED = 0xFFB9C4BC;
    private static final int CARD = 0x33FFFFFF;

    private static final String[] HIJRI_MONTHS = {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى",
            "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};
    private static final String[] GREG_MONTHS = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};
    private static final String[] DAYS = {"الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"};
    private static final String[] AZKAR = {
            "سبحان الله وبحمده، سبحان الله العظيم",
            "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير",
            "أستغفر الله العظيم وأتوب إليه",
            "لا حول ولا قوة إلا بالله",
            "سبحان الله، والحمد لله، ولا إله إلا الله، والله أكبر",
            "اللهم صلِّ وسلِّم على نبينا محمد ﷺ",
            "حسبي الله لا إله إلا هو، عليه توكلت وهو رب العرش العظيم",
            "رضيت بالله ربًا، وبالإسلام دينًا، وبمحمد ﷺ نبيًا",
            "اللهم إنك عفوٌّ تحب العفو فاعفُ عني",
            "يا حي يا قيوم برحمتك أستغيث، أصلح لي شأني كله",
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PrayerTimes prayer = new PrayerTimes(BuildConfig.CITY_LAT, BuildConfig.CITY_LON, BuildConfig.CITY_TZ);

    private TextView clock, ampm, hijri, greg, special, next, zikr, banner;
    private final TextView[] cardName = new TextView[6];
    private final TextView[] cardTime = new TextView[6];
    private final LinearLayout[] cards = new LinearLayout[6];
    private int zikrIndex = 0;
    private long bannerUntil = 0;
    private String lastAdhanKey = "";

    static ZoneOffset zone() {
        return ZoneOffset.ofTotalSeconds((int) Math.round(BuildConfig.CITY_TZ * 3600));
    }

    private final BroadcastReceiver adhanNow = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { tick(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        AdhanReceiver.scheduleNext(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(ACTION_ADHAN_NOW);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(adhanNow, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(adhanNow, f);
        handler.post(ticker);
        handler.post(zikrRotator);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(adhanNow);
        handler.removeCallbacksAndMessages(null);
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() { tick(); handler.postDelayed(this, 1000); }
    };

    private final Runnable zikrRotator = new Runnable() {
        @Override public void run() {
            zikr.setText(AZKAR[zikrIndex % AZKAR.length]);
            zikr.setAlpha(0f);
            zikr.animate().alpha(1f).setDuration(800).start();
            zikrIndex++;
            handler.postDelayed(this, 20000);
        }
    };

    private void tick() {
        LocalDateTime now = LocalDateTime.now(zone());
        LocalDate today = now.toLocalDate();
        int h = now.getHour(), mi = now.getMinute();
        int h12 = h % 12 == 0 ? 12 : h % 12;
        clock.setText(String.format("%d:%02d", h12, mi));
        ampm.setText(h < 12 ? "صباحًا" : "مساءً");

        HijrahDate hd = HijrahDate.from(today);
        int hDay = hd.get(ChronoField.DAY_OF_MONTH);
        hijri.setText(DAYS[today.getDayOfWeek().getValue() - 1] + "  " + hDay + " "
                + HIJRI_MONTHS[hd.get(ChronoField.MONTH_OF_YEAR) - 1] + " " + hd.get(ChronoField.YEAR) + " هـ");
        greg.setText(today.getDayOfMonth() + " " + GREG_MONTHS[today.getMonthValue() - 1] + " " + today.getYear() + " م   •   " + BuildConfig.CITY_NAME);

        boolean friday = today.getDayOfWeek() == DayOfWeek.FRIDAY;
        if (friday) special.setText("جمعة مباركة 🌿  لا تنسَ سورة الكهف");
        else if (hDay >= 13 && hDay <= 15) special.setText("الأيام البيض 🤍");
        else special.setText("");

        int[] t = prayer.minutesFor(today);
        int nowMin = h * 60 + mi;
        int nextIdx = -1;
        for (int i = 0; i < 6; i++) {
            cardTime[i].setText(fmt(t[i]));
            if (nextIdx < 0 && t[i] > nowMin) nextIdx = i;
        }
        int remaining;
        if (nextIdx >= 0) remaining = t[nextIdx] - nowMin;
        else { nextIdx = 0; remaining = prayer.minutesFor(today.plusDays(1))[0] + 24 * 60 - nowMin; }
        for (int i = 0; i < 6; i++) {
            boolean on = i == nextIdx;
            cards[i].setBackground(round(on ? 0x55E8C35A : CARD, on ? GOLD : 0));
            cardName[i].setTextColor(on ? GOLD : MUTED);
        }
        String label = PrayerTimes.IS_PRAYER[nextIdx] ? "أذان " + PrayerTimes.NAMES[nextIdx] : PrayerTimes.NAMES[nextIdx];
        next.setText(label + " بعد " + (remaining >= 60 ? (remaining / 60) + " ساعة و" : "") + (remaining % 60) + " دقيقة");

        // Adhan banner: shown for 4 minutes from the exact prayer minute
        for (int i = 0; i < 6; i++) {
            String key = today + "-" + i;
            if (PrayerTimes.IS_PRAYER[i] && t[i] == nowMin && !key.equals(lastAdhanKey)) {
                lastAdhanKey = key;
                bannerUntil = System.currentTimeMillis() + 4 * 60_000;
                banner.setText("حان الآن موعد أذان " + PrayerTimes.NAMES[i] + " حسب توقيت " + BuildConfig.CITY_NAME);
            }
        }
        banner.setVisibility(System.currentTimeMillis() < bannerUntil ? View.VISIBLE : View.GONE);
    }

    private static String fmt(int minutes) {
        minutes = ((minutes % 1440) + 1440) % 1440;
        int h = minutes / 60, m = minutes % 60;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format("%d:%02d %s", h12, m, h < 12 ? "ص" : "م");
    }

    // ---------- UI ----------

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B2A2A, 0xFF0F3B33, 0xFF0A1F2E});
        root.setBackground(bg);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(48), dp(28), dp(48), dp(24));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        special = text(18, GOLD, true);
        col.addView(special);

        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setGravity(Gravity.BOTTOM);
        clock = text(110, TEXT, true);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        ampm = text(24, MUTED, false);
        ampm.setPadding(dp(12), 0, dp(12), dp(26));
        clockRow.addView(clock);
        clockRow.addView(ampm);
        col.addView(clockRow, wrap());

        hijri = text(26, TEXT, true);
        col.addView(hijri);
        greg = text(17, MUTED, false);
        greg.setPadding(0, dp(4), 0, dp(18));
        col.addView(greg);

        next = text(22, GOLD, true);
        next.setPadding(dp(22), dp(8), dp(22), dp(8));
        next.setBackground(round(0x22E8C35A, 0));
        col.addView(next, wrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(22), 0, 0);
        for (int i = 0; i < 6; i++) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(8), dp(12), dp(8), dp(12));
            cardName[i] = text(18, MUTED, true);
            cardName[i].setText(PrayerTimes.NAMES[i]);
            cardTime[i] = text(22, TEXT, false);
            c.addView(cardName[i]);
            c.addView(cardTime[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(dp(6), 0, dp(6), 0);
            row.addView(c, lp);
            cards[i] = c;
        }
        col.addView(row, new LinearLayout.LayoutParams(-1, -2));

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        zikr = text(24, TEXT, false);
        zikr.setPadding(dp(16), 0, dp(16), dp(6));
        col.addView(zikr);
        TextView salawat = text(16, GOLD, false);
        salawat.setText("صلِّ على النبي ﷺ");
        col.addView(salawat);

        banner = text(28, 0xFF10261F, true);
        banner.setBackgroundColor(GOLD);
        banner.setPadding(dp(24), dp(16), dp(24), dp(16));
        banner.setVisibility(View.GONE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(banner, blp);
        return root;
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(14));
        if (stroke != 0) d.setStroke(dp(2), stroke);
        return d;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
