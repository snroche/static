# Changelog

All notable changes to Static are recorded here.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows [docs/VERSIONING.md](docs/VERSIONING.md).

When they change, each entry lists the three independent versions:
**App** (`version.properties`) · **DB** (Room schema) · **Content** (`balance.json → contentVersion`).

## [Unreleased]

_Nothing yet._

## [0.8.2] — A cleaner map, popups for trades and incoming codes — in verification

App 0.8.2 (versionCode 802) · DB 14 (no change) · Content 28 (no change)

### Added
- **Incoming codes open a popup** with what they brought, or why nothing came through. The log still has it all.
- **Trades open in their own popup.** NEARBY shows what they're offering and whether they've agreed; OPEN TRADE has the rest.
- **LINK shows a count** of wanderers waiting on the road (◉ while linked to a bunker nearby).
- **Sounds and touch:** a short data chirp when a code is made or taken, and a chirp and a buzz when a nearby link comes up.

### Changed
- **The region map is laid out cell by cell.** Each place sits in the middle of its sector, icon over name; several in one cell spread across it. Ring names sit on the ring borders, and names are drawn on a patch of background so lines never cross them. Taps match what's drawn.
- **Smaller drop and trade editors:** each good is one row with its icon, how many of how many, and − / + (hold for five), instead of four buttons under each.

## [0.8.1] — A tidier radio, a code popup, rewards for cracking codes — in verification

App 0.8.1 (versionCode 801) · DB 14 (no change) · **Content 27 → 28**

### Added
- **Cracking another bunker's message pays.** Under it are coordinates to a stash they left: up to 2 scrap for Caesar, rising through Morse, Atbash and tap code to Base64 (up to 3 components, 2 batteries and 2 parts). Plain messages give nothing.
- **Relays on the map.** A small mark next to each place with a relay, its condition in the place's details, and a line under the map with every relay and the kits ready.

### Changed
- **The radio has five sections, each with an icon over its name:** DIAL, STATIONS, DECODE, TRADE and LINK. RELAYS moved to the map, where the places they serve are.
- **A code opens in its own popup:** black, framed in amber, with rounded corners. It has three tabs: TEXT (copy or share), QR (dark on amber, so any scanner reads it) and NEARBY (send it over the Bluetooth link, or go set one up).
- **Icons on LINK:** send, receive, nearby, the four kinds of code, paste, scan, host and find.
- Eight new icons in the terminal font: QR, scan, Bluetooth, copy, paste, share, close and text.

### Fixed
- **Map icons, "?" marks and labels** are centered on their place again. Icons were drawn from a guessed baseline that doesn't fit the icon font, so some sat off-center; the selection ring now circles the mark it belongs to.

## [0.8.0] — Other bunkers: codes, QR and Bluetooth — in verification

App 0.8.0 (versionCode 800) · **DB 13 → 14** · **Content 26 → 27**

### Added
- **Other bunkers, with no server (RADIO → LINK).** Your bunker has a callsign, like LU4QXR. What you send travels as a code: text in groups of five, a QR, or Bluetooth.
  - **Messages:** in the clear, or in Caesar, Morse or any scheme whose key item you hold. A ciphered one arrives as a transmission to decode, signed with the sender's callsign.
  - **Drops:** up to 40 units of supplies and 2 pieces of gear. They leave your stores when packed, so they can't be sent twice. They arrive at the other bunker's hatch.
  - **Wanderers:** someone from your crew, as others will meet them. They wait about 3 days at the farthest place the other bunker can reach. The team coming back from there meets them and gets their words, a lighter heart, and a gift or a tip that depends on their best stat. They stay home with you, and nobody gets recruited.
  - **Last words:** one of your fallen, heard once on another bunker's dial and kept in their Diary.
  - **Broadcasts:** signed codes from the developers for events, made with `tools/link_broadcast.sh`.
  - **Taking codes:** type, paste or scan them. Up to 3 codes from players a day; broadcasts and codes sent over Bluetooth don't count. Your own codes and codes already taken are refused.
  - **Codes sent lately** can be shown again, to copy, share, show as a QR or send over Bluetooth.
- **Nearby, over Bluetooth.** Two phones in the same room: one hosts, the other finds it.
  - Codes go across without typing.
  - **Trade:** each side puts up an offer. When both agree to the same pair, they swap.
  - **Joint run:** each side picks a place in their own world and someone from the crew. Each bunker gets a cache and the dangers called out on that route, and the other's scout waits there.
- `tools/link_broadcast.sh` signs broadcasts. The private key lives in `D:\Android\keys\link_broadcast.pk8`, outside the repo; `tools/broadcasts/example.properties` shows the format.

### Changed
- **The radio has a sixth section, LINK.**
- **New permissions:** Bluetooth (for nearby links) and the camera (for scanning QR codes). Both are asked for only when used.
- **New dependency:** `zxing-android-embedded` 4.3.0, for showing and scanning QR codes.

### Technical
- DB 14: a `link_signals` table (codes taken, sent, and wanderers waiting), with its migration.
- `domain/link`: the code format (deflate, salted CRC32 or an ECDSA P-256 signature, Crockford base32), the limits, and the callsign. All pure Java, with tests.

## [0.7.2] — Map labels, steadier economy, room upgrades — in verification

App 0.7.2 (versionCode 702) · DB 13 (no change) · **Content 25 → 26**

### Added
- **Upgrades for the rooms you restore.** Each one builds on what the room already does:
  - Lookout: watchtower, up to +6 defense with someone on lookout.
  - School: library, teaching up to 50% faster.
  - Water recycler: purifier, food lasts longer.
  - Music room: record player, everyone at home slowly sheds stress.
  - Hydroponics bay: grow lights, up to 60% more food on a slightly bigger share.
  - Archive: card index, plans worked out at the bench more often.
  - Kitchen: stove, food lasts longer.
- **More to decode:**
  - Repeatable transmissions now come in three or four wordings each.
  - Twelve new ones across all bands: caches, warnings, scrap piles, fuel, parts.
- **More to hear while tuning:**
  - Two to six new lines for each kind of signal.
  - Ten more rare finds: a wedding, a birthday, a lost dog, a radio play, whale song on VLF and more.
- **Each station has its own voice:** pitched and paced its own way, so two voices on the dial no longer sound identical.
- `tools/font_subset.py` rebuilds the terminal font with new icons. The full source fonts live in `D:\Android\fonts`.

### Changed
- **Map:**
  - Names go under each icon when there's room.
  - Long names are cut to 7 characters ("Hosp.", "Pharm.").
  - The legend shows the bunker as the dot drawn on the map.
- **Resources:**
  - **The Workshop and Sorting tasks** no longer eat all the scrap. Each leaves 20 alone, and they stop at 40 components and 30 parts. Workshop stripping is also slower: 3 scrap → 1 component every 4 h.
  - **Events:** about half the components they gave are now scrap, the rest give 30% fewer. Medicine and batteries are 30% lower, and scrap slightly higher.
  - **Places:** scrap turns up more often and in bigger piles. Parts are now found at the police station, the train station and the gated community.
- **Signals on the dial** each get their own stretch of it, so they never sit on top of each other.

### Fixed
- **The Fair's map icon** was missing from the font.
- **Morse and tap code** wrap a whole letter at a time: a letter like ".-" no longer splits across two lines.

## [0.7.1] — Gear that lasts, the Fair, more radio, chapter 2 — in verification

App 0.7.1 (versionCode 701) · DB 13 (no change) · **Content 24 → 25**

### Added
- **The Voice, chapter 2: "Echo-7".**
  - It opens once chapter 1 is complete. Its fragments never turn up before that.
  - Six fragments: three on the dial (medium, long and deep band), one from an old engineer at the Fair, one in the dam's control room, one in the mountain observatory.
  - Every fragment can be found by any crew; a new test checks this for all chapters.
- **Twelve more pieces of gear:**
  - Headlamp, gas mask, binoculars, compass, leather jacket, rain poncho, running shoes, steel-toe boots, messenger bag, hiking pack, multitool, canteen and crowbar.
  - The gas mask, rain poncho and canteen also take some of the stress out there.
  - Eight new blueprints build some of them.
- **Gear shows what it actually does at its rarity**, for example "Wits +2 · +38% loot · trips 13% shorter", in the gear picker, on the person's Gear tab and in the workshop.
- **Six new stations, three with new synthesized music:**
  - LATE PIANO: an upright piano at night playing Für Elise.
  - THE PORCH: guitar and Greensleeves at 17:00.
  - THE SOCIAL CLUB: a slow tango of our own on accordion at 20:00.
  - BEDTIME STORIES: a lighthouse story at 22:00 that never reaches its end.
  - THE MATCH: an old football final narrated from memory at 16:00.
  - BREAKER NINE: a trucker on the long band.
- **More to hear on the old stations:** two to four new lines on every one.

### Changed
- **Gear wears by what happens to it**, instead of a flat 15 per trip:
  - Time out there wears it: 1.5 an hour, so a three-minute run barely touches it and a long trip costs about 6.
  - A failed check scuffs it (2).
  - An injury hits it harder: 5, 10 or 15 by severity.
  - Uncommon pieces wear 25% slower, rare ones 50% slower.
- **Rarity matters for every piece:**
  - Stat bonuses go up a point per rarity (+1, +2, +3).
  - Speed, loot, signal and stress bonuses grow with rarity.
  - A rare first-aid kit takes two levels off an injury, and uncommon or rare kits may survive the use.
- **La Feria is now the Fair.** Spanish and Argentine touches stay as small nods (mate, a truck called La Tortuga) instead of being everywhere.
- **The Fair's hours:** on the air 06:00–noon and 18:00–midnight, half the day, with more chatter.
- **Top bar:**
  - The days of rations left now sit on the first line, next to the weather.
  - The info sheet only explains what's on the bar.
  - Relay kits left the bar; their count lives in RADIO → RELAYS.

