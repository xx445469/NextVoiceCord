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
package com.jagrosh.jmusicbot.unit.config.loader;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.config.loader.ConfigLoader;
import com.jagrosh.jmusicbot.config.migration.ConfigMigrationException;
import com.jagrosh.jmusicbot.testutil.config.LegacyConfigBuilder;
import com.jagrosh.jmusicbot.testutil.config.V1ConfigBuilder;
import com.typesafe.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigLoader Unit Tests")
class ConfigLoaderTest extends BaseConfigTest {
    
    @Nested
    @DisplayName("loadRawUserConfig() Tests")
    class LoadRawUserConfigTests {
        
        @Test
        @DisplayName("loadRawUserConfig() loads existing config file")
        void loadRawUserConfigLoadsExistingFile() throws IOException {
            // Test with legacy format - loadRawUserConfig returns raw config before migration
            Path configFile = createTempConfigFile("token = test_token\nowner = 123456789");
            
            Config config = ConfigLoader.loadRawUserConfig(configFile);
            
            assertNotNull(config);
            // Raw config has flat keys
            assertEquals("test_token", config.getString("token"));
            assertEquals(123456789L, config.getLong("owner"));
        }
        
        @Test
        @DisplayName("loadRawUserConfig() returns empty config for non-existing file")
        void loadRawUserConfigReturnsEmptyForNonExistingFile() {
            Path nonExistentFile = tempDir.resolve("nonexistent.conf");
            
            Config config = ConfigLoader.loadRawUserConfig(nonExistentFile);
            
            assertNotNull(config);
            assertTrue(config.isEmpty());
            assertFalse(config.hasPath("token"));
        }
        
