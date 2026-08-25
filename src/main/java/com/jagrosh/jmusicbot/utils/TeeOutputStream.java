/*
 * Copyright 2026 Arif Banai (arif-banai)
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
package com.jagrosh.jmusicbot.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * An OutputStream that writes to both a primary stream (e.g., the real stdout/stderr)
 * and an in-memory buffer. Used to capture early startup logs before we know if
 * the GUI is enabled, so they can be replayed into the GUI console later.
 * 
 * <p>Thread-safety: writes are synchronized to prevent interleaving when multiple
 * threads write concurrently during startup.</p>
 *
 * @author Arif Banai (arif-banai)
 */
public class TeeOutputStream extends OutputStream {
    
    private final OutputStream primary;
    private final ByteArrayOutputStream buffer;
    
    /**
     * Creates a TeeOutputStream that writes to the given primary stream
     * and also captures output in an internal buffer.
     *
     * @param primary The primary output stream (e.g., original System.out)
     */
    public TeeOutputStream(OutputStream primary) {
        this.primary = primary;
        this.buffer = new ByteArrayOutputStream(4096);
    }
    
    @Override
    public synchronized void write(int b) throws IOException {
        primary.write(b);
        buffer.write(b);
    }
    
    @Override
    public synchronized void write(byte[] b) throws IOException {
        primary.write(b);
        buffer.write(b);
    }
    
    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        primary.write(b, off, len);
        buffer.write(b, off, len);
    }
    
    @Override
    public synchronized void flush() throws IOException {
        primary.flush();
        // ByteArrayOutputStream.flush() is a no-op, but call for consistency
        buffer.flush();
    }
    
    @Override
    public synchronized void close() throws IOException {
        // Don't close the primary stream (we don't own it)
        // ByteArrayOutputStream.close() is a no-op
        buffer.close();
    }
    
    /**
     * Returns the buffered content as a UTF-8 string.
     *
     * @return The buffered output as a string
     */
    public synchronized String getBufferedContent() {
        return buffer.toString(StandardCharsets.UTF_8);
    }
    
    /**
     * Clears the buffer. Call after replaying to free memory.
     */
    public synchronized void clearBuffer() {
        buffer.reset();
    }
}
