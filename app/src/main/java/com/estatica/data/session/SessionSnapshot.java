package com.estatica.data.session;

import java.util.Collections;
import java.util.List;

/** Immutable read model of the game for the UI. Built on the game executor. */
public final class SessionSnapshot {

    public final List<ScoutView> scouts;
    /** Situations and conversations waiting for the operator; situations first. */
    public final List<PendingDecision> pending;
    public final List<ZoneView> zones;
    public final List<ResourceView> resources;
    /** Personal objects in the bunker, available as gifts. */
    public final List<ItemView> inventory;
    public final List<TaskView> tasks;
    /** Rescues and relay jobs available at dispatch, besides plain exploring. */
    public final List<MissionView> missions;
    public final RadioView radio;
    public final BaseView base;
    /** Next moment the game state changes on its own, or -1. */
    public final long nextWakeAtMillis;

    SessionSnapshot(List<ScoutView> scouts, List<PendingDecision> pending, List<ZoneView> zones,
                    List<ResourceView> resources, List<ItemView> inventory, List<TaskView> tasks,
                    List<MissionView> missions, RadioView radio, BaseView base, long nextWakeAtMillis) {
        this.base = base;
        this.missions = Collections.unmodifiableList(missions);
        this.radio = radio;
        this.scouts = Collections.unmodifiableList(scouts);
        this.pending = Collections.unmodifiableList(pending);
        this.zones = Collections.unmodifiableList(zones);
        this.resources = Collections.unmodifiableList(resources);
        this.inventory = Collections.unmodifiableList(inventory);
        this.tasks = Collections.unmodifiableList(tasks);
        this.nextWakeAtMillis = nextWakeAtMillis;
    }

    public static final class ScoutView {
        public final String id;
        public final String name;
        public final String bio;
        /** Status label. */
        public final String status;
        /** Injury label. */
        public final String injury;
        public final boolean dead;
        public final int stress;
        public final int trust;
        public final int bondLevel;
        public final int bondLevels;
        public final List<StatView> stats;
        /** Visible trait names. */
        public final List<String> traits;
        /** Task label (or the "no task" label). */
        public final String task;
        /** Discovered tastes, already worded ("Le gusta: …"), or null. */
        public final String likes;
        public final String dislikes;
        /** Names of gifts they carry. */
        public final List<String> gifts;
        public final boolean canDispatch;
        public final boolean canTalk;
        public final boolean canGift;
        public final boolean canAssignTask;
        /** Zone name while away, else null. */
        public final String awayAt;
        /** Equipped gear name, or null. */
        public final String gear;
        public final boolean canEquip;
        /** "Amistad con Tano", "Roces con Irene"… */
        public final List<String> relations;

        ScoutView(Builder b) {
            this.relations = Collections.unmodifiableList(b.relations);
            this.gear = b.gear;
            this.canEquip = b.canEquip;
            this.id = b.id;
            this.name = b.name;
            this.bio = b.bio;
            this.status = b.status;
            this.injury = b.injury;
            this.dead = b.dead;
            this.stress = b.stress;
            this.trust = b.trust;
            this.bondLevel = b.bondLevel;
            this.bondLevels = b.bondLevels;
            this.stats = Collections.unmodifiableList(b.stats);
            this.traits = Collections.unmodifiableList(b.traits);
            this.task = b.task;
            this.likes = b.likes;
            this.dislikes = b.dislikes;
            this.gifts = Collections.unmodifiableList(b.gifts);
            this.canDispatch = b.canDispatch;
            this.canTalk = b.canTalk;
            this.canGift = b.canGift;
            this.canAssignTask = b.canAssignTask;
            this.awayAt = b.awayAt;
        }

        static final class Builder {
            String id;
            String name;
            String bio;
            String status;
            String injury;
            boolean dead;
            int stress;
            int trust;
            int bondLevel;
            int bondLevels;
            List<StatView> stats;
            List<String> traits;
            String task;
            String likes;
            String dislikes;
            List<String> gifts;
            boolean canDispatch;
            boolean canTalk;
            boolean canGift;
            boolean canAssignTask;
            String awayAt;
            String gear;
            boolean canEquip;
            List<String> relations;
        }
    }

    public static final class StatView {
        public final String name;
        public final int value;
        public final int ceiling;

        StatView(String name, int value, int ceiling) {
            this.name = name;
            this.value = value;
            this.ceiling = ceiling;
        }
    }