## [0.7.0] — THE FAIR and the workshop — in verification

App 0.7.0 (versionCode 700) · DB 13 (no change) · **Content 23 → 24**

Phase 5 (content and polish) is closed; 0.7 is the economy. The version table in docs/VERSIONING.md now plans 0.7 to 0.9 before the 1.0 launch.

### Added
- **THE FAIR:** a market in the old fairground, with its own station on the short band (96.40 MHz).
  - **Hours:** on the air 09:00–12:00 and 21:00–midnight. Hearing it once puts it on the map, and opens **RADIO → TRADE**.
  - **Chips:** the currency. You get them by selling, and now and then on expeditions (the gated community, the police station, the hospital, the train station, the airport).
  - **Buying:** goods, plus each day's offers: two blueprints you don't have, and two pieces of gear. Rare gear only shows up once you have a standing of 70.
    - *Courier:* +15%, arrives in about 4 hours.
    - *Pickup:* free; it waits at the Fair until someone goes there and comes back with it.
  - **Selling:** goods and spare gear. Worn gear fetches less.
  - **Prices move:**
    - Each day the trader pays 1.5× for two goods and charges 1.5× for one it's short of.
    - Selling a lot of one thing in a day drives its price down.
    - Standing (0–100, up a point per trade) moves every price by up to 10%.
  - **The place itself:** three situations of its own (haggling, a pickpocket, being followed on the way out). Stray radio signals never reveal it; only its station does.
- **Workshop rework:**
  - **Someone has to be at the bench.** Nothing gets made without a person on the Workshop task. The best Craft there sets the speed (×0.6 plus 0.08 per point), the quality, and what can be made at all. They train Craft while working.
  - **Craft requirements:** every recipe needs a minimum. Bicycle 3, padded vest and helmet 4, motorbike and portable radio 6, pickup truck 8.
  - **Blueprints:** only relay kits and parts are known from the start. The rest turn up three ways:
    - in a drawer on an expedition (more often farther out);
    - bought at THE FAIR;
    - worked out at the bench by whoever is there and good enough (about one in five days).
  - **A queue:** three jobs can wait in line beyond the benches, so a long absence still gets work done.
  - **Salvage:** break unwanted gear into scrap, components and parts. Better and bigger pieces give more back; it takes two taps.
  - **Upgrade:** raise a piece one rarity with parts and components. Common to uncommon needs Craft 5 at the bench, uncommon to rare needs Craft 7.
  - The workshop shows who's at the bench, their Craft and how many plans you have. The craft list shows what each recipe needs and how many plans are still out there.
- Seven more icons (chips, trading, plans, salvage, upgrade, courier, package).

### Changed
- **Recipes from older saves:** they're hidden again until found, since the workshop had barely been used.
- **The Workshop task** now says it's the bench and what Craft does there.

## [0.6.15] — Teams of three, rides and fuel, retraining, deep runs — in verification

App 0.6.15 (versionCode 615) · **DB 12 → 13** · **Content 22 → 23**

### Added
- **Teams of three.** Send out picks up to three people, the first one leading.
  - Situations can be handled by whoever on the team fits.
  - The best hand on the team helps with each check, and how each one gets on with the lead counts.
  - Loot ×1.7 for three (×1.4 for two).
  - The strongest one still standing tries to carry a dying lead home; with two to carry it, the odds are better.
  - Everyone who comes back together grows closer.
- **Rides:** a new gear slot.
  - **Bicycle:** one seat, trips 25% shorter, no fuel.
  - **Motorbike:** two seats, trips half as long, burns 1 fuel an hour.
  - **Pickup truck:** three seats, trips 40% shorter, brings 40% more home, burns 2 fuel an hour.
  - **Seats:** rides only count if they seat the whole team. Otherwise everyone walks at the slowest pace.
  - **Fuel:** engines need it for the whole trip, paid on leaving. Without enough, they stay parked and the team goes on foot. The send screen shows the fuel each trip needs, and the times account for it.
  - **Building and repairs:** rides are built in the workshop and never turn up at random. They wear like other gear, but repairs cost more.
  - **Mara** finds her old delivery bike in storage, once.
- **Fuel:** a new resource. Found at the gas station, the police station, the gated community, the airport, the dam, and out in the suburbs and the wasteland.
  - BUNKER → POWER: *Burn fuel in blackouts* lets the generator cover the lost watts, at 1 fuel per blackout hour, until the tank runs dry.
- **Retraining:** once every stat is at its ceiling, a person can retrain from the Stats tab.
  - Every stat drops to half, every limit goes up by 1, and the one you focus on by 2. Then they grow again.
  - Each retraining makes them a veteran one step further. Far-off places (long band and beyond) push veterans harder, so events there stay a challenge.
- **Deep runs:** past the map's edge, on VLF.
  - **Antenna:** two new levels, 7 and 8.
  - **Places:** three, none of them on the map: the drowned city, the salt flats and the mountain observatory. Each has its own situations, plus six shared ones.
  - **Never run dry:** pick a tier instead.
  - **Tier I:** opens at antenna 7.
  - **Tier II:** also at 7. Trips 50% longer, 60% more loot, harder checks.
  - **Tier III:** needs antenna 8. Twice as long, double the loot, harder still.

### Changed
- On foot, only boots and traits change someone's pace; rides count per team.
- The antenna's description lists the new levels.

## [0.6.14] — Person pages, two-step dispatch, real frequencies — in verification

App 0.6.14 (versionCode 614) · DB 12 (no change) · Content 22 (no change)

### Added
- **A person's page, in tabs.** On top, always: name with ♂/♀ and the current task (or where they are). The expand arrow is gone. Below, four tabs:
  - **Stats:** stress, bond and trust as full bars, each with what it means. Bond is notched per level and shows the way to the next one. Then every stat and the traits.
  - **File:** likes and dislikes, what you've noticed, how they get on with the others, what they carry, and the bio.
  - **Gear:** one outlined box per slot with its icon; empty slots are dashed. Tap one to change it.
  - **Orders:** send out, call back, talk, set task, give a gift.
- **Tasks on the floor plan.** Setting a task shows the bunker: tap a room and pick one of its tasks, or one that can be done anywhere. The current one is marked. Rest and training now happen in the bunks.
- **Sending out, in two steps.** You can switch between them freely before sending.
  - **Who goes:** crew tiles. Tap to pick, long-press to make leader (marked with a crown). Each tile shows how the person gets on with the leader, whether they're hurt, and whether they're fast or slow. People who can't go are shown dimmed.
  - **Where:** one block per place with the **exact trip time** for the team you've picked (events aside), danger, and how picked over it is (%). The band name is gone.
  - **Sort** by danger (default), by % picked (least first) or by trip time. The choice is remembered.
  - **Order:** missions come first. Rescues and personal missions are framed in blue and shown even when the antenna can't reach them yet. Then all places, then the ones picked clean (over 95%).
  - **Personal missions:** tapping one makes its owner the leader.
- **Tap code:** the current letter is lit and underlined, like in Morse. The space key fills itself in at the end of a word.
- Twenty-three more icons in the terminal font (tabs, leader, trip time, bunks, training, and vehicles and fuel for the next update). The font grew ~4 KB.

### Changed
- **Real frequencies on the dial:**

  | Band | Range |
  |---|---|
  | Short | FM, 88–108 MHz |
  | Medium | AM medium wave, 530–1700 kHz |
  | Long | AM long wave, 150–280 kHz |
  | Deep | VLF, 3–30 kHz |

  Stations keep their place on the dial, and their descriptions quote the new numbers.
- **Trip length is fixed:** the middle of each place's range, at the team's pace. Before, it was a random roll inside that range. Situations still add time.
- **People come to talk much less often.** At most one check an hour, a 15% chance, at least 6 hours between any two, and 12 hours before the same person asks again. Coming back after a while finds one person waiting, not the whole crew. Grief and requests for advice still come when they need to.

### Fixed
- **Tap code** was missing its erase key: SPACE filled the whole row and pushed ⌫ off the screen.

## [0.6.13] — Rooms, room tasks — in verification

App 0.6.13 (versionCode 613) · DB 12 (no change) · **Content 21 → 22**

### Added
- **Bunker rooms:**
  - BUNKER opens on a floor plan (ROOMS section). Open rooms show who's working there. Sealed ones are hatched; tap one to see what's in the way and what it would open.
  - **Restoring** a room: pay its resources, then people on the new **Restore** task put in the work hours.
  - Module upgrades now live in their room: the radio and antenna in the radio room, the battery bank in the generator room, barricades at the hatch, and so on.
  - Sealed rooms: storage (the pantry), lookout, sorting corner, school corner, music room, water recycler, hydroponics bay, archive.
- **Room tasks:**
  - **Lookout:** +4 defense against raids, and a line when they're spotted.
  - **Sorting:** 4 scrap into 1 part every 3 h.
  - **Teaching:** everyone at home slowly trains their weakest stat.
  - **Music:** stress relief for the whole bunker.
  - **Hydroponics** (late game: needs the water recycler and storage):
    - A little food every hour, never more than a quarter of what the crew eats.
    - Uses reserve for the lamps, and blight stops it for a day now and then.
  - Existing tasks need their room open. All of those rooms are open from the start.
***REMOVED***
***REMOVED***
- **Options → [ RESET ALL DATA ]** wipes every save and setting after a confirmation popup; the game closes and starts from zero next time.

### Changed
- "Teenager on their own" is now "Teenager, alone". Trait, glossary and task texts no longer use "they" for one person.

### Fixed
- The radio's STATIONS tab (and any tab whose label shrinks to fit) no longer sits lower than the others.

## [0.6.12] — Smoother radio, more sounds, more to build — in verification

App 0.6.12 (versionCode 612) · DB 12 (no change) · **Content 20 → 21**

