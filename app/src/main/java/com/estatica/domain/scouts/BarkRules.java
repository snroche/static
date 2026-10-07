package com.estatica.domain.scouts;

import com.estatica.core.GameRandom;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.VoiceDef;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ambient chatter in the bunker: now and then someone says something in their own voice,
 * colored by their mood, their task and who they get along with. Makes the bunker feel
 * inhabited between expeditions.
 */
public final class BarkRules {

    private final GameContent content;
    private final ScoutRules rules;

    public BarkRules(GameContent content, ScoutRules rules) {
        this.content = content;
        this.rules = rules;
    }

    /** One line of chatter from someone in the bunker, or null if nobody is home. */
    public LogLine bark(Collection<Scout> scouts, Affinities affinities, GameRandom rng, long now) {
        return bark(scouts, affinities, rng, now, false);
    }

    /** @param hungry the stores are empty: food is all anyone talks about */
    public LogLine bark(Collection<Scout> scouts, Affinities affinities, GameRandom rng, long now, boolean hungry) {
        List<Scout> home = new ArrayList<>();
        for (Scout s : scouts) {
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE) {
                home.add(s);
            }
        }
        if (home.isEmpty()) {
            return null;
        }
        Balance.Affinity thresholds = content.getBalance().getAffinity();
        Scout s = rng.pick(home);
        Scout friend = null;
        Scout rival = null;
        for (Scout other : scouts) {
            if (other == s || !other.isAlive()) {
                continue;
            }
            double closeness = affinities.get(s.getId(), other.getId());
            if (closeness >= thresholds.getFriendAt() && friend == null) {
                friend = other;
            } else if (closeness <= thresholds.getRivalAt() && rival == null) {
                rival = other;
            }
        }

        VoiceDef voice = content.voice(s.getVoiceId());
        Map<String, List<String>> phrases = voice.getPhrases();
        Map<String, String> vars = new HashMap<>();
        vars.put("name", s.getName());
        String key;
        String task = s.getLife().getTaskId();
        if (hungry && phrases.containsKey("hungry") && rng.chance(0.7)) {
            key = "hungry";
        } else if (rival != null && rng.chance(0.5)) {
            key = "aboutRival";
            vars.put("other", rival.getName());
        } else if (friend != null && rng.chance(0.4)) {
            key = "aboutFriend";
            vars.put("other", friend.getName());
        } else if (s.getStress() >= 45) {
            key = "idleTense";
        } else if (task != null && phrases.containsKey("task:" + task) && rng.chance(0.5)) {
            key = "task:" + task;
        } else {
            key = "idle";
        }
        String text = VoiceRenderer.phrase(voice, key, rng, vars, rules.isClose(s));
        return LogLine.draft(now, LogChannel.RADIO, s.getName(), text, s.getId());
    }
}
