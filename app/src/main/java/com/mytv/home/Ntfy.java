package com.mytv.home;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Messages from anywhere (outside the home Wi-Fi) through ntfy.sh: the TV checks a secret channel
 * every few seconds. Title "ماما" = from Mama, anything else = from Baba.
 */
public final class Ntfy {
    private static final String SERVER = "https://ntfy.sh/";
    private static boolean started;

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("ntfy", Context.MODE_PRIVATE); }

    /** The secret channel name, made once on this TV. */
    public static String topic(Context c) {
        String t = sp(c).getString("topic", null);
        if (t == null) {
            String abc = "abcdefghijkmnpqrstuvwxyz23456789";
            SecureRandom r = new SecureRandom();
            StringBuilder sb = new StringBuilder("shashti-");
            for (int i = 0; i < 20; i++) sb.append(abc.charAt(r.nextInt(abc.length())));
            t = sb.toString();
            sp(c).edit().putString("topic", t).putString("since", String.valueOf(System.currentTimeMillis() / 1000)).apply();
        }
        return t;
    }

    public static String url(Context c) { return SERVER + topic(c); }

    public static synchronized void start(Context ctx) {
        if (started) return;
        started = true;
        Context app = ctx.getApplicationContext();
        topic(app);
        Thread t = new Thread(() -> {
            while (true) {
                try { poll(app); } catch (Exception ignored) { }
                try { Thread.sleep(8000); } catch (InterruptedException e) { return; }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void poll(Context c) throws Exception {
        String since = sp(c).getString("since", String.valueOf(System.currentTimeMillis() / 1000));
        HttpURLConnection h = (HttpURLConnection) new URL(SERVER + topic(c) + "/json?poll=1&since=" + since).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(15000);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(h.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                JSONObject m = new JSONObject(line);
                if (m.has("id")) sp(c).edit().putString("since", m.getString("id")).apply();
                if (!"message".equals(m.optString("event"))) continue;
                String text = m.optString("message").trim();
                if (text.isEmpty() || text.length() > 300) text = text.isEmpty() ? "" : text.substring(0, 300);
                if (text.isEmpty()) continue;
                String title = m.optString("title");
                String from = title.contains("ماما") || title.toLowerCase().contains("mom") ? "mom" : "dad";
                Messages.post(c, from, text);
            }
        } finally {
            h.disconnect();
        }
    }
}