    public static final class PendingDecision {
        public enum Kind { ORDER, TALK }

        public final Kind kind;
        /** Expedition id (ORDER) or conversation id (TALK). */
        public final String id;
        public final String scoutName;
        public final List<String> options;
        /** When it resolves without the operator, or -1 if it waits. */
        public final long deadlineMillis;

        PendingDecision(Kind kind, String id, String scoutName, List<String> options, long deadlineMillis) {
            this.kind = kind;
            this.id = id;
            this.scoutName = scoutName;
            this.options = Collections.unmodifiableList(options);
            this.deadlineMillis = deadlineMillis;
        }
    }

    public static final class ZoneView {
        public final String id;
        public final String name;
        public final String description;
        public final int danger;
        public final int minMinutes;
        public final int maxMinutes;
        public final String band;
        /** Relay condition 0–100, or -1 if none. */
        public final int relay;
        /** Decoded intel waiting here (cache / danger). */
        public final boolean intel;

        ZoneView(String id, String name, String description, int danger, int minMinutes, int maxMinutes,
                 String band, int relay, boolean intel) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.danger = danger;
            this.minMinutes = minMinutes;
            this.maxMinutes = maxMinutes;
            this.band = band;
            this.relay = relay;
            this.intel = intel;
        }
    }

    /** A special expedition (rescue, relay install / repair, personal mission). */
    public static final class MissionView {
        public final String type;
        public final String zoneId;
        /** Survivor id for rescues, personal mission id, else null. */
        public final String target;
        public final String label;
        public final String zoneName;
        /** Only this scout can take it (personal missions), or null for anyone. */
        public final String scoutId;

        MissionView(String type, String zoneId, String target, String label, String zoneName) {
            this(type, zoneId, target, label, zoneName, null);
        }

        MissionView(String type, String zoneId, String target, String label, String zoneName, String scoutId) {
            this.type = type;
            this.zoneId = zoneId;
            this.target = target;
            this.label = label;
            this.zoneName = zoneName;
            this.scoutId = scoutId;
        }
    }

    /** A daily or weekly goal (GDD §9). */
    public static final class GoalView {
        public final String label;
        public final int progress;
        public final int target;
        public final boolean weekly;

        GoalView(String label, int progress, int target, boolean weekly) {
            this.label = label;
            this.progress = progress;
            this.target = target;
            this.weekly = weekly;
        }
    }

    public static final class BandView {
        public final String id;
        public final String name;
        public final boolean unlocked;

        BandView(String id, String name, boolean unlocked) {
            this.id = id;
            this.name = name;
            this.unlocked = unlocked;
        }
    }

    public static final class TransmissionView {
        public final String id;
        /** CAESAR or MORSE. */
        public final String kind;
        public final String cipherText;
        public final String band;

        TransmissionView(String id, String kind, String cipherText, String band) {
            this.id = id;
            this.kind = kind;
            this.cipherText = cipherText;
            this.band = band;
        }
    }

    public static final class ModuleView {
        public final String id;
        public final String name;
        public final String description;
        public final int level;
        public final int maxLevel;
        /** "10 chatarra, 4 componentes" or null at max level. */
        public final String upgradeCost;
        public final boolean affordable;

        ModuleView(String id, String name, String description, int level, int maxLevel,
                   String upgradeCost, boolean affordable) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.level = level;
            this.maxLevel = maxLevel;
            this.upgradeCost = upgradeCost;
            this.affordable = affordable;
        }
    }

    public static final class RadioView {
        public final List<BandView> bands;
        public final int dialMax;
        public final List<TransmissionView> transmissions;
        public final boolean morseTable;
        public final int relayKits;
        /** "Hospital Municipal 80%" … */
        public final List<String> relays;

        RadioView(List<BandView> bands, int dialMax, List<TransmissionView> transmissions,
                  boolean morseTable, int relayKits, List<String> relays) {
            this.bands = Collections.unmodifiableList(bands);
            this.dialMax = dialMax;
            this.transmissions = Collections.unmodifiableList(transmissions);
            this.morseTable = morseTable;
            this.relayKits = relayKits;
            this.relays = Collections.unmodifiableList(relays);
        }
    }

    /** The bunker (GDD §6.4, §6.5, §7): power, modules, workshop, defense, gear stock. */
    public static final class BaseView {
        public final int watts;
        public final int available;
        public final int used;
        public final boolean blackout;
        public final boolean night;
        public final boolean filtersShort;
        public final List<ConsumerView> consumers;
        public final int reserve;
        public final int reserveCapacity;
        public final boolean canRecharge;
        public final List<ModuleView> modules;
        public final int slots;
        public final List<JobView> jobs;
        public final List<RecipeView> recipes;
        public final List<String> guards;
        public final int defense;
        public final List<ItemView> gearStock;
        public final int scouts;
        public final int bunks;
        public final List<GoalView> goals;

        BaseView(List<GoalView> goals, int watts, int available, int used, boolean blackout, boolean night, boolean filtersShort,
                 List<ConsumerView> consumers, int reserve, int reserveCapacity, boolean canRecharge,
                 List<ModuleView> modules, int slots, List<JobView> jobs, List<RecipeView> recipes,
                 List<String> guards, int defense, List<ItemView> gearStock, int scouts, int bunks) {
            this.watts = watts;
            this.available = available;
            this.used = used;
            this.blackout = blackout;
            this.night = night;
            this.filtersShort = filtersShort;
            this.consumers = Collections.unmodifiableList(consumers);
            this.reserve = reserve;
            this.reserveCapacity = reserveCapacity;
            this.canRecharge = canRecharge;
            this.modules = Collections.unmodifiableList(modules);
            this.slots = slots;
            this.jobs = Collections.unmodifiableList(jobs);
            this.recipes = Collections.unmodifiableList(recipes);
            this.guards = Collections.unmodifiableList(guards);
            this.defense = defense;
            this.gearStock = Collections.unmodifiableList(gearStock);
            this.scouts = scouts;
            this.bunks = bunks;
            this.goals = Collections.unmodifiableList(goals);
        }
    }

    public static final class ConsumerView {
        public final String id;
        public final String name;
        public final String description;
        public final int requested;
        public final int effective;
        public final int max;

        ConsumerView(String id, String name, String description, int requested, int effective, int max) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.requested = requested;
            this.effective = effective;
            this.max = max;
        }
    }

    public static final class JobView {
        public final String name;
        public final int percent;
        /** Real minutes left, or -1 while the workshop has no power. */
        public final int minutesLeft;

        JobView(String name, int percent, int minutesLeft) {
            this.name = name;
            this.percent = percent;
            this.minutesLeft = minutesLeft;
        }
    }

    public static final class RecipeView {
        public final String id;
        public final String name;
        public final String cost;
        public final int minutes;
        public final boolean affordable;
        /** Workshop level needed, or 0 if available. */
        public final int needsLevel;

        RecipeView(String id, String name, String cost, int minutes, boolean affordable, int needsLevel) {
            this.id = id;
            this.name = name;
            this.cost = cost;
            this.minutes = minutes;
            this.affordable = affordable;
            this.needsLevel = needsLevel;
        }
    }

    /** What the dial picks up right now. */
    public static final class Tuning {
        public final double strength;
        public final String text;
        public final boolean lockable;

        public Tuning(double strength, String text, boolean lockable) {
            this.strength = strength;
            this.text = text;
            this.lockable = lockable;
        }
    }

    public static final class ResourceView {
        public final String name;
        public final int amount;

        ResourceView(String name, int amount) {
            this.name = name;
            this.amount = amount;
        }
    }

    public static final class ItemView {
        /** Instance id. */
        public final String id;
        public final String name;
        public final String description;

        ItemView(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }
    }

    public static final class TaskView {
        /** Null for "no task". */
        public final String id;
        public final String name;
        public final String description;

        TaskView(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }
    }

    /**
     * A Diary row (GDD §6.6): a section title ({@link #section}) or an entry: a fallen scout,
     * a La Voz fragment, a decoded transmission, a known zone.
     */
    public static final class DiaryEntry {
        public final boolean section;
        public final String name;
        /** Second line ("Sin señal desde el … · zone", band, source…), or null. */
        public final String header;
        public final String bio;
        public final List<String> memories;
        public final String lastWords;

        DiaryEntry(String name, String header, String bio, List<String> memories, String lastWords) {
            this(false, name, header, bio, memories, lastWords);
        }

        DiaryEntry(boolean section, String name, String header, String bio, List<String> memories, String lastWords) {
            this.section = section;
            this.name = name;
            this.header = header;
            this.bio = bio;
            this.memories = Collections.unmodifiableList(memories);
            this.lastWords = lastWords;
        }

        static DiaryEntry section(String title) {
            return new DiaryEntry(true, title, null, null, List.of(), null);
        }
    }
}
