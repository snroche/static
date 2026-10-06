package com.estatica;

import com.estatica.core.GameRandom;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.ScoutTemplate;
import com.estatica.domain.scouts.Injury;
import com.estatica.domain.scouts.PersonaGenerator;
import com.estatica.domain.scouts.Scout;
import com.estatica.domain.scouts.ScoutFactory;
import com.estatica.domain.scouts.ScoutLife;
import com.estatica.domain.scouts.ScoutStatus;
import com.estatica.domain.scouts.ScoutTrait;
import com.estatica.domain.scouts.Stat;
import com.estatica.domain.scouts.StatType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Scout builders for tests. */
public final class TestScouts {

    private TestScouts() {
    }

    /** A starter from scouts.json, exactly as a new game creates it. */
    public static Scout starter(String templateId, long now) {
        GameContent content = TestContent.get();
        return ScoutFactory.fromTemplate(content.starter(templateId), content.getBalance().getScouts(), now);
    }

    /**
     * A scout based on a starter template (voice, tastes, dialogues) with custom stats,
     * stress and bond.
     */
    public static Scout custom(String templateId, int statValue, int stress, int bond, long now) {
        Scout base = starter(templateId, now);
        Map<StatType, Stat> stats = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            stats.put(type, new Stat(statValue, 10, 0));
        }
        List<ScoutTrait> traits = new ArrayList<>(base.getTraits());
        ScoutLife life = new ScoutLife(bond, null, Set.of(), now, 0, now, 0, 0, 0, null, 0, null);
        return new Scout(base.getId(), templateId, base.getName(), base.getVoiceId(), stats, traits,
                stress, 50, Injury.NONE, ScoutStatus.IDLE, now, 0, life);
    }

    /** A generated person with that background (first seed that rolls it). */
    public static ScoutTemplate persona(String background) {
        PersonaGenerator generator = new PersonaGenerator(TestContent.get());
        for (long seed = 0; seed < 1000; seed++) {
            ScoutTemplate p = generator.generate(PersonaGenerator.ID_PREFIX + seed, 1, List.of(), List.of(),
                    Set.of(), GameRandom.fromSeed(seed));
            if (background.equals(p.getBackground())) {
                return p;
            }
        }
        throw new AssertionError("no seed rolls " + background);
    }

    /** A generated person with that background, custom stats, stress and bond. */
    public static Scout person(String background, int statValue, int stress, int bond, long now) {
        ScoutTemplate persona = persona(background);
        Scout base = ScoutFactory.fromTemplate(persona, TestContent.get().getBalance().getScouts(), now);
        Map<StatType, Stat> stats = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            stats.put(type, new Stat(statValue, 10, 0));
        }
        ScoutLife life = new ScoutLife(bond, null, Set.of(), now, 0, now, 0, 0, 0, null, 0, null);
        Scout s = new Scout(base.getId(), null, base.getName(), base.getVoiceId(), stats,
                new ArrayList<>(base.getTraits()), stress, 50, Injury.NONE, ScoutStatus.IDLE, now, 0, life);
        s.setPersona(persona);
        return s;
    }
}
