package com.mytv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Little equalizer bars: they dance while the recitation plays and settle flat when it pauses. */
public class EqView extends View {
    private static final int BARS = 16;
    private final float[] level = new float[BARS];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean active;

    public EqView(Context c, int color) {
        super(c);
        paint.setColor(color);
    }

    public void setActive(boolean on) {
        if (on != active) { active = on; postInvalidateOnAnimation(); }
    }

    @Override
    protected void onDraw(Canvas c) {
        double t = System.currentTimeMillis() / 1000.0;
        float w = getWidth(), h = getHeight();
        float gap = w / BARS * 0.35f, bw = w / BARS - gap;
        boolean moving = false;
        for (int i = 0; i < BARS; i++) {
            float target = active
                    ? (float) (0.2 + 0.8 * Math.abs(Math.sin(t * (2.1 + (i % 5) * 0.53) + i * 1.7))
                        * (0.55 + 0.45 * Math.sin(t * 0.9 + i * 0.4)))
                    : 0.08f;
            level[i] += (target - level[i]) * (target > level[i] ? 0.45f : 0.18f);
            if (Math.abs(target - level[i]) > 0.01f) moving = true;
            float bh = Math.max(h * 0.08f, level[i] * h);
            float x = i * (bw + gap);
            r.set(x, (h - bh) / 2, x + bw, (h + bh) / 2);
            c.drawRoundRect(r, bw / 2, bw / 2, paint);
        }
        if (active || moving) postInvalidateOnAnimation();
    }
}
