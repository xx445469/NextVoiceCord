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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads track lists out of the Spotify Web API.
 *
 * <p>This is metadata only — titles and artist names. It is the one piece of this feature that
 * actually talks to Spotify; everything played afterwards comes from YouTube.
 *
 * <h2>Caps</h2>
 * The playlist-tracks and album-tracks endpoints page: playlists at up to 100 items per page,
 * albums at up to 50. Rather than walk every page of a playlist that might hold thousands of
 * tracks, this client reads exactly one page from each and reports the true total (from the
 * API's own {@code total} field) alongside how many were actually loaded, so a capped playlist
 * says so instead of silently looking like a complete one. Artist "top tracks" is not paged —
 * Spotify returns at most 10 — so there is nothing to cap there.
 *
 * @author adan (xx445469)
 */
public class SpotifyWebApiClient
{
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_API_ROOT = "https://api.spotify.com/v1";

    /** Spotify's playlist-tracks endpoint pages at 100 items; this client reads one page. */
    public static final int MAX_PLAYLIST_TRACKS = 100;
    /** Spotify's album-tracks endpoint pages at 50 items; this client reads one page. */
    public static final int MAX_ALBUM_TRACKS = 50;

    // Artist top-tracks requires a market; Spotify does not offer a "no market" option for it
    // with a client-credentials token. This is a simplification, not a config option: it just
    // means artist results reflect the US catalog.
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(SpotifyWebApiClient.class);

    private static final String TOP_TRACKS_MARKET = "US";

    /** Matches what top-tracks would have returned, so the fallback is not a different size. */
    private static final int ARTIST_SEARCH_LIMIT = 10;

    private final SpotifyTokenCache tokenCache;
    private final HttpClient http;
    private final String apiRoot;

    public SpotifyWebApiClient(SpotifyTokenCache tokenCache)
    {
        this(tokenCache, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), DEFAULT_API_ROOT);
    }

    /** Visible for tests: lets a test point at a mock server. */
    SpotifyWebApiClient(SpotifyTokenCache tokenCache, HttpClient http, String apiRoot)
    {
        this.tokenCache = tokenCache;
        this.http = http;
        this.apiRoot = apiRoot;
    }

    /** Resolves any of the four supported reference kinds to a track list. */
    public SpotifyResolution resolve(SpotifyReference reference) throws IOException, InterruptedException
    {
        return switch (reference.type())
        {
            case TRACK -> resolveTrack(reference.id());
            case ALBUM -> resolveAlbum(reference.id());
            case PLAYLIST -> resolvePlaylist(reference.id());
            case ARTIST -> resolveArtistTopTracks(reference.id());
        };
    }

    private SpotifyResolution resolveTrack(String id) throws IOException, InterruptedException
    {
        JsonNode track = get("/tracks/" + id, null);
        String query = queryFromTrackNode(track);
        List<String> queries = query == null ? List.of() : List.of(query);
        String name = track.path("name").asText("Spotify track");
        return new SpotifyResolution(SpotifyReference.EntityType.TRACK, name, queries, queries.size(), false, 0);
    }

    private SpotifyResolution resolveAlbum(String id) throws IOException, InterruptedException
    {
        JsonNode album = get("/albums/" + id, null);
        String name = album.path("name").asText("Spotify album");

        JsonNode page = get("/albums/" + id + "/tracks", "limit=" + MAX_ALBUM_TRACKS + "&offset=0");
        List<String> queries = queriesFromItems(page.path("items"), false);
        int total = page.path("total").asInt(queries.size());
        boolean capped = total > MAX_ALBUM_TRACKS;

        return new SpotifyResolution(SpotifyReference.EntityType.ALBUM, name, queries, total, capped,
                capped ? MAX_ALBUM_TRACKS : 0);
    }

    private SpotifyResolution resolvePlaylist(String id) throws IOException, InterruptedException
    {
        JsonNode playlist = get("/playlists/" + id, "fields=" + encode("name"));
        String name = playlist.path("name").asText("Spotify playlist");

        JsonNode page = get("/playlists/" + id + "/tracks",
                "limit=" + MAX_PLAYLIST_TRACKS + "&offset=0&fields=" + encode("total,items(track(name,artists(name),is_local))"));
        List<String> queries = queriesFromItems(page.path("items"), true);
        int total = page.path("total").asInt(queries.size());
        boolean capped = total > MAX_PLAYLIST_TRACKS;

        return new SpotifyResolution(SpotifyReference.EntityType.PLAYLIST, name, queries, total, capped,
                capped ? MAX_PLAYLIST_TRACKS : 0);
    }

    private SpotifyResolution resolveArtistTopTracks(String id) throws IOException, InterruptedException
    {
        JsonNode artist = get("/artists/" + id, null);
        String name = artist.path("name").asText("Spotify artist");

        List<String> queries;
        try
        {
            JsonNode response = get("/artists/" + id + "/top-tracks", "market=" + TOP_TRACKS_MARKET);
            // Not paged: Spotify returns at most 10 top tracks, so there is nothing to cap.
            queries = queriesFromItems(response.path("tracks"), false);
        }
        catch (ForbiddenException ex)
        {
            // Spotify refuses this endpoint to applications registered after its November 2024
            // cut-off, while leaving /artists and /search open to the same credentials. So a
            // perfectly valid app gets 403 here and nowhere else, and failing the whole request
            // would mean artist links never work for anyone who registered recently.
            LOG.info("Spotify refuses top-tracks for this application; falling back to search for \"{}\".", name);
            queries = searchTracksByArtist(name);
        }

        return new SpotifyResolution(SpotifyReference.EntityType.ARTIST, name, queries, queries.size(), false, 0);
    }

    /**
     * The artist's tracks by way of the search endpoint.
     *
     * <p>Not the same list as top-tracks — search ranks by relevance rather than by plays — but
     * it is the artist's own material, which is what someone pasting an artist link is asking
     * for. Better than the alternative of refusing the link entirely.
     */
    private List<String> searchTracksByArtist(String name) throws IOException, InterruptedException
    {
        String query = encode("artist:\"" + name + "\"");
        JsonNode response = get("/search", "q=" + query + "&type=track&limit=" + ARTIST_SEARCH_LIMIT
                + "&market=" + TOP_TRACKS_MARKET);
        return queriesFromItems(response.path("tracks").path("items"), false);
    }

    /**
     * Builds one "artist - title" search query per item.
     *
     * @param wrapped whether each item wraps the track under a {@code "track"} field (playlist
     *                items do; album tracks and top-tracks do not)
     */
    private static List<String> queriesFromItems(JsonNode items, boolean wrapped)
    {
        List<String> queries = new ArrayList<>();
        for (JsonNode item : items)
        {
            JsonNode trackNode = wrapped ? item.path("track") : item;
            String query = queryFromTrackNode(trackNode);
            if (query != null)
            {
                queries.add(query);
            }
        }
        return queries;
    }

    /** Returns an "artist - title" query for one track node, or null if it cannot be matched. */
    private static String queryFromTrackNode(JsonNode trackNode)
    {
        if (trackNode == null || trackNode.isMissingNode() || trackNode.isNull())
        {
            // A playlist item can have a null track: Spotify removed it from its catalog.
            return null;
        }
        if (trackNode.path("is_local").asBoolean(false))
        {
            // Local files were never on Spotify's catalog to begin with; there is no metadata
            // to search YouTube with beyond a filename, which is not worth guessing at.
            return null;
        }

        String title = trackNode.path("name").asText("");
        if (title.isBlank())
        {
            return null;
        }

        String artist = firstArtistName(trackNode.path("artists"));
        return artist.isBlank() ? title : artist + " - " + title;
    }

    private static String firstArtistName(JsonNode artists)
    {
        if (artists.isArray() && !artists.isEmpty())
        {
            return artists.get(0).path("name").asText("");
        }
        return "";
    }

    private JsonNode get(String path, String query) throws IOException, InterruptedException
    {
        String token = tokenCache.getAccessToken();
        String url = apiRoot + path + (query == null ? "" : "?" + query);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404)
        {
            throw new IOException("Spotify link not found (it may be wrong, private, or region-locked)");
        }
        if (response.statusCode() == 403)
        {
            throw new ForbiddenException(describeFailure(path, 403, response.body()));
        }
        if (response.statusCode() != 200)
        {
            throw new IOException(describeFailure(path, response.statusCode(), response.body()));
        }

        return MAPPER.readTree(response.body());
    }

    /**
     * Turns a failed response into something someone can act on.
     *
     * <p>The status code alone was all this used to report, and a bare "HTTP 403" says nothing
     * about whether the credentials are wrong, the app is restricted, or the request was.
     * Spotify puts the reason in the body; throwing it away meant every failure looked the same.
     */
    private static String describeFailure(String path, int status, String body)
    {
        String detail = "";
        try
        {
            JsonNode error = MAPPER.readTree(body).path("error");
            String message = error.path("message").asText("");
            if (!message.isBlank())
            {
                detail = " — Spotify says: " + message;
            }
        }
        catch (Exception ignored)
        {
            // A non-JSON body is worth nothing to the reader; the status still is.
        }

        String hint = switch (status)
        {
            case 401 -> " Check spotify.clientId and spotify.clientSecret in config.txt.";
            // 403 on a plain catalogue lookup is not a credentials problem — those return 401.
            // It means this application is not allowed to make the call, which in practice is
            // the Development-mode quota or a restriction on the app itself.
            case 403 -> " The credentials are valid but this application is not permitted to make"
                    + " that request. Check the app's status in the Spotify developer dashboard;"
                    + " apps in Development mode are restricted.";
            case 429 -> " Spotify is rate limiting this application. Try again shortly.";
            default -> "";
        };

        return "Spotify API request failed with HTTP " + status + " on " + path + detail + hint;
    }

    /** A 403, distinguished so a caller can degrade rather than treating every failure alike. */
    static final class ForbiddenException extends IOException
    {
        ForbiddenException(String message)
        {
            super(message);
        }
    }

    private static String encode(String fields)
    {
        return java.net.URLEncoder.encode(fields, java.nio.charset.StandardCharsets.UTF_8);
    }
}
