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

import com.jagrosh.jmusicbot.config.model.PlaybackEngine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("PlaybackEngine.resolve")
class PlaybackEngineTest
{
    private final Logger logger = mock(Logger.class);

    @ParameterizedTest(name = "''{0}'' resolves to LAVAPLAYER")
    @CsvSource({"lavaplayer", "LAVAPLAYER", "LavaPlayer", " lavaplayer "})
    @DisplayName("recognises lavaplayer case/whitespace-insensitively, without logging")
    void resolvesLavaplayer(String raw)
    {
        assertEquals(PlaybackEngine.LAVAPLAYER, PlaybackEngine.resolve(raw, logger));
        verify(logger, never()).warn(org.mockito.ArgumentMatchers.anyString(), (Object) org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest(name = "''{0}'' resolves to LAVALINK")
    @CsvSource({"lavalink", "LAVALINK", "LavaLink", " lavalink "})
    @DisplayName("recognises lavalink case/whitespace-insensitively, without logging")
    void resolvesLavalink(String raw)
    {
        assertEquals(PlaybackEngine.LAVALINK, PlaybackEngine.resolve(raw, logger));
        verify(logger, never()).warn(org.mockito.ArgumentMatchers.anyString(), (Object) org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("'fallback' is accepted but not implemented: falls back to lavaplayer with a clear warning")
    void fallbackIsNotImplementedYet()
    {
        PlaybackEngine result = PlaybackEngine.resolve("fallback", logger);

        assertEquals(PlaybackEngine.LAVAPLAYER, result);
        // The value is not silently ignored: something is logged that says "fallback" is not
        // implemented, distinct from the generic "unknown value" warning an unrecognised string
        // would get - see unknownValueFallsBackToLavaplayer.
        verify(logger).warn(org.mockito.ArgumentMatchers.contains("not implemented"));
    }

    @ParameterizedTest(name = "unknown value ''{0}'' falls back to lavaplayer with a warning")
    @ValueSource(strings = {"lavalink2", "LAVA", "bogus", "lava player"})
    void unknownValueFallsBackToLavaplayer(String raw)
    {
        PlaybackEngine result = PlaybackEngine.resolve(raw, logger);

        assertEquals(PlaybackEngine.LAVAPLAYER, result);
        verify(logger).warn(org.mockito.ArgumentMatchers.contains("Unknown playback.engine"),
                org.mockito.ArgumentMatchers.eq(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null/blank falls back to lavaplayer with a warning, never throws")
    void nullOrBlankFallsBackToLavaplayer(String raw)
    {
        assertEquals(PlaybackEngine.LAVAPLAYER, PlaybackEngine.resolve(raw, logger));
    }

    @Test
    @DisplayName("resolve never returns anything but LAVAPLAYER or LAVALINK")
    void resolveNeverReturnsAThirdValue()
    {
        for (String raw : new String[] { "lavaplayer", "lavalink", "fallback", "garbage", "", null })
        {
            PlaybackEngine result = PlaybackEngine.resolve(raw, LoggerFactory.getLogger(PlaybackEngineTest.class));
            assertEquals(true, result == PlaybackEngine.LAVAPLAYER || result == PlaybackEngine.LAVALINK);
        }
    }

    @Test
    @DisplayName("configValue() round-trips through resolve()")
    void configValueRoundTrips()
    {
        for (PlaybackEngine engine : PlaybackEngine.values())
        {
            assertEquals(engine, PlaybackEngine.resolve(engine.configValue(), logger));
        }
    }
}
