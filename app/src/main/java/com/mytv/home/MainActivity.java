package com.mytv.home;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final String ACTION_ADHAN_NOW = "com.mytv.home.ADHAN_NOW";

    private static final int GOLD = 0xFFE8C35A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xDDFFFFFF;
    private static final int DIM = 0xAAFFFFFF;
    private static final int GLASS = 0xB30B1A22;

    private static final String[] HIJRI_MONTHS = {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى",
            "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};
    private static final String[] GREG_MONTHS = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};
    private static final String[] DAYS = {"الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"};
    private static final String[] AZKAR = {
            "صلِّ على النبي ﷺ",
            "سبحان الله وبحمده، سبحان الله العظيم",
            "أستغفر الله العظيم وأتوب إليه",
            "لا حول ولا قوة إلا بالله",
            "سبحان الله، والحمد لله، ولا إله إلا الله، والله أكبر",
            "حسبي الله لا إله إلا هو، عليه توكلت",
            "رضيت بالله ربًا، وبالإسلام دينًا، وبمحمد ﷺ نبيًا",
            "اللهم إنك عفوٌّ تحب العفو فاعفُ عني",
    };
    private static final String[] DOCK_PACKAGES = {"com.google.android.youtube.tv"};
    private static final String[] DOCK_LABEL_MATCH = {"بابلو", "pablo", "bablo"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PrayerTimes prayer = new PrayerTimes(BuildConfig.CITY_LAT, BuildConfig.CITY_LON, BuildConfig.CITY_TZ);
    private Weather weather = new Weather();
    private Player player;

    private ImageView bgFront, bgBack;
    private String currentWallpaper = "";
    private TextView greeting, clock, ampm, hijriLine, nextLine, gregLine, zikr, banner;
    private final TextView[] chipName = new TextView[6];
    private final TextView[] chipTime = new TextView[6];
    private final LinearLayout[] chips = new LinearLayout[6];
    private LinearLayout dock;
    private View firstCard;

    // player bar
    private LinearLayout bar;
    private TextView barTitle, barSub, barTime, barPlay;
    private ProgressBar barProgress;
    private EqView eq;

    // mushaf list
    private FrameLayout mushaf;
    private GridView mushafGrid;
    private BaseAdapter mushafAdapter;

    // family portraits (two fixed circles)
    private final ImageView[] portraits = new ImageView[2];
    private TextView photoHint, photoCaption;
    private String shownPhotos = "";

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
        player = Player.get(this);
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        AdhanReceiver.scheduleNext(this);
        PhotoServer.start(this);
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
        handler.post(photoRotator);
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
        if (keyCode == KeyEvent.KEYCODE_BACK && mushaf.getVisibility() == View.VISIBLE) {
            mushaf.setVisibility(View.GONE);
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

    private final Runnable photoRotator = new Runnable() {
        @Override public void run() { showNextPhoto(); handler.postDelayed(this, 15000); }
    };

    // ---------- every second ----------

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
        boolean white = hDay >= 13 && hDay <= 15;
        greeting.setText(friday ? "جمعة مباركة 🌿" : white ? "الأيام البيض 🤍" : h < 12 ? "صباح الخير" : "مساء الخير");
        greeting.setTextColor(friday || white ? GOLD : DIM);

        String hijri = hDay + " " + HIJRI_MONTHS[hd.get(ChronoField.MONTH_OF_YEAR) - 1] + " " + hd.get(ChronoField.YEAR) + " هـ";
        hijriLine.setText(weather.hasTemp() ? hijri + "   •   " + weather.icon() + " " + weather.tempC + "°" : hijri);
        gregLine.setText(DAYS[today.getDayOfWeek().getValue() - 1] + " " + today.getDayOfMonth() + " "
                + GREG_MONTHS[today.getMonthValue() - 1] + " " + today.getYear() + "   •   " + BuildConfig.CITY_NAME);

        int[] t = prayer.minutesFor(today);
        int nowMin = h * 60 + mi;
        int nextIdx = -1;
        for (int i = 0; i < 6; i++) {
            chipTime[i].setText(fmtShort(t[i]));
            if (nextIdx < 0 && t[i] > nowMin) nextIdx = i;
        }
        int remaining;
        if (nextIdx >= 0) remaining = t[nextIdx] - nowMin;
        else { nextIdx = 0; remaining = prayer.minutesFor(today.plusDays(1))[0] + 24 * 60 - nowMin; }
        for (int i = 0; i < 6; i++) {
            boolean on = i == nextIdx;
            chips[i].setBackground(round(on ? 0x55E8C35A : GLASS, on ? GOLD : 0, 12));
            chipName[i].setTextColor(on ? GOLD : SOFT);
        }
        String left = remaining >= 60 ? String.format(Locale.US, "%d:%02d", remaining / 60, remaining % 60) : remaining + " دقيقة";
        nextLine.setText("🕌 " + PrayerTimes.NAMES[nextIdx] + " بعد " + left);

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
        updateBar();
    }

    private void updateBar() {
        Player.State st = player.state;
        boolean show = st != Player.State.IDLE;
        bar.setVisibility(show ? View.VISIBLE : View.GONE);
        eq.setActive(st == Player.State.PLAYING);
        if (!show) return;
        barTitle.setText(player.title);
        switch (st) {
            case LOADING: barSub.setText("جاري التحميل…"); break;
            case ERROR: barSub.setText("مش متاح دلوقتي، جرّب تاني"); break;
            case PAUSED: barSub.setText(player.subtitle + "  •  متوقف"); break;
            default: barSub.setText(player.subtitle);
        }
        barPlay.setText(st == Player.State.PLAYING ? "⏸" : "▶");
        int pos = player.positionMs(), dur = player.durationMs();
        if (player.live || dur <= 0) {
            barProgress.setVisibility(View.INVISIBLE);
            barTime.setText(player.live && st == Player.State.PLAYING ? "مباشر" : "");
        } else {
            barProgress.setVisibility(View.VISIBLE);
            barProgress.setMax(dur);
            barProgress.setProgress(pos);
            barTime.setText(clockText(pos) + " / " + clockText(dur));
        }
    }

    private void setWallpaper(String name) {
        if (name.equals(currentWallpaper)) return;
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        if (id == 0) return;
        boolean first = currentWallpaper.isEmpty();
        currentWallpaper = name;
        if (first) { bgFront.setImageResource(id); return; }
        bgBack.setImageResource(id);
        bgFront.animate().alpha(0f).setDuration(2000).withEndAction(() -> {
            bgFront.setImageResource(id);
            bgFront.setAlpha(1f);
            bgBack.setImageDrawable(null);
        }).start();
    }

    /** Shows the two most recent family photos, fixed in round gold frames. Re-checks for new uploads. */
    private void showNextPhoto() {
        File[] files = PhotoServer.photos(this);
        String addr = PhotoServer.address();
        boolean ready = files.length >= 1;
        photoHint.setText("📷 لإضافة الصور: من موبايلك على نفس الواي فاي افتح\n"
                + (addr != null ? addr : "…") + "   •   الرقم السري: " + PhotoServer.pin(this));
        photoHint.setVisibility(ready ? View.GONE : View.VISIBLE);
        photoCaption.setVisibility(ready ? View.VISIBLE : View.GONE);
        String key = "";
        File[] pick = new File[2];
        for (int i = 0; i < 2; i++) {
            int idx = files.length - 2 + i;
            if (files.length == 1) idx = i == 0 ? 0 : -1;
            pick[i] = idx >= 0 && idx < files.length ? files[idx] : null;
            key += pick[i] == null ? "-" : pick[i].getName();
        }
        portraits[1].setVisibility(pick[1] != null || !ready ? View.VISIBLE : View.GONE);
        if (key.equals(shownPhotos)) return;
        shownPhotos = key;
        for (int i = 0; i < 2; i++) {
            ImageView iv = portraits[i];
            File f = pick[i];
            if (f == null) { iv.setImageDrawable(null); continue; }
            new Thread(() -> {
                Bitmap bm = decode(f, 500);
                handler.post(() -> {
                    iv.setAlpha(0f);
                    iv.setImageBitmap(bm);
                    iv.animate().alpha(1f).setDuration(900).start();
                });
            }).start();
        }
    }

    private static Bitmap decode(File f, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private static String fmtShort(int minutes) {
        minutes = ((minutes % 1440) + 1440) % 1440;
        int h = minutes / 60, m = minutes % 60;
        return String.format(Locale.US, "%d:%02d", h % 12 == 0 ? 12 : h % 12, m);
    }

    private static String clockText(int ms) {
        int s = ms / 1000;
        return s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    // ---------- dock ----------

    private void fillDock() {
        dock.removeAllViews();
        firstCard = addCard(cardFace("📖", "المصحف", Player.RECITER, 0xFF6B4E16, 0xFF2E2108), v -> openMushaf());

        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        for (String pkg : DOCK_PACKAGES)
            for (ResolveInfo ri : apps) if (ri.activityInfo.packageName.equals(pkg)) addAppCard(pm, ri);
        for (ResolveInfo ri : apps) {
            String label = ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
            String pkg = ri.activityInfo.packageName.toLowerCase(Locale.ROOT);
            for (String m : DOCK_LABEL_MATCH)
                if (label.contains(m) || pkg.contains(m)) { addAppCard(pm, ri); break; }
        }
    }

    private void addAppCard(PackageManager pm, ResolveInfo ri) {
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
            TextView tv = text(14, WHITE, true);
            tv.setText(label);
            tv.setSingleLine(true);
            l.addView(tv);
            face = l;
        }
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        addCard(face, v -> { try { startActivity(launch); } catch (Exception ignored) { } });
    }

    private View cardFace(String icon, String title, String sub, int c1, int c2) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2}));
        TextView t = text(19, 0xFFF3E3B5, true);
        t.setText(icon + " " + title);
        TextView st = text(12, SOFT, false);
        st.setText(sub);
        l.addView(t);
        l.addView(st);
        return l;
    }

    private View addCard(View face, View.OnClickListener click) {
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
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(150).start();
            v.setElevation(has ? dp(10) : 0);
            ring.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(176), dp(99));
        lp.setMargins(dp(10), dp(10), dp(10), dp(10));
        dock.addView(card, lp);
        return card;
    }

    // ---------- mushaf ----------

    private void openMushaf() {
        mushaf.setVisibility(View.VISIBLE);
        mushafAdapter.notifyDataSetChanged();
        int cur = player.surah();
        mushafGrid.setSelection(cur > 0 ? cur : 0);
        mushafGrid.requestFocus();
    }

    private View buildMushaf() {
        mushaf = new FrameLayout(this);
        mushaf.setBackgroundColor(0x99000000);
        mushaf.setVisibility(View.GONE);
        mushaf.setClickable(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(18), dp(24), dp(18));
        box.setBackground(round(0xF00B1A22, 0x66E8C35A, 22));
        TextView title = text(22, GOLD, true);
        title.setText("📖 المصحف كامل  •  بصوت الشيخ " + Player.RECITER);
        title.setPadding(0, 0, 0, dp(4));
        box.addView(title);
        TextView hint = text(13, DIM, false);
        hint.setText("اختار السورة واضغط OK  •  رجوع للإغلاق  •  بعد ما السورة تخلص اللي بعدها بتشتغل لوحدها");
        hint.setPadding(0, 0, 0, dp(12));
        box.addView(hint);

        mushafGrid = new GridView(this);
        mushafGrid.setNumColumns(6);
        mushafGrid.setHorizontalSpacing(dp(8));
        mushafGrid.setVerticalSpacing(dp(8));
        mushafGrid.setSelector(round(0x00000000, GOLD, 12));
        mushafGrid.setDrawSelectorOnTop(true);
        mushafAdapter = new BaseAdapter() {
            @Override public int getCount() { return 115; }
            @Override public Object getItem(int i) { return i; }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View conv, ViewGroup parent) {
                TextView tv = conv instanceof TextView ? (TextView) conv : text(16, WHITE, true);
                tv.setPadding(dp(6), dp(12), dp(6), dp(12));
                tv.setSingleLine(true);
                boolean playing = i == 0 ? "ruqya".equals(player.key) : player.surah() == i;
                tv.setText(i == 0 ? "🎧 الرقية الشرعية" : i + ". " + Surahs.NAMES[i - 1]);
                tv.setTextColor(playing ? 0xFF10261F : WHITE);
                tv.setBackground(round(playing ? GOLD : (i == 0 ? 0x5514524B : 0x22FFFFFF), 0, 12));
                return tv;
            }
        };
        mushafGrid.setAdapter(mushafAdapter);
        mushafGrid.setOnItemClickListener((p, v, i, id) -> {
            if (i == 0) player.playRuqya(); else player.playSurah(i, Surahs.NAMES[i - 1]);
            mushafAdapter.notifyDataSetChanged();
            updateBar();
        });
        box.addView(mushafGrid, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        lp.setMargins(dp(60), dp(40), dp(60), dp(40));
        mushaf.addView(box, lp);
        return mushaf;
    }

    // ---------- layout ----------

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A1F2E);

        bgBack = new ImageView(this);
        bgFront = new ImageView(this);
        for (ImageView iv : new ImageView[]{bgBack, bgFront}) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        }

        FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        overlay.setClipChildren(false);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        View topShade = new View(this);
        topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xB0000000, 0x00000000}));
        overlay.addView(topShade, new FrameLayout.LayoutParams(-1, dp(240), Gravity.TOP));
        View bottomShade = new View(this);
        bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xC8000000, 0x00000000}));
        overlay.addView(bottomShade, new FrameLayout.LayoutParams(-1, dp(250), Gravity.BOTTOM));

        // --- top right: time & dates
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(44), dp(24), dp(44), 0);
        greeting = text(15, DIM, true);
        header.addView(greeting, startAligned());
        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setGravity(Gravity.BOTTOM);
        clock = text(62, WHITE, false);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        ampm = text(20, SOFT, false);
        ampm.setPadding(dp(8), 0, dp(8), dp(8));
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
        zikr = text(15, SOFT, false);
        zikr.setPadding(0, dp(8), 0, 0);
        zikr.setMaxWidth(dp(430));
        zikr.setGravity(Gravity.START);
        header.addView(zikr, startAligned());
        for (int i = 0; i < header.getChildCount(); i++)
            if (header.getChildAt(i) instanceof TextView) ((TextView) header.getChildAt(i)).setShadowLayer(8, 0, 2, 0xAA000000);
        clock.setShadowLayer(12, 0, 3, 0xAA000000);
        overlay.addView(header, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));

        // --- top left: settings + family photo frame
        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setGravity(Gravity.END);
        leftCol.setClipChildren(false);
        TextView settings = pill("⚙  الإعدادات");
        settings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception ignored) { }
        });
        leftCol.addView(settings, endAligned());
        leftCol.addView(buildPhotoFrame());
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        llp.setMargins(dp(44), dp(28), dp(44), 0);
        overlay.addView(leftCol, llp);

        // --- bottom: prayer chips (right) + player bar (left), then the dock
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(34), 0, dp(34), dp(16));
        bottom.setClipChildren(false);

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout chipRow = new LinearLayout(this);
        for (int i = 0; i < 6; i++) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            chipName[i] = text(12, SOFT, true);
            chipName[i].setText(PrayerTimes.NAMES[i]);
            chipTime[i] = text(16, WHITE, false);
            c.addView(chipName[i]);
            c.addView(chipTime[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(58), dp(50));
            lp.setMargins(dp(3), 0, dp(3), 0);
            chipRow.addView(c, lp);
            chips[i] = c;
        }
        infoRow.addView(chipRow);
        infoRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        infoRow.addView(buildBar());
        bottom.addView(infoRow, new LinearLayout.LayoutParams(-1, dp(58)));

        dock = new LinearLayout(this);
        dock.setClipChildren(false);
        bottom.addView(dock, startAligned());
        overlay.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        overlay.addView(buildMushaf(), new FrameLayout.LayoutParams(-1, -1));

        banner = text(26, 0xFF10261F, true);
        banner.setBackgroundColor(GOLD);
        banner.setPadding(dp(24), dp(16), dp(24), dp(16));
        banner.setVisibility(View.GONE);
        overlay.addView(banner, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        return root;
    }

    private View buildPhotoFrame() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);

        FrameLayout pair = new FrameLayout(this);
        pair.setClipChildren(false);
        int size = dp(132), overlap = dp(22);
        for (int i = 0; i < 2; i++) {
            FrameLayout ring = new FrameLayout(this);
            GradientDrawable gold = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFF6DE8D, 0xFFB8892E});
            gold.setShape(GradientDrawable.OVAL);
            ring.setBackground(gold);
            ring.setPadding(dp(5), dp(5), dp(5), dp(5));
            ring.setElevation(dp(14 - i * 2));
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable oval = new GradientDrawable();
            oval.setShape(GradientDrawable.OVAL);
            oval.setColor(0xFF2A3A40);
            iv.setBackground(oval);
            iv.setClipToOutline(true);
            ring.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            portraits[i] = iv;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.LEFT);
            lp.leftMargin = i * (size - overlap);
            lp.topMargin = i == 0 ? 0 : dp(26);
            ring.setRotation(i == 0 ? -4f : 4f);
            pair.addView(ring, lp);
        }
        col.addView(pair, new LinearLayout.LayoutParams(size * 2 - overlap, size + dp(26)));

        photoCaption = text(15, 0xFFF3E3B5, true);
        photoCaption.setText("حفظهما الله 🤍");
        photoCaption.setShadowLayer(8, 0, 2, 0xAA000000);
        photoCaption.setPadding(0, dp(8), 0, 0);
        col.addView(photoCaption);

        photoHint = text(12, SOFT, false);
        photoHint.setShadowLayer(8, 0, 2, 0xAA000000);
        photoHint.setPadding(0, dp(8), 0, 0);
        photoHint.setMaxWidth(dp(300));
        col.addView(photoHint);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(18), 0, 0);
        lp.gravity = Gravity.END;
        col.setLayoutParams(lp);
        return col;
    }

    private View buildBar() {
        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(5), dp(8), dp(5));
        bar.setBackground(round(GLASS, 0x55E8C35A, 26));
        bar.setVisibility(View.GONE);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        barTitle = text(15, WHITE, true);
        barSub = text(11, DIM, false);
        barTitle.setGravity(Gravity.START);
        barSub.setGravity(Gravity.START);
        info.addView(barTitle, startAligned());
        info.addView(barSub, startAligned());
        barProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barProgress.setProgressTintList(ColorStateList.valueOf(GOLD));
        info.addView(barProgress, new LinearLayout.LayoutParams(dp(130), dp(5)));
        bar.addView(info);

        eq = new EqView(this, GOLD);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(80), dp(30));
        elp.setMargins(dp(12), 0, dp(8), 0);
        bar.addView(eq, elp);

        barTime = text(12, SOFT, false);
        barTime.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        barTime.setPadding(dp(4), 0, dp(8), 0);
        bar.addView(barTime);
        barPlay = barButton("⏸", v -> { player.togglePause(); updateBar(); });
        bar.addView(barPlay);
        bar.addView(barButton("✕", v -> { player.stop(); updateBar(); if (firstCard != null) firstCard.requestFocus(); }));
        return bar;
    }

    private TextView pill(String label) {
        TextView p = text(14, WHITE, false);
        p.setText(label);
        p.setPadding(dp(16), dp(8), dp(16), dp(8));
        p.setBackground(round(0x33FFFFFF, 0, 20));
        p.setFocusable(true);
        p.setClickable(true);
        p.setOnFocusChangeListener((v, has) -> {
            v.setBackground(round(has ? 0xEEFFFFFF : 0x33FFFFFF, 0, 20));
            ((TextView) v).setTextColor(has ? 0xFF10261F : WHITE);
        });
        return p;
    }

    private TextView barButton(String label, View.OnClickListener click) {
        TextView b = text(16, WHITE, true);
        b.setText(label);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(click);
        b.setBackground(round(0x22FFFFFF, 0, 20));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(round(has ? 0xEEFFFFFF : 0x22FFFFFF, 0, 20));
            ((TextView) v).setTextColor(has ? 0xFF10261F : WHITE);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
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

    private static LinearLayout.LayoutParams endAligned() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.END;
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
