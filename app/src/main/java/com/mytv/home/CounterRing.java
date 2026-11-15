package com.mytv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Round counter: a ring that fills as the zikr is repeated. */
public class CounterRing extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float shown, target;

    public CounterRing(Context c, int trackColor, int arcColor) {
        super(c);
        for (Paint p : new Paint[]{track, arc}) { p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); }
        track.setColor(trackColor);
        arc.setColor(arcColor);
    }

    public void setProgress(float f) { target = Math.max(0, Math.min(1, f)); invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        float w = Math.min(getWidth(), getHeight()), sw = w * 0.07f;
        track.setStrokeWidth(sw);
        arc.setStrokeWidth(sw);
        r.set(sw, sw, w - sw, w - sw);
        r.offset((getWidth() - w) / 2, (getHeight() - w) / 2);
        c.drawArc(r, 0, 360, false, track);
        shown += (target - shown) * 0.2f;
        c.drawArc(r, -90, 360 * shown, false, arc);
        if (Math.abs(target - shown) > 0.002f) postInvalidateOnAnimation();
    }
}
