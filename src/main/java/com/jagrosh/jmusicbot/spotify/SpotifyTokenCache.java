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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Obtains and caches a Spotify Web API access token via the client credentials flow.
 *
 * <p>This flow proves the bot's identity, not a user's — it is what lets the bot read public
 * catalog data (track/album/playlist/artist metadata) but it can never grant access to playback,
 * private libraries, or anything resembling actual Spotify audio.
 *
 * <p>Tokens expire (currently around an hour, per {@code expires_in} in the response); this
 * class refreshes proactively, shortly before that happens, rather than on every request or only
 * after a call fails.
 *
 * @author adan (xx445469)
 */
public class SpotifyTokenCache
{
    private static final Logger LOG = LoggerFactory.getLogger(SpotifyTokenCache.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_TOKEN_URL = "https://accounts.spotify.com/api/token";

    /** Refresh this long before the token actually expires, so a request never races expiry. */
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;
    private final HttpClient http;
    private final Clock clock;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.MIN;

    public SpotifyTokenCache(String clientId, String clientSecret)
    {
        this(clientId, clientSecret, DEFAULT_TOKEN_URL,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                Clock.systemUTC());
    }

    /** Visible for tests: lets a test point at a mock server and control the clock. */
    SpotifyTokenCache(String clientId, String clientSecret, String tokenUrl, HttpClient http, Clock clock)
    {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUrl = tokenUrl;
        this.http = http;
        this.clock = clock;
    }

    /**
     * Returns a valid access token, refreshing it first if none is cached or the cached one is
     * close to expiring.
     *
     * @throws IOException          if the token request fails or returns something unusable
     * @throws InterruptedException if the calling thread is interrupted while the request is in flight
     */
    public synchronized String getAccessToken() throws IOException, InterruptedException
    {
        Instant now = Instant.now(clock);
        if (cachedToken != null && now.isBefore(expiresAt.minus(REFRESH_MARGIN)))
        {
            return cachedToken;
        }
        refresh(now);
        return cachedToken;
    }

    /** Whether a cached token would currently be served without a network call. Test hook. */
    boolean isCachedTokenValid(Instant asOf)
    {
        return cachedToken != null && asOf.isBefore(expiresAt.minus(REFRESH_MARGIN));
    }

    private void refresh(Instant now) throws IOException, InterruptedException
    {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
        {
            // Deliberately excludes the response body: Spotify's own error payload could, in
            // principle, echo back request details, and this text can end up in a chat message.
            throw new IOException("Spotify token request failed with HTTP " + response.statusCode());
        }

        JsonNode node = MAPPER.readTree(response.body());
        String token = node.path("access_token").asText(null);
        int expiresInSeconds = node.path("expires_in").asInt(0);

        if (token == null || token.isBlank())
        {
            throw new IOException("Spotify token response did not include an access token");
        }

        cachedToken = token;
        expiresAt = now.plusSeconds(Math.max(0, expiresInSeconds));
        LOG.debug("Refreshed Spotify access token; expires in {}s", expiresInSeconds);
    }
}
