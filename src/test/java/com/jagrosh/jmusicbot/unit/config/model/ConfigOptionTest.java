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
package com.jagrosh.jmusicbot.unit.config.model;

import com.jagrosh.jmusicbot.config.model.ConfigOption;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigOption Unit Tests")
class ConfigOptionTest {
    
    @Nested
    @DisplayName("Enum Properties")
    class EnumPropertiesTests {
        
        @Test
        @DisplayName("All enum values have valid properties")
        void allEnumValuesHaveValidProperties() {
            for (ConfigOption option : ConfigOption.values()) {
                assertNotNull(option.getKey(), "Key should not be null for " + option);
                assertFalse(option.getKey().isEmpty(), "Key should not be empty for " + option);
                assertNotNull(option.getType(), "Type should not be null for " + option);
                assertNotNull(option.getDescription(), "Description should not be null for " + option);
            }
        }
        
        @Test
        @DisplayName("Required options are correctly marked")
        void requiredOptionsAreCorrectlyMarked() {
            assertTrue(ConfigOption.TOKEN.isRequired(), "TOKEN should be required");
            assertTrue(ConfigOption.OWNER.isRequired(), "OWNER should be required");
            
            assertFalse(ConfigOption.PREFIX.isRequired(), "PREFIX should not be required");
            assertFalse(ConfigOption.GAME.isRequired(), "GAME should not be required");
        }
        
        @Test
        @DisplayName("All keys are unique")
        void allKeysAreUnique() {
            Set<String> keys = ConfigOption.getAllKeys();
            assertEquals(ConfigOption.values().length, keys.size(), "All keys should be unique");
        }
    }
    
    @Nested
    @DisplayName("Type-Safe Getters")
    class TypeSafeGetterTests {
        
        @Test
        @DisplayName("getString() returns correct value")
        void getStringReturnsCorrectValue() {
            Config config = ConfigFactory.parseMap(Map.of("discord", Map.of("token", "test_token")));
            assertEquals("test_token", ConfigOption.TOKEN.getString(config));
        }
        
        @Test
        @DisplayName("getLong() returns correct value")
        void getLongReturnsCorrectValue() {
            Config config = ConfigFactory.parseMap(Map.of("discord", Map.of("owner", 123456789L)));
            assertEquals(123456789L, ConfigOption.OWNER.getLong(config));
        }
        
        @Test
        @DisplayName("getInt() returns correct value")
        void getIntReturnsCorrectValue() {
            Config config = ConfigFactory.parseMap(Map.of("playback", Map.of("maxYouTubePlaylistPages", 15)));
            assertEquals(15, ConfigOption.MAX_YT_PLAYLIST_PAGES.getInt(config));
        }
        
        @Test
        @DisplayName("getDouble() returns correct value")
        void getDoubleReturnsCorrectValue() {
            Config config = ConfigFactory.parseMap(Map.of("playback", Map.of("skipRatio", 0.75)));
            assertEquals(0.75, ConfigOption.SKIP_RATIO.getDouble(config));
        }
        
        @Test
        @DisplayName("getBoolean() returns correct value")
        void getBooleanReturnsCorrectValue() {
            Config config = ConfigFactory.parseMap(Map.of("voice", Map.of("stayInChannel", true)));
            assertTrue(ConfigOption.STAY_IN_CHANNEL.getBoolean(config));
        }
        
        @Test
        @DisplayName("getConfig() returns nested config")
        void getConfigReturnsNestedConfig() {
            Map<String, Object> aliasesMap = Map.of("play", List.of("p"));
            Config config = ConfigFactory.parseMap(Map.of("commands", Map.of("aliases", aliasesMap)));
            Config aliases = ConfigOption.ALIASES.getConfig(config);
            assertNotNull(aliases);
            assertTrue(aliases.hasPath("play"));
        }
        
        @Test
        @DisplayName("getConfig() returns nested config for audioSources")
        void getConfigReturnsNestedConfigForAudioSources() {
            Config config = ConfigFactory.parseMap(Map.of("playback", Map.of("audioSources", Map.of("youtube", true))));
            Config audioSources = ConfigOption.AUDIO_SOURCES.getConfig(config);
            assertNotNull(audioSources);
            assertTrue(audioSources.getBoolean("youtube"));
        }
        
