package com.estatica.domain.content;

import java.util.List;
import java.util.Map;

/** Terminal texts from {@code assets/content/terminal.json}. Filled by Gson. */
public final class TerminalText {

    private List<String> boot;
    private List<List<String>> reconnect;
    private ElapsedUnits elapsedUnits;
    private String unknownCommand;
    private Map<String, List<String>> commands;
    /** Stat key to display name. */
    private Map<String, String> statNames;
    /** ScoutStatus name to label. */
    private Map<String, String> scoutStatus;
    /** Injury name to label. */
    private Map<String, String> injuries;
    private ExpeditionText expedition;
    private BunkerText bunker;
    private RadioText radio;

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

    /** System lines about the dial, decoding, relays and rescues. All are templates. */
    public static final class RadioText {
        private String staticNoise;
        private String hintLost;
        /** {delay}. */
        private String orderDelayed;
        /** {zone}, {band}. */
        private String zoneFound;
        /** {name}, {zone}. */
        private String survivorFound;
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
        private String relayBuilt;
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

        public String getRelayBuilt() {
            return relayBuilt;
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
        /** {name}, {hours}. */
        private String talkCooldown;
        /** {name}. */
        private String conversationExpired;
        /** {name}. */
        private String bondUp;
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