        @Test
        @DisplayName("loadRawUserConfig() loads complex config with nested structures")
        void loadRawUserConfigLoadsComplexConfig() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                aliases {
                  play = [ p ]
                  skip = [ voteskip, vs ]
                }
                audiosources = [ youtube, soundcloud ]
                """;
            Path configFile = createTempConfigFile(configContent);
            
            Config config = ConfigLoader.loadRawUserConfig(configFile);
            
            assertNotNull(config);
            assertTrue(config.hasPath("aliases.play"));
            assertEquals(2, config.getStringList("aliases.skip").size());
            assertEquals(2, config.getStringList("audiosources").size());
        }
        
        @Test
        @DisplayName("loadRawUserConfig() handles empty config file")
        void loadRawUserConfigHandlesEmptyFile() throws IOException {
            Path configFile = createTempConfigFile("");
            
            Config config = ConfigLoader.loadRawUserConfig(configFile);
            
            assertNotNull(config);
            assertTrue(config.isEmpty());
        }
    }
    
    @Nested
    @DisplayName("loadMergedConfig() Tests")
    class LoadMergedConfigTests {
        
        @Test
        @DisplayName("loadMergedConfig() uses defaults when user config is empty")
        void loadMergedConfigUsesDefaultsWhenUserConfigEmpty() {
            Path nonExistentFile = tempDir.resolve("nonexistent.conf");
            
            Config merged = ConfigLoader.loadMergedConfig(nonExistentFile);
            
            assertNotNull(merged);
            // Should have access to defaults from reference.conf if available
            // The exact behavior depends on what's in reference.conf
        }
        
        @Test
        @DisplayName("loadMergedConfig() preserves user-specific values")
        void loadMergedConfigPreservesUserValues() throws IOException {
            // Legacy config gets migrated to nested format
            String configContent = """
                token = custom_token
                owner = 987654321
                prefix = custom_prefix
                stayinchannel = true
                """;
            Path configFile = createTempConfigFile(configContent);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertNotNull(merged);
            // After migration, check nested paths
            assertEquals("custom_token", merged.getString("discord.token"));
            assertEquals(987654321L, merged.getLong("discord.owner"));
            assertEquals("custom_prefix", merged.getString("commands.prefix"));
            assertTrue(merged.getBoolean("voice.stayInChannel"));
        }
        
    }
    
    @Nested
    @DisplayName("Migration Exception Propagation Tests")
    class MigrationExceptionTests {
        
        @Test
        @DisplayName("loadMigratedUserConfig() propagates ConfigMigrationException when migration path is missing")
        void loadMigratedUserConfigPropagatesException() {
            // Create a legacy config (version 0)
            Config rawUserConfig = LegacyConfigBuilder.create()
                .withToken("test_token")
                .build();
            
            // Create defaults that claim version 3 (no migration path exists for 2->3)
            // Migration will succeed for 0->1 and 1->2, then fail for 2->3
            Config defaults = V1ConfigBuilder.create()
                .withMetaVersion(3)
                .build();
            
            // Should throw ConfigMigrationException, not swallow it
            assertThrows(ConfigMigrationException.class, () -> {
                ConfigLoader.loadMigratedUserConfig(rawUserConfig, defaults);
            });
        }
    }
    
    @Nested
    @DisplayName("Fresh Install (Empty Config) Tests")
    class FreshInstallTests {
        
        @Test
        @DisplayName("loadMigratedUserConfig() skips migration for empty config (fresh install)")
        void loadMigratedUserConfigSkipsMigrationForEmptyConfig() {
            // Empty config simulates no config file exists (fresh install)
            Config emptyConfig = com.typesafe.config.ConfigFactory.empty();
            Config defaults = V1ConfigBuilder.create()
                .withMetaVersion(2)
                .build();
            
            // Should return empty config without triggering migration
            Config result = ConfigLoader.loadMigratedUserConfig(emptyConfig, defaults);
            
            assertNotNull(result);
            assertTrue(result.isEmpty(), "Empty config should remain empty - no migration should occur");
        }
        
        @Test
        @DisplayName("loadMergedConfig() returns defaults for non-existent file without migration")
        void loadMergedConfigReturnsDefaultsForNonExistentFile() {
            Path nonExistentFile = tempDir.resolve("nonexistent.conf");
            
            // Should not throw and should return merged config with defaults
            Config merged = ConfigLoader.loadMergedConfig(nonExistentFile);
            
            assertNotNull(merged);
            // The merged config should have values from defaults (reference.conf)
            // Verify it has the expected structure from v1 format
            assertTrue(merged.hasPath("meta.configVersion"));
            assertEquals(2, merged.getInt("meta.configVersion"));
        }
        
        @Test
        @DisplayName("Empty config is not treated as version 0 legacy config")
        void emptyConfigNotTreatedAsLegacy() {
            // Create an empty config (simulating non-existent file)
            Config emptyConfig = com.typesafe.config.ConfigFactory.empty();
            Config defaults = V1ConfigBuilder.create()
                .withMetaVersion(2)
                .build();
            
            Config result = ConfigLoader.loadMigratedUserConfig(emptyConfig, defaults);
            
            // Result should be empty - not contain migrated meta.configVersion
            // If migration occurred, it would add meta.configVersion = 1
            assertFalse(result.hasPath("meta.configVersion"), 
                "Empty config should not have meta.configVersion added by migration");
            assertTrue(result.isEmpty(), 
                "Empty config should remain empty after loadMigratedUserConfig");
        }
    }
    
    @Nested
    @DisplayName("Version Validation Tests")
    class VersionValidationTests {
        
        @Test
        @DisplayName("loadMigratedUserConfig() returns raw config when user version is higher than latest")
        void loadMigratedUserConfigReturnsRawConfigWhenUserVersionHigher() {
            // Create a config with version higher than latest (user manually set version 3)
            Config rawUserConfig = V1ConfigBuilder.create()
                .withDiscordToken("test_token")
                .withMetaVersion(3)
                .build();
            
            // Defaults have version 2 (the actual latest)
            Config defaults = V1ConfigBuilder.create()
                .withMetaVersion(2)
                .build();
            
            // Should return raw config without modification (and log a warning)
            Config result = ConfigLoader.loadMigratedUserConfig(rawUserConfig, defaults);
            
            // Config should be returned unchanged
            assertNotNull(result);
            assertEquals(3, result.getInt("meta.configVersion"));
            assertEquals("test_token", result.getString("discord.token"));
        }
        
        @Test
        @DisplayName("loadMigratedUserConfig() does not throw when user version is higher than latest")
        void loadMigratedUserConfigDoesNotThrowWhenUserVersionHigher() {
            Config rawUserConfig = V1ConfigBuilder.create()
                .withDiscordToken("test_token")
                .withMetaVersion(99)  // Absurdly high version
                .build();
            
            Config defaults = V1ConfigBuilder.create()
                .withMetaVersion(2)
                .build();
            
            // Should not throw - just warn and return the config
            assertDoesNotThrow(() -> {
                ConfigLoader.loadMigratedUserConfig(rawUserConfig, defaults);
            });
        }
    }
}
