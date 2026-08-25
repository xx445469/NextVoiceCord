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
package com.jagrosh.jmusicbot.commands.v2.music;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.MusicSlashCommand;
import com.jagrosh.jmusicbot.listener.interaction.PaginatedListComponents;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.utils.PaginatedListEmbedUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Slash command to show playback history (recently played tracks) with actions to queue, play, or save as playlist.
 */
public class HistorySlashCmd extends MusicSlashCommand
{
    public static final int TRACKS_PER_PAGE = 10;
    private final MusicService musicService;

    public HistorySlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "history";
        this.help = "shows recently played tracks (history queue)";
        this.options = Collections.singletonList(
                new OptionData(OptionType.INTEGER, "page", "page number to display", false)
                        .setMinValue(1)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = false;
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        int page = event.getOption("page") != null ? (int) event.getOption("page").getAsLong() : 1;

        MusicService.HistoryInfo historyInfo = musicService.getHistoryInfo(event.getGuild(), event.getJDA());
        if (historyInfo == null)
        {
            event.reply(event.getClient().getWarning() + " " + bot.msg(event.getGuild(), "history.errors.empty")).setEphemeral(true).queue();
            return;
        }
        if (historyInfo.isDisabled())
        {
            event.reply(event.getClient().getWarning() + " " + bot.msg(event.getGuild(), "history.disabled")).setEphemeral(true).queue();
            return;
        }
        if (historyInfo.isEmpty())
        {
            event.reply(event.getClient().getWarning() + " " + bot.msg(event.getGuild(), "history.errors.empty")).setEphemeral(true).queue();
            return;
        }

        int totalPages = (int) Math.ceil((double) historyInfo.tracks.length / TRACKS_PER_PAGE);
        if (page > totalPages)
        {
            page = totalPages;
        }

        long userId = event.getUser().getIdLong();
        int tracksOnPage = getTracksOnPage(page, historyInfo.tracks.length);

        MessageEmbed embed = buildHistoryEmbed(bot, event.getGuild(), historyInfo, page, totalPages, 0, event.getMember().getColor());
        List<ActionRow> components = buildHistoryComponents(bot, event.getGuild(), page, totalPages, tracksOnPage, 0, userId);

        event.replyEmbeds(embed).setComponents(components).queue();
    }

    /**
     * Builds the history embed with paginated track list.
     */
    public static MessageEmbed buildHistoryEmbed(Bot bot, net.dv8tion.jda.api.entities.Guild guild,
                                                 MusicService.HistoryInfo historyInfo, int page, int totalPages,
                                                 int selectedTrack, Color memberColor)
    {
        int startIndex = (page - 1) * TRACKS_PER_PAGE;
        int endIndex = Math.min(startIndex + TRACKS_PER_PAGE, historyInfo.tracks.length);
        List<String> pageLines = Arrays.asList(historyInfo.tracks).subList(startIndex, endIndex);

        String description = PaginatedListEmbedUtil.buildNumberedListSection(
                bot.msg(guild, "history.embed.section"), pageLines, selectedTrack, startIndex + 1);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(bot.msg(guild, "history.embed.title"))
                .setDescription(description)
                .addField(bot.msg(guild, "queue.embed.entries"), String.valueOf(historyInfo.tracks.length), true)
                .addField(bot.msg(guild, "nowplaying.embed.duration"), TimeUtil.formatTime(historyInfo.totalDuration), true)
                .addField(bot.msg(guild, "history.embed.maxHistory"), String.valueOf(historyInfo.maxSize), true);
        PaginatedListEmbedUtil.applyStandardEmbedOptions(embed,
                bot.msg(guild, "history.embed.pageFooter", page, totalPages), memberColor);

        return embed.build();
    }

    /**
     * Builds the interactive button components for history.
     * Component ID format: history_{action}_{page}_{selectedTrack}_{userId}
     */
    public static List<ActionRow> buildHistoryComponents(Bot bot, net.dv8tion.jda.api.entities.Guild guild,
                                                         int page, int totalPages, int tracksOnPage,
                                                         int selectedTrack, long userId)
    {
        List<ActionRow> rows = new ArrayList<>();
        String baseId = PaginatedListComponents.baseId("history", page, selectedTrack, userId);
        rows.addAll(PaginatedListComponents.buildSelectRows(baseId, page, TRACKS_PER_PAGE, tracksOnPage, selectedTrack));

        // Row 3: Pagination
        ActionRow paginationRow = PaginatedListComponents.buildPaginationRow(baseId, page, totalPages);
        if (paginationRow != null)
        {
            rows.add(paginationRow);
        }

        // Row 4: Save as playlist always; Queue and Play Now only when a track is selected; Add all to queue only when no track selected
        Button saveBtn = Button.primary(String.format(baseId, "save"), bot.msg(guild, "history.button.save")).withEmoji(Emoji.fromUnicode("💾"));
        if (selectedTrack > 0)
        {
            Button queueBtn = Button.secondary(String.format(baseId, "queue"), bot.msg(guild, "history.button.queue")).withEmoji(Emoji.fromUnicode("➕"));
            Button playNowBtn = Button.success(String.format(baseId, "playnow"), bot.msg(guild, "queue.button.playNow")).withEmoji(Emoji.fromUnicode("▶️"));
            rows.add(ActionRow.of(queueBtn, playNowBtn, saveBtn));
        }
        else
        {
            Button queueAllBtn = Button.secondary(String.format(baseId, "queueall"), bot.msg(guild, "history.button.queueAll")).withEmoji(Emoji.fromUnicode("📋"));
            rows.add(ActionRow.of(queueAllBtn, saveBtn));
        }

        return rows;
    }

    public static int getTracksOnPage(int page, int totalTracks)
    {
        int startIndex = (page - 1) * TRACKS_PER_PAGE;
        return Math.min(TRACKS_PER_PAGE, totalTracks - startIndex);
    }

    public static int getTotalPages(int totalTracks)
    {
        return (int) Math.ceil((double) totalTracks / TRACKS_PER_PAGE);
    }
}
