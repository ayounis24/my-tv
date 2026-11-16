package com.mytv.home;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * One audio player for the whole app (Quran surahs and Ruqya from mp3quran.net).
 * Pauses on its own when another app (Netflix, YouTube...) starts playing sound.
 */
public final class Player {
    public enum State { IDLE, LOADING, PLAYING, PAUSED, ERROR }

    /** {name shown, name match on mp3quran.net, fallback server} — always the Hafs murattal recording. */
    public static final String[][] RECITERS = {
            {"ماهر المعيقلي", "المعيقلي", "https://cdn.mp3quran.net/audio/maher-muaiqly/r1/"},
            {"مشاري العفاسي", "العفاسي", "https://cdn.mp3quran.net/audio/mishary-alafasy/r1/"},
    };
    public static final String RECITER = RECITERS[0][0];

    private static Player instance;
    public static Player get(Context ctx) {
        if (instance == null) instance = new Player(ctx.getApplicationContext());
        return instance;
    }

    private final AudioManager audio;
    private final Context ctx;
    private int retries = 0;
    private int resumeAt = 0;
    private boolean pausedByOthers = false;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private AudioFocusRequest focus;
    private final String[] servers = new String[RECITERS.length];
    public int reciter = 0;

    public State state = State.IDLE;
    public String title = "";
    public String subtitle = "";
    public String key = "";
    public boolean live = false;

    private Player(Context ctx) {
        this.ctx = ctx;
        audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    }

    /** Play surah number n (1..114). Pressing the same item again pauses/resumes. */
    public void playSurah(int n, String name) { playSurah(n, name, reciter); }

    public void playSurah(int n, String name, int r) {
        if (toggleIfSame("s" + r + "_" + n)) return;
        reciter = r;
        start("s" + r + "_" + n, "سورة " + name, RECITERS[r][0], false, () -> surahUrl(r, n));
    }

    /** Ruqya: a live mp3quran radio station whose name contains الرقية. */
    public void playRuqya() {
        if (toggleIfSame("ruqya")) return;
        start("ruqya", "الرقية الشرعية", "بث مباشر", true, () -> radioUrl("الرقية"));
    }

    public void togglePause() {
        if (mp == null) return;
        pausedByOthers = false;
        if (state == State.PLAYING) { mp.pause(); state = State.PAUSED; }
        else if (state == State.PAUSED && requestFocus()) { mp.start(); state = State.PLAYING; }
    }

    public void stop() {
        if (mp != null) { try { mp.release(); } catch (Exception ignored) { } }
        mp = null;
        if (focus != null) audio.abandonAudioFocusRequest(focus);
        state = State.IDLE;
        key = "";
    }

    public int audioSession() { try { return mp != null ? mp.getAudioSessionId() : 0; } catch (Exception e) { return 0; } }
    public int surah() { return key.startsWith("s") && key.contains("_") ? Integer.parseInt(key.substring(key.indexOf('_') + 1)) : 0; }

    public int positionMs() { try { return mp != null ? mp.getCurrentPosition() : 0; } catch (Exception e) { return 0; } }
    public int durationMs() { try { return mp != null && !live ? mp.getDuration() : 0; } catch (Exception e) { return 0; } }

    private boolean toggleIfSame(String k) {
        if (k.equals(key) && (state == State.PLAYING || state == State.PAUSED)) { togglePause(); return true; }
        return false;
    }

    private interface UrlSource { String url() throws Exception; }

    private void start(String k, String t, String sub, boolean isLive, UrlSource src) {
        stop();
        retries = 0; resumeAt = 0;
        key = k; title = t; subtitle = sub; live = isLive;
        state = State.LOADING;
        new Thread(() -> {
            String url;
            try { url = src.url(); } catch (Exception e) { url = null; }
            final String u = url;
            main.post(() -> {
                if (!k.equals(key)) return; // user picked something else meanwhile
                if (u == null) { state = State.ERROR; return; }
                prepare(k, u);
            });
        }).start();
    }

