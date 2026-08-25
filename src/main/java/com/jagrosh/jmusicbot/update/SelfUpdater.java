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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.utils.OtherUtil;

import net.dv8tion.jda.api.entities.Guild;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads new releases and restarts into them.
 *
 * <p>Disabled by default. Replacing the binary of a running process is not something to do
 * to someone without being asked: a bad release, or a release the operator did not want,
 * takes the bot down with no human in the loop. {@code updates.autoUpdate} is opt-in.
 *
 * <h2>Why it waits</h2>
 * A restart cuts every voice connection instantly. Applying an update the moment it is found
 * would drop listeners mid-song, which is a worse experience than running yesterday's build
 * for a few more hours. The update is downloaded eagerly — that is invisible to users — but
 * installed only once nothing is playing anywhere.
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

    private final Bot bot;
    private final UpdateChecker checker;
    private final boolean autoInstall;

    /** Guards against a second check starting while one is mid-download. */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    /** Set once an update is staged, so idle checks know there is something to install. */
    private volatile Path staged;
    private volatile String stagedVersion;

    public SelfUpdater(Bot bot, UpdateChecker checker, boolean autoInstall)
    {
        this.bot = bot;
        this.checker = checker;
        this.autoInstall = autoInstall;
    }

    /**
     * Starts periodic checking.
     *
     * @param intervalHours hours between checks
     */
    public void start(int intervalHours)
    {
        long interval = Math.max(1, intervalHours);
        bot.getThreadpool().scheduleWithFixedDelay(
                this::checkAndStage,
                // Delayed rather than immediate: startup is already doing enough, and an
                // update that has waited hours can wait five minutes more.
                5, interval * 60, TimeUnit.MINUTES);

        if (autoInstall)
        {
            // Separate, more frequent cadence. Staging is rate-limited by GitHub; noticing
            // that playback stopped is free, and the wait for idle is the unpredictable part.
            bot.getThreadpool().scheduleWithFixedDelay(
                    this::installIfIdle, 10, 5, TimeUnit.MINUTES);
        }

        LOG.info("Update checks every {}h; automatic install is {}.",
                 interval, autoInstall ? "ON — the bot will restart itself when idle" : "off");
    }

    /** Looks for a newer release and downloads it, without installing. */
    public void checkAndStage()
    {
        if (staged != null || !busy.compareAndSet(false, true))
        {
            return;
        }

        try
        {
            String current = OtherUtil.getCurrentVersion();
            Optional<UpdateChecker.Release> latest = checker.fetchLatest();

            if (latest.isEmpty() || !UpdateChecker.isNewer(current, latest.get().version()))
            {
                return;
            }

            UpdateChecker.Release release = latest.get();
            LOG.info("Update available: {} -> {}", current, release.version());

            Optional<Path> downloaded = checker.download(release, currentJar().getParent());
            if (downloaded.isPresent())
            {
                staged = downloaded.get();
                stagedVersion = release.version();
                LOG.info("Version {} is staged. It will be installed once nothing is playing.",
                         stagedVersion);
            }
        }
        catch (RuntimeException ex)
        {
            LOG.warn("Update check failed: {}", ex.toString());
        }
        finally
        {
            busy.set(false);
        }
    }

    /** Installs a staged update if no guild is currently playing. */
    public void installIfIdle()
    {
        if (staged == null)
        {
            return;
        }

        List<String> busyGuilds = playingGuilds();
        if (!busyGuilds.isEmpty())
        {
            LOG.debug("Update to {} is waiting; still playing in {} guild(s).",
                      stagedVersion, busyGuilds.size());
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
