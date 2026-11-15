package com.mytv.home;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Current weather from Open-Meteo (free, no key). Fields stay at defaults if the fetch fails. */
public final class Weather {
    public static final String CLEAR = "clear", CLOUDY = "cloudy", RAIN = "rain", DUST = "dust";

    public int tempC = Integer.MIN_VALUE;
    public String condition = CLEAR;
    public boolean isDay = true;

    public boolean hasTemp() { return tempC != Integer.MIN_VALUE; }

    public String icon() {
        switch (condition) {
            case RAIN: return "🌧";
            case CLOUDY: return "☁";
            case DUST: return "🌫";
            default: return isDay ? "☀" : "☾";
        }
    }

    public String label() {
        switch (condition) {
            case RAIN: return "مطر";
            case CLOUDY: return "غيم";
            case DUST: return "غبار";
            default: return "صحو";
        }
    }

    public static Weather fetch(double lat, double lon) {
        Weather w = new Weather();
        try {
            JSONObject cur = new JSONObject(get(String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day",
                    lat, lon))).getJSONObject("current");
            w.tempC = (int) Math.round(cur.getDouble("temperature_2m"));
            w.isDay = cur.optInt("is_day", 1) == 1;
            int code = cur.getInt("weather_code");
            if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95) w.condition = RAIN;
            else if (code == 2 || code == 3 || code == 45 || code == 48) w.condition = CLOUDY;
        } catch (Exception ignored) {
            return w;
        }
        if (CLEAR.equals(w.condition)) {
            try {
                JSONObject aq = new JSONObject(get(String.format(Locale.US,
                        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=dust",
                        lat, lon))).getJSONObject("current");
                if (aq.optDouble("dust", 0) > 250) w.condition = DUST;
            } catch (Exception ignored) { }
        }
        return w;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
