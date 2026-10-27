package com.estatica.ui.terminal;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.estatica.R;
import com.estatica.data.link.NearbyLink;
import com.estatica.databinding.DialogLinkCodeBinding;

/**
 * A code just made (RADIO → LINK), and the three ways to get it to the other bunker: TEXT to
 * copy or share, QR to scan, NEARBY over the Bluetooth link. Closing it hides the code; it can
 * be shown again from the codes sent lately.
 */
final class CodeDialog {

    interface Host {
        void sendNearby(String code);

        /** Takes the operator to LINK → NEARBY to set up a link. */
        void openNearby();

        void closed();
    }

    private enum Page { TEXT, QR, NEARBY }

    private final Context context;
    private final Host host;
    private Dialog dialog;
    private DialogLinkCodeBinding d;
    private String code;
    private String callsign;
    private Page page = Page.TEXT;
    private LinkController.View near = new LinkController.View();
    private boolean sent;

    CodeDialog(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    String code() {
        return dialog != null && dialog.isShowing() ? code : null;
    }

    void show(String code, String label, String callsign, LinkController.View near) {
        if (code.equals(code())) {
            return;
        }
        this.code = code;
        this.callsign = callsign;
        this.near = near;
        sent = false;
        page = Page.TEXT;
        if (dialog != null) {
            dialog.setOnDismissListener(null);
            dialog.dismiss();
        }
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d = DialogLinkCodeBinding.inflate(LayoutInflater.from(context));
        dialog.setContentView(d.getRoot());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.codeTitle.setText(context.getString(R.string.code_title, callsign));
        d.codeLabel.setText(label != null ? label : "");
        d.codeText.setText(code);
        d.codeClose.setOnClickListener(v -> dialog.dismiss());
        d.codeCopy.setOnClickListener(v -> copy());
        d.codeShare.setOnClickListener(v -> share());
        d.codeTabText.setOnClickListener(v -> showPage(Page.TEXT));
        d.codeTabQr.setOnClickListener(v -> showPage(Page.QR));
        d.codeTabNearby.setOnClickListener(v -> showPage(Page.NEARBY));
        dialog.setOnDismissListener(x -> host.closed());
        showPage(Page.TEXT);
        dialog.show();
    }

    void onNearby(LinkController.View near) {
        this.near = near;
        if (code() != null && page == Page.NEARBY) {
            showPage(Page.NEARBY);
        }
    }

    private void showPage(Page target) {
        page = target;
        tab(d.codeTabText, page == Page.TEXT);
        tab(d.codeTabQr, page == Page.QR);
        tab(d.codeTabNearby, page == Page.NEARBY);
        d.codeTextPage.setVisibility(page == Page.TEXT ? View.VISIBLE : View.GONE);
        d.codeQrPage.setVisibility(page == Page.QR ? View.VISIBLE : View.GONE);
        d.codeNearbyPage.setVisibility(page == Page.NEARBY ? View.VISIBLE : View.GONE);
        if (page == Page.QR && d.codeQr.getDrawable() == null) {
            Bitmap qr = qr(code);
            if (qr != null) {
                d.codeQr.setImageBitmap(qr);
            }
        }
        if (page == Page.NEARBY) {
            boolean linked = near.state == NearbyLink.State.CONNECTED;
            d.codeNearbyStatus.setText(sent ? context.getString(R.string.code_nearby_sent, near.peer)
                    : linked ? context.getString(R.string.code_nearby_linked, near.peer != null ? near.peer : "…")
                    : context.getString(R.string.code_nearby_off));
            d.codeNearbyAction.setText(linked ? R.string.code_nearby_send : R.string.code_nearby_setup);
            d.codeNearbyAction.setAlpha(sent ? 0.4f : 1f);
            d.codeNearbyAction.setOnClickListener(sent ? null : linked ? v -> {
                host.sendNearby(code);
                sent = true;
                showPage(Page.NEARBY);
            } : v -> {
                dialog.dismiss();
                host.openNearby();
            });
        }
    }

    private void tab(TextView view, boolean on) {
        view.setTextColor(ContextCompat.getColor(context, on ? R.color.amber_bright : R.color.amber_dim));
        BracketDrawable.mark(view, on);
    }

    /** Dark modules on amber: in theme, and still the light-background QR every scanner reads. */
    private Bitmap qr(String text) {
        int size = Math.min(context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels) * 7 / 10;
        try {
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.EnumMap<>(
                    com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION,
                    com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L);
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 2);
            com.google.zxing.common.BitMatrix m = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints);
            int paper = ContextCompat.getColor(context, R.color.amber_bright);
            int ink = ContextCompat.getColor(context, R.color.terminal_bg);
            int w = m.getWidth();
            int h = m.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pixels[y * w + x] = m.get(x, y) ? ink : paper;
                }
            }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.RGB_565);
        } catch (com.google.zxing.WriterException e) {
            return null;
        }
    }

    private void copy() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.link_clip_label), code));
            Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.link_share_text, callsign, code));
        context.startActivity(Intent.createChooser(send, context.getString(R.string.link_share)));
    }
}
