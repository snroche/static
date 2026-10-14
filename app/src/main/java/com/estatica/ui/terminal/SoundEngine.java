package com.estatica.ui.terminal;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Terminal sounds synthesized in code (GDD §2): static, dial click, typing tick, alert beep,
 * and the radio itself. Each sound is generated once into a small static buffer, so there are
 * no audio assets and no work while silent.
 *
 * <p>The radio is two loops playing at once while its screen is open: a bed of static, and
 * whatever the dial is near (a voice babbling, Morse, a modem warble, music, breathing…). Their
 * volumes follow the signal strength, and the signal loop plays slightly off pitch when you're
 * off frequency, the way a single-sideband voice goes chipmunk until you tune it in.
 */
final class SoundEngine {

    private static final int RATE = 22050;

    /** What the dial can sound like (SessionSnapshot.Tuning.audio). */
    enum Audio {
        NONE, VOICE, WHISPER, MORSE, DATA, MUSIC, BREATH, CARRIER,
        /** A woman talking. */
        VOICE_F,
        /** A child. */
        CHILD,
        /** A slow man's voice in a big echoing room. */
        PREACHER,
        /** Voices holding notes together. */
        CHOIR,
        /** A hymn on a church organ. */
        ORGAN,
        /** A music box waltz. */
        WALTZ,
        /** A numbers station: a flat voice in groups, and a chime. */
        NUMBERS,
        /** An upright piano (Für Elise, slightly out of tune). */
        PIANO,
        /** A guitar on a porch (Greensleeves). */
        GUITAR,
        /** An accordion and a bass, a slow tango of our own. */
        ACCORDION
    }

    private final AudioTrack click;
    private final AudioTrack tick;
    private final AudioTrack beep;
    private AudioTrack sweep;
    /** Built on a background thread at startup; null until ready (then never touched again). */
    private volatile AudioTrack bed;
    private volatile Map<Audio, AudioTrack> loops = new EnumMap<>(Audio.class);
    private Audio playing = Audio.NONE;
    private boolean radioOn;
    private boolean enabled;
    /** What the dial was hearing when the loops weren't ready yet. */
    private String pendingAudio;
    private double pendingStrength;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile boolean released;

    SoundEngine(boolean enabled) {
        this.enabled = enabled;
        Random random = new Random(7);
        click = track(clickBurst(0.012));
        tick = track(staticBurst(random, 0.006));
        beep = track(tone(880, 0.15));
        // Synthesizing every loop takes a moment: do it now, off the main thread, so opening
        // the radio never stalls the screen.
        Thread builder = new Thread(this::build, "radio-sounds");
        builder.setPriority(Thread.MIN_PRIORITY);
        builder.setDaemon(true);
        builder.start();
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            stopRadio();
        }
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

    // ---- Radio ----

    /** The radio screen opened: static starts hissing. Loops are built the first time. */
    void startRadio() {
        if (!enabled) {
            return;
        }
        radioOn = true;
        loop(bed, 0.25f, 1f); // nothing yet if still building; build() starts it when done
    }

    /** The radio screen closed, or the app went away. */
    void stopRadio() {
        radioOn = false;
        pause(bed);
        for (AudioTrack t : loops.values()) {
            pause(t);
        }
        playing = Audio.NONE;
    }

    /**
     * Mixes static and signal for what the dial hears now.
     *
     * @param strength 0 (static only) to 1 (tuned in)
     */
    void radioMix(String audio, double strength) {
        if (!radioOn || !enabled) {
            return;
        }
        pendingAudio = audio;
        pendingStrength = strength;
        if (bed == null) {
            return;
        }
        Audio kind = parse(audio);
        if (strength < 0.08) {
            kind = Audio.NONE;
        }
        if (kind != playing) {
            pause(loops.get(playing));
            playing = kind;
        }
        // Static never quite goes away: it's an old radio.
        float hiss = (float) (0.08 + 0.32 * (1 - strength));
        setVolume(bed, hiss);
        AudioTrack signal = loops.get(kind);
        if (signal != null) {
            float level = (float) (0.75 * Math.pow(strength, 1.3));
            // Off frequency, a voice comes in sped up and thin; on it, natural.
            float detune = (float) (1 + 0.22 * (1 - strength) * (1 - strength));
            loop(signal, level, detune);
        }
    }

