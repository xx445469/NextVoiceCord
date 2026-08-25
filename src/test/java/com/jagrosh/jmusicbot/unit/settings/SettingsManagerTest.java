/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.unit.settings;

import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RepeatMode and QueueType enum parsing, specifically testing
 * graceful handling of invalid enum values via the valueOfOrDefault methods.
 */
public class SettingsManagerTest
{
    // RepeatMode tests

    @Test
    void testValidRepeatModeValues()
    {
        assertEquals(RepeatMode.OFF, RepeatMode.valueOfOrDefault("OFF", RepeatMode.OFF));
        assertEquals(RepeatMode.ALL, RepeatMode.valueOfOrDefault("ALL", RepeatMode.OFF));
        assertEquals(RepeatMode.SINGLE, RepeatMode.valueOfOrDefault("SINGLE", RepeatMode.OFF));
    }

    @Test
    void testInvalidRepeatModeReturnsDefault()
    {
        // Invalid values should return the default
        assertEquals(RepeatMode.OFF, RepeatMode.valueOfOrDefault("INVALID_MODE", RepeatMode.OFF));
        assertEquals(RepeatMode.ALL, RepeatMode.valueOfOrDefault("GARBAGE", RepeatMode.ALL));
        assertEquals(RepeatMode.SINGLE, RepeatMode.valueOfOrDefault("CORRUPTED", RepeatMode.SINGLE));
    }

    @Test
    void testNullRepeatModeReturnsDefault()
    {
        assertEquals(RepeatMode.OFF, RepeatMode.valueOfOrDefault(null, RepeatMode.OFF));
        assertEquals(RepeatMode.ALL, RepeatMode.valueOfOrDefault(null, RepeatMode.ALL));
    }

    @Test
    void testEmptyRepeatModeReturnsDefault()
    {
        assertEquals(RepeatMode.OFF, RepeatMode.valueOfOrDefault("", RepeatMode.OFF));
        assertEquals(RepeatMode.ALL, RepeatMode.valueOfOrDefault("", RepeatMode.ALL));
    }

    @Test
    void testRepeatModeCaseSensitive()
    {
        // Enum values are case-sensitive - lowercase should be treated as invalid
        assertEquals(RepeatMode.OFF, RepeatMode.valueOfOrDefault("all", RepeatMode.OFF));
        assertEquals(RepeatMode.OFF, RepeatMode.valueOfOrDefault("All", RepeatMode.OFF));
        assertEquals(RepeatMode.OFF, RepeatMode.valueOfOrDefault("single", RepeatMode.OFF));
    }

    @Test
    void testRepeatModeWhitespaceReturnsDefault()
    {
        assertEquals(RepeatMode.OFF, RepeatMode.valueOfOrDefault(" ", RepeatMode.OFF));
        assertEquals(RepeatMode.OFF, RepeatMode.valueOfOrDefault("  ALL  ", RepeatMode.OFF));
    }

    @Test
    void testRepeatModeDoesNotThrow()
    {
        assertDoesNotThrow(() -> RepeatMode.valueOfOrDefault("TOTALLY_INVALID", RepeatMode.OFF));
        assertDoesNotThrow(() -> RepeatMode.valueOfOrDefault(null, RepeatMode.OFF));
        assertDoesNotThrow(() -> RepeatMode.valueOfOrDefault("", RepeatMode.OFF));
    }

    // QueueType tests

    @Test
    void testValidQueueTypeValues()
    {
        assertEquals(QueueType.LINEAR, QueueType.valueOfOrDefault("LINEAR", QueueType.FAIR));
        assertEquals(QueueType.FAIR, QueueType.valueOfOrDefault("FAIR", QueueType.LINEAR));
    }

    @Test
    void testInvalidQueueTypeReturnsDefault()
    {
        // Invalid values should return the default
        assertEquals(QueueType.FAIR, QueueType.valueOfOrDefault("INVALID_QUEUE", QueueType.FAIR));
        assertEquals(QueueType.LINEAR, QueueType.valueOfOrDefault("NONSENSE", QueueType.LINEAR));
    }

    @Test
    void testNullQueueTypeReturnsDefault()
    {
        assertEquals(QueueType.FAIR, QueueType.valueOfOrDefault(null, QueueType.FAIR));
        assertEquals(QueueType.LINEAR, QueueType.valueOfOrDefault(null, QueueType.LINEAR));
    }

    @Test
    void testEmptyQueueTypeReturnsDefault()
    {
        assertEquals(QueueType.FAIR, QueueType.valueOfOrDefault("", QueueType.FAIR));
        assertEquals(QueueType.LINEAR, QueueType.valueOfOrDefault("", QueueType.LINEAR));
    }

    @Test
    void testQueueTypeCaseSensitive()
    {
        // Enum values are case-sensitive - lowercase should be treated as invalid
        assertEquals(QueueType.FAIR, QueueType.valueOfOrDefault("linear", QueueType.FAIR));
        assertEquals(QueueType.FAIR, QueueType.valueOfOrDefault("Linear", QueueType.FAIR));
    }

    @Test
    void testQueueTypeWhitespaceReturnsDefault()
    {
        assertEquals(QueueType.FAIR, QueueType.valueOfOrDefault("\t", QueueType.FAIR));
        assertEquals(QueueType.FAIR, QueueType.valueOfOrDefault("  LINEAR  ", QueueType.FAIR));
    }

    @Test
    void testQueueTypeDoesNotThrow()
    {
        assertDoesNotThrow(() -> QueueType.valueOfOrDefault("COMPLETELY_WRONG", QueueType.FAIR));
        assertDoesNotThrow(() -> QueueType.valueOfOrDefault(null, QueueType.FAIR));
        assertDoesNotThrow(() -> QueueType.valueOfOrDefault("", QueueType.FAIR));
    }
}
