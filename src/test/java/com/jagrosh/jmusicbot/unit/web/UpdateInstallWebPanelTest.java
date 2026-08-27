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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.i18n.LanguageManager;
import com.jagrosh.jmusicbot.update.SelfUpdater;
import com.jagrosh.jmusicbot.update.UpdateChecker;
import com.jagrosh.jmusicbot.web.WebPanel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code /api/update-install} over real HTTP — the riskiest endpoint {@link WebPanel} exposes,
 * so this is a claim about exactly what the socket lets through, not about
 * {@link SelfUpdater} in isolation (see {@code SelfUpdaterInstallDecisionTest} for that side).
 *
 * <p>Nothing here ever reaches an actual install: every test either gets refused before {@link
 * SelfUpdater} is ever consulted, or reaches it with nothing staged, or reaches it staged but
 * never sends {@code confirm:true} — the one thing that would schedule {@link
 * SelfUpdater#installNow()} at all. {@code bot.getThreadpool()} is a Mockito mock throughout, so
 * even the one test that does send {@code confirm:true} only proves the process asked to have
 * the install scheduled — the mock never actually runs the task, so nothing is downloaded,
 * moved, or restarted.
 *
 * @author adan (xx445469)
 */
class UpdateInstallWebPanelTest
{
    @TempDir
    Path directory;

    private WebPanel panel;
    private Bot bot;
    private BotConfig botConfig;
    private ScheduledExecutorService threadpool;
    private String token;
    private int port;
    private String previousConfigProperty;

    @BeforeEach
    void setUp()
    {
        threadpool = mock(ScheduledExecutorService.class);
    }

    @AfterEach
    void tearDown()
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

    private void startPanel(String bindAddress, boolean allowConfigEdit) throws Exception
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
                  bindAddress = "%s"
                  allowConfigEdit = %s
                }
                """.formatted(bindAddress, allowConfigEdit), StandardCharsets.UTF_8);

        previousConfigProperty = System.getProperty("config.file");
        System.setProperty("config.file", config.toAbsolutePath().toString());

        botConfig = mock(BotConfig.class);
        when(botConfig.getWebBindAddress()).thenReturn(bindAddress);
        when(botConfig.isWebConfigEditAllowed()).thenReturn(allowConfigEdit);
        when(botConfig.getGuiLanguage()).thenReturn(Language.EN);
        when(botConfig.getDefaultLanguage()).thenReturn(Language.EN);

        bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(botConfig);
        when(bot.getJDA()).thenReturn(null);
        when(bot.getStartTime()).thenReturn(Instant.now());
        when(bot.getLanguages()).thenReturn(LanguageManager.load(Language.EN));
        when(bot.getThreadpool()).thenReturn(threadpool);
        // bot.getUpdater() left unstubbed (null) unless a test calls withStagedUpdater(): the
        // gating tests must never need it, since a caller refused by auth or the bind gate
        // should never reach far enough to ask.

        port = freePort();
        panel = new WebPanel(bot, port);
        panel.start();

        token = panel.getUrl().orElseThrow().replaceAll(".*token=", "");
    }

    /** Wires a real {@link SelfUpdater} with a fake release already staged, via reflection. */
    private SelfUpdater withStagedUpdater(String version) throws ReflectiveOperationException
    {
        SelfUpdater updater = new SelfUpdater(bot, new UpdateChecker());

        Field staged = SelfUpdater.class.getDeclaredField("staged");
        staged.setAccessible(true);
        staged.set(updater, Paths.get("bot-" + version + ".jar"));

        Field stagedVersion = SelfUpdater.class.getDeclaredField("stagedVersion");
        stagedVersion.setAccessible(true);
        stagedVersion.set(updater, version);

        when(bot.getUpdater()).thenReturn(updater);
        return updater;
    }

    /** A real {@link SelfUpdater} with nothing staged — the ordinary pre-update state. */
    private void withFreshUpdater()
    {
        when(bot.getUpdater()).thenReturn(new SelfUpdater(bot, new UpdateChecker()));
    }

    @Test
    @DisplayName("a token in the query string is refused — the header is required, same as every other write")
    void queryStringTokenIsRefused() throws Exception
    {
        startPanel("127.0.0.1", false);

        Response response = post("/api/update-install?token=" + token, "{}", null);

        assertEquals(401, response.status());
    }

    @Test
    @DisplayName("no token at all is refused")
    void noTokenIsRefused() throws Exception
    {
        startPanel("127.0.0.1", false);

        Response response = post("/api/update-install", "{}", null);

        assertEquals(401, response.status());
    }

    @Test
    @DisplayName("GET is refused; only POST is served")
    void getIsRefused() throws Exception
    {
        startPanel("127.0.0.1", false);

        Response response = get("/api/update-install?token=" + token);

        assertEquals(405, response.status());
    }

    @Test
    @DisplayName("refused when bound to every interface and config editing is off")
    void refusedWhenNonLoopbackAndConfigEditOff() throws Exception
    {
        startPanel("0.0.0.0", false);

        Response response = post("/api/update-install", "{}", token);

        assertEquals(403, response.status());
        assertTrue(response.body().contains("web.allowConfigEdit"), response.body());
    }

    @Test
    @DisplayName("the loopback gate itself: only 127.0.0.1, ::1, localhost and blank count as loopback")
    void loopbackGateRecognisesOnlyLocalAddresses() throws ReflectiveOperationException
    {
        // Exercised directly rather than by binding a real socket to each address: a LAN IP
        // such as 192.168.1.50 is not guaranteed to be assignable on the machine running this
        // suite, and the gate itself is a pure function of the configured bind address — it
        // does not need a live server to prove what it decides.
        java.lang.reflect.Method isLoopback = WebPanel.class.getDeclaredMethod("isLoopback", String.class);
        isLoopback.setAccessible(true);

        assertTrue((boolean) isLoopback.invoke(null, "127.0.0.1"));
        assertTrue((boolean) isLoopback.invoke(null, "::1"));
        assertTrue((boolean) isLoopback.invoke(null, "localhost"));
        assertTrue((boolean) isLoopback.invoke(null, (Object) null));
        assertTrue((boolean) isLoopback.invoke(null, ""));

        assertEquals(false, isLoopback.invoke(null, "0.0.0.0"));
        assertEquals(false, isLoopback.invoke(null, "::"));
        assertEquals(false, isLoopback.invoke(null, "192.168.1.50"));
        assertEquals(false, isLoopback.invoke(null, "10.0.0.5"));
    }

    @Test
    @DisplayName("allowed when bound to loopback, even with config editing off")
    void allowedWhenLoopback() throws Exception
    {
        startPanel("127.0.0.1", false);
        withFreshUpdater();

        Response response = post("/api/update-install", "{}", token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"status\":\"notStaged\""), response.body());
    }

    @Test
    @DisplayName("allowed when bound to every interface, as long as config editing is on")
    void allowedWhenNonLoopbackButConfigEditOn() throws Exception
    {
        startPanel("0.0.0.0", true);
        withFreshUpdater();

        Response response = post("/api/update-install", "{}", token);

        assertEquals(200, response.status());
    }

    @Test
    @DisplayName("nothing staged: reports notStaged and never touches the thread pool")
    void reportsNotStaged() throws Exception
    {
        startPanel("127.0.0.1", false);
        withFreshUpdater();

        Response response = post("/api/update-install", "{}", token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"status\":\"notStaged\""), response.body());
        verify(threadpool, never()).schedule(
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(TimeUnit.class));
    }

    @Test
    @DisplayName("staged and idle, unconfirmed: reports installing with the version, but schedules nothing")
    void reportsReadyWithoutSchedulingWhenNotConfirmed() throws Exception
    {
        startPanel("127.0.0.1", false);
        withStagedUpdater("9.9.9");

        Response response = post("/api/update-install", "{}", token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"status\":\"installing\""), response.body());
        assertTrue(response.body().contains("\"version\":\"9.9.9\""), response.body());

        // The whole point of splitting decide from confirm: a dry run must never schedule the
        // actual install, no matter what it found.
        verify(threadpool, never()).schedule(
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(TimeUnit.class));
    }

    @Test
    @DisplayName("staged and idle, confirmed: schedules the install on the shared thread pool, not inline")
    void confirmedInstallIsScheduled() throws Exception
    {
        startPanel("127.0.0.1", false);
        withStagedUpdater("9.9.9");

        Response response = post("/api/update-install", "{\"confirm\":\"true\"}", token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"status\":\"installing\""), response.body());

        // Scheduled on the pool, which in this test is a mock that never actually runs the
        // task — proving the request is wired to trigger an install without ever letting one
        // happen. bot.shutdown() (which a real install would eventually reach) is never called.
        // Mockito's timeout() rather than a plain verify(): the scheduling call happens on the
        // server's own request thread, a moment after — not strictly before — the response this
        // test already read arrives back on the client side.
        verify(threadpool, org.mockito.Mockito.timeout(1000)).schedule(
                org.mockito.ArgumentMatchers.any(Runnable.class), org.mockito.ArgumentMatchers.eq(500L),
                org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS));
        verify(bot, never()).shutdown();
    }

    @Test
    @DisplayName("staged but something is playing, unconfirmed and unforced: reports blocked with who is playing")
    void reportsBlockedWhenPlaying() throws Exception
    {
        startPanel("127.0.0.1", false);
        SelfUpdater updater = withStagedUpdater("9.9.9");

        net.dv8tion.jda.api.JDA jda = mock(net.dv8tion.jda.api.JDA.class);
        net.dv8tion.jda.api.entities.Guild guild = mock(net.dv8tion.jda.api.entities.Guild.class);
        net.dv8tion.jda.api.managers.AudioManager audioManager = mock(net.dv8tion.jda.api.managers.AudioManager.class);
        com.jagrosh.jmusicbot.audio.AudioHandler audioHandler = mock(com.jagrosh.jmusicbot.audio.AudioHandler.class);
        when(guild.getName()).thenReturn("Listener Lounge");
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(audioManager.getSendingHandler()).thenReturn(audioHandler);
        when(audioHandler.isMusicPlaying(jda)).thenReturn(true);
        when(jda.getGuilds()).thenReturn(java.util.List.of(guild));
        when(bot.getJDA()).thenReturn(jda);

        Response response = post("/api/update-install", "{}", token);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"status\":\"blocked\""), response.body());
        assertTrue(response.body().contains("Listener Lounge"), response.body());
        verify(threadpool, never()).schedule(
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(TimeUnit.class));
        verify(bot, never()).shutdown();
        assertTrue(updater.getStagedVersion().isPresent(), "still staged — nothing consumed it");
    }

    @Test
    @DisplayName("not connected to Discord yet: reports notReady rather than throwing")
    void notReadyWhenUpdaterAbsent() throws Exception
    {
        startPanel("127.0.0.1", false);
        // bot.getUpdater() left unstubbed — null, the real state before Bot.setJDA() runs.

        Response response = post("/api/update-install", "{}", token);

        assertEquals(409, response.status());
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
