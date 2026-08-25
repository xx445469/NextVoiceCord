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

import java.util.HashSet;
import java.util.Set;

import com.jagrosh.jmusicbot.web.WebAuth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The credential guarding the web panel.
 *
 * <p>The panel can see every guild the bot is in, and on a home connection its port may well
 * be reachable from the internet. Everything here is about this being the only thing standing
 * between the two.
 */
@DisplayName("WebAuth")
class WebAuthTest
{
    @Test
    @DisplayName("accepts its own token and nothing else")
    void acceptsOnlyItsOwnToken()
    {
        WebAuth auth = new WebAuth();

        assertTrue(auth.matches(auth.getToken()));
        assertFalse(auth.matches("wrong"));
        assertFalse(auth.matches(auth.getToken() + "x"));
        assertFalse(auth.matches(auth.getToken().substring(1)));
    }

    @Test
    @DisplayName("rejects absent input rather than treating it as a match")
    void rejectsAbsentInput()
    {
        WebAuth auth = new WebAuth();

        assertFalse(auth.matches(null));
        assertFalse(auth.matches(""));
    }

    @Test
    @DisplayName("issues a different token every time")
    void tokensAreUnique()
    {
        // Reusing a token across restarts would turn a convenience into a long-lived
        // credential sitting on disk beside the bot token.
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++)
        {
            seen.add(new WebAuth().getToken());
        }
        assertEquals(200, seen.size());
    }

    @Test
    @DisplayName("the token is long enough that guessing is not a strategy")
    void tokenIsLongEnough()
    {
        // 32 random bytes as base64url: 43 characters, around 192 bits.
        String token = new WebAuth().getToken();
        assertNotNull(token);
        assertTrue(token.length() >= 40, "token was only " + token.length() + " characters");
    }

    @Test
    @DisplayName("a token differing only in its last character is rejected")
    void nearMissRejected()
    {
        // The case a naive comparison leaks first. String equality returns as soon as two
        // characters differ, so a matching prefix takes measurably longer than a mismatched
        // one — enough, over many requests, to recover a token one character at a time.
        WebAuth auth = new WebAuth();
        String token = auth.getToken();
        char last = token.charAt(token.length() - 1);
        String nearMiss = token.substring(0, token.length() - 1) + (last == 'a' ? 'b' : 'a');

        assertFalse(auth.matches(nearMiss));
    }
}
