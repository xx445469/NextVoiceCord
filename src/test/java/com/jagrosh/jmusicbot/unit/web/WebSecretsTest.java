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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Spotify client ID/secret are credentials, the same as the Discord token or the proxy
 * password: the web panel must never send them back, and a masked value written back unchanged
 * must not overwrite the real one.
 */
@DisplayName("WebSecrets: Spotify credentials")
class WebSecretsTest
{
    @Test
    @DisplayName("spotify.clientSecret is recognised as a secret (name match)")
    void clientSecretIsSecretByName()
    {
        assertTrue(WebSecrets.isSecret("spotify.clientSecret"));
    }

    @Test
    @DisplayName("spotify.clientId is recognised as a secret (explicit entry, no keyword in its name)")
    void clientIdIsSecretExplicitly()
    {
        // "clientId" contains none of "token"/"password"/"secret"/"apikey", so this only passes
        // if it is listed explicitly in WebSecrets.
        assertTrue(WebSecrets.isSecret("spotify.clientId"));
    }

    @Test
    @DisplayName("both Spotify credentials are masked for display when set")
    void bothAreMaskedWhenSet()
    {
        assertEquals(WebSecrets.MASK, WebSecrets.forDisplay("spotify.clientId", "real-client-id"));
        assertEquals(WebSecrets.MASK, WebSecrets.forDisplay("spotify.clientSecret", "real-client-secret"));
    }

    @Test
    @DisplayName("an unset Spotify credential displays as empty, not masked")
    void unsetCredentialDisplaysEmpty()
    {
        assertEquals("", WebSecrets.forDisplay("spotify.clientId", ""));
        assertEquals("", WebSecrets.forDisplay("spotify.clientSecret", null));
    }

    @Test
    @DisplayName("the mask coming back unchanged is recognised, so saving does not overwrite the real value")
    void maskRoundTripIsIgnored()
    {
        assertTrue(WebSecrets.isUnchangedMask(WebSecrets.MASK));
        assertFalse(WebSecrets.isUnchangedMask("a-new-client-id"));
    }
}
