package com.estatica.domain.bunker;

import com.estatica.core.GameRandom;
import com.estatica.domain.content.EnergyDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ResourceDef;
import com.estatica.domain.content.TaskDef;
import com.estatica.domain.scouts.Injury;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutRules;
import com.estatica.domain.scouts.ScoutStatus;
import com.estatica.domain.scouts.StatType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raids on the bunker (GDD §5.3 Guardia). Guards defend with Aguante and Nervio; a lost
 * raid costs resources and hurts someone. Raids never kill: a death needs an order from
 * the operator (GDD §5.5).
 */
public final class RaidRules {

    private static final int GUARD_XP = 3;

    private final GameContent content;
    private final ScoutRules rules;

    public RaidRules(GameContent content, ScoutRules rules) {
        this.content = content;
        this.rules = rules;
    }

    public static final class Result {
        public final boolean repelled;
        public final boolean unguarded;
        public final List<Scout> guards;
        /** Resource id to amount taken (positive). */
        public final Map<String, Integer> stolen;
        /** Who got hurt, or null. */
        public final Scout injured;
        public final int attack;
        public final int defense;

        Result(boolean repelled, boolean unguarded, List<Scout> guards, Map<String, Integer> stolen,
               Scout injured, int attack, int defense) {
            this.repelled = repelled;
            this.unguarded = unguarded;
            this.guards = guards;
            this.stolen = stolen;
            this.injured = injured;
            this.attack = attack;
            this.defense = defense;
        }
    }

    public List<Scout> guards(Collection<Scout> scouts) {
        List<Scout> out = new ArrayList<>();
        for (Scout s : scouts) {
            String task = s.getLife().getTaskId();
            if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE && task != null
                    && content.task(task).isGuards()) {
                out.add(s);
            }
        }
        return out;
    }

    public int defense(Collection<Scout> guards) {
        int total = 0;
        for (Scout g : guards) {
            total += (rules.effectiveStat(g, StatType.ENDURANCE) + rules.effectiveStat(g, StatType.NERVE) + 1) / 2;
        }
        return total;
    }

    public Result resolve(Collection<Scout> scouts, Map<String, Integer> resources, int antennaLevel, GameRandom rng) {
        return resolve(scouts, resources, antennaLevel, 0, rng);
    }

    /** @param fortification barricades: defense that holds even with nobody on guard */
    public Result resolve(Collection<Scout> scouts, Map<String, Integer> resources, int antennaLevel,
                          int fortification, GameRandom rng) {
        EnergyDef.Raids raids = content.getBalance().getEnergy().getRaids();
        List<Scout> guards = guards(scouts);
        int defense = defense(guards) + fortification;
        int attack = raids.getBaseStrength() + raids.getStrengthPerAntennaLevel() * antennaLevel
                + rng.nextInt(raids.getStrengthVariance() + 1);
        if ((!guards.isEmpty() || fortification > 0) && defense >= attack) {
            for (Scout g : guards) {
                rules.trainXp(g, StatType.ENDURANCE, GUARD_XP);
            }
            return new Result(true, false, guards, Map.of(), null, attack, defense);
        }

        List<String> targets = new ArrayList<>();
        for (ResourceDef r : content.getResources().values()) {
            if (r.getKind() == ResourceDef.Kind.MATERIAL && resources.getOrDefault(r.getId(), 0) > 0) {
                targets.add(r.getId());
            }
        }
        Map<String, Integer> stolen = new LinkedHashMap<>();
        for (int i = 0; i < raids.getResourcesTargeted() && !targets.isEmpty(); i++) {
            String id = targets.remove(rng.nextInt(targets.size()));
            int have = resources.get(id);
            stolen.put(id, Math.max(1, (int) Math.floor(have * raids.getStealFraction())));
        }

        List<Scout> exposed = new ArrayList<>(guards);
        if (exposed.isEmpty()) {
            for (Scout s : scouts) {
                if (s.isAlive() && s.getStatus() == ScoutStatus.IDLE) {
                    exposed.add(s);
                }
            }
        }
        Scout injured = null;
        if (!exposed.isEmpty()) {
            injured = rng.pick(exposed);
            int margin = attack - defense;
            rules.injure(injured, margin >= raids.getSeriousMargin() ? Injury.SERIOUS : Injury.LIGHT);
        }
        return new Result(false, guards.isEmpty(), guards, stolen, injured, attack, defense);
    }

    /** Whether a task is guard duty (for UI and tests). */
    public static boolean isGuardTask(TaskDef task) {
        return task != null && task.isGuards();
    }
}
