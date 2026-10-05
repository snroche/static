package com.estatica.ui.terminal;

import android.view.View;

import com.estatica.R;
import com.estatica.data.session.SessionSnapshot;
import com.estatica.databinding.PanelDecodeBinding;
import com.estatica.domain.radio.Cipher;

/**
 * Decoding with on-screen controls (GDD §6.2): a shift dial for Caesar, a dot/dash pad for
 * Morse. The answer is checked by the game, not here.
 */
final class DecoderPanel {

    interface Host {
        void closeDecoder();
    }

    private final PanelDecodeBinding b;
    private final TerminalViewModel viewModel;
    private SessionSnapshot.TransmissionView transmission;
    private int shift;
    private final StringBuilder typed = new StringBuilder();
    private final StringBuilder symbol = new StringBuilder();

    DecoderPanel(PanelDecodeBinding binding, TerminalViewModel viewModel, Host host) {
        this.b = binding;
        this.viewModel = viewModel;
        b.back.setOnClickListener(v -> host.closeDecoder());
        b.shiftUp.setOnClickListener(v -> setShift(shift + 1));
        b.shiftDown.setOnClickListener(v -> setShift(shift - 1));
        b.dot.setOnClickListener(v -> addSymbol(Cipher.DOT));
        b.dash.setOnClickListener(v -> addSymbol(Cipher.DASH));
        b.letter.setOnClickListener(v -> commitLetter());
        b.space.setOnClickListener(v -> {
            commitLetter();
            if (typed.length() > 0 && typed.charAt(typed.length() - 1) != ' ') {
                typed.append(' ');
            }
            showMorseAnswer();
        });
        b.erase.setOnClickListener(v -> {
            if (symbol.length() > 0) {
                symbol.setLength(symbol.length() - 1);
            } else if (typed.length() > 0) {
                typed.setLength(typed.length() - 1);
            }
            showMorseAnswer();
        });
        b.tableButton.setOnClickListener(v ->
                b.table.setVisibility(b.table.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        b.table.setText(morseTable());
        b.send.setOnClickListener(v -> {
            if (transmission != null) {
                viewModel.decode(transmission.id, answer());
                host.closeDecoder();
            }
        });
    }

    boolean isShown() {
        return b.getRoot().getVisibility() == View.VISIBLE;
    }

    void show(SessionSnapshot.TransmissionView t, boolean morseTable) {
        transmission = t;
        shift = 0;
        typed.setLength(0);
        symbol.setLength(0);
        boolean morse = "MORSE".equals(t.kind);
        b.title.setText(morse ? R.string.decode_morse_title : R.string.decode_caesar_title);
        b.instructions.setText(morse ? R.string.decode_morse_help : R.string.decode_caesar_help);
        b.cipher.setText(t.cipherText);
        b.caesarControls.setVisibility(morse ? View.GONE : View.VISIBLE);
        b.morseControls.setVisibility(morse ? View.VISIBLE : View.GONE);
        b.tableButton.setVisibility(morseTable ? View.VISIBLE : View.GONE);
        b.table.setVisibility(View.GONE);
        if (morse) {
            showMorseAnswer();
        } else {
            setShift(0);
        }
        b.getRoot().setVisibility(View.VISIBLE);
    }

    void hide() {
        b.getRoot().setVisibility(View.GONE);
    }

    private String answer() {
        if ("MORSE".equals(transmission.kind)) {
            commitLetter();
            return typed.toString();
        }
        return Cipher.caesar(transmission.cipherText, -shift);
    }

    private void setShift(int value) {
        shift = Math.floorMod(value, 26);
        b.shift.setText(b.getRoot().getResources().getString(R.string.shift_value, shift));
        b.answer.setText(Cipher.caesar(transmission.cipherText, -shift));
    }

    private void addSymbol(char c) {
        if (symbol.length() < 5) {
            symbol.append(c);
        }
        showMorseAnswer();
    }

    private void commitLetter() {
        if (symbol.length() == 0) {
            return;
        }
        Character letter = Cipher.letter(symbol.toString());
        typed.append(letter != null ? letter : '?');
        symbol.setLength(0);
        showMorseAnswer();
    }

    // Typed letters plus the symbols of the letter in progress: no translatable text here.
    @android.annotation.SuppressLint("SetTextI18n")
    private void showMorseAnswer() {
        b.answer.setText(typed + (symbol.length() > 0 ? "[" + symbol + "]" : "_"));
    }

    private static String morseTable() {
        StringBuilder out = new StringBuilder();
        for (char c = 'A'; c <= 'Z'; c++) {
            String cell = c + " " + Cipher.code(c);
            out.append(cell);
            int col = (c - 'A') % 3;
            if (col == 2 || c == 'Z') {
                out.append('\n');
            } else {
                for (int i = cell.length(); i < 10; i++) {
                    out.append(' ');
                }
            }
        }
        return out.toString();
    }
}