### Added
- **Seven new radio sounds,** all synthesized: a woman's voice, a child, a preacher in an echoing hall, a choir, a church organ, a music box waltz, and a numbers-station reader with a chime between groups. Stations, rare finds and numbers transmissions use them.
- **Two new stations:**
  - The Chapel of San Roque: organ, every morning 08:00–08:45.
  - A music box that only plays at night, and someone winds it again every time it stops.
- **Batteries from the radio:** the DIAL section shows the reserve and battery count, with a [ +1 BATTERY ] button.
- **Three new bunker upgrades:**
  - **Battery bank:** +8 reserve per level, and each battery charges 1 more per level.
  - **Pantry:** 6% less food eaten per level. The total saving cap with cooks goes from 40% to 55%.
  - **Barricades:** +3 defense per level, and they hold off raids even with nobody on guard.

  Old saves start them at level 1.

### Changed
- **Signals you already locked onto stay on the dial.** They read "Already logged" and can't be locked again, instead of vanishing.

### Fixed
- **Opening the radio froze the screen for about half a second:** every sound loop was synthesized at that moment. They're now built on a low-priority background thread when the app starts; if the radio opens first, the static starts as soon as they're ready.

## [0.6.11] — A tidier COMMS, clearer screens — in verification

App 0.6.11 (versionCode 611) · DB 12 (no change) · Content 20 (no change)

### Added
- **Every log line shows its time,** small and dim. Tapping a line adds the date.
- **Warning before sending someone hurt:** the send screen says who's hurt and how badly. The first tap on SEND turns it into SEND ANYWAY.
- **Goals explain themselves:** each one shows its reward (paid straight into your supplies when you finish it), and when daily and weekly goals reset.

### Changed
- **EXPEDITIONS → ALL is an overview:**
  - It shows departures, returns (with loot and injuries), deaths, recalls and calls for orders.
  - Each expedition's full radio log is on its own chip. A line separates one expedition from the next.
  - These summaries no longer go to SYSTEM.
- **Calls for orders switch off:** once the order is given, or a newer call replaces it, the bright "!" turns into a dim, switched-off one.
- **Expedition chips stay after the trip:**
  - Each expedition's chip ("Mara ✓") stays for 4 hours after its result came in.
  - Results are worked out while the game is open: on screen if you were there, or when you open the game if you weren't.
- **CHATS → ALL** shows only what happens between the crew (shared moments, reactions). One-on-one chats are on each person's chip, and the "Bunker" chip is gone.
- **Selected tabs get tall brackets:** one bracket around the whole button, icon and name together, instead of characters beside the text. Same for the COMMS tabs, inventory tabs and the radio and bunker sections.
- **Radio and bunker section buttons stay on one line:** they shrink to fit and the bracket shows the selected one.
- **The map legend is titled "MAP SYMBOLS"** and lists one symbol per line, saying what each means.

## [0.6.10] — Fewer repeats, timed choices, a radio with a timetable — in verification

App 0.6.10 (versionCode 610) · DB 12 (no change) · **Content 19 → 20**

### Added
- **30 common situations** that can come up in any zone of their band: a vending machine, a lone trader, a bridge down, a farmhouse with chickens, a hospital bed in the street… Short, medium and long each get at least 10. New `bands` field on events.
- **Choices take time, or save it:**
  - An option can cost or save time on the trip, shown on its button: "Wait for them to leave (+4 min)", "Rob them while they aren't looking (−2 min)".
  - It's a share of the trip's planned length (`time` on the option), so it scales with the band.
  - A shortcut only saves time if it works; waiting costs time either way.
  - 99 existing options are tagged.
- **Stations keep their own timetable:**
  - Some are always on (weather, the emergency broadcast).
  - Old Berto talks every evening 19:00–20:00. The tango plays 21:00–23:00. The Voice comes on for three minutes every hour.
  - Each station's lines change on their own rhythm, or once per airing.
- **Bulletins:**
  - Relief Net drops food three times a day. Dr. Levy leaves medicine at 10:00.
  - Scav Net warns about dangers at 13:00. The Locksmith gives away stashes at 03:00 and 15:00.
  - You catch a bulletin if you're listening, or if someone is on the **radio task**: they log it and collect it while you're away. That's what the radio assistant is for now.
- **STATIONS section on the radio:** every station you've heard, with its frequency, timetable and "ON AIR" now. Tap one to tune straight to it.
- **Treatment in the infirmary:**
  - Whoever is on the infirmary task (not the injured one) offers to treat an injured person at home on the spot: "Do it (4 medicine)" or "Not now".
  - Gentle, Reckless and Hothead healers don't ask; they just do it if there's medicine.
  - Costs 2 medicine per injury level, and the injury heals at once.
  - "Not now" keeps them from asking again for 6 hours. Nothing is stored: the offer shows while the conditions hold.

### Changed
- **Orders arrive at once.** Radio waves don't take minutes. The radio module now cleans up the signal instead of shortening a delay.
- **Recent situations wait their turn:** anything seen in the last 48 hours only comes up once the fresh ones run out.
- **Crew screen:**
  - The living crew is a grid of tiles: name, what they're doing (their task, or "→ place") and stress, bond, hurt and talk marks.
  - Solid amber frame: in the bunker. Dashed blue frame: out on a mission.
  - Tap a tile for the full card and every action.
  - Filters hide behind [ FILTER ▾ ], which shows the active filter.
- **The Diary moved to COMMS,** as a fourth tab next to SYSTEM · EXPEDITIONS · CHATS. The fallen stay there instead of in the crew grid.
- **Task texts:** the radio task explains bulletins, and the infirmary task explains treatment.

## [0.6.9] — More to talk about, a deeper radio, and things in the dark — in verification

App 0.6.9 (versionCode 609) · DB 12 (no change) · **Content 18 → 19**

### Added
- **46 new conversations**, chosen by who the person is and how things stand with you:
  - **Trust:** someone who doubts you asks why you sent them there, or wants the real odds. Someone who trusts you says they'd go anywhere, and that it scares them.
  - **Stress:** shaking hands and sleepless nights when it's high; "is it weird that today was good?" when it's low.
  - **Bond:** strangers ask what to call you. Close friends share food, ask you to teach them the radio, and talk about the day it's over.
  - **Traits:** each personality starts its own conversations (the Fearful asks you to leave the light on, the Distrustful wants a code word…).
  - **Everyday pool:** ten new "got a minute?" chats replace the lonely "I'm here" one, plus five more for when you start the talk.
  - New dialogue conditions: `minTrust`/`maxTrust`, `minStress`/`maxStress`, `maxBondLevel`.
  - More ways to open a chat in every voice.
- **Radio stations:** fixed channels that are always on the same frequency, free to listen to, and say something new every few minutes. Each goes in the Diary the first time.
  - Automated weather, Old Berto talking about his garden, the emergency broadcast, a tango record.
  - The Litany, which reads the names of the dead, ours included.
  - Some only come on at night.
- **Echoes:** some hours after someone falls, their voice can turn up on the short band, saying their last words.
- **Ten more rare finds**, half of them with supplies.
- **Night omens:** knocks on the hatch, footsteps above, a photo of everyone asleep. They get worse as the Voice's story advances. Stress only, never harm. Some come as an alert.
- **Five suspense situations** on expeditions: footsteps that stop when you do, a radio saying your name, mannequins, a child's voice in a basement, shapes in the fog. None is lethal.
- **Radio sound:**
  - A continuous static bed instead of a click per dial move.
  - A squelch sweep when you switch band.
  - Each signal has its own sound: a man babbling in no language (synthesized), whispers, Morse tones, a teletype warble, a bandoneon, breathing, an open carrier.
  - Off frequency, the signal plays sped up and thin, like single-sideband, and settles as you tune in.
- **Map:** tap a place to see when it's full again ("Full again in 1 d 6 h") and how fast places refill. The send screen shows it too.

### Changed
- **Frequencies in MHz** (short 3.000–3.999, medium 7, long 14, deep 21), plus ◀ ▶ buttons for fine-tuning.
- **Locking on says what it costs:** "LOCK ON · ◈2", or "LISTEN · free" for stations and finds with nothing to keep, which now cost no reserve.
- **Rare finds don't repeat** until you've heard them all; after that, the ones with supplies come first.
- **Morse with the radio manual:** the dot/dash pad becomes a letter keyboard with each letter's code. The group you're on is highlighted in the message, and spaces go in by themselves. Without the manual it's the pad, as before.
- **Talking:**
  - The cooldown is 45 minutes (was 4 hours).
  - Scout-started chats come every 3 hours (was 6).
  - Every bond change now shows a line ("Mara feels a little closer to you. Bond 2: 40% to the next."), not just level-ups.
  - The crew card shows the progress too.
- **No more repeats:** the same repeatable chat never comes twice in a row. A repeatable chat only jumps the queue the first time; after that it joins the everyday pool.
- **Tidier screens:**
  - Crew cards show only SEND/RECALL, TALK and [ ⋯ ]. Gift, task and gear are on the open card. TALK stays visible when they're not in the mood, dimmed, with the time they will be.
  - The radio is split into DIAL · DECODE · RELAYS.
  - The bunker is split into POWER · WORKSHOP · UPGRADES · GOALS, with INVENTORY in the header. A section that needs you is marked: "!" for a power problem, "↑" for a free bench or an affordable upgrade.

### Fixed
- **A signal's preview didn't match its contents:** it showed Morse and turned out to be Caesar, or the other way round. The preview and the lock now roll the same seeded dice per signal. Every cipher has its own preview, and so do the rare finds.
- **Talking didn't seem to raise the bond with more than one person:** only level-ups were announced, so smaller gains went unseen. Every change shows now.

## [0.6.8] — Static in stretches, colors, tidier screen — in verification

App 0.6.8 (versionCode 608) · DB 12 (no change) · Content 18 (no change)

