package com.estatica.ui.terminal;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.estatica.R;

/**
 * A pair of tall brackets around the whole button, [ like this ], spanning every line of its
 * label. Marks the selected tab without adding characters to the text.
 */
final class BracketDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float inset;
    private final float tick;

    BracketDrawable(int color, float stroke, float inset, float tick) {
        paint.setColor(color);
        paint.setStrokeWidth(stroke);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        this.inset = inset;
        this.tick = tick;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        float top = b.top + inset;
        float bottom = b.bottom - inset;
        float left = b.left + inset;
        float right = b.right - inset;
        // [
        canvas.drawLine(left, top, left, bottom, paint);
        canvas.drawLine(left, top, left + tick, top, paint);
        canvas.drawLine(left, bottom, left + tick, bottom, paint);
        // ]
        canvas.drawLine(right, top, right, bottom, paint);
        canvas.drawLine(right, top, right - tick, top, paint);
        canvas.drawLine(right, bottom, right - tick, bottom, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filter) {
        paint.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /**
     * Shows or hides the brackets on a view, keeping its own background (ripple) underneath.
     * The original background is kept in the view's tag the first time.
     */
    static void mark(View view, boolean on) {
        Object tag = view.getTag(R.id.bracket_original);
        if (tag == null) {
            Drawable original = view.getBackground();
            tag = original != null ? original : new Object();
            view.setTag(R.id.bracket_original, tag);
        }
        Drawable original = tag instanceof Drawable ? (Drawable) tag : null;
        if (!on) {
            view.setBackground(original);
            return;
        }
        float dp = view.getResources().getDisplayMetrics().density;
        Drawable brackets = new BracketDrawable(ContextCompat.getColor(view.getContext(), R.color.amber_bright),
                1.5f * dp, 3 * dp, 5 * dp);
        view.setBackground(original != null
                ? new android.graphics.drawable.LayerDrawable(new Drawable[]{original, brackets}) : brackets);
    }
}
