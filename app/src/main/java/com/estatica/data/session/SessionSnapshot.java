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
    /** Scouts out right now, for the expedition strip. */
    public final List<ExpeditionView> expeditions;
    /** Key items: notebooks, manuals, codebooks (read-only, they unlock things). */
    public final List<KeyItemView> keyItems;
    /** Term to explanation, for long-press info (traits, stats, resources, places, gear…). */
    public final java.util.Map<String, String> glossary;
    /** Weather outside now (WeatherRules.Weather name) and its display name. */
    public final String weather;
    public final String weatherName;
    /** Words the terminal highlights: every place name and resource name. */
    public final List<String> placeNames;
    public final List<String> resourceNames;
    public final List<String> dangerWords;
    public final List<String> goodWords;

    /** The region around the bunker: sectors and the places found so far. */
    public final MapView map;
    /** How long the rations last at the current rate (infinite if nobody eats). */
    public final double foodDaysLeft;
    /** The stores are empty and the crew is going hungry. */
    public final boolean hungry;
    /** Next moment the game state changes on its own, or -1. */
    public final long nextWakeAtMillis;

    SessionSnapshot(List<ScoutView> scouts, List<PendingDecision> pending, List<ZoneView> zones,
                    List<ResourceView> resources, List<ItemView> inventory, List<TaskView> tasks,
                    List<MissionView> missions, RadioView radio, BaseView base,
                    List<ExpeditionView> expeditions, double foodDaysLeft, boolean hungry,
                    MapView map, List<KeyItemView> keyItems, java.util.Map<String, String> glossary, String weather, String weatherName,
                    List<String> placeNames, List<String> resourceNames, List<String> dangerWords,
                    List<String> goodWords, long nextWakeAtMillis) {
        this.map = map;
        this.glossary = glossary;
        this.keyItems = Collections.unmodifiableList(keyItems);
        this.weather = weather;
        this.weatherName = weatherName;
        this.placeNames = placeNames;
        this.resourceNames = resourceNames;
        this.dangerWords = dangerWords;
        this.goodWords = goodWords;
        this.expeditions = Collections.unmodifiableList(expeditions);
        this.foodDaysLeft = foodDaysLeft;
        this.hungry = hungry;
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
        /** Task id, or null for none. */
        public final String taskId;
        /** Trip length multiplier (traits, gear, injuries): 1 is normal, lower is faster. */
        public final double pace;
        /** Discovered tastes, already worded ("Le gusta: …"), or null. */
        public final String likes;
        public final String dislikes;
        /** Names of gifts they carry. */
        public final List<String> gifts;
        public final boolean canDispatch;
        public final boolean canTalk;
        /** At home but still tired of talking: when they'll want to again. 0 otherwise. */
        public final long talkReadyAtMillis;
        /** How far into the current bond level, 0–100. */
        public final int bondProgress;
        public final boolean female;
        /** "♥ together", "flirting"…, or null. */
        public final String romance;
        public final boolean canGift;
        public final boolean canAssignTask;
        /** Zone name while away, else null. */
        public final String awayAt;
        /** Everything worn, as one line, or null. */
        public final String gear;
        /** Every slot, empty or not. */
        public final List<SlotView> slots;
        public final boolean canEquip;
        /** Out in the field and not already heading home. */
        public final boolean canRecall;
        /** Has any injury. */
        public final boolean hurt;
        /** "Friends with Tano", "Friction with Irene"… */
        public final List<String> relations;
        /** Mood label. */
        public final String mood;
        /** Mood key: content, steady, tense, breaking. */
        public final String moodKey;
        /** Habits revealed so far. */
        public final List<String> habits;
        /** Other scout id to "friends", "inseparable" or "rivals". */
        public final java.util.Map<String, String> relationKinds;

        ScoutView(Builder b) {
            this.mood = b.mood;
            this.moodKey = b.moodKey;
            this.habits = Collections.unmodifiableList(b.habits);
            this.canRecall = b.canRecall;
            this.hurt = b.hurt;
            this.relationKinds = Collections.unmodifiableMap(b.relationKinds);
            this.relations = Collections.unmodifiableList(b.relations);
            this.gear = b.gear;
            this.slots = b.slots != null ? Collections.unmodifiableList(b.slots) : List.of();
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
            this.taskId = b.taskId;
            this.pace = b.pace;
            this.likes = b.likes;
            this.dislikes = b.dislikes;
            this.gifts = Collections.unmodifiableList(b.gifts);
            this.canDispatch = b.canDispatch;
            this.canTalk = b.canTalk;
            this.talkReadyAtMillis = b.talkReadyAtMillis;
            this.bondProgress = b.bondProgress;
            this.female = b.female;
            this.romance = b.romance;
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
            String taskId;
            double pace = 1;
            String likes;
            String dislikes;
            List<String> gifts;
            boolean canDispatch;
            boolean canTalk;
            long talkReadyAtMillis;
            int bondProgress;
            boolean female;
            String romance;
            boolean canGift;
            boolean canAssignTask;
            String awayAt;
            String gear;
            List<SlotView> slots;
            boolean canEquip;
            List<String> relations;
            String mood;
            String moodKey;
            List<String> habits;
            boolean canRecall;
            boolean hurt;
            java.util.Map<String, String> relationKinds = new java.util.HashMap<>();
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
        public enum Kind { ORDER, TALK, TREAT }

        public final Kind kind;
        /** Expedition id (ORDER) or conversation id (TALK). */
        public final String id;
        public final String scoutName;
        /** Null options aren't this team's to take (indexes still match). */
        public final List<String> options;
        /** When it resolves without the operator, or -1 if it waits. */
        public final long deadlineMillis;
        /** Where (ORDER), or null. */
        public final String place;
        /** Log thread with the full story (expedition or chat). */
        public final String thread;
        /** What they said: the situation, or their lines in a conversation. */
        public final List<String> situation;
        /** Gut feeling per option, ready to show ("bad feeling"), or null. */
        public final List<String> hints;

        PendingDecision(Kind kind, String id, String scoutName, List<String> options, long deadlineMillis,
                        String place, String thread, List<String> situation, List<String> hints) {
            this.kind = kind;
            this.id = id;
            this.scoutName = scoutName;
            this.options = Collections.unmodifiableList(options);
            this.deadlineMillis = deadlineMillis;
            this.place = place;
            this.thread = thread;
            this.situation = Collections.unmodifiableList(situation);
            this.hints = Collections.unmodifiableList(hints);
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
        /** How picked-over it is, 0–100. */
        public final int depleted;
        /** "2 d 4 h" until it's full again, or null if it's untouched. */
        public String refillIn;

        ZoneView(String id, String name, String description, int danger, int minMinutes, int maxMinutes,
                 String band, int relay, boolean intel, int depleted) {
            this.depleted = depleted;
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
        /** Within the antenna's reach: shown either way, sendable only if true. */
        public boolean reachable = true;
        /** Trip range in minutes at normal pace. */
        public int minMinutes;
        public int maxMinutes;
        public int danger;

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
        /** "2 rations", what finishing it pays. */
        public String reward = "";

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

        /** Frequency at each dial position, for the readout under the dial. */
        public final java.util.function.IntFunction<String> frequency;

        BandView(String id, String name, boolean unlocked, java.util.function.IntFunction<String> frequency) {
            this.id = id;
            this.name = name;
            this.unlocked = unlocked;
            this.frequency = frequency;
        }
    }

    public static final class TransmissionView {
        public final String id;
        /** Transmission.Kind name. */
        public final String kind;
        public final String cipherText;
        public final String band;
        /** You have what it takes to work on it (or it needs nothing). */
        public final boolean unlocked;
        /** Locked: what it looks like and what might help. Null when unlocked. */
        public final String lockedHint;
        /** Where the keyword comes from ("Net KESTREL"), or null. */
        public final String keyHint;
        /** What the key item does for you (mirror flipped, pad applied, NATO read out), or null. */
        public final String assisted;
        /** Display name of the scheme. */
        public final String schemeName;

        TransmissionView(String id, String kind, String cipherText, String band, boolean unlocked,
                         String lockedHint, String keyHint, String assisted, String schemeName) {
            this.id = id;
            this.kind = kind;
            this.cipherText = cipherText;
            this.band = band;
            this.unlocked = unlocked;
            this.lockedHint = lockedHint;
            this.keyHint = keyHint;
            this.assisted = assisted;
            this.schemeName = schemeName;
        }
    }

    public static final class ModuleView {
        public final String id;
        public final String name;
        public final String description;
        public final int level;
        public final int maxLevel;
        /** "10 scrap, 4 components" or null at max level. */
        public final String upgradeCost;
        public final boolean affordable;
        /** What it does now, and what the next level gives (null at max). */
        public final String now;
        public final String next;

        ModuleView(String id, String name, String description, int level, int maxLevel,
                   String upgradeCost, boolean affordable, String now, String next) {
            this.now = now;
            this.next = next;
            this.id = id;
            this.name = name;
            this.description = description;
            this.level = level;
            this.maxLevel = maxLevel;
            this.upgradeCost = upgradeCost;
            this.affordable = affordable;
        }
    }

    /** A station you've heard: where it is and when it's on. */
    public static final class StationView {
        public final String id;
        public final String name;
        public final String bandId;
        public final int position;
        /** "3.520 MHz · Short band". */
        public final String where;
        /** "daily at 19:00, for 1 h". */
        public final String schedule;
        public final boolean onAir;
        /** Leaves something each airing. */
        public final boolean bulletin;

        StationView(String id, String name, String bandId, int position, String where, String schedule,
                    boolean onAir, boolean bulletin) {
            this.id = id;
            this.name = name;
            this.bandId = bandId;
            this.position = position;
            this.where = where;
            this.schedule = schedule;
            this.onAir = onAir;
            this.bulletin = bulletin;
        }
    }

    public static final class RadioView {
        public final List<StationView> stations;
        public final List<BandView> bands;
        public final int dialMax;
        public final List<TransmissionView> transmissions;
        public final boolean morseTable;
        public final int relayKits;
        /** "Hospital Municipal 80%" … */
        public final List<String> relays;

        RadioView(List<BandView> bands, int dialMax, List<TransmissionView> transmissions,
                  boolean morseTable, int relayKits, List<String> relays, List<StationView> stations) {
            this.stations = Collections.unmodifiableList(stations);
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
        /** Reserve one battery adds (bigger with the battery bank). */
        public int rechargePer;
        /** The bunker's rooms, open and sealed. */
        public List<RoomView> rooms = List.of();
        /** Batteries in stock. */
        public int batteries;
        public final boolean canRecharge;
        public final List<ModuleView> modules;
        public final int slots;
        public final List<JobView> jobs;
        public final List<RecipeView> recipes;
        public final List<String> guards;
        public final int defense;
        public final List<ItemView> gearStock;
        /** Every piece the bunker has (stock and equipped), for repairs. */
        public final List<GearView> gear;
        public final int scouts;
        public final int bunks;
        public final List<GoalView> goals;
        /** Some consumer gets less power than asked for. */
        public final boolean powerCut;

        BaseView(List<GoalView> goals, boolean powerCut, int watts, int available, int used, boolean blackout, boolean night, boolean filtersShort,
                 List<ConsumerView> consumers, int reserve, int reserveCapacity, boolean canRecharge,
                 List<ModuleView> modules, int slots, List<JobView> jobs, List<RecipeView> recipes,
                 List<String> guards, int defense, List<ItemView> gearStock, int scouts, int bunks,
                 List<GearView> gear) {
            this.gear = Collections.unmodifiableList(gear);
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
            this.powerCut = powerCut;
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
        /** "7.215 MHz". */
        public final String frequency;
        /** A station's name, or null. */
        public final String station;
        /** What it sounds like (SoundEngine.Audio name): NONE, VOICE, VOICE_F, CHILD, MUSIC… */
        public final String audio;
        /** Reserve it costs to lock on (0: free). */
        public final int cost;

        public Tuning(double strength, String text, boolean lockable, String frequency, String station,
                      String audio, int cost) {
            this.strength = strength;
            this.text = text;
            this.lockable = lockable;
            this.frequency = frequency;
            this.station = station;
            this.audio = audio;
            this.cost = cost;
        }

        /** Nothing but static. */
        public static Tuning quiet(String text, String frequency) {
            return new Tuning(0, text, false, frequency, null, "NONE", 0);
        }
    }

    public static final class ResourceView {
        public final String name;
        public final int amount;
        /** Three-letter label for the status bar, or null. */
        public final String shortName;
        /** Icon glyph for the status bar, or null. */
        public final String icon;

        ResourceView(String name, int amount, String shortName, String icon) {
            this.name = name;
            this.amount = amount;
            this.shortName = shortName;
            this.icon = icon;
        }
    }

    /** The region map: rings by band, eight directions, places found. */
    public static final class MapView {
        public final List<SectorView> sectors;
        public final List<PlaceView> places;
        /** Ring labels, inner first. */
        public final List<String> bands;
        /** How much of its loot a picked-over place gets back per day, in percent. */
        public final int refillPercentPerDay;

        MapView(List<SectorView> sectors, List<PlaceView> places, List<String> bands, int refillPercentPerDay) {
            this.refillPercentPerDay = refillPercentPerDay;
            this.sectors = Collections.unmodifiableList(sectors);
            this.places = Collections.unmodifiableList(places);
            this.bands = Collections.unmodifiableList(bands);
        }
    }

    public static final class SectorView {
        public final String id;
        public final int ring;
        /** 0 = north, clockwise, out of {@link #count}. */
        public final int direction;
        /** Sectors on this ring (8, 12, 16…). */
        public final int count;
        /** "the northeast, short band". */
        public final String label;
        public final boolean scouted;
        /** The radio picked up something from here. */
        public final boolean heard;
        public final boolean reachable;
        public final boolean beingScouted;

        SectorView(String id, int ring, int direction, int count, String label, boolean scouted, boolean heard,
                   boolean reachable, boolean beingScouted) {
            this.id = id;
            this.ring = ring;
            this.direction = direction;
            this.count = count;
            this.label = label;
            this.scouted = scouted;
            this.heard = heard;
            this.reachable = reachable;
            this.beingScouted = beingScouted;
        }
    }

    /** A known place, positioned inside its sector. */
    public static final class PlaceView {
        public final String zoneId;
        public final String name;
        public final int ring;
        /** 0 inner edge of the ring, 1 outer edge. */
        public final double ringPosition;
        /** 0–1 of a turn, clockwise from north. */
        public final double turn;
        public final int danger;
        public final boolean reachable;
        /** How picked-over it is, 0–100. */
        public final int depleted;
        /** Map glyph, or null; and the short label. */
        public final String icon;
        public final String shortName;
        /** "2 d 4 h" until it's full again, or null if it's untouched. */
        public final String refillIn;

        PlaceView(String zoneId, String name, int ring, double ringPosition, double turn, int danger,
                  boolean reachable, int depleted, String icon, String shortName, String refillIn) {
            this.depleted = depleted;
            this.refillIn = refillIn;
            this.icon = icon;
            this.shortName = shortName;
            this.zoneId = zoneId;
            this.name = name;
            this.ring = ring;
            this.ringPosition = ringPosition;
            this.turn = turn;
            this.danger = danger;
            this.reachable = reachable;
        }
    }

    /** A key item: something you keep and read, never use up. */
    public static final class KeyItemView {
        public final String id;
        public final String name;
        public final String description;
        /** What's written in it (readable), or null. */
        public final String text;

        KeyItemView(String id, String name, String description, String text) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.text = text;
        }
    }

    /** One gear slot on a scout. */
    public static final class SlotView {
        /** GearDef.Slot name. */
        public final String slot;
        public final String name;
        /** Null if empty. */
        public final String pieceId;
        public final String label;

        SlotView(String slot, String name, String pieceId, String label) {
            this.slot = slot;
            this.name = name;
            this.pieceId = pieceId;
            this.label = label;
        }
    }

    /** One piece of equipment in the bunker or on someone. */
    public static final class GearView {
        public final String id;
        /** "Flashlight · rare · 80%". */
        public final String label;
        /** Who has it on, or null if in stock. */
        public final String holder;
        /** "2 parts, 1 components", or null at full condition. */
        public final String repairCost;
        public final boolean affordable;

        GearView(String id, String label, String holder, String repairCost, boolean affordable) {
            this.id = id;
            this.label = label;
            this.holder = holder;
            this.repairCost = repairCost;
            this.affordable = affordable;
        }
    }

    /** One expedition in progress. */
    /** A room of the bunker, for the floor plan and its sheet. */
    public static final class RoomView {
        public final String id;
        public final String name;
        /** What it is (open), or what's in the way (sealed). */
        public final String text;
        public final int x;
        public final int y;
        public final int w;
        public final int h;
        public final boolean open;
        /** Being restored right now. */
        public final boolean restoring;
        /** 0–1 while restoring. */
        public final double progress;
        /** "12 scrap, 4 components" to start restoring (sealed only). */
        public final String cost;
        public final int hours;
        /** It can be started now: requirements met, affordable, no other project. */
        public final boolean canRestore;
        /** Rooms that must be open first. */
        public final List<String> needs;
        /** Who's working here now. */
        public final List<String> working;
        public final List<String> moduleIds;
        /** Tasks it unlocks. */
        public final List<String> tasks;
        /** People on the Restore task. */
        public final int restorers;

        RoomView(String id, String name, String text, int x, int y, int w, int h, boolean open, boolean restoring,
                 double progress, String cost, int hours, boolean canRestore, List<String> needs,
                 List<String> working, List<String> moduleIds, List<String> tasks, int restorers) {
            this.id = id;
            this.name = name;
            this.text = text;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.open = open;
            this.restoring = restoring;
            this.progress = progress;
            this.cost = cost;
            this.hours = hours;
            this.canRestore = canRestore;
            this.needs = needs;
            this.working = working;
            this.moduleIds = moduleIds;
            this.tasks = tasks;
            this.restorers = restorers;
        }
    }

    /** A finished expedition whose log chip is still shown: thread and who went. */
    public static final class DoneLog {
        public final String thread;
        public final String who;

        DoneLog(String thread, String who) {
            this.thread = thread;
            this.who = who;
        }
    }

    /** Finished expeditions whose chips stay in COMMS a while longer. */
    public List<DoneLog> doneLogs = List.of();

    public static final class ExpeditionView {
        /** Log thread of this expedition. */
        public final String thread;
        public final String scoutName;
        public final String zoneName;
        /** 0–100, by planned time. */
        public final int percent;
        /** Estimated return (absolute time), so the UI needs no ticking. */
        public final long returnAtMillis;
        public final boolean waitingForOrders;
        public final boolean orderOnItsWay;

        ExpeditionView(String thread, String scoutName, String zoneName, int percent, long returnAtMillis,
                       boolean waitingForOrders, boolean orderOnItsWay) {
            this.thread = thread;
            this.scoutName = scoutName;
            this.zoneName = zoneName;
            this.percent = percent;
            this.returnAtMillis = returnAtMillis;
            this.waitingForOrders = waitingForOrders;
            this.orderOnItsWay = orderOnItsWay;
        }
    }

    public static final class ItemView {
        /** Instance id. */
        public final String id;
        public final String name;
        public final String description;
        /** Gear only: the slot it goes in (GearDef.Slot name), else null. */
        public final String slot;

        ItemView(String id, String name, String description) {
            this(id, name, description, null);
        }

        ItemView(String id, String name, String description, String slot) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.slot = slot;
        }
    }

    public static final class TaskView {
        /** Null for "no task". */
        public final String id;
        public final String name;
        public final String description;
        /** Room it's done in, or null if anywhere. */
        public final String room;

        TaskView(String id, String name, String description, String room) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.room = room;
        }
    }

    /**
     * A Diary row (GDD §6.6): a section title ({@link #section}) or an entry: a fallen scout,
     * a La Voz fragment, a decoded transmission, a known zone.
     */
    public static final class DiaryEntry {
        public final boolean section;
        public final String name;
        /** Second line ("No signal since … · zone", band, source…), or null. */
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
