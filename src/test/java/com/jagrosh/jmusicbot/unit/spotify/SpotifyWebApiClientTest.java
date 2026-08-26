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

import com.jagrosh.jmusicbot.spotify.SpotifyReference;
import com.jagrosh.jmusicbot.spotify.SpotifyResolution;
import com.jagrosh.jmusicbot.spotify.SpotifyTokenCache;
import com.jagrosh.jmusicbot.spotify.SpotifyWebApiClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises track-list resolution against a mock HTTP server — never the real Spotify API —
 * with particular attention to the playlist/album paging cap: the Web API pages at 100 (playlist)
 * and 50 (album) items, and this client is only supposed to read one page, reporting honestly
 * when that means it stopped short of the entity's real size.
 */
@DisplayName("SpotifyWebApiClient")
class SpotifyWebApiClientTest
{
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException
    {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException
    {
        server.shutdown();
    }

    private void enqueueOk(String body)
    {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body));
    }

    private void enqueueToken()
    {
        enqueueOk("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
    }

    @SuppressWarnings("unchecked")
    private SpotifyWebApiClient newClient() throws Exception
    {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        Constructor<SpotifyTokenCache> tokenCtor = SpotifyTokenCache.class.getDeclaredConstructor(
                String.class, String.class, String.class, HttpClient.class, Clock.class);
        tokenCtor.setAccessible(true);
        SpotifyTokenCache tokenCache = tokenCtor.newInstance(
                "id", "secret", server.url("/token").toString(), http, Clock.systemUTC());

        Constructor<SpotifyWebApiClient> clientCtor = SpotifyWebApiClient.class.getDeclaredConstructor(
                SpotifyTokenCache.class, HttpClient.class, String.class);
        clientCtor.setAccessible(true);
        return clientCtor.newInstance(tokenCache, http, server.url("/v1").toString().replaceAll("/$", ""));
    }

    @Test
    @DisplayName("an artist link falls back to search when Spotify refuses top-tracks")
    void artistFallsBackToSearchOnForbidden() throws Exception
    {
        // Measured against a real account: /artists/{id} and /search both return 200 while
        // /artists/{id}/top-tracks returns 403, because Spotify closed that endpoint to
        // applications registered after November 2024. Refusing the whole link would mean
        // artist links never work for anyone who registered recently.
        enqueueToken();
        enqueueOk("{\"name\":\"OMFG\"}");
        server.enqueue(new MockResponse().setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"status\":403,\"message\":\"Forbidden\"}}"));
        enqueueOk("{\"tracks\":{\"items\":[" + trackJson("Hello", "OMFG") + "," + trackJson("Ice", "OMFG") + "]}}");

        SpotifyWebApiClient client = newClient();
        var result = client.resolve(new SpotifyReference(SpotifyReference.EntityType.ARTIST, "abc"));

        assertEquals("OMFG", result.entityName());
        assertEquals(2, result.searchQueries().size(), "the search results should stand in for top-tracks");
        assertTrue(result.searchQueries().get(0).contains("OMFG"));
    }

    @Test
    @DisplayName("a non-403 failure on top-tracks is not swallowed by the fallback")
    void artistDoesNotFallBackOnOtherErrors() throws Exception
    {
        // The fallback exists for one specific, understood refusal. A 500 means something else
        // is wrong, and quietly returning search results would hide it.
        enqueueToken();
        enqueueOk("{\"name\":\"OMFG\"}");
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));

        SpotifyWebApiClient client = newClient();
        assertThrows(IOException.class,
                () -> client.resolve(new SpotifyReference(SpotifyReference.EntityType.ARTIST, "abc")));
    }

    private static String trackJson(String name, String artist)
    {
        return "{\"name\":\"" + name + "\",\"artists\":[{\"name\":\"" + artist + "\"}]}";
    }

    private static String playlistItemJson(String name, String artist)
    {
        return "{\"track\":" + trackJson(name, artist) + "}";
    }

    // ---- Track -------------------------------------------------------------------------------

    @Test
    @DisplayName("resolves a single track to one search query")
    void resolvesTrack() throws Exception
    {
        enqueueToken();
        enqueueOk(trackJson("Never Gonna Give You Up", "Rick Astley"));

        SpotifyWebApiClient client = newClient();
        SpotifyResolution resolution = client.resolve(new SpotifyReference(SpotifyReference.EntityType.TRACK, "abc"));

        assertEquals(List.of("Rick Astley - Never Gonna Give You Up"), resolution.searchQueries());
        assertEquals(1, resolution.totalAvailable());
        assertFalse(resolution.capped());
    }

    // ---- Album paging cap ----------------------------------------------------------------------

    @Test
    @DisplayName("caps an oversized album at MAX_ALBUM_TRACKS and reports the true total")
    void capsOversizedAlbum() throws Exception
    {
        enqueueToken();
        enqueueOk("{\"name\":\"Big Album\"}");

        int total = SpotifyWebApiClient.MAX_ALBUM_TRACKS + 37;
        String items = IntStream.range(0, SpotifyWebApiClient.MAX_ALBUM_TRACKS)
                .mapToObj(i -> trackJson("Track " + i, "Artist"))
                .collect(Collectors.joining(","));
        enqueueOk("{\"items\":[" + items + "],\"total\":" + total + "}");

        SpotifyWebApiClient client = newClient();
        SpotifyResolution resolution = client.resolve(new SpotifyReference(SpotifyReference.EntityType.ALBUM, "abc"));

        assertEquals("Big Album", resolution.entityName());
        assertEquals(SpotifyWebApiClient.MAX_ALBUM_TRACKS, resolution.searchQueries().size());
        assertEquals(total, resolution.totalAvailable());
        assertTrue(resolution.capped());
        assertEquals(SpotifyWebApiClient.MAX_ALBUM_TRACKS, resolution.capLimit());
    }

    @Test
    @DisplayName("does not report a cap for an album that fits within one page")
    void doesNotCapSmallAlbum() throws Exception
    {
        enqueueToken();
        enqueueOk("{\"name\":\"Small Album\"}");
        enqueueOk("{\"items\":[" + trackJson("Only Track", "Artist") + "],\"total\":1}");

        SpotifyWebApiClient client = newClient();
        SpotifyResolution resolution = client.resolve(new SpotifyReference(SpotifyReference.EntityType.ALBUM, "abc"));

        assertEquals(1, resolution.searchQueries().size());
        assertFalse(resolution.capped());
        assertEquals(0, resolution.capLimit());
    }

    @Test
    @DisplayName("requests exactly one page of album tracks at the documented page size")
    void albumTracksRequestUsesPageSizeLimit() throws Exception
    {
        enqueueToken();
        enqueueOk("{\"name\":\"Album\"}");
        enqueueOk("{\"items\":[],\"total\":0}");

        SpotifyWebApiClient client = newClient();
        client.resolve(new SpotifyReference(SpotifyReference.EntityType.ALBUM, "abc"));

        server.takeRequest(1, TimeUnit.SECONDS); // token
        server.takeRequest(1, TimeUnit.SECONDS); // album metadata
        RecordedRequest tracksRequest = server.takeRequest(1, TimeUnit.SECONDS);
        assertTrue(tracksRequest.getPath().contains("limit=" + SpotifyWebApiClient.MAX_ALBUM_TRACKS),
                "Expected the album tracks request to ask for the page-size limit, got: " + tracksRequest.getPath());
    }

    // ---- Playlist paging cap -------------------------------------------------------------------

    @Test
    @DisplayName("caps an oversized playlist at MAX_PLAYLIST_TRACKS and reports the true total")
    void capsOversizedPlaylist() throws Exception
    {
        enqueueToken();
        enqueueOk("{\"name\":\"Huge Playlist\"}");

        int total = 500;
        String items = IntStream.range(0, SpotifyWebApiClient.MAX_PLAYLIST_TRACKS)
                .mapToObj(i -> playlistItemJson("Track " + i, "Artist"))
                .collect(Collectors.joining(","));
        enqueueOk("{\"items\":[" + items + "],\"total\":" + total + "}");

        SpotifyWebApiClient client = newClient();
        SpotifyResolution resolution = client.resolve(new SpotifyReference(SpotifyReference.EntityType.PLAYLIST, "abc"));

        assertEquals(SpotifyWebApiClient.MAX_PLAYLIST_TRACKS, resolution.searchQueries().size());
        assertEquals(total, resolution.totalAvailable());
        assertTrue(resolution.capped());
        assertEquals(SpotifyWebApiClient.MAX_PLAYLIST_TRACKS, resolution.capLimit());
    }

    @Test
    @DisplayName("skips removed tracks (null) and local files in a playlist page")
    void skipsRemovedAndLocalPlaylistTracks() throws Exception
    {
        enqueueToken();
        enqueueOk("{\"name\":\"Mixed Playlist\"}");
        String items = String.join(",",
                playlistItemJson("Good Track", "Artist"),
                "{\"track\":null}",
                "{\"track\":{\"name\":\"Local File\",\"artists\":[],\"is_local\":true}}");
        enqueueOk("{\"items\":[" + items + "],\"total\":3}");

        SpotifyWebApiClient client = newClient();
        SpotifyResolution resolution = client.resolve(new SpotifyReference(SpotifyReference.EntityType.PLAYLIST, "abc"));

        assertEquals(List.of("Artist - Good Track"), resolution.searchQueries());
        assertEquals(3, resolution.totalAvailable());
        assertFalse(resolution.capped(), "3 total with a page size of 100 should not be capped");
    }

    @Test
    @DisplayName("requests exactly one page of playlist tracks at the documented page size")
    void playlistTracksRequestUsesPageSizeLimit() throws Exception
    {
        enqueueToken();
        enqueueOk("{\"name\":\"Playlist\"}");
        enqueueOk("{\"items\":[],\"total\":0}");

        SpotifyWebApiClient client = newClient();
        client.resolve(new SpotifyReference(SpotifyReference.EntityType.PLAYLIST, "abc"));

        server.takeRequest(1, TimeUnit.SECONDS); // token
        server.takeRequest(1, TimeUnit.SECONDS); // playlist metadata
        RecordedRequest tracksRequest = server.takeRequest(1, TimeUnit.SECONDS);
        assertTrue(tracksRequest.getPath().contains("limit=" + SpotifyWebApiClient.MAX_PLAYLIST_TRACKS),
                "Expected the playlist tracks request to ask for the page-size limit, got: " + tracksRequest.getPath());
    }

    // ---- Artist top tracks (not paged) ---------------------------------------------------------

    @Test
    @DisplayName("artist top tracks is never reported as capped")
    void artistTopTracksNeverCapped() throws Exception
    {
        enqueueToken();
        enqueueOk("{\"name\":\"Some Artist\"}");
        String tracks = IntStream.range(0, 10)
                .mapToObj(i -> trackJson("Hit " + i, "Some Artist"))
                .collect(Collectors.joining(","));
        enqueueOk("{\"tracks\":[" + tracks + "]}");

        SpotifyWebApiClient client = newClient();
        SpotifyResolution resolution = client.resolve(new SpotifyReference(SpotifyReference.EntityType.ARTIST, "abc"));

        assertEquals(10, resolution.searchQueries().size());
        assertFalse(resolution.capped());
        assertEquals(0, resolution.capLimit());
    }

    // ---- Errors -------------------------------------------------------------------------------

    @Test
    @DisplayName("throws when the API returns a non-200 status")
    void throwsOnHttpError() throws Exception
    {
        enqueueToken();
        server.enqueue(new MockResponse().setResponseCode(500));

        SpotifyWebApiClient client = newClient();
        try
        {
            client.resolve(new SpotifyReference(SpotifyReference.EntityType.TRACK, "abc"));
            throw new AssertionError("Expected an IOException");
        }
        catch (IOException expected)
        {
            // expected
        }
    }
}
