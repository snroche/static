package com.estatica.ui.terminal;

import android.content.res.Resources;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;
import com.estatica.databinding.PanelRadioBinding;

/**
 * The radio (GDD §6.1), in three sections: the dial (pick a band, tune, lock on), the
 * transmissions waiting to be decoded, and the relays.
 */
final class RadioPanel {

    private boolean haptics = true;

    void setHaptics(boolean on) {
        haptics = on;
    }

    interface Host {
        void openDecoder(SessionSnapshot.TransmissionView transmission, boolean morseTable);

        void closeRadio();
    }

    private enum Section { DIAL, STATIONS, DECODE, RELAYS }

    /** Strength steps that give a haptic tick while tuning. */
    private static final double[] TICKS = {0.3, 0.6, 0.85};
    private static final int METER_CELLS = 10;

    private final PanelRadioBinding b;
    private final TerminalViewModel viewModel;
    private final Host host;
    private final SoundEngine sound;
    private SessionSnapshot snapshot;
    private boolean wasLockable;
    private String bandId;
    private int lastTick = -1;
    private Section section = Section.DIAL;

    RadioPanel(PanelRadioBinding binding, TerminalViewModel viewModel, Host host, SoundEngine sound) {
        this.sound = sound;
        this.b = binding;
        this.viewModel = viewModel;
        this.host = host;
        b.close.setOnClickListener(v -> host.closeRadio());
        b.dial.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                showFrequency();
                if (bandId != null) {
                    viewModel.tune(bandId, progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        b.fineDown.setOnClickListener(v -> b.dial.setProgress(Math.max(0, b.dial.getProgress() - 1)));
        b.fineUp.setOnClickListener(v -> b.dial.setProgress(Math.min(b.dial.getMax(), b.dial.getProgress() + 1)));
        b.tune.setOnClickListener(v -> {
            if (bandId != null) {
                sound.click();
                viewModel.capture(bandId, b.dial.getProgress());
            }
        });
        b.sectionDial.setOnClickListener(v -> showSection(Section.DIAL));
        b.radioRecharge.setOnClickListener(v -> viewModel.recharge());
        b.sectionDecode.setOnClickListener(v -> showSection(Section.DECODE));
        b.sectionStations.setOnClickListener(v -> showSection(Section.STATIONS));
        b.sectionRelays.setOnClickListener(v -> showSection(Section.RELAYS));
    }

    boolean isShown() {
        return b.getRoot().getVisibility() == View.VISIBLE;
    }

    void show() {
        b.getRoot().setVisibility(View.VISIBLE);
        // Waiting transmissions come first: that's usually why you opened the radio.
        if (snapshot != null && !snapshot.radio.transmissions.isEmpty() && section == Section.RELAYS) {
            section = Section.DECODE;
        }
        if (section == Section.DIAL) {
            sound.startRadio();
        }
        if (snapshot != null) {
            render(snapshot);
        }
        if (bandId != null) {
            viewModel.tune(bandId, b.dial.getProgress());
        }
    }

    void hide() {
        b.getRoot().setVisibility(View.GONE);
        sound.stopRadio();
    }

    private void showSection(Section target) {
        section = target;
        if (target == Section.DIAL) {
            sound.startRadio();
            if (bandId != null) {
                viewModel.tune(bandId, b.dial.getProgress());
            }
        } else {
            sound.stopRadio();
        }
        if (snapshot != null) {
            render(snapshot);
        }
    }

    void render(SessionSnapshot snapshot) {
        this.snapshot = snapshot;
        if (!isShown()) {
            return;
        }
        Resources res = b.getRoot().getResources();
        SessionSnapshot.RadioView radio = snapshot.radio;
        b.dial.setMax(radio.dialMax - 1);

        sectionButton(b.sectionDial, Section.DIAL, res.getString(R.string.radio_section_dial));
        sectionButton(b.sectionDecode, Section.DECODE, radio.transmissions.isEmpty()
                ? res.getString(R.string.radio_section_decode)
                : res.getString(R.string.radio_section_decode_count, radio.transmissions.size()));
        sectionButton(b.sectionRelays, Section.RELAYS, res.getString(R.string.relays_title));
        int onAir = 0;
        for (SessionSnapshot.StationView st : radio.stations) {
            onAir += st.onAir ? 1 : 0;
        }
        sectionButton(b.sectionStations, Section.STATIONS, onAir > 0
                ? res.getString(R.string.radio_section_stations_on, onAir) : res.getString(R.string.radio_section_stations));
        b.stations.setVisibility(section == Section.STATIONS ? View.VISIBLE : View.GONE);
        b.dialSection.setVisibility(section == Section.DIAL ? View.VISIBLE : View.GONE);
        b.transmissions.setVisibility(section == Section.DECODE ? View.VISIBLE : View.GONE);
        b.relays.setVisibility(section == Section.RELAYS ? View.VISIBLE : View.GONE);

        if (bandId == null) {
            bandId = radio.bands.get(0).id;
            viewModel.tune(bandId, b.dial.getProgress());
        }
        b.bands.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(b.getRoot().getContext());
        for (SessionSnapshot.BandView band : radio.bands) {
            TextView button = (TextView) inflater.inflate(R.layout.item_decision_option, b.bands, false);
            button.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            boolean selected = band.id.equals(bandId);
            button.setText(band.unlocked ? (selected ? "[■ " : "[ ") + band.name + " ]"
                    : res.getString(R.string.band_locked, band.name));
            button.setAlpha(band.unlocked ? 1f : 0.4f);
            button.setEnabled(band.unlocked);
            button.setOnClickListener(band.unlocked ? v -> selectBand(band.id) : null);
            b.bands.addView(button);
        }
        showFrequency();
        SessionSnapshot.BaseView base = snapshot.base;
        b.radioReserve.setText(res.getString(R.string.radio_reserve, base.reserve, base.reserveCapacity, base.batteries));
        b.radioRecharge.setText(res.getString(R.string.radio_recharge, base.rechargePer));
        boolean canCharge = base.batteries > 0 && base.reserve < base.reserveCapacity;
        b.radioRecharge.setAlpha(canCharge ? 1f : 0.4f);
        b.radioRecharge.setEnabled(canCharge);

        b.transmissions.removeAllViews();
        if (radio.transmissions.isEmpty()) {
            TextView none = (TextView) inflater.inflate(R.layout.item_log_line, b.transmissions, false);
            none.setText(R.string.no_transmissions);
            b.transmissions.addView(none);
        }
        for (SessionSnapshot.TransmissionView t : radio.transmissions) {
            TextView row = (TextView) inflater.inflate(R.layout.item_decision_option, b.transmissions, false);
            // Locked ones stay here until you find what they need.
            String kind = t.unlocked ? t.schemeName : res.getString(R.string.transmission_locked, t.schemeName);
            String preview = t.cipherText.length() > 24 ? t.cipherText.substring(0, 24) + "…" : t.cipherText;
            row.setText(res.getString(R.string.transmission_row, kind, preview));
            row.setOnClickListener(v -> host.openDecoder(t, radio.morseTable));
            b.transmissions.addView(row);
        }

        b.stations.removeAllViews();
        if (radio.stations.isEmpty()) {
            TextView none = (TextView) inflater.inflate(R.layout.item_log_line, b.stations, false);
            none.setText(R.string.stations_none);
            b.stations.addView(none);
        }
        for (SessionSnapshot.StationView st : radio.stations) {
            TextView row = (TextView) inflater.inflate(R.layout.item_decision_option, b.stations, false);
            row.setText(res.getString(st.onAir ? R.string.station_row_on : R.string.station_row_off,
                    st.name, st.where, st.schedule + (st.bulletin ? res.getString(R.string.station_bulletin) : "")));
            row.setAlpha(st.onAir ? 1f : 0.6f);
            row.setOnClickListener(v -> tuneTo(st));
            b.stations.addView(row);
        }

        String relays = radio.relays.isEmpty() ? res.getString(R.string.relays_none) : String.join(", ", radio.relays);
        b.relays.setText(res.getString(R.string.relays_line, radio.relayKits, relays));
    }

    private void sectionButton(TextView view, Section which, String label) {
        boolean on = section == which;
        view.setText(label);
        view.setTextColor(ContextCompat.getColor(view.getContext(), on ? R.color.amber_bright : R.color.amber_dim));
        BracketDrawable.mark(view, on);
    }

    void onTuning(SessionSnapshot.Tuning tuning) {
        if (!isShown() || section != Section.DIAL) {
            return;
        }
        Resources res = b.getRoot().getResources();
        b.readout.setText(tuning.text);
        b.station.setText(tuning.station != null ? res.getString(R.string.radio_station, tuning.station) : "");
        StringBuilder meter = new StringBuilder();
        int filled = (int) Math.round(tuning.strength * METER_CELLS);
        for (int i = 0; i < METER_CELLS; i++) {
            meter.append(i < filled ? '▮' : '▯');
        }
        b.meter.setText(meter);
        // What locking on will do: listen for free, or spend reserve.
        b.tune.setText(!tuning.lockable ? res.getString(R.string.tune_button)
                : tuning.cost > 0 ? res.getString(R.string.tune_button_cost, tuning.cost)
                : res.getString(R.string.tune_button_free));
        b.tune.setAlpha(tuning.lockable ? 1f : 0.5f);
        if (tuning.lockable && !wasLockable) {
            sound.click();
        }
        sound.radioMix(tuning.audio, tuning.strength);
        wasLockable = tuning.lockable;

        int tick = -1;
        for (int i = 0; i < TICKS.length; i++) {
            if (tuning.strength >= TICKS[i]) {
                tick = i;
            }
        }
        if (tick > lastTick) {
            if (haptics) {
                b.dial.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        }
        lastTick = tick;
    }

    /** Tapping a station: its band, its frequency, back to the dial. */
    private void tuneTo(SessionSnapshot.StationView st) {
        boolean locked = true;
        for (SessionSnapshot.BandView band : snapshot.radio.bands) {
            locked &= !(band.id.equals(st.bandId) && band.unlocked);
        }
        if (locked) {
            return;
        }
        if (!st.bandId.equals(bandId)) {
            sound.bandSwitch();
        }
        bandId = st.bandId;
        lastTick = -1;
        section = Section.DIAL;
        sound.startRadio();
        b.dial.setProgress(st.position);
        render(snapshot);
        viewModel.tune(bandId, st.position);
    }

    private void selectBand(String id) {
        if (!id.equals(bandId)) {
            sound.bandSwitch();
        }
        bandId = id;
        lastTick = -1;
        render(snapshot);
        viewModel.tune(bandId, b.dial.getProgress());
    }

    private void showFrequency() {
        if (snapshot == null) {
            return;
        }
        for (SessionSnapshot.BandView band : snapshot.radio.bands) {
            if (band.id.equals(bandId)) {
                b.frequency.setText(b.getRoot().getResources().getString(R.string.frequency,
                        band.frequency.apply(b.dial.getProgress()), band.name));
            }
        }
    }
}
