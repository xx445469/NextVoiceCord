/*
 * Copyright 2026 adan (xx445469) - NextVoiceCord
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
package com.jagrosh.jmusicbot.unit;

import java.io.IOException;
import java.nio.file.Path;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.lavalink.LavalinkNodeConfig;
import com.jagrosh.jmusicbot.config.model.PlaybackEngine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@code playback.engine} / {@code lavalink.nodes} parsing in {@link BotConfig}.
 *
 * <p>The single most important case here is {@link DefaultWiring#omittingPlaybackEngineIsLavaplayer()}:
 * stage 1's hardest constraint is that a config with no {@code playback} engine opinion at all -
 * which is every config.txt in production today - must come out wired exactly as it did before
 * this feature existed.
 *
 * @author adan (xx445469)
 */
@DisplayName("BotConfig: Lavalink stage 1")
class BotConfigLavalinkUnitTest extends BaseConfigTest {

    @Override
    @org.junit.jupiter.api.BeforeEach
    protected void setUpBase() {
        super.setUpBase();
        MockitoAnnotations.openMocks(this);
    }

    private static final String BASE = """
            meta {
              configVersion = 2
            }
            discord.token = test_token
            discord.owner = 123456789
            """;

    @Nested
    @DisplayName("default wiring (playback.engine unset)")
    class DefaultWiring {

        @Test
        @DisplayName("a config with no playback.engine at all resolves to lavaplayer")
        void omittingPlaybackEngineIsLavaplayer() throws IOException {
            Path configFile = createTempConfigFile(BASE);
            setConfigFileProperty(configFile);

            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();

            assertTrue(config.isValid());
            assertEquals(PlaybackEngine.LAVAPLAYER, config.getPlaybackEngine());
            assertFalse(config.isLavalinkMode());
            // Deliberately not asserting getLavalinkNodes() here: a config this minimal has many
            // keys missing from reference.conf's defaults (not just playback/lavalink ones),
            // which triggers BotConfig's existing config-repair/regeneration path (see
            // ConfigDiagnostics/ConfigUpdater) - and reference.conf ships a demo lavalink.nodes
            // entry, so the regenerated file may or may not end up with one depending on that
            // unrelated mechanism. What must hold regardless - and is asserted above - is that
            // the *engine* stays lavaplayer, since getLavalinkNodes() is never even consulted
            // when it does (see Bot's constructor).
        }

        @Test
        @DisplayName("explicit playback.engine = lavaplayer behaves identically to omitting it")
        void explicitLavaplayerMatchesDefault() throws IOException {
            Path configFile = createTempConfigFile(BASE + "\nplayback.engine = \"lavaplayer\"\n");
            setConfigFileProperty(configFile);

            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();

            assertEquals(PlaybackEngine.LAVAPLAYER, config.getPlaybackEngine());
            assertFalse(config.isLavalinkMode());
        }
    }

    @Nested
    @DisplayName("playback.engine = lavalink")
    class LavalinkWiring {

        @Test
        @DisplayName("with a valid node, resolves to lavalink and parses the node")
        void validNodeResolvesToLavalink() throws IOException {
            String content = BASE + """
                    playback.engine = "lavalink"
                    lavalink.nodes = [
                      { name = "main", host = "localhost", port = 2333, password = "youshallnotpass", secure = false }
                    ]
                    """;
            Path configFile = createTempConfigFile(content);
            setConfigFileProperty(configFile);

            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();

            assertTrue(config.isValid());
            assertEquals(PlaybackEngine.LAVALINK, config.getPlaybackEngine());
            assertTrue(config.isLavalinkMode());
            assertEquals(1, config.getLavalinkNodes().size());
            LavalinkNodeConfig node = config.getLavalinkNodes().get(0);
            assertEquals("localhost", node.host());
            assertEquals(2333, node.port());
        }

        @Test
        @DisplayName("with no configured nodes, falls back to lavaplayer rather than running with nothing to connect to")
        void noNodesFallsBackToLavaplayer() throws IOException {
            String content = BASE + """
                    playback.engine = "lavalink"
                    lavalink.nodes = []
                    """;
            Path configFile = createTempConfigFile(content);
            setConfigFileProperty(configFile);

            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();

            assertTrue(config.isValid());
            assertEquals(PlaybackEngine.LAVAPLAYER, config.getPlaybackEngine());
            assertFalse(config.isLavalinkMode());
        }
    }

    @Nested
    @DisplayName("playback.engine = fallback (stage 3, not implemented)")
    class FallbackWiring {

        @Test
        @DisplayName("is accepted rather than crashing config load, and resolves to lavaplayer")
        void fallbackResolvesToLavaplayer() throws IOException {
            Path configFile = createTempConfigFile(BASE + "\nplayback.engine = \"fallback\"\n");
            setConfigFileProperty(configFile);

            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();

            assertTrue(config.isValid());
            assertEquals(PlaybackEngine.LAVAPLAYER, config.getPlaybackEngine());
            assertFalse(config.isLavalinkMode());
        }
    }

    @Nested
    @DisplayName("playback.engine = <garbage>")
    class InvalidValueWiring {

        @Test
        @DisplayName("an unrecognised value does not fail config load; it falls back to lavaplayer")
        void unknownValueFallsBackToLavaplayer() throws IOException {
            Path configFile = createTempConfigFile(BASE + "\nplayback.engine = \"not-a-real-engine\"\n");
            setConfigFileProperty(configFile);

            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();

            assertTrue(config.isValid());
            assertEquals(PlaybackEngine.LAVAPLAYER, config.getPlaybackEngine());
        }
    }
}
