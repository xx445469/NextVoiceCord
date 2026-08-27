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

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.update.SelfUpdater;
import com.jagrosh.jmusicbot.update.UpdateChecker;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.managers.AudioManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SelfUpdater#decideInstall(boolean)} and the guarantee it exists to make checkable:
 * nothing installs — nothing calls {@link Bot#shutdown()} or touches a file on disk — from
 * deciding alone, and not from the hourly background check either. Only an explicit, confirmed
 * request is ever allowed to reach the actual swap-and-restart, which is exactly why this test
 * never calls {@link SelfUpdater#installNow()}: that method's whole job, on the one path these
 * tests could reach, is to replace the running process, and there is no way to call it from a
 * test without either doing that for real or not testing it at all. Every claim these tests can
 * make about "nothing installs without an explicit request" is instead made the other way round:
 * by proving the paths that are NOT an explicit request never get anywhere near it.
 */
@DisplayName("SelfUpdater install decision")
class SelfUpdaterInstallDecisionTest
{
    @Test
    @DisplayName("nothing staged: reports NotStaged, and never touches Bot at all")
    void nothingStagedReportsNotStaged()
    {
        Bot bot = mock(Bot.class);
        SelfUpdater updater = new SelfUpdater(bot, new UpdateChecker());

        SelfUpdater.InstallDecision decision = updater.decideInstall(false);

        assertInstanceOf(SelfUpdater.InstallDecision.NotStaged.class, decision);
        verify(bot, never()).shutdown();
        verify(bot, never()).getJDA();
    }

    @Test
    @DisplayName("staged, nothing playing: reports Ready with the staged version")
    void stagedAndIdleReportsReady() throws ReflectiveOperationException
    {
        Bot bot = mock(Bot.class);
        when(bot.getJDA()).thenReturn(null);
        SelfUpdater updater = stage(bot, "9.9.9");

        SelfUpdater.InstallDecision decision = updater.decideInstall(false);

        assertInstanceOf(SelfUpdater.InstallDecision.Ready.class, decision);
        assertEquals("9.9.9", ((SelfUpdater.InstallDecision.Ready) decision).version());
        verify(bot, never()).shutdown();
    }

    @Test
    @DisplayName("staged, something playing, not forced: reports Blocked with who is playing, and never installs")
    void stagedAndPlayingReportsBlocked() throws ReflectiveOperationException
    {
        JDA jda = mock(JDA.class);
        Guild guild = mock(Guild.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioHandler audioHandler = mock(AudioHandler.class);

        when(guild.getName()).thenReturn("Listener Lounge");
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(audioManager.getSendingHandler()).thenReturn(audioHandler);
        when(audioHandler.isMusicPlaying(jda)).thenReturn(true);
        when(jda.getGuilds()).thenReturn(List.of(guild));

        Bot bot = mock(Bot.class);
        when(bot.getJDA()).thenReturn(jda);
        SelfUpdater updater = stage(bot, "9.9.9");

        SelfUpdater.InstallDecision decision = updater.decideInstall(false);

        assertInstanceOf(SelfUpdater.InstallDecision.Blocked.class, decision);
        assertEquals(List.of("Listener Lounge"), ((SelfUpdater.InstallDecision.Blocked) decision).playingGuilds());
        verify(bot, never()).shutdown();
    }

    @Test
    @DisplayName("staged, something playing, forced: reports Ready — force is what a person confirming supplies")
    void stagedAndPlayingButForcedReportsReady() throws ReflectiveOperationException
    {
        JDA jda = mock(JDA.class);
        Guild guild = mock(Guild.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioHandler audioHandler = mock(AudioHandler.class);

        when(guild.getName()).thenReturn("Listener Lounge");
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(audioManager.getSendingHandler()).thenReturn(audioHandler);
        when(audioHandler.isMusicPlaying(jda)).thenReturn(true);
        when(jda.getGuilds()).thenReturn(List.of(guild));

        Bot bot = mock(Bot.class);
        when(bot.getJDA()).thenReturn(jda);
        SelfUpdater updater = stage(bot, "9.9.9");

        SelfUpdater.InstallDecision decision = updater.decideInstall(true);

        assertInstanceOf(SelfUpdater.InstallDecision.Ready.class, decision);
        verify(bot, never()).shutdown();
    }

    @Test
    @DisplayName("installNow() staged does nothing when nothing is staged")
    void installNowIsANoOpWithNothingStaged()
    {
        // The one call into installNow() safe to make from a test: with staged == null it
        // returns immediately, before touching the filesystem or Bot.shutdown() at all — see
        // the guard at the top of installNow(). This is the only branch of that method this
        // suite exercises, deliberately.
        Bot bot = mock(Bot.class);
        SelfUpdater updater = new SelfUpdater(bot, new UpdateChecker());

        updater.installNow();

        verify(bot, never()).shutdown();
    }

    @Test
    @DisplayName("checkAndStage(), the hourly background task, never installs even once something is staged")
    void checkAndStageNeverInstalls() throws ReflectiveOperationException
    {
        // checkAndStage() bails out immediately once something is already staged (see its own
        // "staged != null" guard) — this is exactly what a repeated hourly firing does once the
        // first check found something, and it must never progress from there to an install on
        // its own.
        Bot bot = mock(Bot.class);
        SelfUpdater updater = stage(bot, "9.9.9");

        updater.checkAndStage();
        updater.checkAndStage();
        updater.checkAndStage();

        verify(bot, never()).shutdown();
        assertTrue(updater.getStagedVersion().isPresent());
        assertEquals("9.9.9", updater.getStagedVersion().get());
    }

    /** Stages a fake release via reflection — no download, no network, nothing on disk. */
    private static SelfUpdater stage(Bot bot, String version) throws ReflectiveOperationException
    {
        SelfUpdater updater = new SelfUpdater(bot, new UpdateChecker());

        Field staged = SelfUpdater.class.getDeclaredField("staged");
        staged.setAccessible(true);
        staged.set(updater, Path.of("bot-" + version + ".jar"));

        Field stagedVersion = SelfUpdater.class.getDeclaredField("stagedVersion");
        stagedVersion.setAccessible(true);
        stagedVersion.set(updater, version);

        return updater;
    }
}
