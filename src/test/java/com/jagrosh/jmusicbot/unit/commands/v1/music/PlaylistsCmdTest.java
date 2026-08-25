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
package com.jagrosh.jmusicbot.unit.commands.v1.music;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.music.PlaylistsCmd;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaylistsCmdTest
{
    private Bot bot;
    private PlaylistsCmd command;
    private MusicService musicService;
    private CommandEvent event;

    @BeforeEach
    void setUp()
    {
        bot = mock(Bot.class);
        musicService = mock(MusicService.class);
        event = mock(CommandEvent.class);
        CommandClient client = mock(CommandClient.class);
        Member member = mock(Member.class);
        User user = mock(User.class);

        when(bot.getMusicService()).thenReturn(musicService);
        when(bot.getConfig()).thenReturn(mock(com.jagrosh.jmusicbot.BotConfig.class));
        when(bot.getConfig().getAliases(any())).thenReturn(new String[0]);

        when(event.getClient()).thenReturn(client);
        when(client.getWarning()).thenReturn("⚠️");
        when(client.getError()).thenReturn("❌");
        when(event.getAuthor()).thenReturn(user);
        when(user.getIdLong()).thenReturn(42L);
        when(event.getMember()).thenReturn(member);
        when(member.getColor()).thenReturn(null);

        command = new PlaylistsCmd(bot);
    }

    @Test
    void doCommand_emptyPlaylists_repliesWarningText()
    {
        when(musicService.getAvailablePlaylistNames()).thenReturn(MusicService.PlaylistNamesInfo.success(List.of()));

        command.doCommand(event);

        verify(event).reply(any(String.class));
    }

    @Test
    void doCommand_hasPlaylists_repliesInteractiveMessage()
    {
        when(musicService.getAvailablePlaylistNames())
                .thenReturn(MusicService.PlaylistNamesInfo.success(List.of("favorite", "workout")));

        command.doCommand(event);

        verify(event).reply(any(MessageCreateData.class));
    }
}
