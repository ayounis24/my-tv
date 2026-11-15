package com.mytv.home;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final String ACTION_ADHAN_NOW = "com.mytv.home.ADHAN_NOW";

    private static final int GOLD = 0xFFE8C35A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xDDFFFFFF;
    private static final int DIM = 0xAAFFFFFF;

    private static final String[] HIJRI_MONTHS = {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى",
            "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};
    private static final String[] GREG_MONTHS = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};
    private static final String[] DAYS = {"الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"};
    private static final String[] AZKAR = {
            "صلِّ على النبي ﷺ",
            "سبحان الله وبحمده، سبحان الله العظيم",
            "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير",
            "أستغفر الله العظيم وأتوب إليه",
            "لا حول ولا قوة إلا بالله",
            "سبحان الله، والحمد لله، ولا إله إلا الله، والله أكبر",
            "حسبي الله لا إله إلا هو، عليه توكلت وهو رب العرش العظيم",
            "رضيت بالله ربًا، وبالإسلام دينًا، وبمحمد ﷺ نبيًا",
            "اللهم إنك عفوٌّ تحب العفو فاعفُ عني",
    };
    /** Apps shown first in the dock, in this order, when installed. */
    private static final List<String> FAVORITES = Arrays.asList(
            "com.netflix.ninja", "com.google.android.youtube.tv", "net.mbc.shahidTV", "com.mbc.shahid.androidtv",
            "com.apple.atve.androidtv.appletv", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
            "com.osn.go.tv", "com.tod.tv", "com.spotify.tv.android", "com.google.android.youtube.tvmusic");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PrayerTimes prayer = new PrayerTimes(BuildConfig.CITY_LAT, BuildConfig.CITY_LON, BuildConfig.CITY_TZ);
    private Weather weather = new Weather();

    private ImageView bgFront, bgBack;
    private String currentWallpaper = "";
    private TextView greeting, clock, ampm, hijriLine, nextLine, gregLine, zikr, banner, focusTitle;
    private TextView prayerCardNext;
    private LinearLayout panel;
    private final TextView[] cellName = new TextView[6];
    private final TextView[] cellTime = new TextView[6];
    private final LinearLayout[] cells = new LinearLayout[6];
    private LinearLayout dock;
    private View firstCard;
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
        fillDock();
        handler.post(ticker);
        handler.post(zikrRotator);
        handler.post(weatherUpdater);
        if (firstCard != null) firstCard.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(adhanNow);
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && panel.getVisibility() == View.VISIBLE) {
            panel.setVisibility(View.GONE);
            if (firstCard != null) firstCard.requestFocus();
            return true;
        }
        return super.onKeyDown(keyCode, event);
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

    private final Runnable weatherUpdater = new Runnable() {
        @Override public void run() {
            new Thread(() -> {
                Weather w = Weather.fetch(BuildConfig.CITY_LAT, BuildConfig.CITY_LON);
                handler.post(() -> { if (w.hasTemp()) weather = w; tick(); });
            }).start();
            handler.postDelayed(this, 30 * 60_000);
        }
    };

    private void tick() {
        LocalDateTime now = LocalDateTime.now(zone());
        LocalDate today = now.toLocalDate();
        int h = now.getHour(), mi = now.getMinute();
        int h12 = h % 12 == 0 ? 12 : h % 12;
        clock.setText(String.format(Locale.US, "%d:%02d", h12, mi));
        ampm.setText(h < 12 ? "ص" : "م");

        HijrahDate hd = HijrahDate.from(today);
        int hDay = hd.get(ChronoField.DAY_OF_MONTH);
        boolean friday = today.getDayOfWeek() == DayOfWeek.FRIDAY;
        if (friday) greeting.setText("جمعة مباركة 🌿");
        else if (hDay >= 13 && hDay <= 15) greeting.setText("الأيام البيض 🤍");
        else greeting.setText(h < 12 ? "صباح الخير" : "مساء الخير");
        greeting.setTextColor(friday || (hDay >= 13 && hDay <= 15) ? GOLD : DIM);

        String hijri = hDay + " " + HIJRI_MONTHS[hd.get(ChronoField.MONTH_OF_YEAR) - 1] + " " + hd.get(ChronoField.YEAR) + " هـ";
        hijriLine.setText(weather.hasTemp() ? hijri + "   •   " + weather.icon() + " " + weather.tempC + "°" : hijri);
        gregLine.setText(DAYS[today.getDayOfWeek().getValue() - 1] + " " + today.getDayOfMonth() + " "
                + GREG_MONTHS[today.getMonthValue() - 1] + " " + today.getYear() + "   •   " + BuildConfig.CITY_NAME);

        int[] t = prayer.minutesFor(today);
        int nowMin = h * 60 + mi;
        int nextIdx = -1;
        for (int i = 0; i < 6; i++) {
            cellTime[i].setText(fmt(t[i]));
            if (nextIdx < 0 && t[i] > nowMin) nextIdx = i;
        }
        int remaining;
        if (nextIdx >= 0) remaining = t[nextIdx] - nowMin;
        else { nextIdx = 0; remaining = prayer.minutesFor(today.plusDays(1))[0] + 24 * 60 - nowMin; }
        for (int i = 0; i < 6; i++) {
            boolean on = i == nextIdx;
            cells[i].setBackground(round(on ? 0x44E8C35A : 0x22FFFFFF, on ? GOLD : 0, 14));
            cellName[i].setTextColor(on ? GOLD : SOFT);
        }
        String left = (remaining >= 60 ? String.format(Locale.US, "%d:%02d", remaining / 60, remaining % 60) : remaining + " دقيقة");
        nextLine.setText("🕌 " + PrayerTimes.NAMES[nextIdx] + " بعد " + left);
        prayerCardNext.setText(PrayerTimes.NAMES[nextIdx] + " " + fmt(t[nextIdx]));

        // Wallpaper follows the time of day and the weather
        String period;
        if (nowMin >= t[1] && nowMin < t[3]) period = "morning";
        else if (nowMin >= t[3] && nowMin < t[4] + 40) period = "evening";
        else period = "night";
        setWallpaper("wp_" + period + "_" + weather.condition);

        for (int i = 0; i < 6; i++) {
            String key = today + "-" + i;
            if (PrayerTimes.IS_PRAYER[i] && t[i] == nowMin && !key.equals(lastAdhanKey)) {
                lastAdhanKey = key;
                bannerUntil = System.currentTimeMillis() + 4 * 60_000;
                banner.setText("🕌 حان الآن موعد أذان " + PrayerTimes.NAMES[i] + " حسب توقيت " + BuildConfig.CITY_NAME);
            }
        }
        banner.setVisibility(System.currentTimeMillis() < bannerUntil ? View.VISIBLE : View.GONE);
    }

    private void setWallpaper(String name) {
        if (name.equals(currentWallpaper)) return;
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        if (id == 0) return;
        boolean first = currentWallpaper.isEmpty();
        currentWallpaper = name;
        if (first) { bgFront.setImageResource(id); return; }
        // new image goes underneath, the old one fades away on top
        bgBack.setImageResource(id);
        bgFront.animate().alpha(0f).setDuration(2000).withEndAction(() -> {
            bgFront.setImageResource(id);
            bgFront.setAlpha(1f);
            bgBack.setImageDrawable(null);
        }).start();
    }

    private static String fmt(int minutes) {
        minutes = ((minutes % 1440) + 1440) % 1440;
        int h = minutes / 60, m = minutes % 60;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.US, "%d:%02d %s", h12, m, h < 12 ? "ص" : "م");
    }

    // ---------- Dock ----------

    private void fillDock() {
        dock.removeAllViews();
        firstCard = addCard(prayerCard(), "مواقيت الصلاة", v -> {
            panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });

        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        List<ResolveInfo> sorted = new ArrayList<>();
        for (ResolveInfo ri : apps) if (!ri.activityInfo.packageName.equals(getPackageName())) sorted.add(ri);
        Collections.sort(sorted, (a, b) -> {
            int fa = FAVORITES.indexOf(a.activityInfo.packageName), fb = FAVORITES.indexOf(b.activityInfo.packageName);
            if (fa < 0) fa = 1000; if (fb < 0) fb = 1000;
            if (fa != fb) return fa - fb;
            return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
        });
        for (ResolveInfo ri : sorted) {
            String label = ri.loadLabel(pm).toString();
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            View face;
            if (banner != null) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageDrawable(banner);
                face = iv;
            } else {
                LinearLayout l = new LinearLayout(this);
                l.setOrientation(LinearLayout.VERTICAL);
                l.setGravity(Gravity.CENTER);
                l.setBackgroundColor(0xFF263238);
                ImageView ic = new ImageView(this);
                ic.setImageDrawable(ri.loadIcon(pm));
                l.addView(ic, new LinearLayout.LayoutParams(dp(44), dp(44)));
                TextView tv = text(13, WHITE, false);
                tv.setText(label);
                tv.setSingleLine(true);
                l.addView(tv);
                face = l;
            }
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            addCard(face, label, v -> {
                try { startActivity(launch); } catch (Exception ignored) { }
            });
        }
    }

    private View prayerCard() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF0F4C3A, 0xFF0A2E2A});
        l.setBackground(g);
        TextView title = text(17, GOLD, true);
        title.setText("🕌 مواقيت الصلاة");
        prayerCardNext = text(13, SOFT, false);
        l.addView(title);
        l.addView(prayerCardNext);
        return l;
    }

    private View addCard(View face, String title, View.OnClickListener click) {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(click);
        card.setBackground(round(0xFF1E2A30, 0, 12));
        card.setClipToOutline(true);
        card.addView(face, new FrameLayout.LayoutParams(-1, -1));
        View ring = new View(this);
        ring.setBackground(round(0, WHITE, 12));
        ring.setVisibility(View.INVISIBLE);
        card.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        card.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(150).start();
            v.setElevation(has ? dp(10) : 0);
            ring.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            if (has) focusTitle.setText(title);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(176), dp(99));
        lp.setMargins(dp(10), dp(14), dp(10), dp(14));
        dock.addView(card, lp);
        return card;
    }

    // ---------- Layout ----------

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A1F2E);

        bgFront = new ImageView(this);
        bgBack = new ImageView(this);
        for (ImageView iv : new ImageView[]{bgBack, bgFront}) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        }

        FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        View topShade = new View(this);
        topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xB0000000, 0x00000000}));
        overlay.addView(topShade, new FrameLayout.LayoutParams(-1, dp(230), Gravity.TOP));
        View bottomShade = new View(this);
        bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xC0000000, 0x00000000}));
        overlay.addView(bottomShade, new FrameLayout.LayoutParams(-1, dp(260), Gravity.BOTTOM));

        // Header (top right)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(48), dp(30), dp(48), 0);
        greeting = text(15, DIM, true);
        greeting.setLetterSpacing(0.05f);
        header.addView(greeting, startAligned());
        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setGravity(Gravity.BOTTOM);
        clock = text(64, WHITE, false);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        ampm = text(20, SOFT, false);
        ampm.setPadding(dp(8), 0, dp(8), dp(10));
        clockRow.addView(clock);
        clockRow.addView(ampm);
        header.addView(clockRow, startAligned());
        hijriLine = text(19, WHITE, true);
        header.addView(hijriLine, startAligned());
        nextLine = text(18, GOLD, true);
        nextLine.setPadding(0, dp(4), 0, dp(2));
        header.addView(nextLine, startAligned());
        gregLine = text(14, DIM, false);
        header.addView(gregLine, startAligned());
        zikr = text(16, SOFT, false);
        zikr.setPadding(0, dp(10), 0, 0);
        header.addView(zikr, startAligned());
        for (int i = 0; i < header.getChildCount(); i++) {
            View c = header.getChildAt(i);
            if (c instanceof TextView) ((TextView) c).setShadowLayer(8, 0, 2, 0xAA000000);
        }
        clock.setShadowLayer(12, 0, 3, 0xAA000000);
        overlay.addView(header, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));

        // Settings pill (top left)
        TextView settings = text(14, WHITE, false);
        settings.setText("⚙  الإعدادات");
        settings.setPadding(dp(16), dp(8), dp(16), dp(8));
        settings.setBackground(round(0x33FFFFFF, 0, 20));
        settings.setFocusable(true);
        settings.setOnFocusChangeListener((v, has) -> {
            v.setBackground(round(has ? 0xEEFFFFFF : 0x33FFFFFF, 0, 20));
            ((TextView) v).setTextColor(has ? 0xFF10261F : WHITE);
        });
        settings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception ignored) { }
        });
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        slp.setMargins(dp(48), dp(36), dp(48), 0);
        overlay.addView(settings, slp);

        // Dock (bottom)
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(38), 0, dp(38), dp(22));
        focusTitle = text(30, WHITE, true);
        focusTitle.setShadowLayer(10, 0, 2, 0xAA000000);
        focusTitle.setPadding(dp(10), 0, dp(10), 0);
        bottom.addView(focusTitle, startAligned());
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        dock = new LinearLayout(this);
        dock.setClipChildren(false);
        dock.setPadding(dp(8), 0, dp(8), 0);
        scroller.addView(dock);
        bottom.setClipChildren(false);
        bottom.addView(scroller, new LinearLayout.LayoutParams(-1, -2));
        overlay.setClipChildren(false);
        overlay.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // Prayer times panel (toggled from the first card)
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(28), dp(20), dp(28), dp(24));
        panel.setBackground(round(0xD90B1A22, 0x66E8C35A, 22));
        TextView pt = text(22, GOLD, true);
        pt.setText("مواقيت الصلاة اليوم  •  " + BuildConfig.CITY_NAME);
        pt.setPadding(0, 0, 0, dp(14));
        panel.addView(pt);
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < 6; i++) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(18), dp(12), dp(18), dp(12));
            cellName[i] = text(18, SOFT, true);
            cellName[i].setText(PrayerTimes.NAMES[i]);
            cellTime[i] = text(21, WHITE, false);
            c.addView(cellName[i]);
            c.addView(cellTime[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(dp(6), 0, dp(6), 0);
            row.addView(c, lp);
            cells[i] = c;
        }
        panel.addView(row);
        TextView hint = text(13, DIM, false);
        hint.setText("اضغط رجوع للإغلاق");
        hint.setPadding(0, dp(12), 0, 0);
        panel.addView(hint);
        panel.setVisibility(View.GONE);
        overlay.addView(panel, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        // Adhan banner
        banner = text(26, 0xFF10261F, true);
        banner.setBackgroundColor(GOLD);
        banner.setPadding(dp(24), dp(16), dp(24), dp(16));
        banner.setVisibility(View.GONE);
        overlay.addView(banner, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
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

    private static LinearLayout.LayoutParams startAligned() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.START;
        return lp;
    }

    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(2), stroke);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
