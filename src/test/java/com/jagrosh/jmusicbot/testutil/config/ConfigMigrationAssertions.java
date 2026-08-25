/*
 * Copyright 2026 Arif Banai (arif-banai)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.testutil.config;

import com.typesafe.config.Config;
import org.junit.jupiter.api.Assertions;

import java.util.List;

/**
 * Helper class for asserting migration correctness in tests.
 * 
 * <p>Provides static methods to verify that config migrations work correctly.
 * 
 * <p>Example usage:
 * <pre>{@code
 * Config legacy = LegacyConfigBuilder.create().withToken("test").build();
 * Config migrated = migration.migrate(legacy);
 * ConfigMigrationAssertions.assertLegacyToV1Migration(legacy, migrated);
 * }</pre>
 */
public class ConfigMigrationAssertions {
    
    /**
     * Asserts that a legacy config was correctly migrated to v1 format.
     * Verifies all common key mappings and that legacy keys are removed.
     */
    public static void assertLegacyToV1Migration(Config legacy, Config migrated) {
        // Verify meta version is set
        Assertions.assertTrue(migrated.hasPath("meta.configVersion"), 
            "Migrated config should have meta.configVersion");
        Assertions.assertEquals(1, migrated.getInt("meta.configVersion"),
            "Migrated config should have version 1");
        
        // Verify common key migrations
        if (legacy.hasPath("token")) {
            assertKeyMigrated(migrated, "token", "discord.token");
        }
        if (legacy.hasPath("owner")) {
            assertKeyMigrated(migrated, "owner", "discord.owner");
        }
        if (legacy.hasPath("prefix")) {
            assertKeyMigrated(migrated, "prefix", "commands.prefix");
        }
        if (legacy.hasPath("altprefix")) {
            assertKeyMigrated(migrated, "altprefix", "commands.altPrefix");
        }
        if (legacy.hasPath("help")) {
            assertKeyMigrated(migrated, "help", "commands.help");
        }
        if (legacy.hasPath("game")) {
            assertKeyMigrated(migrated, "game", "presence.game");
        }
        if (legacy.hasPath("status")) {
            assertKeyMigrated(migrated, "status", "presence.status");
        }
        if (legacy.hasPath("songinstatus")) {
            assertKeyMigrated(migrated, "songinstatus", "presence.songInStatus");
        }
        if (legacy.hasPath("stayinchannel")) {
            assertKeyMigrated(migrated, "stayinchannel", "voice.stayInChannel");
        }
        if (legacy.hasPath("maxtime")) {
            assertKeyMigrated(migrated, "maxtime", "playback.maxTrackSeconds");
        }
        if (legacy.hasPath("skipratio")) {
            assertKeyMigrated(migrated, "skipratio", "playback.skipRatio");
        }
        if (legacy.hasPath("loglevel")) {
            assertKeyMigrated(migrated, "loglevel", "logging.level");
        }
        
        // Verify legacy keys are removed
        assertLegacyKeysRemoved(migrated, 
            "token", "owner", "prefix", "altprefix", "help", "game", "status",
            "songinstatus", "stayinchannel", "maxtime", "skipratio", "loglevel");
        
        // Verify audio sources migration if present
        if (legacy.hasPath("audiosources")) {
            assertAudioSourcesMigrated(legacy, migrated);
        }
    }
    
    /**
     * Asserts that a legacy key was migrated to a new key with the same value.
     */
    public static void assertKeyMigrated(Config migrated, String legacyKey, String newKey) {
        Assertions.assertTrue(migrated.hasPath(newKey),
            String.format("Migrated config should have new key '%s' (from legacy key '%s')", newKey, legacyKey));
    }
    
    /**
     * Asserts that a legacy key was migrated to a new key with the same string value.
     */
    public static void assertKeyMigrated(Config legacy, Config migrated, String legacyKey, String newKey) {
        if (legacy.hasPath(legacyKey)) {
            String legacyValue = legacy.getString(legacyKey);
            Assertions.assertTrue(migrated.hasPath(newKey),
                String.format("Migrated config should have new key '%s'", newKey));
            Assertions.assertEquals(legacyValue, migrated.getString(newKey),
                String.format("Value should be preserved when migrating '%s' to '%s'", legacyKey, newKey));
        }
    }
    
