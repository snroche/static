package com.estatica.ui.scouts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.estatica.R;

/**
 * A full-width bar: a thin frame, filled to a fraction, optionally cut into segments (bond
 * levels). Static: redrawn only when the value changes.
 */
public final class MeterView extends View {

    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    private final Paint gap = new Paint();
    private final float dp;
    private float fraction;
    private int segments = 1;

    public MeterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(1f * dp);
        frame.setColor(ContextCompat.getColor(context, R.color.amber_dim));
        fill.setColor(ContextCompat.getColor(context, R.color.amber));
        gap.setColor(ContextCompat.getColor(context, R.color.terminal_bg));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** @param fraction 0–1 filled; @param segments 1 for a plain bar */
    public void set(float fraction, int segments, int colorRes) {
        float f = Math.max(0f, Math.min(1f, fraction));
        int color = ContextCompat.getColor(getContext(), colorRes);
        if (f == this.fraction && segments == this.segments && color == fill.getColor()) {
            return;
        }
        this.fraction = f;
        this.segments = Math.max(1, segments);
        fill.setColor(color);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.round(12 * dp));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float inset = 2 * dp;
        float w = getWidth();
        float h = getHeight();
        canvas.drawRect(0.5f * dp, 0.5f * dp, w - 0.5f * dp, h - 0.5f * dp, frame);
        canvas.drawRect(inset, inset, inset + (w - 2 * inset) * fraction, h - inset, fill);
        // Notches between levels.
        for (int i = 1; i < segments; i++) {
            float x = w * i / segments;
            canvas.drawRect(x - 1.5f * dp, 0, x + 1.5f * dp, h, gap);
        }
    }
}
