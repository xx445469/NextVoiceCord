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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.jagrosh.jmusicbot.BotConfig;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import dev.lavalink.youtube.YoutubeAudioSourceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claims Spotify track/album/playlist/artist links and turns them into playable audio.
 *
 * <p><b>What this is not:</b> Spotify's audio is DRM-protected and there is no public streaming
 * API for it, so nothing here ever touches it. What actually happens is: the Spotify Web API
 * supplies a track list (title + artist), and each entry is matched with a YouTube search using
 * the same, already-registered YouTube source manager that a normal {@code play} search uses.
 * Every track this manager hands back is a YouTube upload, not a Spotify stream — it is even
 * built and owned by the YouTube source manager, not this one.
 *
 * <p>Follows the same shape as {@link com.jagrosh.jmusicbot.audio.TransformativeAudioSourceManager}:
 * claim a URL by regex, resolve it to something the YouTube source manager can load, and hand
 * that off. The difference is that a Spotify link can expand to many tracks, and each of those
 * is a full Spotify Web API round trip's worth of metadata plus one YouTube search — so unlike
 * Transformative, this manager reuses the bot's already-authenticated YouTube source manager
 * instead of constructing its own, and resolves multi-track links sequentially rather than in
 * parallel (see {@link #resolveMultiple}).
 *
 * @author adan (xx445469)
 */
public class SpotifyAudioSourceManager implements AudioSourceManager
{
    private static final Logger LOG = LoggerFactory.getLogger(SpotifyAudioSourceManager.class);

    private final SpotifyWebApiClient webApiClient;

    public SpotifyAudioSourceManager(BotConfig config)
    {
        this(config.hasSpotifyCredentials()
                ? new SpotifyWebApiClient(new SpotifyTokenCache(config.getSpotifyClientId(), config.getSpotifyClientSecret()))
                : null);
    }

    /** Visible for tests: lets a test supply a client backed by a mock server, or none at all. */
    SpotifyAudioSourceManager(SpotifyWebApiClient webApiClient)
    {
        this.webApiClient = webApiClient;
    }

    @Override
    public String getSourceName()
    {
        return "spotify";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager playerManager, AudioReference reference)
    {
        Optional<SpotifyReference> parsed = SpotifyUrlParser.parse(reference.identifier);
        if (parsed.isEmpty())
        {
            // Not a Spotify link: let the next registered source manager have it.
            return null;
        }

        if (webApiClient == null)
        {
            throw new FriendlyException(
                    "Spotify support is not configured. Set spotify.clientId and spotify.clientSecret "
                            + "to enable it.",
                    Severity.COMMON, null);
        }

        YoutubeAudioSourceManager youtube = findYoutubeManager(playerManager);
        if (youtube == null)
        {
            throw new FriendlyException(
                    "Spotify links are matched through YouTube, but the youtube audio source is disabled.",
                    Severity.COMMON, null);
        }

        SpotifyReference ref = parsed.get();
        try
        {
            SpotifyResolution resolution = webApiClient.resolve(ref);
            if (resolution.searchQueries().isEmpty())
            {
                throw new FriendlyException(
                        "No matchable tracks were found for this Spotify " + entityLabel(ref.type()) + ".",
                        Severity.COMMON, null);
            }

            if (ref.type() == SpotifyReference.EntityType.TRACK)
            {
                // A single track behaves exactly like a normal YouTube search: same result
                // shape (a search-result playlist), same downstream handling by callers.
                return youtube.loadItem(playerManager, new AudioReference(
                        YoutubeAudioSourceManager.SEARCH_PREFIX + resolution.searchQueries().get(0), null));
            }

            return resolveMultiple(playerManager, youtube, ref, resolution);
        }
        catch (IOException ex)
        {
            throw new FriendlyException("Spotify lookup failed: " + ex.getMessage(), Severity.SUSPICIOUS, ex);
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new FriendlyException("Spotify lookup was interrupted.", Severity.SUSPICIOUS, ex);
        }
    }

    /**
     * Resolves an album/playlist/artist to a list of YouTube tracks.
     *
     * <p>Searches run one at a time, not in parallel. This project has already been rate-limited
     * by YouTube once; firing a burst of a hundred simultaneous searches to resolve one playlist
     * is exactly the kind of thing that causes it again.
     */
    private AudioItem resolveMultiple(AudioPlayerManager playerManager, YoutubeAudioSourceManager youtube,
                                       SpotifyReference ref, SpotifyResolution resolution)
    {
        List<AudioTrack> resolved = new ArrayList<>();
        for (String query : resolution.searchQueries())
        {
            AudioTrack track = firstTrackOf(youtube.loadItem(playerManager,
                    new AudioReference(YoutubeAudioSourceManager.SEARCH_PREFIX + query, null)));
            if (track != null)
            {
                resolved.add(track);
            }
            else
            {
                LOG.debug("No YouTube match for Spotify track \"{}\"", query);
            }
        }

        if (resolved.isEmpty())
        {
            throw new FriendlyException(
                    "None of the tracks in this Spotify " + entityLabel(ref.type()) + " could be matched on YouTube.",
                    Severity.COMMON, null);
        }

        return new SpotifyAudioPlaylist(ref.type(), resolution.entityName(), resolved,
                resolution.searchQueries().size(), resolution.totalAvailable(),
                resolution.capped(), resolution.capLimit());
    }

    /**
     * Finds the already-registered YouTube source manager by source name, rather than by class.
     *
     * <p>{@link com.jagrosh.jmusicbot.audio.TransformativeAudioSourceManager} also extends
     * {@code YoutubeAudioSourceManager} and may be registered under a config-defined name; an
     * {@code instanceof} or {@code apm.source(YoutubeAudioSourceManager.class)} lookup could
     * match one of those instead of the real thing. Matching on {@code getSourceName()} is what
     * the config actually promises: the manager registered as {@code "youtube"}.
     */
    private static YoutubeAudioSourceManager findYoutubeManager(AudioPlayerManager playerManager)
    {
        for (AudioSourceManager manager : playerManager.getSourceManagers())
        {
            if (manager instanceof YoutubeAudioSourceManager yt && "youtube".equals(manager.getSourceName()))
            {
                return yt;
            }
        }
        return null;
    }

    private static AudioTrack firstTrackOf(AudioItem item)
    {
        if (item instanceof AudioTrack track)
        {
            return track;
        }
        if (item instanceof AudioPlaylist playlist && !playlist.getTracks().isEmpty())
        {
            return playlist.getTracks().get(0);
        }
        return null;
    }

    private static String entityLabel(SpotifyReference.EntityType type)
    {
        return switch (type)
        {
            case TRACK -> "track";
            case ALBUM -> "album";
            case PLAYLIST -> "playlist";
            case ARTIST -> "artist";
        };
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track)
    {
        // Every track this manager hands out is actually built and owned by the YouTube source
        // manager, which is what lavaplayer calls on to encode/decode it. This manager never
        // holds a track of its own.
        return false;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output)
    {
        throw new UnsupportedOperationException("Spotify tracks are encoded by the YouTube source manager");
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input)
    {
        throw new UnsupportedOperationException("Spotify tracks are decoded by the YouTube source manager");
    }

    @Override
    public void shutdown()
    {
        // No owned resources to release: the token cache holds a plain HttpClient, and the
        // shared YouTube source manager is shut down by its own registration.
    }
}
