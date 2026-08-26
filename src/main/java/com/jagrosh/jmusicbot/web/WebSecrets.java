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
package com.jagrosh.jmusicbot.web;

import java.util.Set;

/**
 * Which config values may leave the process, and which may not.
 *
 * <p>The panel edits {@code config.txt}, which means it also has to show what is in it — and
 * what is in it includes the bot's Discord token. A token read out of a config editor is a
 * token someone else can run the bot with, so these never appear in a response at all: not
 * masked client-side, not truncated, not sent.
 *
 * <p>A masked placeholder is sent in their place, and a write carrying that placeholder back is
 * treated as "unchanged" rather than as a literal new value. Without that rule, opening the
 * editor and pressing save would overwrite the real token with a row of asterisks.
 *
 * <p>An allow-list would be safer than a deny-list, but it would also silently hide any option
 * added later, which is its own failure. The list is small and the properties it keys on —
 * token, password, secret — are the ones that recur.
 *
 * @author adan (xx445469)
 */
public final class WebSecrets
{
    /** Sent instead of the value. Recognised on the way back in and ignored. */
    public static final String MASK = "••••••••";

    private static final Set<String> SECRET_KEYS = Set.of(
            "discord.token",
            "playback.youtube.poToken",
            "playback.youtube.visitorData",
            "proxy.password",
            "proxy.username",
            // clientSecret is also caught by the "secret" name match below; clientId is not
            // (nothing in its name looks like a credential), so it needs to be listed
            // explicitly. Both authenticate the bot to Spotify's catalog API.
            "spotify.clientId",
            "spotify.clientSecret");

    private WebSecrets() { }

    /**
     * Whether this config key holds a credential.
     *
     * <p>Matched by name as well as by the explicit list, so an option added later that follows
     * the same naming ends up protected without anyone remembering to add it here.
     */
    public static boolean isSecret(String key)
    {
        if (SECRET_KEYS.contains(key))
        {
            return true;
        }
        String leaf = key.substring(key.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
        return leaf.contains("token")
                || leaf.contains("password")
                || leaf.contains("secret")
                || leaf.contains("apikey");
    }

    /** The value as the panel should see it. */
    public static String forDisplay(String key, String value)
    {
        if (!isSecret(key))
        {
            return value;
        }
        // Distinguishes "set but hidden" from "not set". Whether a token exists is not itself
        // a secret, and someone configuring the bot needs to know which one they are looking at.
        return value == null || value.isBlank() ? "" : MASK;
    }

    /** Whether an incoming value is the mask coming back unchanged, and so must be ignored. */
    public static boolean isUnchangedMask(String value)
    {
        return MASK.equals(value);
    }
}
