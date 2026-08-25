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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.jagrosh.jmusicbot.gui.components.LogView;

/**
 * A headless copy of the console, kept for the web panel.
 *
 * <p>The desktop GUI has {@link LogView} to show recent output, but that is a Swing component
 * — it does not exist when the bot is run with {@code --nogui}, which is exactly when the web
 * panel is most likely to be someone's only window into what the bot is doing. This is that
 * component's non-visual equivalent: something a log line can be handed to regardless of
 * whether a GUI exists, and something an HTTP request can later read back from.
 *
 * <p>One instance for the whole process, reached through {@link #getInstance()}, because there
 * is exactly one console to mirror. Every log line the bot produces funnels through a single
 * pair of streams (stdout/stderr), so a singleton is not a compromise here — it is the shape
 * of the thing being modelled.
 *
 * <p>Lines are numbered with a sequence that only ever increases, so a browser tab polling the
 * panel every second or two can ask "everything after N" via {@link #since(long, int)} instead
 * of re-fetching and re-rendering the whole buffer on every poll.
 *
 * @author adan (xx445469)
 */
public final class LogBuffer
{
    /**
     * Maximum number of lines retained at once.
     *
     * <p>A bot can run for weeks; without a cap this is an unbounded list that grows for as
     * long as the process does, which is the same memory leak {@link LogView#trimTo(int)}
     * exists to avoid on the GUI side. 1000 lines is generous for "what just happened" — enough
     * to cover a startup sequence or a burst of errors — without pretending to be a durable log
     * store. Anyone who needs the full history has the log file on disk for that.
     */
    private static final int CAPACITY = 1000;

    private static final LogBuffer INSTANCE = new LogBuffer();

    /** The one buffer for the process. */
    public static LogBuffer getInstance()
    {
        return INSTANCE;
    }

    // A Deque rather than a fixed-size array: appends and removals from either end are O(1),
    // and the buffer is never indexed by position, only walked in order, so an array's random
    // access would buy nothing.
    private final Deque<Entry> lines = new ArrayDeque<>(CAPACITY);
    private final AtomicLong nextSequence = new AtomicLong(1);
    private long dropped = 0;

    private LogBuffer()
    {
    }

    /**
     * Records one log line. Thread-safe: log output arrives from every thread in the process
     * (Discord's gateway thread, command handlers, the audio pipeline), not just one.
     */
    public synchronized void append(String line)
    {
        if (line == null)
        {
            return;
        }

        // A caller may hand us a line with its terminator still attached (a raw stdout tap
        // has no reason to have stripped it). Storing it either way would be fine for
        // sequencing, but every rendered line gaining a trailing blank line is a real bug in
        // whatever displays the buffer, so it is normalised out once, here.
        String text = stripTrailingNewline(line);

        lines.addLast(new Entry(nextSequence.getAndIncrement(), text, LogView.levelOf(text)));

        if (lines.size() > CAPACITY)
        {
            lines.removeFirst();
            dropped++;
        }
    }

    /**
     * Lines with a sequence number greater than {@code sequence}, oldest first, capped at
     * {@code limit}. Passing 0 for {@code sequence} returns from the start of what is still
     * retained — which, once the buffer has wrapped, is not the start of the process's output.
     */
    public synchronized List<Entry> since(long sequence, int limit)
    {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : lines)
        {
            if (result.size() >= limit)
            {
                break;
            }
            if (entry.sequence() > sequence)
            {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * The sequence number of the most recent line, or 0 if nothing has been logged yet. A
     * poller passes this back as the {@code sequence} argument to its next call to
     * {@link #since(long, int)}.
     */
    public synchronized long highestSequence()
    {
        return lines.isEmpty() ? 0 : lines.getLast().sequence();
    }

    /**
     * How many lines have fallen off the front of the buffer since the process started. Lets
     * the panel say "N earlier lines discarded" instead of a buffer that quietly starts
     * partway through the bot's history with nothing to say so.
     */
    public synchronized long droppedCount()
    {
        return dropped;
    }

    private static String stripTrailingNewline(String line)
    {
        int end = line.length();
        if (end > 0 && line.charAt(end - 1) == '\n')
        {
            end--;
        }
        if (end > 0 && line.charAt(end - 1) == '\r')
        {
            end--;
        }
        return end == line.length() ? line : line.substring(0, end);
    }

    /** One retained line: where it sits in the sequence, its text, and its level. */
    public record Entry(long sequence, String text, LogView.Level level)
    {
    }
}
