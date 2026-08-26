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
package com.jagrosh.jmusicbot.spotify;

import java.util.List;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

/**
 * The result of resolving a Spotify album, playlist, or artist link: a list of YouTube tracks
 * matched by searching {@code "artist - title"} for each Spotify entry.
 *
 * <p>Carries the numbers a caller needs to tell the user the truth about what happened: how many
 * Spotify tracks were actually considered (after any paging cap), how many of those had no
 * YouTube match, and whether the source list itself was longer than what got checked at all.
 * {@link #getTracks()} only ever contains tracks that were found — this class is what lets a
 * caller report the gap between "found" and "asked for" instead of hiding it.
 *
 * @author adan (xx445469)
 */
public class SpotifyAudioPlaylist extends BasicAudioPlaylist
{
    private final SpotifyReference.EntityType entityType;
    private final String spotifyName;
    private final int tracksConsidered;
    private final int totalAvailable;
    private final boolean capped;
    private final int capLimit;

    public SpotifyAudioPlaylist(SpotifyReference.EntityType entityType, String spotifyName,
                                 List<AudioTrack> resolvedTracks, int tracksConsidered,
                                 int totalAvailable, boolean capped, int capLimit)
    {
        super(spotifyName, resolvedTracks, null, false);
        this.entityType = entityType;
        this.spotifyName = spotifyName;
        this.tracksConsidered = tracksConsidered;
        this.totalAvailable = totalAvailable;
        this.capped = capped;
        this.capLimit = capLimit;
    }

    /** Whether this came from an album, playlist, or artist link (never TRACK — see {@link SpotifyAudioSourceManager}). */
    public SpotifyReference.EntityType getEntityType()
    {
        return entityType;
    }

    /** The album/playlist/artist name, as Spotify has it. */
    public String getSpotifyName()
    {
        return spotifyName;
    }

    /** How many Spotify tracks were searched for on YouTube (after any paging cap). */
    public int getTracksConsidered()
    {
        return tracksConsidered;
    }

    /** How many of the considered tracks had no YouTube match, and so are absent from {@link #getTracks()}. */
    public int getFailedMatches()
    {
        return tracksConsidered - getTracks().size();
    }

    /** How many tracks Spotify reports this entity actually has. */
    public int getTotalAvailable()
    {
        return totalAvailable;
    }

    /** Whether {@link #getTotalAvailable()} exceeds {@link #getTracksConsidered()} — i.e. the paging cap was hit. */
    public boolean isCapped()
    {
        return capped;
    }

    /** The cap that was applied, or 0 if {@link #isCapped()} is false. */
    public int getCapLimit()
    {
        return capLimit;
    }
}