    /** Switching band: a squelch burst with a whistle falling through the static. */
    void bandSwitch() {
        if (!enabled) {
            return;
        }
        if (sweep == null) {
            sweep = track(sweepBurst(new Random(11), 0.55));
        }
        play(sweep, 0.55f);
    }

    void release() {
        released = true;
        click.release();
        tick.release();
        beep.release();
        if (sweep != null) {
            sweep.release();
        }
        if (bed != null) {
            bed.release();
        }
        for (AudioTrack t : loops.values()) {
            t.release();
        }
    }

    private static Audio parse(String name) {
        if (name == null) {
            return Audio.NONE;
        }
        try {
            return Audio.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Audio.VOICE;
        }
    }

    /** Background thread: synthesize everything, then hand it to the main thread. */
    private void build() {
        Random random = new Random(1919);
        Map<Audio, AudioTrack> built = new EnumMap<>(Audio.class);
        AudioTrack staticLoop = loopTrack(staticBed(random, 3.0));
        built.put(Audio.VOICE, loopTrack(radio(voice(random, 4.5, VOICE_MAN), 1.0)));
        built.put(Audio.WHISPER, loopTrack(radio(voice(random, 4.0, VOICE_WHISPER), 0.9)));
        built.put(Audio.VOICE_F, loopTrack(radio(voice(random, 4.5, VOICE_WOMAN), 1.0)));
        built.put(Audio.CHILD, loopTrack(radio(voice(random, 4.0, VOICE_CHILD), 0.95)));
        built.put(Audio.PREACHER, loopTrack(radio(echo(voice(random, 5.0, VOICE_PREACHER), 0.23, 0.45), 1.0)));
        built.put(Audio.NUMBERS, loopTrack(radio(numbers(random, 5.0), 1.0)));
        built.put(Audio.MORSE, loopTrack(morse(random, 3.6)));
        built.put(Audio.DATA, loopTrack(radio(data(random, 3.0), 0.7)));
        built.put(Audio.MUSIC, loopTrack(radio(music(4.0), 0.9)));
        built.put(Audio.ORGAN, loopTrack(radio(organ(6.0), 0.9)));
        built.put(Audio.WALTZ, loopTrack(radio(musicBox(4.8), 0.85)));
        built.put(Audio.CHOIR, loopTrack(radio(echo(choir(random, 6.0), 0.31, 0.4), 0.9)));
        built.put(Audio.BREATH, loopTrack(radio(breath(random, 4.2), 1.0)));
        built.put(Audio.CARRIER, loopTrack(radio(carrier(3.0), 0.6)));
        built.put(Audio.PIANO, loopTrack(radio(score(PIANO_SCORE, 0.17, Instrument.PIANO, random), 0.9)));
        built.put(Audio.GUITAR, loopTrack(radio(score(GUITAR_SCORE, 0.21, Instrument.GUITAR, random), 0.9)));
        built.put(Audio.ACCORDION, loopTrack(radio(mix(score(ACCORDION_SCORE, 0.15, Instrument.ACCORDION, random),
                score(ACCORDION_BASS, 0.15, Instrument.PIANO, random), 0.55), 0.9)));
        main.post(() -> {
            if (released) {
                staticLoop.release();
                for (AudioTrack t : built.values()) {
                    t.release();
                }
                return;
            }
            loops = built;
            bed = staticLoop;
            if (radioOn && enabled) {
                loop(bed, 0.25f, 1f);
                if (pendingAudio != null) {
                    radioMix(pendingAudio, pendingStrength);
                }
            }
        });
    }

    // ---- Playback ----

