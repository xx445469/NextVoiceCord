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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.i18n.LanguageManager;
import com.jagrosh.jmusicbot.update.SelfUpdater;
import com.jagrosh.jmusicbot.update.UpdateChecker;
import com.jagrosh.jmusicbot.web.WebPanel;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@code /api/update-check} over real HTTP, the same way {@link YoutubeOauthWebPanelTest}
 * exercises {@code /api/youtube-oauth} — this is a claim about a network surface (which outcome
 * renders as which JSON, and who is allowed to trigger the outbound call at all), so a test that
 * poked {@link com.jagrosh.jmusicbot.update.SelfUpdater} directly would not actually prove
 * anything about what a request on the socket can see.
 *
 * <p>The endpoint now checks AND, when something newer exists, downloads it — see
 * {@link SelfUpdater#checkAndStage()}, which {@link WebPanel#handleUpdateCheck} calls via {@link
 * Bot#getUpdater()} rather than talking to {@link UpdateChecker} on its own any more. {@link
 * SelfUpdater} itself is wired here at a real {@link UpdateChecker} pointed at a {@link
 * MockWebServer} instead of the real GitHub API — the same substitution
 * {@code UpdateCheckerCheckOutcomeTest} makes one layer down, and {@code SelfUpdaterCheckAndStageTest}
 * makes for {@link SelfUpdater} directly — with the running version fixed via reflection into
 * {@link SelfUpdater}'s package-private test-only constructor.
 *
 * @author adan (xx445469)
 */
class UpdateCheckWebPanelTest
{
    @TempDir
    Path directory;

    private MockWebServer github;
    private WebPanel panel;
    private Bot bot;
    private String token;
    private int port;
    private String previousConfigProperty;

    @BeforeEach
    void setUp() throws IOException
    {
        github = new MockWebServer();
        github.start();
    }

    @AfterEach
    void tearDown() throws IOException
    {
        if (panel != null)
        {
            panel.stop();
        }
        github.shutdown();
        if (previousConfigProperty == null)
        {
            System.clearProperty("config.file");
        }
        else
        {
            System.setProperty("config.file", previousConfigProperty);
        }
    }

    private void startPanel() throws Exception
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

        bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(botConfig);
        when(bot.getJDA()).thenReturn(null);
        when(bot.getStartTime()).thenReturn(Instant.now());
        when(bot.getLanguages()).thenReturn(LanguageManager.load(Language.EN));
        // bot.getUpdater() left unstubbed (null) unless a test calls withUpdater(): the
        // notConnectedYet test needs exactly that, the real state before Bot.setJDA() runs.

        port = freePort();
        panel = new WebPanel(bot, port);
        panel.start();

