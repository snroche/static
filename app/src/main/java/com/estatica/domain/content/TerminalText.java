package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/** Terminal texts from {@code assets/content/terminal.json}. Filled by Gson. */
public final class TerminalText {

    /** Weather names and the lines scouts use about it. */
    private WeatherText weather;

    public WeatherText getWeather() {
        return weather != null ? weather : new WeatherText();
    }

    public static final class WeatherText {
        /** CLEAR, CLOUDY, RAIN, FOG, STORM to a short name. */
        private Map<String, String> names;
        /** Same keys plus NIGHT: road lines. */
        private Map<String, List<String>> lines;

        public Map<String, String> getNames() {
            return names != null ? names : Map.of();
        }

        public Map<String, List<String>> getLines() {
            return lines != null ? lines : Map.of();
        }
    }

    /** Words the terminal colors: danger in red, good news in green. */
    private Highlight highlight;

    public Highlight getHighlight() {
        return highlight != null ? highlight : new Highlight();
    }

    public static final class Highlight {
        private List<String> danger;
        private List<String> good;

        public List<String> getDanger() {
            return danger != null ? danger : List.of();
        }

        public List<String> getGood() {
            return good != null ? good : List.of();
        }
    }

    /** New game: asking the operator's name. */
    private List<String> askName;
    /** {operator}: after they answer. */
    private List<String> nameSet;
    /** {operator}: renamed from the options screen. */
    private String nameChanged;
    /** Used until the operator gives a name. */
    private String defaultOperatorName;

    public List<String> getAskName() {
        return askName != null ? askName : List.of();
    }

    public List<String> getNameSet() {
        return nameSet != null ? nameSet : List.of();
    }

    public String getNameChanged() {
        return nameChanged;
    }

    public String getDefaultOperatorName() {
        return defaultOperatorName != null ? defaultOperatorName : "Operator";
    }

    private List<String> boot;
    private List<List<String>> reconnect;
    private ElapsedUnits elapsedUnits;
    private String unknownCommand;
    private Map<String, List<String>> commands;
    /** Stat key to display name. */
    private Map<String, String> statNames;
    /** Console command read-outs and errors. */
    private Map<String, String> console;

    public Map<String, String> getConsole() {
        return console != null ? console : Map.of();
    }

    /** Stat key to what it does. */
    private Map<String, String> statDescriptions;
    /** Mood key to what it means. */
    private Map<String, String> moodDescriptions;
    /** General terms (Bond, Trust, Reserve…) to their explanation. */
    private Map<String, String> glossary;

    public Map<String, String> getStatDescriptions() {
        return statDescriptions != null ? statDescriptions : Map.of();
    }

    public Map<String, String> getMoodDescriptions() {
        return moodDescriptions != null ? moodDescriptions : Map.of();
    }

    public Map<String, String> getGlossary() {
        return glossary != null ? glossary : Map.of();
    }
    /** ScoutStatus name to label. */
    private Map<String, String> scoutStatus;
    /** Injury name to label. */
    private Map<String, String> injuries;
    private ExpeditionText expedition;
    private BunkerText bunker;
    private RadioText radio;
    private BaseText base;
    private StoryText story;
    /** Mood key (content, steady, tense, breaking) to label. */
    private Map<String, String> moods;

    public Map<String, String> getMoods() {
        return moods;
    }

    public StoryText getStory() {
        return story;
    }

    public BaseText getBase() {
        return base;
    }

    public RadioText getRadio() {
        return radio;
    }

    public BunkerText getBunker() {
        return bunker;
    }

    public Map<String, String> getStatNames() {
        return statNames;
    }

    public Map<String, String> getScoutStatus() {
        return scoutStatus;
    }

    public Map<String, String> getInjuries() {
        return injuries;
    }

    public ExpeditionText getExpedition() {
        return expedition;
    }

    /** Lines shown the very first time the game starts. */
    public List<String> getBoot() {
        return boot;
    }

    /** Variants (one is picked at random) shown when the operator comes back. */
    public List<List<String>> getReconnect() {
        return reconnect;
    }

