package com.estatica.ui.terminal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * The bunker's floor plan: each room a box on a small grid. Open rooms in amber with who's
 * inside, sealed ones hatched and dim, the room being restored with a progress bar. Static:
 * redrawn only when the rooms change or one is picked.
 */
public final class BunkerView extends View {

    public interface Listener {
        void onRoom(SessionSnapshot.RoomView room);
    }

    private static final int COLUMNS = 10;

    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hatch = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DashPathEffect dashes;
    private final int amber;
    private final int bright;
    private final int dim;
    private final int hint;
    private final int signal;
    private List<SessionSnapshot.RoomView> rooms = new ArrayList<>();
    private String selected;
    private Listener listener;
    private int rows = 10;

    public BunkerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        amber = ContextCompat.getColor(context, R.color.amber);
        bright = ContextCompat.getColor(context, R.color.amber_bright);
        dim = ContextCompat.getColor(context, R.color.amber_dim);
        hint = ContextCompat.getColor(context, R.color.amber_hint);
        signal = ContextCompat.getColor(context, R.color.signal);
        float dp = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        Typeface mono = ResourcesCompat.getFont(context, R.font.terminal);
        dashes = new DashPathEffect(new float[]{6 * dp, 4 * dp}, 0);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(1.5f * dp);
        hatch.setStrokeWidth(1f * dp);
        hatch.setColor(hint);
        label.setTypeface(mono);
        label.setTextSize(12 * sp);
        small.setTypeface(mono);
        small.setTextSize(10 * sp);
        setContentDescription(context.getString(R.string.rooms_description));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setRooms(List<SessionSnapshot.RoomView> rooms, String selected) {
        this.rooms = rooms;
        this.selected = selected;
        int maxRow = 0;
        for (SessionSnapshot.RoomView r : rooms) {
            maxRow = Math.max(maxRow, r.y + r.h);
        }
        if (maxRow != rows) {
            rows = Math.max(1, maxRow);
            requestLayout();
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        // Cells are a bit wider than tall: rooms read like a blueprint, not a chessboard.
        int height = Math.round(width / (float) COLUMNS * 0.8f * rows);
        setMeasuredDimension(width, height);
    }

    private RectF box(SessionSnapshot.RoomView r) {
        float cw = getWidth() / (float) COLUMNS;
        float ch = getHeight() / (float) rows;
        float pad = 2 * getResources().getDisplayMetrics().density;
        return new RectF(r.x * cw + pad, r.y * ch + pad, (r.x + r.w) * cw - pad, (r.y + r.h) * ch - pad);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float dp = getResources().getDisplayMetrics().density;
        for (SessionSnapshot.RoomView r : rooms) {
            RectF b = box(r);
            boolean picked = r.id.equals(selected);
            if (r.open) {
                frame.setPathEffect(null);
                frame.setColor(picked ? bright : amber);
                frame.setStrokeWidth((picked ? 2.5f : 1.5f) * dp);
                canvas.drawRect(b, frame);
            } else {
                // Rubble: diagonal hatching inside a dashed frame.
                canvas.save();
                canvas.clipRect(b);
                for (float x = b.left - b.height(); x < b.right; x += 10 * dp) {
                    canvas.drawLine(x, b.bottom, x + b.height(), b.top, hatch);
                }
                canvas.restore();
                frame.setPathEffect(dashes);
                frame.setColor(picked ? bright : r.restoring ? signal : dim);
                frame.setStrokeWidth((picked ? 2.5f : 1.5f) * dp);
                canvas.drawRect(b, frame);
            }
            if (r.restoring) {
                fill.setColor(signal);
                canvas.drawRect(b.left, b.bottom - 4 * dp, b.left + (float) (b.width() * r.progress), b.bottom, fill);
            }
            label.setColor(r.open ? (picked ? bright : amber) : dim);
            float x = b.left + 4 * dp;
            float y = b.top + label.getTextSize() + 2 * dp;
            canvas.drawText(fit(r.name, b.width() - 8 * dp, label), x, y, label);
            String sub = r.open ? (r.working.isEmpty() ? "" : String.join(", ", r.working))
                    : r.restoring ? Math.round(r.progress * 100) + "%" : "▒";
            if (!sub.isEmpty() && b.height() > label.getTextSize() * 2.2f) {
                small.setColor(r.open ? dim : r.restoring ? signal : hint);
                canvas.drawText(fit(sub, b.width() - 8 * dp, small), x, y + small.getTextSize() + 4 * dp, small);
            }
        }
    }

    /** Cuts text to the width with an ellipsis. */
    private static String fit(String text, float width, Paint paint) {
        if (paint.measureText(text) <= width) {
            return text;
        }
        int n = paint.breakText(text, true, width - paint.measureText("…"), null);
        return n <= 0 ? "" : text.substring(0, n) + "…";
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            for (SessionSnapshot.RoomView r : rooms) {
                if (box(r).contains(event.getX(), event.getY())) {
                    if (listener != null) {
                        listener.onRoom(r);
                    }
                    performClick();
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
