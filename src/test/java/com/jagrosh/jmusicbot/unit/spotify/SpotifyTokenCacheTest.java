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

import com.jagrosh.jmusicbot.spotify.SpotifyTokenCache;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the client-credentials token cache against a mock HTTP server — never the real
 * Spotify API — with a controllable clock so expiry/refresh timing is deterministic.
 */
@DisplayName("SpotifyTokenCache")
class SpotifyTokenCacheTest
{
    private MockWebServer server;
    private MutableClock clock;

    @BeforeEach
    void setUp() throws IOException
    {
        server = new MockWebServer();
        server.start();
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @AfterEach
    void tearDown() throws IOException
    {
        server.shutdown();
    }

    private SpotifyTokenCache newCache(String clientId, String clientSecret) throws Exception
    {
        String url = server.url("/api/token").toString();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        Constructor<SpotifyTokenCache> ctor = SpotifyTokenCache.class.getDeclaredConstructor(
                String.class, String.class, String.class, HttpClient.class, Clock.class);
        ctor.setAccessible(true);
        return ctor.newInstance(clientId, clientSecret, url, http, clock);
    }

    private void enqueueToken(String token, int expiresInSeconds)
    {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\",\"expires_in\":" + expiresInSeconds + "}"));
    }

    @Test
    @DisplayName("fetches a token on first use")
    void fetchesTokenOnFirstUse() throws Exception
    {
        enqueueToken("token-a", 3600);
        SpotifyTokenCache cache = newCache("client-id", "client-secret");

        assertEquals("token-a", cache.getAccessToken());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    @DisplayName("sends client id/secret as HTTP Basic auth and grant_type=client_credentials")
    void sendsClientCredentialsCorrectly() throws Exception
    {
        enqueueToken("token-a", 3600);
        SpotifyTokenCache cache = newCache("my-id", "my-secret");
        cache.getAccessToken();

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertTrue(request != null);
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("my-id:my-secret".getBytes());
        assertEquals(expectedAuth, request.getHeader("Authorization"));
        assertEquals("grant_type=client_credentials", request.getBody().readUtf8());
    }

    @Test
    @DisplayName("reuses a cached token instead of making a second request")
    void reusesCachedToken() throws Exception
    {
        enqueueToken("token-a", 3600);
        SpotifyTokenCache cache = newCache("client-id", "client-secret");

        assertEquals("token-a", cache.getAccessToken());

        clock.advance(Duration.ofMinutes(30)); // well within the 1-hour expiry
        assertEquals("token-a", cache.getAccessToken());
        assertEquals(1, server.getRequestCount(), "A second request should not have been made");
    }

    @Test
    @DisplayName("refreshes once the cached token is within the expiry margin")
    void refreshesNearExpiry() throws Exception
    {
        enqueueToken("token-a", 3600); // expires at T+3600s
        SpotifyTokenCache cache = newCache("client-id", "client-secret");
        assertEquals("token-a", cache.getAccessToken());

        // 3600 - 30 = 30s from expiry, inside the 60s refresh margin.
        clock.advance(Duration.ofSeconds(3600 - 30));
        enqueueToken("token-b", 3600);
        assertEquals("token-b", cache.getAccessToken());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("does not refresh just before the expiry margin is reached")
    void doesNotRefreshBeforeMargin() throws Exception
    {
        enqueueToken("token-a", 3600);
        SpotifyTokenCache cache = newCache("client-id", "client-secret");
        assertEquals("token-a", cache.getAccessToken());

        // 3600 - 120 = still outside the 60s refresh margin.
        clock.advance(Duration.ofSeconds(3600 - 120));
        assertEquals("token-a", cache.getAccessToken());
        assertEquals(1, server.getRequestCount(), "Token should still be considered valid");
    }

    @Test
    @DisplayName("refreshes after the token has fully expired")
    void refreshesAfterExpiry() throws Exception
    {
        enqueueToken("token-a", 60);
        SpotifyTokenCache cache = newCache("client-id", "client-secret");
        assertEquals("token-a", cache.getAccessToken());

        clock.advance(Duration.ofSeconds(120));
        enqueueToken("token-b", 3600);
        assertEquals("token-b", cache.getAccessToken());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("throws when the token endpoint returns a non-200 status")
    void throwsOnHttpError()
    {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"invalid_client\"}"));

        assertTrue(assertThrowsIoException(() ->
        {
            SpotifyTokenCache cache = newCache("bad-id", "bad-secret");
            cache.getAccessToken();
        }));
    }

    private interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    private static boolean assertThrowsIoException(ThrowingRunnable runnable)
    {
        try
        {
            runnable.run();
            return false;
        }
        catch (IOException expected)
        {
            return true;
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    /** A {@link Clock} whose instant can be advanced on demand, for deterministic expiry tests. */
    private static final class MutableClock extends Clock
    {
        private Instant instant;

        MutableClock(Instant initial)
        {
            this.instant = initial;
        }

        void advance(Duration duration)
        {
            instant = instant.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone()
        {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone)
        {
            return this;
        }

        @Override
        public Instant instant()
        {
            return instant;
        }
    }
}
