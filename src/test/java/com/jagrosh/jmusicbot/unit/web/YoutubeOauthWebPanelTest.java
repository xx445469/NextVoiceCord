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
package com.jagrosh.jmusicbot.unit.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.i18n.LanguageManager;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.utils.YoutubeOauth2TokenHandler;
import com.jagrosh.jmusicbot.web.WebPanel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * Exercises {@code /api/youtube-oauth} over real HTTP, the same way {@link WebPanelSecurityTest}
 * exercises the rest of the panel — these are claims about a network surface, so a unit test
 * poking the Java objects directly would not actually prove anything about what a request on the
 * socket can see.
 *
 * <p>{@code youtubetoken.txt} is resolved by {@link OtherUtil#getPath} relative to the process's
 * working directory, not to anything a {@code @TempDir} can redirect — the same file every other
 * test touching {@link YoutubeOauth2TokenHandler} uses. Every test here therefore records whatever
 * was there before it ran and restores exactly that afterwards, rather than assuming an empty
 * starting point.
 *
 * @author adan (xx445469)
 */
class YoutubeOauthWebPanelTest
{
    private static final Path TOKEN_FILE = OtherUtil.getPath("youtubetoken.txt");
    /** Recognisable in a response body, and long enough not to occur by accident. */
    private static final String FAKE_REFRESH_TOKEN = "ya29.NOT_A_REAL_REFRESH_TOKEN_ffffffffffffffffffffffffffff";

    @TempDir
    Path directory;

    private WebPanel panel;
    private String token;
    private int port;
    private String previousConfigProperty;
    private byte[] tokenFileBefore;

    @BeforeEach
    void saveTokenFileState() throws IOException
    {
        tokenFileBefore = Files.exists(TOKEN_FILE) ? Files.readAllBytes(TOKEN_FILE) : null;
        Files.deleteIfExists(TOKEN_FILE);
    }

    @AfterEach
    void restoreTokenFileState() throws IOException
    {
        Files.deleteIfExists(TOKEN_FILE);
        if (tokenFileBefore != null)
        {
            Files.write(TOKEN_FILE, tokenFileBefore);
        }
    }

    private void startPanel(boolean oauthEnabled, YoutubeOauth2TokenHandler handler) throws IOException
    {
        Path config = directory.resolve("config.txt");
        Files.writeString(config, """
                discord {
                  token = "not-a-real-token"
                }

                commands {
                  prefix = "!"
                }

                web {
                  bindAddress = "127.0.0.1"
                  allowConfigEdit = false
                }
                """, StandardCharsets.UTF_8);

        previousConfigProperty = System.getProperty("config.file");
        System.setProperty("config.file", config.toAbsolutePath().toString());

        BotConfig botConfig = mock(BotConfig.class);
        when(botConfig.getWebBindAddress()).thenReturn("127.0.0.1");
        when(botConfig.isWebConfigEditAllowed()).thenReturn(false);
        when(botConfig.getGuiLanguage()).thenReturn(Language.EN);
        when(botConfig.getDefaultLanguage()).thenReturn(Language.EN);
        when(botConfig.useYouTubeOauth()).thenReturn(oauthEnabled);

        Bot bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(botConfig);
        when(bot.getJDA()).thenReturn(null);
        when(bot.getStartTime()).thenReturn(Instant.now());
        when(bot.getLanguages()).thenReturn(LanguageManager.load(Language.EN));
        when(bot.getYouTubeOauth2Handler()).thenReturn(handler);

        port = freePort();
        panel = new WebPanel(bot, port);
        panel.start();

        token = panel.getUrl().orElseThrow().replaceAll(".*token=", "");
    }

    @AfterEach
    void stopPanel()
    {
        if (panel != null)
        {
            panel.stop();
        }
        if (previousConfigProperty == null)
        {
            System.clearProperty("config.file");
        }
        else
        {
            System.setProperty("config.file", previousConfigProperty);
        }
    }

    /** Feeds a handler the device-code log line directly, the same way the unit tests for it do. */
    private static YoutubeOauth2TokenHandler handlerWithPendingCode(String url, String code)
    {
        YoutubeOauth2TokenHandler handler = new YoutubeOauth2TokenHandler();
        Logger youtubeLogger = (Logger) LoggerFactory.getLogger("dev.lavalink.youtube.http.YoutubeOauth2Handler");
        handler.decide(null, youtubeLogger, Level.INFO,
                "OAUTH INTEGRATION: To give youtube-source access to your account, go to {} and enter code {}",
                new Object[] { url, code }, null);
        return handler;
    }

    @Test
    @DisplayName("reports OAUTH_DISABLED when playback.youtube.useOAuth is off")
    void reportsDisabledWhenOauthOff() throws IOException
    {
        startPanel(false, new YoutubeOauth2TokenHandler());

        Response response = get("/api/youtube-oauth?token=" + token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"phase\":\"OAUTH_DISABLED\""), response.body());
    }

    @Test
    @DisplayName("reports WAITING_FOR_CODE when OAuth is on but no code has arrived")
    void reportsWaitingWhenNoCodeYet() throws IOException
    {
        startPanel(true, new YoutubeOauth2TokenHandler());

        Response response = get("/api/youtube-oauth?token=" + token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"phase\":\"WAITING_FOR_CODE\""), response.body());
    }

    @Test
    @DisplayName("reports CODE_READY with the code folded into the URL once one arrives")
    void reportsCodeReadyOnceCodeArrives() throws IOException
    {
        startPanel(true, handlerWithPendingCode("https://www.google.com/device", "ABCD-EFGH"));

        Response response = get("/api/youtube-oauth?token=" + token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"phase\":\"CODE_READY\""), response.body());
        assertTrue(response.body().contains("\"code\":\"ABCD-EFGH\""), response.body());
        assertTrue(response.body().contains("user_code=ABCD-EFGH"),
                   "the URL sent to the panel should already carry the code");
    }

    @Test
    @DisplayName("reports SIGNED_IN when a token is already stored, and never sends its contents")
    void reportsSignedInWithoutExposingTheToken() throws IOException
    {
        Files.writeString(TOKEN_FILE, FAKE_REFRESH_TOKEN, StandardCharsets.UTF_8);
        startPanel(true, new YoutubeOauth2TokenHandler());

        Response response = get("/api/youtube-oauth?token=" + token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"phase\":\"SIGNED_IN\""), response.body());
        assertFalse(response.body().contains(FAKE_REFRESH_TOKEN),
                    "youtubetoken.txt's contents must never leave the process");
        assertFalse(response.body().toLowerCase(java.util.Locale.ROOT).contains("\"token\""),
                    "the payload should not carry a token field of any kind");
    }

    @Test
    @DisplayName("signing out deletes the stored token and requires a header token, not a query one")
    void signOutDeletesTheStoredTokenAndRequiresAHeaderToken() throws IOException
    {
        Files.writeString(TOKEN_FILE, FAKE_REFRESH_TOKEN, StandardCharsets.UTF_8);
        startPanel(true, new YoutubeOauth2TokenHandler());

        // The CSRF defence every other mutating endpoint has: a query-string token must not work.
        Response viaQueryString = post("/api/youtube-oauth?token=" + token, null);
        assertEquals(401, viaQueryString.status());
        assertTrue(Files.exists(TOKEN_FILE), "a refused sign-out must not delete anything");

        Response viaHeader = post("/api/youtube-oauth", token);
        assertEquals(200, viaHeader.status());
        assertFalse(Files.exists(TOKEN_FILE), "signing out should delete youtubetoken.txt");
        assertFalse(viaHeader.body().contains(FAKE_REFRESH_TOKEN),
                    "the deleted token's contents must not appear in the response either");

        Response afterSignOut = get("/api/youtube-oauth?token=" + token);
        assertTrue(afterSignOut.body().contains("\"phase\":\"WAITING_FOR_CODE\""), afterSignOut.body());
    }

    // ==================== Plumbing ====================

    private static int freePort() throws IOException
    {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0))
        {
            return socket.getLocalPort();
        }
    }

    private Response get(String path) throws IOException
    {
        return read(open(path));
    }

    private Response post(String path, String bearer) throws IOException
    {
        HttpURLConnection connection = open(path);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        if (bearer != null)
        {
            connection.setRequestProperty("Authorization", "Bearer " + bearer);
        }
        connection.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
        return read(connection);
    }

    private HttpURLConnection open(String path) throws IOException
    {
        return (HttpURLConnection) URI.create("http://127.0.0.1:" + port + path).toURL().openConnection();
    }

    private static Response read(HttpURLConnection connection) throws IOException
    {
        int status = connection.getResponseCode();
        var stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();
        return new Response(status, body);
    }

    private record Response(int status, String body) { }
}
