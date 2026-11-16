package com.mytv.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** "ما شاء الله يا شادن!" with falling confetti: a full day of prayers, or the weekly crown. */
public class CelebrateActivity extends Activity {
    public static void show(Context c, String girl, boolean crown) {
        try {
            c.startActivity(new Intent(c, CelebrateActivity.class).putExtra("girl", girl).putExtra("crown", crown)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String girl = getIntent().getStringExtra("girl");
        boolean crown = getIntent().getBooleanExtra("crown", false);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                crown ? new int[]{0xFF2A1E5C, 0xFF6B3FA0, 0xFFE8A93C} : new int[]{0xFFAEE2FF, 0xFFFFD6EA, 0xFFFFF3C4}));
        root.addView(new Confetti(this), new FrameLayout.LayoutParams(-1, -1));
        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0, 1.3f);
        t.setTextColor(crown ? 0xFFFFFFFF : 0xFF22314D);
        t.setShadowLayer(10, 0, 2, crown ? 0x88000000 : 0x33000000);
        t.setTextSize(44);
        t.setText(crown
                ? "👑\nتاج الأسبوع لـ" + girl + "!\n" + Stars.WEEK_GOAL + " نجمة… ربنا يبارك فيكِ 🤍"
                : "🌟 ما شاء الله يا " + girl + "! 🌟\nصلّيتِ الخمس صلوات النهارده\nربنا يتقبّل منكِ 🤍");
        root.addView(t, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        t.setScaleX(0.5f); t.setScaleY(0.5f); t.setAlpha(0f);
        t.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(700).start();
        Chime.play();
        handler.postDelayed(Chime::play, 700);
        handler.postDelayed(this::finish, 12_000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) { finish(); return true; }

    @Override
    protected void onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null); }

    static class Confetti extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[][] bits = new float[90][5];
        private final int[] colors = {0xFFFF8FB1, 0xFF7FC8F8, 0xFFFFC857, 0xFF8BD3A6, 0xFFB79CED, 0xFFFF9E6B, 0xFFFFFFFF};
        private final long t0 = System.currentTimeMillis();
        Confetti(Context c) {
            super(c);
            java.util.Random r = new java.util.Random();
            for (float[] b : bits) { b[0] = r.nextFloat(); b[1] = -r.nextFloat(); b[2] = 0.08f + r.nextFloat() * 0.12f; b[3] = r.nextFloat() * 360; b[4] = r.nextInt(colors.length); }
        }
        @Override protected void onDraw(Canvas c) {
            float s = (System.currentTimeMillis() - t0) / 1000f, w = getWidth(), h = getHeight();
            for (float[] b : bits) {
                float y = ((b[1] + s * b[2]) % 1.2f) * h, x = (b[0] + 0.03f * (float) Math.sin(s * 2 + b[3])) * w;
                p.setColor(colors[(int) b[4]]);
                c.save();
                c.rotate(b[3] + s * 120, x, y);
                c.drawRect(x - 7, y - 4, x + 7, y + 4, p);
                c.restore();
            }
            postInvalidateOnAnimation();
        }
    }
}
