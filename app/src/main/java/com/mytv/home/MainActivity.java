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
    static volatile boolean visible;
    private LinearLayout msgBar;
    private TextView msgFrom, msgText;
    private String shownMsgAt = "";

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
    private TextView photoHint, famTitle, famKids, famDua;
    private LinearLayout famCard, pinCard, famStars;
    private TextView pinAddr;
    private ImageView pinQr;
    private LinearLayout testCard;
    private final TextView[] pinDigits = new TextView[4];
    private int duaIndex = 0;
    private TextView overlayPill;
    private String shownPhotos = "";

    private int zikrIndex = 0;
    private long bannerUntil = 0;
    private String lastAdhanKey = "";

    static ZoneOffset zone() {
        return ZoneOffset.ofTotalSeconds((int) Math.round(BuildConfig.CITY_TZ * 3600));
    }

    private final BroadcastReceiver adhanNow = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Stars.ACTION.equals(i.getAction())) refreshStars();
            tick();
        }
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
        visible = true;
        Messages.hideOverlay(this);
        IntentFilter f = new IntentFilter(ACTION_ADHAN_NOW);
        f.addAction(Messages.ACTION);
        f.addAction(Stars.ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(adhanNow, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(adhanNow, f);
        fillDock();
        handler.post(ticker);
        handler.post(zikrRotator);
        handler.post(weatherUpdater);
        handler.post(photoRotator);
        handler.postDelayed(duaRotator, 25000);
        if (firstCard != null) firstCard.requestFocus();
        overlayPill.setVisibility(Settings.canDrawOverlays(this) ? View.GONE : View.VISIBLE);
    }

    /** Lets the adhan screen open on top of YouTube / Pablo. Some TVs hide this setting. */
    private void askOverlay() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
            overlayPill.setText("🔔 التليفزيون ده مش بيسمح بالإعداد ده");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        unregisterReceiver(adhanNow);
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (msgBar.getVisibility() == View.VISIBLE && e.getAction() == KeyEvent.ACTION_DOWN
                && (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || e.getKeyCode() == KeyEvent.KEYCODE_ENTER
                || e.getKeyCode() == KeyEvent.KEYCODE_BACK)) {
            Messages.markSeen(this);
            msgBar.animate().alpha(0f).translationY(dp(30)).setDuration(300).withEndAction(() -> msgBar.setVisibility(View.GONE)).start();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void updateMessage() {
        org.json.JSONObject m = Messages.current(this);
        boolean show = m.has("text") && !m.has("seen");
        if (!show) { if (msgBar.getVisibility() == View.VISIBLE && msgBar.getAlpha() == 1f) msgBar.setVisibility(View.GONE); return; }
        String at = m.optString("at");
        if (at.equals(shownMsgAt) && msgBar.getVisibility() == View.VISIBLE) return;
        shownMsgAt = at;
        msgFrom.setText("💌  " + Messages.senderLabel(this, m.optString("from")));
        msgText.setText(m.optString("text"));
        msgBar.setVisibility(View.VISIBLE);
        msgBar.setAlpha(0f);
        msgBar.setTranslationY(dp(40));
        msgBar.animate().alpha(1f).translationY(0).setDuration(450).start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && testCard.getVisibility() == View.VISIBLE) {
            testCard.setVisibility(View.GONE);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && pinCard.getVisibility() == View.VISIBLE) {
            pinCard.setVisibility(View.GONE);
            return true;
        }
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
                AdhanActivity.launch(this, PrayerTimes.NAMES[i]);
            }
        }
        if (nowMin == t[0] + AdhanReceiver.AZKAR_AFTER) AzkarActivity.launch(this, true);
        if (nowMin == t[3] + AdhanReceiver.AZKAR_AFTER) AzkarActivity.launch(this, false);
        banner.setVisibility(System.currentTimeMillis() < bannerUntil ? View.VISIBLE : View.GONE);
        updateBar();
        updateMessage();
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
        photoHint.setText("اضغط «📱 صفحة الموبايل» فوق عشان تضيف صوركم");
        photoHint.setVisibility(ready ? View.GONE : View.VISIBLE);
        refreshFamily();
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

    private String[] familyDuas = new String[0];

    /** Family names (typed on the phone page) -> title, kids line and personal duas. */
    private void refreshFamily() {
        org.json.JSONObject j = PhotoServer.family(this);
        String dad = j.optString("dad").trim(), mom = j.optString("mom").trim();
        org.json.JSONArray ka = j.optJSONArray("kids");
        java.util.List<String> kids = new java.util.ArrayList<>();
        if (ka != null) for (int i = 0; i < ka.length(); i++) if (!ka.optString(i).trim().isEmpty()) kids.add(ka.optString(i).trim());
        boolean girls = j.optBoolean("girls");
        if (dad.isEmpty() && mom.isEmpty() && kids.isEmpty()) { famCard.setVisibility(View.GONE); return; }
        famCard.setVisibility(View.VISIBLE);

        String parents = dad.isEmpty() ? mom : mom.isEmpty() ? dad : dad + " و" + mom;
        famTitle.setText("🏡 بيت " + parents);
        famKids.setVisibility(View.GONE);
        refreshStars();

        String kidsAnd = joinAnd(kids);
        String them = girls ? "هن" : "هم";
        java.util.List<String> d = new java.util.ArrayList<>();
        if (!kids.isEmpty()) {
            d.add("اللهم احفظ " + parents + " و" + (girls ? "بناتهم " : "ذريتهم ") + kidsAnd + "، واجعل بيتهم عامرًا بذكرك");
            d.add("اللهم اجعل " + kidsAnd + " قرة عين لوالدي" + them + "، وأنبت" + them + " نباتًا حسنًا، وبارك في" + them);
        } else {
            d.add("اللهم احفظ " + parents + "، واجعل بيتهم عامرًا بذكرك");
        }
        if (!dad.isEmpty() && !mom.isEmpty())
            d.add("اللهم بارك لـ" + dad + " و" + mom + " في بعضهما، واجمع بينهما في خير");
        d.add("﴿رَبَّنَا هَبْ لَنَا مِنْ أَزْوَاجِنَا وَذُرِّيَّاتِنَا قُرَّةَ أَعْيُنٍ وَاجْعَلْنَا لِلْمُتَّقِينَ إِمَامًا﴾");
        d.add("﴿رَبِّ اجْعَلْنِي مُقِيمَ الصَّلَاةِ وَمِن ذُرِّيَّتِي رَبَّنَا وَتَقَبَّلْ دُعَاءِ﴾");
        d.add("اللهم اجعل هذا البيت سكنًا ومودة ورحمة، واجمعنا في الفردوس الأعلى");
        String[] nd = d.toArray(new String[0]);
        if (!java.util.Arrays.equals(nd, familyDuas)) { familyDuas = nd; duaIndex = 0; famDua.setText(nd[0]); }
    }

    /** Today's prayer stars for each girl, under the photos (👑 when she has the week's crown). */
    private void refreshStars() {
        famStars.removeAllViews();
        java.util.List<String> girls = Stars.girls(this);
        famStars.setVisibility(girls.isEmpty() ? View.GONE : View.VISIBLE);
        LocalDate today = Stars.today();
        for (String g : girls) {
            boolean[] d = Stars.day(this, today, g);
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            TextView n = text(13, 0xFFF3E3B5, true);
            n.setText((Stars.week(this, g) >= Stars.WEEK_GOAL ? "👑 " : "") + g);
            StringBuilder sb = new StringBuilder();
            for (boolean b : d) sb.append(b ? "★" : "☆");
            TextView st = text(14, GOLD, false);
            st.setText(sb);
            c.addView(n);
            c.addView(st);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            famStars.addView(c, lp);
        }
    }

    private static String joinAnd(java.util.List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) sb.append(i == 0 ? "" : " و").append(names.get(i));
        return sb.toString();
    }

    private final Runnable duaRotator = new Runnable() {
        @Override public void run() {
            if (familyDuas.length > 0) {
                duaIndex = (duaIndex + 1) % familyDuas.length;
                famDua.animate().alpha(0f).setDuration(500).withEndAction(() -> {
                    famDua.setText(familyDuas[duaIndex]);
                    famDua.animate().alpha(1f).setDuration(700).start();
                }).start();
            }
            handler.postDelayed(this, 25000);
        }
    };

    private void showPinCard() {
        String addr = PhotoServer.address();
        pinAddr.setText(addr != null ? addr : "التليفزيون مش متوصل بالواي فاي");
        String pin = PhotoServer.pin(this);
        testCard.setVisibility(View.GONE);
        if (addr != null) pinQr.setImageBitmap(qr(addr + "/?pin=" + pin, dp(170)));
        for (int i = 0; i < 4; i++) pinDigits[i].setText(String.valueOf(pin.charAt(i)));
        pinCard.setVisibility(View.VISIBLE);
        pinCard.setAlpha(0f);
        pinCard.setTranslationY(-dp(10));
        pinCard.animate().alpha(1f).translationY(0).setDuration(250).start();
        handler.removeCallbacks(hidePin);
        handler.postDelayed(hidePin, 60_000);
    }

    private final Runnable hidePin = () -> pinCard.setVisibility(View.GONE);

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
        addCard(hifzFace(), v -> startActivity(new Intent(this, JuzAmmaActivity.class)));
        addCard(cardFace("⭐", "نجوم البنات", "نجمة لكل صلاة", 0xFFFFC857, 0xFFFF9E6B),
                v -> startActivity(new Intent(this, StarsActivity.class)));

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

    /** The Mushaf on its stand, with «احفظ مع الحصري» under it. */
    private View hifzFace() {
        FrameLayout f = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageResource(R.drawable.card_hifz);
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        TextView t = text(14, 0xFFF3E3B5, true);
        t.setText("احفظ مع الحصري");
        t.setPadding(0, dp(3), 0, dp(4));
        t.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xCC000000, 0x00000000}));
        f.addView(t, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return f;
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
        TextView photosPill = pill("📱  صفحة الموبايل");
        TextView testPill = pill("🧪  جرّب");
        testPill.setOnClickListener(v -> {
            pinCard.setVisibility(View.GONE);
            testCard.setVisibility(testCard.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            if (testCard.getVisibility() == View.VISIBLE) testCard.getChildAt(1).requestFocus();
        });
        photosPill.setOnClickListener(v -> {
            if (pinCard.getVisibility() == View.VISIBLE) pinCard.setVisibility(View.GONE); else showPinCard();
        });
        overlayPill = pill("🔔  فعّل الأذان فوق التطبيقات");
        overlayPill.setOnClickListener(v -> askOverlay());
        LinearLayout pills = new LinearLayout(this);
        pills.addView(overlayPill);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-2, -2);
        op.setMarginStart(dp(10));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.setMarginStart(dp(10));
        pills.addView(testPill, tp);
        pills.addView(photosPill, op);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.setMarginStart(dp(10));
        pills.addView(settings, sp);
        leftCol.addView(pills, endAligned());
        FrameLayout under = new FrameLayout(this);
        under.setClipChildren(false);
        under.addView(buildPhotoFrame());
        under.addView(buildPinCard(), new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        under.addView(buildTestCard(), new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        leftCol.addView(under, endAligned());
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

        msgBar = new LinearLayout(this);
        msgBar.setGravity(Gravity.CENTER_VERTICAL);
        msgBar.setPadding(dp(22), dp(14), dp(22), dp(14));
        msgBar.setBackground(round(0xF00B1A22, GOLD, 22));
        msgBar.setElevation(dp(24));
        msgBar.setVisibility(View.GONE);
        msgFrom = text(16, GOLD, true);
        msgBar.addView(msgFrom);
        msgText = text(24, WHITE, true);
        msgText.setGravity(Gravity.START);
        msgText.setMaxLines(2);
        LinearLayout.LayoutParams mtl = new LinearLayout.LayoutParams(0, -2, 1);
        mtl.setMargins(dp(18), 0, dp(18), 0);
        msgBar.addView(msgText, mtl);
        TextView ok = text(14, 0xFF10261F, true);
        ok.setText("دوس OK ✓");
        ok.setPadding(dp(14), dp(8), dp(14), dp(8));
        ok.setBackground(round(GOLD, 0, 16));
        msgBar.addView(ok);
        FrameLayout.LayoutParams mbl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        mbl.setMargins(dp(60), 0, dp(60), dp(24));
        overlay.addView(msgBar, mbl);

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
        int size = dp(112), overlap = dp(18);
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
            lp.topMargin = i == 0 ? 0 : dp(20);
            ring.setRotation(i == 0 ? -4f : 4f);
            pair.addView(ring, lp);
        }
        col.addView(pair, new LinearLayout.LayoutParams(size * 2 - overlap, size + dp(20)));

        famCard = new LinearLayout(this);
        famCard.setOrientation(LinearLayout.VERTICAL);
        famCard.setGravity(Gravity.CENTER_HORIZONTAL);
        famCard.setPadding(dp(16), dp(10), dp(16), dp(12));
        famCard.setBackground(round(0x990B1A22, 0x44E8C35A, 16));
        famCard.setVisibility(View.GONE);
        famTitle = text(16, GOLD, true);
        famKids = text(13, 0xFFF3E3B5, false);
        famKids.setPadding(0, dp(2), 0, dp(6));
        famDua = text(13, WHITE, false);
        famDua.setMaxLines(2);
        famDua.setLineSpacing(0, 1.15f);
        famCard.addView(famTitle);
        famCard.addView(famKids);
        famStars = new LinearLayout(this);
        famStars.setGravity(Gravity.CENTER);
        famStars.setPadding(0, dp(4), 0, dp(6));
        famCard.addView(famStars);
        famCard.addView(famDua);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(dp(300), -2);
        fl.setMargins(0, dp(10), 0, 0);
        col.addView(famCard, fl);

        photoHint = text(12, SOFT, false);
        photoHint.setShadowLayer(8, 0, 2, 0xAA000000);
        photoHint.setPadding(0, dp(8), 0, 0);
        photoHint.setMaxWidth(dp(300));
        col.addView(photoHint);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(14), 0, 0);
        col.setLayoutParams(lp);
        return col;
    }

    /** QR code (ZXing) so the iPhone camera opens the phone page straight away, PIN included. */
    private static Bitmap qr(String text, int size) {
        try {
            com.google.zxing.common.BitMatrix m = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
                            java.util.Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN, 0));
            Bitmap b = Bitmap.createBitmap(m.getWidth(), m.getHeight(), Bitmap.Config.RGB_565);
            for (int y = 0; y < m.getHeight(); y++)
                for (int x = 0; x < m.getWidth(); x++) b.setPixel(x, y, m.get(x, y) ? 0xFF0B1A22 : 0xFFFFFFFF);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** «🧪 جرّب»: try every feature right now from the remote. */
    private View buildTestCard() {
        testCard = new LinearLayout(this);
        testCard.setOrientation(LinearLayout.VERTICAL);
        testCard.setGravity(Gravity.CENTER_HORIZONTAL);
        testCard.setPadding(dp(18), dp(14), dp(18), dp(16));
        testCard.setBackground(round(0xF20B1A22, 0x88E8C35A, 18));
        testCard.setElevation(dp(20));
        testCard.setVisibility(View.GONE);
        TextView t = text(16, GOLD, true);
        t.setText("🧪 جرّب دلوقتي");
        t.setPadding(0, 0, 0, dp(8));
        testCard.addView(t);
        Object[][] items = {
                {"🕌  الأذان", (Runnable) () -> startActivity(new Intent(this, AdhanActivity.class)
                        .putExtra(AdhanActivity.EXTRA_NAME, nextPrayerName()))},
                {"☀️  أذكار الصباح", (Runnable) () -> AzkarActivity.open(this, true)},
                {"🌙  أذكار المساء", (Runnable) () -> AzkarActivity.open(this, false)},
                {"💌  رسالة تجريبية", (Runnable) () -> Messages.post(this, "dad", "رسالة تجريبية 🤍 لو شايفها يبقى كله تمام")},
                {"🎉  الاحتفال", (Runnable) () -> {
                    java.util.List<String> g = Stars.girls(this);
                    CelebrateActivity.show(this, g.isEmpty() ? "يا بطلة" : g.get(0), false);
                }},
        };
        for (Object[] it : items) {
            TextView b = text(15, WHITE, true);
            b.setText((String) it[0]);
            b.setPadding(dp(18), dp(9), dp(18), dp(9));
            b.setFocusable(true);
            b.setClickable(true);
            b.setBackground(round(0x22FFFFFF, 0, 14));
            b.setOnFocusChangeListener((v, has) -> {
                v.setBackground(round(has ? GOLD : 0x22FFFFFF, 0, 14));
                ((TextView) v).setTextColor(has ? 0xFF10261F : WHITE);
            });
            Runnable r = (Runnable) it[1];
            b.setOnClickListener(v -> { testCard.setVisibility(View.GONE); r.run(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(210), -2);
            lp.setMargins(0, dp(4), 0, dp(4));
            testCard.addView(b, lp);
        }
        return testCard;
    }

    private String nextPrayerName() {
        LocalDateTime now = LocalDateTime.now(zone());
        int[] t = prayer.minutesFor(now.toLocalDate());
        int m = now.getHour() * 60 + now.getMinute();
        for (int i = 0; i < 6; i++) if (PrayerTimes.IS_PRAYER[i] && t[i] > m) return PrayerTimes.NAMES[i];
        return PrayerTimes.NAMES[0];
    }

    /** Hidden until "📷 صور البيت" is pressed: the address to open on the phone and the 4-digit code. */
    private View buildPinCard() {
        pinCard = new LinearLayout(this);
        pinCard.setOrientation(LinearLayout.VERTICAL);
        pinCard.setGravity(Gravity.CENTER_HORIZONTAL);
        pinCard.setPadding(dp(20), dp(14), dp(20), dp(16));
        pinCard.setBackground(round(0xF20B1A22, 0x88E8C35A, 18));
        pinCard.setElevation(dp(20));
        pinCard.setVisibility(View.GONE);
        TextView t = text(16, GOLD, true);
        t.setText("📱 صفحة الموبايل");
        pinCard.addView(t);
        TextView t2 = text(12, SOFT, false);
        t2.setText("الرسايل، والنجوم، والصور، والأذان، والعيلة");
        pinCard.addView(t2);
        TextView s0 = text(14, WHITE, true);
        s0.setText("وجّه كاميرا الآيفون على الكود 👇");
        s0.setPadding(0, dp(10), 0, dp(6));
        pinCard.addView(s0);
        pinQr = new ImageView(this);
        pinQr.setBackgroundColor(0xFFFFFFFF);
        pinQr.setPadding(dp(8), dp(8), dp(8), dp(8));
        pinCard.addView(pinQr, new LinearLayout.LayoutParams(dp(186), dp(186)));
        TextView s1 = text(12, DIM, false);
        s1.setText("أو افتح من Safari وإنت على واي فاي البيت:");
        s1.setPadding(0, dp(8), 0, dp(2));
        pinCard.addView(s1);
        pinAddr = text(17, WHITE, true);
        pinAddr.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        pinCard.addView(pinAddr);
        TextView s2 = text(12, DIM, false);
        s2.setText("واكتب الرقم السري");
        s2.setPadding(0, dp(10), 0, dp(6));
        pinCard.addView(s2);
        LinearLayout digits = new LinearLayout(this);
        digits.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int i = 0; i < 4; i++) {
            TextView d = text(24, 0xFF10261F, true);
            d.setBackground(round(GOLD, 0, 10));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(40), dp(48));
            dl.setMargins(dp(4), 0, dp(4), 0);
            digits.addView(d, dl);
            pinDigits[i] = d;
        }
        pinCard.addView(digits);
        TextView s3 = text(11, DIM, false);
        s3.setText("بيختفي لوحده بعد دقيقة  •  رجوع للإغلاق");
        s3.setPadding(0, dp(10), 0, 0);
        pinCard.addView(s3);
        return pinCard;
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
