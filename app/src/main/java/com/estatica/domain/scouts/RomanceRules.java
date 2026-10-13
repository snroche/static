package com.estatica.domain.scouts;

import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ScoutTemplate;

import java.util.Map;

/**
 * Romance between the operator and the crew. Pure rules: who is open to it, how jealous
 * they get, when flirting can start. Everyone adult can be won over in the long run; some
 * just take a lot longer. The meter and timestamps live in the session's value store; being
 * together is a scout flag ({@link #FLAG}) so voices and orders can read it.
 */
public final class RomanceRules {

    /** Scout flag while together with the operator. */
    public static final String FLAG = "romance";
    /** Scout flag once a romance with the operator has ended (rekindling dialogues). */
    public static final String EX_FLAG = "romance:ex";

    private static final Map<String, Integer> OPENNESS = Map.ofEntries(
            Map.entry("sociable", 20), Map.entry("optimista", 15), Map.entry("imprudencia", 10),
            Map.entry("gentil", 10), Map.entry("calentona", 5), Map.entry("curiosa", 5),
            Map.entry("solitaria", -25), Map.entry("desconfianza", -20), Map.entry("sangre_fria", -10),
            Map.entry("metodo", -10), Map.entry("terca", -5), Map.entry("miedo", -5));
    private static final Map<String, Integer> JEALOUSY = Map.ofEntries(
            Map.entry("calentona", 25), Map.entry("lealtad", 20), Map.entry("desconfianza", 20),
            Map.entry("miedo", 10), Map.entry("terca", 10),
            Map.entry("sociable", -20), Map.entry("optimista", -15), Map.entry("sangre_fria", -15),
            Map.entry("solitaria", -10), Map.entry("imprudencia", -5));

    private final GameContent content;
    private final ScoutRules rules;

    public RomanceRules(GameContent content, ScoutRules rules) {
        this.content = content;
        this.rules = rules;
    }

    /** Never with minors: the teenager background is out, whatever else happens. */
    public boolean isAdult(Scout s) {
        ScoutTemplate persona = content.persona(s);
        return persona == null || !"teen".equals(persona.getBackground());
    }

    /** 0–100: how easily they open up to it. */
    public int openness(Scout s) {
        int value = 35 + Math.floorMod((s.getId() + "open").hashCode(), 30);
        for (ScoutTrait t : s.getTraits()) {
            value += OPENNESS.getOrDefault(t.getTraitId(), 0);
        }
        return clamp(value);
    }

    /** 0–100: how badly they take sharing you. */
    public int jealousy(Scout s) {
        int value = 25 + Math.floorMod((s.getId() + "jealous").hashCode(), 35);
        for (ScoutTrait t : s.getTraits()) {
            value += JEALOUSY.getOrDefault(t.getTraitId(), 0);
        }
        return clamp(value);
    }

    /** Bond level at which flirting becomes possible: sooner for the open ones. */
    public int readyAtBondLevel(Scout s) {
        int open = openness(s);
        return open >= 60 ? 2 : open >= 35 ? 3 : 4;
    }

    public boolean isReady(Scout s) {
        return isAdult(s) && s.isAlive() && rules.bondLevel(s) >= readyAtBondLevel(s);
    }

    /** Romance gained per point offered: open people warm up faster. */
    public double gainMultiplier(Scout s) {
        return Math.max(0.4, openness(s) / 50.0);
    }

    public boolean isTogether(Scout s) {
        return s.getLife().hasFlag(FLAG);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
