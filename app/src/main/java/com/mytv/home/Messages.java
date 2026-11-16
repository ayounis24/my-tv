package com.mytv.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONObject;

/** A message from Baba or Mama's phone: kept until someone presses OK on the TV, then marked as seen. */
public final class Messages {
    static final String ACTION = "com.mytv.home.MESSAGE";

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("msg", Context.MODE_PRIVATE); }

    /** {from: "dad"|"mom", text, at, seen} or empty. */
    public static JSONObject current(Context c) {
        try { return new JSONObject(sp(c).getString("cur", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    public static boolean pending(Context c) {
        JSONObject m = current(c);
        return m.has("text") && !m.has("seen");
    }

    public static void post(Context c, String from, String text) {
        try {
            JSONObject m = new JSONObject().put("from", from).put("text", text).put("at", System.currentTimeMillis());
            sp(c).edit().putString("cur", m.toString()).apply();
        } catch (Exception ignored) { }
        Chime.play();
        c.sendBroadcast(new Intent(ACTION).setPackage(c.getPackageName()));
        if (!MainActivity.visible) new Handler(Looper.getMainLooper()).post(() -> showOverlay(c));
    }

    public static void markSeen(Context c) {
        try {
            JSONObject m = current(c);
            if (m.has("text") && !m.has("seen")) sp(c).edit().putString("cur", m.put("seen", System.currentTimeMillis()).toString()).apply();
        } catch (Exception ignored) { }
    }

    /** "بابا" / "ماما", with the name typed on the phone page if there is one. */
    public static String senderLabel(Context c, String from) {
        JSONObject fam = PhotoServer.family(c);
        boolean dad = "dad".equals(from);
        String name = fam.optString(dad ? "dad" : "mom").trim();
        return (dad ? "بابا" : "ماما") + (name.isEmpty() ? "" : " " + name);
    }

    // While YouTube / Pablo is on screen: a small bar on top of it for 90 s (needs "display over other apps").
    // It can't take the OK press there, so the message stays waiting on شاشتي until someone presses OK.
    private static View overlay;

    private static void showOverlay(Context c) {
        if (Build.VERSION.SDK_INT < 23 || !Settings.canDrawOverlays(c)) return;
        JSONObject m = current(c);
        if (!m.has("text")) return;
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        hideOverlay(c);
        TextView v = new TextView(c);
        v.setText("💌  " + senderLabel(c, m.optString("from")) + ":  " + m.optString("text"));
        v.setTextSize(22);
        v.setTextColor(0xFFFFFFFF);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = (int) (18 * c.getResources().getDisplayMetrics().density);
        v.setPadding(pad * 2, pad, pad * 2, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE60B1A22);
        bg.setCornerRadius(pad * 1.5f);
        bg.setStroke(pad / 6, 0xFFE8C35A);
        v.setBackground(bg);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = pad * 3;
        try { wm.addView(v, lp); overlay = v; } catch (Exception ignored) { return; }
        new Handler(Looper.getMainLooper()).postDelayed(() -> hideOverlay(c), 90_000);
    }

    static void hideOverlay(Context c) {
        if (overlay == null) return;
        try { ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).removeView(overlay); } catch (Exception ignored) { }
        overlay = null;
    }
}
