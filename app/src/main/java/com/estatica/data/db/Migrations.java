package com.estatica.data.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.List;

/**
 * Every Room schema migration, registered on the database builder. Never use destructive
 * fallback: losing a save means losing scouts the player cared about.
 *
 * <p>To add one: bump {@link EstaticaDatabase} version, build so Room exports the new
 * schema, copy its SQL into {@code MIGRATION_N_M_SQL} (the schema diff can be generated),
 * append it to {@link #STEPS}, and log it in CHANGELOG.md. {@code MigrationsTest} checks the SQL against the exported
 * schemas.
 */
public final class Migrations {

    private Migrations() {
    }

    /** Phase 1: scouts, memories, expeditions, resources. */
    static final List<String> MIGRATION_1_2_SQL = List.of(
            "ALTER TABLE `log_lines` ADD COLUMN `scout_id` TEXT",
            "ALTER TABLE `game_state` ADD COLUMN `content_version` INTEGER NOT NULL DEFAULT 1",
            "CREATE TABLE IF NOT EXISTS `scouts` (`id` TEXT NOT NULL, `template_id` TEXT, `name` TEXT NOT NULL, `voice_id` TEXT NOT NULL, `stress` INTEGER NOT NULL, `trust` INTEGER NOT NULL, `injury` TEXT NOT NULL, `status` TEXT NOT NULL, `recovered_at_millis` INTEGER NOT NULL, `heal_progress_hours` REAL NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `scout_stats` (`scout_id` TEXT NOT NULL, `stat` TEXT NOT NULL, `value` INTEGER NOT NULL, `ceiling` INTEGER NOT NULL, `xp` INTEGER NOT NULL, PRIMARY KEY(`scout_id`, `stat`), FOREIGN KEY(`scout_id`) REFERENCES `scouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `scout_traits` (`scout_id` TEXT NOT NULL, `trait_id` TEXT NOT NULL, `hidden` INTEGER NOT NULL, `revealed` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`scout_id`, `trait_id`), FOREIGN KEY(`scout_id`) REFERENCES `scouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `memories` (`id` TEXT NOT NULL, `scout_id` TEXT NOT NULL, `type` TEXT NOT NULL, `text` TEXT NOT NULL, `ref_id` TEXT, `created_at_millis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_memories_scout_id` ON `memories` (`scout_id`)",
            "CREATE TABLE IF NOT EXISTS `expeditions` (`id` TEXT NOT NULL, `scout_id` TEXT NOT NULL, `zone_id` TEXT NOT NULL, `seed` INTEGER NOT NULL, `started_at_millis` INTEGER NOT NULL, `rng_state` INTEGER NOT NULL, `status` TEXT NOT NULL, `segment_index` INTEGER NOT NULL, `cursor_at_millis` INTEGER NOT NULL, `pending_since_millis` INTEGER NOT NULL, `chosen_option` INTEGER NOT NULL, `chosen_at_millis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_expeditions_scout_id` ON `expeditions` (`scout_id`)",
            "CREATE INDEX IF NOT EXISTS `index_expeditions_status` ON `expeditions` (`status`)",
            "CREATE TABLE IF NOT EXISTS `expedition_segments` (`expedition_id` TEXT NOT NULL, `idx` INTEGER NOT NULL, `type` TEXT NOT NULL, `duration_millis` INTEGER NOT NULL, `event_id` TEXT, PRIMARY KEY(`expedition_id`, `idx`), FOREIGN KEY(`expedition_id`) REFERENCES `expeditions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `expedition_loot` (`expedition_id` TEXT NOT NULL, `resource_id` TEXT NOT NULL, `amount` INTEGER NOT NULL, PRIMARY KEY(`expedition_id`, `resource_id`), FOREIGN KEY(`expedition_id`) REFERENCES `expeditions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `resources` (`resource_id` TEXT NOT NULL, `amount` INTEGER NOT NULL, PRIMARY KEY(`resource_id`))"
    );

