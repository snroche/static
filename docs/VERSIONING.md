# Versioning and changelog

Static has **three independent versions**. Each changes for different reasons and protects different things.

| Version | Where it lives | What it protects | When it goes up |
|---|---|---|---|
| **App** | `version.properties` | Builds and the Play Store | On every release |
| **DB** | `EstaticaDatabase.version` + `app/schemas/N.json` | Players' saved games | On every Room schema change |
| **Content** | `assets/content/balance.json → contentVersion` | Save compatibility with content | When the format or IDs of the JSON content change |

## 1. App version (SemVer)

Format `MAJOR.MINOR.PATCH`, with a single source of truth: `version.properties`. Don't edit `versionName` or `versionCode` in `build.gradle`.

**Before 1.0, the version follows the roadmap (GDD §11):**

| Version | Milestone |
|---|---|
| 0.1.x | Phase 0: Skeleton |
| 0.2.x | Phase 1: Expeditions |
| 0.3.x | Phase 2: Life in the bunker |
| 0.4.x | Phase 3: Active radio and distance |
| 0.5.x | Phase 4: Bunker |
| 0.6.x | Phase 5: Content and polish |
| **1.0.0** | Phase 6: Play Store launch |
| 1.x | Post-MVP (Deep Band, grid guidance…) |
| 2.0.0 | Multiplayer (needs a backend; breaking change) |

- **MINOR** goes up when a phase is closed.
- **PATCH** goes up with fixes, balance tweaks or new content within a phase.
- After 1.0, **MAJOR** goes up only if save compatibility breaks or a manual migration is required.

**versionCode** is computed in `app/build.gradle`:

```
versionCode = MAJOR * 10000 + MINOR * 100 + PATCH      // 0.1.0 → 100, 1.2.3 → 10203
```

That's why MINOR and PATCH must stay at 99 or below. The versionCode always grows, as the Play Store requires.

Debug builds carry the `-debug` suffix (e.g. `0.1.0-debug`).

## 2. Database version (Room)

Saves must never be lost: a scout's death should hurt because of the game, not because of a migration bug.

Rules:
1. Any change to an `@Entity` (table, column, index) → bump `version` in `EstaticaDatabase`.
2. Write `MIGRATION_N_M` in `data/db/Migrations.java` and add it to `Migrations.ALL`.
3. `fallbackToDestructiveMigration()` is **forbidden**.
4. Commit the new JSON Room generates in `app/schemas/com.estatica.data.db.EstaticaDatabase/N.json`. Never edit or delete the previous ones: they are the history for testing migrations.
5. Add a migration test once the first one exists (`MigrationTestHelper`, instrumented test; `room-testing` is already in the catalog).
6. Record the change in the CHANGELOG with a `DB N → M` line.

| DB | App | Change |
|---|---|---|
| 1 | 0.1.0 | Initial schema: `log_lines`, `game_state` |
| 2 | 0.2.0 | Scouts, memories, expeditions, resources; `log_lines.scout_id`, `game_state.content_version` |
| 3 | 0.3.0 | Bond, tasks, flags, conversations, personal items, affinity; `expeditions.retreat_offered` |
| 4 | 0.4.0 | Radio: known zones, caught signals, transmissions, intel, relays, rescues, lore, modules; missions in `expeditions` |
| 5 | 0.5.0 | Power (`power`, `bunker_state`), workshop (`craft_jobs`), `scouts.gear_id` |
| 6 | 0.6.2 | Generated people: `scouts.persona`, `rescue_targets.persona` |
| 7 | 0.6.4 | Equipment pieces: `gear_pieces` (rarity, condition, location) |
| 8 | 0.6.4 | Pair expeditions: `expeditions.partner_id` |
| 9 | 0.6.5 | Operator name: `game_state.operator_name` |
| 10 | 0.6.5 | Log threads: `log_lines.thread` (+ index) |
| 11 | 0.6.6 | Places run dry: `expeditions.loot_factor` |

`MigrationsTest` (JVM) compares each migration's SQL against the exported schemas. When adding a migration, extend that test.

## 3. Content version

`contentVersion` in `balance.json`:

- It **does not go up** when numbers or texts change: a balance tweak breaks nothing.
- It **goes up** when a JSON file's structure changes, or when an ID a save can reference (event, item, trait, zone) is renamed or deleted. In that case, add a mapping from old IDs to new ones, or a fallback for existing saves.
- Since DB v2, `game_state.content_version` stores the content version the save was last written with, and `GameSession.start()` updates it on load. That's where adapting old saves will go: read the stored version, map IDs, and only then update it.
- Saves reference IDs of scouts (`template_id`), traits, zones, events, resources, voices and backgrounds (inside generated personas). Renaming or deleting any of them requires bumping `contentVersion` and adding a mapping.

## 4. Changelog

File: [`CHANGELOG.md`](../CHANGELOG.md), [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format.

- **Every** visible or structural change is recorded under `## [Unreleased]`, in the same commit that introduces it.
- Categories: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`, plus `Balance` for GDD number tweaks.
- If the DB or content changes, the entry says so (`DB 1 → 2`, `Content 1 → 2`).
- Write for someone who hasn't read the code: what changed and why it matters, not which file was touched.

## 5. Git

- Main branch: `main`, always buildable with tests green.
- Work branches: `phase<N>/<topic>` (e.g. `phase1/timeline`) or `fix/<topic>`.
- Commit messages in English, imperative, with a type prefix: `feat:`, `fix:`, `balance:`, `content:`, `refactor:`, `test:`, `docs:`, `build:`.
- Each release gets an annotated tag `vMAJOR.MINOR.PATCH`.

## 6. Release checklist

1. `./gradlew test lint assembleRelease` green.
2. Test on a real device: a clean install **and** an upgrade over the previous version (to check the save migration).
3. Bump the version in `version.properties`.
4. In `CHANGELOG.md`, rename `[Unreleased]` to `[X.Y.Z] — YYYY-MM-DD` and open an empty `[Unreleased]`.
5. Check that the new schemas in `app/schemas/` are committed.
6. Commit `release: vX.Y.Z` and `git tag -a vX.Y.Z -m "..."`.
7. Check the APK size (target < 15 MB) and note it in the entry if it changed noticeably.
