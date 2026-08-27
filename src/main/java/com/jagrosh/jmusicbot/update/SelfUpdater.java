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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.utils.OtherUtil;

import net.dv8tion.jda.api.entities.Guild;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads new releases and, on explicit request, restarts into them.
 *
 * <p>Checking is always on: every hour, unconditionally, not a setting — see {@code
 * updates.alerts} in {@code reference.conf} for the one update-related thing that still is one.
 * An hourly cadence sits nowhere near GitHub's 60/hour unauthenticated rate limit, so there is
 * nothing here worth making configurable, and {@link #checkAndStage()}'s own single-flight guard
 * keeps one check from overlapping the next regardless.
 *
 * <h2>Staging versus installing</h2>
 * The two are deliberately separate. {@link #checkAndStage()} only ever downloads — that is
 * invisible to anyone using the bot, whether it runs on the hourly timer or because someone
 * pressed "Check for updates". Installing is not: a restart cuts every voice connection
 * instantly, and replacing the binary of a running process without a person in the loop is not
 * something to do to someone. Nothing here ever calls {@link #installNow()} on its own; the
 * desktop window's and the web panel's "Install and restart" buttons are the only callers, and
 * both show what would be interrupted first — see {@link #decideInstall(boolean)}.
 *
 * <h2>Checking is also staging now</h2>
 * A person pressing "Check for updates" and being told a newer version exists, with no way to
 * act on it until the hourly timer happens to catch up, is worse than not telling them at all —
 * so {@link #checkAndStage()} is the one method both the hourly timer and an explicit press call,
 * and it reports what happened rather than only logging it. Only one download ever runs at a
 * time: a press that lands while the hourly timer (or another press) is already mid-check waits
 * for that one and reports its outcome, instead of starting a redundant second download or
 * reporting a misleading "busy" failure for something that is, from the caller's side, already in
 * progress and about to succeed.
 *
 * <h2>How the swap survives failure</h2>
 * The running jar is moved aside rather than overwritten, so a jar that turns out not to
 * start leaves the previous one on disk under {@code .backup} to restore by hand. The new
 * process is spawned before this one exits, and inherits stdio so its output still reaches
 * whatever was watching.
 *
 * @author adan (xx445469)
 */
public final class SelfUpdater
{
    private static final Logger LOG = LoggerFactory.getLogger(SelfUpdater.class);

    /** Suffix for the displaced jar, kept so a failed update can be undone by renaming. */
    private static final String BACKUP_SUFFIX = ".backup";

    /** Not configurable — see the class javadoc. */
    private static final long CHECK_INTERVAL_MINUTES = 60;

    private final Bot bot;
    private final UpdateChecker checker;

    /**
     * Overrides {@link OtherUtil#getCurrentVersion()} for {@link #checkAndStage()} — {@code
     * null} means the real running version. Test-only, the same seam {@link
     * com.jagrosh.jmusicbot.web.WebPanel}'s own test constructor offers for the same reason:
     * {@code getCurrentVersion()} answers {@code "UNKNOWN"} for anything not run from the
     * packaged jar, and {@link UpdateChecker#isNewer} treats {@code "UNKNOWN"} as never newer
     * than anything, which would make the "update available" branch unreachable from a test.
     */
    private final String currentVersionOverride;

    /**
     * Guards a single in-flight {@link #checkAndStage()} run. A second caller — the hourly timer
     * landing mid-press, or a second press — becomes a waiter on this rather than starting a
     * redundant check or being told the check is "busy"; see {@link #checkAndStage()}.
     */
    private final Object checkLock = new Object();
    private volatile CompletableFuture<CheckAndStageOutcome> inFlightCheck;

    /**
     * True while the in-flight {@link #checkAndStage()} run is actually transferring a release,
     * as opposed to still asking GitHub what exists — set only by whichever call owns the run,
     * so a caller with a UI can show "downloading" once a ~68 MB transfer is genuinely underway
     * rather than leaving "checking" on screen for however long that takes.
     */
    private volatile boolean downloading = false;

    /** Set once an update is staged, so a caller knows there is something to install. */
    private volatile Path staged;
    private volatile String stagedVersion;

    public SelfUpdater(Bot bot, UpdateChecker checker)
    {
        this(bot, checker, null);
    }

    /** Test-only: see {@link #currentVersionOverride}. */
    SelfUpdater(Bot bot, UpdateChecker checker, String currentVersionOverride)
    {
        this.bot = bot;
        this.checker = checker;
        this.currentVersionOverride = currentVersionOverride;
    }

    /**
     * What one {@link #checkAndStage()} run found, so a caller that just pressed "Check for
     * updates" can say which of these happened rather than only ever seeing the button light up
     * later or not. Checking never installs anything — see the class javadoc.
     */
    public sealed interface CheckAndStageOutcome
    {
        /** No release newer than {@code currentVersion} exists. */
        record UpToDate(String currentVersion) implements CheckAndStageOutcome { }

        /** A newer release was found and downloaded; {@link #installNow()} can act on it now. */
        record Staged(String version, String releasesUrl) implements CheckAndStageOutcome { }

        /** A newer release was found, but downloading it failed. Nothing is staged. */
        record DownloadFailed(String version, String releasesUrl) implements CheckAndStageOutcome { }

        /**
         * The check itself could not be completed. {@code detail} is a technical, English
         * string for a log or a "why" line — see {@link UpdateChecker.CheckOutcome.Failed} for
         * why that is never the whole story shown to a person.
         */
        record CheckFailed(String detail) implements CheckAndStageOutcome { }
    }

    /** What deciding whether to install right now found. Deciding never installs anything. */
    public sealed interface InstallDecision
    {
        /** Nothing is staged; there is nothing {@link #installNow()} could do yet. */
        record NotStaged() implements InstallDecision { }

        /**
         * Something is staged, but music is playing somewhere and {@code force} was not set —
         * installing now would cut every one of these off without warning.
         */
        record Blocked(List<String> playingGuilds) implements InstallDecision { }

        /** Clear to install: either nothing is playing, or the caller said to proceed anyway. */
        record Ready(String version) implements InstallDecision { }
    }

    /**
     * Starts the hourly check. Always on; see the class javadoc for why there is no argument
     * here to turn it off or change its cadence.
     */
    public void start()
    {
        bot.getThreadpool().scheduleWithFixedDelay(
                this::checkAndStage,
                // Delayed rather than immediate: startup is already doing enough, and an
                // update that has waited who-knows-how-long can wait five minutes more.
                5, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);

        LOG.info("Checking for updates every hour. Installing is never automatic — "
                 + "it happens only when someone presses \"Install and restart\".");
    }

    /**
     * Looks for a newer release and downloads it, without installing — the hourly timer's own
     * task, and also what an explicit "Check for updates" press now calls directly, so pressing
     * it can actually end with something the "Install and restart" button can act on rather than
     * only a status line. See the class javadoc for why staging is never itself a problem to do
     * silently, and why a collision between two callers waits rather than fails.
     *
     * @return what this run found — or, for a caller that landed on someone else's already
     *         in-flight run, that run's own result
     */
    public CheckAndStageOutcome checkAndStage()
    {
        if (staged != null)
        {
            return new CheckAndStageOutcome.Staged(stagedVersion, null);
        }

        CompletableFuture<CheckAndStageOutcome> future;
        boolean owner;
        synchronized (checkLock)
        {
            if (inFlightCheck != null)
            {
                future = inFlightCheck;
                owner = false;
            }
            else
            {
                future = new CompletableFuture<>();
                inFlightCheck = future;
                owner = true;
            }
        }

        if (!owner)
        {
            return awaitInFlight(future);
        }

        CheckAndStageOutcome outcome = runCheckAndStage();
        synchronized (checkLock)
        {
            inFlightCheck = null;
        }
        future.complete(outcome);
        return outcome;
    }

    /** True while a download triggered by {@link #checkAndStage()} is actually in flight. */
    public boolean isDownloading()
    {
        return downloading;
    }

    /**
     * Waits for someone else's {@link #checkAndStage()} run to finish and reports its outcome —
     * the truth, rather than a "busy" failure invented for a check that is, from here, already
     * under way.
     */
    private static CheckAndStageOutcome awaitInFlight(CompletableFuture<CheckAndStageOutcome> future)
    {
        try
        {
            return future.get();
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            return new CheckAndStageOutcome.CheckFailed(
                    "Interrupted while waiting for the update check already in progress.");
        }
        catch (ExecutionException ex)
        {
            // runCheckAndStage() below never lets an exception escape it, so this branch is
            // unreachable in practice — kept only so awaiting the future has no unchecked path.
            return new CheckAndStageOutcome.CheckFailed(String.valueOf(ex.getCause()));
        }
    }

    /** The actual check-then-download, run by whichever caller won ownership above. */
    private CheckAndStageOutcome runCheckAndStage()
    {
        try
        {
            String current = currentVersion();
            UpdateChecker.CheckOutcome outcome = checker.checkForUpdate(current);

            return switch (outcome)
            {
                case UpdateChecker.CheckOutcome.UpToDate upToDate ->
                        new CheckAndStageOutcome.UpToDate(upToDate.currentVersion());
                case UpdateChecker.CheckOutcome.Failed failed ->
                        new CheckAndStageOutcome.CheckFailed(failed.detail());
                case UpdateChecker.CheckOutcome.UpdateAvailable available ->
                        downloadAndStage(current, available);
            };
        }
        catch (RuntimeException ex)
        {
            LOG.warn("Update check failed: {}", ex.toString());
            return new CheckAndStageOutcome.CheckFailed(ex.toString());
        }
    }

    /**
     * Re-fetches the release {@code available} already named — {@link UpdateChecker#checkForUpdate}
     * does not itself return the asset details {@link UpdateChecker#download} needs — and
     * downloads it.
     */
    private CheckAndStageOutcome downloadAndStage(String current, UpdateChecker.CheckOutcome.UpdateAvailable available)
    {
        Optional<UpdateChecker.Release> release = checker.fetchLatest(current);
        if (release.isEmpty())
        {
            LOG.warn("Update {} was found but its release details could not be re-fetched for download.",
                     available.latestVersion());
            return new CheckAndStageOutcome.DownloadFailed(available.latestVersion(), available.releasesUrl());
        }

        LOG.info("Update available: {} -> {}", current, release.get().version());

        downloading = true;
        try
        {
            Optional<Path> downloaded = checker.download(release.get(), currentJar().getParent());
            if (downloaded.isEmpty())
            {
                return new CheckAndStageOutcome.DownloadFailed(release.get().version(), available.releasesUrl());
            }

            staged = downloaded.get();
            stagedVersion = release.get().version();
            LOG.info("Version {} is staged and ready to install.", stagedVersion);
            return new CheckAndStageOutcome.Staged(stagedVersion, available.releasesUrl());
        }
        finally
        {
            downloading = false;
        }
    }

    /** {@link #currentVersionOverride}, or the real running version. */
    private String currentVersion()
    {
        return currentVersionOverride != null ? currentVersionOverride : OtherUtil.getCurrentVersion();
    }

    /**
     * Decides what pressing "Install and restart" should do right now, without doing it.
     *
     * <p>Split from {@link #installNow()} on purpose: both the desktop window and the web panel
     * call this first, so a person can be told plainly what installing now would interrupt
     * before anything is interrupted — never a silent refusal, and never a restart that cuts
     * off listeners with no warning at all.
     *
     * @param force proceed even though something is playing — set only after a person has
     *              already been shown exactly what that would cut off and said to continue
     */
    public InstallDecision decideInstall(boolean force)
    {
        if (staged == null)
        {
            return new InstallDecision.NotStaged();
        }

        List<String> playing = playingGuilds();
        if (!playing.isEmpty() && !force)
        {
            return new InstallDecision.Blocked(playing);
        }

        return new InstallDecision.Ready(stagedVersion);
    }

    /**
     * Installs the staged update and restarts, if {@link #decideInstall(boolean)} was not just
     * called or returned anything other than {@link InstallDecision.Ready} — the caller is
     * trusted to have checked first, since checking again here would reopen exactly the race a
     * person confirming "continue anyway" already resolved. A no-op if nothing is staged.
     *
     * <p>Does not return when it succeeds: the process that would return is the one this
     * replaces.
     */
    public void installNow()
    {
        if (staged == null)
        {
            return;
        }
        install();
    }

    /** Names of guilds currently playing, so waiting can be explained rather than silent. */
    private List<String> playingGuilds()
    {
        List<String> playing = new ArrayList<>();
        if (bot.getJDA() == null)
        {
            return playing;
        }

        for (Guild guild : bot.getJDA().getGuilds())
        {
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            if (handler != null && handler.isMusicPlaying(bot.getJDA()))
            {
                playing.add(guild.getName());
            }
        }
        return playing;
    }

    /**
     * Swaps in the staged jar and restarts.
     *
     * <p>Does not return when it succeeds.
     */
    private void install()
    {
        Path newJar = staged;
        Path running;
        try
        {
            running = currentJar();
        }
        catch (RuntimeException ex)
        {
            LOG.warn("Cannot locate the running jar, so the update cannot be installed: {}", ex.toString());
            return;
        }

        if (newJar.equals(running))
        {
            LOG.warn("The staged jar is the running jar; nothing to do.");
            staged = null;
            return;
        }

        try
        {
            Path backup = running.resolveSibling(running.getFileName() + BACKUP_SUFFIX);
            LOG.info("Installing {}. Previous version kept at {}", stagedVersion, backup.getFileName());

            // Moved, not deleted. If the new jar does not start, the operator has something
            // to rename back rather than a bot that is simply gone.
            Files.move(running, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.move(newJar, running, StandardCopyOption.REPLACE_EXISTING);

            restart(running);
        }
        catch (IOException ex)
        {
            LOG.error("Failed to install the update: {}. The bot keeps running on the current version.",
                      ex.toString());
            staged = null;
        }
    }

    /**
     * Spawns a fresh JVM on {@code jar} and shuts this one down.
     *
     * <p>The new process is started first and inherits stdio, so its startup output lands
     * wherever this process's output was going — a systemd journal, a terminal, a log file.
     */
    private void restart(Path jar)
    {
        try
        {
            // Not named "java": a local of that name shadows the java.* package,
            // making java.lang.management below unresolvable.
            String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();

            List<String> command = new ArrayList<>();
            command.add(javaBinary);
            // Carried over so a restart does not silently drop -Xmx or -Dnogui from however
            // the operator launched the bot.
            command.addAll(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
            command.add("-jar");
            command.add(jar.toAbsolutePath().toString());

            LOG.info("Restarting into {}...", jar.getFileName());

            new ProcessBuilder(command)
                    .directory(jar.getParent().toFile())
                    .inheritIO()
                    .start();

            // Shut down cleanly so the instance lock is released and voice connections are
            // closed politely; the replacement would otherwise refuse to start.
            bot.shutdown();
        }
        catch (IOException | RuntimeException ex)
        {
            LOG.error("Could not restart automatically: {}. The new jar is in place — restart "
                      + "manually to finish the update.", ex.toString());
        }
    }

    /** Filesystem location of the jar this process is running from. */
    private static Path currentJar()
    {
        try
        {
            return new File(SelfUpdater.class.getProtectionDomain()
                                             .getCodeSource()
                                             .getLocation()
                                             .toURI()).toPath();
        }
        catch (URISyntaxException | RuntimeException ex)
        {
            throw new IllegalStateException("Could not determine the running jar's location", ex);
        }
    }

    /** Version staged for installation, if any. */
    public Optional<String> getStagedVersion()
    {
        return Optional.ofNullable(stagedVersion);
    }
}