    /** Phase 2: bond, tasks, flags, conversations, personal objects, affinity. */
    static final List<String> MIGRATION_2_3_SQL = List.of(
            "ALTER TABLE `scouts` ADD COLUMN `bond` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `scouts` ADD COLUMN `task_id` TEXT",
            "ALTER TABLE `scouts` ADD COLUMN `last_talk_at_millis` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `scouts` ADD COLUMN `last_initiated_at_millis` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `scouts` ADD COLUMN `last_decay_at_millis` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `scouts` ADD COLUMN `task_xp_carry` REAL NOT NULL DEFAULT 0",
            "ALTER TABLE `scouts` ADD COLUMN `task_output_carry` REAL NOT NULL DEFAULT 0",
            "ALTER TABLE `scouts` ADD COLUMN `died_at_millis` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `scouts` ADD COLUMN `died_zone_id` TEXT",
            "ALTER TABLE `scouts` ADD COLUMN `stress_carry` REAL NOT NULL DEFAULT 0",
            "ALTER TABLE `expeditions` ADD COLUMN `retreat_offered` INTEGER NOT NULL DEFAULT 0",
            "CREATE TABLE IF NOT EXISTS `scout_flags` (`scout_id` TEXT NOT NULL, `flag` TEXT NOT NULL, PRIMARY KEY(`scout_id`, `flag`), FOREIGN KEY(`scout_id`) REFERENCES `scouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `scout_id` TEXT NOT NULL, `dialogue_id` TEXT NOT NULL, `started_at_millis` INTEGER NOT NULL, `initiated` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_conversations_scout_id` ON `conversations` (`scout_id`)",
            "CREATE INDEX IF NOT EXISTS `index_conversations_status` ON `conversations` (`status`)",
            "CREATE TABLE IF NOT EXISTS `items` (`id` TEXT NOT NULL, `item_id` TEXT NOT NULL, `location` TEXT NOT NULL, `original_owner_id` TEXT, `acquired_at_millis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_items_location` ON `items` (`location`)",
            "CREATE TABLE IF NOT EXISTS `affinity` (`scout_a` TEXT NOT NULL, `scout_b` TEXT NOT NULL, `value` REAL NOT NULL, PRIMARY KEY(`scout_a`, `scout_b`))",
            "CREATE TABLE IF NOT EXISTS `expedition_items` (`expedition_id` TEXT NOT NULL, `idx` INTEGER NOT NULL, `item_id` TEXT NOT NULL, PRIMARY KEY(`expedition_id`, `idx`), FOREIGN KEY(`expedition_id`) REFERENCES `expeditions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    );

    /** Phase 3: radio, relays, rescues, intel, lore, modules; mission columns on expeditions. */
    static final List<String> MIGRATION_3_4_SQL = List.of(
            "ALTER TABLE `expeditions` ADD COLUMN `mission_type` TEXT NOT NULL DEFAULT 'EXPLORE'",
            "ALTER TABLE `expeditions` ADD COLUMN `mission_target` TEXT",
            "ALTER TABLE `expeditions` ADD COLUMN `signal_noise` REAL NOT NULL DEFAULT 0",
            "ALTER TABLE `expeditions` ADD COLUMN `order_delay_millis` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `expeditions` ADD COLUMN `cache_intel` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `expeditions` ADD COLUMN `danger_intel` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `expeditions` ADD COLUMN `aborted` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `expeditions` ADD COLUMN `lost_hints` INTEGER NOT NULL DEFAULT 0",
            "CREATE TABLE IF NOT EXISTS `game_flags` (`flag` TEXT NOT NULL, `set_at_millis` INTEGER NOT NULL, PRIMARY KEY(`flag`))",
            "CREATE TABLE IF NOT EXISTS `known_zones` (`zone_id` TEXT NOT NULL, `discovered_at_millis` INTEGER NOT NULL, PRIMARY KEY(`zone_id`))",
            "CREATE TABLE IF NOT EXISTS `caught_signals` (`signal_key` TEXT NOT NULL, `caught_at_millis` INTEGER NOT NULL, PRIMARY KEY(`signal_key`))",
            "CREATE TABLE IF NOT EXISTS `transmissions` (`id` TEXT NOT NULL, `source_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `plain_text` TEXT NOT NULL, `shift` INTEGER NOT NULL, `band_id` TEXT, `zone_id` TEXT, `received_at_millis` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_transmissions_status` ON `transmissions` (`status`)",
            "CREATE TABLE IF NOT EXISTS `intel` (`id` TEXT NOT NULL, `zone_id` TEXT NOT NULL, `type` TEXT NOT NULL, `created_at_millis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_intel_zone_id` ON `intel` (`zone_id`)",
            "CREATE TABLE IF NOT EXISTS `relays` (`zone_id` TEXT NOT NULL, `condition` REAL NOT NULL, `last_decay_at_millis` INTEGER NOT NULL, PRIMARY KEY(`zone_id`))",
            "CREATE TABLE IF NOT EXISTS `rescue_targets` (`survivor_id` TEXT NOT NULL, `zone_id` TEXT NOT NULL, `found_at_millis` INTEGER NOT NULL, PRIMARY KEY(`survivor_id`))",
            "CREATE TABLE IF NOT EXISTS `lore` (`id` TEXT NOT NULL, `source_id` TEXT NOT NULL, `text` TEXT NOT NULL, `found_at_millis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `modules` (`module_id` TEXT NOT NULL, `level` INTEGER NOT NULL, PRIMARY KEY(`module_id`))"
    );

    /** Phase 4: energy, bunker values, workshop jobs; gear on scouts. */
    static final List<String> MIGRATION_4_5_SQL = List.of(
            "ALTER TABLE `scouts` ADD COLUMN `gear_id` TEXT",
            "CREATE TABLE IF NOT EXISTS `power` (`consumer_id` TEXT NOT NULL, `units` INTEGER NOT NULL, PRIMARY KEY(`consumer_id`))",
            "CREATE TABLE IF NOT EXISTS `bunker_state` (`key` TEXT NOT NULL, `value` REAL NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS `craft_jobs` (`id` TEXT NOT NULL, `recipe_id` TEXT NOT NULL, `progress_minutes` REAL NOT NULL, `started_at_millis` INTEGER NOT NULL, PRIMARY KEY(`id`))"
    );

    /** Generated people: who a scout or a waiting survivor is, as JSON. */
    static final List<String> MIGRATION_5_6_SQL = List.of(
            "ALTER TABLE `scouts` ADD COLUMN `persona` TEXT",
            "ALTER TABLE `rescue_targets` ADD COLUMN `persona` TEXT"
    );

    /** Equipment as pieces: rarity, condition, where it is. */
    static final List<String> MIGRATION_6_7_SQL = List.of(
            "CREATE TABLE IF NOT EXISTS `gear_pieces` (`id` TEXT NOT NULL, `gear_id` TEXT NOT NULL, `rarity` TEXT NOT NULL, `condition` INTEGER NOT NULL, `location` TEXT NOT NULL, `original_owner_id` TEXT, PRIMARY KEY(`id`))"
    );

    /** Pair expeditions: the second scout. */
    static final List<String> MIGRATION_7_8_SQL = List.of(
            "ALTER TABLE `expeditions` ADD COLUMN `partner_id` TEXT"
    );

    /** The operator's name. */
    static final List<String> MIGRATION_8_9_SQL = List.of(
            "ALTER TABLE `game_state` ADD COLUMN `operator_name` TEXT"
    );

    /** Log threads: system, expeditions, chats. */
    static final List<String> MIGRATION_9_10_SQL = List.of(
            "ALTER TABLE `log_lines` ADD COLUMN `thread` TEXT NOT NULL DEFAULT 'system'",
            "CREATE INDEX IF NOT EXISTS `index_log_lines_thread` ON `log_lines` (`thread`)"
    );

    /** Places run dry: how much was left to find. */
    static final List<String> MIGRATION_10_11_SQL = List.of(
            "ALTER TABLE `expeditions` ADD COLUMN `loot_factor` REAL NOT NULL DEFAULT 1"
    );

    /** Ciphers: the transmission's keyword or precomputed cipher text. */
    static final List<String> MIGRATION_11_12_SQL = List.of(
            "ALTER TABLE `transmissions` ADD COLUMN `cipher_key` TEXT"
    );

    /** SQL of each step; index i migrates version i + 1 to i + 2. */
    static final List<List<String>> STEPS = List.of(MIGRATION_1_2_SQL, MIGRATION_2_3_SQL, MIGRATION_3_4_SQL, MIGRATION_4_5_SQL, MIGRATION_5_6_SQL, MIGRATION_6_7_SQL, MIGRATION_7_8_SQL, MIGRATION_8_9_SQL, MIGRATION_9_10_SQL, MIGRATION_10_11_SQL, MIGRATION_11_12_SQL);

    public static final Migration[] ALL = build();

    private static Migration[] build() {
        Migration[] out = new Migration[STEPS.size()];
        for (int i = 0; i < STEPS.size(); i++) {
            List<String> statements = STEPS.get(i);
            out[i] = new Migration(i + 1, i + 2) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase db) {
                    for (String statement : statements) {
                        db.execSQL(statement);
                    }
                }
            };
        }
        return out;
    }
}
