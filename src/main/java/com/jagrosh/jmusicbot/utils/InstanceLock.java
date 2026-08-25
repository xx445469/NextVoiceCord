/*
 * Copyright 2025 John Grosh <john.a.grosh@gmail.com>.
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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Utility class to prevent multiple instances of the bot from running
 * in the same directory. Uses FileChannel.tryLock() which is automatically
 * released by the OS when the JVM exits (even on crash).
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class InstanceLock {
    private static final String LOCK_FILE = ".jmusicbot.lock";
    private static FileChannel channel;
    private static FileLock lock;

    /**
     * Attempts to acquire an exclusive lock to prevent duplicate instances.
     * The lock is held for the lifetime of the JVM and automatically released
     * when the process exits.
     *
     * @return true if the lock was acquired (no other instance running),
     *         false if another instance already holds the lock
     */
    public static boolean tryAcquire() {
        try {
            Path lockPath = OtherUtil.getPath(LOCK_FILE);
            channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            lock = channel.tryLock();
            return lock != null;
        } catch (IOException | OverlappingFileLockException e) {
            return false;
        }
    }
    
    /**
     * Releases the instance lock and closes the file channel.
     * Safe to call even if lock was never acquired or already released.
     */
    public static void release() {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException ignored) {
        } finally {
            lock = null;
        }
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ignored) {
        } finally {
            channel = null;
        }
    }
}