    public ElapsedUnits getElapsedUnits() {
        return elapsedUnits;
    }

    public String getUnknownCommand() {
        return unknownCommand;
    }

    /** Command word (normalized: lowercase, no accents) to response templates. */
    public Map<String, List<String>> getCommands() {
        return commands;
    }

    /** System lines around expeditions. All are templates. */
    public static final class ExpeditionText {
        /** {name}, {zone}, {eta}. */
        private String dispatched;
        /** {n}, {label}, {hint}. */
        private String optionLine;
        /** An option's label with what it does to the trip: {label}, {time}. */
        private String optionTimeMore;
        private String optionTimeLess;
        /** After it resolves: {name}, {time}. */
        private String timeLost;
        private String timeSaved;

        public String getOptionTimeMore() {
            return optionTimeMore;
        }

        public String getOptionTimeLess() {
            return optionTimeLess;
        }

        public String getTimeLost() {
            return timeLost;
        }

        public String getTimeSaved() {
            return timeSaved;
        }
        /** {name}. */
        private String awaitingOrders;
        /** {name}, {stat}, {value}. */
        private String statUp;
        /** {name}, {loot}. */
        private String returnedWithLoot;
        /** {name}. */
        private String returnedEmpty;
        /** {amount}, {resource}. */
        private String lootItem;
        private String lootSeparator;
        private String signalLost;
        /** Decision card: "GOOD", "UNSURE", "BAD", "LOST" to a short label. */
        private java.util.Map<String, String> hintLabels;

        public java.util.Map<String, String> getHintLabels() {
            return hintLabels != null ? hintLabels : java.util.Map.of();
        }

        /** System summaries. {who}, {zone}. */
        private String summaryDeparted;
        /** {who}, {zone}. */
        private String summaryOrders;
        /** {who}, {zone}, {loot}. */
        private String summaryReturned;
        /** {who}, {zone}. */
        private String summaryReturnedEmpty;
        /** {name}, {injury}: appended per hurt scout. */
        private String summaryHurt;
        /** {name}, {zone}. */
        private String summaryDied;
        /** {who}. */
        private String summaryRecalled;

        public String getSummaryRecalled() {
            return summaryRecalled;
        }

        public String getSummaryDeparted() {
            return summaryDeparted;
        }

        public String getSummaryOrders() {
            return summaryOrders;
        }

        public String getSummaryReturned() {
            return summaryReturned;
        }

        public String getSummaryReturnedEmpty() {
            return summaryReturnedEmpty;
        }

        public String getSummaryHurt() {
            return summaryHurt;
        }

        public String getSummaryDied() {
            return summaryDied;
        }

        /** {name} joins {other} (pairs). */
        private String pairJoins;
        /** {name} carried {other} home: the carried one's memory. */
        private String carriedMemory;

        public String getPairJoins() {
            return pairJoins;
        }

        public String getCarriedMemory() {
            return carriedMemory;
        }
        /** {name}. */
        private String deathNotice;
        /** Notification text when a scout gets back. {name}. */
        private String notifyReturned;

        public String getDispatched() {
            return dispatched;
        }

        public String getOptionLine() {
            return optionLine;
        }

        public String getAwaitingOrders() {
            return awaitingOrders;
        }

        public String getStatUp() {
            return statUp;
        }

        public String getReturnedWithLoot() {
            return returnedWithLoot;
        }

        public String getReturnedEmpty() {
            return returnedEmpty;
        }

        public String getLootItem() {
            return lootItem;
        }

        public String getLootSeparator() {
            return lootSeparator;
        }

        public String getSignalLost() {
            return signalLost;
        }

        public String getDeathNotice() {
            return deathNotice;
        }

        public String getNotifyReturned() {
            return notifyReturned;
        }
    }

