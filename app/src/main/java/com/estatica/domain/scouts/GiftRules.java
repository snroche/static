package com.estatica.domain.scouts;

import com.estatica.core.GameRandom;
import com.estatica.core.TextTemplate;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ItemDef;
import com.estatica.domain.content.ScoutTemplate;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gifts (GDD §5.3): the right one raises bond a lot, the wrong one can lower it. Tastes
 * are hidden until discovered by talking or by giving something.
 */
public final class GiftRules {

    public enum Reaction { LIKED, NEUTRAL, DISLIKED }

    /** Flag prefixes for discovered tastes, shown on the scout card. */
    public static final String LIKES = "likes:";
    public static final String DISLIKES = "dislikes:";

    private final GameContent content;
    private final ScoutRules rules;

    public GiftRules(GameContent content, ScoutRules rules) {
        this.content = content;
        this.rules = rules;
    }

    public Reaction reactionTo(Scout scout, ItemDef item) {
        ScoutTemplate template = content.persona(scout);
        if (template == null) {
            return Reaction.NEUTRAL;
        }
        if (matches(template.getLikes(), item)) {
            return Reaction.LIKED;
        }
        return matches(template.getDislikes(), item) ? Reaction.DISLIKED : Reaction.NEUTRAL;
    }

    public static final class Result {
        public final Reaction reaction;
        public final List<LogLine> lines = new ArrayList<>();
        public final List<Memory> memories = new ArrayList<>();

        Result(Reaction reaction) {
            this.reaction = reaction;
        }
    }

    public Result give(Scout scout, ItemDef item, GameRandom rng, long now) {
        Reaction reaction = reactionTo(scout, item);
        Result out = new Result(reaction);
        Balance.Bond bond = content.getBalance().getBond();
        Map<String, String> vars = new HashMap<>();
        vars.put("name", scout.getName());
        vars.put("item", item.getName().toLowerCase(java.util.Locale.ROOT));

        out.lines.add(LogLine.draft(now, LogChannel.SYSTEM, null,
                TextTemplate.format(content.getTerminal().getBunker().getGiftGiven(), vars), scout.getId()));
        String key;
        int delta;
        switch (reaction) {
            case LIKED:
                key = "giftLiked";
                delta = bond.getGiftLiked();
                scout.getLife().addFlag(LIKES + item.getId());
                break;
            case DISLIKED:
                key = "giftDisliked";
                delta = -bond.getGiftDisliked();
                scout.getLife().addFlag(DISLIKES + item.getId());
                break;
            default:
                key = "giftNeutral";
                delta = bond.getGiftNeutral();
        }
        boolean close = rules.isClose(scout);
        out.lines.add(LogLine.draft(now, LogChannel.RADIO, scout.getName(),
                VoiceRenderer.phrase(content.voice(scout.getVoiceId()), key, rng, vars, close), scout.getId()));
        DialogueOutcomes.applyBond(content, rules, scout, delta, now, out.lines);
        if (reaction != Reaction.DISLIKED) {
            out.memories.add(Memory.create(scout.getId(), Memory.Type.GIFT,
                    item.getName().toLowerCase(java.util.Locale.ROOT), item.getId(), now));
        }
        return out;
    }

    private static boolean matches(List<String> tastes, ItemDef item) {
        for (String taste : tastes) {
            if (taste.equals(item.getId()) || item.getTags().contains(taste)) {
                return true;
            }
        }
        return false;
    }
}
