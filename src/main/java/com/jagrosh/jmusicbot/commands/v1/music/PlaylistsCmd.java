/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.commands.v1.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.MusicCommand;
import com.jagrosh.jmusicbot.commands.v2.music.PlaylistsSlashCmd;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.List;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PlaylistsCmd extends MusicCommand 
{
    public PlaylistsCmd(Bot bot)
    {
        super(bot);
        this.name = "playlists";
        this.help = "shows the available playlists";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = true;
        this.beListening = false;
        this.beListening = false;
    }
    
    @Override
    public void doCommand(CommandEvent event) 
    {
        var playlistNamesInfo = bot.getMusicService().getAvailablePlaylistNames();
        if(playlistNamesInfo.hasError())
            event.reply(event.getClient().getError()+" " + playlistNamesInfo.errorMessage);
        else if(playlistNamesInfo.names.isEmpty())
            event.reply(event.getClient().getWarning()+" There are no playlists in the Playlists folder!");
        else
        {
            List<String> list = playlistNamesInfo.names;
            int page = 1;
            int totalPages = PlaylistsSlashCmd.getTotalPages(list.size());
            int playlistsOnPage = PlaylistsSlashCmd.getPlaylistsOnPage(page, list.size());
            long userId = event.getAuthor().getIdLong();
            var member = event.getMember();
            var color = member != null ? member.getColor() : event.getSelfMember().getColor();

            var embed = PlaylistsSlashCmd.buildPlaylistsEmbed(list, page, totalPages, 0, color);
            var components = PlaylistsSlashCmd.buildPlaylistsComponents(page, totalPages, playlistsOnPage, 0, userId);
            MessageCreateData message = new MessageCreateBuilder()
                    .addEmbeds(embed)
                    .setComponents(components)
                    .build();
            event.reply(message);
        }
    }
}