    /** Personal missions, La Voz, relationships, goals and settings. All are templates. */
    public static final class StoryText {
        /** {name}, {title}. */
        private String personalAvailable;
        /** {name}, {title}. */
        private String personalDone;
        /** {title}. */
        private String missionPersonal;
        /** {title}, {found}, {total}. */
        private String plotFound;
        private String voiceSpeaker;
        /** {name}, {other}. */
        private String friends;
        private String inseparable;
        private String rivals;
        /** {label}. */
        private String goalDone;
        /** {loot}. */
        private String goalReward;
        private String soundOn;
        private String soundOff;
        private String notificationsOn;
        private String notificationsOff;
        private String diaryFallen;
        private String diaryVoice;
        private String diaryLore;
        private String diaryZones;
        private String diaryUnknownFragment;

        public String getPersonalAvailable() {
            return personalAvailable;
        }

        public String getPersonalDone() {
            return personalDone;
        }

        public String getMissionPersonal() {
            return missionPersonal;
        }

        public String getPlotFound() {
            return plotFound;
        }

        public String getVoiceSpeaker() {
            return voiceSpeaker;
        }

        public String getFriends() {
            return friends;
        }

        public String getInseparable() {
            return inseparable;
        }

        public String getRivals() {
            return rivals;
        }

        public String getGoalDone() {
            return goalDone;
        }

        public String getGoalReward() {
            return goalReward;
        }

        public String getSoundOn() {
            return soundOn;
        }

        public String getSoundOff() {
            return soundOff;
        }

        public String getNotificationsOn() {
            return notificationsOn;
        }

        public String getNotificationsOff() {
            return notificationsOff;
        }

        public String getDiaryFallen() {
            return diaryFallen;
        }

        public String getDiaryVoice() {
            return diaryVoice;
        }

        public String getDiaryLore() {
            return diaryLore;
        }

        public String getDiaryZones() {
            return diaryZones;
        }

        public String getDiaryUnknownFragment() {
            return diaryUnknownFragment;
        }
    }

    /** System lines about energy, modules, the workshop and raids. All are templates. */
    public static final class BaseText {
        /** {watts}, {hours}. */
        private String blackout;
        /** {consumer}. */
        private String powerCut;
        private String badAir;
        /** {guards}. */
        private String raidRepelled;
        /** {loot}. */
        private String raidLost;
        private String raidUnguarded;
        /** {name}. */
        private String raidInjured;
        /** {item}. */
        private String craftStarted;
        /** {item}. */
        private String craftDone;
        private String craftNoSlot;
        /** {name}, {gear}. */
        private String equipped;
        /** {name}. */
        private String unequipped;
        /** {name}, {gear}. */
        private String gearSaved;
        /** {name}, {gear}. */
        private String gearFound;
        /** {name}, {gear}, {condition}. */
        private String gearWorn;
        /** {name}, {gear}. */
        private String gearBroken;
        /** {gear}. */
        private String gearRepaired;
        /** {name}, {dead}, {gear}. */
        private String gearRecovered;
        /** {gear}, {rarity}, {condition}. */
        private String gearLabel;
        /** {gear}, {rarity}. */
        private String gearLabelBroken;
        /** {name}, {direction}, {band}. */
        private String mapScouted;
        /** {name}, {zone}. */
        private String mapFoundZone;
        /** {name}. */
        private String mapNothing;
        /** {direction}, {band}. */
        private String mapHeard;
        /** North first, clockwise: eight names. */
        private java.util.List<String> directions;

        public String getMapScouted() {
            return mapScouted;
        }

        public String getMapFoundZone() {
            return mapFoundZone;
        }

        public String getMapNothing() {
            return mapNothing;
        }

        public String getMapHeard() {
            return mapHeard;
        }

        public java.util.List<String> getDirections() {
            return directions != null ? directions : java.util.List.of();
        }

        /** {name} (who brought it, may be empty), {item}. */
        private String keyItemFound;

        public String getKeyItemFound() {
            return keyItemFound;
        }

        /** GearDef.Slot name to display name ("Head"). */
        private java.util.Map<String, String> slotNames;

        public java.util.Map<String, String> getSlotNames() {
            return slotNames != null ? slotNames : java.util.Map.of();
        }

        /** COMMON, UNCOMMON, RARE to display name. */
        private java.util.Map<String, String> rarities;

