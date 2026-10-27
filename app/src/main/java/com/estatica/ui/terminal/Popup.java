package com.estatica.ui.terminal;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;

import com.estatica.databinding.DialogPopupBinding;

/**
 * A framed popup in the terminal's look (black, amber frame, rounded corners): a title, a close
 * button and a body the caller fills, and can refill while it's open.
 */
final class Popup {

    private final Dialog dialog;
    private final DialogPopupBinding b;

    private Popup(Context context, CharSequence title, Runnable onClose) {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        b = DialogPopupBinding.inflate(LayoutInflater.from(context));
        dialog.setContentView(b.getRoot());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        b.popupTitle.setText(title);
        b.popupClose.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            if (onClose != null) {
                onClose.run();
            }
        });
    }

    static Popup open(Context context, CharSequence title, Runnable onClose) {
        Popup p = new Popup(context, title, onClose);
        p.dialog.show();
        return p;
    }

    LinearLayout body() {
        return b.popupBody;
    }

    void setTitle(CharSequence title) {
        b.popupTitle.setText(title);
    }

    boolean isShowing() {
        return dialog.isShowing();
    }

    void dismiss() {
        dialog.dismiss();
    }
}
