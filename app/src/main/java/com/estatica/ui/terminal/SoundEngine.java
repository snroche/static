package com.estatica.ui.terminal;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Terminal sounds synthesized in code (GDD §2): static, dial click, typing tick, alert beep.
 * Each is generated once into a small static buffer, so there are no audio assets and no
 * work while silent. Real recordings can replace these later.
 */
final class SoundEngine {

    private static final int RATE = 22050;

    private final AudioTrack noise;
    private final AudioTrack click;
    private final AudioTrack tick;
    private final AudioTrack beep;
    private boolean enabled;

    SoundEngine(boolean enabled) {
        this.enabled = enabled;
        Random random = new Random(7);
        noise = track(staticBurst(random, 0.12));
        click = track(clickBurst(0.012));
        tick = track(staticBurst(random, 0.006));
        beep = track(tone(880, 0.15));
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Static while tuning: louder the farther from a signal. */
    void staticNoise(float volume) {
        play(noise, volume);
    }

    void click() {
        play(click, 0.6f);
    }

    /** A key of the teletype. */
    void tick() {
        play(tick, 0.12f);
    }

    /** Urgent message. */
    void beep() {
        play(beep, 0.5f);
    }

    void release() {
        noise.release();
        click.release();
        tick.release();
        beep.release();
    }

    private void play(AudioTrack track, float volume) {
        if (!enabled || track.getState() != AudioTrack.STATE_INITIALIZED) {
            return;
        }
        try {
            track.stop();
            track.reloadStaticData();
            track.setVolume(volume);
            track.play();
        } catch (IllegalStateException ignored) {
            // A sound that can't play is never worth a crash.
        }
    }

    private static AudioTrack track(short[] pcm) {
        AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(pcm.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        t.write(pcm, 0, pcm.length);
        return t;
    }

    /** White noise with a short fade-out, roughly low-passed for a "radio" color. */
    private static short[] staticBurst(Random random, double seconds) {
        int n = (int) (RATE * seconds);
        short[] out = new short[n];
        double last = 0;
        for (int i = 0; i < n; i++) {
            double white = random.nextDouble() * 2 - 1;
            last = last * 0.6 + white * 0.4;
            double envelope = 1.0 - (double) i / n;
            out[i] = (short) (last * envelope * 12000);
        }
        return out;
    }

    private static short[] clickBurst(double seconds) {
        int n = (int) (RATE * seconds);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double envelope = Math.exp(-i / (n / 5.0));
            out[i] = (short) ((i % 20 < 10 ? 1 : -1) * envelope * 14000);
        }
        return out;
    }

    private static short[] tone(double hz, double seconds) {
        int n = (int) (RATE * seconds);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double envelope = Math.min(1, i / (RATE * 0.01)) * Math.min(1, (n - i) / (RATE * 0.03));
            out[i] = (short) (Math.sin(2 * Math.PI * hz * t) * envelope * 10000);
        }
        return out;
    }
}
