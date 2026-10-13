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
    /** Scouting grounds by band id (zones.json entries with "wilds"). */
    private final Map<String, ZoneDef> wilds = new LinkedHashMap<>();
    private final Map<String, EventDef> events;
    private final Map<String, VoiceDef> voices;
    private final Map<String, ResourceDef> resources;
    private final Map<String, ItemDef> items;
    private final Map<String, TaskDef> tasks;
    private final Map<String, DialogueDef> dialogues;
    private final List<ScoutTemplate> survivors;
    private final SignalsDef signals;
    private final Map<String, GearDef> gear;
    private final Map<String, RecipeDef> recipes;
    private final StoryDef story;
    private final PeopleDef people;
    private final List<VignetteDef> vignettes;
    private final Map<String, KeyItemDef> keyItems;

    public GameContent(Balance balance, TerminalText terminal, List<ScoutTemplate> starters,
                       List<TraitDef> traits, List<ZoneDef> zones, List<EventDef> events,
                       List<VoiceDef> voices, List<ResourceDef> resources, List<ItemDef> items,
                       List<TaskDef> tasks, List<DialogueDef> dialogues,
                       List<ScoutTemplate> survivors, SignalsDef signals, List<GearDef> gear,
                       List<RecipeDef> recipes, StoryDef story, PeopleDef people, List<VignetteDef> vignettes,
                       List<KeyItemDef> keyItems) {
        this.balance = Objects.requireNonNull(balance);
        this.terminal = Objects.requireNonNull(terminal);
        this.starters = Collections.unmodifiableList(starters);
        this.traits = index(traits, TraitDef::getId);
        List<ZoneDef> places = new java.util.ArrayList<>();
        for (ZoneDef z : zones) {
            if (z.isWilds()) {
                if (wilds.put(z.getBand(), z) != null) {
                    throw new IllegalStateException("Invalid content: two scouting grounds on band " + z.getBand());
                }
            } else {
                places.add(z);
            }
        }
        this.zones = index(places, ZoneDef::getId);
        this.events = index(events, EventDef::getId);
        this.voices = index(voices, VoiceDef::getId);
        this.resources = index(resources, ResourceDef::getId);
        this.items = index(items, ItemDef::getId);
        this.tasks = index(tasks, TaskDef::getId);
        this.dialogues = index(dialogues, DialogueDef::getId);
        this.survivors = Collections.unmodifiableList(survivors);
        this.signals = Objects.requireNonNull(signals);
        this.gear = index(gear, GearDef::getId);
        this.recipes = index(recipes, RecipeDef::getId);
        this.story = Objects.requireNonNull(story);
        this.people = Objects.requireNonNull(people);
        this.vignettes = Collections.unmodifiableList(vignettes);
        this.keyItems = index(keyItems, KeyItemDef::getId);
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

    /** A place or a scouting ground. */
    public ZoneDef zone(String id) {
        for (ZoneDef w : wilds.values()) {
            if (w.getId().equals(id)) {
                return w;
            }
        }
        return require(zones, id, "zone");
    }

    /** Scouting ground for a band, or null. */
    public ZoneDef wilds(String bandId) {
        return wilds.get(bandId);
    }

    public java.util.Collection<ZoneDef> getWilds() {
        return Collections.unmodifiableCollection(wilds.values());
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

    public Map<String, KeyItemDef> getKeyItems() {
        return keyItems;
    }

    /** Key item by id, or null. */
    public KeyItemDef keyItem(String id) {
        return keyItems.get(id);
    }

    public List<VignetteDef> getVignettes() {
        return vignettes;
    }

    public PeopleDef getPeople() {
        return people;
    }

    /** Who a scout is: their generated persona, else their authored template, else null. */
    /** He or she: their own gender, or from the name table for older saves. */
    public boolean isFemale(com.estatica.domain.scouts.Scout scout) {
        ScoutTemplate persona = persona(scout);
        return people.isFemale(persona != null ? persona.getGender() : null, scout.getName(), scout.getId());
    }

    public boolean isFemale(ScoutTemplate persona) {
        return people.isFemale(persona.getGender(), persona.getName(), persona.getId());
    }

    public ScoutTemplate persona(com.estatica.domain.scouts.Scout scout) {
        return scout.getPersona() != null ? scout.getPersona() : starter(scout.getTemplateId());
    }

    /** Template by id (starter or legacy survivor), or null. */
    public ScoutTemplate starter(String templateId) {
        for (ScoutTemplate template : starters) {
            if (template.getId().equals(templateId)) {
                return template;
            }
        }
        for (ScoutTemplate template : survivors) {
            if (template.getId().equals(templateId)) {
                return template;
            }
        }
        return null;
    }

    /** Hand-made survivors from before generated people; kept so older saves still load. */
    public List<ScoutTemplate> getSurvivors() {
        return survivors;
    }

    public SignalsDef getSignals() {
        return signals;
    }

    /** Gear definition, or null if the id isn't gear. */
    public GearDef gear(String id) {
        return id != null ? gear.get(id) : null;
    }

    public Map<String, GearDef> getGear() {
        return gear;
    }

    public StoryDef getStory() {
        return story;
    }

    public RecipeDef recipe(String id) {
        return require(recipes, id, "recipe");
    }

    public Map<String, RecipeDef> getRecipes() {
        return recipes;
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
