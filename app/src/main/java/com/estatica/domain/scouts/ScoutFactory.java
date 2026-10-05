package com.estatica.domain.scouts;

import com.estatica.core.Ids;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.ScoutTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Creates scouts from content templates. */
public final class ScoutFactory {

    private ScoutFactory() {
    }

    public static Scout fromTemplate(ScoutTemplate template, Balance.Scouts balance, long nowMillis) {
        Map<StatType, Stat> stats = new EnumMap<>(StatType.class);
        for (Map.Entry<String, int[]> entry : template.getStats().entrySet()) {
            int[] range = entry.getValue();
            stats.put(StatType.fromKey(entry.getKey()), new Stat(range[0], range[1], 0));
        }
        List<ScoutTrait> traits = new ArrayList<>();
        for (String traitId : template.getTraits()) {
            traits.add(new ScoutTrait(traitId, false, false));
        }
        if (template.getHiddenTrait() != null) {
            traits.add(new ScoutTrait(template.getHiddenTrait(), true, false));
        }
        return new Scout(Ids.newId(), template.getId(), template.getName(), template.getVoice(),
                stats, traits, balance.getInitialStress(), balance.getInitialTrust(),
                Injury.NONE, ScoutStatus.IDLE, nowMillis, 0);
    }
}
