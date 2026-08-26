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
package com.jagrosh.jmusicbot.unit.config.model;

import java.util.List;

import com.jagrosh.jmusicbot.config.model.ConfigOption;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ConfigOption: Lavalink entries")
class ConfigOptionLavalinkTest
{
    @Test
    @DisplayName("PLAYBACK_ENGINE is a plain, optional STRING option")
    void playbackEngineIsOptionalString()
    {
        assertEquals("playback.engine", ConfigOption.PLAYBACK_ENGINE.getKey());
        assertEquals(ConfigOption.ConfigType.STRING, ConfigOption.PLAYBACK_ENGINE.getType());
        assertFalse(ConfigOption.PLAYBACK_ENGINE.isRequired());
    }

    @Test
    @DisplayName("LAVALINK_NODES is an optional CONFIG_LIST option")
    void lavalinkNodesIsOptionalConfigList()
    {
        assertEquals("lavalink.nodes", ConfigOption.LAVALINK_NODES.getKey());
        assertEquals(ConfigOption.ConfigType.CONFIG_LIST, ConfigOption.LAVALINK_NODES.getType());
        assertFalse(ConfigOption.LAVALINK_NODES.isRequired());
    }

    @Test
    @DisplayName("getConfigList() reads a list of node objects")
    void getConfigListReadsNodeObjects()
    {
        Config config = ConfigFactory.parseString("""
                lavalink.nodes = [
                  { name = "main", host = "localhost", port = 2333, password = "x" }
                ]
                """);

        List<? extends Config> nodes = ConfigOption.LAVALINK_NODES.getConfigList(config);

        assertEquals(1, nodes.size());
        assertEquals("main", nodes.get(0).getString("name"));
    }

    @Test
    @DisplayName("both are discoverable via findByKey, like every other option")
    void findableByKey()
    {
        assertTrue(ConfigOption.findByKey("playback.engine").isPresent());
        assertTrue(ConfigOption.findByKey("lavalink.nodes").isPresent());
        assertEquals(ConfigOption.PLAYBACK_ENGINE, ConfigOption.findByKey("playback.engine").get());
        assertEquals(ConfigOption.LAVALINK_NODES, ConfigOption.findByKey("lavalink.nodes").get());
    }

    @Test
    @DisplayName("both are included in getAllKeys()/getOptionalKeys()")
    void includedInKeySets()
    {
        assertTrue(ConfigOption.getAllKeys().contains("playback.engine"));
        assertTrue(ConfigOption.getAllKeys().contains("lavalink.nodes"));
        assertTrue(ConfigOption.getOptionalKeys().contains("playback.engine"));
        assertTrue(ConfigOption.getOptionalKeys().contains("lavalink.nodes"));
    }
}