        public String getGearFound() {
            return gearFound;
        }

        /** {who}, {ride}, {fuel}. */
        private String fuelUsed;
        private String noFuel;
        /** {name}, {stat}, {tier}. */
        private String retrained;
        /** {fuel}, {hours}. */
        private String generatorFuel;
        private String generatorDry;
        /** Mara and her old bike. */
        private String maraBike;

        public String getFuelUsed() {
            return fuelUsed;
        }

        public String getNoFuel() {
            return noFuel;
        }

        public String getRetrained() {
            return retrained;
        }

        public String getGeneratorFuel() {
            return generatorFuel;
        }

        public String getGeneratorDry() {
            return generatorDry;
        }

        public String getMaraBike() {
            return maraBike;
        }

        public String getGearWorn() {
            return gearWorn;
        }

        public String getGearBroken() {
            return gearBroken;
        }

        public String getGearRepaired() {
            return gearRepaired;
        }

        public String getGearRecovered() {
            return gearRecovered;
        }

        public String getGearLabel() {
            return gearLabel;
        }

        public String getGearLabelBroken() {
            return gearLabelBroken;
        }

        public java.util.Map<String, String> getRarities() {
            return rarities != null ? rarities : java.util.Map.of();
        }
        private String reserveLow;
        private String reserveRecharged;
        private String nobody;

        public String getBlackout() {
            return blackout;
        }

        public String getPowerCut() {
            return powerCut;
        }

        public String getBadAir() {
            return badAir;
        }

        public String getRaidRepelled() {
            return raidRepelled;
        }

        public String getRaidLost() {
            return raidLost;
        }

        public String getRaidUnguarded() {
            return raidUnguarded;
        }

        public String getRaidInjured() {
            return raidInjured;
        }

        public String getCraftStarted() {
            return craftStarted;
        }

        public String getCraftDone() {
            return craftDone;
        }

        public String getCraftNoSlot() {
            return craftNoSlot;
        }

        public String getEquipped() {
            return equipped;
        }

        public String getUnequipped() {
            return unequipped;
        }

        public String getGearSaved() {
            return gearSaved;
        }

        public String getReserveLow() {
            return reserveLow;
        }

        public String getReserveRecharged() {
            return reserveRecharged;
        }

        public String getNobody() {
            return nobody;
        }
    }

    /** System lines about the dial, decoding, relays and rescues. All are templates. */
    public static final class RadioText {
        /** Locked onto a station: {frequency}, {station}, {line}. */
        private String stationHeard;
        /** Something creepy came in and the crew heard it. */
        private String crewUneasy;
        /** The radio assistant caught a bulletin: {name}, {station}, {time}, {line}. */
        private String assistantBulletin;
        /** Station schedules: {time}, {length}, {hours}. */
        private String scheduleAlways;
        private String scheduleDaily;
        private String scheduleEvery;
        private String scheduleNight;

        public String getAssistantBulletin() {
            return assistantBulletin;
        }

        public String getScheduleAlways() {
            return scheduleAlways;
        }

        public String getScheduleDaily() {
            return scheduleDaily;
        }

        public String getScheduleEvery() {
            return scheduleEvery;
        }

        public String getScheduleNight() {
            return scheduleNight;
        }

        public String getStationHeard() {
            return stationHeard;
        }

        public String getCrewUneasy() {
            return crewUneasy;
        }

        private String staticNoise;
        private String hintLost;
        /** {delay}. */
        private String orderDelayed;
        /** {zone}, {band}. */
        private String zoneFound;
        /** {name}, {zone}. */
        private String survivorFound;
        /** Transmission.Kind name to what a locked one looks like and what might help. */
        private Map<String, String> lockedHints;
        /** Transmission.Kind name to its display name. */
        private Map<String, String> schemeNames;

        public Map<String, String> getLockedHints() {
            return lockedHints != null ? lockedHints : Map.of();
        }

