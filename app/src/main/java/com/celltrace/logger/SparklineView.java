package com.celltrace.logger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** RSRP 实时趋势曲线（滚动窗口） */
public class SparklineView extends View {

    private static final int CAP = 150;

    private final float[] values = new float[CAP];
    private int count;
    private int head;

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path area = new Path();

    public SparklineView(Context c) {
        this(c, null);
    }

    public SparklineView(Context c, AttributeSet a) {
        super(c, a);
        grid.setColor(0xFF1E2A36);
        grid.setStrokeWidth(dp(1));
        line.setColor(0xFF22D3EE);
        line.setStrokeWidth(dp(2));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        dot.setColor(0xFF22D3EE);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void push(float v) {
        if (Float.isNaN(v) || v == CellRecord.INVALID) return;
        values[head] = v;
        head = (head + 1) % CAP;
        if (count < CAP) count++;
        invalidate();
    }

    public float last() {
        if (count == 0) return Float.NaN;
        int idx = (head - 1 + CAP) % CAP;
        return values[idx];
    }

    public float min() {
        float m = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) m = Math.min(m, values[i]);
        return count == 0 ? Float.NaN : m;
    }

    public float max() {
        float m = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) m = Math.max(m, values[i]);
        return count == 0 ? Float.NaN : m;
    }

    public void clearAll() {
        count = 0;
        head = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // 网格
        for (int i = 1; i < 4; i++) {
            float y = h * i / 4f;
            canvas.drawLine(0, y, w, y, grid);
        }

        if (count < 2) return;

        float lo = min();
        float hi = max();
        float span = Math.max(12f, hi - lo);
        float top = hi + span * 0.15f;
        float bottom = top - span * 1.3f;

        path.reset();
        area.reset();
        for (int i = 0; i < count; i++) {
            int idx = (head - count + i + CAP * 2) % CAP;
            float v = Math.min(top, Math.max(bottom, values[idx]));
            float x = w * i / (float) (CAP - 1);
            float y = h - (v - bottom) / (top - bottom) * h;
            if (i == 0) {
                path.moveTo(x, y);
                area.moveTo(x, h);
                area.lineTo(x, y);
            } else {
                path.lineTo(x, y);
                area.lineTo(x, y);
            }
        }
        area.lineTo(w * (count - 1) / (float) (CAP - 1), h);
        area.close();

        fill.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x5522D3EE, 0x0022D3EE}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(area, fill);
        canvas.drawPath(path, line);

        float lx = w * (count - 1) / (float) (CAP - 1);
        float lv = Math.min(top, Math.max(bottom, last()));
        float ly = h - (lv - bottom) / (top - bottom) * h;
        canvas.drawCircle(lx, ly, dp(3.5f), dot);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int h = (int) dp(86);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthSpec), resolveSize(h, heightSpec));
    }
}
