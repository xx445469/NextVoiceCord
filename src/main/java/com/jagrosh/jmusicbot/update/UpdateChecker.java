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
 * <h2>Repository</h2>
 * Always this project — {@link #REPOSITORY} — never a setting. A self-updater that could be
 * pointed at an arbitrary repository is a way to get arbitrary code installed on someone's
 * machine, so there is deliberately no config key for it any more. The repository used to be
 * private, which is the only reason a token ever existed here; it is public now, so there is
 * nothing left to authenticate.
 *
 * <h2>Pre-releases</h2>
 * Every release this project has shipped so far is a pre-release (e.g. {@code 0.9.0-beta.1}),
 * and GitHub's {@code /releases/latest} endpoint — by design — never returns one; it answers
 * 404 until the first non-prerelease is published. A build that is itself a pre-release almost
 * certainly wants to hear about newer pre-releases (that is the only kind of update a beta
 * tester will ever see), while a build running an already-stable version most likely does not
 * want to be offered a beta. So the endpoint is chosen from the running version: a pre-release
 * build asks {@code /releases?per_page=1} (the single newest release of any kind, exactly what
 * the repository's own "Releases" page shows first); a stable build asks {@code
 * /releases/latest} (GitHub's own "latest", which by definition excludes pre-releases). Either
 * way, {@link #isNewer} is what actually decides whether the result is worth installing — this
 * only decides which release GitHub is asked to name.
 *
 * @author adan (xx445469)
 */
public final class UpdateChecker
{
    private static final Logger LOG = LoggerFactory.getLogger(UpdateChecker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String API_ROOT = "https://api.github.com/repos/";

    /**
     * The one repository this build ever checks. Not configurable — see the class javadoc for
     * why letting that be a setting would be a code-execution hazard rather than a convenience.
     */
    public static final String REPOSITORY = "xx445469/NextVoiceCord";

    private final String apiRoot;
    private final HttpClient http;

    /** Checks {@link #REPOSITORY} against the real GitHub API. */
    public UpdateChecker()
    {
        this(API_ROOT);
    }

    /**
     * @param apiRoot GitHub's API root by default; overridable so tests can point this at a
     *                local mock server instead of the real network
     */
    public UpdateChecker(String apiRoot)
    {
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
         * "no network", "rate limited" and "nothing published yet" look alike to the person
         * who pressed the button.
         */
        record Failed(String detail) implements CheckOutcome { }
    }

    /**
     * Fetches the newest release {@code currentVersion} should be offered — see the class
     * javadoc for how a pre-release running version changes which GitHub endpoint that is.
     *
     * @param currentVersion the running version, as from {@code OtherUtil.getCurrentVersion()}
     * @return the release, or empty if there is none or the lookup failed
     */
    public Optional<Release> fetchLatest(String currentVersion)
    {
        try
        {
            HttpResponse<String> response = requestReleases(currentVersion);

            if (response.statusCode() == 404)
            {
                LOG.warn("No releases found for {}. The repository has never published a release "
                         + "matching this request.", REPOSITORY);
                return Optional.empty();
            }
            if (response.statusCode() == 401 || response.statusCode() == 403)
            {
                LOG.warn("GitHub rejected the update check for {} (HTTP {}) — likely rate-limited.",
                         REPOSITORY, response.statusCode());
                return Optional.empty();
            }
            if (response.statusCode() != 200)
            {
                LOG.warn("Update check for {} failed with HTTP {}", REPOSITORY, response.statusCode());
                return Optional.empty();
            }

            Optional<JsonNode> node = newestReleaseNode(response.body());
            return node.isEmpty() ? Optional.empty() : parseRelease(node.get());
        }
        catch (IOException | RuntimeException ex)
        {
            LOG.warn("Update check for {} failed: {}", REPOSITORY, ex.toString());
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
            HttpResponse<String> response = requestReleases(currentVersion);

            if (response.statusCode() == 404)
            {
                return new CheckOutcome.Failed(
                        "HTTP 404 from GitHub — " + REPOSITORY + " has never published a release.");
            }
            if (response.statusCode() == 401 || response.statusCode() == 403)
            {
                return new CheckOutcome.Failed("GitHub rejected the request (HTTP " + response.statusCode()
                        + "). The request was likely rate-limited — try again later.");
            }
            if (response.statusCode() != 200)
            {
                return new CheckOutcome.Failed("GitHub returned HTTP " + response.statusCode());
            }

            Optional<JsonNode> node = newestReleaseNode(response.body());
            if (node.isEmpty())
            {
                return new CheckOutcome.Failed(REPOSITORY + " has no releases published yet.");
            }

            Optional<Release> release = parseRelease(node.get());
            if (release.isEmpty())
            {
                return new CheckOutcome.Failed(
                        "The latest release of " + REPOSITORY + " has no downloadable build attached.");
            }

            String latest = release.get().version();
            if (!isNewer(currentVersion, latest))
            {
                return new CheckOutcome.UpToDate(currentVersion);
            }

            // html_url is the page a person can read; the asset's own "url" is the API
            // endpoint fetchLatest() downloads from, which is not something to open in a
            // browser.
            String releasesUrl = node.get().path("html_url").asText(releasesPageUrl());
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

    /**
     * Whether {@code currentVersion} is itself a pre-release (carries a {@code -suffix}, e.g.
     * {@code 0.9.0-beta.1}) — see the class javadoc for what that changes about the request.
     */
    static boolean isPreReleaseVersion(String currentVersion)
    {
        return currentVersion != null && !currentVersion.isBlank()
                && splitVersion(currentVersion)[1].length() > 0;
    }

    /** The path this request asks GitHub for, given the running version. */
    private String releasesPath(String currentVersion)
    {
        // A pre-release build asks for the single newest release of any kind; a stable build
        // asks GitHub's own "latest", which excludes pre-releases by definition.
        return isPreReleaseVersion(currentVersion) ? "releases?per_page=1" : "releases/latest";
    }

    private HttpResponse<String> requestReleases(String currentVersion) throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiRoot + REPOSITORY + "/" + releasesPath(currentVersion)))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(TIMEOUT)
                .GET()
                .build();

        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String releasesPageUrl()
    {
        return "https://github.com/" + REPOSITORY + "/releases/latest";
    }

    /**
     * {@code /releases/latest} answers with a single release object; {@code /releases} answers
     * with an array, newest first — this normalises both into "the one release under
     * discussion", or empty if the array came back with nothing in it (a repository with no
     * releases at all answers {@code /releases?per_page=1} with {@code 200 []}, not a 404).
     */
    private Optional<JsonNode> newestReleaseNode(String body) throws IOException
    {
        JsonNode root = MAPPER.readTree(body);
        if (!root.isArray())
        {
            return Optional.of(root);
        }
        return root.isEmpty() ? Optional.empty() : Optional.of(root.get(0));
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
                // header used by download() below, unlike the plain redirect URL.
                return Optional.of(new Release(
                        stripLeadingV(tag),
                        name,
                        asset.path("url").asText(),
                        asset.path("size").asLong(0)));
            }
        }

        LOG.warn("Release {} of {} has no .jar asset attached; nothing to download.", tag, REPOSITORY);
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
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(release.downloadUrl()))
                    // Required for the API asset endpoint; without it GitHub returns JSON
                    // metadata rather than the file itself.
                    .header("Accept", "application/octet-stream")
                    .timeout(Duration.ofMinutes(10))
                    .GET()
                    .build();

            LOG.info("Downloading {} ({} bytes)...", release.assetName(), release.sizeBytes());
            HttpResponse<InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());

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
