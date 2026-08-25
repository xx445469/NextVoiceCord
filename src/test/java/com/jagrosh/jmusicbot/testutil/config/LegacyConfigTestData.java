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
import com.typesafe.config.parser.ConfigDocument;

import java.util.List;
import java.util.Map;

/**
 * Predefined test data sets for legacy configs.
 * 
 * <p>Provides factory methods for common legacy config scenarios used in tests.
 * These are based on the reference-legacy.conf file structure.
 */
public class LegacyConfigTestData {
    
    /**
     * Creates a minimal valid legacy config with only required fields.
     */
    public static Config minimal() {
        return LegacyConfigBuilder.create()
            .withToken("test_token_12345")
            .withOwner(123456789L)
            .build();
    }
    
    /**
     * Creates a full legacy config with all common fields set.
     */
    public static Config full() {
        return LegacyConfigBuilder.create()
            .withToken("test_token_12345")
            .withOwner(123456789L)
            .withPrefix("@mention")
            .withAltPrefix("NONE")
            .withHelp("help")
            .withGame("DEFAULT")
            .withStatus("ONLINE")
            .withSongInStatus(false)
            .withSuccess("🎶")
            .withWarning("💡")
            .withError("🚫")
            .withLoading("⌚")
            .withSearching("🔎")
            .withStayInChannel(false)
            .withMaxTime(0L)
            .withSkipRatio(0.55)
            .withLogLevel("info")
            .withEval(false)
            .withEvalEngine("Nashorn")
            .withUseYouTubeOAuth(false)
            .withMaxYTPlaylistPages(10)
            .withAloneTimeUntilStop(0L)
            .withPlaylistsFolder("Playlists")
            .withUpdateAlerts(true)
            .withNPImages(false)
            .withAudioSources("youtube", "soundcloud")
            .build();
    }
    
    /**
     * Creates a legacy config with audio sources.
     */
    public static Config withAudioSources(String... sources) {
        return LegacyConfigBuilder.create()
            .withToken("test_token_12345")
            .withOwner(123456789L)
            .withAudioSources(sources)
            .build();
    }
    
    /**
     * Creates a legacy config with aliases.
     */
    public static Config withAliases(Map<String, List<String>> aliases) {
        return LegacyConfigBuilder.create()
            .withToken("test_token_12345")
            .withOwner(123456789L)
            .withAliases(aliases)
            .build();
    }
    
    /**
     * Creates a legacy config with invalid token placeholder.
     */
    public static Config withInvalidToken() {
        return LegacyConfigBuilder.create()
            .withToken("BOT_TOKEN_HERE")
            .withOwner(123456789L)
            .build();
    }
    
    /**
     * Creates a legacy config with invalid owner (zero).
     */
    public static Config withInvalidOwner() {
        return LegacyConfigBuilder.create()
            .withToken("test_token_12345")
            .withOwner(0L)
            .build();
    }
    
    /**
     * Creates a legacy config with missing required fields (only optional fields).
     */
    public static Config withMissingRequired() {
        return LegacyConfigBuilder.create()
            .withPrefix("@mention")
            .build();
    }
    
    /**
     * Creates a legacy config for integration testing with common values.
     */
    public static Config integrationTest() {
        return LegacyConfigBuilder.create()
            .withToken("integration_test_token")
            .withOwner(987654321L)
            .withPrefix("!!")
            .withAltPrefix("NONE")
            .withHelp("commands")
            .withGame("DEFAULT")
            .withStatus("ONLINE")
            .build();
    }
    
    /**
     * Creates a legacy config matching reference-legacy.conf defaults.
     * This is useful for testing against the actual reference configuration.
     */
    public static Config withReferenceDefaults() {
        return LegacyConfigBuilder.withReferenceDefaults();
    }
    
    /**
     * Creates a minimal valid legacy config as ConfigDocument (primary method).
     */
    public static ConfigDocument minimalDocument() {
        return LegacyConfigBuilder.create()
            .withToken("test_token_12345")
            .withOwner(123456789L)
            .buildDocument();
    }
    
    /**
     * Creates a legacy config as ConfigDocument matching reference-legacy.conf defaults.
     * Note: This converts Config to ConfigDocument, which may not preserve all formatting.
     * For best results, use the builder's buildDocument() method directly.
     */
    public static ConfigDocument withReferenceDefaultsDocument() {
        Config config = LegacyConfigBuilder.withReferenceDefaults();
        // Convert Config to ConfigDocument via string representation
        return com.typesafe.config.parser.ConfigDocumentFactory.parseString(
            config.root().render()
        );
    }
}
