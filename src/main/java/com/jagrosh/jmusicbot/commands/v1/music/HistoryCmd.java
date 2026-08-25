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
package com.jagrosh.jmusicbot.commands.v1.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.MusicCommand;
import com.jagrosh.jmusicbot.commands.v2.music.HistorySlashCmd;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.List;

/**
 * Text command to show playback history. Sends the same interactive embed and buttons as /history
 * so users can peek, queue, play, or save history as a playlist.
 */
public class HistoryCmd extends MusicCommand
{
    private final MusicService musicService;

    public HistoryCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "history";
        this.help = "shows recently played tracks (history queue)";
        this.arguments = "[pagenum]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = false;
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        int page = 1;
        String args = event.getArgs().trim();
        if (!args.isEmpty())
        {
            try
            {
                page = Integer.parseInt(args.split("\\s+")[0]);
            }
            catch (NumberFormatException ignored)
            {
            }
        }

        MusicService.HistoryInfo historyInfo = musicService.getHistoryInfo(event.getGuild(), event.getJDA());
        if (historyInfo == null)
        {
            event.reply(event.getClient().getWarning() + " Playback history is empty!");
            return;
        }
        if (historyInfo.isDisabled())
        {
            event.reply(event.getClient().getWarning() + " " + MusicService.HISTORY_DISABLED_MESSAGE);
            return;
        }
        if (historyInfo.isEmpty())
        {
            event.reply(event.getClient().getWarning() + " Playback history is empty!");
            return;
        }

        int totalPages = HistorySlashCmd.getTotalPages(historyInfo.tracks.length);
        if (page > totalPages)
        {
            page = totalPages;
        }
        if (page < 1)
        {
            page = 1;
        }

        long userId = event.getAuthor().getIdLong();
        int tracksOnPage = HistorySlashCmd.getTracksOnPage(page, historyInfo.tracks.length);

        var member = event.getMember();
        var color = member != null ? member.getColor() : event.getSelfMember().getColor();
        var embed = HistorySlashCmd.buildHistoryEmbed(historyInfo, page, totalPages, 0, color);
        List<net.dv8tion.jda.api.components.actionrow.ActionRow> components =
                HistorySlashCmd.buildHistoryComponents(page, totalPages, tracksOnPage, 0, userId);

        MessageCreateData message = new MessageCreateBuilder()
                .addEmbeds(embed)
                .setComponents(components)
                .build();
        event.reply(message);
    }
}