    private void play(AudioTrack track, float volume) {
        if (!enabled || track == null || track.getState() != AudioTrack.STATE_INITIALIZED) {
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

    private static void loop(AudioTrack track, float volume, float rate) {
        if (track == null || track.getState() != AudioTrack.STATE_INITIALIZED) {
            return;
        }
        try {
            track.setVolume(volume);
            track.setPlaybackRate(Math.round(RATE * rate));
            if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                track.play();
            }
        } catch (IllegalStateException ignored) {
            // Never worth a crash.
        }
    }

    private static void setVolume(AudioTrack track, float volume) {
        if (track != null && track.getState() == AudioTrack.STATE_INITIALIZED) {
            track.setVolume(volume);
        }
    }

    private static void pause(AudioTrack track) {
        if (track == null || track.getState() != AudioTrack.STATE_INITIALIZED) {
            return;
        }
        try {
            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause();
            }
        } catch (IllegalStateException ignored) {
            // Never worth a crash.
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

    private static AudioTrack loopTrack(short[] pcm) {
        AudioTrack t = track(pcm);
        if (t.getState() == AudioTrack.STATE_INITIALIZED) {
            t.setLoopPoints(0, pcm.length, -1);
            t.setVolume(0f);
        }
        return t;
    }

    // ---- Synthesis ----

    /** RBJ biquad: enough filter for a radio's narrow, boxy sound. */
    private static final class Biquad {
        private double b0, b1, b2, a1, a2, x1, x2, y1, y2;

        static Biquad lowPass(double hz, double q) {
            return new Biquad().set(hz, q, 0);
        }

        static Biquad highPass(double hz, double q) {
            return new Biquad().set(hz, q, 1);
        }

        static Biquad bandPass(double hz, double q) {
            return new Biquad().set(hz, q, 2);
        }

        /** type: 0 low, 1 high, 2 band (constant 0 dB peak). */
        Biquad set(double hz, double q, int type) {
            double w = 2 * Math.PI * Math.min(hz, RATE * 0.45) / RATE;
            double cos = Math.cos(w);
            double alpha = Math.sin(w) / (2 * q);
            double a0 = 1 + alpha;
            switch (type) {
                case 0:
                    b0 = (1 - cos) / 2;
                    b1 = 1 - cos;
                    b2 = (1 - cos) / 2;
                    break;
                case 1:
                    b0 = (1 + cos) / 2;
                    b1 = -(1 + cos);
                    b2 = (1 + cos) / 2;
                    break;
                default:
                    b0 = alpha;
                    b1 = 0;
                    b2 = -alpha;
            }
            b0 /= a0;
            b1 /= a0;
            b2 /= a0;
            a1 = -2 * cos / a0;
            a2 = (1 - alpha) / a0;
            return this;
        }

        double run(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }

    /** Scales to 16 bits with a crossfade at the seam, so the loop never clicks. */
    private static short[] toPcm(double[] in, double gain, boolean loop) {
        double peak = 1e-9;
        for (double v : in) {
            peak = Math.max(peak, Math.abs(v));
        }
        int fade = loop ? Math.min(in.length / 4, RATE / 20) : 0;
        int n = in.length - fade;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double v = in[i];
            if (i < fade) {
                double k = (double) i / fade;
                v = v * k + in[n + i] * (1 - k);
            }
            out[i] = (short) Math.max(-32000, Math.min(32000, v / peak * gain * 26000));
        }
        return out;
    }

    /** Narrows anything to a radio: 300–3000 Hz, a little overdriven. */
    private static short[] radio(double[] in, double gain) {
        Biquad hp = Biquad.highPass(320, 0.7);
        Biquad hp2 = Biquad.highPass(320, 0.7);
        Biquad lp = Biquad.lowPass(2900, 0.7);
        Biquad lp2 = Biquad.lowPass(2900, 0.7);
        double peak = 1e-9;
        double[] out = new double[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = lp2.run(lp.run(hp2.run(hp.run(in[i]))));
            peak = Math.max(peak, Math.abs(out[i]));
        }
        for (int i = 0; i < out.length; i++) {
            out[i] = Math.tanh(2.2 * out[i] / peak);
        }
        return toPcm(out, gain, true);
    }

    /**
     * Shortwave static: band-limited noise with a slow fading flutter, a soft roar underneath
     * and the odd crackle.
     */
    private static short[] staticBed(Random random, double seconds) {
        int n = (int) (RATE * seconds);
        double[] out = new double[n];
        Biquad hp = Biquad.highPass(180, 0.7);
        Biquad lp = Biquad.lowPass(4200, 0.6);
        Biquad roar = Biquad.lowPass(500, 0.8);
        double crackle = 0;
        double fadePhase = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double white = random.nextGaussian() * 0.5;
            double hiss = lp.run(hp.run(white));
            double low = roar.run(random.nextGaussian()) * 1.4;
            if (random.nextDouble() < 0.0007) {
                crackle = (random.nextDouble() * 2 - 1) * (1.5 + random.nextDouble() * 2);
            }
            crackle *= 0.93;
            double t = (double) i / RATE;
            double flutter = 0.72 + 0.18 * Math.sin(2 * Math.PI * 0.6 * t + fadePhase)
                    + 0.1 * Math.sin(2 * Math.PI * 2.3 * t);
            out[i] = (hiss + low) * flutter + crackle;
        }
        return toPcm(out, 0.9, true);
    }

    /** Vowel formants (Hz): a, e, i, o, u. */
    private static final double[][] VOWELS = {
            {730, 1090, 2440}, {530, 1840, 2480}, {300, 2250, 3000}, {570, 840, 2410}, {330, 900, 2240}};

    /** Who's talking: pitch (Hz, 0 = whispered), formant scale, syllable speed. */
    private static final double[] VOICE_MAN = {98, 1.0, 1.0};
    private static final double[] VOICE_WHISPER = {0, 1.0, 1.0};
    private static final double[] VOICE_WOMAN = {185, 1.17, 1.05};
    private static final double[] VOICE_CHILD = {270, 1.32, 1.2};
    private static final double[] VOICE_PREACHER = {88, 0.95, 0.7};

    /**
     * Someone talking in a language that doesn't exist: syllables of formant-filtered voice with
     * a falling pitch per phrase and pauses between phrases. Whispered: noise instead of pitch.
     *
     * @param who one of the VOICE_ constants
     */
    private static double[] voice(Random random, double seconds, double[] who) {
        boolean whisper = who[0] == 0;
        double formantScale = who[1];
        double speed = who[2];
        int n = (int) (RATE * seconds);
        double[] out = new double[n];
        Biquad[] formants = {Biquad.bandPass(700, 6), Biquad.bandPass(1200, 8), Biquad.bandPass(2500, 10)};
        double[] gains = {1.0, 0.55, 0.25};
        Biquad fricative = Biquad.bandPass(4000, 2);
        double phase = 0;
        double prevGlottal = 0;
        double[] from = VOWELS[0];
        double[] to = VOWELS[0];
        int i = (int) (RATE * 0.15);
        while (i < n) {
            // A phrase of 3–7 syllables, then a breath.
            int syllables = 3 + random.nextInt(5);
            double base = whisper ? 0 : who[0] * (1 + random.nextDouble() * 0.2);
            for (int s = 0; s < syllables && i < n; s++) {
                from = to;
                to = VOWELS[random.nextInt(VOWELS.length)];
                int length = (int) (RATE * (0.11 + random.nextDouble() * 0.15) / speed);
                int consonant = random.nextDouble() < 0.6 ? (int) (RATE * (0.02 + random.nextDouble() * 0.04)) : 0;
                boolean plosive = random.nextBoolean();
                double pitch = base * (1.12 - 0.22 * s / syllables) * (0.94 + random.nextDouble() * 0.12);
                for (int k = 0; k < length + consonant && i < n; k++, i++) {
                    if (k < consonant) {
                        double env = plosive ? Math.exp(-k / (RATE * 0.006)) : 0.6;
                        out[i] = fricative.run(random.nextGaussian()) * env * 0.5;
                        continue;
                    }
                    int v = k - consonant;
                    if (v % 64 == 0) {
                        double mix = Math.min(1, v / (length * 0.35));
                        for (int f = 0; f < 3; f++) {
                            formants[f].set((from[f] + (to[f] - from[f]) * mix) * formantScale, 6 + 2 * f, 2);
                        }
                    }
                    double source;
                    if (whisper) {
                        source = random.nextGaussian() * 0.6;
                    } else {
                        double hz = pitch * (1 + 0.01 * random.nextGaussian());
                        phase += hz / RATE;
                        if (phase >= 1) {
                            phase -= 1;
                        }
                        double g = phase < 0.4 ? 0.5 * (1 - Math.cos(Math.PI * phase / 0.4))
                                : phase < 0.56 ? Math.cos(Math.PI * (phase - 0.4) / 0.32) : 0;
                        source = (g - prevGlottal) * 8 + random.nextGaussian() * 0.02;
                        prevGlottal = g;
                    }
                    double y = 0;
                    for (int f = 0; f < 3; f++) {
                        y += formants[f].run(source) * gains[f];
                    }
                    double env = Math.min(1, v / (RATE * 0.02)) * Math.min(1, (length - v) / (RATE * 0.04));
                    out[i] = y * env;
                }
            }
            i += (int) (RATE * (0.18 + random.nextDouble() * 0.35) / speed);
        }
        return out;
    }

    /** A big empty room: a few decaying repeats. */
    private static double[] echo(double[] in, double delaySeconds, double feedback) {
        int delay = (int) (RATE * delaySeconds);
        double[] out = in.clone();
        for (int i = delay; i < out.length; i++) {
            out[i] += out[i - delay] * feedback;
        }
        // Wrap the tail onto the start so the loop doesn't cut the echo off.
        for (int i = 0; i < delay && i < out.length; i++) {
            out[i] += out[out.length - delay + i] * feedback * 0.5;
        }
        return out;
    }

    /** A flat woman's voice reading digit groups, a pause, and a chime between groups. */
    private static double[] numbers(Random random, double seconds) {
        int n = (int) (RATE * seconds);
        double[] out = new double[n];
        // Short syllables, one per digit, then a long gap.
        double[] talk = voice(random, seconds, new double[]{175, 1.15, 1.6});
        int group = (int) (RATE * 1.4);
        int gap = (int) (RATE * 1.0);
        for (int i = 0; i < n; i++) {
            int inCycle = i % (group + gap);
            if (inCycle < group) {
                out[i] = talk[i];
            } else if (inCycle < group + RATE * 0.6) {
                // The chime: two bell tones.
                int k = inCycle - group;
                double t = (double) k / RATE;
                out[i] = (Math.sin(2 * Math.PI * 1320 * t) + 0.6 * Math.sin(2 * Math.PI * 990 * t))
                        * Math.exp(-t * 6) * 0.5;
            }
        }
        return out;
    }

    /** Voices holding an "aah", four parts, slow vibrato, changing chord. */
    private static double[] choir(Random random, double seconds) {
        int n = (int) (RATE * seconds);
        double[] out = new double[n];
        double[][] chords = {{130.8, 196.0, 261.6, 329.6}, {110.0, 164.8, 261.6, 329.6}, {123.5, 185.0, 246.9, 311.1}};
        int per = n / chords.length;
        Biquad[] f = {Biquad.bandPass(730, 5), Biquad.bandPass(1090, 7), Biquad.bandPass(2440, 9)};
        double[] phase = new double[4];
        for (int i = 0; i < n; i++) {
            double[] chord = chords[Math.min(chords.length - 1, i / per)];
            int k = i % per;
            double swell = Math.min(1, k / (RATE * 0.5)) * Math.min(1, (per - k) / (RATE * 0.5));
            double t = (double) i / RATE;
            double src = 0;
            for (int v = 0; v < 4; v++) {
                double hz = chord[v] * (1 + 0.006 * Math.sin(2 * Math.PI * (4.8 + v * 0.3) * t));
                phase[v] += hz / RATE;
                src += saw(phase[v]);
            }
            src += random.nextGaussian() * 0.15;
            out[i] = (f[0].run(src) + f[1].run(src) * 0.5 + f[2].run(src) * 0.2) * swell;
        }
        return out;
    }

    /** A slow hymn: organ pipes (a few sine harmonics), four chords. */
    private static double[] organ(double seconds) {
        int n = (int) (RATE * seconds);
        double[] out = new double[n];
        double[][] chords = {{130.8, 164.8, 196.0, 261.6}, {116.5, 146.8, 174.6, 233.1},
                {110.0, 130.8, 164.8, 220.0}, {98.0, 123.5, 146.8, 196.0}};
        int per = n / chords.length;
        for (int i = 0; i < n; i++) {
            double[] chord = chords[Math.min(chords.length - 1, i / per)];
            int k = i % per;
            double env = Math.min(1, k / (RATE * 0.08)) * Math.min(1, (per - k) / (RATE * 0.12));
            double t = (double) i / RATE;
            double y = 0;
            for (double hz : chord) {
                y += Math.sin(2 * Math.PI * hz * t) + 0.5 * Math.sin(4 * Math.PI * hz * t)
                        + 0.25 * Math.sin(6 * Math.PI * hz * t);
            }
            // A slow tremulant, like an old church organ.
            out[i] = y * env * (0.85 + 0.15 * Math.sin(2 * Math.PI * 5.5 * t));
        }
        return out;
    }

    // ---- Scored music: public-domain tunes and one of our own ----

    private enum Instrument { PIANO, GUITAR, ACCORDION }

    /** Für Elise (Beethoven, 1810), opening: notes and chords, length in sixteenths. */
    private static final String PIANO_SCORE = "E5/1 D#5/1 E5/1 D#5/1 E5/1 B4/1 D5/1 C5/1 A4+A2+E3/3 C4/1 E4/1 A4/1 "
            + "B4+E2+G#3/3 E4/1 G#4/1 B4/1 C5+A2+E3/3 E4/1 E5/1 D#5/1 E5/1 D#5/1 E5/1 B4/1 D5/1 C5/1 "
            + "A4+A2+E3/3 C4/1 E4/1 A4/1 B4+E2+G#3/3 E4/1 C5/1 B4/1 A4+A2+E3/6";
    /** Greensleeves (traditional), in eighths. */
    private static final String GUITAR_SCORE = "A4+A2/2 C5/4 D5+F2/2 E5/3 F5/1 E5/2 D5+G2/4 B4/2 G4/3 A4/1 B4/2 "
            + "C5+A2/4 A4/2 A4/3 G#4/1 A4/2 B4+E2/4 G#4/2 E4+E2/4 R/2";
    /** A slow tango in D minor, written for the game. Sixteenths. */
    private static final String ACCORDION_SCORE = "D5/3 C#5/1 D5/2 F5/2 E5/3 D5/1 C#5/4 A4/3 A#4/1 C#5/2 E5/2 "
            + "D5/4 R/2 A4/2 F5/3 E5/1 D5/2 C5/2 A#4/3 A4/1 G4/4 A4/3 C#5/1 E5/2 G5/2 F5/6 R/2";
    /** Its habanera bass: long, short, two even. */
    private static final String ACCORDION_BASS = "D3/3 D3/1 A2/2 F3/2 D3/3 D3/1 A2/2 E3/2 A2/3 A2/1 E3/2 C#3/2 "
            + "D3/3 D3/1 A2/2 F3/2 D3/3 D3/1 A2/2 F3/2 G2/3 G2/1 D3/2 A#2/2 A2/3 A2/1 E3/2 C#3/2 D3/3 D3/1 A2/4";

    /** "C#5" → Hz (A4 = 440). */
    private static double noteHz(String note) {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        String name = note.substring(0, note.length() - 1);
        int octave = note.charAt(note.length() - 1) - '0';
        int index = java.util.Arrays.asList(names).indexOf(name);
        int midi = (octave + 1) * 12 + index;
        return 440.0 * Math.pow(2, (midi - 69) / 12.0);
    }

    /**
     * Plays a score: space-separated notes or chords ("A4+A2+E3") with their length in units
     * ("/3"), "R" for a rest. Each note rings out in the instrument's own way.
     */
    private static double[] score(String score, double unitSeconds, Instrument instrument, Random random) {
        String[] tokens = score.trim().split("\\s+");
        int units = 0;
        for (String t : tokens) {
            units += Integer.parseInt(t.substring(t.indexOf('/') + 1));
        }
        int n = (int) (RATE * unitSeconds * units);
        double[] out = new double[n];
        int at = 0;
        for (String t : tokens) {
            int length = (int) (RATE * unitSeconds * Integer.parseInt(t.substring(t.indexOf('/') + 1)));
            String notes = t.substring(0, t.indexOf('/'));
            if (!"R".equals(notes)) {
                String[] chord = notes.split("\\+");
                for (int c = 0; c < chord.length; c++) {
                    // The melody on top is louder than what's under it.
                    note(out, at, length, noteHz(chord[c]), c == 0 ? 1.0 : 0.55, instrument, random);
                }
            }
            at += length;
        }
        return out;
    }

    private static void note(double[] out, int at, int length, double hz, double gain, Instrument instrument,
                             Random random) {
        switch (instrument) {
            case GUITAR: {
                // Karplus-Strong: a burst of noise in a loop that dulls as it rings.
                int period = Math.max(2, (int) Math.round(RATE / hz));
                double[] ring = new double[period];
                for (int i = 0; i < period; i++) {
                    ring[i] = random.nextDouble() * 2 - 1;
                }
                int ringFor = Math.min(out.length - at, length + RATE / 2);
                for (int i = 0; i < ringFor; i++) {
                    int k = i % period;
                    double v = ring[k];
                    ring[k] = 0.996 * 0.5 * (v + ring[(k + 1) % period]);
                    out[at + i] += v * gain * 0.8;
                }
                break;
            }
            case ACCORDION: {
                // Two reeds a hair apart, odd harmonics, a little bellows swell.
                for (int i = 0; i < length && at + i < out.length; i++) {
                    double t = (double) i / RATE;
                    double env = Math.min(1, t / 0.04) * Math.min(1, (length - i) / (RATE * 0.05));
                    double vib = 1 + 0.004 * Math.sin(2 * Math.PI * 5 * t);
                    double y = 0;
                    for (int h = 1; h <= 7; h += 2) {
                        y += (Math.sin(2 * Math.PI * hz * h * vib * t) + Math.sin(2 * Math.PI * hz * 1.004 * h * t)) / h;
                    }
                    out[at + i] += y * 0.35 * env * gain;
                }
                break;
            }
            default: {
                // An upright piano: bright attack, harmonics fading faster than the root, a touch out of tune.
                int ringFor = Math.min(out.length - at, length + RATE);
                for (int i = 0; i < ringFor; i++) {
                    double t = (double) i / RATE;
                    double y = Math.sin(2 * Math.PI * hz * t) * Math.exp(-t * 2.2)
                            + 0.5 * Math.sin(2 * Math.PI * hz * 2.003 * t) * Math.exp(-t * 3.5)
                            + 0.25 * Math.sin(2 * Math.PI * hz * 3.01 * t) * Math.exp(-t * 5);
                    out[at + i] += y * Math.min(1, t / 0.004) * gain * 0.7;
                }
                break;
            }
        }
    }

    /** Two parts together, the second at {@code gain}. */
    private static double[] mix(double[] a, double[] b, double gain) {
        double[] out = new double[Math.max(a.length, b.length)];
        for (int i = 0; i < out.length; i++) {
            out[i] = (i < a.length ? a[i] : 0) + (i < b.length ? b[i] * gain : 0);
        }
        return out;
    }

    /** A music box playing a waltz in A minor: bell-like plucks, 3/4. */
    private static double[] musicBox(double seconds) {
        int n = (int) (RATE * seconds);
        double[] out = new double[n];
        double[] melody = {880, 1046.5, 1318.5, 1174.7, 1046.5, 987.8, 880, 659.3, 783.99, 880, 987.8, 1046.5};
        int step = n / melody.length;
        for (int s = 0; s < melody.length; s++) {
            double hz = melody[s];
            for (int k = 0; k < step * 3 && s * step + k < n; k++) {
                double t = (double) k / RATE;
                double v = (Math.sin(2 * Math.PI * hz * t) + 0.4 * Math.sin(2 * Math.PI * hz * 2.76 * t))
                        * Math.exp(-t * 3.2);
                out[s * step + k] += v * (s % 3 == 0 ? 1.0 : 0.7);
            }
            if (s % 3 == 0) {
                // The bass note on the first beat.
                double bass = melody[s] / 4;
                for (int k = 0; k < step * 2 && s * step + k < n; k++) {
                    double t = (double) k / RATE;
                    out[s * step + k] += Math.sin(2 * Math.PI * bass * t) * Math.exp(-t * 2.5) * 0.6;
                }
            }
        }
        return out;
    }

    /** A keyed 700 Hz tone sending random letters at about 15 words a minute. */
    private static short[] morse(Random random, double seconds) {
        int n = (int) (RATE * seconds);
        double[] out = new double[n];
        int unit = (int) (RATE * 0.075);
        String[] codes = {".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-", ".-..", "--",
                "-.", "---", ".--.", "--.-", ".-.", "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--.."};
        int i = unit * 2;
        while (i < n - unit * 8) {
            String code = codes[random.nextInt(codes.length)];
            for (char c : code.toCharArray()) {
                int length = (c == '.' ? 1 : 3) * unit;
                for (int k = 0; k < length && i < n; k++, i++) {
                    double env = Math.min(1, k / (RATE * 0.004)) * Math.min(1, (length - k) / (RATE * 0.004));
                    out[i] = Math.sin(2 * Math.PI * 700 * i / RATE) * env;
                }
                i += unit;
            }
            i += unit * (random.nextDouble() < 0.25 ? 6 : 2);
        }
        Biquad noise = Biquad.bandPass(700, 3);
        for (int k = 0; k < n; k++) {
            out[k] += noise.run(random.nextGaussian()) * 0.08;
        }
        return toPcm(out, 0.8, true);
    }

    /** Teletype warble: two tones swapping 45 times a second. */
    private static double[] data(Random random, double seconds) {
        int n = (int) (RATE * seconds);
        double[] out = new double[n];
        int baud = RATE / 45;
        double phase = 0;
        boolean mark = true;
        for (int i = 0; i < n; i++) {
            if (i % baud == 0) {
                mark = random.nextBoolean();
            }
            phase += 2 * Math.PI * (mark ? 1585 : 1415) / RATE;
            out[i] = Math.sin(phase);
        }
        return out;
    }

    /** A bandoneon on a scratchy record: reeds a little out of tune with each other, swelling. */
    private static double[] music(double seconds) {
        int n = (int) (RATE * seconds);
        double[] out = new double[n];
        // Am, Dm, E7, Am: a tango cadence.
        double[][] chords = {{220, 261.6, 329.6}, {293.7, 349.2, 440}, {246.9, 329.6, 415.3}, {220, 261.6, 329.6}};
        int per = n / chords.length;
        Biquad lp = Biquad.lowPass(1800, 0.7);
        Random hiss = new Random(3);
        for (int i = 0; i < n; i++) {
            double[] chord = chords[Math.min(chords.length - 1, i / per)];
            int k = i % per;
            double bellows = Math.sin(Math.PI * k / per) * 0.8 + 0.2;
            double t = (double) i / RATE;
            double y = 0;
            for (double hz : chord) {
                y += saw(hz * t) + saw(hz * 1.004 * t) * 0.8;
            }
            y += saw(chord[0] / 2 * t) * 0.6;
            double crackle = hiss.nextDouble() < 0.0015 ? hiss.nextGaussian() * 3 : 0;
            out[i] = lp.run(y) * bellows + crackle;
        }
        return out;
    }

    private static double saw(double cycles) {
        return 2 * (cycles - Math.floor(cycles)) - 1;
    }

    /** Someone breathing too close to a microphone. */
    private static double[] breath(Random random, double seconds) {
        int n = (int) (RATE * seconds);
        double[] out = new double[n];
        Biquad body = Biquad.bandPass(650, 1.2);
        Biquad air = Biquad.bandPass(1800, 1.5);
        double[] phases = {0, 0.32, 0.42, 0.9, 1.0}; // in, pause, out (longer), pause
        for (int i = 0; i < n; i++) {
            double p = (double) i / n;
            double env = 0;
            if (p < phases[1]) {
                env = Math.sin(Math.PI * p / phases[1]) * 0.8;
            } else if (p >= phases[2] && p < phases[3]) {
                env = Math.sin(Math.PI * (p - phases[2]) / (phases[3] - phases[2]));
            }
            double w = random.nextGaussian();
            out[i] = (body.run(w) + air.run(w) * 0.6) * env * env;
        }
        return out;
    }

    /** An open carrier: mains hum and a whistle that drifts. */
    private static double[] carrier(double seconds) {
        int n = (int) (RATE * seconds);
        double[] out = new double[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            // The drift must come back to its start so the loop closes.
            double hz = 900 + 18 * Math.sin(2 * Math.PI * t / seconds);
            phase += 2 * Math.PI * hz / RATE;
            out[i] = Math.sin(phase) * 0.5 + Math.sin(2 * Math.PI * 100 * t) * 0.35
                    + Math.sin(2 * Math.PI * 150 * t) * 0.15;
        }
        return out;
    }

    /** Band switch: a burst of static, its color falling, with a heterodyne whistle under it. */
    private static short[] sweepBurst(Random random, double seconds) {
        int n = (int) (RATE * seconds);
        double[] out = new double[n];
        Biquad band = Biquad.bandPass(3000, 1.5);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double p = (double) i / n;
            if (i % 32 == 0) {
                band.set(3000 * Math.pow(400 / 3000.0, p), 1.5, 2);
            }
            double hz = 2600 * Math.pow(180 / 2600.0, p);
            phase += 2 * Math.PI * hz / RATE;
            double env = Math.min(1, i / (RATE * 0.01)) * Math.pow(1 - p, 1.5);
            out[i] = (band.run(random.nextGaussian()) * 1.6 + Math.sin(phase) * 0.35) * env;
        }
        return toPcm(out, 0.9, false);
    }

    /** White noise with a short fade-out, roughly low-passed. */
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
