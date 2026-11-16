package com.mytv.home;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
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
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Locale;

/**
 * Juz Amma for the girls: Sheikh Al-Husary (Al-Muallim) reads verse by verse,
 * each verse three times in a row, the verse big on screen. Audio from everyayah.com.
 */
public class JuzAmmaActivity extends Activity {
    private static final int REPEAT = 3;
    private static final String AUDIO = "https://everyayah.com/data/Husary_Muallim_128kbps/%03d%03d.mp3";
    private static final int[] PALETTE = {0xFFFF8FB1, 0xFF7FC8F8, 0xFFFFC857, 0xFF8BD3A6, 0xFFB79CED, 0xFFFF9E6B};
    private static final int INK = 0xFF22314D;

    private JSONArray surahs;
    private String basmala;
    private float basmalaEnd;

    private int surahIdx = -1, ayah = 0, rep = 0;   // ayah 0 = basmala
    private boolean done;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private float curEnd;
    private AudioManager audio;
    private AudioFocusRequest focus;

    private FrameLayout picker, reader;
    private GridView grid;
    private TextView title, step, verse, nextVerse, pauseBtn, celebrate;
    private LinearLayout dots;
    private LinearLayout card;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            InputStream in = getAssets().open("juz_amma.json");
            byte[] buf = new byte[in.available()];
            int r = 0; while (r < buf.length) { int k = in.read(buf, r, buf.length - r); if (k < 0) break; r += k; }
            in.close();
            JSONObject j = new JSONObject(new String(buf, "UTF-8"));
            surahs = j.getJSONArray("surahs");
            basmala = j.getJSONArray("basmala").getString(0);
            basmalaEnd = (float) j.getJSONArray("basmala").getDouble(1);
        } catch (Exception e) { finish(); return; }
        Player p = Player.get(this);
        if (p.state == Player.State.PLAYING) p.togglePause();
        setContentView(buildUi());
        showPicker();
    }

    private SharedPreferences prefs() { return getSharedPreferences("juz", Context.MODE_PRIVATE); }

    // ---------- choosing a surah ----------

    private void showPicker() {
        stopAudio();
        reader.setVisibility(View.GONE);
        picker.setVisibility(View.VISIBLE);
        int last = prefs().getInt("last", surahs.length() - 1);
        grid.setSelection(gridPos(last));
        grid.requestFocus();
    }

    /** The grid starts from An-Nas (how kids usually begin) and goes up to An-Naba. */
    private int surahAt(int gridPos) { return surahs.length() - 1 - gridPos; }
    private int gridPos(int idx) { return surahs.length() - 1 - idx; }

    private String surahName(int idx) {
        int n = surahs.optJSONObject(idx).optInt("n");
        return Surahs.NAMES[n - 1];
    }

    // ---------- reading ----------

    private void startSurah(int idx) {
        surahIdx = idx;
        prefs().edit().putInt("last", idx).apply();
        picker.setVisibility(View.GONE);
        reader.setVisibility(View.VISIBLE);
        celebrate.setVisibility(View.GONE);
        card.setVisibility(View.VISIBLE);
        done = false;
        title.setText("سورة " + surahName(idx));
        playVerse(0, 0);
        pauseBtn.requestFocus();
    }

    private JSONArray ayat() { return surahs.optJSONObject(surahIdx).optJSONArray("ayat"); }

    private void playVerse(int a, int r) {
        ayah = a;
        rep = r;
        JSONArray ay = ayat();
        int count = ay.length();
        String text;
        String url;
        if (a == 0) {
            text = basmala;
            curEnd = basmalaEnd;
            url = String.format(Locale.US, AUDIO, 1, 1);
            step.setText("");
        } else {
            JSONArray v = ay.optJSONArray(a - 1);
            text = v.optString(0) + "  ﴿" + a + "﴾";
            curEnd = (float) v.optDouble(1);
            int n = surahs.optJSONObject(surahIdx).optInt("n");
            url = String.format(Locale.US, AUDIO, n, a);
            step.setText("آية " + a + " من " + count);
        }
        if (r == 0) {
            verse.setText(text);
            verse.setTextSize(text.length() > 120 ? 30 : text.length() > 70 ? 36 : 44);
            card.setScaleX(0.96f); card.setScaleY(0.96f); card.setAlpha(0f);
            card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350).start();
            nextVerse.setText(a < count ? ay.optJSONArray(a).optString(0) : "");
        }
        updateDots(a == 0 ? -1 : r);
        stopAudio();
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(url);
            MediaPlayer me = mp;
            mp.setOnPreparedListener(x -> {
                if (x != mp) return;
                if (focus == null) {
                    focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build();
                    audio.requestAudioFocus(focus);
                }
                x.start();
                pauseBtn.setText("⏸");
                handler.post(watch);
            });
            mp.setOnCompletionListener(x -> { if (x == me) verseDone(); });
            mp.setOnErrorListener((x, w, e) -> { if (x == me) handler.postDelayed(this::verseDone, 1500); return true; });
            mp.prepareAsync();
        } catch (Exception e) {
            handler.postDelayed(this::verseDone, 1500);
        }
    }

    /** Stops at the end of the sheikh's voice (the recording has a pause after it for repeating). */
    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if (mp == null) return;
            try {
                if (mp.isPlaying() && mp.getCurrentPosition() >= curEnd * 1000) { verseDone(); return; }
            } catch (Exception ignored) { }
            handler.postDelayed(this, 40);
        }
    };

    private void verseDone() {
        handler.removeCallbacks(watch);
        int count = ayat().length();
        if (ayah == 0) { playVerse(1, 0); return; }
        if (rep + 1 < REPEAT) {
            rep++;
            updateDots(rep);
            try { mp.seekTo(0); mp.start(); handler.post(watch); return; } catch (Exception e) { playVerse(ayah, rep); return; }
        }
        if (ayah < count) playVerse(ayah + 1, 0);
        else finishSurah();
    }

    private void finishSurah() {
        stopAudio();
        done = true;
        card.setVisibility(View.GONE);
        dots.removeAllViews();
        step.setText("");
        celebrate.setText("🌟 أحسنتِ! 🌟\nخلّصنا سورة " + surahName(surahIdx) + "\n\nOK: نعيدها   •   ⏭: السورة اللي بعدها");
        celebrate.setVisibility(View.VISIBLE);
        celebrate.setScaleX(0.6f); celebrate.setScaleY(0.6f);
        celebrate.animate().scaleX(1f).scaleY(1f).setDuration(500).start();
        Chime.play();
        pauseBtn.setText("↻");
    }

    private void updateDots(int r) {
        dots.removeAllViews();
        if (r < 0) return;
        for (int i = 0; i < REPEAT; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i <= r ? PALETTE[(ayah + i) % PALETTE.length] : 0x55FFFFFF);
            g.setStroke(dp(2), 0xFFFFFFFF);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(22), dp(22));
            lp.setMargins(dp(6), 0, dp(6), 0);
            dots.addView(d, lp);
        }
    }

    private void togglePause() {
        if (done) { startSurah(surahIdx); return; }
        if (mp == null) return;
        try {
            if (mp.isPlaying()) { mp.pause(); pauseBtn.setText("▶"); }
            else { mp.start(); pauseBtn.setText("⏸"); handler.post(watch); }
        } catch (Exception ignored) { }
    }

    private void jump(int delta) {
        if (done) { if (delta > 0 && surahIdx > 0) startSurah(surahIdx - 1); return; } // kids go An-Nas → An-Naba
        int a = Math.max(1, Math.min(ayat().length(), ayah + delta));
        playVerse(a, 0);
    }

    private void stopAudio() {
        handler.removeCallbacks(watch);
        if (mp != null) { try { mp.release(); } catch (Exception ignored) { } mp = null; }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (reader.getVisibility() == View.VISIBLE) { showPicker(); return true; }
            finish();
            return true;
        }
        if (reader.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { togglePause(); return true; }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) { jump(1); return true; }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { jump(-1); return true; }
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        stopAudio();
        if (focus != null) audio.abandonAudioFocusRequest(focus);
    }

    // ---------- look ----------

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFAEE2FF, 0xFFFFD6EA, 0xFFFFF3C4}));
        root.addView(new SkyView(this), new FrameLayout.LayoutParams(-1, -1));

        // --- surah picker
        picker = new FrameLayout(this);
        LinearLayout pc = new LinearLayout(this);
        pc.setOrientation(LinearLayout.VERTICAL);
        pc.setPadding(dp(50), dp(26), dp(50), dp(20));
        TextView pt = tv("🌈 يلا نحفظ جزء عمّ", 32, INK, true);
        pc.addView(pt);
        TextView ps = tv("مع الشيخ الحصري  •  كل آية بتتكرر 3 مرات  •  اختاري السورة", 16, 0xCC22314D, false);
        ps.setPadding(0, dp(4), 0, dp(16));
        pc.addView(ps);
        grid = new GridView(this);
        grid.setNumColumns(6);
        grid.setHorizontalSpacing(dp(12));
        grid.setVerticalSpacing(dp(12));
        grid.setSelector(round(0x00000000, dp(22), 0xFFFFFFFF, dp(4)));
        grid.setDrawSelectorOnTop(true);
        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return surahs.length(); }
            @Override public Object getItem(int i) { return i; }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int pos, View conv, ViewGroup parent) {
                TextView t = conv instanceof TextView ? (TextView) conv : tv("", 20, 0xFFFFFFFF, true);
                t.setText(surahName(surahAt(pos)));
                t.setPadding(dp(6), dp(16), dp(6), dp(16));
                t.setShadowLayer(4, 0, 1, 0x55000000);
                t.setBackground(round(PALETTE[pos % PALETTE.length], dp(22), 0, 0));
                return t;
            }
        });
        grid.setOnItemClickListener((p, v, pos, id) -> startSurah(surahAt(pos)));
        pc.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        picker.addView(pc, new FrameLayout.LayoutParams(-1, -1));
        root.addView(picker, new FrameLayout.LayoutParams(-1, -1));

        // --- reader
        reader = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(50), dp(22), dp(50), dp(20));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        title = tv("", 24, 0xFFFFFFFF, true);
        title.setPadding(dp(22), dp(8), dp(22), dp(8));
        title.setBackground(round(0xFFFF8FB1, dp(22), 0, 0));
        top.addView(title);
        View sp = new View(this);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
        step = tv("", 18, INK, true);
        top.addView(step);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout mid = new FrameLayout(this);
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(30), dp(26), dp(30), dp(22));
        card.setBackground(round(0xF2FFFFFF, dp(30), 0, 0));
        card.setElevation(dp(10));
        verse = tv("", 44, INK, true);
        verse.setLineSpacing(0, 1.4f);
        card.addView(verse);
        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, dp(18), 0, 0);
        card.addView(dots);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        mid.addView(card, cl);
        celebrate = tv("", 34, INK, true);
        celebrate.setLineSpacing(0, 1.3f);
        celebrate.setVisibility(View.GONE);
        mid.addView(celebrate, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        col.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));

        nextVerse = tv("", 18, 0x9922314D, false);
        nextVerse.setMaxLines(1);
        nextVerse.setPadding(0, 0, 0, dp(12));
        col.addView(nextVerse);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(button("⏮  الآية اللي فاتت", 0xFF7FC8F8, v -> jump(-1)));
        pauseBtn = button("⏸", 0xFFFF8FB1, v -> togglePause());
        controls.addView(pauseBtn);
        controls.addView(button("الآية اللي بعدها  ⏭", 0xFF8BD3A6, v -> jump(1)));
        controls.addView(button("📚 السور", 0xFFFFC857, v -> showPicker()));
        col.addView(controls);
        reader.addView(col, new FrameLayout.LayoutParams(-1, -1));
        reader.setVisibility(View.GONE);
        root.addView(reader, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    private TextView button(String s, int color, View.OnClickListener l) {
        TextView b = tv(s, 17, 0xFFFFFFFF, true);
        b.setShadowLayer(3, 0, 1, 0x44000000);
        b.setPadding(dp(22), dp(11), dp(22), dp(11));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(l);
        b.setBackground(round(color, dp(24), 0, 0));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(round(color, dp(24), has ? 0xFFFFFFFF : 0, dp(4)));
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(120).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(9), 0, dp(9), 0);
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

    private GradientDrawable round(int color, int r, int stroke, int sw) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(r);
        if (stroke != 0) d.setStroke(sw, stroke);
        return d;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    /** Soft drifting clouds and twinkling little stars. */
    static class SkyView extends View {
        private final Paint cloud = new Paint(Paint.ANTI_ALIAS_FLAG), star = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[][] stars = new float[26][3];
        SkyView(Context c) {
            super(c);
            cloud.setColor(0xB3FFFFFF);
            star.setColor(0xFFFFD45C);
            java.util.Random r = new java.util.Random(3);
            for (float[] s : stars) { s[0] = r.nextFloat(); s[1] = r.nextFloat(); s[2] = r.nextFloat() * 6; }
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            double t = System.currentTimeMillis() / 1000.0;
            for (int i = 0; i < 4; i++) {
                float x = (float) (((t * (8 + i * 3) + i * 400) % (w + 400)) - 200);
                float y = h * (0.12f + i * 0.2f);
                float s = h * (0.05f + 0.01f * i);
                c.drawCircle(x, y, s, cloud);
                c.drawCircle(x + s * 1.1f, y - s * 0.4f, s * 1.2f, cloud);
                c.drawCircle(x + s * 2.2f, y, s * 0.9f, cloud);
            }
            for (float[] s : stars) {
                star.setAlpha((int) (90 + 140 * (0.5 + 0.5 * Math.sin(t * 1.3 + s[2]))));
                c.drawCircle(s[0] * w, s[1] * h, 3 + (s[2] % 2), star);
            }
            postInvalidateDelayed(50);
        }
    }
}
