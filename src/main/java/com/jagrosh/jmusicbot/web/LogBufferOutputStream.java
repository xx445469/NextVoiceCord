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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Splits whatever bytes pass through into lines and hands each complete line to
 * {@link LogBuffer}, while forwarding every byte to a downstream stream unchanged.
 *
 * <p>A tap, not a replacement. Whatever this is wrapping — the real stdout/stderr, or the
 * GUI's console stream — is expected to look exactly as it would without this class in the
 * way; on any doubt the byte goes through untouched and only the buffering side is
 * best-effort. This mirrors {@code TextAreaOutputStream}'s approach to the same problem for
 * the GUI console, just aimed at a ring buffer instead of a text component.
 *
 * <p>Public rather than package-private because {@code JMusicBot} is the one place that knows
 * every point where System.out/err change hands (early buffering, GUI redirection, the
 * {@code --nogui} fallback) and has to insert this at each of them.
 *
 * @author adan (xx445469)
 */
public final class LogBufferOutputStream extends OutputStream
{
    private final OutputStream downstream;

    // Bytes seen since the last newline. A single write() call is not guaranteed to align
    // with a line boundary — a logger can write a partial line and finish it on the next
    // call — so this carries the fragment across calls.
    private final StringBuilder pending = new StringBuilder();

    public LogBufferOutputStream(OutputStream downstream)
    {
        this.downstream = downstream;
    }

    @Override
    public synchronized void write(int b) throws IOException
    {
        downstream.write(b);
        buffer(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException
    {
        downstream.write(b, off, len);
        buffer(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException
    {
        write(b, 0, b.length);
    }

    @Override
    public void flush() throws IOException
    {
        downstream.flush();
    }

    @Override
    public void close() throws IOException
    {
        // Deliberately does not close downstream: this stream never owns System.out/err or
        // the GUI's console stream, so closing it here would take those down for every other
        // caller for the sake of one tap giving up its bookkeeping.
    }

    private void buffer(byte[] b, int off, int len)
    {
        pending.append(new String(b, off, len, StandardCharsets.UTF_8));

        int newline;
        while ((newline = pending.indexOf("\n")) >= 0)
        {
            LogBuffer.getInstance().append(pending.substring(0, newline));
            pending.delete(0, newline + 1);
        }
    }
}