### Changed
- **Static comes in stretches:** a dropped signal now eats a run of 2–4 letters inside a word ("s··-·ations") instead of scattered single letters, drawn in a dim burnt amber so it reads as noise.
- **Expedition progress** moved out of the header: it shows only in COMMS → EXPEDITIONS, and only the picked team when a chip is selected.
- **Bigger icons:** the options button (top right) and the supply icons on the status bar.
- **Status bar explains itself:** tap it for what power, reserve, food, weather and each supply are for.
- **More color:**
  - Field radio, bunker chats and your own commands each have their own tone.
  - Every person keeps their own name color.
  - The status bar turns green, amber or red with the state of power, reserve, supplies and food; weather has its own color.
  - Expedition progress lines are colored too.

### Fixed
- **Release builds failed** since the Nerd Font icons arrived: aapt2 stored characters beyond U+FFFF as invalid UTF-8 and the resource shrinker crashed. The bundled font now also maps every icon in the private area U+E000–F8FF (U+E000 + icon − U+F0000), XML resources use those, and a test keeps it that way. JSON content keeps the original codepoints.

## [0.6.7] — Readable map, info, console, key items, radio depth — in verification

App 0.6.7 (versionCode 607) · **DB 11 → 12** · **Content 16 → 18**

From this version on, saves carry over between updates.

### Added
- **Log details:**
  - Tap a line to see when it happened.
  - "──── Tuesday 8 October ────" separators between days.
  - Unread counts on each expedition and person chip in COMMS ("[ Mara •3 ]"), not just on the three views.
