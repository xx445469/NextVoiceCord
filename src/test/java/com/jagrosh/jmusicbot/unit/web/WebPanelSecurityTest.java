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

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.i18n.LanguageManager;
import com.jagrosh.jmusicbot.web.WebPanel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the panel over real HTTP, because these are claims about a network surface.
 *
 * <p>The panel can rewrite {@code config.txt} and reads a file containing the Discord token.
 * Unit-testing the pieces would leave the thing that actually matters untested — what a request
 * arriving on the socket can get out of it, or do to it. So this starts a server and makes
 * requests.
 *
 * @author adan (xx445469)
 */
class WebPanelSecurityTest
{
    /** Recognisable in a response body, and long enough not to occur by accident. */
    private static final String FAKE_TOKEN = "NOT_A_REAL_TOKEN_ffffffffffffffffffffffffffff";

    @TempDir
    Path directory;

    private WebPanel panel;
    private String token;
    private int port;
    private String previousConfigProperty;

    @BeforeEach
    void startPanel() throws IOException
    {
        Path config = directory.resolve("config.txt");
        Files.writeString(config, """
                discord {
                  token = "%s"
                }

                commands {
                  prefix = "!"
                }

                web {
                  bindAddress = "127.0.0.1"
                  allowConfigEdit = false
                }
                """.formatted(FAKE_TOKEN), StandardCharsets.UTF_8);

        previousConfigProperty = System.getProperty("config.file");
        System.setProperty("config.file", config.toAbsolutePath().toString());

        BotConfig botConfig = mock(BotConfig.class);
        when(botConfig.getWebBindAddress()).thenReturn("127.0.0.1");
        when(botConfig.isWebConfigEditAllowed()).thenReturn(false);
        when(botConfig.getGuiLanguage()).thenReturn(Language.EN);
        when(botConfig.getDefaultLanguage()).thenReturn(Language.EN);

        Bot bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(botConfig);
        when(bot.getJDA()).thenReturn(null);
        when(bot.getStartTime()).thenReturn(Instant.now());
        when(bot.getLanguages()).thenReturn(LanguageManager.load(Language.EN));

        // Port 0 lets the OS pick a free one, so a developer machine already running the bot
        // does not fail this test with a bind conflict.
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

    @Test
    @DisplayName("the Discord token never appears in the config response")
    void configResponseOmitsTheToken() throws IOException
    {
        Response response = get("/api/config?token=" + token);

        assertEquals(200, response.status());
        assertFalse(response.body().contains(FAKE_TOKEN),
                    "the bot token must not be readable through the panel");
        assertTrue(response.body().contains("discord.token"),
                   "the option should still be listed, so it can be seen to exist");
        assertTrue(response.body().contains("\"secret\":true"),
                   "and marked as one that is being withheld");
    }

    @Test
    @DisplayName("a token in the query string is refused for writes")
    void writesRejectQueryStringTokens() throws IOException
    {
        // This is the CSRF defence. Another site can make a browser POST to this URL carrying
        // whatever is in it; it cannot set an Authorization header. If a query token were ever
        // accepted here, a link would be enough to rewrite someone's config.
        Response response = post("/api/config?token=" + token, "{\"commands.prefix\":\"?\"}", null);

        assertEquals(401, response.status(), "a query-string token must not authorise a write");
    }

    @Test
    @DisplayName("writes are refused while config editing is off")
    void writesRefusedWhenDisabled() throws IOException
    {
        Response response = post("/api/config", "{\"commands.prefix\":\"?\"}", token);

        assertEquals(403, response.status());
        assertTrue(response.body().contains("disabled"), "and should say why");
    }

    @Test
    @DisplayName("reads still work with a bearer token")
    void readsAcceptBearerTokens() throws IOException
    {
        assertEquals(200, get("/api/status", token).status());
    }

    @Test
    @DisplayName("a wrong token is refused")
    void wrongTokenRefused() throws IOException
    {
        assertEquals(401, get("/api/status?token=wrong").status());
    }

    @Test
    @DisplayName("repeated bad tokens lock the address out")
    void repeatedFailuresLockOut() throws IOException
    {
        int lastStatus = 0;
        for (int attempt = 0; attempt < 12; attempt++)
        {
            lastStatus = get("/api/status?token=wrong" + attempt).status();
        }
        assertEquals(429, lastStatus, "guessing should stop being answered");

        // And the lockout is by address, so it applies to a correct token from the same place
        // too — otherwise it would only slow down someone who never gets it right.
        assertEquals(429, get("/api/status?token=" + token).status());
    }

    @Test
    @DisplayName("endpoints that change things reject GET")
    void mutatingEndpointsRejectGet() throws IOException
    {
        assertEquals(405, get("/api/control?token=" + token).status());
        assertEquals(405, get("/api/prefs?token=" + token).status());
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
        return get(path, null);
    }

    private Response get(String path, String bearer) throws IOException
    {
        HttpURLConnection connection = open(path);
        if (bearer != null)
        {
            connection.setRequestProperty("Authorization", "Bearer " + bearer);
        }
        return read(connection);
    }

    private Response post(String path, String body, String bearer) throws IOException
    {
        HttpURLConnection connection = open(path);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        if (bearer != null)
        {
            connection.setRequestProperty("Authorization", "Bearer " + bearer);
        }
        connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
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
