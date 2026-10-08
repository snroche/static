package com.estatica.ui.terminal;

import android.content.res.Resources;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import com.estatica.R;
import com.estatica.databinding.PanelOptionsBinding;

/** The ⚙ screen: what the crew calls you, sound, haptics, notifications, text speed, CRT. */
final class OptionsPanel {

    private final PanelOptionsBinding b;
    private final TerminalViewModel viewModel;

    OptionsPanel(PanelOptionsBinding binding, TerminalViewModel viewModel, Runnable close, String version) {
        this.b = binding;
        this.viewModel = viewModel;
        b.optionsClose.setOnClickListener(v -> close.run());
        b.optionsNameSave.setOnClickListener(v -> saveName());
        b.optionsName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveName();
                return true;
            }
            return false;
        });
        b.optionsSound.setOnClickListener(v -> viewModel.toggleSoundOption());
        b.optionsHaptics.setOnClickListener(v -> viewModel.toggleHapticsOption());
        b.optionsNotifications.setOnClickListener(v -> viewModel.toggleNotificationsOption());
        b.optionsTextSpeed.setOnClickListener(v -> viewModel.cycleTextSpeed());
        b.optionsCrt.setOnClickListener(v -> viewModel.toggleCrtOption());
        b.optionsVersion.setText(b.getRoot().getResources().getString(R.string.options_version, version));
    }

    private void saveName() {
        String name = b.optionsName.getText().toString().trim();
        if (!name.isEmpty()) {
            viewModel.rename(name);
        }
    }

    boolean isShown() {
        return b.getRoot().getVisibility() == View.VISIBLE;
    }

    void show() {
        b.getRoot().setVisibility(View.VISIBLE);
    }

    void hide() {
        b.getRoot().setVisibility(View.GONE);
    }

    void render(OptionsState o) {
        Resources res = b.getRoot().getResources();
        if (!b.optionsName.hasFocus()) {
            b.optionsName.setText(o.operatorName != null ? o.operatorName : "");
        }
        b.optionsSound.setText(res.getString(R.string.options_toggle, check(o.sound), res.getString(R.string.options_sound)));
        b.optionsHaptics.setText(res.getString(R.string.options_toggle, check(o.haptics), res.getString(R.string.options_haptics)));
        b.optionsNotifications.setText(res.getString(R.string.options_toggle, check(o.notifications),
                res.getString(R.string.options_notifications)));
        String[] speeds = res.getStringArray(R.array.options_text_speeds);
        b.optionsTextSpeed.setText(res.getString(R.string.options_text_speed, speeds[Math.min(o.textSpeed, speeds.length - 1)]));
        b.optionsCrt.setText(res.getString(R.string.options_toggle, check(o.crt), res.getString(R.string.options_crt)));
    }

    private static String check(boolean on) {
        return on ? "x" : " ";
    }
}
