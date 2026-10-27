package com.estatica.ui.terminal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;

/**
 * The region around the bunker, drawn like a radar plot: the bunker in the middle, one ring per
 * band, split into sectors. Unscouted sectors show "?"; places found show as an icon over their
 * name, in the middle of their sector's cell (spread out when a cell holds more than one).
 * Ring names sit on the ring borders. Names are drawn on a patch of background so lines never
 * run through them. Static: laid out when the map or the size changes, never animated.
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
    /** Longest label on the map; longer names are cut with a period ("Hospital" → "Hospi."). */
    private static final int MAX_LABEL = 7;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knockout = new Paint();
    private final int amber;
    private final int bright;
    private final int dim;
    private final int hint;
    private final float dp;
    private final String relayMark;
    private final Rect bounds = new Rect();

    private SessionSnapshot.MapView map;
    private String selectedSector;
    private Listener listener;

    /** Where each place is drawn, worked out in layoutPlaces() and used for taps too. */
    private static final class Spot {
        final SessionSnapshot.PlaceView place;
        final float x;
        final float y;
        /** The name under (or over) the icon, or null when there's no room for it. */
        String label;
        RectF labelBox;

        Spot(SessionSnapshot.PlaceView place, float x, float y) {
            this.place = place;
            this.x = x;
            this.y = y;
        }
    }

    private final List<Spot> spots = new ArrayList<>();

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
        knockout.setStyle(Paint.Style.FILL);
        knockout.setColor(ContextCompat.getColor(context, R.color.terminal_bg));
        relayMark = context.getString(R.string.ic_relay);
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    private float iconSize() {
        return sp(15);
    }

    private float labelSize() {
        return sp(10);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    static String abbreviate(String name) {
        if (name == null || name.length() <= MAX_LABEL) {
            return name;
        }
        return name.substring(0, MAX_LABEL - 1).trim() + ".";
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        layoutPlaces();
    }

    void setMap(SessionSnapshot.MapView map, String selectedSector) {
        this.map = map;
        this.selectedSector = selectedSector;
        layoutPlaces();
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

    /** The sector cell a place belongs to (its turn sits inside its sector's wedge). */
    private static int directionOf(SessionSnapshot.PlaceView p) {
        int count = directionsOn(p.ring);
        return (int) Math.round(p.turn * count) % count;
    }

    /**
     * Each place in the middle of its cell: alone, right on the center; several, spread across
     * the cell's width. The icon and its name form one block centered on that point. A name
     * that would land on another goes above the icon instead, or is left out (tap shows it).
     */
    private void layoutPlaces() {
        spots.clear();
        if (map == null || getWidth() == 0) {
            return;
        }
        Map<String, List<SessionSnapshot.PlaceView>> cells = new LinkedHashMap<>();
        for (SessionSnapshot.PlaceView p : map.places) {
            cells.computeIfAbsent(p.ring + ":" + directionOf(p), k -> new ArrayList<>()).add(p);
        }
        float icon = iconSize();
        float gap = 2 * dp;
        text.setTextSize(labelSize());
        Paint.FontMetrics fm = text.getFontMetrics();
        float h = fm.descent - fm.ascent;
        List<RectF> taken = new ArrayList<>();
        for (List<SessionSnapshot.PlaceView> cell : cells.values()) {
            cell.sort(Comparator.comparing(p -> p.zoneId));
            int n = cell.size();
            int ring = cell.get(0).ring;
            int count = directionsOn(ring);
            double center = (double) directionOf(cell.get(0)) / count;
            float radius = ringStart(ring) + ringWidth() / 2;
            for (int i = 0; i < n; i++) {
                // Spread over the middle 70% of the wedge.
                double turn = center + (n == 1 ? 0 : (i - (n - 1) / 2.0) * 0.7 / n / count);
                float cx = x(radius, turn);
                float cy = y(radius, turn);
                // The block (icon over name) is centered on the cell point.
                Spot spot = new Spot(cell.get(i), cx, cy - (gap + h) / 2);
                spots.add(spot);
                taken.add(new RectF(spot.x - icon / 2, spot.y - icon / 2, spot.x + icon / 2, spot.y + icon / 2));
            }
        }
        for (Spot spot : spots) {
            String name = abbreviate(spot.place.shortName);
            if (name == null || name.isEmpty()) {
                continue;
            }
            float w = text.measureText(name) + 4 * dp;
            float below = spot.y + icon / 2 + gap + h / 2;
            float above = spot.y - icon / 2 - gap - h / 2;
            for (float ly : new float[]{below, above}) {
                RectF box = new RectF(spot.x - w / 2, ly - h / 2, spot.x + w / 2, ly + h / 2);
                boolean free = box.left >= 0 && box.right <= getWidth() && box.top >= 0 && box.bottom <= getHeight();
                for (RectF other : taken) {
                    free &= !RectF.intersects(box, other);
                }
                if (free) {
                    spot.label = name;
                    spot.labelBox = box;
                    taken.add(box);
                    break;
                }
            }
        }
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
        return Math.min(getWidth(), getHeight()) / 2f - 10 * dp;
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
        // Rings and spokes (sector borders sit between the directions).
        line.setColor(hint);
        for (int r = 0; r <= rings; r++) {
            canvas.drawCircle(cx(), cy(), ringStart(r), line);
        }
        for (int r = 0; r < rings; r++) {
            int count = directionsOn(r);
            for (int d = 0; d < count; d++) {
                double turn = (d + 0.5) / count;
                canvas.drawLine(x(ringStart(r), turn), y(ringStart(r), turn),
                        x(ringStart(r + 1), turn), y(ringStart(r + 1), turn), line);
            }
        }
        // Ring names on each ring's outer border, at the top, on a patch of background.
        text.setTextSize(sp(9));
        text.setColor(dim);
        for (int r = 0; r < rings; r++) {
            label(canvas, map.bands.get(r), cx(), cy() - ringStart(r + 1));
        }
        // Sectors not scouted yet.
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
        // Places: icon (or a dot), a relay mark, and the name.
        float icon = iconSize();
        for (Spot spot : spots) {
            SessionSnapshot.PlaceView p = spot.place;
            // Picked over: dim. Out of reach: dimmer.
            int color = !p.reachable ? hint : p.depleted >= 50 ? dim : bright;
            if (p.icon != null) {
                text.setTextSize(icon);
                text.setColor(color);
                drawCentered(canvas, p.icon, spot.x, spot.y);
            } else {
                dot.setColor(color);
                canvas.drawCircle(spot.x, spot.y, 3.5f * dp, dot);
            }
            if (map.relays.containsKey(p.zoneId)) {
                text.setTextSize(sp(9));
                text.setColor(map.relays.get(p.zoneId) >= 30 ? bright : dim);
                drawCentered(canvas, relayMark, spot.x + icon * 0.65f, spot.y - icon * 0.55f);
            }
        }
        text.setTextSize(labelSize());
        for (Spot spot : spots) {
            if (spot.label != null) {
                text.setColor(spot.place.reachable ? amber : dim);
                label(canvas, spot.label, spot.labelBox.centerX(), spot.labelBox.centerY());
            }
        }
        // The bunker.
        dot.setColor(bright);
        canvas.drawCircle(cx(), cy(), 5 * dp, dot);
        line.setColor(bright);
        canvas.drawCircle(cx(), cy(), 9 * dp, line);
    }

    /** Text centered on (x, y), on a patch of background so lines behind it don't show through. */
    private void label(Canvas canvas, String s, float x, float y) {
        Paint.FontMetrics fm = text.getFontMetrics();
        float w = text.measureText(s) / 2 + 2 * dp;
        float h = (fm.descent - fm.ascent) / 2;
        canvas.drawRect(x - w, y - h, x + w, y + h, knockout);
        canvas.drawText(s, x, y - (fm.ascent + fm.descent) / 2, text);
    }

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
        // A place under the finger wins: its icon or its name.
        Spot nearest = null;
        double best = 24 * dp;
        for (Spot spot : spots) {
            double d = Math.hypot(tx - spot.x, ty - spot.y);
            if (spot.labelBox != null && spot.labelBox.contains(tx, ty)) {
                d = 0;
            }
            if (d < best) {
                best = d;
                nearest = spot;
            }
        }
        if (nearest != null) {
            listener.onPlace(nearest.place);
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
