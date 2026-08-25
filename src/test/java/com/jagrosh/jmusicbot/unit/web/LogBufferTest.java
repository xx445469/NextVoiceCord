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

import java.util.List;

import com.jagrosh.jmusicbot.gui.components.LogView;
import com.jagrosh.jmusicbot.web.LogBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The headless console the web panel reads from.
 *
 * <p>{@link LogBuffer} is a process-wide singleton (there is one console, and only one), so
 * these tests never assume it starts empty — another test class, or even a preceding test in
 * this one, may have already appended to it in the same JVM. Every assertion is made relative
 * to a baseline taken at the start of the test instead.
 */
@DisplayName("LogBuffer")
class LogBufferTest
{
    private final LogBuffer buffer = LogBuffer.getInstance();

    @Test
    @DisplayName("getInstance always returns the same buffer")
    void isASingleton()
    {
        assertTrue(LogBuffer.getInstance() == buffer);
    }

    @Test
    @DisplayName("sequence numbers strictly increase with each append")
    void sequenceNumbersIncrease()
    {
        long first = appendAndSequence("one");
        long second = appendAndSequence("two");
        long third = appendAndSequence("three");

        assertTrue(second > first);
        assertTrue(third > second);
    }

    @Test
    @DisplayName("since() returns only lines appended after the given sequence")
    void sinceReturnsOnlyNewerLines()
    {
        long before = buffer.highestSequence();
        buffer.append("alpha");
        long afterAlpha = buffer.highestSequence();
        buffer.append("beta");
        buffer.append("gamma");

        List<LogBuffer.Entry> newerThanBefore = buffer.since(before, 100);
        assertEquals(3, newerThanBefore.size());
        assertEquals("alpha", newerThanBefore.get(0).text());
        assertEquals("beta", newerThanBefore.get(1).text());
        assertEquals("gamma", newerThanBefore.get(2).text());

        List<LogBuffer.Entry> newerThanAlpha = buffer.since(afterAlpha, 100);
        assertEquals(2, newerThanAlpha.size());
        assertEquals("beta", newerThanAlpha.get(0).text());
        assertEquals("gamma", newerThanAlpha.get(1).text());

        // Asking for what is already known returns nothing, not the same lines again.
        assertEquals(0, buffer.since(buffer.highestSequence(), 100).size());
    }

    @Test
    @DisplayName("since() honors the limit even when more new lines exist")
    void sinceHonorsLimit()
    {
        long before = buffer.highestSequence();
        for (int i = 0; i < 10; i++)
        {
            buffer.append("line " + i);
        }

        List<LogBuffer.Entry> capped = buffer.since(before, 3);
        assertEquals(3, capped.size());
        assertEquals("line 0", capped.get(0).text());
        assertEquals("line 2", capped.get(2).text());
    }

    @Test
    @DisplayName("the buffer is bounded regardless of how much is appended")
    void capBoundsMemory()
    {
        // Comfortably more than any reasonable cap, so this proves boundedness rather than
        // just happening to land under whatever the current cap is.
        for (int i = 0; i < 5000; i++)
        {
            buffer.append("flood " + i);
        }

        List<LogBuffer.Entry> everything = buffer.since(0, Integer.MAX_VALUE);
        assertTrue(everything.size() <= 1000,
                "buffer should be capped, but held " + everything.size() + " lines");
    }

    @Test
    @DisplayName("dropped-line count tracks exactly how many lines fell out of a full buffer")
    void droppedCountIsExact()
    {
        // Fill comfortably past the cap first, so the buffer is in a known "full" state
        // before the part of the test that is actually measured begins.
        for (int i = 0; i < 1200; i++)
        {
            buffer.append("warm up " + i);
        }

        long droppedBefore = buffer.droppedCount();
        for (int i = 0; i < 50; i++)
        {
            buffer.append("measured " + i);
        }

        // The buffer was already full, so every one of these 50 appends evicts exactly one
        // older line.
        assertEquals(droppedBefore + 50, buffer.droppedCount());
    }

    @Test
    @DisplayName("level parsing agrees with the desktop console for tagged lines")
    void levelParsingMatchesTaggedLines()
    {
        long before = buffer.highestSequence();
        buffer.append("12:00:00 [ERROR] something broke");
        buffer.append("12:00:01 [WARN] something might break");
        buffer.append("12:00:02 [INFO] all fine");
        buffer.append("12:00:03 [DEBUG] gory detail");
        buffer.append("12:00:04 [TRACE] gorier detail");

        List<LogBuffer.Entry> entries = buffer.since(before, 100);
        assertEquals(LogView.Level.ERROR, entries.get(0).level());
        assertEquals(LogView.Level.WARN, entries.get(1).level());
        assertEquals(LogView.Level.INFO, entries.get(2).level());
        assertEquals(LogView.Level.DEBUG, entries.get(3).level());
        assertEquals(LogView.Level.TRACE, entries.get(4).level());
    }

    @Test
    @DisplayName("an untagged stack-trace continuation line defaults to INFO")
    void untaggedLineDefaultsToInfo()
    {
        long before = buffer.highestSequence();
        buffer.append("12:00:00 [ERROR] Uncaught exception in command handler");
        // A stack trace frame: no level tag of its own, arrives as a follow-up line.
        buffer.append("\tat com.jagrosh.jmusicbot.commands.PlayCmd.execute(PlayCmd.java:42)");

        List<LogBuffer.Entry> entries = buffer.since(before, 100);
        assertEquals(LogView.Level.ERROR, entries.get(0).level());
        assertEquals(LogView.Level.INFO, entries.get(1).level());
    }

    @Test
    @DisplayName("entries record the exact text passed in, without a trailing newline")
    void entryTextIsExact()
    {
        long before = buffer.highestSequence();
        buffer.append("plain line");
        buffer.append("line with terminator\n");
        buffer.append("line with crlf terminator\r\n");

        List<LogBuffer.Entry> entries = buffer.since(before, 100);
        assertEquals("plain line", entries.get(0).text());
        assertEquals("line with terminator", entries.get(1).text());
        assertEquals("line with crlf terminator", entries.get(2).text());
    }

    private long appendAndSequence(String text)
    {
        long before = buffer.highestSequence();
        buffer.append(text);
        long after = buffer.highestSequence();
        assertTrue(after > before);
        return after;
    }
}
