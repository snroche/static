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

    public enum Reaction {
        /** The one thing they'd love most. */
        FAVORITE,
        LIKED,
        NEUTRAL,
        DISLIKED;

        /** Liked at least: lifts them, and close friends pay it back. */
        public boolean loved() {
            return this == FAVORITE || this == LIKED;
        }
    }

    /** Flag once their favorite has been found out (by talking, or by giving it). */
    public static final String FAVORITE_KNOWN = "fav_known";

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
        ItemDef favorite = content.favoriteOf(scout);
        if (favorite != null && favorite.getId().equals(item.getId())) {
            return Reaction.FAVORITE;
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
            case FAVORITE:
                key = "giftLiked";
                delta = bond.getGiftFavorite();
                scout.getLife().addFlag(LIKES + item.getId());
                scout.getLife().addFlag(FAVORITE_KNOWN);
                break;
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
        com.estatica.domain.content.VoiceDef voice = content.voice(scout.getVoiceId());
        LogLine said = LogLine.draft(now, LogChannel.RADIO, scout.getName(),
                VoiceRenderer.phrase(voice, key, rng, vars, close), scout.getId());
        out.lines.add(reaction.loved() ? said.withCue(LogLine.Cue.WARM) : said);
        com.estatica.domain.content.TerminalText text = content.getTerminal();
        if (reaction == Reaction.FAVORITE) {
            // Not just something they like: the thing. They say so, in their own words.
            List<String> pool = text.momentLines("favorite");
            if (!pool.isEmpty()) {
                out.lines.add(LogLine.draft(now, LogChannel.RADIO, scout.getName(),
                        VoiceRenderer.render(voice, rng.pick(pool), vars), scout.getId()));
            }
        }
        DialogueOutcomes.applyBond(content, rules, scout, delta, now, out.lines);
        if (reaction.loved()) {
            int relief = reaction == Reaction.FAVORITE ? bond.getFavoriteStressRelief() : bond.getLikedStressRelief();
            int eased = -rules.addStress(scout, -relief);
            boolean lifted = scout.getLife().addFlag(ScoutRules.LIFTED);
            vars.put("n", String.valueOf(eased));
            String template = text.moment(lifted ? "giftLift" : "giftEase");
            if (!template.isEmpty()) {
                out.lines.add(LogLine.draft(now, LogChannel.SYSTEM, null, TextTemplate.format(template, vars), scout.getId()));
            }
        }
        if (reaction != Reaction.DISLIKED) {
            out.memories.add(Memory.create(scout.getId(), Memory.Type.GIFT,
                    reaction == Reaction.FAVORITE
                            ? TextTemplate.format(text.moment("favoriteMemory"), vars)
                            : item.getName().toLowerCase(java.util.Locale.ROOT), item.getId(), now));
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
