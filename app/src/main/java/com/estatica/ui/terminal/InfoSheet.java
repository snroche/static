package com.estatica.ui.terminal;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.estatica.R;
import com.estatica.databinding.PanelInfoBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Long-press anything: the sheet explains every known term in that text (traits, stats,
 * resources, places, gear, moods…), in the order they appear.
 */
final class InfoSheet {

    private final PanelInfoBinding b;
    private Map<String, String> glossary = Map.of();

    InfoSheet(PanelInfoBinding binding, Runnable onClose) {
        this.b = binding;
        b.infoClose.setOnClickListener(v -> {
            hide();
            onClose.run();
        });
    }

    void setGlossary(Map<String, String> glossary) {
        this.glossary = glossary;
    }

    boolean isShown() {
        return b.getRoot().getVisibility() == View.VISIBLE;
    }

    void hide() {
        b.getRoot().setVisibility(View.GONE);
    }

    /** Makes a view explain itself on long-press. */
    void explainOnLongPress(TextView view) {
        view.setOnLongClickListener(v -> {
            show(view.getText());
            return true;
        });
    }

    /** A key item's text (or any titled text), as is. */
    void showText(String title, String body) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        int bright = ContextCompat.getColor(b.getRoot().getContext(), R.color.amber_bright);
        out.append(title.toUpperCase(Locale.ROOT));
        out.setSpan(new StyleSpan(Typeface.BOLD), 0, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(bright), 0, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.append("\n\n").append(body);
        b.infoText.setText(out);
        b.getRoot().setVisibility(View.VISIBLE);
    }

    void show(CharSequence context) {
        String text = context.toString();
        List<int[]> found = new ArrayList<>();
        List<String> terms = new ArrayList<>();
        for (String term : glossary.keySet()) {
            // Explicit word edges, no inline flags (Android's ICU regex).
            Matcher m = Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(term) + "(?![\\p{L}\\d])",
                    Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) {
                found.add(new int[]{m.start(), terms.size()});
                terms.add(term);
            }
        }
        found.sort((x, y) -> Integer.compare(x[0], y[0]));
        SpannableStringBuilder out = new SpannableStringBuilder();
        int bright = ContextCompat.getColor(b.getRoot().getContext(), R.color.amber_bright);
        for (int[] f : found) {
            String term = terms.get(f[1]);
            if (out.length() > 0) {
                out.append("\n\n");
            }
            int start = out.length();
            out.append(term.toUpperCase(Locale.ROOT));
            out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new ForegroundColorSpan(bright), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.append("\n").append(glossary.get(term));
        }
        if (out.length() == 0) {
            out.append(b.getRoot().getContext().getString(R.string.info_nothing));
        }
        b.infoText.setText(out);
        b.getRoot().setVisibility(View.VISIBLE);
    }
}