- **Crew sorting and filters:** people at home first, then those out, the fallen last. Filter chips: all · home · away · hurt · stressed.
- **Radio depth: seven new schemes**, each with its own on-screen tool. A key item unlocks each one; without it, the transmission stays in the radio list with a lock as "unknown scheme" and a hint at what would help, so you can keep it and decode it later.
  - **Mirror alphabet** (Atbash): the decoder card flips it for you.
  - **Tap code**: tap letters on a 5×5 grid (cell wall chart).
  - **Vigenère**: type the keyword with a live preview. The message hints at the key ("what everyone fears now"). Needs the puzzle notebook.
  - **Base64** (programmer's notebook).
  - **Military field cipher**: Playfair read out as NATO words. The field codebook turns the words into letters; you find the net's keyword in its pages and type it.
  - **Numbers stations**: one-time pad digit groups. Only the matching pad page (7 or 19) breaks them.
  - **Book code**: page-line-word against the 1974 field handbook, whose pages you can read.
- **19 new transmissions** spread across those schemes, with lore, stashes, dangers and supplies.
  - Some only come in high on the dial (position 500, 600, 700 or 800 and up on their band).
  - Some only on the **deep band**: a sixth antenna level now opens it, for listening only (nothing to scout out there).
  - Several continue the Voice's trail (Echo-7 relays the Voice; "echo seven is under the dam").
- **DB 11 → 12:** `transmissions.cipher_key`. Transmissions from old saves keep working, and unknown saved kinds fall back to Caesar instead of crashing. Content 17 → 18.
- **Key items and the inventory screen:**
  - [ INVENTORY ] in the BUNKER opens four tabs:
    - SUPPLIES: each resource and what it's for.
    - GEAR: stock, and what everyone is wearing.
    - KEEPSAKES.
    - KEY ITEMS: tap one to read it.
  - Nine key items (`keyitems.json`): radio manual, decoder card, cell wall chart, puzzle notebook, programmer's notebook, field codebook, two one-time pad pages and a 1974 field handbook. Most unlock a way to decode transmissions (next).
  - **Where they turn up:** eight new situations in specific places (school, police station ×2, gated community, airport, northern antenna, dam, train station).
    - A key item is yours only if the scout makes it home.
    - Each situation stops appearing once you own its item.
  - Stored as game flags, so no save change. **Old saves:** if the Morse table was already unlocked, you get the radio manual on load.
- **Console commands**, with aliases (`help` lists them all):
  - `crew`, `scout <name>`
  - `send <name> [+name] <place>`, `recall <name>`
  - `places`, `map`
  - `inventory`, `power`, `food`
  - `weather`, `time`
  - `radio`, `decode <n> <answer>`
  - `goals`, `name <new>`
  - `clear` empties the screen only; scrolling up still shows the log.

  Names and places match by their first letters and ignore accents. Wording lives in `terminal.json → console`.
- **Suggestion chips above the input:** while typing they complete commands, then names, then places. With an empty input they show your recent commands.
- **[RECALL]** on a crew card while that person is out (and `recall <name>`). The order takes the usual radio delay to arrive, then they head straight home, and SYSTEM logs "Called Mara home."
- **Send again:** SEND opened from someone who has been out before starts with their last team and destination, so a repeat trip is one tap. This is remembered while the app is open.
- **Long-press explanations:** long-press a log line, a crew card, the status bar, the expedition strip, a bunker module, the decision card or the map's place info to open an INFO sheet explaining every known term in it.
  - It covers traits, stats, moods, resources (and what they're for), gear, places, bands, modules, power consumers, and terms like Bond, Trust, Stress, Reserve, Picked over and Bad feeling.
  - The glossary is built from content: `terminal.json` → `glossary`, `statDescriptions`, `moodDescriptions`; `resources.json` → `use`; band descriptions in `balance.json`.
- **Modules explain their levels:** each shows what it does now and what the next level gives ("Antenna 1/5 · now: hears the short band only · next: opens the medium band"). Content 16 → 17: `levels` on modules.

### Changed
- **Readable map:**
  - Each place has its own icon (store, gas station, pharmacy, school, laundry, hospital, trains, police, gated community, airport, dam, antenna) and a short label ("Hospital", "Trains").
  - Labels go to the right, left, above or below their icon, whichever spot doesn't overlap anything. If none is free the label is skipped, and tapping the place shows the full name.
  - Picked-over places are dimmed; out-of-reach places are dimmer.
  - The layout is worked out only when the map changes, not on every frame.
- **Send from the map:** tapping a reachable place shows [ SEND SOMEONE HERE ], which opens the SEND screen with that destination already picked.
- Font re-subset with icons for the coming features (104 icons, ~150 KB per weight). Zones get `icon` and `shortName` fields; old saves are unaffected.
- **Static instead of cut-off words:** a bad signal no longer chops words ("i found some—"). Letters drop out as dots and dashes and words keep their shape, so you can still piece it together: "i f·u-d s·me r-ti·ns". The worse the signal, the more letters go.

### Fixed
- **Crash when sending out someone with the slang or kid voice.** Voice word swaps ("very" → "real") used a regex flag, `(?U)`, that the JDK accepts but Android's ICU regex engine rejects. Tests ran on the JDK, so they never saw it. This is also what crashed Tano, whose voice was the slang one. The text highlighter had the same kind of flag, `(?u)`. Both now pass case-insensitivity as a compile flag and use explicit letter/digit word edges.
- New test `AndroidRegexTest` scans the source for inline regex flags Android rejects (anything but i, m, s, x).

## [0.6.6] — Icons, highlights, finer map, gear slots, depletion, weather, vignettes — in verification

App 0.6.6 (versionCode 606) · **DB 10 → 11** · **Content 13 → 16**

No release date: not tested on a device. Unsigned release APK: ~1.2 MB.

### Added
- **Bunker vignettes:** while people are home, every few hours two of them may have a moment.
  - Moments: a shared meal, cards on a crate, a song at night, a made-up birthday, a nightmare, a truce.
  - Friends get friendship scenes and rivals get fights over rations. Some scenes need a trait or happen only at night.
  - Each one nudges both people's stress and how they get on, and won't repeat for two days.
  - They show in CHATS under a new "Bunker" chip, with narration plus lines in each person's voice.
  - 16 to start, in `vignettes.json`. Content 15 → 16: `balance.vignettes`.
- **Weather and daylight:**
  - Weather outside comes in 3-hour blocks from the game seed: clear, cloudy, rain, fog or a storm. Fronts tend to last.
  - Night follows the device clock, like the air filters.
  - Effects on checks:
    - Night: stealth +8 %, wits −5 %.
    - Fog: stealth +8 %, wits −6 %.
    - Rain: endurance −5 %.
    - Storm: everything −6 %, plus more radio static.
  - Shown in the status bar (☀ / ☾ plus the weather icon and name). Scouts mention it on the road now and then.
  - Content 14 → 15: `balance.weather` and `terminal.weather`.
- **Places run dry:**
  - Every trip to a place leaves it more picked over (+30 %). It refills by itself (−25 % a day), worked out from elapsed time.
  - Loot found there shrinks with it, down to a quarter when it's bare. The factor is fixed when someone leaves and stored with the expedition. **DB 10 → 11:** `expeditions.loot_factor`.
  - Shown in the SEND list ("picked over 60 %"), on the map (a hollow dot means picked over) and in the map's place info. Scouting grounds never run dry.
  - Content 13 → 14: `balance.depletion`.
- **Gear slots:** every scout has seven slots (head, neck, body, hands, belt, back, feet), one piece each, and the effects of everything worn add up.
  - **Gear screen:** [GEAR] on a crew card opens it. It lists the slots with what's in them and how many spare pieces in the bunker fit each one. Tap a slot to put something on or take it off; whatever was there goes back to the bunker.
  - **Five new pieces:** helmet (head), lucky charm (neck), soft boots (feet: Stealth +1 and 10 % shorter trips), tool belt (belt) and work gloves (hands). The workshop can make four of them; the charm only turns up on expeditions.
  - Wear applies to every piece worn. The first-aid kit is used up from whichever slot holds it. A dead scout's whole outfit stays where they fell.
  - No save change: a piece's location says who wears it.

### Changed
- **Finer outer rings on the map:** the short band has 8 sectors, the medium 12, the long 16. Farther rings cover more ground, so there's more to scout. Sectors are named by 16 compass points ("north-northeast · long band"). Places keep being placed per save from the game seed: same places, different spots each game.
- **Terminal font with icons:** JetBrains Mono Nerd Font (SIL OFL, license in `assets/licenses/`), subset to the characters and ~60 icons the game uses (~140 KB per weight; the APK grew ~30 KB).
  - **Icons appear in:** the bottom bar tabs, the ⚙ options button, the status bar (power, reserve, day/night, each resource, food), the COMMS views, the expedition strip, the crew cards (stress, bond) and the decision cards.
  - **Glyphs:** ⚠ ✝ ☾ in content became icons the font has.
- **Highlighted text:**
  - Names (crew and yours) are bold and bright, places bright and underlined, numbers bold.
  - Danger in red (died, injury, bad feeling, hungry, raid…) and good news in green (back, found, repaired…). Both word lists live in `terminal.json → highlight`, and resource names are green.
  - Radio lines start with a walkie icon and the speaker in bold. Alerts have an inverse-video ⚠ tag, your commands a ❯, and system lines a dim ›.
  - Panel titles are inverse-video title bars.

## [0.6.5] — Interface: bottom bar, COMMS views, decisions, options — in verification

App 0.6.5 (versionCode 605) · **DB 8 → 10** · Content 13 (texts added)

No release date: not tested on a device.

### Added
- **Your name:** a new game asks who's on the radio, and the crew calls you by it.
  - The terminal greets you by name, and `status` shows it.
  - Closer scouts use it in their own voice ("Off I go, Sam. Put the kettle on.").
  - Any line can use `{operator}`.
  - Change it any time in options. **DB 8 → 9:** `game_state.operator_name`.
- **One SEND screen:** tick one or two people (the first leads), pick a destination, press SEND.
  - The list shows who gets on with whom, and every destination's band, time, danger, relay and intel.
  - Personal missions only appear for whoever leads.
  - Sending one person and then another always makes two separate expeditions.
  - Opening it from a crew card pre-ticks that person.
  - This replaces the destination picker plus the "who goes with them?" picker.
- **Readable decisions:** the decision card above the input now shows:
  - who and where ("⚠ Mara + Noor · Hospital");
  - the deadline;
  - what they see, without the radio noise;
  - each order with the scout's gut feeling under it: "looks easy", "could go either way", "bad feeling", or "lost in the static" when the hint didn't come through.

  Conversations show the scout's lines the same way. ◀ 1/3 ▶ pages through everything waiting, and tapping the name opens that expedition's radio thread or that person's chat.
- **COMMS split in three views** (SYSTEM · EXPEDITIONS · CHATS), each with its unread count:
  - **SYSTEM** keeps only what matters:
    - "Mara + Noor left for Hospital."
    - "⚠ Mara needs orders at Hospital."
    - "Mara back from Hospital: 3 rations, 2 scrap. Mara: light injury."
    - "✝ Noor died at Hospital."
    - Plus the bunker's own messages (power, food, gear, map, workshop) and your typed commands.
  - **EXPEDITIONS** has the radio traffic from the field. A chip row narrows it to one team.
  - **CHATS** has conversations, gifts and bunker chatter. A chip row narrows it to one person.
  - The typewriter only animates what's on screen. Urgent alerts beep and vibrate whatever view is open, once per event.
  - The log keeps more history in memory (400 lines), and a quiet view loads older lines.
  - **DB 9 → 10:** `log_lines.thread` and its index. The migration test now also checks new indexes on existing tables.
- **Options screen** (⚙ in the header): your name, sound, vibration, notifications, text speed (slow / normal / fast / instant) and the CRT scanlines. The typed commands `sound` and `notifications` still work.

### Changed
- **Bottom bar navigation:** COMMS · CREW · MAP · RADIO · BUNKER replace the top buttons.
  - The selected tab is bright and bracketed.
  - Badges on the tabs: orders waiting (COMMS ●n), home/alive (CREW 3/4), sectors to scout while nothing is found yet (MAP ?n), transmissions to decode (RADIO •n), and power or food trouble (BUNKER !).
  - The header keeps the status bar and expedition strip visible on every tab.
  - Back from any tab returns to COMMS. The map moved out of the crew panel into its own tab.

## [0.6.4] — Depth: situations, food, gear, map, pairs — in verification

App 0.6.4 (versionCode 604) · **DB 6 → 8** · **Content 9 → 13**

No release date: not tested on a device. Unsigned release APK: ~1.1 MB.

### Added
- **Situations react to who's there** (GDD §4.2):
  - 31 options exist only for certain people:
    - A nurse treats the wounded stranger properly.
    - A mechanic pulls the battery from the overturned car.
    - A Cold-blooded scout counts the sniper's shots.
    - A musician plays for the camp.
  - Options requiring a trait only show when that trait is visible, so they never give away a hidden one. Authored starters only get trait options.
  - 12 new situations only happen to some people: a Curious scout finds the hatch under the rug, a Fearful one hears crying, a trucker finds a jackknifed semi.
  - The log and the buttons only number the options that person can take. Saved choices keep working.
  - Content 8 → 9: events and options can have `requiresTrait` / `requiresBackground`. Validation now allows 2–4 options anyone can take and 6 at most in all.
- Tests: 130 in total (5 new for who-gets-what).
- **The bunker eats:**
  - Everyone alive goes through 1 ration a day; those out carry their share.
  - Each cook on the Kitchen task saves a fifth, up to 40 %.
  - When the stores run out, whoever is home gets hungry: stress rises and wounds heal at half speed. Hungry people talk about food in the bunker chatter.
  - The log warns when there's less than a day of food, when it runs out and when it's back.
  - The status bar shows how long the food lasts ("food ~2.5d", or "⚠ hungry"). The [ BUNKER ! ] badge lights up with under a day of food.
  - More zones now drop rations. It's all elapsed-time arithmetic, with no ticking and no save change (the food clock lives in the bunker's key-value store).
  - Content 9 → 10: `balance.food`, the `foodSaving` task field, the `hungry` voice phrases and the `foodLow`/`foodOut`/`foodBack` texts.
- Tests: 136 in total (6 new for food).
- **Equipment:**
  - Every piece is now its own object with a **rarity** and a **condition**:
    - Uncommon pieces work 1.5× as well, rare ones 2× (a rare padded vest gives Endurance +4).
    - Condition drops after each expedition, faster for common pieces and more if they came back hurt.
    - Under 30 % the log warns. At 0 it breaks and does nothing.
  - **Repair** in the [ BUNKER ] panel's new GEAR section: scrap for common pieces, components and parts for rare ones. You can fix pieces in stock or on anyone at home.
  - **Finding gear:** pieces turn up on the way back from expeditions. Farther bands give better odds and better rarities. The workshop also makes gear, and a skilled crafter on the Workshop task sometimes turns out uncommon or rare pieces.
  - **The dead keep their gear where they fell:** whoever goes back to that zone recovers it, like personal items.
  - The gear picker and crew cards show "Flashlight · rare · 80%".
  - **DB 6 → 7:** new `gear_pieces` table. Old saves' gear (stock counts and equipped ids) becomes common pieces in full condition on load.
  - Content 10 → 11: `balance.gear` and the gear texts and rarity names.
- Tests: 139 in total (3 new for rarity, wear, finds and crafting).
- **Map scouting:**
  - **The map starts blank:** no place is known at the start. Every zone is found by scouting and added to the map, so the first thing a new game asks for is to send someone out.
  - **Region map** ([MAP] in the CREW panel):
    - The bunker in the middle, one ring per band, eight directions.
    - Unexplored sectors show "?". Places found show as dots with their names, and the ones out of reach are dimmed.
    - It's drawn only when the game state changes (no animation).
  - **Scouting:**
    - Tap a "?" and pick who goes. They travel through that band's scouting ground: Outskirts (2–4 min), Far suburbs (12–24 min), The wastes (1–2 h), each with its own situations and loot.
    - When they come back the sector is mapped, and every place in it is added. An empty sector is still mapped.
  - **Where places sit:** each game places zones differently, from the game seed. Close to home they're spread across different directions, so early scouting is rarely wasted.
  - **The radio points the way:** a zone signal no longer reveals the place. It marks its sector "?!" ("Something on the radio from the northeast"). Survivors are only heard in places you've already found.
  - No save change: scouted and heard sectors live in the game flags. Content 11 → 12: `wilds` zones, the map texts and direction names, and no more `startKnown`.
- Tests: 144 in total (5 new for the map).
- **Two-scout expeditions** (GDD §5.3): after picking a destination, choose who goes along, or send them alone. The picker shows who gets on and who doesn't.
  - **Skills:**
    - Situations offer what either of them can do: a nurse partner brings the nurse's options.
    - Checks get help when the partner is better at that stat, plus a bonus between friends (+5 %) or inseparables (+10 %), and −5 % between rivals.
    - Both take the stress and both learn.
  - **Loot:** a pair brings back 40 % more.
  - **On the road:** friends talk about each other on the radio. Rivals argue, and it costs them both stress.
  - **Carrying home:** if an order would kill the lead, the partner can carry them home, critically hurt but alive. The odds come from the partner's Endurance and how close they are. If it fails, the partner comes home alone.
  - Coming back together raises their affinity, even between rivals.
  - The pair moves at the slower one's pace. The expedition strip shows "Mara + Noor → …".
  - **DB 7 → 8:** `expeditions.partner_id`. Content 12 → 13: `balance.pairs`, the `carryHome` voice phrases and the pair texts.
- Tests: 148 in total (4 new for pairs).
- **A generated third starter:** new games start with Mara, Irene and one generated person (`balance.json → people.generatedStarters`). It comes from the game seed, so each game starts with someone different. Existing saves are untouched.

## [0.6.3] — Tano removed — in verification

App 0.6.3 (versionCode 603) · DB 6 (unchanged) · Content 8 (IDs removed, see below)

### Removed
- **Tano**, the third starter. He crashed the game on a device and the cause wasn't found: his whole path through the game logic (every dialogue, chatter, voice phrase, gift and expedition) runs without errors in tests, so the crash is somewhere in the app layer. New games start with Mara and Irene.
- His 8 dialogues, his personal mission (the school annex) and its situation.

### Changed
- On load, open conversations whose dialogue no longer exists are dropped instead of crashing.
- Text and names for generated people are countryless:
  - New name and nickname pools.
  - Background lines no longer mention Córdoba, truco, mate, Los Piojos or pastafrola.

### Notes
- Saves that already have Tano keep him (without bio or dialogues). If the crash came from his saved data, it will still happen there: start a new game.

## [0.6.2] — Generated survivors — in verification

App 0.6.2 (versionCode 602) · **DB 5 → 6** · **Content 7 → 8**

No release date: still needs testing on a device, including the upgrade from 0.6.1.

### Changed
- **Survivors are generated people, not named characters.** Anyone can die at any time, so nobody heard on the dial is hand-made anymore. Each new person is built from parts:
  - **Name:** a random first name from a countryless pool, or now and then a nickname ("Sparrow", "Patch"). Never one the bunker has already known.
  - **Background:** one of 8 (railway signalman, teenager on their own, nurse, trucker, schoolteacher, mechanic, cook, street musician). It sets the bio, which stats are stronger, which voices fit, some tastes and habits, and the rescue lines.
  - **Traits:** two visible ones that don't clash, plus a hidden one.
  - **Voice:** one that fits the background, leaning toward the traits.
  - **Tastes and habits:** drawn from the background and the traits.
  - Farther bands still mean stronger people: higher stat ceilings.
- **Nobody is like the last one:** a new person never repeats the background or trait pair of the crew or the latest recruits, the voice of the last recruit, or any name.
- **Dialogues follow traits and backgrounds instead of specific people:**
  - Each of the 14 personality traits has two conversations.
  - Six traits can be hidden, and each of those has a confession that reveals it.
  - Each background has two to four conversations and asks for its own personal mission at bond level 4.
  - Starters keep their own hand-written dialogues and also get the trait ones.
- Beto, Lucho, Nilda and La Rusa were folded into the railway, teen, nurse and trucker backgrounds. Their conversations and missions now belong to anyone with that background.
- At most 2 survivors wait for rescue at a time (`balance.json → people`).

### Added
- **Six personal missions**, one per new background: the nurse's ward, the trucker's truck at the pump, the teacher's classroom, the mechanic's machines, the cook's canteen at the dam, the musician's confiscated guitar. Each has its own situation.
- `people.json` (names, nicknames, backgrounds). `traits.json` adds generation pools: voices, likes, dislikes, habits and `hideable`.
- Content validation: every personality trait must have a dialogue, and every hideable trait a confession. Every background must have at least 2 dialogues and a personal mission.
- Tests: 125 in total (10 new):
  - Generator: same seed gives the same person; everyone is well formed; nobody repeats the crew; the voice changes from the last recruit; farther is stronger.
  - Recruits keep who they are, and every background has talk and a mission.
  - Trait dialogues and confessions, and the save round-trip.

### DB 5 → 6
- `scouts.persona` and `rescue_targets.persona`: who a generated person is, stored as JSON.
- Existing saves keep their scouts as they are. Survivors recruited from the old named list still load from `survivors.json`, now kept only for that.

### Notes
- Not tested on a device. Unsigned release APK: ~1.1 MB.

## [0.6.1] — English, personalities and interface — in verification

App 0.6.1 (versionCode 601) · DB 5 (unchanged) · **Content 6 → 7**

No release date: still needs testing on a device.

### Changed
- **All game text is now in English:** terminal, events, dialogues, voices, bios, signals, the Voice's story, the Journal and the Android strings. The setting stays in Argentina (Mara, Tano, Olazábal, Lanús, Córdoba).
  - "La Voz" is now **the Voice**, and the app is called **Static**.
  - Terminal commands are now `help`, `status`, `sound` and `notifications`.
  - The six voice styles were rewritten for English (lowercase, slang, formal, telegraph, kid, grandma), with their word swaps adapted.
- This changelog and `docs/VERSIONING.md` are in English. `docs/GDD.md` stays as written.

### Added
- **Personalities:**
  - Seven new traits:
    - *Loner*: Stealth +1; clashes with *Sociable*.
    - *Sociable*: less stress.
    - *Curious*: Wits +1; prefers the risky option.
    - *Stubborn*: Nerve +1; may disobey at low trust.
    - *Hothead*: Endurance +1; more stress; clashes with *Methodical*.
    - *Optimist*: much less stress.
    - *Gentle*: heals others faster.
  - Each scout and survivor now has a second visible trait. Existing saves receive it on load.
  - **Mood** at a glance (content, steady, tense, breaking), from stress and bond.
  - **Habits:** small details about each scout, revealed as the bond grows ("Noticed: …").
  - **Bunker chatter:** every ~40 min, someone at home says something in their own voice. It depends on their mood, their task, and their friends or rivals.
  - **Conversations:** 14 new per-scout ones, plus 4 about relationships:
    - Friends talk about each other.
    - Inseparables open up.
    - Rivals complain.
    - What you answer moves their affinity.
  - Every voice has new phrase banks: tense, about a friend, about a rival, and per task.
- **Interface:**
  - **Status bar** under the top buttons: power used/available (or blackout), reserve, night, and supplies by short name (SCR, CMP, RAT…).
  - **Expedition strip:** one line per scout out. It shows the zone, a progress bar and the estimated return time, or "waiting for orders" / "order on its way". The strip shows absolute times, so nothing ticks.
  - **Badges** on the top buttons: [ RADIO •n ] with transmissions to decode, [ BUNKER ! ] on blackout, power cut or short filters, and [ CREW home/alive ].
  - **Collapsible crew cards:**
    - Collapsed: name, where they are, mood, stress and bond mini-bars.
    - Expanded: stats, relations, habits and the rest. The actions are always visible.
  - Subtle **phosphor glow** on the terminal text (a static text shadow, no animation).
- Tests: 115 in total (4 new): mood, chatter (silence with nobody home, rivals named), and friend conversations with their affinity effect.

### Content 6 → 7
- Texts, trait names and voice phrases changed. Every ID stays the same, so saves remain compatible.
- Format changes:
  - `resources.json`: `shortName`.
  - `scouts.json` / `survivors.json`: `habits`.
  - `dialogues.json`: `requiresRelation` and the choice field `affinity`.
  - `terminal.json`: `moods`.
  - `balance.json`: `barks`.

### Notes
- Log lines already saved before this version stay in Spanish.
- The Voice's story is still a **draft for review**.
- Not tested on a device. Unsigned release APK: ~1.1 MB.

## [0.6.0] — Phase 5: Content and polish — in verification

App 0.6.0 (versionCode 600) · DB 5 (unchanged) · **Content 5 → 6**

No release date: still needs testing on a device.

### Added
- **Long band:** Regional airport, The dam and North antenna, with 2 to 6 h expeditions. New survivors: Nilda (retired nurse, new "grandma" voice) and La Rusa (trucker).
- **40 events** (previously 15), spread across all bands. They include the 5 for personal missions and the Voice's relay.
- **Personal missions** (GDD §5.2): Mara, Tano, Irene, Beto and Lucho each have one.
  - At bond level 4 they ask for it in a conversation, and it appears as a mission when dispatching that scout only.
  - Each plays its own situation. Completing it gives +15 bond, −30 stress, the *At peace* trait, a memory and a few words in their voice.
- **Relationships between scouts** (GDD §5.3): affinity can now be negative.
  - Clashing traits (Reckless vs Methodical, Distrustful vs Loyal) cause friction when sharing a task.
  - Affinity ≥ 50 makes friends, ≥ 80 inseparables, ≤ −40 rivals.
  - Sharing a task with a friend calms; with a rival, it stresses. The card shows relationships.
- **The Voice, chapter 1** (GDD §8), a **DRAFT for review** (`story.json`, marked `_draft`):
  - Five fragments: three ciphered transmissions (short, medium and long band), the police archive or Irene's lab, and the ECHO-7 relay at the North antenna.
  - With all five, the Voice speaks to the operator and the chapter closes.
  - The story in one line: the Silence began at 3:10 with the activation of an alert network called Project Echo; someone keeps the Echo-7 station running and knows the bunker is listening.
- **Full Journal** (GDD §6.6): the fallen (with their story and last transmission), the Voice (found and missing fragments, with the ending), decoded transmissions and known zones.
- **Sound synthesized in code**, with no files or dependencies:
  - Static when moving the dial, louder far from a signal.
  - A click when locking on.
  - Teletype clatter.
  - A beep on urgent messages.

  Recordings can replace it later.
- **Haptics** on urgent messages, in addition to the dial vibration.
- **Settings** (GDD §9): the `sound` and `notifications` commands turn them on or off. They are stored on the device and are not part of the save.
- **Goals:**
  - Daily: decode 1 transmission, complete 2 expeditions, lock 5 signals.
  - Weekly: 10 expeditions.
  - They reset at local midnight, give resources and show in [BUNKER].
- Tests: 111 in total. They include personal missions, reachable fragments, the lab fragment and trait rivalries.

### Changed
- Content validation requires at least 40 events, all reachable from a zone or a personal mission.
- The catch-up test covers 12 h, to include long-band expeditions.

### Notes
- Not tested on a device.
- Unsigned release APK: ~1.1 MB.
- Daily and weekly goals are resolved in `GameSession` (data layer) and don't have their own unit tests yet.

## [0.5.0] — Phase 4: Bunker — in verification

App 0.5.0 (versionCode 500) · **DB 4 → 5** · **Content 4 → 5**

No release date: still needs testing on a device, including the upgrade from 0.4.0.

### Added
- **Power** (GDD §6.4): the generator gives watts (7 at level 1, 17 at level 5), shared with [−] [+] among five consumers:
  - Air filters: mandatory; they ask for one more watt at night, by device time. If they run short, everyone gets stressed.
  - Radio: less noise with the scouts.
  - Antenna: without power only the short band is reachable.
  - Workshop: crafting speed; without power it pauses.
  - Infirmary: healing and stress relief.
  - When power runs short, the lowest priority is cut first, and the log says so.
- **Power reserve:** charged by spare watts. Locking a signal on the dial uses it. It can be recharged by spending a battery.
- **Blackouts:** scheduled per day from the seed. They lower the generator by 4 W for 1 to 3 h.
- **Raids** (GDD §5.3, the "real" version was chosen):
  - Also scheduled per day from the seed. Their strength grows with the antenna level: the farther you listen, the more attention you draw.
  - Whoever is on guard defends with Endurance and Nerve.
  - If they fail or nobody is on guard, raiders steal 30 % of two materials and hurt someone (badly if the gap is large). They never kill: death requires an order from the operator.
- **Modules** (GDD §7):
  - Generator.
  - Radio.
  - Workshop: one more station per level, and new recipes.
  - Infirmary.
  - Bunks: one more scout per level.
  - The antenna.

  Higher levels ask for parts.
- **Workshop** (GDD §6.5): 2 to 10 min recipes (`recipes.json`), in parallel, one per station.
  - Speed = power × (1 + 5 % of the best Craft among those on the Workshop task).
  - It makes relay kits (moved from the radio to the workshop), parts and gear.
- **Gear** (`gear.json`), one piece per scout, put on or taken off with [GEAR]. If the scout dies, the gear is lost.
  - Flashlight: Wits +1.
  - Reinforced backpack: +25 % loot.
  - First-aid kit: the first wound is one level less severe, and the kit is used up.
  - Padded vest: Endurance +2.
  - Portable radio: −40 % noise.
- **Interface:**
  - [ BUNKER ] button with power, reserve, workshop, modules and defense.
  - Recipe picker, gear picker, and a [GEAR] button on the card.
  - The top bar has three more compact buttons.
- Tests: 107 in total (11 new):
  - Power sharing and cuts, and night filters.
  - Scheduled events, the same however catch-up is split.
  - Raids, the workshop, gear and the first-aid kit.

### Changed
- Relay kits are no longer built from the radio: they are a workshop recipe.
- The radio panel shows only relays; modules moved to the bunker panel.
- Antenna upgrades at levels 4 and 5 ask for parts.

### Design notes
- Power effects over a catch-up interval use the power split in place at the end of the interval. It's an approximation: nothing is simulated minute by minute.

### DB 4 → 5
- `scouts.gear_id`.
- New tables: `power`, `bunker_state` (reserve, last event check) and `craft_jobs`.
- Existing saves get no retroactive raids or blackouts, and the reserve starts full.

### Notes
- Not tested on a device.
- Unsigned release APK: ~1.1 MB.

## [0.4.0] — Phase 3: Active radio and distance — in verification

App 0.4.0 (versionCode 400) · **DB 3 → 4** · **Content 3 → 4**

No release date: still needs testing on a device, including the upgrade from 0.3.0.

### Added
- **Bands and distance** (GDD §4.4):
  - Short, medium, long and deep.
  - Each has its own noise, order delay, dial precision and signal mix.
  - All **[placeholder]** in `balance.json → bands`.
  - Reports from far zones arrive broken up, and sometimes the odds hint is lost in the static. If the "bad feeling" wasn't heard, it doesn't count as a warning for the death rule.
  - On far bands, orders take time to arrive: 1 min on medium, 5 min on long. If they arrive after the deadline, the scout has already decided alone.
- **Antenna:** the first bunker module. Upgrading it costs resources and opens bands: medium at level 2, long at level 4.
- **Frequency sweep** (GDD §6.1): a 0–999 dial per band.
  - Signals sit at positions derived from the seed and rotate every 4 h.
  - The text clears up as you get closer, with a vibration when crossing thresholds. [LOCK ON] locks the signal.
  - Each signal can be:
    - a new zone;
    - a survivor;
    - a ciphered transmission;
    - a rare find: music, numbers, beacons.
- **Decoding** (GDD §6.2) with on-screen controls:
  - Caesar: a shift dial with a preview.
  - Morse: a dot/dash keypad that builds letter by letter. The reference table unlocks with the first transmission (the "radio manual").
  - Rewards:
    - Zone intel: a stash, which gives extra loot, or its dangers, which improve rolls.
    - Lore, kept for the Journal.
    - Resources.
- **Relays:** kits built from resources and installed by sending a scout.
  - They reduce noise and delay in their zone (×0.3), and extend reach to the next band with one less antenna level.
  - They wear 10 % per day, stop working below 30 %, and are repaired with another mission.
- **Missions:** rescue a survivor, install a relay or repair it. They only count if the scout returns without aborting.
- **Survivors:**
  - Beto: former signalman, telegraph voice in capitals.
  - Lucho: seventeen, kid voice.
  - They join with low trust and have higher stat ceilings. Each brings 2 dialogues of their own.
  - Limit of 5 living scouts; Phase 4 raises it with the bunks.
- **Radio assistant:** the task now sweeps the dial on its own, with one signal every 6 h of work.
- **Medium band:**
  - Zones: Municipal hospital, Train station, Police station No. 3 and Gated community, plus a hidden short-band zone (Industrial laundry).
  - Six new events: pack, looters, gas, wagon, archive and wounded.
- **Interface:**
  - [ RADIO ] button with a band selector, dial, readout and signal meter.
  - The radio panel also lists pending transmissions, the antenna and relays.
  - Decoding panel.
  - The dispatch panel shows available missions, and each zone's band, relay and intel.
- Tests: 96 in total (18 new):
  - Ciphers, the signal field, reach and signal quality.
  - Relays, order delay and noise.
  - Lost hints, stash intel and missions.

### Changed
- Scouts can only be dispatched to known zones within reach.
- `GameContent.starter()` also searches survivors.
- The command test no longer depends on which sections `terminal.json` requires.
- The top bar shows [ RADIO ] and [ SCOUTS ]; the title was removed to make room.

### DB 3 → 4
- New tables: `game_flags`, `known_zones`, `caught_signals`, `transmissions`, `intel`, `relays`, `rescue_targets`, `lore` and `modules`.
- `expeditions`: new columns `mission_type`, `mission_target`, `signal_noise`, `order_delay_millis`, `cache_intel`, `danger_intel`, `aborted` and `lost_hints`.
- In existing saves, the starting zones are known and the antenna starts at level 1.

### Notes
- Not tested on a device.
- Unsigned release APK: ~1.0 MB.

## [0.3.0] — Phase 2: Life in the bunker — in verification

App 0.3.0 (versionCode 300) · **DB 2 → 3** · **Content 2 → 3**

No release date: still needs testing on a device, including the upgrade from 0.2.0.

### Added
- **Bond** (GDD §5.2): a personal relationship with the operator, separate from trust. It goes from 0 to 100 over 5 levels.
  - It rises by talking, with gifts, and by letting someone come back when they ask.
  - It drops if the scout is ignored for more than 48 h (−3 per day), or is sent into a risky option they had a bad feeling about.
  - From level 3:
    - the hidden trait is revealed;
    - the voice changes ("brother", and Irene drops the formal address);
    - when in danger, the scout can ask to come back.
- **Conversations** (`dialogues.json`, 22 dialogues) played in the log, with answers as buttons:
  - Per scout: their story, their tastes, a confession that reveals their hidden trait, and a request for advice.
  - Shared: routine chat, "got a minute?" and grief conversations.
  - What you choose stays as a memory. The operator can start one chat per scout every 4 h; the ones a scout starts expire after 12 h.
- **Gifts:** 9 personal items (`items.json`) that turn up on expeditions depending on the zone.
  - Each scout has hidden likes and dislikes, discovered by talking or giving gifts. A liked gift gives +15 bond; a disliked one, −6.
  - The scout carries the gifts they received.
- **Bunker tasks** (`tasks.json`), computed from elapsed time:
  - Rest: own stress drops ×2.5.
  - Infirmary: others heal ×1.5 and the group calms.
  - Kitchen: lowers group stress.
  - Workshop: 3 scrap → 1 component every 2 h.
  - Guard and Radio assistant: for now they only train their stat; the radio also calms the group a little.
  - Training: whoever knows more teaches, ×1.5.
  - Every task trains a stat.
- **Guidance and specialization:** when a scout reaches a certain stat, they ask for advice, and the answer gives a permanent specialization. The options are Shadow, Tracking, Marksman, Mechanics and First aid.
- **Affinity between scouts** (minimal, hidden): it rises by training or sharing a task. It decides who suffers a loss more, and whom a scout names when dying.
- **Full death** (GDD §5.5):
  - With high bond and in danger, the scout asks to come back, and the [Come back to the bunker] option appears.
  - The last transmission mentions the gift they carry, the closest companion and something the operator told them.
  - Afterwards, the others get grief stress according to their closeness, and sometimes a new scar (*Grudge* or *Fear*). A day or two later they talk about the dead.
  - Their task goes uncovered and their card stays dimmed in the list.
  - Their items stay in the zone and are recovered by going back there.
- **Journal:** one entry per fallen scout, with their story, memories and the full last transmission.
- **Interface:**
  - The scout card shows bond (■■□□□), task, discovered tastes and carried gifts.
  - [TALK] [GIFT] [TASK] buttons, which wrap onto the next line on narrow screens.
  - [JOURNAL] button in the scouts panel.
- Tests: 78 in total (23 new):
  - Dialogues, tasks and gifts.
  - Grief, the request to come back and found items.
  - Last words, and the stress regression.

### Fixed
- **Stress didn't drop with the app open.** Each catch-up rounded the recovery down, and with catch-ups every few seconds (active expeditions) all of it was lost. The fraction now accumulates (`stress_carry`), like task XP.

### Changed
- `ZoneAdapter` was replaced by a generic `PickerAdapter`, used for zones, gifts, tasks and the Journal.
- The decision panel shows both situation orders and conversation answers; situations go first.
- `Migrations` is now a list of steps (`STEPS`), and `MigrationsTest` checks each step against the exported schemas.
- Content v3:
  - New files: `items.json`, `tasks.json` and `dialogues.json`.
  - New voice phrases, specialization and scar traits, and scout tastes.
  - `balance.json` adds the `bond`, `grief` and `affinity` sections.

### DB 2 → 3
- `scouts`: new columns `bond`, `task_id`, `last_talk_at_millis`, `last_initiated_at_millis`, `last_decay_at_millis`, `task_xp_carry`, `task_output_carry`, `stress_carry`, `died_at_millis` and `died_zone_id`.
- `expeditions`: new column `retreat_offered`.
- New tables: `scout_flags`, `conversations`, `items`, `affinity` and `expedition_items`.
- In existing saves, scouts start with 0 bond and no task.

### Notes
- Not tested on a device.
- Unsigned release APK: ~1.0 MB.

## [0.2.0] — Phase 1: Expeditions — in verification

App 0.2.0 (versionCode 200) · **DB 1 → 2** · **Content 1 → 2**

No release date: still needs testing on a device, including the upgrade from 0.1.0 (DB migration).

### Added
- **Scouts** (GDD §5.1): 3 starters (Mara, Tano, Irene) defined in `scouts.json`. Each one has:
  - 5 stats with their own base and ceiling;
  - visible traits, and a hidden trait that already has an effect while unseen;
  - stress, trust and wounds.
- **Growth through use:** each roll gives XP to the stat used, and stats rise up to their ceiling.
- **Recovery in the bunker**, computed from elapsed time with no ticks: stress drops and wounds heal one level every 4 h.
- **Traits** (`traits.json`): Reckless, Distrustful, Light feet, Methodical, Fear, Loyalty, Cold blood. They modify:
  - stats, rolls and loot;
  - duration and stress;
  - obedience, and how the scout decides alone.
- **Voice styles** (`voices.json`): lowercase, slang and formal. Each has its own text transformation and phrase banks.
- **4 Short Band zones** (`zones.json`) and **9 situations** (`events.json`), with 2–3 options each.
- **Expeditions** (GDD §4):
  - The whole timeline is generated at dispatch, with its own seed. Durations are relative: waiting for a decision pushes back everything after it.
  - Odds are never shown: the scout says them in their own voice, at three levels ("I've got a bad feeling about this").
  - If the operator doesn't answer within 15 min, the scout decides according to their traits.
  - A scout with low trust and a disobedience trait may ignore the order.
  - A critical wound sends them back to the bunker.
  - The loot they bring is added to the bunker's stores on return.
- **Catch-up** (GDD §4.3):
  - On opening the app, everything due is resolved in order and dumped into the log, without the typing effect.
  - With the app open, a single timer wakes the game at the next event, with no polling.
- **Simple death** (a preview of Phase 2):
  - It is permanent.
  - The last transmission comes through with signal drops and mentions a memory or the last order, then "— signal lost —".
  - It is kept as a `LAST_WORDS` memory for the future Journal.
- **Memories** (GDD §5.4): situations that marked the scout, risky orders or ones that went wrong, and last words. They are kept even if the scout dies.
- **Interface:**
  - [ SCOUTS ] button that opens the panel: a card with stats as bars (■■■□□), status, stress, trust, traits and bio.
  - From the card you dispatch and pick the zone.
  - A decision panel with options as buttons and the deadline.
  - The `status` command now lists scouts and stores.
- **Notifications** (WorkManager 2.9.1):
  - When the app goes to the background, the next notable moment (situation or return) is scheduled per expedition. That moment is computed from the saved timeline, without simulating anything.
  - The worker only posts a ready-made text in the scout's voice. Notifications are cancelled when you return to the app.
  - The permission is requested on the first dispatch.
- `GameSession`: loads the save once and applies each step (catch-up, dispatch, order) in a single Room transaction.
- `ContentValidator`: checks the references between content files (traits, voices, events, resources, stats) and lists all problems together.
- Tests: 55 in total. They include:
  - One-shot catch-up = step-by-step catch-up, in every zone and with many seeds.
  - No death without a warning.
  - Scouts who decide alone never die.
  - Notifications predict exactly when each thing happens.
  - The 1→2 migration matches the schemas exported by Room.

### Death rules (design decision)
- A scout dying requires **a warning + an order from the operator**.
- Warning: a severe wound or worse, stress ≥ 70, or the scout having said they had a bad feeling about the option.
- If the scout decided alone (timeout or disobedience), they can be wounded but not die (GDD §4.2: being away doesn't punish hard).
- A lethal failure without warning leaves the wound the outcome indicates, and that wound is the warning for next time.

### Changed
- `LogLine` now has an optional `scoutId`, to filter the log by scout later.
- Content v2:
  - `terminal.json` adds `statNames`, `scoutStatus`, `injuries` and the expedition texts.
  - `balance.json` adds the `scouts` and `checks` sections.

### DB 1 → 2
- New tables: `scouts`, `scout_stats`, `scout_traits`, `memories`, `expeditions`, `expedition_segments`, `expedition_loot`, `resources`.
- New columns: `log_lines.scout_id` and `game_state.content_version` (default 1).
- Existing saves receive the 3 starting scouts on load.

### Balance
Everything is **[placeholder]** and lives in `balance.json`. The table shows 1000 simulations per scout and zone, with no operator input:

| | Warehouse | Station | Pharmacy | School |
|---|---|---|---|---|
| Mara (wounded / critical) | 13% / 0% | 0% / 0% | 28% / 2% | 35% / 4% |
| Tano | 41% / 0% | 34% / 0% | 61% / 8% | 68% / 15% |
| Irene | 0% / 0% | 8% / 0% | 20% / 0% | 22% / 0% |

- Deaths without intervention: 0%.
- Loot: 3–5 units per expedition.
- Tano gets hurt often because he's reckless, on purpose.

### Notes
- Not tested on a device: no emulator or phone was available.
- The migration is checked against the exported schemas. An instrumented test with `MigrationTestHelper` is pending until there is a device.
- Unsigned release APK: ~0.9 MB.

## [0.1.0] — Phase 0: Skeleton — in verification

App 0.1.0 (versionCode 100) · DB 1 · Content 1

No release date yet: it still needs testing on a device or emulator. Once approved, it gets a date and the `v0.1.0` tag.

### Added
- Native Android project: Java 17, Views + ViewBinding, minSdk 26, compile/targetSdk 35, Gradle 8.11.1, AGP 8.7.2.
- Dependency version catalog in `gradle/libs.versions.toml`: Room 2.6.1, Hilt 2.52, Lifecycle 2.8.7, Gson 2.11.0, RecyclerView, ConstraintLayout.
- App version centralized in `version.properties`; `versionCode` is computed automatically.
- Core utilities:
  - `core/GameClock` (injectable interface) and `SystemGameClock`.
  - `core/GameRandom`: a deterministic SplitMix64 PRNG whose whole state is one persisted `long`.
  - `core/TextTemplate` (`{key}` placeholders) and `core/Ids` (UUID).
- `domain/`: `LogLine`/`LogChannel`, content models (`Balance`, `TerminalText`), `ElapsedFormatter`, `CommandInterpreter`, `GameState`.
- Room v1:
  - Tables `log_lines` (local seq + global UUID) and `game_state` (single row: operator, seed and RNG state, timestamps).
  - The schema is exported to `app/schemas/`.
  - Migrations go in `data/db/Migrations`, with no destructive fallback.
- JSON content loading (`assets/content/balance.json`, `terminal.json`): parsed once, validated and cached.
- `balance.json` with every **[placeholder]** from the GDD: situation timeout, bands, bond levels, signal rotation, workshop times, ad cap.
- Terminal screen:
  - The log is a `RecyclerView` (`ListAdapter` + `DiffUtil` + stable IDs).
  - It keeps a 200-line window in memory and pages backwards from Room.
- Typewriter effect: a single `Handler`, only for the active line. Tapping the log completes the line, and going to the background completes it too.
- Static CRT scanlines (repeated bitmap shader, no animation), amber palette `#FFB000`, adaptive icon.
- Boot sequence on the first game. On return, a reconnection message with the time away, chosen with the persisted RNG (the basis of catch-up).
- Terminal commands defined in JSON: `help` and `status`.
- Single-thread game executor: all logic and Room access run there, in order.
- Unit tests (23): `GameRandom`, `TextTemplate`, `ElapsedFormatter`, `CommandInterpreter`, validation of the bundled JSON.

### Changed
- `GDD.md` moved to `docs/GDD.md`.

### Notes
- Unsigned release APK: ~0.7 MB (target < 15 MB).
- Lint reports newer versions of AGP, Room and Lifecycle. The project uses the ones already in the local cache; upgrading is a separate decision.

[Unreleased]: #unreleased
[0.6.7]: #067--readable-map-info-console-key-items-radio-depth--in-verification
[0.6.6]: #066--icons-highlights-finer-map-gear-slots-depletion-weather-vignettes--in-verification
[0.6.5]: #065--interface-bottom-bar-comms-views-decisions-options--in-verification
[0.6.4]: #064--depth-situations-food-gear-map-pairs--in-verification
[0.6.3]: #063--tano-removed--in-verification
[0.6.2]: #062--generated-survivors--in-verification
[0.6.1]: #061--english-personalities-and-interface--in-verification
[0.6.0]: #060--phase-5-content-and-polish--in-verification
[0.5.0]: #050--phase-4-bunker--in-verification
[0.4.0]: #040--phase-3-active-radio-and-distance--in-verification
[0.3.0]: #030--phase-2-life-in-the-bunker--in-verification
[0.2.0]: #020--phase-1-expeditions--in-verification
[0.1.0]: #010--phase-0-skeleton--in-verification
