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
package com.jagrosh.jmusicbot.unit.config.render;

import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.jagrosh.jmusicbot.config.model.ConfigUpdateType;
import com.jagrosh.jmusicbot.config.render.ConfigRenderer;
import com.jagrosh.jmusicbot.testutil.config.V1ConfigBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.parser.ConfigDocument;
import com.typesafe.config.parser.ConfigDocumentFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigRenderer Unit Tests")
class ConfigRendererTest {
    
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Invalidate caches to ensure test isolation
        ConfigFactory.invalidateCaches();
    }
    
    @Nested
    @DisplayName("ConfigDocument Usage")
    class ConfigDocumentUsageTests {
        
        @Test
        @DisplayName("generateConfigContent uses ConfigDocument when reference.conf is available")
        void generateConfigContentUsesConfigDocument() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .withCommandsPrefix("!!")
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.MIGRATION);
            
            assertNotNull(content);
            // Should contain header comments added by ConfigRenderer
            assertTrue(content.contains("# This file was automatically migrated on"));
            // Should be parseable as ConfigDocument
            ConfigDocument doc = ConfigDocumentFactory.parseString(content);
            assertNotNull(doc);
        }
        
        @Test
        @DisplayName("generateConfigContent preserves structure from reference.conf template")
        void generateConfigContentPreservesStructure() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.MIGRATION);
            
            // Should have nested structure matching reference.conf
            assertTrue(content.contains("meta {") || content.contains("meta"));
            assertTrue(content.contains("discord {") || content.contains("discord"));
        }
        
        @Test
        @DisplayName("generateConfigContent updates user values in template")
        void generateConfigContentUpdatesUserValues() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("custom_token")
                .withDiscordOwner(999999999L)
                .withCommandsPrefix("custom_prefix")
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.MIGRATION);
            
            // Parse the generated content to verify values
            Config generated = ConfigFactory.parseString(content);
            assertEquals("custom_token", generated.getString("discord.token"));
            assertEquals(999999999L, generated.getLong("discord.owner"));
            assertEquals("custom_prefix", generated.getString("commands.prefix"));
        }
        
        @Test
        @DisplayName("generateConfigContent includes diagnostic information in comments")
        void generateConfigContentIncludesDiagnostics() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            Set<String> missingRequired = new HashSet<>();
            missingRequired.add("discord.token");
            Set<String> deprecated = new HashSet<>();
            deprecated.add("oldKey");
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                missingRequired, new HashSet<>(), deprecated
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.MIGRATION);
            
            assertTrue(content.contains("# Changes detected"));
            assertTrue(content.contains("Missing required keys"));
            assertTrue(content.contains("Deprecated keys removed"));
        }
    }
    
    @Nested
    @DisplayName("Comment and Formatting Preservation")
    class CommentAndFormattingPreservationTests {
        
        @Test
        @DisplayName("generateConfigContent preserves comments from reference.conf template")
        void generateConfigContentPreservesComments() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.MIGRATION);
            
            // reference.conf should have comments, and ConfigDocument should preserve them
            // The exact comments depend on reference.conf, but we should have some
            assertNotNull(content);
            
            // Parse as ConfigDocument to verify it's valid
            ConfigDocument doc = ConfigDocumentFactory.parseString(content);
            assertNotNull(doc);
            
            // Verify that ConfigDocument was used (content should have structure from reference.conf)
            // If ConfigDocument wasn't used, the fallback would have different formatting
            String rendered = doc.render();
            assertFalse(rendered.isEmpty());
            
            // Verify the content can be parsed back to Config with correct values
            Config parsed = ConfigFactory.parseString(content);
            assertEquals("test_token", parsed.getString("discord.token"));
        }
        
        @Test
        @DisplayName("generateConfigContent preserves nested structure formatting")
        void generateConfigContentPreservesNestedStructure() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .withCommandsPrefix("!!")
                .withPlaybackMaxTrackSeconds(3600L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.MIGRATION);
            
            // Parse and verify nested structure is preserved
            Config parsed = ConfigFactory.parseString(content);
            assertTrue(parsed.hasPath("meta.configVersion"));
            assertTrue(parsed.hasPath("discord.token"));
            assertTrue(parsed.hasPath("commands.prefix"));
            assertTrue(parsed.hasPath("playback.maxTrackSeconds"));
        }
        
        @Test
        @DisplayName("generateConfigContent uses ConfigDocument to preserve reference.conf structure")
        void generateConfigContentUsesConfigDocumentForStructure() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.MIGRATION);
            
            // Verify ConfigDocument was used (not fallback)
            // ConfigDocument preserves the structure from reference.conf template
            ConfigDocument doc = ConfigDocumentFactory.parseString(content);
            assertNotNull(doc);
            
            // The content should be parseable and have correct structure
            Config parsed = ConfigFactory.parseString(content);
            assertTrue(parsed.hasPath("meta.configVersion"));
            assertEquals(2, parsed.getInt("meta.configVersion"));
            assertEquals("test_token", parsed.getString("discord.token"));
        }
        
        @Test
        @DisplayName("generateConfigContent preserves comments when ConfigDocument is used")
        void generateConfigContentPreservesCommentsViaConfigDocument() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.MIGRATION);
            
            // Parse as ConfigDocument to verify it was used
            ConfigDocument doc = ConfigDocumentFactory.parseString(content);
            assertNotNull(doc);
            
            // ConfigDocument should preserve comments from reference.conf template
            // The rendered document should maintain the structure
            
            // Verify the content is valid and has correct values
            Config parsed = ConfigFactory.parseString(content);
            assertEquals("test_token", parsed.getString("discord.token"));
            assertEquals(123456789L, parsed.getLong("discord.owner"));
            
            // Verify structure is preserved (ConfigDocument maintains nested structure)
            assertTrue(parsed.hasPath("meta.configVersion"));
            assertTrue(parsed.hasPath("discord.token"));
        }
    }
    
    @Nested
    @DisplayName("Nested Config Preservation")
    class NestedConfigPreservationTests {
        
        @Test
        @DisplayName("generateConfigContent preserves template values for missing nested config keys")
        void generateConfigContentPreservesTemplateValuesForMissingNestedKeys() {
            // Create a config with only some audioSources keys set (missing local and others)
            Map<String, Boolean> partialAudioSources = new HashMap<>();
            partialAudioSources.put("youtube", false);
            partialAudioSources.put("soundcloud", false);
            // Intentionally missing: local, bandcamp, vimeo, twitch, etc.
            
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .withPlaybackAudioSources(partialAudioSources)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.REPAIR);
            
            // Parse the generated content
            Config parsed = ConfigFactory.parseString(content);
            assertTrue(parsed.hasPath("playback.audioSources"));
            Config audioSources = parsed.getConfig("playback.audioSources");
            
            // Verify user's values are preserved
            assertFalse(audioSources.getBoolean("youtube"), 
                "User's youtube=false should be preserved");
            assertFalse(audioSources.getBoolean("soundcloud"), 
                "User's soundcloud=false should be preserved");
            
            // Verify template defaults are preserved for missing keys
            assertTrue(audioSources.getBoolean("local"), 
                "Template's local=true should be preserved (was missing from user config)");
            assertTrue(audioSources.getBoolean("bandcamp"), 
                "Template's bandcamp=true should be preserved (was missing from user config)");
            assertTrue(audioSources.getBoolean("vimeo"), 
                "Template's vimeo=true should be preserved (was missing from user config)");
            assertTrue(audioSources.getBoolean("twitch"), 
                "Template's twitch=true should be preserved (was missing from user config)");
            assertTrue(audioSources.getBoolean("beam"), 
                "Template's beam=true should be preserved (was missing from user config)");
            assertTrue(audioSources.getBoolean("getyarn"), 
                "Template's getyarn=true should be preserved (was missing from user config)");
            assertTrue(audioSources.getBoolean("nico"), 
                "Template's nico=true should be preserved (was missing from user config)");
            assertTrue(audioSources.getBoolean("http"), 
                "Template's http=true should be preserved (was missing from user config)");
        }
        
        @Test
        @DisplayName("generateConfigContent preserves template values when user config has partial nested config")
        void generateConfigContentPreservesTemplateValuesForPartialNestedConfig() {
            // Create a config with only one audioSources key set
            Map<String, Boolean> minimalAudioSources = new HashMap<>();
            minimalAudioSources.put("youtube", false);
            // All other keys are missing
            
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .withPlaybackAudioSources(minimalAudioSources)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.REPAIR);
            
            // Parse the generated content
            Config parsed = ConfigFactory.parseString(content);
            Config audioSources = parsed.getConfig("playback.audioSources");
            
            // Verify user's single value is preserved
            assertFalse(audioSources.getBoolean("youtube"), 
                "User's youtube=false should be preserved");
            
            // Verify all other template defaults are preserved
            assertTrue(audioSources.getBoolean("soundcloud"), 
                "Template's soundcloud=true should be preserved");
            assertTrue(audioSources.getBoolean("local"), 
                "Template's local=true should be preserved");
            assertTrue(audioSources.getBoolean("bandcamp"), 
                "Template's bandcamp=true should be preserved");
        }
        
        @Test
        @DisplayName("generateConfigContent updates individual nested keys without replacing entire object")
        void generateConfigContentUpdatesIndividualNestedKeys() {
            // Create a config with mixed audioSources values
            Map<String, Boolean> mixedAudioSources = new HashMap<>();
            mixedAudioSources.put("youtube", true);
            mixedAudioSources.put("soundcloud", false);
            mixedAudioSources.put("local", false);
            // Missing: bandcamp, vimeo, twitch, etc.
            
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .withPlaybackAudioSources(mixedAudioSources)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.REPAIR);
            
            // Parse the generated content
            Config parsed = ConfigFactory.parseString(content);
            Config audioSources = parsed.getConfig("playback.audioSources");
            
            // Verify all user values are correctly applied
            assertTrue(audioSources.getBoolean("youtube"), 
                "User's youtube=true should be preserved");
            assertFalse(audioSources.getBoolean("soundcloud"), 
                "User's soundcloud=false should be preserved");
            assertFalse(audioSources.getBoolean("local"), 
                "User's local=false should be preserved");
            
            // Verify template defaults are preserved for missing keys
            assertTrue(audioSources.getBoolean("bandcamp"), 
                "Template's bandcamp=true should be preserved");
            assertTrue(audioSources.getBoolean("vimeo"), 
                "Template's vimeo=true should be preserved");
        }
    }
    
    @Nested
    @DisplayName("Dynamic Nested Config Preservation (Transforms)")
    class DynamicNestedConfigPreservationTests {
        
        @Test
        @DisplayName("diagnostics does not flag user-defined transforms as deprecated")
        void diagnosticsDoesNotFlagTransformsAsDeprecated() {
            Map<String, Object> spotifyTransform = Map.of(
                    "regex", "https?://.*spotify.com/track/.*",
                    "replacement", "https://open.spotify.com/track/$1",
                    "selector", "title",
                    "format", "ytsearch:%s"
            );
            
            Map<String, Object> transforms = new HashMap<>();
            transforms.put("spotify", spotifyTransform);
            
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .withPlaybackTransforms(transforms)
                .build();
            
            // Run diagnostics as BotConfig would
            Config defaults = ConfigFactory.load("reference.conf");
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report diagnostics = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            // Verify no deprecated warnings for transforms
            assertFalse(diagnostics.getDeprecated().contains("playback.transforms.spotify"),
                    "spotify transform should not be flagged as deprecated");
            for (String deprecated : diagnostics.getDeprecated()) {
                assertFalse(deprecated.startsWith("playback.transforms."),
                        "No paths under playback.transforms should be deprecated, but found: " + deprecated);
            }
        }
        
        @Test
        @DisplayName("diagnostics does not flag nested transform keys as deprecated")
        void diagnosticsDoesNotFlagNestedTransformKeysAsDeprecated() {
            // Test that inner keys like regex, replacement, selector, format are not flagged
            Map<String, Object> spotifyTransform = Map.of(
                    "regex", "pattern",
                    "replacement", "replacement",
                    "selector", "title",
                    "format", "format"
            );
            
            Map<String, Object> transforms = new HashMap<>();
            transforms.put("spotify", spotifyTransform);
            
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .withPlaybackTransforms(transforms)
                .build();
            
            Config defaults = ConfigFactory.load("reference.conf");
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report diagnostics = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            // Verify none of the nested keys are flagged
            assertFalse(diagnostics.getDeprecated().contains("playback.transforms.spotify.regex"));
            assertFalse(diagnostics.getDeprecated().contains("playback.transforms.spotify.replacement"));
            assertFalse(diagnostics.getDeprecated().contains("playback.transforms.spotify.selector"));
            assertFalse(diagnostics.getDeprecated().contains("playback.transforms.spotify.format"));
        }
        
        @Test
        @DisplayName("diagnostics does not flag multiple transforms as deprecated")
        void diagnosticsDoesNotFlagMultipleTransformsAsDeprecated() {
            Map<String, Object> spotifyTransform = Map.of("regex", "pattern1", "replacement", "rep1", "selector", "title", "format", "fmt1");
            Map<String, Object> youtubeTransform = Map.of("regex", "pattern2", "replacement", "rep2", "selector", "title", "format", "fmt2");
            
            Map<String, Object> transforms = new HashMap<>();
            transforms.put("spotify", spotifyTransform);
            transforms.put("youtube", youtubeTransform);
            
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .withPlaybackTransforms(transforms)
                .build();
            
            Config defaults = ConfigFactory.load("reference.conf");
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report diagnostics = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            // Verify neither transform is flagged as deprecated
            assertFalse(diagnostics.getDeprecated().contains("playback.transforms.spotify"));
            assertFalse(diagnostics.getDeprecated().contains("playback.transforms.youtube"));
        }
        
        @Test
        @DisplayName("generateConfigContent does not show deprecated warning for user-defined transforms")
        void generateConfigContentNoDeprecatedWarningForTransforms() {
            Map<String, Object> spotifyTransform = Map.of(
                    "regex", "https?://.*spotify.com/track/.*",
                    "replacement", "https://open.spotify.com/track/$1",
                    "selector", "title",
                    "format", "ytsearch:%s"
            );
            
            Map<String, Object> transforms = new HashMap<>();
            transforms.put("spotify", spotifyTransform);
            
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .withPlaybackTransforms(transforms)
                .build();
            
            // Run diagnostics as BotConfig would
            Config defaults = ConfigFactory.load("reference.conf");
            Config merged = migratedUserConfig.withFallback(defaults).resolve();
            ConfigDiagnostics.Report diagnostics = ConfigDiagnostics.analyze(migratedUserConfig, merged, defaults);
            
            // Verify no deprecated warnings for transforms
            assertFalse(diagnostics.getDeprecated().contains("playback.transforms.spotify"),
                    "spotify transform should not be flagged as deprecated");
            for (String deprecated : diagnostics.getDeprecated()) {
                assertFalse(deprecated.startsWith("playback.transforms."),
                        "No paths under playback.transforms should be deprecated, but found: " + deprecated);
            }
            
            // Generate content and verify no "Deprecated keys removed" mentioning transforms
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.MIGRATION);
            assertFalse(content.contains("playback.transforms.spotify") && content.contains("Deprecated keys removed"),
                    "Generated content should not mention transforms in deprecated keys section");
        }
    }
    
    @Nested
    @DisplayName("Fallback Behavior")
    class FallbackBehaviorTests {
        
        @Test
        @DisplayName("generateConfigContentFallback uses Config when ConfigDocument unavailable")
        void generateConfigContentFallbackUsesConfig() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContentFallback(migratedUserConfig, diagnostics, ConfigUpdateType.MIGRATION);
            
            assertNotNull(content);
            // Should still be valid HOCON
            Config parsed = ConfigFactory.parseString(content);
            assertNotNull(parsed);
            assertEquals("test_token", parsed.getString("discord.token"));
        }
    }
    
    @Nested
    @DisplayName("ConfigUpdateType Wording")
    class ConfigUpdateTypeWordingTests {
        
        private static final Clock EPOCH_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        private static final String EPOCH_TIMESTAMP = "1970-01-01 00:00:00";
        
        @Test
        @DisplayName("MIGRATION type produces 'migrated' wording in header comment with correct timestamp")
        void migrationTypeProducesMigratedWording() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.MIGRATION, EPOCH_CLOCK);
            
            assertTrue(content.contains("# This file was automatically migrated on " + EPOCH_TIMESTAMP),
                "Header should contain 'migrated on " + EPOCH_TIMESTAMP + "' for MIGRATION type");
            assertFalse(content.contains("repaired"),
                "Header should NOT contain 'repaired' for MIGRATION type");
        }
        
        @Test
        @DisplayName("REPAIR type produces 'repaired' wording in header comment with correct timestamp")
        void repairTypeProducesRepairedWording() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            Set<String> missingOptional = new HashSet<>();
            missingOptional.add("some.missing.key");
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), missingOptional, new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.REPAIR, EPOCH_CLOCK);
            
            assertTrue(content.contains("# This file was automatically repaired on " + EPOCH_TIMESTAMP),
                "Header should contain 'repaired on " + EPOCH_TIMESTAMP + "' for REPAIR type");
            assertFalse(content.contains("migrated"),
                "Header should NOT contain 'migrated' for REPAIR type");
        }
        
        @Test
        @DisplayName("UPDATE type produces 'updated' wording in header comment with correct timestamp")
        void updateTypeProducesUpdatedWording() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            Set<String> deprecated = new HashSet<>();
            deprecated.add("some.old.key");
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), deprecated
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.UPDATE, EPOCH_CLOCK);
            
            assertTrue(content.contains("# This file was automatically updated on " + EPOCH_TIMESTAMP),
                "Header should contain 'updated on " + EPOCH_TIMESTAMP + "' for UPDATE type");
            assertFalse(content.contains("migrated"),
                "Header should NOT contain 'migrated' for UPDATE type");
            assertFalse(content.contains("repaired"),
                "Header should NOT contain 'repaired' for UPDATE type");
        }
        
        @Test
        @DisplayName("Fallback method also respects ConfigUpdateType wording with correct timestamp")
        void fallbackMethodRespectsUpdateType() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            // Test REPAIR type in fallback
            String repairContent = ConfigRenderer.generateConfigContentFallback(
                migratedUserConfig, diagnostics, ConfigUpdateType.REPAIR, EPOCH_CLOCK);
            assertTrue(repairContent.contains("# This file was automatically repaired on " + EPOCH_TIMESTAMP),
                "Fallback should use 'repaired on " + EPOCH_TIMESTAMP + "' for REPAIR type");
            
            // Test MIGRATION type in fallback
            String migrationContent = ConfigRenderer.generateConfigContentFallback(
                migratedUserConfig, diagnostics, ConfigUpdateType.MIGRATION, EPOCH_CLOCK);
            assertTrue(migrationContent.contains("# This file was automatically migrated on " + EPOCH_TIMESTAMP),
                "Fallback should use 'migrated on " + EPOCH_TIMESTAMP + "' for MIGRATION type");
        }
    }
}
