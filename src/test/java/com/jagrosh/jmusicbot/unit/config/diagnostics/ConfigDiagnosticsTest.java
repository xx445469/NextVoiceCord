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
package com.jagrosh.jmusicbot.unit.config.diagnostics;

import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigDiagnostics Unit Tests")
class ConfigDiagnosticsTest {
    
    @Nested
    @DisplayName("Missing Required Keys")
    class MissingRequiredKeysTests {
        
        @Test
        @DisplayName("detects missing token")
        void testDetectMissingKeys_token() {
            Map<String, Object> userMap = new HashMap<>();
            // Missing token in user config (already in new format)
            userMap.put("discord", Map.of("owner", 123456789L));
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            // Merged will have owner but NO token
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.getMissingRequired().contains("discord.token"));
        }
        
        @Test
        @DisplayName("detects missing owner")
        void testDetectMissingKeys_owner() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token"));
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.getMissingRequired().contains("discord.owner"));
        }
        
        @Test
        @DisplayName("no missing keys when all required present")
        void testDetectMissingKeys_allPresent() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.getMissingRequired().isEmpty());
        }
    }
    
    @Nested
    @DisplayName("Missing Optional Keys")
    class MissingOptionalKeysTests {
        
        @Test
        @DisplayName("detects missing optional keys from defaults")
        void testDetectMissingOptionalKeys() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            
            Map<String, Object> commands = new HashMap<>();
            commands.put("prefix", "@mention");
            defaultMap.put("commands", commands);
            
            Config defaults = ConfigFactory.parseMap(defaultMap);
            // Merged must NOT have the optional key for it to be reported as missing
            Config merged = migratedUserConfig; 
            
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            // Should detect that commands.prefix is in defaults but not in merged config
            assertTrue(report.getMissingOptional().contains("commands.prefix"));
        }
    }
    
    @Nested
    @DisplayName("Deprecated Keys")
    class DeprecatedKeysTests {
        
        @Test
        @DisplayName("detects unknown top-level key")
        void testDetectDeprecatedKeys_unknownKey() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            userMap.put("unknownKey", "value");
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.getDeprecated().contains("unknownKey"));
        }
        
        @Test
        @DisplayName("ignores meta.configVersion")
        void testDetectDeprecatedKeys_ignoresMetaVersion() {
            Map<String, Object> userMap = new HashMap<>();
            Map<String, Object> meta = new HashMap<>();
            meta.put("configVersion", 1);
            userMap.put("meta", meta);
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            // meta.configVersion should not be flagged as deprecated
            assertFalse(report.getDeprecated().contains("meta.configVersion"));
        }
        
        @Test
        @DisplayName("detects nested unknown key")
        void testDetectDeprecatedKeys_nestedUnknown() {
            Map<String, Object> userMap = new HashMap<>();
            Map<String, Object> commands = new HashMap<>();
            commands.put("unknownCommand", "value");
            userMap.put("commands", commands);
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            
            Map<String, Object> defaultCommands = new HashMap<>();
            defaultCommands.put("prefix", "@mention");
            defaultMap.put("commands", defaultCommands);
            
            Config defaults = ConfigFactory.parseMap(defaultMap);
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.getDeprecated().contains("commands.unknownCommand"));
        }
        
        @Test
        @DisplayName("does not flag user-defined transform under playback.transforms as deprecated")
        void testDetectDeprecatedKeys_userDefinedTransformNotDeprecated() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            
            // User-defined transform with all required fields
            Map<String, Object> spotifyTransform = new HashMap<>();
            spotifyTransform.put("regex", "https?://.*spotify.com/track/([A-Za-z0-9]+).*");
            spotifyTransform.put("replacement", "https://open.spotify.com/track/$1");
            spotifyTransform.put("selector", "title");
            spotifyTransform.put("format", "ytsearch:%s");
            
            Map<String, Object> transforms = new HashMap<>();
            transforms.put("spotify", spotifyTransform);
            
            Map<String, Object> playback = new HashMap<>();
            playback.put("transforms", transforms);
            userMap.put("playback", playback);
            
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            // Defaults have empty transforms
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            
            Map<String, Object> defaultPlayback = new HashMap<>();
            defaultPlayback.put("transforms", Map.of()); // empty transforms in defaults
            defaultMap.put("playback", defaultPlayback);
            
            Config defaults = ConfigFactory.parseMap(defaultMap);
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            // Should NOT flag any paths under playback.transforms as deprecated
            assertFalse(report.getDeprecated().contains("playback.transforms.spotify"),
                    "User-defined transform 'spotify' should not be flagged as deprecated");
            assertFalse(report.getDeprecated().contains("playback.transforms.spotify.regex"),
                    "Transform inner key 'regex' should not be flagged as deprecated");
            assertFalse(report.getDeprecated().contains("playback.transforms.spotify.replacement"),
                    "Transform inner key 'replacement' should not be flagged as deprecated");
            assertFalse(report.getDeprecated().contains("playback.transforms.spotify.selector"),
                    "Transform inner key 'selector' should not be flagged as deprecated");
            assertFalse(report.getDeprecated().contains("playback.transforms.spotify.format"),
                    "Transform inner key 'format' should not be flagged as deprecated");
            
            // Verify no paths under playback.transforms are in deprecated set
            for (String deprecatedPath : report.getDeprecated()) {
                assertFalse(deprecatedPath.startsWith("playback.transforms."),
                        "No paths under playback.transforms should be deprecated, but found: " + deprecatedPath);
            }
        }
        
        @Test
        @DisplayName("does not flag multiple user-defined transforms as deprecated")
        void testDetectDeprecatedKeys_multipleTransformsNotDeprecated() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            
            // Multiple user-defined transforms
            Map<String, Object> spotifyTransform = Map.of(
                    "regex", "https?://.*spotify.com/track/.*",
                    "replacement", "https://open.spotify.com/track/$1",
                    "selector", "title",
                    "format", "ytsearch:%s"
            );
            Map<String, Object> youtubeTransform = Map.of(
                    "regex", "https?://.*youtube.com/.*",
                    "replacement", "https://www.youtube.com/$1",
                    "selector", "title",
                    "format", "%s"
            );
            
            Map<String, Object> transforms = new HashMap<>();
            transforms.put("spotify", spotifyTransform);
            transforms.put("youtube", youtubeTransform);
            
            Map<String, Object> playback = new HashMap<>();
            playback.put("transforms", transforms);
            userMap.put("playback", playback);
            
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            defaultMap.put("discord", Map.of("token", "", "owner", 0L));
            defaultMap.put("playback", Map.of("transforms", Map.of()));
            
            Config defaults = ConfigFactory.parseMap(defaultMap);
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertFalse(report.getDeprecated().contains("playback.transforms.spotify"));
            assertFalse(report.getDeprecated().contains("playback.transforms.youtube"));
        }
        
        @Test
        @DisplayName("still flags unknown keys under playback that are not transforms")
        void testDetectDeprecatedKeys_unknownPlaybackKeyStillFlagged() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            
            Map<String, Object> playback = new HashMap<>();
            playback.put("transforms", Map.of()); // valid empty transforms
            playback.put("unknownSection", "some value"); // unknown key
            userMap.put("playback", playback);
            
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            defaultMap.put("discord", Map.of("token", "", "owner", 0L));
            defaultMap.put("playback", Map.of("transforms", Map.of()));
            
            Config defaults = ConfigFactory.parseMap(defaultMap);
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            // Unknown key should still be flagged
            assertTrue(report.getDeprecated().contains("playback.unknownSection"),
                    "Unknown key under playback should be flagged as deprecated");
        }
    }
    
    @Nested
    @DisplayName("Report Methods")
    class ReportMethodsTests {
        
        @Test
        @DisplayName("hasIssues returns true when issues present")
        void testHasIssues() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("unknownKey", "value");
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Config defaults = ConfigFactory.empty();
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.hasIssues());
        }
        
        @Test
        @DisplayName("hasErrors returns true when required keys missing")
        void testHasErrors() {
            Map<String, Object> userMap = new HashMap<>();
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.hasErrors());
        }
        
        @Test
        @DisplayName("hasWarnings returns true when optional keys missing or deprecated keys present")
        void testHasWarnings() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("discord", Map.of("token", "test_token", "owner", 123456789L));
            userMap.put("unknownKey", "value");
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Map<String, Object> defaultMap = new HashMap<>();
            Map<String, Object> discord = new HashMap<>();
            discord.put("token", "");
            discord.put("owner", 0L);
            defaultMap.put("discord", discord);
            Config defaults = ConfigFactory.parseMap(defaultMap);
            
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            assertTrue(report.hasWarnings());
        }
        
        @Test
        @DisplayName("generateMessage formats diagnostic message correctly")
        void testGenerateMessage() {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("unknownKey", "value");
            Config migratedUserConfig = ConfigFactory.parseMap(userMap);
            
            Config defaults = ConfigFactory.empty();
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            
            ConfigDiagnostics.Report report = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            String message = report.generateMessage();
            
            assertNotNull(message);
            assertTrue(message.contains("WARN") || message.contains("ERROR"));
        }
    }
}
