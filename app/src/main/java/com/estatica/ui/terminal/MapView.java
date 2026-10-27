package com.estatica.ui.terminal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;

/**
 * The region around the bunker, drawn like a radar plot: the bunker in the middle, one ring per
 * band, eight directions. Unscouted sectors show "?", places found show as dots with names.
 * Static: redrawn only when the snapshot changes, never animated.
 */
public final class MapView extends View {

    public interface Listener {
        void onSector(SessionSnapshot.SectorView sector);

        void onPlace(SessionSnapshot.PlaceView place);
    }

    /** Same rule as the region map: 8 on the inner ring, 4 more per ring outward. */
    private static int directionsOn(int ring) {
        return com.estatica.domain.map.RegionMap.directionsOn(ring);
    }
    private static final float BUNKER_FRACTION = 0.12f;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int amber;
    private final int bright;
    private final int dim;
    private final int hint;
    private final float dp;

    private SessionSnapshot.MapView map;
    private String selectedSector;
    private Listener listener;

    public MapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        amber = ContextCompat.getColor(context, R.color.amber);
        bright = ContextCompat.getColor(context, R.color.amber_bright);
        dim = ContextCompat.getColor(context, R.color.amber_dim);
        hint = ContextCompat.getColor(context, R.color.amber_hint);
        dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp);
        Typeface terminal = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.terminal);
        text.setTypeface(terminal != null ? terminal : Typeface.MONOSPACE);
        text.setTextAlign(Paint.Align.CENTER);
        dot.setStyle(Paint.Style.FILL);
        relayMark = context.getString(R.string.ic_relay);
    }

    private final String relayMark;

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** A place label where it fits, computed when the map or the size changes (not per frame). */
    private static final class Label {
        final String text;
        final float x;
        final float y;
        final boolean reachable;

        Label(String text, float x, float y, boolean reachable) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.reachable = reachable;
        }
    }

    private final List<Label> labels = new ArrayList<>();

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        layoutLabels();
    }

    /** Longest label on the map; longer names are cut with a period ("Hospital" → "Hospit."). */
    private static final int MAX_LABEL = 7;

    static String abbreviate(String name) {
        if (name == null || name.length() <= MAX_LABEL) {
            return name;
        }
        return name.substring(0, MAX_LABEL - 1).trim() + ".";
    }

    /** Under each icon if there's room; else right, left or above; else none. */
    private void layoutLabels() {
        labels.clear();
        if (map == null || getWidth() == 0) {
            return;
        }
        float iconSize = sp(15);
        float h = sp(10);
        text.setTextSize(h);
        List<android.graphics.RectF> taken = new ArrayList<>();
        for (SessionSnapshot.PlaceView p : map.places) {
            float radius = ringStart(p.ring) + ringWidth() * (float) p.ringPosition;
            float px = x(radius, p.turn);
            float py = y(radius, p.turn);
            taken.add(new android.graphics.RectF(px - iconSize / 2, py - iconSize / 2, px + iconSize / 2, py + iconSize / 2));
        }
        for (SessionSnapshot.PlaceView p : map.places) {
            float radius = ringStart(p.ring) + ringWidth() * (float) p.ringPosition;
            float px = x(radius, p.turn);
            float py = y(radius, p.turn);
            String name = abbreviate(p.shortName);
            float w = text.measureText(name);
            float gap = iconSize / 2 + 2 * dp;
            // Label centers: below, right, left, above.
            float[][] spots = {
                    {px, py + gap + h / 2},
                    {px + gap + w / 2, py},
                    {px - gap - w / 2, py},
                    {px, py - gap - h / 2},
            };
            for (float[] spot : spots) {
                android.graphics.RectF box = new android.graphics.RectF(spot[0] - w / 2, spot[1] - h / 2,
                        spot[0] + w / 2, spot[1] + h / 2);
                boolean free = box.left >= 0 && box.right <= getWidth() && box.top >= 0 && box.bottom <= getHeight();
                for (android.graphics.RectF other : taken) {
                    free &= !android.graphics.RectF.intersects(box, other);
                }
                if (free) {
                    labels.add(new Label(name, spot[0], spot[1], p.reachable));
                    taken.add(box);
                    break;
                }
            }
        }
    }

    void setMap(SessionSnapshot.MapView map, String selectedSector) {
        this.map = map;
        layoutLabels();
        this.selectedSector = selectedSector;
        setContentDescription(describe(map));
        invalidate();
    }

    private String describe(SessionSnapshot.MapView map) {
        int unexplored = 0;
        for (SessionSnapshot.SectorView s : map.sectors) {
            if (!s.scouted && s.reachable) {
                unexplored++;
            }
        }
        return getResources().getString(R.string.map_description, map.places.size(), unexplored);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                ? width : MeasureSpec.getSize(heightSpec);
        int side = Math.min(width, height);
        setMeasuredDimension(width, side);
    }

    private float cx() {
        return getWidth() / 2f;
    }

    private float cy() {
        return getHeight() / 2f;
    }

    private float outer() {
        return Math.min(getWidth(), getHeight()) / 2f - 8 * dp;
    }

    private float ringStart(int ring) {
        int rings = Math.max(1, map.bands.size());
        float inner = outer() * BUNKER_FRACTION;
        return inner + (outer() - inner) * ring / rings;
    }

    private float ringWidth() {
        int rings = Math.max(1, map.bands.size());
        return (outer() - outer() * BUNKER_FRACTION) / rings;
    }

    /** Clockwise from north, in radians, for a fraction of a turn. */
    private static double angle(double turn) {
        return turn * 2 * Math.PI;
    }

    private float x(double radius, double turn) {
        return (float) (cx() + radius * Math.sin(angle(turn)));
    }

    private float y(double radius, double turn) {
        return (float) (cy() - radius * Math.cos(angle(turn)));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (map == null) {
            return;
        }
        int rings = map.bands.size();
        // Rings and spokes (sector borders sit between the eight directions).
        line.setColor(hint);
        for (int r = 0; r <= rings; r++) {
            canvas.drawCircle(cx(), cy(), ringStart(r), line);
        }
        // Each ring has its own spokes: farther rings are split finer.
        for (int r = 0; r < rings; r++) {
            int count = directionsOn(r);
            for (int d = 0; d < count; d++) {
                double turn = (d + 0.5) / count;
                canvas.drawLine(x(ringStart(r), turn), y(ringStart(r), turn),
                        x(ringStart(r + 1), turn), y(ringStart(r + 1), turn), line);
            }
        }
        // Ring names, along the north spoke.
        text.setTextSize(sp(9));
        text.setColor(dim);
        for (int r = 0; r < rings; r++) {
            canvas.drawText(map.bands.get(r), cx(), cy() - ringStart(r + 1) + text.getTextSize() + 2 * dp, text);
        }
        // Sectors.
        text.setTextSize(sp(16));
        for (SessionSnapshot.SectorView s : map.sectors) {
            double turn = (double) s.direction / s.count;
            float radius = ringStart(s.ring) + ringWidth() / 2;
            float px = x(radius, turn);
            float py = y(radius, turn);
            String mark;
            int color;
            if (s.beingScouted) {
                mark = "…";
                color = bright;
            } else if (s.scouted) {
                continue;
            } else if (!s.reachable) {
                mark = "·";
                color = hint;
            } else {
                mark = s.heard ? "?!" : "?";
                color = s.heard ? bright : amber;
            }
            text.setColor(s.id.equals(selectedSector) ? bright : color);
            drawCentered(canvas, mark, px, py);
            if (s.id.equals(selectedSector)) {
                line.setColor(bright);
                canvas.drawCircle(px, py, text.getTextSize() * 0.9f, line);
                line.setColor(hint);
            }
        }
        // Places found: their icon, and the labels laid out in layoutLabels().
        float iconSize = sp(15);
        for (SessionSnapshot.PlaceView p : map.places) {
            float radius = ringStart(p.ring) + ringWidth() * (float) p.ringPosition;
            float px = x(radius, p.turn);
            float py = y(radius, p.turn);
            // Picked over: dim. Out of reach: dimmer.
            int color = !p.reachable ? hint : p.depleted >= 50 ? dim : bright;
            if (p.icon != null) {
                text.setTextSize(iconSize);
                text.setColor(color);
                drawCentered(canvas, p.icon, px, py);
            } else {
                dot.setColor(color);
                canvas.drawCircle(px, py, 3.5f * dp, dot);
            }
            // A relay there: a small mark at the icon's top right.
            if (map.relays.containsKey(p.zoneId)) {
                text.setTextSize(sp(9));
                text.setColor(map.relays.get(p.zoneId) >= 30 ? bright : dim);
                drawCentered(canvas, relayMark, px + iconSize * 0.6f, py - iconSize * 0.6f);
            }
        }
        text.setTextSize(sp(10));
        // Labels center on their spot by the font's line metrics (not each word's ink, so a row
        // of names sits on one line).
        Paint.FontMetrics fm = text.getFontMetrics();
        float middle = -(fm.ascent + fm.descent) / 2;
        for (Label label : labels) {
            text.setColor(label.reachable ? amber : dim);
            canvas.drawText(label.text, label.x, label.y + middle, text);
        }
        // The bunker.
        dot.setColor(bright);
        canvas.drawCircle(cx(), cy(), 5 * dp, dot);
        line.setColor(bright);
        canvas.drawCircle(cx(), cy(), 9 * dp, line);
    }

    private final android.graphics.Rect bounds = new android.graphics.Rect();

    /**
     * Draws a glyph with its ink centered on (x, y). Icon glyphs don't sit where their advance
     * and baseline say (Nerd Font icons overhang the cell), so align by the measured bounds.
     */
    private void drawCentered(Canvas canvas, String glyph, float x, float y) {
        text.getTextBounds(glyph, 0, glyph.length(), bounds);
        Paint.Align was = text.getTextAlign();
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(glyph, x - bounds.left - bounds.width() / 2f, y - bounds.top - bounds.height() / 2f, text);
        text.setTextAlign(was);
    }

    @SuppressLint("ClickableViewAccessibility") // Taps are mirrored by the panel's buttons.
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (map == null || listener == null) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return false;
        }
        float tx = event.getX();
        float ty = event.getY();
        // A place under the finger wins.
        SessionSnapshot.PlaceView nearest = null;
        double best = 24 * dp;
        for (SessionSnapshot.PlaceView p : map.places) {
            float radius = ringStart(p.ring) + ringWidth() * (float) p.ringPosition;
            double d = Math.hypot(tx - x(radius, p.turn), ty - y(radius, p.turn));
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest != null) {
            listener.onPlace(nearest);
            performClick();
            return true;
        }
        double dx = tx - cx();
        double dy = cy() - ty;
        double radius = Math.hypot(dx, dy);
        if (radius < ringStart(0) || radius > outer()) {
            return false;
        }
        int ring = (int) ((radius - ringStart(0)) / ringWidth());
        double turn = Math.atan2(dx, dy) / (2 * Math.PI);
        int count = directionsOn(ring);
        int direction = (int) Math.round((turn < 0 ? turn + 1 : turn) * count) % count;
        for (SessionSnapshot.SectorView s : map.sectors) {
            if (s.ring == ring && s.direction == direction) {
                listener.onSector(s);
                performClick();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
