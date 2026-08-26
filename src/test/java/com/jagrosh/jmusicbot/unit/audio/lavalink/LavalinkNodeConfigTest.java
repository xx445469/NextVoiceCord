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
package com.jagrosh.jmusicbot.unit.audio.lavalink;

import java.util.List;

import com.jagrosh.jmusicbot.audio.lavalink.LavalinkNodeConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("LavalinkNodeConfig.parseList")
class LavalinkNodeConfigTest
{
    private final Logger logger = mock(Logger.class);

    @Test
    @DisplayName("missing lavalink.nodes path returns an empty list without logging")
    void missingPathReturnsEmptyList()
    {
        Config config = ConfigFactory.parseString("something.else = 1");

        List<LavalinkNodeConfig> nodes = LavalinkNodeConfig.parseList(config, logger);

        assertTrue(nodes.isEmpty());
        verifyNoInteractions(logger);
    }

    @Test
    @DisplayName("parses the documented default node exactly")
    void parsesDefaultNode()
    {
        Config config = ConfigFactory.parseString("""
                lavalink.nodes = [
                  { name = "main", host = "localhost", port = 2333, password = "youshallnotpass", secure = false }
                ]
                """);

        List<LavalinkNodeConfig> nodes = LavalinkNodeConfig.parseList(config, logger);

        assertEquals(1, nodes.size());
        LavalinkNodeConfig node = nodes.get(0);
        assertEquals("main", node.name());
        assertEquals("localhost", node.host());
        assertEquals(2333, node.port());
        assertEquals("youshallnotpass", node.password());
        assertFalse(node.secure());
        assertEquals("http://localhost:2333", node.httpBaseUrl());
        assertEquals("ws://localhost:2333/v4/websocket", node.webSocketUrl());
    }

    @Test
    @DisplayName("secure = true selects https/wss")
    void secureSelectsHttpsAndWss()
    {
        Config config = ConfigFactory.parseString("""
                lavalink.nodes = [
                  { name = "main", host = "lavalink.example.com", port = 443, password = "x", secure = true }
                ]
                """);

        LavalinkNodeConfig node = LavalinkNodeConfig.parseList(config, logger).get(0);

        assertEquals("https://lavalink.example.com:443", node.httpBaseUrl());
        assertEquals("wss://lavalink.example.com:443/v4/websocket", node.webSocketUrl());
    }

    @Test
    @DisplayName("parses multiple nodes in order")
    void parsesMultipleNodesInOrder()
    {
        Config config = ConfigFactory.parseString("""
                lavalink.nodes = [
                  { name = "first", host = "a.example.com", port = 1, password = "p" }
                  { name = "second", host = "b.example.com", port = 2, password = "p" }
                ]
                """);

        List<LavalinkNodeConfig> nodes = LavalinkNodeConfig.parseList(config, logger);

        assertEquals(2, nodes.size());
        assertEquals("first", nodes.get(0).name());
        assertEquals("second", nodes.get(1).name());
    }

    @Test
    @DisplayName("a node with a blank host is skipped with a logged error, not thrown")
    void blankHostIsSkipped()
    {
        Config config = ConfigFactory.parseString("""
                lavalink.nodes = [
                  { name = "bad", host = "", port = 2333, password = "p" }
                ]
                """);

        List<LavalinkNodeConfig> nodes = LavalinkNodeConfig.parseList(config, logger);

        assertTrue(nodes.isEmpty());
        verify(logger).error(org.mockito.ArgumentMatchers.contains("no host"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a node with an out-of-range port is skipped with a logged error, not thrown")
    void invalidPortIsSkipped()
    {
        Config config = ConfigFactory.parseString("""
                lavalink.nodes = [
                  { name = "bad", host = "localhost", port = 70000, password = "p" }
                ]
                """);

        List<LavalinkNodeConfig> nodes = LavalinkNodeConfig.parseList(config, logger);

        assertTrue(nodes.isEmpty());
        verify(logger).error(org.mockito.ArgumentMatchers.contains("invalid port"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("one invalid node does not prevent the others from loading")
    void oneInvalidNodeDoesNotBlockOthers()
    {
        Config config = ConfigFactory.parseString("""
                lavalink.nodes = [
                  { name = "bad", host = "", port = 2333, password = "p" }
                  { name = "good", host = "localhost", port = 2333, password = "p" }
                ]
                """);

        List<LavalinkNodeConfig> nodes = LavalinkNodeConfig.parseList(config, logger);

        assertEquals(1, nodes.size());
        assertEquals("good", nodes.get(0).name());
    }

    @Test
    @DisplayName("a missing name is defaulted to node-<index> rather than failing")
    void missingNameGetsDefaultLabel()
    {
        Config config = ConfigFactory.parseString("""
                lavalink.nodes = [
                  { host = "localhost", port = 2333, password = "p" }
                ]
                """);

        LavalinkNodeConfig node = LavalinkNodeConfig.parseList(config, logger).get(0);

        assertEquals("node-0", node.name());
    }

    @Test
    @DisplayName("password never appears in describe() or toString()")
    void passwordNeverAppearsInLoggableStrings()
    {
        LavalinkNodeConfig node = new LavalinkNodeConfig("main", "localhost", 2333, "super-secret-password", false);

        assertFalse(node.describe().contains("super-secret-password"));
        assertFalse(node.toString().contains("super-secret-password"));
        // describe() is genuinely useful for logs: it still names the node.
        assertTrue(node.describe().contains("main"));
        assertTrue(node.describe().contains("localhost"));
    }

    @Test
    @DisplayName("password is not accidentally omitted from parsing itself (only from logging)")
    void passwordIsActuallyParsed()
    {
        Config config = ConfigFactory.parseString("""
                lavalink.nodes = [
                  { name = "main", host = "localhost", port = 2333, password = "youshallnotpass" }
                ]
                """);

        LavalinkNodeConfig node = LavalinkNodeConfig.parseList(config, logger).get(0);

        assertEquals("youshallnotpass", node.password());
    }
}
