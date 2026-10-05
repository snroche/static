package com.estatica.data.content;

import com.estatica.domain.content.Balance;
import com.estatica.domain.content.DialogueDef;
import com.estatica.domain.content.EventDef;
import com.estatica.domain.content.ItemDef;
import com.estatica.domain.content.TaskDef;
import com.estatica.domain.content.ResourceDef;
import com.estatica.domain.content.ScoutTemplate;
import com.estatica.domain.content.TerminalText;
import com.estatica.domain.content.TraitDef;
import com.estatica.domain.content.VoiceDef;
import com.estatica.domain.content.ZoneDef;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.Reader;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

/**
 * Parses content JSON and checks each file's own shape. Cross-file references are checked
 * by {@link com.estatica.domain.content.ContentValidator}. Pure Java, so it is unit-testable.
 */
public final class ContentParser {

    private final Gson gson = new Gson();

    @Inject
    public ContentParser() {
    }

    public Balance parseBalance(Reader reader) {
        Balance balance = parse(reader, Balance.class, "balance.json");
        require(balance.getContentVersion() > 0, "balance.json: contentVersion");
        require(balance.getUi() != null, "balance.json: ui");
        require(balance.getUi().getTypewriterCharMillis() > 0, "balance.json: ui.typewriterCharMillis");
        require(balance.getUi().getLogWindowLines() > 0, "balance.json: ui.logWindowLines");
        require(balance.getUi().getLogPageSize() > 0, "balance.json: ui.logPageSize");
        require(balance.getExpedition() != null, "balance.json: expedition");
        require(balance.getExpedition().getSituationTimeoutMinutes() > 0,
                "balance.json: expedition.situationTimeoutMinutes");
        require(balance.getScouts() != null, "balance.json: scouts");
        require(balance.getScouts().getStatMax() > 0, "balance.json: scouts.statMax");
        require(balance.getScouts().getXpPerLevel() > 0, "balance.json: scouts.xpPerLevel");
        require(balance.getScouts().getInjuryStatPenalty() != null
                && balance.getScouts().getInjuryStatPenalty().length == 4,
                "balance.json: scouts.injuryStatPenalty needs 4 values");
        require(balance.getScouts().getInjuryHealHoursPerLevel() > 0,
                "balance.json: scouts.injuryHealHoursPerLevel");
        require(balance.getChecks() != null, "balance.json: checks");
        require(balance.getChecks().getMinChance() < balance.getChecks().getMaxChance(),
                "balance.json: checks.minChance < maxChance");
        require(balance.getBond() != null && balance.getBond().getLevelThresholds() != null
                && balance.getBond().getLevelThresholds().length > 0, "balance.json: bond.levelThresholds");
        require(balance.getGrief() != null && balance.getGrief().getTraits() != null, "balance.json: grief");
        require(balance.getAffinity() != null, "balance.json: affinity");
        return balance;
    }

    public TerminalText parseTerminal(Reader reader) {
        TerminalText text = parse(reader, TerminalText.class, "terminal.json");
        require(text.getBoot() != null && !text.getBoot().isEmpty(), "terminal.json: boot");
        require(text.getReconnect() != null && !text.getReconnect().isEmpty(), "terminal.json: reconnect");
        require(text.getElapsedUnits() != null, "terminal.json: elapsedUnits");
        require(text.getUnknownCommand() != null, "terminal.json: unknownCommand");
        require(text.getCommands() != null, "terminal.json: commands");
        require(text.getStatNames() != null, "terminal.json: statNames");
        require(text.getExpedition() != null, "terminal.json: expedition");
        require(text.getBunker() != null, "terminal.json: bunker");
        return text;
    }

    public List<ScoutTemplate> parseScouts(Reader reader) {
        return parseArray(reader, ScoutTemplate[].class, "scouts.json");
    }

    public List<TraitDef> parseTraits(Reader reader) {
        return parseArray(reader, TraitDef[].class, "traits.json");
    }

    public List<ZoneDef> parseZones(Reader reader) {
        return parseArray(reader, ZoneDef[].class, "zones.json");
    }

    public List<EventDef> parseEvents(Reader reader) {
        return parseArray(reader, EventDef[].class, "events.json");
    }

    public List<VoiceDef> parseVoices(Reader reader) {
        return parseArray(reader, VoiceDef[].class, "voices.json");
    }

    public List<ResourceDef> parseResources(Reader reader) {
        return parseArray(reader, ResourceDef[].class, "resources.json");
    }

    public List<ItemDef> parseItems(Reader reader) {
        return parseArray(reader, ItemDef[].class, "items.json");
    }

    public List<TaskDef> parseTasks(Reader reader) {
        return parseArray(reader, TaskDef[].class, "tasks.json");
    }

    public List<DialogueDef> parseDialogues(Reader reader) {
        return parseArray(reader, DialogueDef[].class, "dialogues.json");
    }

    private <T> List<T> parseArray(Reader reader, Class<T[]> type, String name) {
        T[] items = parse(reader, type, name);
        require(items.length > 0, name + " is empty");
        for (T item : items) {
            require(item != null, name + " has a null entry");
        }
        return Arrays.asList(items);
    }

    private <T> T parse(Reader reader, Class<T> type, String name) {
        try {
            T value = gson.fromJson(reader, type);
            require(value != null, name + " is empty");
            return value;
        } catch (JsonParseException e) {
            throw new IllegalStateException("Malformed " + name, e);
        }
    }

    private static void require(boolean condition, String what) {
        if (!condition) {
            throw new IllegalStateException("Invalid content: " + what);
        }
    }
}
