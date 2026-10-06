package com.estatica;

import com.estatica.domain.content.GameContent;
import com.estatica.domain.scouts.Injury;
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
}
