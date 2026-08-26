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
package com.jagrosh.jmusicbot.unit.spotify;

import com.jagrosh.jmusicbot.spotify.SpotifyAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SpotifyAudioSourceManager")
class SpotifyAudioSourceManagerTest
{
    private static SpotifyAudioSourceManager unconfigured() throws Exception
    {
        Constructor<SpotifyAudioSourceManager> ctor =
                SpotifyAudioSourceManager.class.getDeclaredConstructor(com.jagrosh.jmusicbot.spotify.SpotifyWebApiClient.class);
        ctor.setAccessible(true);
        return ctor.newInstance((Object) null);
    }

    @Test
    @DisplayName("reports its source name as \"spotify\"")
    void reportsSourceName() throws Exception
    {
        assertEquals("spotify", unconfigured().getSourceName());
    }

    @Test
    @DisplayName("ignores identifiers that are not Spotify links, leaving them for the next source manager")
    void ignoresNonSpotifyIdentifiers() throws Exception
    {
        SpotifyAudioSourceManager manager = unconfigured();
        DefaultAudioPlayerManager apm = new DefaultAudioPlayerManager();

        AudioItem result = manager.loadItem(apm, new AudioReference("https://www.youtube.com/watch?v=dQw4w9WgXcQ", null));
        assertNull(result);
    }

    @Test
    @DisplayName("throws a friendly error for a Spotify link when credentials are not configured")
    void throwsWhenNotConfigured() throws Exception
    {
        SpotifyAudioSourceManager manager = unconfigured();
        DefaultAudioPlayerManager apm = new DefaultAudioPlayerManager();

        FriendlyException ex = assertThrows(FriendlyException.class, () ->
                manager.loadItem(apm, new AudioReference("spotify:track:4cOdK2wGLETKBW3PvgPWqT", null)));
        assertTrue(ex.getMessage().toLowerCase().contains("not configured"),
                "Expected a clear 'not configured' message, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("never claims to be able to encode/decode its own tracks")
    void neverOwnsTracks() throws Exception
    {
        SpotifyAudioSourceManager manager = unconfigured();
        assertEquals(false, manager.isTrackEncodable(null));
    }
}
