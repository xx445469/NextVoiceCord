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
package com.jagrosh.jmusicbot.unit.config.migration;

import com.jagrosh.jmusicbot.config.migration.ConfigMigration;
import com.jagrosh.jmusicbot.config.migration.ConfigMigrationException;
import com.jagrosh.jmusicbot.testutil.config.LegacyConfigBuilder;
import com.jagrosh.jmusicbot.testutil.config.V1ConfigBuilder;
import com.typesafe.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigMigration Unit Tests")
class ConfigMigrationTest {
    
    @Nested
    @DisplayName("Version Detection")
    class VersionDetectionTests {
        
        @Test
        @DisplayName("detectVersion returns 0 for legacy config without meta.configVersion")
        void testDetectVersion_legacyConfig_returns0() {
            Config config = LegacyConfigBuilder.create()
                .withToken("test_token")
                .withOwner(123456789L)
                .build();
            
            int version = ConfigMigration.detectVersion(config);
            
            assertEquals(0, version);
        }
        
        @Test
        @DisplayName("detectVersion returns 1 for config with meta.configVersion = 1")
        void testDetectVersion_newConfig_returns1() {
            Config config = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .build();
            
            int version = ConfigMigration.detectVersion(config);
            
            assertEquals(1, version);
        }
        
        @Test
        @DisplayName("detectVersion returns 0 for empty config")
        void testDetectVersion_emptyConfig_returns0() {
            Config config = LegacyConfigBuilder.create().build();
            
            int version = ConfigMigration.detectVersion(config);
            
            assertEquals(0, version);
        }
    }
    
    @Nested
    @DisplayName("Latest Version Detection")
    class LatestVersionTests {
        
        @Test
        @DisplayName("getLatestVersion returns 2 from defaults with meta.configVersion = 2")
        void testGetLatestVersion_returns2() {
            Config defaults = V1ConfigBuilder.create()
                .withMetaVersion(2)
                .build();
            
            int version = ConfigMigration.getLatestVersion(defaults);
            
            assertEquals(2, version);
        }
        
        @Test
        @DisplayName("getLatestVersion returns 2 for defaults without meta.configVersion")
        void testGetLatestVersion_noVersion_returns2() {
            Config defaults = LegacyConfigBuilder.create().build();
            
            int version = ConfigMigration.getLatestVersion(defaults);
            
            assertEquals(2, version);
        }
    }
    
    @Nested
    @DisplayName("Migration")
    class MigrationTests {
        
        @Test
        @DisplayName("migrate returns same config when fromVersion >= toVersion")
        void testMigrate_noMigrationNeeded() {
            Config config = V1ConfigBuilder.create()
                .withMetaVersion(2)
                .withDiscordToken("test_token")
                .build();
            
            Config result = ConfigMigration.migrate(config, 2, 2);
            
            assertEquals(config, result);
        }
        
        @Test
        @DisplayName("migrate applies 0->1 migration successfully")
        void testMigrate_v0ToV1_success() {
            Config legacyConfig = LegacyConfigBuilder.create()
                .withToken("test_token")
                .withOwner(123456789L)
                .withPrefix("!!")
                .build();
            
            Config migrated = ConfigMigration.migrate(legacyConfig, 0, 1);
            
            assertNotNull(migrated);
            assertTrue(migrated.hasPath("meta.configVersion"));
            assertEquals(1, migrated.getInt("meta.configVersion"));
            assertTrue(migrated.hasPath("discord.token"));
            assertEquals("test_token", migrated.getString("discord.token"));
            assertTrue(migrated.hasPath("discord.owner"));
            assertEquals(123456789L, migrated.getLong("discord.owner"));
            assertTrue(migrated.hasPath("commands.prefix"));
            assertEquals("!!", migrated.getString("commands.prefix"));
        }
        
        @Test
        @DisplayName("migrate throws exception for unsupported version range")
        void testMigrate_invalidVersionRange_throwsException() {
            Config config = LegacyConfigBuilder.create().build();
            
            assertThrows(ConfigMigrationException.class, () -> {
                ConfigMigration.migrate(config, 0, 3); // No migration for 2->3
            });
        }
    }
}
