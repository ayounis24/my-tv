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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

/**
 * Morning / evening azkar, full screen. A sheikh's voice reads each zikr (from hisnmuslim.com),
 * the text scrolls along, and the ring counts every repetition.
 */
public class AzkarActivity extends Activity {
    public static final String EXTRA_MORNING = "morning";
    /** Repetitions read aloud; above this (the "100 times" ones) you continue the count with OK. */
    private static final int VOICE_MAX = 10;

    private boolean morning;
    private List<Azkar.Zikr> list;
    private int index = -1, count = 0;
    private boolean firstPass, manual, finished;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private AudioManager audio;
    private AudioFocusRequest focus;

    private int ink, soft, accent, glass;
    private TextView title, step, text, eveNote, num, ofNum, status, pauseBtn;
    private ScrollView scroll;
    private CounterRing ring;
    private FrameLayout ringBox;
    private ProgressBar progress;

    public static void launch(Context c, boolean morning) {
        String key = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) + (morning ? "m" : "e");
        android.content.SharedPreferences sp = c.getSharedPreferences("azkar", Context.MODE_PRIVATE);
        if (key.equals(sp.getString("last", ""))) return;
        sp.edit().putString("last", key).apply();
        open(c, morning);
    }

    public static void open(Context c, boolean morning) {
        try {
            c.startActivity(new Intent(c, AzkarActivity.class).putExtra(EXTRA_MORNING, morning)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (Exception ignored) { }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        morning = getIntent().getBooleanExtra(EXTRA_MORNING, true);
        list = Azkar.forTime(morning);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        Player p = Player.get(this);
        if (p.state == Player.State.PLAYING) p.togglePause();
        setContentView(buildUi());
        go(0);
    }

    // ---------- flow ----------

    private void go(int i) {
        stopAudio();
        handler.removeCallbacksAndMessages(null);
        if (i >= list.size()) { finishAll(); return; }
        if (i < 0) i = 0;
        index = i;
        count = 0;
        firstPass = true;
        manual = false;
        Azkar.Zikr z = list.get(i);

        step.setText((i + 1) + " / " + list.size());
        progress.setMax(list.size());
        progress.setProgress(i + 1);
        text.setText(z.text);
        text.setTextSize(z.text.length() > 380 ? 25 : z.text.length() > 220 ? 28 : 32);
        scroll.scrollTo(0, 0);
        boolean showEve = !morning && !z.evening.isEmpty();
        eveNote.setVisibility(showEve ? View.VISIBLE : View.GONE);
        if (showEve) eveNote.setText("🌙  في المساء قل: " + z.evening);
        text.setAlpha(0f);
        text.animate().alpha(1f).setDuration(500).start();
        updateCounter();
        status.setText("🔊  جاري التحميل…");
        pauseBtn.requestFocus();
        play(z);
    }

    private void play(Azkar.Zikr z) {
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(z.audioUrl());
            MediaPlayer me = mp;
            mp.setOnPreparedListener(x -> {
                if (x != mp) return;
                focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build();
                audio.requestAudioFocus(focus);
                x.start();
                status.setText("🔊  الشيخ بيقرأ");
                pauseBtn.setText("⏸");
                handler.post(watch);
            });
            mp.setOnCompletionListener(x -> { if (x == me) onRecitationDone(); });
            mp.setOnErrorListener((x, w, e) -> { if (x == me) goManual("الصوت مش متاح دلوقتي"); return true; });
            mp.prepareAsync();
        } catch (Exception e) {
            goManual("الصوت مش متاح دلوقتي");
        }
    }

    /** Follows the voice: scrolls the text on the first reading, and counts each repeat at the end of the zikr. */
    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if (mp == null) return;
            Azkar.Zikr z = list.get(index);
            int pos;
            try { pos = mp.getCurrentPosition(); } catch (Exception e) { return; }
            if (firstPass) {
                int max = scroll.getChildAt(0).getHeight() - scroll.getHeight();
                if (max > 0) scroll.smoothScrollTo(0, (int) (max * Math.min(1f, pos / (z.end * 1000f))));
            } else if (pos >= z.loopEnd * 1000) {
                onRecitationDone();
                return;
            }
            handler.postDelayed(this, 60);
        }
    };

    private void onRecitationDone() {
        Azkar.Zikr z = list.get(index);
        count++;
        firstPass = false;
        updateCounter();
        int voiced = Math.min(z.repeat, VOICE_MAX);
        if (count < voiced && mp != null) {
            try { mp.seekTo(0); if (!mp.isPlaying()) mp.start(); } catch (Exception ignored) { }
            handler.removeCallbacks(watch);
            handler.post(watch);
        } else if (count < z.repeat) {
            goManual(null);
        } else {
            stopAudio();
            status.setText("✓");
            handler.postDelayed(() -> go(index + 1), 1200);
        }
    }

    /** For the long counts (100 times) or when there is no sound: each OK press counts one. */
    private void goManual(String why) {
        stopAudio();
        manual = true;
        status.setText((why != null ? why + "  •  " : "") + "كمّل العدد: دوس OK مع كل مرة");
        ringBox.setFocusable(true);
        ringBox.requestFocus();
    }

    private void countByHand() {
        if (!manual) return;
        Azkar.Zikr z = list.get(index);
        count++;
        updateCounter();
        ring.animate().scaleX(1.08f).scaleY(1.08f).setDuration(80).withEndAction(() ->
                ring.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
        if (count >= z.repeat) {
            manual = false;
            ringBox.setFocusable(false);
            pauseBtn.requestFocus();
            status.setText("✓");
            handler.postDelayed(() -> go(index + 1), 900);
        }
    }

    private void updateCounter() {
        Azkar.Zikr z = list.get(index);
        num.setText(String.valueOf(count));
        ofNum.setText(z.repeat == 1 ? "مرة واحدة" : "من " + z.repeat);
        ring.setProgress(count / (float) z.repeat);
    }

    private void togglePause() {
        if (mp == null) return;
        try {
            if (mp.isPlaying()) { mp.pause(); pauseBtn.setText("▶"); status.setText("⏸  متوقف"); }
            else { mp.start(); pauseBtn.setText("⏸"); status.setText("🔊  الشيخ بيقرأ"); handler.post(watch); }
        } catch (Exception ignored) { }
    }

    private void finishAll() {
        finished = true;
        stopAudio();
        title.setText(morning ? "☀️  تقبّل الله منا ومنكم" : "🌙  تقبّل الله منا ومنكم");
        text.setText(morning ? "أصبحنا وأصبح الملك لله 🤍" : "أمسينا وأمسى الملك لله 🤍");
        text.setTextSize(34);
        eveNote.setVisibility(View.GONE);
        status.setText("");
        handler.postDelayed(this::finish, 8000);
    }

    private void stopAudio() {
        handler.removeCallbacks(watch);
        if (mp != null) { try { mp.release(); } catch (Exception ignored) { } mp = null; }
        if (focus != null) { audio.abandonAudioFocusRequest(focus); focus = null; }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        if (finished) return super.onKeyDown(keyCode, e);
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && manual && ringBox.hasFocus()) {
            countByHand();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) { go(index + 1); return true; }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) { go(index - 1); return true; }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { togglePause(); return true; }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        stopAudio();
    }

    // ---------- look ----------

    private View buildUi() {
        int[] bg;
        if (morning) {      // sunrise: soft cream to warm gold
            bg = new int[]{0xFFFFF4DC, 0xFFFDE2B4, 0xFFF4BE86};
            ink = 0xFF3A2410; soft = 0xB33A2410; accent = 0xFFB4651E; glass = 0x66FFFFFF;
        } else {            // evening: night blue to dusk violet
            bg = new int[]{0xFF0D0A28, 0xFF231A52, 0xFF4A2B6B};
            ink = 0xFFF4EEFF; soft = 0xB3F4EEFF; accent = 0xFFE8C35A; glass = 0x26FFFFFF;
        }
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bg));
        if (!morning) {
            View stars = new StarsView(this);
            root.addView(stars, new FrameLayout.LayoutParams(-1, -1));
        }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(56), dp(26), dp(56), dp(22));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        title = tv(morning ? "☀️  أذكار الصباح" : "🌙  أذكار المساء", 26, accent, true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        title.setGravity(Gravity.START);
        step = tv("", 18, soft, true);
        step.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        top.addView(step);
        col.addView(top);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(glass));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, dp(5));
        pl.setMargins(0, dp(10), 0, dp(14));
        col.addView(progress, pl);

        LinearLayout middle = new LinearLayout(this);
        middle.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(middle, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        text = tv("", 30, ink, true);
        text.setLineSpacing(0, 1.45f);
        text.setPadding(dp(8), dp(10), dp(8), dp(10));
        scroll.addView(text);
        textCol.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        eveNote = tv("", 16, ink, false);
        eveNote.setPadding(dp(16), dp(10), dp(16), dp(10));
        eveNote.setBackground(round(glass, 14));
        eveNote.setMaxLines(3);
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-1, -2);
        el.setMargins(0, dp(10), 0, 0);
        textCol.addView(eveNote, el);
        middle.addView(textCol, new LinearLayout.LayoutParams(0, -1, 1));

        // counter
        LinearLayout counter = new LinearLayout(this);
        counter.setOrientation(LinearLayout.VERTICAL);
        counter.setGravity(Gravity.CENTER);
        ringBox = new FrameLayout(this);
        ring = new CounterRing(this, glass, accent);
        ringBox.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout nums = new LinearLayout(this);
        nums.setOrientation(LinearLayout.VERTICAL);
        nums.setGravity(Gravity.CENTER);
        num = tv("0", 44, ink, true);
        ofNum = tv("", 14, soft, false);
        nums.addView(num);
        nums.addView(ofNum);
        ringBox.addView(nums, new FrameLayout.LayoutParams(-1, -1));
        ringBox.setOnFocusChangeListener((v, has) -> v.setBackground(has ? oval(glass) : null));
        counter.addView(ringBox, new LinearLayout.LayoutParams(dp(150), dp(150)));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(200), -2);
        cl.setMarginStart(dp(30));
        middle.addView(counter, cl);

        status = tv("", 16, soft, false);
        status.setPadding(0, dp(14), 0, dp(10));
        col.addView(status);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(button("⏮  اللي فات", v -> go(index - 1)));
        pauseBtn = button("⏸", v -> togglePause());
        controls.addView(pauseBtn);
        controls.addView(button("اللي بعده  ⏭", v -> go(index + 1)));
        controls.addView(button("✕  إغلاق", v -> finish()));
        col.addView(controls);
        return root;
    }

    private TextView button(String s, View.OnClickListener l) {
        TextView b = tv(s, 17, ink, true);
        b.setPadding(dp(22), dp(10), dp(22), dp(10));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(l);
        b.setBackground(round(glass, 24));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(round(has ? accent : glass, 24));
            ((TextView) v).setTextColor(has ? (morning ? 0xFFFFFFFF : 0xFF1A1235) : ink);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(8), 0, dp(8), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(r));
        return d;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** A few soft twinkling stars for the evening screen. */
    static class StarsView extends View {
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final float[][] s = new float[70][3];
        StarsView(Context c) {
            super(c);
            java.util.Random r = new java.util.Random(7);
            for (float[] a : s) { a[0] = r.nextFloat(); a[1] = r.nextFloat() * 0.6f; a[2] = r.nextFloat() * 6; }
            p.setColor(0xFFFFFFFF);
        }
        @Override protected void onDraw(android.graphics.Canvas c) {
            double t = System.currentTimeMillis() / 1000.0;
            for (float[] a : s) {
                p.setAlpha((int) (60 + 120 * (0.5 + 0.5 * Math.sin(t * 0.8 + a[2]))));
                c.drawCircle(a[0] * getWidth(), a[1] * getHeight(), 1.2f + (a[2] % 1.5f), p);
            }
            postInvalidateDelayed(80);
        }
    }
}
