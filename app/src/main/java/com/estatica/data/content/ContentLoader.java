package com.estatica.data.content;

import com.estatica.domain.content.Balance;
import com.estatica.domain.content.DialogueDef;
import com.estatica.domain.content.EventDef;
import com.estatica.domain.content.GearDef;
import com.estatica.domain.content.RecipeDef;
import com.estatica.domain.content.ItemDef;
import com.estatica.domain.content.TaskDef;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.PeopleDef;
import com.estatica.domain.content.KeyItemDef;
import com.estatica.domain.content.VignetteDef;
import com.estatica.domain.content.ResourceDef;
import com.estatica.domain.content.ScoutTemplate;
import com.estatica.domain.content.SignalsDef;
import com.estatica.domain.content.StoryDef;
import com.estatica.domain.content.TerminalText;
import com.estatica.domain.content.TraitDef;
import com.estatica.domain.content.VoiceDef;
import com.estatica.domain.content.ZoneDef;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

/** Reads every content file from a {@link Source} (assets on device, files in tests). */
public final class ContentLoader {

    public interface Source {
        Reader open(String file) throws IOException;
    }

    private ContentLoader() {
    }

    public static GameContent load(ContentParser parser, Source source) throws IOException {
        Balance balance;
        TerminalText terminal;
        List<ScoutTemplate> scouts;
        List<TraitDef> traits;
        List<ZoneDef> zones;
        List<EventDef> events;
        List<VoiceDef> voices;
        List<ResourceDef> resources;
        List<ItemDef> items;
        List<TaskDef> tasks;
        List<DialogueDef> dialogues;
        List<ScoutTemplate> survivors;
        SignalsDef signals;
        List<GearDef> gear;
        List<RecipeDef> recipes;
        StoryDef story;
        PeopleDef people;
        List<VignetteDef> vignettes;
        List<KeyItemDef> keyItems;
        try (Reader r = source.open("balance.json")) {
            balance = parser.parseBalance(r);
        }
        try (Reader r = source.open("terminal.json")) {
            terminal = parser.parseTerminal(r);
        }
        try (Reader r = source.open("scouts.json")) {
            scouts = parser.parseScouts(r);
        }
        try (Reader r = source.open("traits.json")) {
            traits = parser.parseTraits(r);
        }
        try (Reader r = source.open("zones.json")) {
            zones = parser.parseZones(r);
        }
        try (Reader r = source.open("events.json")) {
            events = parser.parseEvents(r);
        }
        try (Reader r = source.open("voices.json")) {
            voices = parser.parseVoices(r);
        }
        try (Reader r = source.open("resources.json")) {
            resources = parser.parseResources(r);
        }
        try (Reader r = source.open("items.json")) {
            items = parser.parseItems(r);
        }
        try (Reader r = source.open("tasks.json")) {
            tasks = parser.parseTasks(r);
        }
        try (Reader r = source.open("dialogues.json")) {
            dialogues = parser.parseDialogues(r);
        }
        try (Reader r = source.open("survivors.json")) {
            survivors = parser.parseSurvivors(r);
        }
        try (Reader r = source.open("signals.json")) {
            signals = parser.parseSignals(r);
        }
        try (Reader r = source.open("gear.json")) {
            gear = parser.parseGear(r);
        }
        try (Reader r = source.open("recipes.json")) {
            recipes = parser.parseRecipes(r);
        }
        try (Reader r = source.open("story.json")) {
            story = parser.parseStory(r);
        }
        try (Reader r = source.open("people.json")) {
            people = parser.parsePeople(r);
        }
        try (Reader r = source.open("vignettes.json")) {
            vignettes = parser.parseVignettes(r);
        }
        try (Reader r = source.open("keyitems.json")) {
            keyItems = parser.parseKeyItems(r);
        }
        return new GameContent(balance, terminal, scouts, traits, zones, events, voices, resources,
                items, tasks, dialogues, survivors, signals, gear, recipes, story, people, vignettes, keyItems);
    }
}
