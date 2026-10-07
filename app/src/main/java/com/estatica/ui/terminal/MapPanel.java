package com.estatica.ui.terminal;

import android.content.res.Resources;
import android.view.View;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;
import com.estatica.databinding.PanelMapBinding;

/** The map panel: tap a "?" sector to pick who scouts it, tap a place to see what it is. */
final class MapPanel {

    interface Host {
        void closeMap();

        /** Pick who goes to scout this sector. */
        void pickScout(SessionSnapshot.SectorView sector);
    }

    private final PanelMapBinding b;
    private final Host host;
    private SessionSnapshot snapshot;
    private String selectedSector;

    MapPanel(PanelMapBinding binding, Host host) {
        this.b = binding;
        this.host = host;
        b.mapClose.setOnClickListener(v -> host.closeMap());
        b.map.setListener(new MapView.Listener() {
            @Override
            public void onSector(SessionSnapshot.SectorView sector) {
                select(sector);
            }

            @Override
            public void onPlace(SessionSnapshot.PlaceView place) {
                selectedSector = null;
                Resources res = b.getRoot().getResources();
                b.mapInfo.setText(res.getString(place.reachable ? R.string.map_place : R.string.map_place_far,
                        place.name, place.danger));
                b.mapAction.setVisibility(View.GONE);
                redraw();
            }
        });
    }

    boolean isShown() {
        return b.getRoot().getVisibility() == View.VISIBLE;
    }

    void show() {
        b.getRoot().setVisibility(View.VISIBLE);
        selectedSector = null;
        b.mapInfo.setText(R.string.map_hint);
        b.mapAction.setVisibility(View.GONE);
        redraw();
    }

    void hide() {
        b.getRoot().setVisibility(View.GONE);
    }

    void render(SessionSnapshot snapshot) {
        this.snapshot = snapshot;
        if (!isShown()) {
            return;
        }
        if (selectedSector != null) {
            for (SessionSnapshot.SectorView s : snapshot.map.sectors) {
                if (s.id.equals(selectedSector)) {
                    select(s); // its state may have changed (someone left to scout it)
                    return;
                }
            }
        }
        redraw();
    }

    private void select(SessionSnapshot.SectorView sector) {
        selectedSector = sector.id;
        Resources res = b.getRoot().getResources();
        String state;
        boolean canScout = false;
        if (sector.beingScouted) {
            state = res.getString(R.string.map_sector_scouting, sector.label);
        } else if (sector.scouted) {
            state = res.getString(R.string.map_sector_scouted, sector.label);
        } else if (!sector.reachable) {
            state = res.getString(R.string.map_sector_far, sector.label);
        } else {
            state = res.getString(sector.heard ? R.string.map_sector_heard : R.string.map_sector_unknown, sector.label);
            canScout = true;
        }
        b.mapInfo.setText(state);
        b.mapAction.setVisibility(canScout ? View.VISIBLE : View.GONE);
        b.mapAction.setOnClickListener(canScout ? v -> host.pickScout(sector) : null);
        redraw();
    }

    private void redraw() {
        if (snapshot != null) {
            b.map.setMap(snapshot.map, selectedSector);
        }
    }
}