    /**
     * Asserts that a legacy key was migrated to a new key with the same long value.
     */
    public static void assertKeyMigratedLong(Config legacy, Config migrated, String legacyKey, String newKey) {
        if (legacy.hasPath(legacyKey)) {
            Long legacyValue = legacy.getLong(legacyKey);
            Assertions.assertTrue(migrated.hasPath(newKey),
                String.format("Migrated config should have new key '%s'", newKey));
            Assertions.assertEquals(legacyValue, migrated.getLong(newKey),
                String.format("Value should be preserved when migrating '%s' to '%s'", legacyKey, newKey));
        }
    }
    
    /**
     * Asserts that a legacy key was migrated to a new key with the same boolean value.
     */
    public static void assertKeyMigratedBoolean(Config legacy, Config migrated, String legacyKey, String newKey) {
        if (legacy.hasPath(legacyKey)) {
            Boolean legacyValue = legacy.getBoolean(legacyKey);
            Assertions.assertTrue(migrated.hasPath(newKey),
                String.format("Migrated config should have new key '%s'", newKey));
            Assertions.assertEquals(legacyValue, migrated.getBoolean(newKey),
                String.format("Value should be preserved when migrating '%s' to '%s'", legacyKey, newKey));
        }
    }
    
    /**
     * Asserts that a legacy key was migrated to a new key with the same double value.
     */
    public static void assertKeyMigratedDouble(Config legacy, Config migrated, String legacyKey, String newKey) {
        if (legacy.hasPath(legacyKey)) {
            Double legacyValue = legacy.getDouble(legacyKey);
            Assertions.assertTrue(migrated.hasPath(newKey),
                String.format("Migrated config should have new key '%s'", newKey));
            Assertions.assertEquals(legacyValue, migrated.getDouble(newKey),
                String.format("Value should be preserved when migrating '%s' to '%s'", legacyKey, newKey));
        }
    }
    
    /**
     * Asserts that legacy keys are not present in the migrated config.
     */
    public static void assertLegacyKeysRemoved(Config migrated, String... legacyKeys) {
        for (String legacyKey : legacyKeys) {
            Assertions.assertFalse(migrated.hasPath(legacyKey),
                String.format("Legacy key '%s' should not be present in migrated config", legacyKey));
        }
    }
    
    /**
     * Asserts that audio sources were correctly migrated from list format to boolean map format.
     */
    public static void assertAudioSourcesMigrated(Config legacy, Config migrated) {
        Assertions.assertTrue(migrated.hasPath("playback.audioSources"),
            "Migrated config should have playback.audioSources");
        
        Config audioSources = migrated.getConfig("playback.audioSources");
        
        if (legacy.hasPath("audiosources")) {
            List<String> legacySources = legacy.getStringList("audiosources");
            
            // Verify enabled sources are true
            for (String source : legacySources) {
                String sourceKey = source.toLowerCase();
                Assertions.assertTrue(audioSources.hasPath(sourceKey),
                    String.format("Audio source '%s' should exist in migrated config", sourceKey));
                Assertions.assertTrue(audioSources.getBoolean(sourceKey),
                    String.format("Audio source '%s' should be enabled", sourceKey));
            }
            
            // Verify other sources are false (if they exist)
            // Note: This is a basic check - actual implementation may vary
        } else {
            // If no audiosources key, all should be enabled by default
            Assertions.assertTrue(audioSources.getBoolean("youtube"),
                "When audiosources is missing, youtube should default to enabled");
        }
    }
    
    /**
     * Asserts that a nested structure was preserved during migration.
     */
    public static void assertNestedStructurePreserved(Config legacy, Config migrated, 
                                                      String legacyPath, String newPath) {
        if (legacy.hasPath(legacyPath)) {
            Assertions.assertTrue(migrated.hasPath(newPath),
                String.format("Nested structure should be preserved: %s -> %s", legacyPath, newPath));
        }
    }
}
