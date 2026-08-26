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
package com.jagrosh.jmusicbot.unit.web;

import com.jagrosh.jmusicbot.web.WebSecrets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms a Lavalink node's {@code password} is protected the same way {@code proxy.password}
 * and the Spotify keys already are, per {@link WebSecrets}'s generic leaf-name matching - see
 * that class's javadoc for why a leaf named {@code password}/{@code token}/{@code secret}/
 * {@code apikey} is always treated as a credential regardless of where in the config tree it is.
 *
 * <p>{@code lavalink.nodes} itself is deliberately not exposed by the web panel at all yet (see
 * {@code WebData.HIDDEN_KEYS}), so nothing here exercises that path directly - these tests
 * confirm the underlying rule these hypothetical per-node keys would fall under, the same rule
 * {@code proxy.password} already relies on today.
 */
@DisplayName("WebSecrets: Lavalink node credential coverage")
class WebSecretsLavalinkTest
{
    @ParameterizedTest(name = "''{0}'' is treated as secret")
    @ValueSource(strings = {
            "lavalink.nodes.password",
            "lavalink.nodes[0].password",
            "lavalink.main.password",
            "password"
    })
    @DisplayName("any key whose leaf is 'password' is masked, the same as proxy.password")
    void nodePasswordStyleKeysAreSecret(String key)
    {
        assertTrue(WebSecrets.isSecret(key));
    }

    @Test
    @DisplayName("forDisplay masks a configured node password instead of showing it")
    void forDisplayMasksNodePassword()
    {
        String masked = WebSecrets.forDisplay("lavalink.nodes[0].password", "youshallnotpass");

        assertEquals(WebSecrets.MASK, masked);
        assertTrue(!masked.contains("youshallnotpass"));
    }

    @Test
    @DisplayName("forDisplay shows empty (not the mask) when no password is set, distinguishing unset from hidden")
    void forDisplayShowsEmptyWhenUnset()
    {
        assertEquals("", WebSecrets.forDisplay("lavalink.nodes[0].password", ""));
        assertEquals("", WebSecrets.forDisplay("lavalink.nodes[0].password", null));
    }

    @Test
    @DisplayName("a host/port/name/secure field is not treated as secret")
    void nonCredentialNodeFieldsAreNotSecret()
    {
        assertEquals(false, WebSecrets.isSecret("lavalink.nodes.host"));
        assertEquals(false, WebSecrets.isSecret("lavalink.nodes.port"));
        assertEquals(false, WebSecrets.isSecret("lavalink.nodes.name"));
        assertEquals(false, WebSecrets.isSecret("lavalink.nodes.secure"));
    }
}