        public Map<String, String> getSchemeNames() {
            return schemeNames != null ? schemeNames : Map.of();
        }
        private String transmissionFound;
        private String nothingThere;
        private String alreadyCaught;
        private String decoded;
        private String decodeWrong;
        /** {zone}. */
        private String intelCache;
        /** {zone}. */
        private String intelDanger;
        private String loreFound;
        /** {loot}. */
        private String resourcesFound;
        private String morseTableUnlocked;
        /** {name}. */
        private String rescued;
        /** {name}. */
        private String rescueFailed;
        private String rosterFull;
        /** {zone}. */
        private String relayInstalled;
        /** {zone}. */
        private String relayMaintained;
        /** {zone}. */
        private String relayFailing;
        /** {module}, {level}. */
        private String moduleUpgraded;
        /** {cost}. */
        private String cantAfford;
        private String assistantCaught;
        /** {name}, {zone}. */
        private String missionRescue;
        /** {zone}. */
        private String missionInstall;
        /** {zone}. */
        private String missionMaintain;

        public String getStaticNoise() {
            return staticNoise;
        }

        public String getHintLost() {
            return hintLost;
        }

        public String getOrderDelayed() {
            return orderDelayed;
        }

        public String getZoneFound() {
            return zoneFound;
        }

        public String getSurvivorFound() {
            return survivorFound;
        }

        public String getTransmissionFound() {
            return transmissionFound;
        }

        public String getNothingThere() {
            return nothingThere;
        }

        public String getAlreadyCaught() {
            return alreadyCaught;
        }

        public String getDecoded() {
            return decoded;
        }

        public String getDecodeWrong() {
            return decodeWrong;
        }

        public String getIntelCache() {
            return intelCache;
        }

        public String getIntelDanger() {
            return intelDanger;
        }

        public String getLoreFound() {
            return loreFound;
        }

        public String getResourcesFound() {
            return resourcesFound;
        }

        public String getMorseTableUnlocked() {
            return morseTableUnlocked;
        }

        public String getRescued() {
            return rescued;
        }

        public String getRescueFailed() {
            return rescueFailed;
        }

        public String getRosterFull() {
            return rosterFull;
        }

        public String getRelayInstalled() {
            return relayInstalled;
        }

        public String getRelayMaintained() {
            return relayMaintained;
        }

        public String getRelayFailing() {
            return relayFailing;
        }

        public String getModuleUpgraded() {
            return moduleUpgraded;
        }

        public String getCantAfford() {
            return cantAfford;
        }

        public String getAssistantCaught() {
            return assistantCaught;
        }

        public String getMissionRescue() {
            return missionRescue;
        }

        public String getMissionInstall() {
            return missionInstall;
        }

        public String getMissionMaintain() {
            return missionMaintain;
        }
    }

    /** System lines about life in the bunker. All are templates. */
    public static final class BunkerText {
        private String retreatLabel;
        /** {days}. */
        private String foodLow;
        private String foodOut;
        private String foodBack;

        public String getFoodLow() {
            return foodLow;
        }

        public String getFoodOut() {
            return foodOut;
        }

        public String getFoodBack() {
            return foodBack;
        }
        /** {name}, {hours}. */
        private String talkCooldown;
        /** {name}. */
        private String conversationExpired;
        /** {name}. */
        private String bondUp;
        /** Romance: {name}. */
        private String romanceSpark;
        private String romanceStart;
        private String romanceEndYou;
        private String romanceEndThem;
        private String romanceLost;
        /** Said in the bunker chat by a jealous partner: {name}, {other}. */
        private List<String> jealousLines;
        /** On the crew card: {stage}. */
        private Map<String, String> romanceStages;

        public String getRomanceSpark() {
            return romanceSpark;
        }

        public String getRomanceStart() {
            return romanceStart;
        }

        public String getRomanceEndYou() {
            return romanceEndYou;
        }

        public String getRomanceEndThem() {
            return romanceEndThem;
        }

        public String getRomanceLost() {
            return romanceLost;
        }

        public List<String> getJealousLines() {
            return jealousLines != null ? jealousLines : List.of();
        }

