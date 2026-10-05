package com.estatica.ui.terminal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Static CRT scanlines: a 1×N tiled bitmap drawn with a shader. No animation; it only draws
 * when the system invalidates it.
 */
public final class ScanlineOverlayView extends View {

    private static final int LINE_ALPHA_COLOR = 0x2E000000;

    private final Paint paint = new Paint();

    public ScanlineOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        int period = Math.max(3, Math.round(3 * getResources().getDisplayMetrics().density));
        Bitmap tile = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888);
        int thickness = Math.max(1, period / 3);
        for (int y = 0; y < thickness; y++) {
            tile.setPixel(0, y, LINE_ALPHA_COLOR);
        }
        paint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPaint(paint);
    }
}
