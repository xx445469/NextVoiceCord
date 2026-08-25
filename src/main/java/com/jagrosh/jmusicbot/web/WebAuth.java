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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The token that guards the web panel.
 *
 * <p>The panel can see every guild the bot is in and, with control enabled, can stop playback
 * in any of them. On a home connection the port may well be reachable from the internet, and
 * a bot that quietly exposed that would be doing something its operator did not ask for. So
 * there is no unauthenticated mode: a token is generated whether or not anyone wants one.
 *
 * <p>Generated fresh each start rather than persisted. A token that survives restarts is a
 * long-lived credential sitting in a file next to the bot token, and the panel is a
 * convenience — regenerating costs a glance at the console, while a leaked permanent token
 * costs considerably more.
 *
 * @author adan (xx445469)
 */
public final class WebAuth
{
    /** 32 bytes: long enough that guessing is not a strategy, short enough to retype. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] tokenDigest;
    private final String token;

    public WebAuth()
    {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        this.token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        this.tokenDigest = digest(this.token);
    }

    /** The token, for printing once at startup. */
    public String getToken()
    {
        return token;
    }

    /**
     * Whether {@code candidate} is the token.
     *
     * <p>Compares digests with a constant-time check rather than {@code equals}. String
     * comparison returns as soon as two characters differ, and that timing difference is
     * enough to recover a token one character at a time over enough requests. The digest step
     * also makes the comparison independent of length, which would otherwise leak on its own.
     */
    public boolean matches(String candidate)
    {
        if (candidate == null || candidate.isEmpty())
        {
            return false;
        }
        return MessageDigest.isEqual(tokenDigest, digest(candidate));
    }

    private static byte[] digest(String value)
    {
        try
        {
            return MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception ex)
        {
            // SHA-256 is required of every JVM; if it is genuinely missing, failing loudly
            // beats silently comparing something weaker.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
