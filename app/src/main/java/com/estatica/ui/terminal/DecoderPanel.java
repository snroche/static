package com.estatica.ui.terminal;

import android.view.View;
import android.widget.TextView;

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
    /** Morse with the manual: tap letters instead of building them. */
    private boolean keyboard;
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
        b.keyInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                showKeywordPreview();
            }
        });
        buildTapGrid();
        buildMorseKeyboard();
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
        b.keyInput.setText("");
        String kind = t.kind;
        boolean morse = "MORSE".equals(kind);
        boolean caesar = "CAESAR".equals(kind);
        android.content.res.Resources res = b.getRoot().getResources();
        b.title.setText(t.unlocked ? res.getString(R.string.decode_title, t.schemeName)
                : res.getString(R.string.decode_locked_title));
        b.cipher.setText(t.cipherText);
        b.caesarControls.setVisibility(t.unlocked && caesar ? View.VISIBLE : View.GONE);
        // With the manual, Morse is a letter keyboard; without it, the dot/dash pad.
        keyboard = morse && morseTable;
        b.morseControls.setVisibility(t.unlocked && morse && !keyboard ? View.VISIBLE : View.GONE);
        b.morseKeyboard.setVisibility(t.unlocked && keyboard ? View.VISIBLE : View.GONE);
        b.tableButton.setVisibility(View.GONE);
        b.table.setVisibility(View.GONE);
        b.tapControls.setVisibility(t.unlocked && "TAP".equals(kind) ? View.VISIBLE : View.GONE);
        boolean keyword = "VIGENERE".equals(kind) || "PLAYFAIR".equals(kind);
        b.keyControls.setVisibility(t.unlocked && keyword ? View.VISIBLE : View.GONE);
        b.keyHint.setText(t.keyHint != null ? t.keyHint : "");
        b.locked.setVisibility(t.unlocked ? View.GONE : View.VISIBLE);
        b.locked.setText(t.lockedHint);
        b.send.setVisibility(t.unlocked ? View.VISIBLE : View.GONE);
        b.tool.setVisibility(t.unlocked && t.assisted != null ? View.VISIBLE : View.GONE);
        if (t.assisted != null) {
            b.tool.setText("PLAYFAIR".equals(kind) ? res.getString(R.string.decode_letters, t.assisted)
                    : res.getString(R.string.decode_tool, t.assisted));
        }
        b.instructions.setText(keyboard ? R.string.decode_morse_keyboard_help : help(kind));
        if (!t.unlocked) {
            b.answer.setText("");
        } else if (morse || "TAP".equals(kind)) {
            showMorseAnswer();
        } else if (caesar) {
            setShift(0);
        } else if (keyword) {
            showKeywordPreview();
        } else {
            b.answer.setText(t.assisted != null ? t.assisted : "");
        }
        b.getRoot().setVisibility(View.VISIBLE);
    }

    private static int help(String kind) {
        switch (kind) {
            case "MORSE":
                return R.string.decode_morse_help;
            case "ATBASH":
                return R.string.decode_help_atbash;
            case "TAP":
                return R.string.decode_help_tap;
            case "VIGENERE":
                return R.string.decode_help_vigenere;
            case "BASE64":
                return R.string.decode_help_base64;
            case "PLAYFAIR":
                return R.string.decode_help_playfair;
            case "NUMBERS":
                return R.string.decode_help_numbers;
            case "BOOK":
                return R.string.decode_help_book;
            default:
                return R.string.decode_caesar_help;
        }
    }

    /** Vigenère or Playfair with the keyword typed so far. */
    private void showKeywordPreview() {
        if (transmission == null) {
            return;
        }
        String key = b.keyInput.getText().toString();
        if ("VIGENERE".equals(transmission.kind)) {
            b.answer.setText(com.estatica.domain.radio.Ciphers.vigenere(transmission.cipherText, key, true));
        } else if ("PLAYFAIR".equals(transmission.kind) && transmission.assisted != null) {
            b.answer.setText(key.isBlank() ? transmission.assisted
                    : com.estatica.domain.radio.Ciphers.playfair(transmission.assisted, key, true));
        }
    }

    /** Tap code: a 5×5 grid of letters (C and K share), plus space and erase. */
    private void buildTapGrid() {
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(b.getRoot().getContext());
        for (int row = 1; row <= 5; row++) {
            android.widget.LinearLayout line = new android.widget.LinearLayout(b.getRoot().getContext());
            line.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            for (int col = 1; col <= 5; col++) {
                char letter = com.estatica.domain.radio.Ciphers.tapLetter(row, col);
                TextView cell = (TextView) inflater.inflate(R.layout.item_decision_option, line, false);
                cell.setText(letter == 'C' ? "C/K" : String.valueOf(letter));
                cell.setGravity(android.view.Gravity.CENTER);
                android.widget.LinearLayout.LayoutParams lp =
                        new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cell.setLayoutParams(lp);
                cell.setOnClickListener(v -> {
                    typed.append(letter);
                    showMorseAnswer();
                });
                line.addView(cell);
            }
            b.tapControls.addView(line);
        }
        android.widget.LinearLayout tools = new android.widget.LinearLayout(b.getRoot().getContext());
        tools.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        TextView space = (TextView) inflater.inflate(R.layout.item_decision_option, tools, false);
        space.setText(R.string.morse_space);
        space.setOnClickListener(v -> {
            if (typed.length() > 0 && typed.charAt(typed.length() - 1) != ' ') {
                typed.append(' ');
            }
            showMorseAnswer();
        });
        TextView erase = (TextView) inflater.inflate(R.layout.item_decision_option, tools, false);
        erase.setText(R.string.morse_erase);
        erase.setOnClickListener(v -> {
            if (typed.length() > 0) {
                typed.setLength(typed.length() - 1);
            }
            showMorseAnswer();
        });
        tools.addView(space);
        tools.addView(erase);
        b.tapControls.addView(tools);
    }

    void hide() {
        b.getRoot().setVisibility(View.GONE);
    }

    private String answer() {
        switch (transmission.kind) {
            case "MORSE":
                commitLetter();
                return typed.toString();
            case "TAP":
                return typed.toString();
            case "CAESAR":
                return Cipher.caesar(transmission.cipherText, -shift);
            default:
                return b.answer.getText().toString();
        }
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
        autoSpace();
        showMorseAnswer();
    }

    /** Morse groups in the cipher, word breaks as "/". */
    private String[] groups() {
        return transmission.cipherText.trim().split("\\s+");
    }

    /** Index of the group the next letter decodes, or -1 past the end. */
    private int currentGroup() {
        int letters = 0;
        for (int i = 0; i < typed.length(); i++) {
            letters += typed.charAt(i) == ' ' ? 0 : 1;
        }
        String[] groups = groups();
        for (int i = 0; i < groups.length; i++) {
            if (!"/".equals(groups[i]) && letters-- == 0) {
                return i;
            }
        }
        return -1;
    }

    /** After the last letter of a word, the space goes in by itself. */
    private void autoSpace() {
        if (!"MORSE".equals(transmission.kind) || typed.length() == 0 || typed.charAt(typed.length() - 1) == ' ') {
            return;
        }
        int next = currentGroup();
        String[] groups = groups();
        if (next > 0 && "/".equals(groups[next - 1])) {
            typed.append(' ');
        }
    }

    /** The cipher with the group being decoded lit up. */
    private void highlightCipher() {
        if (!"MORSE".equals(transmission.kind)) {
            return;
        }
        int current = currentGroup();
        android.text.SpannableStringBuilder out = new android.text.SpannableStringBuilder();
        String[] groups = groups();
        int bright = androidx.core.content.ContextCompat.getColor(b.getRoot().getContext(), R.color.amber_bright);
        int dim = androidx.core.content.ContextCompat.getColor(b.getRoot().getContext(), R.color.amber_dim);
        for (int i = 0; i < groups.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            int start = out.length();
            out.append(groups[i]);
            int color = i == current ? bright : current >= 0 && i < current ? dim : 0;
            if (color != 0) {
                out.setSpan(new android.text.style.ForegroundColorSpan(color), start, out.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (i == current) {
                out.setSpan(new android.text.style.UnderlineSpan(), start, out.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, out.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        b.cipher.setText(out);
    }

    /** A–Z, each with its code, four to a row; then space and erase. */
    private void buildMorseKeyboard() {
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(b.getRoot().getContext());
        android.widget.LinearLayout line = null;
        for (char c = 'A'; c <= 'Z'; c++) {
            if ((c - 'A') % 4 == 0) {
                line = new android.widget.LinearLayout(b.getRoot().getContext());
                line.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                b.morseKeyboard.addView(line);
            }
            char letter = c;
            TextView key = (TextView) inflater.inflate(R.layout.item_decision_option, line, false);
            key.setText(b.getRoot().getResources().getString(R.string.morse_key, String.valueOf(letter), Cipher.code(letter)));
            key.setGravity(android.view.Gravity.CENTER);
            key.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            key.setContentDescription(String.valueOf(letter));
            key.setOnClickListener(v -> {
                typed.append(letter);
                autoSpace();
                showMorseAnswer();
            });
            line.addView(key);
        }
        TextView space = (TextView) inflater.inflate(R.layout.item_decision_option, line, false);
        space.setText(R.string.morse_space);
        space.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        space.setOnClickListener(v -> {
            if (typed.length() > 0 && typed.charAt(typed.length() - 1) != ' ') {
                typed.append(' ');
            }
            showMorseAnswer();
        });
        TextView erase = (TextView) inflater.inflate(R.layout.item_decision_option, line, false);
        erase.setText(R.string.morse_erase);
        erase.setContentDescription(b.getRoot().getResources().getString(R.string.erase_description));
        erase.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        erase.setOnClickListener(v -> {
            // An auto-space goes with the letter before it.
            if (typed.length() > 0 && typed.charAt(typed.length() - 1) == ' ') {
                typed.setLength(typed.length() - 1);
            }
            if (typed.length() > 0) {
                typed.setLength(typed.length() - 1);
            }
            showMorseAnswer();
        });
        line.addView(space);
        line.addView(erase);
    }

    // Typed letters plus the symbols of the letter in progress: no translatable text here.
    @android.annotation.SuppressLint("SetTextI18n")
    private void showMorseAnswer() {
        b.answer.setText(typed + (symbol.length() > 0 ? "[" + symbol + "]" : "_"));
        if (transmission != null) {
            highlightCipher();
        }
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
