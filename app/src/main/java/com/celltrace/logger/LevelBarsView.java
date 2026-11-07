package com.celltrace.logger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** 5 格信号强度条 */
public class LevelBarsView extends View {

    private static final int BARS = 5;

    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int level;

    public LevelBarsView(Context c) {
        this(c, null);
    }

    public LevelBarsView(Context c, AttributeSet a) {
        super(c, a);
        on.setColor(0xFF34D399);
        off.setColor(0xFF1E2A36);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setLevel(int lv) {
        if (lv != level) {
            level = lv;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float gap = dp(3);
        float bw = (w - gap * (BARS - 1)) / BARS;
        float radius = dp(2);
        int filled = Math.max(0, Math.min(BARS, level));
        for (int i = 0; i < BARS; i++) {
            float left = i * (bw + gap);
            float barH = h * (0.42f + 0.145f * i);
            rect.set(left, h - barH, left + bw, h);
            canvas.drawRoundRect(rect, radius, radius, i < filled ? on : off);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthSpec),
                resolveSize((int) dp(24), heightSpec));
    }
}
