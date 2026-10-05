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
    /** Next moment the game state changes on its own, or -1. */
    public final long nextWakeAtMillis;

    SessionSnapshot(List<ScoutView> scouts, List<PendingDecision> pending, List<ZoneView> zones,
                    List<ResourceView> resources, List<ItemView> inventory, List<TaskView> tasks,
                    List<MissionView> missions, RadioView radio, long nextWakeAtMillis) {
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

        ScoutView(Builder b) {
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

    /** A special expedition (rescue, relay install / repair). */
    public static final class MissionView {
        public final String type;
        public final String zoneId;
        /** Survivor id for rescues, else null. */
        public final String target;
        public final String label;
        public final String zoneName;

        MissionView(String type, String zoneId, String target, String label, String zoneName) {
            this.type = type;
            this.zoneId = zoneId;
            this.target = target;
            this.label = label;
            this.zoneName = zoneName;
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
        public final List<ModuleView> modules;
        public final int relayKits;
        public final String relayKitCost;
        public final boolean relayKitAffordable;
        /** "Hospital Municipal 80%" … */
        public final List<String> relays;

        RadioView(List<BandView> bands, int dialMax, List<TransmissionView> transmissions,
                  boolean morseTable, List<ModuleView> modules, int relayKits, String relayKitCost,
                  boolean relayKitAffordable, List<String> relays) {
            this.bands = Collections.unmodifiableList(bands);
            this.dialMax = dialMax;
            this.transmissions = Collections.unmodifiableList(transmissions);
            this.morseTable = morseTable;
            this.modules = Collections.unmodifiableList(modules);
            this.relayKits = relayKits;
            this.relayKitCost = relayKitCost;
            this.relayKitAffordable = relayKitAffordable;
            this.relays = Collections.unmodifiableList(relays);
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

    /** One fallen scout in the Diary (GDD §5.5, §6.6). */
    public static final class DiaryEntry {
        public final String name;
        /** "Sin señal desde el … · zone". */
        public final String header;
        public final String bio;
        public final List<String> memories;
        public final String lastWords;

        DiaryEntry(String name, String header, String bio, List<String> memories, String lastWords) {
            this.name = name;
            this.header = header;
            this.bio = bio;
            this.memories = Collections.unmodifiableList(memories);
            this.lastWords = lastWords;
        }
    }
}
