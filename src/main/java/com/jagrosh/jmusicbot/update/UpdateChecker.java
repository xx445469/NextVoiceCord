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
package com.jagrosh.jmusicbot.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds and downloads newer releases of the bot.
 *
 * <p>Split from the code that applies an update so the two can be reasoned about separately:
 * asking GitHub what exists is safe and repeatable, replacing a running binary is neither.
 *
 * <h2>Private repositories</h2>
 * The GitHub releases API returns 404 — not 403 — for a private repository accessed without
 * credentials, which is indistinguishable from "this repository does not exist". That makes
 * the most likely misconfiguration also the most confusing one, so a 404 is reported with
 * both possibilities named rather than as a bare failure.
 *
 * @author adan (xx445469)
 */
public final class UpdateChecker
{
    private static final Logger LOG = LoggerFactory.getLogger(UpdateChecker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String API_ROOT = "https://api.github.com/repos/";

    private final String repository;
    private final String token;
    private final String apiRoot;
    private final HttpClient http;

    /**
     * @param repository {@code owner/name}, e.g. {@code xx445469/NextVoiceCord}
     * @param token      GitHub token, or {@code null}; required only for private repositories
     */
    public UpdateChecker(String repository, String token)
    {
        this(repository, token, API_ROOT);
    }

    /**
     * @param apiRoot GitHub's API root by default; overridable so tests can point this at a
     *                local mock server instead of the real network
     */
    public UpdateChecker(String repository, String token, String apiRoot)
    {
        this.repository = repository;
        this.token = token == null || token.isBlank() ? null : token;
        this.apiRoot = apiRoot;
        this.http = HttpClient.newBuilder()
                              .connectTimeout(TIMEOUT)
                              .followRedirects(HttpClient.Redirect.NORMAL)
                              .build();
    }

    /** A release and the asset to download from it. */
    public record Release(String version, String assetName, String downloadUrl, long sizeBytes) { }

    /**
     * Outcome of an on-demand check, distinguishing "nothing newer" from "the check itself
     * failed". {@link #fetchLatest} collapses both into an empty {@code Optional} — fine for
     * a background timer that just tries again later — but a button someone just pressed has
     * to say which one happened, or pressing it was indistinguishable from doing nothing.
     */
    public sealed interface CheckOutcome
    {
        /** No release newer than {@code currentVersion} was found. */
        record UpToDate(String currentVersion) implements CheckOutcome { }

        /** A newer release exists, with a page a human can open to read about it. */
        record UpdateAvailable(String currentVersion, String latestVersion, String releasesUrl)
                implements CheckOutcome { }

        /**
         * The check could not be completed. {@code detail} is a technical, English string
         * for a log or a "why" line — never a message shown as the whole story, since none of
         * "no network", "rate limited" and "private repo, no token" look alike to the person
         * who pressed the button.
         */
        record Failed(String detail) implements CheckOutcome { }
    }

    /**
     * Fetches the latest non-prerelease.
     *
     * @return the release, or empty if there is none or the lookup failed
     */
    public Optional<Release> fetchLatest()
    {
        try
        {
            HttpResponse<String> response = requestLatestRelease();

            if (response.statusCode() == 404)
            {
                LOG.warn("No releases found for {}. Either the repository has never published a "
                         + "release, or it is private and updates.githubToken is not set — the API "
                         + "returns 404 for both.", repository);
                return Optional.empty();
            }
            if (response.statusCode() == 401 || response.statusCode() == 403)
            {
                LOG.warn("GitHub rejected the update check for {} (HTTP {}). If updates.githubToken "
                         + "is set, it may be expired or lack 'Contents: read'.",
                         repository, response.statusCode());
                return Optional.empty();
            }
            if (response.statusCode() != 200)
            {
                LOG.warn("Update check for {} failed with HTTP {}", repository, response.statusCode());
                return Optional.empty();
            }

            return parseRelease(MAPPER.readTree(response.body()));
        }
        catch (IOException | RuntimeException ex)
        {
            LOG.warn("Update check for {} failed: {}", repository, ex.toString());
            return Optional.empty();
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Checks now, for a caller that needs to tell "up to date" apart from "the check failed"
     * rather than silently trying again later.
     *
     * <p>Only reports an update when the release also carries a {@code .jar} asset, matching
     * {@link #fetchLatest}: a tag with nothing attached is not one this checker could ever
     * install, so calling it "available" here would promise a self-update that cannot happen.
     *
     * <p>Never downloads or installs anything — that stays behind {@code updates.autoUpdate},
     * decided elsewhere.
     *
     * @param currentVersion the running version, as from {@code OtherUtil.getCurrentVersion()}
     */
    public CheckOutcome checkForUpdate(String currentVersion)
    {
        try
        {
            HttpResponse<String> response = requestLatestRelease();

            if (response.statusCode() == 404)
            {
                return new CheckOutcome.Failed("HTTP 404 from GitHub — the repository has never "
                        + "published a release, or it is private and updates.githubToken is not set.");
            }
            if (response.statusCode() == 401 || response.statusCode() == 403)
            {
                return new CheckOutcome.Failed("GitHub rejected the request (HTTP " + response.statusCode()
                        + "). The token may be missing or expired, or the request was rate-limited.");
            }
            if (response.statusCode() != 200)
            {
                return new CheckOutcome.Failed("GitHub returned HTTP " + response.statusCode());
            }

            JsonNode node = MAPPER.readTree(response.body());
            Optional<Release> release = parseRelease(node);
            if (release.isEmpty())
            {
                return new CheckOutcome.Failed(
                        "The latest release of " + repository + " has no downloadable build attached.");
            }

            String latest = release.get().version();
            if (!isNewer(currentVersion, latest))
            {
                return new CheckOutcome.UpToDate(currentVersion);
            }

            // html_url is the page a person can read; the asset's own "url" is the API
            // endpoint fetchLatest() downloads from, which is not something to open in a
            // browser — for a private repository it demands the same bearer token this
            // process carries, one a browser tab has no way to send.
            String releasesUrl = node.path("html_url").asText(releasesPageUrl());
            return new CheckOutcome.UpdateAvailable(currentVersion, latest, releasesUrl);
        }
        catch (IOException | RuntimeException ex)
        {
            return new CheckOutcome.Failed(ex.toString());
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            return new CheckOutcome.Failed("Interrupted");
        }
    }

    private HttpResponse<String> requestLatestRelease() throws IOException, InterruptedException
    {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(apiRoot + repository + "/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(TIMEOUT)
                .GET();

        if (token != null)
        {
            request.header("Authorization", "Bearer " + token);
        }

        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String releasesPageUrl()
    {
        return "https://github.com/" + repository + "/releases/latest";
    }

    private Optional<Release> parseRelease(JsonNode node)
    {
        String tag = node.path("tag_name").asText(null);
        if (tag == null)
        {
            return Optional.empty();
        }

        for (JsonNode asset : node.path("assets"))
        {
            String name = asset.path("name").asText("");
            if (name.endsWith(".jar"))
            {
                // url, not browser_download_url: the API endpoint honours the Authorization
                // header, which is what makes assets on a private repository reachable.
                return Optional.of(new Release(
                        stripLeadingV(tag),
                        name,
                        asset.path("url").asText(),
                        asset.path("size").asLong(0)));
            }
        }

        LOG.warn("Release {} of {} has no .jar asset attached; nothing to download.", tag, repository);
        return Optional.empty();
    }

    /**
     * Downloads {@code release} to a temporary file beside {@code destinationDirectory}.
     *
     * <p>Written to a temporary name and only then moved into place, so an interrupted
     * download can never leave a half-written jar where a working one is expected.
     *
     * @return the downloaded file, or empty if the download or verification failed
     */
    public Optional<Path> download(Release release, Path destinationDirectory)
    {
        Path temporary = destinationDirectory.resolve(release.assetName() + ".partial");

        try
        {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(release.downloadUrl()))
                    // Required for the API asset endpoint; without it GitHub returns JSON
                    // metadata rather than the file itself.
                    .header("Accept", "application/octet-stream")
                    .timeout(Duration.ofMinutes(10))
                    .GET();

            if (token != null)
            {
                request.header("Authorization", "Bearer " + token);
            }

            LOG.info("Downloading {} ({} bytes)...", release.assetName(), release.sizeBytes());
            HttpResponse<InputStream> response =
                    http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200)
            {
                LOG.warn("Download failed with HTTP {}", response.statusCode());
                return Optional.empty();
            }

            try (InputStream in = response.body())
            {
                Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
            }

            long actual = Files.size(temporary);
            if (release.sizeBytes() > 0 && actual != release.sizeBytes())
            {
                // The release metadata states the size, so a mismatch means a truncated or
                // corrupted transfer. Installing it would replace a working bot with one
                // that cannot start.
                LOG.warn("Downloaded {} bytes but the release says {}. Discarding.",
                         actual, release.sizeBytes());
                Files.deleteIfExists(temporary);
                return Optional.empty();
            }

            Path finished = destinationDirectory.resolve(release.assetName());
            Files.move(temporary, finished, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Downloaded {} to {}", release.assetName(), finished);
            return Optional.of(finished);
        }
        catch (IOException | RuntimeException ex)
        {
            LOG.warn("Download of {} failed: {}", release.assetName(), ex.toString());
            quietlyDelete(temporary);
            return Optional.empty();
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            quietlyDelete(temporary);
            return Optional.empty();
        }
    }

    private static void quietlyDelete(Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
            // Nothing useful to do; a stray .partial is harmless.
        }
    }

    private static String stripLeadingV(String tag)
    {
        return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
    }

    /**
     * Compares version strings, pre-releases included.
     *
     * <p>Comparing as plain strings is wrong in a way that only appears later: "0.10.0"
     * sorts before "0.9.0" lexically, so updates would silently stop once the numbers grew
     * past a single digit.
     *
     * <p>Pre-releases follow semver: with equal numeric parts, a version carrying a suffix
     * ranks BELOW one without. Treating the suffix as just another segment gets this
     * backwards in both directions at once — a beta never sees the stable release that
     * supersedes it, and a stable install "updates" itself onto a beta.
     *
     * @return true if {@code candidate} is strictly newer than {@code current}
     */
    public static boolean isNewer(String current, String candidate)
    {
        if (current == null || candidate == null || "UNKNOWN".equals(current))
        {
            return false;
        }

        String[] currentParts = splitVersion(current);
        String[] candidateParts = splitVersion(candidate);

        int core = compareNumeric(currentParts[0], candidateParts[0]);
        if (core != 0)
        {
            return core < 0;
        }

        String currentPre = currentParts[1];
        String candidatePre = candidateParts[1];

        // Equal cores: absence of a suffix wins.
        if (currentPre.isEmpty() || candidatePre.isEmpty())
        {
            return !candidatePre.isEmpty() ? false : !currentPre.isEmpty();
        }

        return compareNumeric(currentPre, candidatePre) < 0;
    }

    /** Splits "1.2.3-beta.4+build" into {"1.2.3", "beta.4"}, discarding build metadata. */
    private static String[] splitVersion(String version)
    {
        String withoutBuild = version.split("\\+", 2)[0];
        int dash = withoutBuild.indexOf('-');
        return dash < 0
                ? new String[] { withoutBuild, "" }
                : new String[] { withoutBuild.substring(0, dash), withoutBuild.substring(dash + 1) };
    }

    /**
     * Compares dot-separated segments.
     *
     * <p>Numeric segments compare as numbers; a numeric segment ranks below an alphanumeric
     * one, as semver specifies, so "1" sorts before "alpha".
     */
    private static int compareNumeric(String left, String right)
    {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");

        for (int i = 0; i < Math.max(a.length, b.length); i++)
        {
            String x = i < a.length ? a[i] : "0";
            String y = i < b.length ? b[i] : "0";

            Integer xn = asNumber(x);
            Integer yn = asNumber(y);

            if (xn != null && yn != null)
            {
                if (!xn.equals(yn))
                {
                    return Integer.compare(xn, yn);
                }
            }
            else if (xn != null)
            {
                return -1;
            }
            else if (yn != null)
            {
                return 1;
            }
            else
            {
                int cmp = x.compareTo(y);
                if (cmp != 0)
                {
                    return cmp;
                }
            }
        }
        return 0;
    }

    private static Integer asNumber(String text)
    {
        try
        {
            return Integer.valueOf(text);
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }
}
