package com.estatica.domain.scouts;

import com.estatica.core.GameRandom;
import com.estatica.core.TextTemplate;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.VignetteDef;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Life goes on in the bunker without the operator: two people at home share a meal, argue,
 * sing. Picks a fitting moment for a pair, writes it, and nudges their stress and affinity.
 */
public final class VignetteRules {

    /** What happened: the lines and who was in it. */
    public static final class Moment {
        public final VignetteDef vignette;
        public final Scout a;
        public final Scout b;
        public final List<LogLine> lines;

        Moment(VignetteDef vignette, Scout a, Scout b, List<LogLine> lines) {
            this.vignette = vignette;
            this.a = a;
            this.b = b;
            this.lines = lines;
        }
    }

    private final GameContent content;
    private final ScoutRules rules;

    public VignetteRules(GameContent content, ScoutRules rules) {
        this.content = content;
        this.rules = rules;
    }

    /**
     * A moment between two people at home, or null if nothing fits.
     *
     * @param recent vignette ids that happened lately (not repeated)
     */
    public Moment pick(Collection<Scout> scouts, Affinities affinities, boolean night, Set<String> recent,
                       GameRandom rng, long now) {
        List<Scout> home = new ArrayList<>();
        for (Scout s : scouts) {
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE) {
                home.add(s);
            }
        }
        if (home.size() < 2) {
            return null;
        }
        Balance.Affinity thresholds = content.getBalance().getAffinity();
        List<VignetteDef> options = new ArrayList<>();
        List<Scout[]> pairs = new ArrayList<>();
        for (VignetteDef v : content.getVignettes()) {
            if (recent.contains(v.getId()) || (v.isNight() && !night)) {
                continue;
            }
            List<Scout[]> fitting = new ArrayList<>();
            for (Scout a : home) {
                if (!rules.fits(a, v.getRequiresTrait(), null)) {
                    continue;
                }
                for (Scout b : home) {
                    if (a == b) {
                        continue;
                    }
                    double closeness = affinities.get(a.getId(), b.getId());
                    boolean ok = v.getRequires() == null
                            || ("friends".equals(v.getRequires()) && closeness >= thresholds.getFriendAt())
                            || ("rivals".equals(v.getRequires()) && closeness <= thresholds.getRivalAt());
                    if (ok) {
                        fitting.add(new Scout[]{a, b});
                    }
                }
            }
            if (!fitting.isEmpty()) {
                for (int w = 0; w < v.getWeight(); w++) {
                    options.add(v);
                    pairs.add(fitting.get(rng.nextInt(fitting.size())));
                }
            }
        }
        if (options.isEmpty()) {
            return null;
        }
        int i = rng.nextInt(options.size());
        VignetteDef v = options.get(i);
        Scout a = pairs.get(i)[0];
        Scout b = pairs.get(i)[1];

        Map<String, String> vars = new HashMap<>();
        vars.put("a", a.getName());
        vars.put("b", b.getName());
        List<LogLine> lines = new ArrayList<>();
        for (VignetteDef.Line line : v.getLines()) {
            Scout speaker = "a".equals(line.getWho()) ? a : "b".equals(line.getWho()) ? b : null;
            if (speaker == null) {
                lines.add(LogLine.draft(now, LogChannel.SYSTEM, null, TextTemplate.format(line.getText(), vars)));
            } else {
                Map<String, String> own = new HashMap<>(vars);
                own.put("name", speaker.getName());
                String text = VoiceRenderer.render(content.voice(speaker.getVoiceId()), line.getText(), own);
                lines.add(LogLine.draft(now, LogChannel.RADIO, speaker.getName(), text, speaker.getId()));
            }
        }
        if (v.getStress() != 0) {
            rules.addStress(a, v.getStress());
            rules.addStress(b, v.getStress());
        }
        if (v.getAffinity() != 0) {
            affinities.add(a.getId(), b.getId(), v.getAffinity());
        }
        return new Moment(v, a, b, lines);
    }
}
