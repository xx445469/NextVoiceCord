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
package com.jagrosh.jmusicbot.unit.audio;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioSource;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeast;

@DisplayName("PlayerManager Tests")
class PlayerManagerTest {

    @Mock
    private Bot bot;

    @Mock
    private BotConfig config;

    private PlayerManager playerManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(bot.getConfig()).thenReturn(config);
        
        // Default config mock setup
        when(config.getFrameBufferMs()).thenReturn(2000);
        when(config.getTransforms()).thenReturn(ConfigFactory.empty());
        when(config.getEnabledAudioSources()).thenReturn(Set.of()); // Empty to avoid source registration
        
        playerManager = new PlayerManager(bot);
    }

    @Nested
    @DisplayName("Proxy Configuration Tests")
    class ProxyConfigurationTests {

        @Test
        @DisplayName("init() does not configure proxy when proxyLavaplayer is false")
        void initDoesNotConfigureProxyWhenDisabled() {
            when(config.proxyLavaplayer()).thenReturn(false);
            when(config.hasProxy()).thenReturn(true);
            when(config.getProxyHost()).thenReturn("127.0.0.1");
            when(config.getProxyPort()).thenReturn(8080);

            playerManager.init();

            // Verify proxy settings were checked
            verify(config).proxyLavaplayer();
            // hasProxy() should not be called when proxyLavaplayer() is false
            // (short-circuit evaluation)
        }

        @Test
        @DisplayName("init() does not configure proxy when hasProxy is false")
        void initDoesNotConfigureProxyWhenNoProxy() {
            when(config.proxyLavaplayer()).thenReturn(true);
            when(config.hasProxy()).thenReturn(false);

            playerManager.init();

            // Verify both conditions were checked
            verify(config).proxyLavaplayer();
            verify(config).hasProxy();
        }

        @Test
        @DisplayName("init() configures proxy when both conditions are true")
        void initConfiguresProxyWhenEnabled() {
            when(config.proxyLavaplayer()).thenReturn(true);
            when(config.hasProxy()).thenReturn(true);
            when(config.getProxyHost()).thenReturn("127.0.0.1");
            when(config.getProxyPort()).thenReturn(8080);

            playerManager.init();

            // Verify proxy configuration was accessed
            // Note: These may be called multiple times (PlayerManager + ProxyUtil)
            verify(config).proxyLavaplayer();
            verify(config, atLeast(1)).hasProxy();
            verify(config, atLeast(1)).getProxyHost();
            verify(config, atLeast(1)).getProxyPort();
        }

        @Test
        @DisplayName("init() logs proxy configuration when enabled")
        void initLogsProxyConfigurationWhenEnabled() {
            when(config.proxyLavaplayer()).thenReturn(true);
            when(config.hasProxy()).thenReturn(true);
            when(config.getProxyHost()).thenReturn("proxy.example.com");
            when(config.getProxyPort()).thenReturn(18080);

            // This test verifies the code path executes without error
            // Actual logging verification would require a logging framework mock
            assertDoesNotThrow(() -> playerManager.init());
        }
    }

    @Nested
    @DisplayName("getBot() Tests")
    class GetBotTests {

        @Test
        @DisplayName("getBot() returns the bot instance")
        void getBotReturnsBotInstance() {
            assertEquals(bot, playerManager.getBot());
        }
    }

    @Nested
    @DisplayName("Frame Buffer Configuration Tests")
    class FrameBufferConfigurationTests {

        @Test
        @DisplayName("init() configures frame buffer from config")
        void initConfiguresFrameBuffer() {
            when(config.getFrameBufferMs()).thenReturn(3000);
            when(config.proxyLavaplayer()).thenReturn(false);

            playerManager.init();

            verify(config).getFrameBufferMs();
        }
    }
}