        token = panel.getUrl().orElseThrow().replaceAll(".*token=", "");
    }

    /**
     * {@code currentVersion} is fixed at {@code "1.0.0"} via the same test-only seam {@code
     * SelfUpdaterCheckAndStageTest} uses — see that class's {@code newUpdater} for why: {@code
     * OtherUtil.getCurrentVersion()} answers {@code "UNKNOWN"} outside a packaged jar, which
     * {@code UpdateChecker.isNewer} treats as never newer than anything, making "update
     * available" unreachable from a test otherwise.
     */
    private void withUpdater() throws ReflectiveOperationException
    {
        String apiRoot = "http://localhost:" + github.getPort() + "/repos/";
        Constructor<SelfUpdater> ctor = SelfUpdater.class.getDeclaredConstructor(
                Bot.class, UpdateChecker.class, String.class);
        ctor.setAccessible(true);
        SelfUpdater updater = ctor.newInstance(bot, new UpdateChecker(apiRoot), "1.0.0");
        when(bot.getUpdater()).thenReturn(updater);
    }

    private String assetUrl()
    {
        return "http://localhost:" + github.getPort() + "/repos/xx445469/NextVoiceCord/releases/assets/1";
    }

    private static String releaseJson(String tag, String assetUrl, int size)
    {
        return """
                {
                  "tag_name": "%s",
                  "html_url": "https://github.com/xx445469/NextVoiceCord/releases/tag/%s",
                  "assets": [
                    { "name": "bot.jar", "url": "%s", "size": %d }
                  ],
                  "prerelease": false
                }
                """.formatted(tag, tag, assetUrl, size);
    }

    @Test
    @DisplayName("reports upToDate, with the running version, when nothing newer is published")
    void reportsUpToDate() throws Exception
    {
        github.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(releaseJson("v1.0.0", assetUrl(), 999))
                .setHeader("Content-Type", "application/json"));

        startPanel();
        withUpdater();
        Response response = get("/api/update-check?token=" + token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"status\":\"upToDate\""), response.body());
        assertTrue(response.body().contains("\"currentVersion\":\"1.0.0\""), response.body());
    }

    @Test
    @DisplayName("a newer version is published and downloads cleanly: reports staged, with the version and a browsable URL")
    void reportsStaged() throws Exception
    {
        String assetContent = "not a real jar, just a handful of test bytes";
        github.enqueue(new MockResponse().setResponseCode(200)
                .setBody(releaseJson("v2.0.0", assetUrl(), assetContent.length()))
                .setHeader("Content-Type", "application/json"));
        github.enqueue(new MockResponse().setResponseCode(200)
                .setBody(releaseJson("v2.0.0", assetUrl(), assetContent.length()))
                .setHeader("Content-Type", "application/json"));
        github.enqueue(new MockResponse().setResponseCode(200)
                .setBody(assetContent)
                .setHeader("Content-Type", "application/octet-stream"));

        startPanel();
        withUpdater();
        Response response = get("/api/update-check?token=" + token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"status\":\"staged\""), response.body());
        assertTrue(response.body().contains("\"version\":\"2.0.0\""), response.body());
        assertTrue(response.body().contains(
                "\"url\":\"https://github.com/xx445469/NextVoiceCord/releases/tag/v2.0.0\""),
                response.body());
    }

    @Test
    @DisplayName("a newer version is published but the download fails: reports downloadFailed, with the version")
    void reportsDownloadFailed() throws Exception
    {
        github.enqueue(new MockResponse().setResponseCode(200)
                .setBody(releaseJson("v2.0.0", assetUrl(), 999))
                .setHeader("Content-Type", "application/json"));
        github.enqueue(new MockResponse().setResponseCode(200)
                .setBody(releaseJson("v2.0.0", assetUrl(), 999))
                .setHeader("Content-Type", "application/json"));
        github.enqueue(new MockResponse().setResponseCode(500));

        startPanel();
        withUpdater();
        Response response = get("/api/update-check?token=" + token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"status\":\"downloadFailed\""), response.body());
        assertTrue(response.body().contains("\"version\":\"2.0.0\""), response.body());
    }

    @Test
    @DisplayName("reports failed, with a detail string, when GitHub cannot be reached")
    void reportsFailed() throws Exception
    {
        github.enqueue(new MockResponse().setResponseCode(500));

        startPanel();
        withUpdater();
        Response response = get("/api/update-check?token=" + token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"status\":\"failed\""), response.body());
        assertTrue(response.body().contains("\"detail\":"), response.body());
    }

    @Test
    @DisplayName("not connected to Discord yet: reports notReady rather than throwing")
    void reportsNotReadyWhenUpdaterAbsent() throws Exception
    {
        startPanel();
        // bot.getUpdater() left unstubbed — null, the real state before Bot.setJDA() runs.

        Response response = get("/api/update-check?token=" + token);

        assertEquals(409, response.status());
        assertTrue(github.getRequestCount() == 0, "not being connected yet must never reach GitHub");
    }

    @Test
    @DisplayName("an unauthenticated caller gets nothing, and no request ever reaches GitHub")
    void unauthenticatedCallerTriggersNoOutboundRequest() throws Exception
    {
        // Nothing enqueued: if the handler ever reached SelfUpdater despite the missing
        // token, MockWebServer would have nothing queued to answer with and the test would
        // hang or error on takeRequest() below, rather than pass by accident.
        startPanel();
        withUpdater();

        Response noToken = get("/api/update-check");
        assertEquals(401, noToken.status());

        Response badToken = get("/api/update-check?token=not-the-real-token");
        assertEquals(401, badToken.status());

        // A query-string token is fine for other GET reads, but confirm this one actually
        // requires SOME valid credential rather than being open regardless — the two checks
        // above only show a bad token is refused, not that a request without any auth path
        // reaching the network is impossible in principle.
        assertTrue(github.takeRequest(500, TimeUnit.MILLISECONDS) == null,
                "an unauthenticated caller's request must never reach GitHub");
    }

    @Test
    @DisplayName("only GET is served; other methods do not run a check")
    void onlyGetIsServed() throws Exception
    {
        startPanel();
        withUpdater();
        HttpURLConnection connection = open("/api/update-check?token=" + token);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
        int status = connection.getResponseCode();
        connection.disconnect();

        assertEquals(405, status);
        assertTrue(github.takeRequest(500, TimeUnit.MILLISECONDS) == null,
                "a POST must never trigger the outbound GitHub call either");
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
