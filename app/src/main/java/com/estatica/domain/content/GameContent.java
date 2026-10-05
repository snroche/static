package com.estatica.domain.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** All data-driven content, parsed once at startup and cached. Lookups are by id. */
public final class GameContent {

    private final Balance balance;
    private final TerminalText terminal;
    private final List<ScoutTemplate> starters;
    private final Map<String, TraitDef> traits;
    private final Map<String, ZoneDef> zones;
    private final Map<String, EventDef> events;
    private final Map<String, VoiceDef> voices;
    private final Map<String, ResourceDef> resources;
    private final Map<String, ItemDef> items;
    private final Map<String, TaskDef> tasks;
    private final Map<String, DialogueDef> dialogues;

    public GameContent(Balance balance, TerminalText terminal, List<ScoutTemplate> starters,
                       List<TraitDef> traits, List<ZoneDef> zones, List<EventDef> events,
                       List<VoiceDef> voices, List<ResourceDef> resources, List<ItemDef> items,
                       List<TaskDef> tasks, List<DialogueDef> dialogues) {
        this.balance = Objects.requireNonNull(balance);
        this.terminal = Objects.requireNonNull(terminal);
        this.starters = Collections.unmodifiableList(starters);
        this.traits = index(traits, TraitDef::getId);
        this.zones = index(zones, ZoneDef::getId);
        this.events = index(events, EventDef::getId);
        this.voices = index(voices, VoiceDef::getId);
        this.resources = index(resources, ResourceDef::getId);
        this.items = index(items, ItemDef::getId);
        this.tasks = index(tasks, TaskDef::getId);
        this.dialogues = index(dialogues, DialogueDef::getId);
    }

    private interface IdOf<T> {
        String id(T item);
    }

    private static <T> Map<String, T> index(List<T> items, IdOf<T> idOf) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T item : items) {
            if (map.put(idOf.id(item), item) != null) {
                throw new IllegalStateException("Invalid content: duplicate id " + idOf.id(item));
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public Balance getBalance() {
        return balance;
    }

    public TerminalText getTerminal() {
        return terminal;
    }

    public List<ScoutTemplate> getStarters() {
        return starters;
    }

    public TraitDef trait(String id) {
        return require(traits, id, "trait");
    }

    public ZoneDef zone(String id) {
        return require(zones, id, "zone");
    }

    public EventDef event(String id) {
        return require(events, id, "event");
    }

    public VoiceDef voice(String id) {
        return require(voices, id, "voice");
    }

    public ResourceDef resource(String id) {
        return require(resources, id, "resource");
    }

    public ItemDef item(String id) {
        return require(items, id, "item");
    }

    public TaskDef task(String id) {
        return require(tasks, id, "task");
    }

    public DialogueDef dialogue(String id) {
        return require(dialogues, id, "dialogue");
    }

    public Map<String, ItemDef> getItems() {
        return items;
    }

    public Map<String, TaskDef> getTasks() {
        return tasks;
    }

    public Map<String, DialogueDef> getDialogues() {
        return dialogues;
    }

    /** Template by id, or null (e.g. scouts recruited without a template). */
    public ScoutTemplate starter(String templateId) {
        for (ScoutTemplate template : starters) {
            if (template.getId().equals(templateId)) {
                return template;
            }
        }
        return null;
    }

    public Map<String, ZoneDef> getZones() {
        return zones;
    }

    public Map<String, TraitDef> getTraits() {
        return traits;
    }

    public Map<String, EventDef> getEvents() {
        return events;
    }

    public Map<String, VoiceDef> getVoices() {
        return voices;
    }

    public Map<String, ResourceDef> getResources() {
        return resources;
    }

    private static <T> T require(Map<String, T> map, String id, String kind) {
        T value = map.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Unknown " + kind + ": " + id);
        }
        return value;
    }
}