    private void prepare(String k, String url) {
        try {
            MediaPlayer p = new MediaPlayer();
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            p.setDataSource(url);
            // keep playing even if the TV dims / goes to its screensaver
            try { p.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK); } catch (Exception ignored) { }
            p.setOnPreparedListener(x -> {
                if (!k.equals(key)) { x.release(); return; }
                if (resumeAt > 0) { try { x.seekTo(resumeAt); } catch (Exception ignored) { } }
                if (requestFocus()) { x.start(); state = State.PLAYING; } else state = State.PAUSED;
            });
            p.setOnCompletionListener(x -> {
                if (!k.equals(key)) return;
                // a dropped connection can look like "finished" in the middle — pick up where it stopped
                int pos = position(x), dur = duration(x);
                if (dur > 0 && pos > 0 && pos < dur - 5000 && retry(k, url, pos)) return;
                next();
            });
            p.setOnErrorListener((x, w, e) -> {
                if (!k.equals(key)) return true;
                int pos = position(x), dur = duration(x);
                if (dur > 0 && pos >= dur - 5000) { next(); return true; }   // error right at the end: just go on
                if (!retry(k, url, pos)) {
                    if (pos > 60000) next();          // played a good part already: skip ahead instead of stopping
                    else state = State.ERROR;
                }
                return true;
            });
            mp = p;
            p.prepareAsync();
        } catch (Exception e) {
            state = State.ERROR;
        }
    }

    /** Next surah in the mushaf (stops after An-Nas). */
    private void next() {
        int n = surah();
        if (n > 0 && n < 114) { key = ""; playSurah(n + 1, Surahs.NAMES[n], reciter); } else stop();
    }

    /** Reconnects to the same surah (up to 3 times) and continues from where it stopped. */
    private boolean retry(String k, String url, int pos) {
        if (retries >= 3) return false;
        retries++;
        resumeAt = live ? 0 : Math.max(0, pos - 2000);
        state = State.LOADING;
        if (mp != null) { try { mp.release(); } catch (Exception ignored) { } mp = null; }
        main.postDelayed(() -> { if (k.equals(key)) prepare(k, url); }, 2500L * retries);
        return true;
    }

    private static int position(MediaPlayer x) { try { return x.getCurrentPosition(); } catch (Exception e) { return 0; } }
    private static int duration(MediaPlayer x) { try { return x.getDuration(); } catch (Exception e) { return 0; } }

    private boolean requestFocus() {
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(change -> {
                    if (change <= 0 && mp != null && state == State.PLAYING) {
                        mp.pause(); state = State.PAUSED;
                        // short interruptions (a notification, a voice search) resume by themselves
                        pausedByOthers = change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
                    } else if (change == AudioManager.AUDIOFOCUS_GAIN && pausedByOthers && mp != null && state == State.PAUSED) {
                        pausedByOthers = false;
                        try { mp.start(); state = State.PLAYING; } catch (Exception ignored) { }
                    }
                }).build();
        return audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private String surahUrl(int r, int n) {
        if (servers[r] == null) {
            try {
                JSONArray rs = new JSONObject(get("https://www.mp3quran.net/api/v3/reciters?language=ar")).getJSONArray("reciters");
                for (int i = 0; i < rs.length() && servers[r] == null; i++) {
                    JSONObject rec = rs.getJSONObject(i);
                    if (!rec.optString("name").contains(RECITERS[r][1])) continue;
                    JSONArray ms = rec.getJSONArray("moshaf");
                    for (int j = 0; j < ms.length(); j++) {
                        JSONObject m = ms.getJSONObject(j);
                        String mn = m.optString("name");
                        // Hafs 'an 'Asim, murattal, complete (not mujawwad / mu'allim / other riwayat)
                        if (mn.contains("حفص") && mn.contains("مرتل") && m.optInt("surah_total") == 114) { servers[r] = m.getString("server"); break; }
                    }
                }
            } catch (Exception ignored) { }
            if (servers[r] == null) servers[r] = RECITERS[r][2];
        }
        String sv = servers[r];
        return (sv.endsWith("/") ? sv : sv + "/") + String.format(Locale.US, "%03d.mp3", n);
    }

    private static String radioUrl(String match) throws Exception {
        JSONArray rs = new JSONObject(get("https://www.mp3quran.net/api/v3/radios?language=ar")).getJSONArray("radios");
        for (int i = 0; i < rs.length(); i++) {
            JSONObject r = rs.getJSONObject(i);
            if (r.optString("name").contains(match)) return r.getString("url");
        }
        return null;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
