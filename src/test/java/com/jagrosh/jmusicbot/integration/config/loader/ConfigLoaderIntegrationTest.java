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
package com.jagrosh.jmusicbot.integration.config.loader;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.config.loader.ConfigLoader;
import com.typesafe.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigLoader Integration Tests")
class ConfigLoaderIntegrationTest extends BaseConfigTest {
    
    @Nested
    @DisplayName("Real Config File Loading")
    class RealConfigFileLoadingTests {
        
        @Test
        @DisplayName("Loads real config file with all options")
        void loadsRealConfigFileWithAllOptions() throws IOException {
            String configContent = """
                token = integration_test_token
                owner = 987654321
                prefix = "!!"
                altprefix = "??"
                help = commands
                success = ✅
                warning = ⚠️
                error = ❌
                loading = ⏳
                searching = 🔍
                game = Playing music
                status = ONLINE
                songinstatus = true
                npimages = true
                stayinchannel = true
                maxtime = 3600
                maxytplaylistpages = 20
                useyoutubeoauth = true
                alonetimeuntilstop = 300
                playlistsfolder = CustomPlaylists
                updatealerts = false
                loglevel = debug
                eval = false
                evalengine = Nashorn
                skipratio = 0.75
                aliases {
                  play = [ p, playmusic ]
                  skip = [ voteskip, vs ]
                }
                transforms = {}
                audiosources = [ youtube, soundcloud, bandcamp ]
                """;
            Path configFile = createTempConfigFile(configContent);
            
            Config userConfig = ConfigLoader.loadRawUserConfig(configFile);
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertNotNull(userConfig);
            assertNotNull(merged);
            assertEquals("integration_test_token", merged.getString("discord.token"));
            assertEquals(987654321L, merged.getLong("discord.owner"));
            assertEquals("!!", merged.getString("commands.prefix"));
            assertTrue(merged.getBoolean("presence.songInStatus"));
            assertEquals(20, merged.getInt("playback.maxYouTubePlaylistPages"));
        }
        
        @Test
        @DisplayName("Merges user config with reference.conf defaults")
        void mergesUserConfigWithReferenceDefaults() throws IOException {
            // Minimal user config
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                """;
            Path configFile = createTempConfigFile(configContent);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertNotNull(merged);
            // Should have access to default values from reference.conf
            // User values should override defaults
            assertEquals("test_token", merged.getString("discord.token"));
            assertEquals(123456789L, merged.getLong("discord.owner"));
        }
        
        @Test
        @DisplayName("Handles malformed config file gracefully")
        void handlesMalformedConfigFileGracefully() throws IOException {
            // Config with syntax errors
            String configContent = """
                discord.token = test_token
                discord.owner = 123456789
                commands.prefix = "!!"
                invalid syntax here {
                """;
            Path configFile = createTempConfigFile(configContent);
            
            // Should throw ConfigException
            assertThrows(Exception.class, () -> {
                ConfigLoader.loadRawUserConfig(configFile);
            });
        }
        
        @Test
        @DisplayName("Handles nested config structures")
        void handlesNestedConfigStructures() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                commands.aliases {
                  play = [ p, playmusic, pm ]
                  skip = [ voteskip, vs, s ]
                  queue = [ list, q ]
                }
                playback.transforms {
                  youtube {
                    enabled = true
                  }
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertNotNull(merged);
            assertTrue(merged.hasPath("commands.aliases.play"));
            assertEquals(3, merged.getStringList("commands.aliases.play").size());
            assertTrue(merged.hasPath("playback.transforms.youtube"));
        }
    }
    
    @Nested
    @DisplayName("Config Merging Behavior")
    class ConfigMergingBehaviorTests {
        
        @Test
        @DisplayName("User config values override defaults")
        void userConfigValuesOverrideDefaults() throws IOException {
            // Use new format (already nested) - no migration needed
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = user_token
                discord.owner = 999999999
                commands.prefix = user_prefix
                """;
            Path configFile = createTempConfigFile(configContent);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertEquals("user_token", merged.getString("discord.token"));
            assertEquals(999999999L, merged.getLong("discord.owner"));
            assertEquals("user_prefix", merged.getString("commands.prefix"));
        }
        
        @Test
        @DisplayName("Defaults are used when user config is missing values")
        void defaultsAreUsedWhenUserConfigMissingValues() throws IOException {
            // User config with only required fields
            String configContent = "discord.token = test_token\ndiscord.owner = 123456789";
            Path configFile = createTempConfigFile(configContent);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            // Should still be able to access optional fields from defaults
            assertNotNull(merged);
            assertTrue(merged.hasPath("commands.help"));
            assertEquals("help", merged.getString("commands.help"));
        }
    }
}
