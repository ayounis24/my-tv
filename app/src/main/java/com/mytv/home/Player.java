package com.mytv.home;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

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

    public static final String RECITER = "مشاري العفاسي";
    private static final String RECITER_MATCH = "العفاسي";
    private static final String FALLBACK_SERVER = "https://server8.mp3quran.net/afs/";

    private static Player instance;
    public static Player get(Context ctx) {
        if (instance == null) instance = new Player(ctx.getApplicationContext());
        return instance;
    }

    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private AudioFocusRequest focus;
    private String server;

    public State state = State.IDLE;
    public String title = "";
    public String subtitle = "";
    public String key = "";
    public boolean live = false;

    private Player(Context ctx) {
        audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    }

    /** Play surah number n (1..114). Pressing the same item again pauses/resumes. */
    public void playSurah(int n, String name) {
        if (toggleIfSame("surah" + n)) return;
        start("surah" + n, "سورة " + name, RECITER, false, () -> surahUrl(n));
    }

    /** Ruqya: a live mp3quran radio station whose name contains الرقية. */
    public void playRuqya() {
        if (toggleIfSame("ruqya")) return;
        start("ruqya", "الرقية الشرعية", "بث مباشر", true, () -> radioUrl("الرقية"));
    }

    public void togglePause() {
        if (mp == null) return;
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

    public int positionMs() { try { return mp != null ? mp.getCurrentPosition() : 0; } catch (Exception e) { return 0; } }
    public int durationMs() { try { return mp != null && !live ? mp.getDuration() : 0; } catch (Exception e) { return 0; } }

    private boolean toggleIfSame(String k) {
        if (k.equals(key) && (state == State.PLAYING || state == State.PAUSED)) { togglePause(); return true; }
        return false;
    }

    private interface UrlSource { String url() throws Exception; }

    private void start(String k, String t, String sub, boolean isLive, UrlSource src) {
        stop();
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
            p.setOnPreparedListener(x -> {
                if (!k.equals(key)) { x.release(); return; }
                if (requestFocus()) { x.start(); state = State.PLAYING; } else state = State.PAUSED;
            });
            p.setOnCompletionListener(x -> stop());
            p.setOnErrorListener((x, w, e) -> { state = State.ERROR; return true; });
            mp = p;
            p.prepareAsync();
        } catch (Exception e) {
            state = State.ERROR;
        }
    }

    private boolean requestFocus() {
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(change -> {
                    if (change <= 0 && mp != null && state == State.PLAYING) { mp.pause(); state = State.PAUSED; }
                }).build();
        return audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private String surahUrl(int n) {
        if (server == null) {
            try {
                JSONArray rs = new JSONObject(get("https://www.mp3quran.net/api/v3/reciters?language=ar")).getJSONArray("reciters");
                for (int i = 0; i < rs.length() && server == null; i++) {
                    JSONObject r = rs.getJSONObject(i);
                    if (!r.optString("name").contains(RECITER_MATCH)) continue;
                    JSONArray ms = r.getJSONArray("moshaf");
                    for (int j = 0; j < ms.length(); j++) {
                        JSONObject m = ms.getJSONObject(j);
                        if (("," + m.optString("surah_list") + ",").contains("," + n + ",")) { server = m.getString("server"); break; }
                    }
                }
            } catch (Exception ignored) { }
            if (server == null) server = FALLBACK_SERVER;
        }
        return (server.endsWith("/") ? server : server + "/") + String.format(Locale.US, "%03d.mp3", n);
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
