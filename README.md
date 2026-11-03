# Static (Estática)

**A text-based survival RPG for Android, played entirely through a radio.**

You are the radio operator of an underground bunker, years after an event everyone calls *the Silence*. You never go outside. Your only window on the world is the static on your dial, and the voices of the scouts you send out into the ruined city. You hear what they describe, you decide what they do, and you live with what happens.

> Native Android · Java 17 · fully offline · English and Spanish

---

## The game

Static is built on three ideas.

- **Incomplete information.** You decide from what you *hear*, not what you see. Scouts describe situations in their own words and give you their gut feeling ("this gives me a bad feeling"). The farther they are, the more the signal breaks up: reports cut out, hints get lost in the static, and your orders take minutes to arrive.
- **Attachment.** Every scout has a name, a personality, a voice, stats that grow from how you use them, hidden traits, likes and dislikes, and memories of what you made them do. Death is permanent, and it is never random: a scout only dies when you were warned and gave the order anyway.
- **Short sessions, real time.** Expeditions unfold over minutes or hours of real time. While they're out, there is always something to do in the bunker: decode transmissions, manage power, build gear, talk to the crew.

### What you do

- **Send scouts out** to places on a procedurally laid-out region map, alone or in teams of up to three, and answer the situations they run into: over 170 hand-written situations, some tied to the weather or the night, some that happen only once, some that chain into several decisions in a row.
- **Sweep the radio dial** to find places, survivors, scheduled stations (a man reading the names of the dead, a bedtime story that never ends, a market that only trades while it's on air) and encrypted transmissions.
- **Decode messages** with nine cipher schemes (Caesar, Morse, Atbash, tap code, Vigenère, Base64, Playfair, one-time pads, book codes), several of which need a key item found out in the world.
- **Run the bunker:** split a few watts of power between the air filters, the radio, the antenna and the workshop; ration food; restore sealed rooms; assign the crew to tasks; build and repair gear.
- **Get to know the crew** through about 250 conversations, gifts (everyone has a favorite), bonds that open them up over time, and friendships and rivalries between them.
- **Follow the story** of *the Voice*, a station reading the same four numbers every hour, across two chapters of fragments hidden in the field and on the air.
- **Reach other players without a server:** messages, supply drops and travelling characters are packed into short codes that can be pasted, shown as QR codes or sent over Bluetooth between nearby phones, which can also trade live.

---

## Technical highlights

### Real-time play without a background process

The game never runs in the background: no services, no wakelocks, no per-frame loop. An expedition is generated at dispatch as a seeded timeline of segments. When the app opens, a **catch-up** pass resolves everything due since the last session, in order, from timestamps alone. WorkManager is used only to schedule notifications, which are computed from the same stored timeline.

### Deterministic simulation

All randomness goes through a seeded `GameRandom` whose state is persisted, and all time goes through an injectable `GameClock`. The same save and the same inputs always produce the same outcome, which makes the catch-up logic testable down to the minute.

### A pure-Java domain layer

Game rules (expeditions, scouts, the bunker, the radio, ciphers, the map, multiplayer codes) live in a `domain/` package with no Android dependencies. They are covered by **266 JUnit tests** that run on the JVM in seconds, including tests that play hundreds of seeded expeditions against the real content.

### Data-driven content

Every situation, conversation, zone, item, voice, radio station and balance number lives in JSON under `assets/content/`, parsed once at startup and checked by a content validator. It catches, for example:

- references to things that don't exist
- unreachable follow-up situations
- book-code messages whose words aren't in the in-game handbook

New content is added without touching code. A version number on the content lets saves migrate safely.

### Localization by overlay

The English JSON is the single source of truth for structure and numbers. Translations are flat maps from a stable path (`c_trader/options/trade/label`) to text, laid over the base tree at load time. Dynamic templates adapt text to context and participants. A test fails the build if any line is missing a translation, has stale keys, or changes its placeholders, and it also runs the validator against the translated game.

### Procedural audio

There are no audio files. Static, dial clicks, typing, Morse, modem warbles and the short musical cues are synthesized in code into small buffers. The radio mixes a bed of static with whatever signal is near the dial, detuned when you're off frequency.

### Serverless multiplayer

Inter-bunker codes are compact, checksummed and versioned binary packets. Messages can be sent in clear or in any cipher the sender has unlocked, and decoding one reveals a cache the sender left. Developer broadcasts carry an ECDSA signature so they can't be forged. Nearby phones connect over Bluetooth to trade with a two-sided agree-and-swap protocol.

### Performance by design

- The UI only updates on state changes.
- The radio log is a `RecyclerView` with `DiffUtil`, stable IDs and paging from Room.
- The typewriter effect uses one handler for the active line only.
- Database access and game logic run on a single game thread, never on the main thread.

---

## Stack

| Area | Choice |
|---|---|
| Language / UI | Java 17, Android Views (XML + ViewBinding), ConstraintLayout |
| Persistence | Room, with a migration for every schema change and old saves kept playable |
| Architecture | ViewModel + LiveData, one shared single-thread game executor |
| Dependency injection | Hilt |
| Content | Gson, JSON in `assets/` |
| Background | WorkManager (notifications only) |
| Other | ZXing (QR), Bluetooth RFCOMM |
| Target | minSdk 26, targetSdk 35, R8 in release |

No game engine and no WebView: it's a text game, and a native terminal-style UI keeps cold start fast and the APK small.

## Project layout

```
app/src/main/java/com/estatica/
  core/      seeded RNG, injectable clock, text templates
  domain/    pure game rules: expedition, scouts, bunker, radio, map, link, dialogue
  data/      Room entities and DAOs, repositories, content loading, the game session
  ui/        terminal activity, panels, custom views (map, bunker plan), sound
  work/      notification scheduling
app/src/main/assets/content/      game content (JSON), English
app/src/main/assets/content/es/   Spanish translation overlays
docs/GDD.md          game design document (Spanish)
docs/VERSIONING.md   app, database and content versioning rules
CHANGELOG.md         every change, release by release
```

## Building

Requires the Android SDK and JDK 17.

```
./gradlew assembleDebug    # build a debug APK
./gradlew test             # run the unit tests
./gradlew lint
./gradlew assembleRelease
```

## By the numbers

- About 37,000 lines of Java
- 266 unit tests
- 178 situations, 251 conversations, 19 places, 22 radio stations, 49 encrypted transmissions
- About 5,900 lines of game text, in English and Spanish
- Developed since October 2026, each version recorded in the [changelog](CHANGELOG.md)

---

*Static is a personal project. Its names and places carry an Argentine setting; the design document is in Spanish, while the code and base content are in English.*
