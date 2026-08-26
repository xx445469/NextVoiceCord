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

import com.jagrosh.jmusicbot.update.UpdateChecker;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UpdateChecker#checkForUpdate}, the classification an on-demand button relies on.
 *
 * <p>{@link UpdateChecker#fetchLatest} collapses "nothing newer" and "the check failed" into
 * the same empty {@code Optional} — fine for a background timer that just tries again later.
 * A button someone just clicked cannot get away with that: these tests exist because "up to
 * date", "update available" and "failed" have to come back as three different things, not one
 * flavour of silence.
 */
@DisplayName("UpdateChecker.checkForUpdate")
class UpdateCheckerCheckOutcomeTest
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

    private UpdateChecker checkerFor(String token)
    {
        String apiRoot = "http://localhost:" + server.getPort() + "/repos/";
        return new UpdateChecker("owner/repo", token, apiRoot);
    }

    private static String releaseJson(String tag, boolean withJarAsset)
    {
        String assets = withJarAsset
                ? """
                  "assets": [
                    { "name": "bot.jar", "url": "http://example.invalid/assets/1", "size": 42 }
                  ],
                  """
                : "\"assets\": [],\n";
        return """
                {
                  "tag_name": "%s",
                  "html_url": "https://github.com/owner/repo/releases/tag/%s",
                  %s
                  "prerelease": false
                }
                """.formatted(tag, tag, assets);
    }

    @Nested
    @DisplayName("success")
    class Success
    {
        @Test
        @DisplayName("reports UpToDate when the latest release is not newer")
        void upToDate()
        {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(releaseJson("v1.0.0", true))
                    .setHeader("Content-Type", "application/json"));

            UpdateChecker.CheckOutcome outcome = checkerFor(null).checkForUpdate("1.0.0");

            var upToDate = assertInstanceOf(UpdateChecker.CheckOutcome.UpToDate.class, outcome);
            assertEquals("1.0.0", upToDate.currentVersion());
        }

        @Test
        @DisplayName("reports UpdateAvailable with the new version and a browsable URL")
        void updateAvailable()
        {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(releaseJson("v2.0.0", true))
                    .setHeader("Content-Type", "application/json"));

            UpdateChecker.CheckOutcome outcome = checkerFor(null).checkForUpdate("1.0.0");

            var available = assertInstanceOf(UpdateChecker.CheckOutcome.UpdateAvailable.class, outcome);
            assertEquals("1.0.0", available.currentVersion());
            assertEquals("2.0.0", available.latestVersion());
            assertEquals("https://github.com/owner/repo/releases/tag/v2.0.0", available.releasesUrl());
        }

        @Test
        @DisplayName("does not report an update when the release has no .jar asset")
        void noAssetIsNotAnAvailableUpdate()
        {
            // A tag with nothing attached is not something this checker could ever install,
            // so calling it "available" would promise a self-update that cannot happen.
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(releaseJson("v2.0.0", false))
                    .setHeader("Content-Type", "application/json"));

            UpdateChecker.CheckOutcome outcome = checkerFor(null).checkForUpdate("1.0.0");

            assertInstanceOf(UpdateChecker.CheckOutcome.Failed.class, outcome);
        }
    }

    @Nested
    @DisplayName("failure")
    class Failure
    {
        @Test
        @DisplayName("404 (no release, or private without a token) is reported as Failed")
        void notFound()
        {
            server.enqueue(new MockResponse().setResponseCode(404));

            UpdateChecker.CheckOutcome outcome = checkerFor(null).checkForUpdate("1.0.0");

            assertInstanceOf(UpdateChecker.CheckOutcome.Failed.class, outcome);
        }

        @Test
        @DisplayName("401/403 (bad token, or rate limited) is reported as Failed")
        void unauthorizedOrRateLimited()
        {
            server.enqueue(new MockResponse().setResponseCode(403));

            UpdateChecker.CheckOutcome outcome = checkerFor("stale-token").checkForUpdate("1.0.0");

            assertInstanceOf(UpdateChecker.CheckOutcome.Failed.class, outcome);
        }

        @Test
        @DisplayName("an unexpected HTTP status is reported as Failed")
        void otherHttpError()
        {
            server.enqueue(new MockResponse().setResponseCode(500));

            UpdateChecker.CheckOutcome outcome = checkerFor(null).checkForUpdate("1.0.0");

            assertInstanceOf(UpdateChecker.CheckOutcome.Failed.class, outcome);
        }

        @Test
        @DisplayName("no network reachable is reported as Failed, not thrown")
        void noNetwork() throws IOException
        {
            // Shut down before the call: nothing is listening on this port any more, which is
            // as close as a unit test gets to "the network is unreachable".
            server.shutdown();

            UpdateChecker.CheckOutcome outcome = checkerFor(null).checkForUpdate("1.0.0");

            assertInstanceOf(UpdateChecker.CheckOutcome.Failed.class, outcome);
        }

        @Test
        @DisplayName("Failed carries a detail string, so the reason is not silently dropped")
        void failedCarriesDetail()
        {
            server.enqueue(new MockResponse().setResponseCode(404));

            UpdateChecker.CheckOutcome outcome = checkerFor(null).checkForUpdate("1.0.0");

            var failed = assertInstanceOf(UpdateChecker.CheckOutcome.Failed.class, outcome);
            assertTrue(failed.detail() != null && !failed.detail().isBlank());
        }
    }
}
