package com.estatica.ui.terminal;

import android.os.Handler;

/**
 * Reveals one line character by character with a single Handler callback. Only the active
 * line animates; everything else is static text. Nothing runs when no line is typing.
 */
final class Typewriter {

    interface Listener {
        void onTick(int visibleChars);

        void onFinished();
    }

    /** Roughly one frame: below this we reveal several chars per tick instead. */
    private static final int MIN_TICK_MILLIS = 16;

    private final Handler handler;
    private final Listener listener;
    private int total;
    private int visible;
    private int charsPerTick;
    private int tickMillis;
    private boolean running;

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            visible = Math.min(total, visible + charsPerTick);
            listener.onTick(visible);
            if (visible >= total) {
                finish();
            } else {
                handler.postDelayed(this, tickMillis);
            }
        }
    };

    Typewriter(Handler handler, Listener listener) {
        this.handler = handler;
        this.listener = listener;
    }

    void start(int totalChars, int charMillis) {
        cancel();
        total = totalChars;
        visible = 0;
        charsPerTick = Math.max(1, (MIN_TICK_MILLIS + charMillis - 1) / Math.max(1, charMillis));
        tickMillis = Math.max(1, charMillis) * charsPerTick;
        running = true;
        if (total == 0) {
            finish();
        } else {
            handler.postDelayed(step, tickMillis);
        }
    }

    /** Completes the active line immediately (tap to skip). */
    void skip() {
        if (!running) {
            return;
        }
        handler.removeCallbacks(step);
        visible = total;
        listener.onTick(visible);
        finish();
    }

    boolean isRunning() {
        return running;
    }

    void cancel() {
        handler.removeCallbacks(step);
        running = false;
    }

    private void finish() {
        running = false;
        listener.onFinished();
    }
}
