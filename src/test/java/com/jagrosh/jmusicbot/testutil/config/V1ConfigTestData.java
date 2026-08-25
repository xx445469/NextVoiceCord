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
 * Predefined test data sets for v1 configs.
 * 
 * <p>Provides factory methods for common v1 config scenarios used in tests.
 * These are based on the reference.conf file structure.
 */
public class V1ConfigTestData {
    
    /**
     * Creates a minimal valid v1 config with only required fields.
     */
    public static Config minimal() {
        return V1ConfigBuilder.create()
            .withMetaVersion(1)
            .withDiscordToken("test_token_12345")
            .withDiscordOwner(123456789L)
            .build();
    }
    
    /**
     * Creates a full v1 config with all common sections populated.
     */
    public static Config full() {
        return V1ConfigBuilder.create()
            .withMetaVersion(1)
            .withDiscordToken("test_token_12345")
            .withDiscordOwner(123456789L)
            .withCommandsPrefix("@mention")
            .withCommandsAltPrefix("NONE")
            .withCommandsHelp("help")
            .withPresenceGame("DEFAULT")
            .withPresenceStatus("ONLINE")
            .withPresenceSongInStatus(false)
            .withUIEmojisSuccess("🎶")
            .withUIEmojisWarning("💡")
            .withUIEmojisError("🚫")
            .withUIEmojisLoading("⌚")
            .withUIEmojisSearching("🔎")
            .withNowPlayingImages(false)
            .withVoiceStayInChannel(false)
            .withVoiceAloneTimeUntilStopSeconds(0L)
            .withPlaybackMaxTrackSeconds(0L)
            .withPlaybackMaxYouTubePlaylistPages(10)
            .withPlaybackSkipRatio(0.55)
            .withPlaybackYouTubeUseOAuth(false)
            .withPlaybackAudioSources(Map.of(
                "youtube", true,
                "soundcloud", true,
                "local", false,
                "bandcamp", false
            ))
            .withPathsPlaylistsFolder("Playlists")
            .withUpdatesAlerts(true)
            .withLoggingLevel("info")
            .withDangerousEval(false)
            .withDangerousEvalEngine("Nashorn")
            .build();
    }
    
    /**
     * Creates a v1 config with all sections populated (for comprehensive testing).
     */
    public static Config withAllSections() {
        return V1ConfigBuilder.create()
            .withMetaVersion(1)
            .withDiscordToken("test_token_12345")
            .withDiscordOwner(123456789L)
            .withCommandsPrefix("!!")
            .withCommandsAltPrefix("??")
            .withCommandsHelp("help")
            .withCommandsAliases(Map.of(
                "play", List.of("p", "playmusic"),
                "skip", List.of("voteskip", "vs")
            ))
            .withPresenceGame("Playing music")
            .withPresenceStatus("IDLE")
            .withPresenceSongInStatus(true)
            .withUIEmojisSuccess("✅")
            .withUIEmojisWarning("⚠️")
            .withUIEmojisError("❌")
            .withUIEmojisLoading("⏳")
            .withUIEmojisSearching("🔍")
            .withNowPlayingImages(true)
            .withVoiceStayInChannel(true)
            .withVoiceAloneTimeUntilStopSeconds(300L)
            .withPlaybackMaxTrackSeconds(3600L)
            .withPlaybackMaxYouTubePlaylistPages(20)
            .withPlaybackSkipRatio(0.75)
            .withPlaybackYouTubeUseOAuth(true)
            .withPlaybackAudioSources(Map.of(
                "youtube", true,
                "soundcloud", true,
                "local", true,
                "bandcamp", false
            ))
            .withPlaybackTransforms(Map.of())
            .withPathsPlaylistsFolder("CustomPlaylists")
            .withUpdatesAlerts(false)
            .withLoggingLevel("debug")
            .withDangerousEval(false)
            .withDangerousEvalEngine("Nashorn")
            .build();
    }
    
    /**
     * Creates a v1 config for integration testing.
     */
    public static Config integrationTest() {
        return V1ConfigBuilder.create()
            .withMetaVersion(1)
            .withDiscordToken("integration_test_token")
            .withDiscordOwner(987654321L)
            .withCommandsPrefix("!!")
            .withCommandsAltPrefix("NONE")
            .withCommandsHelp("help")
            .build();
    }
    
    /**
     * Creates a v1 config matching reference.conf defaults.
     * This is useful for testing against the actual reference configuration.
     */
    public static Config withReferenceDefaults() {
        return V1ConfigBuilder.withReferenceDefaults();
    }
    
    /**
     * Creates a minimal valid v1 config as ConfigDocument (primary method).
     */
    public static ConfigDocument minimalDocument() {
        return V1ConfigBuilder.create()
            .withMetaVersion(1)
            .withDiscordToken("test_token_12345")
            .withDiscordOwner(123456789L)
            .buildDocument();
    }
    
    /**
     * Creates a v1 config as ConfigDocument matching reference.conf defaults.
     * Note: This converts Config to ConfigDocument, which may not preserve all formatting.
     * For best results, use the builder's buildDocument() method directly.
     */
    public static ConfigDocument withReferenceDefaultsDocument() {
        Config config = V1ConfigBuilder.withReferenceDefaults();
        // Convert Config to ConfigDocument via string representation
        return com.typesafe.config.parser.ConfigDocumentFactory.parseString(
            config.root().render()
        );
    }
}
