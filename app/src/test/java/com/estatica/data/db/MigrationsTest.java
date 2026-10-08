package com.estatica.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the hand-written migration SQL against the schemas Room exported. Catches a
 * migration that drifts from the entities without needing a device. (A full
 * MigrationTestHelper run needs an instrumented test.)
 */
public class MigrationsTest {

    private static final Path SCHEMAS = Paths.get("schemas", "com.estatica.data.db.EstaticaDatabase");

    @Test
    public void everyMigrationMatchesExportedSchemas() throws IOException {
        for (int i = 0; i < Migrations.STEPS.size(); i++) {
            checkStep(i + 1, i + 2, Migrations.STEPS.get(i));
        }
    }

    private static void checkStep(int from, int to, List<String> sql) throws IOException {
        JsonObject v1 = schema(from);
        JsonObject v2 = schema(to);
        Set<String> expected = new HashSet<>();

        Map<String, JsonObject> oldTables = tables(v1);
        for (Map.Entry<String, JsonObject> entry : tables(v2).entrySet()) {
            String table = entry.getKey();
            JsonObject entity = entry.getValue();
            if (!oldTables.containsKey(table)) {
                expected.add(sub(entity.get("createSql").getAsString(), table));
                for (JsonElement index : array(entity, "indices")) {
                    expected.add(sub(index.getAsJsonObject().get("createSql").getAsString(), table));
                }
                continue;
            }
            Set<String> oldColumns = new HashSet<>();
            for (JsonElement f : array(oldTables.get(table), "fields")) {
                oldColumns.add(f.getAsJsonObject().get("columnName").getAsString());
            }
            for (JsonElement f : array(entity, "fields")) {
                JsonObject field = f.getAsJsonObject();
                String column = field.get("columnName").getAsString();
                if (oldColumns.contains(column)) {
                    continue;
                }
                boolean notNull = field.get("notNull").getAsBoolean();
                StringBuilder alter = new StringBuilder("ALTER TABLE `" + table + "` ADD COLUMN `"
                        + column + "` " + field.get("affinity").getAsString());
                if (notNull) {
                    alter.append(" NOT NULL");
                }
                if (field.has("defaultValue")) {
                    alter.append(" DEFAULT ").append(field.get("defaultValue").getAsString());
                }
                assertTrue("NOT NULL column needs a default: " + table + "." + column,
                        !notNull || field.has("defaultValue"));
                expected.add(alter.toString());
            }
            // New indices on an existing table.
            Set<String> oldIndices = new HashSet<>();
            for (JsonElement i : array(oldTables.get(table), "indices")) {
                oldIndices.add(i.getAsJsonObject().get("name").getAsString());
            }
            for (JsonElement i : array(entity, "indices")) {
                JsonObject index = i.getAsJsonObject();
                if (!oldIndices.contains(index.get("name").getAsString())) {
                    expected.add(sub(index.get("createSql").getAsString(), table));
                }
            }
        }

        for (String statement : expected) {
            assertTrue("missing from migration " + from + "->" + to + ":\n" + statement,
                    sql.contains(statement));
        }
        assertEquals("unexpected extra statements in " + from + "->" + to, expected.size(), sql.size());
    }

    @Test
    public void everyVersionHasAMigration() throws IOException {
        int latest = Migrations.STEPS.size() + 1;
        assertTrue("schema " + latest + ".json must exist", Files.exists(SCHEMAS.resolve(latest + ".json")));
        assertEquals(latest, schema(latest).getAsJsonObject("database").get("version").getAsInt());
        assertEquals(latest - 1, Migrations.ALL.length);
        for (int i = 0; i < Migrations.ALL.length; i++) {
            assertEquals(i + 1, Migrations.ALL[i].startVersion);
            assertEquals(i + 2, Migrations.ALL[i].endVersion);
        }
    }

    private static JsonObject schema(int version) throws IOException {
        try (Reader reader = Files.newBufferedReader(SCHEMAS.resolve(version + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Map<String, JsonObject> tables(JsonObject schema) {
        Map<String, JsonObject> tables = new HashMap<>();
        for (JsonElement e : schema.getAsJsonObject("database").getAsJsonArray("entities")) {
            tables.put(e.getAsJsonObject().get("tableName").getAsString(), e.getAsJsonObject());
        }
        return tables;
    }

    private static JsonArray array(JsonObject object, String name) {
        return object.has(name) ? object.getAsJsonArray(name) : new JsonArray();
    }

    private static String sub(String sql, String table) {
        return sql.replace("${TABLE_NAME}", table);
    }
}
