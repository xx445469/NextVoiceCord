/*
 * Copyright 2026 Arif Banai (arif-banai)
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
package com.jagrosh.jmusicbot.unit.commands.v1;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.commands.v1.MusicCommand;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.testutil.TestTranslations;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Tests for {@link MusicCommand} base class validation logic, mirroring
 * {@code MusicSlashCommandTest} for the text-command (v1) path.
 *
 * <p>Regression coverage for commands invoked from a voice channel's built-in text chat: the
 * event's channel is mocked as a {@link VoiceChannel} whose {@code asTextChannel()} would throw
 * {@link IllegalStateException}, exactly as the real JDA union type does. A fixture that quietly
 * handed back a {@link TextChannel} instead would pass even if the production code regressed to
 * calling {@code CommandEvent.getTextChannel()}.
 */
class MusicCommandTest
{
    private Bot bot;
    private CommandEvent event;
    private CommandClient client;
    private Guild guild;
    private Member member;
    private Settings settings;
    private VoiceChannel voiceChannelChat;
    private Message message;

    @BeforeEach
    void setUp()
    {
        bot = TestTranslations.mockBot();
        BotConfig config = mock(BotConfig.class);
        SettingsManager settingsManager = mock(SettingsManager.class);
        settings = mock(Settings.class);
        PlayerManager playerManager = mock(PlayerManager.class);
        AudioManager audioManager = mock(AudioManager.class);
        JDA jda = mock(JDA.class);

        guild = mock(Guild.class);
        member = mock(Member.class);
        client = mock(CommandClient.class);
        message = mock(Message.class);

        // Stands in for a voice channel's built-in text chat. Implements
        // GuildMessageChannelUnion the way the real channel entity does, but - unlike a plain
        // TextChannel mock - calling asTextChannel() on it must behave like real JDA: throw.
        voiceChannelChat = mock(VoiceChannel.class, withSettings().extraInterfaces(GuildMessageChannelUnion.class));
        when(((GuildMessageChannelUnion) voiceChannelChat).asTextChannel())
                .thenThrow(new IllegalStateException("Cannot convert MessageChannel of type VoiceChannel to TextChannel!"));

        event = mock(CommandEvent.class);

        when(bot.getConfig()).thenReturn(config);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(bot.getPlayerManager()).thenReturn(playerManager);
        when(config.isLavalinkMode()).thenReturn(false);
        when(settingsManager.getSettings(guild)).thenReturn(settings);
        when(playerManager.setUpHandler(guild)).thenReturn(null);

        when(event.getClient()).thenReturn(client);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(event.getJDA()).thenReturn(jda);
        when(event.getMessage()).thenReturn(message);
        // Real commands resolve the invocation channel through getGuildChannel(), which covers
        // both text channels and a voice channel's built-in text chat - never getTextChannel(),
        // which throws for the latter (verified by decompiling jda-chewtils 2.2.1).
        when(event.getGuildChannel()).thenReturn((GuildMessageChannelUnion) voiceChannelChat);
        // Mirrors the real jda-chewtils delegation (getTextChannel() = getChannel().asTextChannel())
        // so a production regression back to getTextChannel() is caught by these tests instead of
        // quietly returning a channel.
        when(event.getTextChannel()).thenAnswer(inv -> ((GuildMessageChannelUnion) voiceChannelChat).asTextChannel());
        when(client.getSettingsFor(guild)).thenReturn(settings);
        when(client.getError()).thenReturn("❌");

        when(guild.getAudioManager()).thenReturn(audioManager);
        when(audioManager.getSendingHandler()).thenReturn(null);

        AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);
        when(message.delete()).thenReturn(deleteAction);
        doNothing().when(deleteAction).queue();
        doNothing().when(event).replyInDm(any(String.class));

        // Default: no configured command channel, so a voice-chat invocation is unrestricted.
        when(settings.getTextChannel(guild)).thenReturn(null);
    }

    private TestMusicCommand basicCommand()
    {
        return new TestMusicCommand(bot);
    }

    @Test
    void execute_InvokedFromVoiceChannelChat_CallsDoCommand()
    {
        // No settc restriction: a command typed in a voice channel's text chat must succeed,
        // not throw IllegalStateException from an internal asTextChannel() call.
        TestMusicCommand command = basicCommand();

        command.testExecute(event);

        assertTrue(command.doCommandCalled, "doCommand should run for a command used in voice-channel chat");
    }

    @Test
    void execute_InvokedFromVoiceChannelChatWithTextChannelRestriction_SendsRedirectReplyNotException()
    {
        TextChannel requiredChannel = mock(TextChannel.class);
        when(requiredChannel.getAsMention()).thenReturn("#music");
        when(settings.getTextChannel(guild)).thenReturn(requiredChannel);
        TestMusicCommand command = basicCommand();

        command.testExecute(event);

        assertFalse(command.doCommandCalled, "doCommand must not run when settc points elsewhere");
        verify(event).replyInDm("❌ You can only use that command in #music!");
        verify(message).delete();
    }

    /**
     * Minimal test double exposing the protected {@code execute} method for testing, mirroring
     * {@code TestMusicSlashCommand} for the v1 (text command) path.
     */
    private static final class TestMusicCommand extends MusicCommand
    {
        private boolean doCommandCalled = false;

        private TestMusicCommand(Bot bot)
        {
            super(bot);
        }

        @Override
        public void doCommand(CommandEvent event)
        {
            doCommandCalled = true;
        }

        void testExecute(CommandEvent event)
        {
            execute(event);
        }
    }
}
