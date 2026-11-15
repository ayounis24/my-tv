package com.mytv.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Calendar;

/** Full-screen adhan: Egyptian mosque photo, the prayer name, the adhan voice, then the dua after the adhan. */
public class AdhanActivity extends Activity {
    public static final String EXTRA_NAME = "name";
    private static final int GOLD = 0xFFE8C35A;
    private static final String DUA = "اللهم رب هذه الدعوة التامة، والصلاة القائمة،\nآتِ محمدًا الوسيلة والفضيلة،\nوابعثه مقامًا محمودًا الذي وعدته";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private TextView title, prayer, sub, button, dua;
    private EqView eq;
    private boolean duaShown;

    /** Opens the adhan screen once per prayer (the alarm and the open app can both ask). */
    public static void launch(Context c, String name) {
        String key = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) + "-" + name;
        android.content.SharedPreferences sp = c.getSharedPreferences("adhan", Context.MODE_PRIVATE);
        if (key.equals(sp.getString("last", ""))) return;
        sp.edit().putString("last", key).apply();
        try {
            c.startActivity(new Intent(c, AdhanActivity.class).putExtra(EXTRA_NAME, name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (Exception ignored) { }
    }

    public static File adhanFile(Context c) {
        return new File(c.getFilesDir(), "adhan.audio");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (name == null) name = "";
        setContentView(buildUi(name));
        Player p = Player.get(this);
        if (p.state == Player.State.PLAYING) p.togglePause();
        play();
        handler.postDelayed(this::showDua, 6 * 60_000); // safety: never stay longer than the adhan
    }

    private void play() {
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            File f = adhanFile(this);
            if (f.exists() && f.length() > 0) mp.setDataSource(f.getPath());
            else mp.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            mp.setOnPreparedListener(x -> {
                focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build();
                audio.requestAudioFocus(focus);
                x.start();
                eq.setActive(true);
            });
            mp.setOnCompletionListener(x -> showDua());
            mp.setOnErrorListener((x, w, e) -> { showDua(); return true; });
            mp.prepareAsync();
        } catch (Exception e) {
            showDua();
        }
    }

    private void stopSound() {
        if (mp != null) { try { mp.release(); } catch (Exception ignored) { } mp = null; }
        if (focus != null) audio.abandonAudioFocusRequest(focus);
        eq.setActive(false);
    }

    private void showDua() {
        if (duaShown) return;
        duaShown = true;
        stopSound();
        title.animate().alpha(0f).setDuration(600).start();
        sub.animate().alpha(0f).setDuration(600).start();
        eq.animate().alpha(0f).setDuration(600).start();
        prayer.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            title.setText("الدعاء بعد الأذان");
            title.animate().alpha(1f).setDuration(800).start();
            dua.setVisibility(View.VISIBLE);
            dua.setAlpha(0f);
            dua.animate().alpha(1f).setDuration(1200).start();
            button.setText("إغلاق");
        }).start();
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::finish, 60_000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { if (duaShown) finish(); else showDua(); return true; }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        stopSound();
    }

    private View buildUi(String name) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF06120F);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        int[] photos = mosquePhotos();
        if (photos.length > 0) {
            ImageView bg = new ImageView(this);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bg.setImageResource(photos[(int) (System.currentTimeMillis() / 60000 % photos.length)]);
            root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
            bg.setScaleX(1.08f); bg.setScaleY(1.08f);
            bg.animate().scaleX(1f).scaleY(1f).setDuration(60_000).start(); // slow gentle zoom
        }
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0xB3000000, 0xE6000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView moon = text("☾", 54, GOLD, false);
        col.addView(moon);
        title = text("حان الآن موعد أذان", 30, 0xFFF5F1E6, false);
        col.addView(title);
        prayer = text(name, 76, GOLD, true);
        prayer.setShadowLayer(24, 0, 0, 0x88E8C35A);
        col.addView(prayer);
        sub = text("حسب توقيت " + BuildConfig.CITY_NAME, 20, 0xCCFFFFFF, false);
        col.addView(sub);

        dua = text(DUA, 30, 0xFFF5F1E6, true);
        dua.setLineSpacing(0, 1.35f);
        dua.setVisibility(View.GONE);
        col.addView(dua);

        eq = new EqView(this, GOLD);
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(dp(200), dp(44));
        el.setMargins(0, dp(26), 0, dp(26));
        col.addView(eq, el);

        button = text("⏹  إيقاف الأذان", 20, 0xFF10261F, true);
        button.setPadding(dp(28), dp(12), dp(28), dp(12));
        button.setFocusable(true);
        button.setClickable(true);
        GradientDrawable bd = new GradientDrawable();
        bd.setColor(GOLD);
        bd.setCornerRadius(dp(26));
        button.setBackground(bd);
        button.setOnClickListener(v -> { if (duaShown) finish(); else showDua(); });
        col.addView(button, new LinearLayout.LayoutParams(-2, -2));
        button.requestFocus();
        return root;
    }

    private int[] mosquePhotos() {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            int id = getResources().getIdentifier("mosque_" + i, "drawable", getPackageName());
            if (id != 0) ids.add(id);
        }
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) out[i] = ids.get(i);
        return out;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