        @Test
        @DisplayName("getString() throws ConfigException.Missing for missing path")
        void getStringThrowsMissingForMissingPath() {
            Config config = ConfigFactory.empty();
            assertThrows(ConfigException.Missing.class, () -> ConfigOption.TOKEN.getString(config));
        }
        
        @Test
        @DisplayName("getLong() throws ConfigException.Missing for missing path")
        void getLongThrowsMissingForMissingPath() {
            Config config = ConfigFactory.empty();
            assertThrows(ConfigException.Missing.class, () -> ConfigOption.OWNER.getLong(config));
        }
    }
    
    @Nested
    @DisplayName("hasValue() Method")
    class HasValueTests {
        
        @Test
        @DisplayName("hasValue() returns true when path exists")
        void hasValueReturnsTrueWhenPathExists() {
            Config config = ConfigFactory.parseMap(Map.of("discord", Map.of("token", "test_token")));
            assertTrue(ConfigOption.TOKEN.hasValue(config));
        }
        
        @Test
        @DisplayName("hasValue() returns false when path does not exist")
        void hasValueReturnsFalseWhenPathDoesNotExist() {
            Config config = ConfigFactory.empty();
            assertFalse(ConfigOption.TOKEN.hasValue(config));
        }
    }
    
    @Nested
    @DisplayName("findByKey() Method")
    class FindByKeyTests {
        
        @Test
        @DisplayName("findByKey() finds existing key")
        void findByKeyFindsExistingKey() {
            var result = ConfigOption.findByKey("discord.token");
            assertTrue(result.isPresent());
            assertEquals(ConfigOption.TOKEN, result.get());
        }
        
        @Test
        @DisplayName("findByKey() returns empty for non-existent key")
        void findByKeyReturnsEmptyForNonExistentKey() {
            var result = ConfigOption.findByKey("nonexistent");
            assertFalse(result.isPresent());
        }
    }
    
    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethodTests {
        
        @Test
        @DisplayName("getAllKeys() returns all keys")
        void getAllKeysReturnsAllKeys() {
            Set<String> keys = ConfigOption.getAllKeys();
            assertTrue(keys.contains("discord.token"));
            assertTrue(keys.contains("discord.owner"));
            assertTrue(keys.contains("commands.prefix"));
            assertEquals(ConfigOption.values().length, keys.size());
        }
        
        @Test
        @DisplayName("getOptionalKeys() returns only optional keys")
        void getOptionalKeysReturnsOnlyOptionalKeys() {
            Set<String> optionalKeys = ConfigOption.getOptionalKeys();
            assertTrue(optionalKeys.contains("commands.prefix"));
            assertFalse(optionalKeys.contains("discord.token"));
            assertFalse(optionalKeys.contains("discord.owner"));
        }
        
        @Test
        @DisplayName("getOptionalKeys() count is correct")
        void getOptionalKeysCountIsCorrect() {
            Set<String> optionalKeys = ConfigOption.getOptionalKeys();
            long requiredCount = java.util.Arrays.stream(ConfigOption.values())
                    .filter(ConfigOption::isRequired)
                    .count();
            assertEquals(ConfigOption.values().length - requiredCount, optionalKeys.size());
        }
    }
    
    @Nested
    @DisplayName("Parameterized Type Tests")
    class ParameterizedTypeTests {
        
        @ParameterizedTest(name = "{0} should have type {1}")
        @MethodSource("typeTestData")
        @DisplayName("Config options have correct types")
        void configOptionsHaveCorrectTypes(ConfigOption option, ConfigOption.ConfigType expectedType) {
            assertEquals(expectedType, option.getType());
        }
        
        static Stream<Arguments> typeTestData() {
            return Stream.of(
                    Arguments.of(ConfigOption.TOKEN, ConfigOption.ConfigType.STRING),
                    Arguments.of(ConfigOption.OWNER, ConfigOption.ConfigType.LONG),
                    Arguments.of(ConfigOption.MAX_YT_PLAYLIST_PAGES, ConfigOption.ConfigType.INT),
                    Arguments.of(ConfigOption.SKIP_RATIO, ConfigOption.ConfigType.DOUBLE),
                    Arguments.of(ConfigOption.STAY_IN_CHANNEL, ConfigOption.ConfigType.BOOLEAN),
                    Arguments.of(ConfigOption.ALIASES, ConfigOption.ConfigType.CONFIG),
                    Arguments.of(ConfigOption.AUDIO_SOURCES, ConfigOption.ConfigType.CONFIG)
            );
        }
    }
}
