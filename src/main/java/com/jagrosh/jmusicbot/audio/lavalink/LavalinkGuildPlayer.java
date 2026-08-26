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
package com.jagrosh.jmusicbot.audio.lavalink;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-guild playback state on the Lavalink side.
 *
 * <p>Intentionally small. This is <b>not</b> a stand-in for {@link com.jagrosh.jmusicbot.audio.AudioHandler}
 * and its {@link com.jagrosh.jmusicbot.queue.AbstractQueue} — repeat modes, playback history,
 * favorites and queue reordering are stage 2. What is here is exactly enough to satisfy stage 1:
 * one currently-playing track, a plain FIFO of what is next, pause/volume, and a skip-vote set
 * mirroring {@code AudioHandler.getVotes()} closely enough that {@code MusicService.skipWithVote}
 * can be ported without duplicating its logic.
 *
 * @author adan (xx445469)
 */
public final class LavalinkGuildPlayer
{
    private final long guildId;
    private final Deque<LavalinkTrack> queue = new ArrayDeque<>();
    private final Set<String> votes = new LinkedHashSet<>();

    private volatile LavalinkTrack current;
    private volatile boolean paused;
    private volatile int volume = 100;
    private volatile boolean connected;

    public LavalinkGuildPlayer(long guildId)
    {
        this.guildId = guildId;
    }

    public long getGuildId()
    {
        return guildId;
    }

    public synchronized LavalinkTrack getCurrent()
    {
        return current;
    }

    public synchronized void setCurrent(LavalinkTrack track)
    {
        this.current = track;
        this.votes.clear();
    }

    /** Adds a track to the end of the simple FIFO queue. Returns its 1-based queue position. */
    public synchronized int enqueue(LavalinkTrack track)
    {
        queue.addLast(track);
        return queue.size();
    }

    /** Pulls the next queued track, or {@code null} if the queue is empty. */
    public synchronized LavalinkTrack pollNext()
    {
        return queue.pollFirst();
    }

    public synchronized int queueSize()
    {
        return queue.size();
    }

    public synchronized void clearQueue()
    {
        queue.clear();
    }

    public boolean isPaused()
    {
        return paused;
    }

    public void setPaused(boolean paused)
    {
        this.paused = paused;
    }

    public int getVolume()
    {
        return volume;
    }

    public void setVolume(int volume)
    {
        this.volume = volume;
    }

    public boolean isConnected()
    {
        return connected;
    }

    public void setConnected(boolean connected)
    {
        this.connected = connected;
    }

    public boolean isPlaying()
    {
        return connected && current != null;
    }

    public Set<String> getVotes()
    {
        return votes;
    }
}
