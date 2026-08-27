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
package com.jagrosh.jmusicbot.unit.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Constructor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.update.SelfUpdater;
import com.jagrosh.jmusicbot.update.UpdateChecker;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SelfUpdater#checkAndStage()} is the fix for the exact gap this whole rework exists to
 * close: a manual "Check for updates" press used to only ever ask GitHub what exists, leaving
 * "Install and restart" permanently disabled until the hourly background timer happened to catch
 * up on its own — telling someone about an update with no way to act on it. These tests exercise
 * the real check-then-download path end to end against a local {@link MockWebServer}, never the
 * real GitHub API and never anything resembling a real ~68 MB release (just a handful of bytes),
 * so a regression back to check-only shows up as a broken test rather than a person staring at a
 * disabled button.
 *
 * @author adan (xx445469)
 */
class SelfUpdaterCheckAndStageTest
{
    private MockWebServer github;

    @BeforeEach
    void setUp() throws Exception
    {
        github = new MockWebServer();
        github.start();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        github.shutdown();
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

    /**
     * Wires a real {@link SelfUpdater} at a real {@link UpdateChecker} pointed at the local
     * {@link #github} server — the same {@code apiRoot} seam {@link UpdateChecker}'s own tests
     * use — with {@code currentVersion} fixed via reflection into {@link SelfUpdater}'s
     * package-private test-only constructor, the same seam {@code WebPanel}'s own test
     * constructor offers for identical reasons: outside a packaged jar,
     * {@code OtherUtil.getCurrentVersion()} always answers {@code "UNKNOWN"}, which {@link
     * UpdateChecker#isNewer} treats as never newer than anything, making "update available"
     * unreachable from a test otherwise.
     */
    private SelfUpdater newUpdater(String currentVersion) throws ReflectiveOperationException
    {
        String apiRoot = "http://localhost:" + github.getPort() + "/repos/";
        Bot bot = mock(Bot.class);

        Constructor<SelfUpdater> ctor = SelfUpdater.class.getDeclaredConstructor(
                Bot.class, UpdateChecker.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(bot, new UpdateChecker(apiRoot), currentVersion);
    }

    @Test
    @DisplayName("nothing newer published: reports UpToDate, and never attempts a download")
    void nothingNewerReportsUpToDate() throws Exception
    {
        github.enqueue(new MockResponse().setResponseCode(200)
                .setBody(releaseJson("v1.0.0", assetUrl(), 999))
                .setHeader("Content-Type", "application/json"));

        SelfUpdater updater = newUpdater("1.0.0");

        SelfUpdater.CheckAndStageOutcome outcome = updater.checkAndStage();

        assertInstanceOf(SelfUpdater.CheckAndStageOutcome.UpToDate.class, outcome);
        assertTrue(updater.getStagedVersion().isEmpty());
        assertEquals(1, github.getRequestCount(), "up to date must never trigger a download request");
    }

    @Test
    @DisplayName("a newer version is found and downloaded: checkAndStage() ends with something staged, not just reported")
    void findingANewerVersionStagesIt() throws Exception
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

        SelfUpdater updater = newUpdater("1.0.0");
        assertTrue(updater.getStagedVersion().isEmpty(), "nothing staged before checking");

        SelfUpdater.CheckAndStageOutcome outcome = updater.checkAndStage();

        assertInstanceOf(SelfUpdater.CheckAndStageOutcome.Staged.class, outcome);
        assertEquals("2.0.0", ((SelfUpdater.CheckAndStageOutcome.Staged) outcome).version());
        // The actual point of this test: checkAndStage() alone — no separate download call, no
        // hourly timer — must be enough for something to be staged afterwards.
        assertTrue(updater.getStagedVersion().isPresent(), "checkAndStage() must leave something staged");
        assertEquals("2.0.0", updater.getStagedVersion().get());
    }

    @Test
    @DisplayName("a newer version is found but the transfer fails: reports DownloadFailed, and nothing is staged")
    void downloadFailureReportsDownloadFailedWithoutStaging() throws Exception
    {
        github.enqueue(new MockResponse().setResponseCode(200)
                .setBody(releaseJson("v2.0.0", assetUrl(), 999))
                .setHeader("Content-Type", "application/json"));
        github.enqueue(new MockResponse().setResponseCode(200)
                .setBody(releaseJson("v2.0.0", assetUrl(), 999))
                .setHeader("Content-Type", "application/json"));
        github.enqueue(new MockResponse().setResponseCode(500));

        SelfUpdater updater = newUpdater("1.0.0");

        SelfUpdater.CheckAndStageOutcome outcome = updater.checkAndStage();

        assertInstanceOf(SelfUpdater.CheckAndStageOutcome.DownloadFailed.class, outcome);
        assertEquals("2.0.0", ((SelfUpdater.CheckAndStageOutcome.DownloadFailed) outcome).version());
        assertTrue(updater.getStagedVersion().isEmpty(), "a failed download must never leave something staged");
    }

    @Test
    @DisplayName("GitHub cannot be reached: reports CheckFailed with a detail string")
    void checkFailureReportsCheckFailed() throws Exception
    {
        github.enqueue(new MockResponse().setResponseCode(500));

        SelfUpdater updater = newUpdater("1.0.0");

        SelfUpdater.CheckAndStageOutcome outcome = updater.checkAndStage();

        assertInstanceOf(SelfUpdater.CheckAndStageOutcome.CheckFailed.class, outcome);
        assertTrue(((SelfUpdater.CheckAndStageOutcome.CheckFailed) outcome).detail().contains("500"));
        assertEquals(1, github.getRequestCount(), "the check itself failing must never trigger a download request");
    }

    @Test
    @DisplayName("a press colliding with one already in flight waits for it and reports the same outcome, "
            + "rather than a busy failure")
    void collidingCheckWaitsForTheInFlightOneInsteadOfFailing() throws Exception
    {
        String assetContent = "not a real jar, just a handful of test bytes";
        // A short delay on the first response is enough to guarantee the second call below
        // starts while the first one is still in flight, without slowing the test down
        // meaningfully.
        github.enqueue(new MockResponse().setResponseCode(200)
                .setBody(releaseJson("v2.0.0", assetUrl(), assetContent.length()))
                .setHeader("Content-Type", "application/json")
                .setBodyDelay(300, TimeUnit.MILLISECONDS));
        github.enqueue(new MockResponse().setResponseCode(200)
                .setBody(releaseJson("v2.0.0", assetUrl(), assetContent.length()))
                .setHeader("Content-Type", "application/json"));
        github.enqueue(new MockResponse().setResponseCode(200)
                .setBody(assetContent)
                .setHeader("Content-Type", "application/octet-stream"));

        SelfUpdater updater = newUpdater("1.0.0");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try
        {
            CompletableFuture<SelfUpdater.CheckAndStageOutcome> first =
                    CompletableFuture.supplyAsync(updater::checkAndStage, pool);
            Thread.sleep(50); // let the first call actually start and win ownership
            CompletableFuture<SelfUpdater.CheckAndStageOutcome> second =
                    CompletableFuture.supplyAsync(updater::checkAndStage, pool);

            SelfUpdater.CheckAndStageOutcome firstOutcome = first.get(5, TimeUnit.SECONDS);
            SelfUpdater.CheckAndStageOutcome secondOutcome = second.get(5, TimeUnit.SECONDS);

            assertInstanceOf(SelfUpdater.CheckAndStageOutcome.Staged.class, firstOutcome);
            assertInstanceOf(SelfUpdater.CheckAndStageOutcome.Staged.class, secondOutcome);
            assertEquals("2.0.0", ((SelfUpdater.CheckAndStageOutcome.Staged) firstOutcome).version());
            assertEquals("2.0.0", ((SelfUpdater.CheckAndStageOutcome.Staged) secondOutcome).version());

            // The whole point: the colliding call must not run a second check-and-download.
            // Exactly three requests total (one metadata fetch for the check, one re-fetch for
            // the download URL, one asset download) proves only one run of checkAndStage() ever
            // reached the network, no matter that two callers asked for one.
            assertEquals(3, github.getRequestCount(),
                    "a colliding check must wait for the in-flight one, not run its own");
        }
        finally
        {
            pool.shutdownNow();
        }
    }
}