        public Map<String, String> getRomanceStages() {
            return romanceStages != null ? romanceStages : Map.of();
        }

        /** Rooms: {room}; {name} for the lookout. */
        private String roomStarted;
        private String roomDone;
        private String blight;
        private String lookoutSaw;

        public String getRoomStarted() {
            return roomStarted;
        }

        public String getRoomDone() {
            return roomDone;
        }

        public String getBlight() {
            return blight;
        }

        public String getLookoutSaw() {
            return lookoutSaw;
        }

        /** Treatment: {healer}, {name}, {injury}, {cost}. */
        private String treatAsk;
        private String treatYes;
        private String treatNo;
        private String treatDone;
        private String treatEager;
        private String treatNoMedicine;

        public String getTreatAsk() {
            return treatAsk;
        }

        public String getTreatYes() {
            return treatYes;
        }

        public String getTreatNo() {
            return treatNo;
        }

        public String getTreatDone() {
            return treatDone;
        }

        public String getTreatEager() {
            return treatEager;
        }

        public String getTreatNoMedicine() {
            return treatNoMedicine;
        }
        /** Bond went up without a new level: {name}, {progress}, {level}. */
        private String bondWarmer;
        /** Bond went down: {name}, {progress}, {level}. */
        private String bondCooler;
        /** {name}, {trait}. */
        private String traitRevealed;
        /** {name}, {trait}. */
        private String traitGained;
        /** {name}, {task}. */
        private String taskAssigned;
        private String noTask;
        /** {amount}, {resource}. */
        private String workshopOutput;
        /** {name}, {dead}. */
        private String griefNotice;
        /** {name}, {item}. */
        private String itemFound;
        /** {name}, {item}, {dead}. */
        private String itemRecovered;
        /** Memory text for a recovered belonging: {item}, {dead}. */
        private String recoveredMemory;

        public String getRecoveredMemory() {
            return recoveredMemory;
        }
        /** {name}, {item}. */
        private String giftGiven;
        /** {zone}, {date}. */
        private String diaryFell;
        private String diaryLastWords;
        private String diaryEmpty;
        /** Shown on the scout card once discovered: {items}. */
        private String knownLikes;
        private String knownDislikes;

        public String getRetreatLabel() {
            return retreatLabel;
        }

        public String getTalkCooldown() {
            return talkCooldown;
        }

        public String getConversationExpired() {
            return conversationExpired;
        }

        public String getBondUp() {
            return bondUp;
        }

        public String getBondWarmer() {
            return bondWarmer;
        }

        public String getBondCooler() {
            return bondCooler;
        }

        public String getTraitRevealed() {
            return traitRevealed;
        }

        public String getTraitGained() {
            return traitGained;
        }

        public String getTaskAssigned() {
            return taskAssigned;
        }

        public String getNoTask() {
            return noTask;
        }

        public String getWorkshopOutput() {
            return workshopOutput;
        }

        public String getGriefNotice() {
            return griefNotice;
        }

        public String getItemFound() {
            return itemFound;
        }

        public String getItemRecovered() {
            return itemRecovered;
        }

        public String getGiftGiven() {
            return giftGiven;
        }

        public String getDiaryFell() {
            return diaryFell;
        }

        public String getDiaryLastWords() {
            return diaryLastWords;
        }

        public String getDiaryEmpty() {
            return diaryEmpty;
        }

        public String getKnownLikes() {
            return knownLikes;
        }

        public String getKnownDislikes() {
            return knownDislikes;
        }
    }

    public static final class ElapsedUnits {
        private String day;
        private String hour;
        private String minute;
        private String lessThanMinute;

        public ElapsedUnits() {
        }

        public ElapsedUnits(String day, String hour, String minute, String lessThanMinute) {
            this.day = day;
            this.hour = hour;
            this.minute = minute;
            this.lessThanMinute = lessThanMinute;
        }

        public String getDay() {
            return day;
        }

        public String getHour() {
            return hour;
        }

        public String getMinute() {
            return minute;
        }

        public String getLessThanMinute() {
            return lessThanMinute;
        }
    }
}
