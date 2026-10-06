package com.estatica.ui.terminal;

import android.content.res.Resources;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;
import com.estatica.databinding.PanelRadioBinding;

/**
 * The radio (GDD §6.1): pick a band, move the dial, lock onto signals; plus pending
 * transmissions, the antenna and relay kits.
 */
final class RadioPanel {

    interface Host {
        void openDecoder(SessionSnapshot.TransmissionView transmission, boolean morseTable);

        void closeRadio();
    }

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
        b.tune.setOnClickListener(v -> {
            if (bandId != null) {
                sound.click();
                viewModel.capture(bandId, b.dial.getProgress());
            }
        });
    }

    boolean isShown() {
        return b.getRoot().getVisibility() == View.VISIBLE;
    }

    void show() {
        b.getRoot().setVisibility(View.VISIBLE);
        if (snapshot != null) {
            render(snapshot);
        }
    }

    void hide() {
        b.getRoot().setVisibility(View.GONE);
    }

    void render(SessionSnapshot snapshot) {
        this.snapshot = snapshot;
        if (!isShown()) {
            return;
        }
        Resources res = b.getRoot().getResources();
        SessionSnapshot.RadioView radio = snapshot.radio;
        b.dial.setMax(radio.dialMax - 1);

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

        b.transmissions.removeAllViews();
        if (radio.transmissions.isEmpty()) {
            TextView none = (TextView) inflater.inflate(R.layout.item_log_line, b.transmissions, false);
            none.setText(R.string.no_transmissions);
            b.transmissions.addView(none);
        }
        for (SessionSnapshot.TransmissionView t : radio.transmissions) {
            TextView row = (TextView) inflater.inflate(R.layout.item_decision_option, b.transmissions, false);
            String kind = res.getString("MORSE".equals(t.kind) ? R.string.kind_morse : R.string.kind_caesar);
            String preview = t.cipherText.length() > 24 ? t.cipherText.substring(0, 24) + "…" : t.cipherText;
            row.setText(res.getString(R.string.transmission_row, kind, preview));
            row.setOnClickListener(v -> host.openDecoder(t, radio.morseTable));
            b.transmissions.addView(row);
        }

        String relays = radio.relays.isEmpty() ? res.getString(R.string.relays_none) : String.join(", ", radio.relays);
        b.relays.setText(res.getString(R.string.relays_line, radio.relayKits, relays));
    }

    void onTuning(SessionSnapshot.Tuning tuning) {
        if (!isShown()) {
            return;
        }
        b.readout.setText(tuning.text);
        StringBuilder meter = new StringBuilder();
        int filled = (int) Math.round(tuning.strength * METER_CELLS);
        for (int i = 0; i < METER_CELLS; i++) {
            meter.append(i < filled ? '▮' : '▯');
        }
        b.meter.setText(meter);
        b.tune.setAlpha(tuning.lockable ? 1f : 0.5f);
        if (tuning.lockable && !wasLockable) {
            sound.click();
        } else if (!tuning.lockable) {
            sound.staticNoise((float) (0.35 * (1.0 - tuning.strength)));
        }
        wasLockable = tuning.lockable;

        int tick = -1;
        for (int i = 0; i < TICKS.length; i++) {
            if (tuning.strength >= TICKS[i]) {
                tick = i;
            }
        }
        if (tick > lastTick) {
            b.dial.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        lastTick = tick;
    }

    private void selectBand(String id) {
        bandId = id;
        lastTick = -1;
        render(snapshot);
        viewModel.tune(bandId, b.dial.getProgress());
    }

    private void showFrequency() {
        String bandName = "";
        if (snapshot != null) {
            for (SessionSnapshot.BandView band : snapshot.radio.bands) {
                if (band.id.equals(bandId)) {
                    bandName = band.name;
                }
            }
        }
        b.frequency.setText(b.getRoot().getResources().getString(R.string.frequency, b.dial.getProgress(), bandName));
    }
}
