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
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.utils.PaginatedListEmbedUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Slash command to show the current queue.
 */
public class QueueSlashCmd extends MusicSlashCommand
{
    public static final int TRACKS_PER_PAGE = 10;
    private final MusicService musicService;

    public QueueSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "queue";
        this.help = "shows the current queue";
        this.options = Collections.singletonList(
                new OptionData(OptionType.INTEGER, "page", "page number to display", false)
                        .setMinValue(1)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        int page = event.getOption("page") != null ? (int) event.getOption("page").getAsLong() : 1;

        MusicService.QueueInfo queueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
        if (queueInfo == null || queueInfo.isEmpty())
        {
            MusicService.NowPlayingInfo npInfo = musicService.getNowPlayingInfo(event.getGuild(), event.getJDA());
            if (npInfo != null)
            {
                MessageCreateData embed = npInfo.isPlaying ? npInfo.nowPlayingMessage : npInfo.noMusicMessage;
                if (embed != null)
                {
                    MessageCreateData built = new MessageCreateBuilder()
                            .setContent(event.getClient().getWarning() + " There is no music in the queue!")
                            .setEmbeds(embed.getEmbeds().get(0)).build();
                    event.reply(built).queue(hook ->
                    {
                        if (npInfo.isPlaying)
                            hook.retrieveOriginal().queue(msg -> bot.getNowplayingHandler().setLastNPMessage(msg));
                    });
                    return;
                }
            }
            event.reply(event.getClient().getWarning() + " There is no music in the queue!").setEphemeral(true).queue();
            return;
        }

        // Build paginated response
        int totalPages = (int) Math.ceil((double) queueInfo.tracks.length / TRACKS_PER_PAGE);
        if (page > totalPages)
        {
            page = totalPages;
        }

        long userId = event.getUser().getIdLong();
        int tracksOnPage = getTracksOnPage(page, queueInfo.tracks.length);

        MessageEmbed embed = buildQueueEmbed(queueInfo, page, totalPages, 0, event.getMember().getColor());
        List<ActionRow> components = buildQueueComponents(page, totalPages, tracksOnPage, 0, userId);

        event.replyEmbeds(embed).setComponents(components).queue();
    }

    /**
     * Builds the queue embed with all relevant information.
     */
    public static MessageEmbed buildQueueEmbed(MusicService.QueueInfo queueInfo, int page, int totalPages,
                                               int selectedTrack, java.awt.Color memberColor)
    {
        int startIndex = (page - 1) * TRACKS_PER_PAGE;
        int endIndex = Math.min(startIndex + TRACKS_PER_PAGE, queueInfo.tracks.length);
        List<String> pageLines = Arrays.asList(queueInfo.tracks).subList(startIndex, endIndex);

        StringBuilder sb = new StringBuilder();
        if (queueInfo.nowPlayingTitle != null)
        {
            sb.append(queueInfo.statusEmoji).append(" **").append(queueInfo.nowPlayingTitle).append("**\n\n");
        }
        sb.append(PaginatedListEmbedUtil.buildNumberedListSection(
                "**Up Next** *(select a track below)*", pageLines, selectedTrack, startIndex + 1));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Current Queue")
                .setDescription(sb.toString())
                .addField("Entries", String.valueOf(queueInfo.tracks.length), true)
                .addField("Duration", TimeUtil.formatTime(queueInfo.totalDuration), true)
                .addField("Mode", queueInfo.queueType.getEmoji() + " " + queueInfo.queueType.getUserFriendlyName(), true);
        PaginatedListEmbedUtil.applyStandardEmbedOptions(embed, "Page " + page + " of " + totalPages, memberColor);

        if (queueInfo.repeatMode != RepeatMode.OFF)
        {
            embed.addField("Repeat", queueInfo.repeatMode.getEmoji() + " " + queueInfo.repeatMode.getUserFriendlyName(), true);
        }

        return embed.build();
    }

    /**
     * Builds the interactive button components for the queue.
     *
     * @param page Current page number (1-based)
     * @param totalPages Total number of pages
     * @param tracksOnPage Number of tracks displayed on current page
     * @param selectedTrack Currently selected track position (0 if none)
     * @param userId User ID who initiated the command
     * @return List of ActionRows containing the buttons
     */
    public static List<ActionRow> buildQueueComponents(int page, int totalPages, int tracksOnPage,
                                                       int selectedTrack, long userId)
    {
        List<ActionRow> rows = new ArrayList<>();
        String baseId = PaginatedListComponents.baseId("queue", page, selectedTrack, userId);
        rows.addAll(PaginatedListComponents.buildSelectRows(baseId, page, TRACKS_PER_PAGE, tracksOnPage, selectedTrack));

        // Row 3: Pagination and Shuffle
        Button shuffleBtn = Button.secondary(String.format(baseId, "shuffle"), "Shuffle").withEmoji(Emoji.fromUnicode("🔀"));
        ActionRow paginationRow = PaginatedListComponents.buildPaginationRow(baseId, page, totalPages, shuffleBtn);
        if (paginationRow != null)
        {
            rows.add(paginationRow);
        }

        // Row 4: Track actions (only shown when a track is selected)
        if (selectedTrack > 0)
        {
            Button removeBtn = Button.danger(String.format(baseId, "remove"), "Remove").withEmoji(Emoji.fromUnicode("🗑️"));
            Button playNextBtn = Button.primary(String.format(baseId, "playnext"), "Play Next").withEmoji(Emoji.fromUnicode("⏭️"));
            Button moveBtn = Button.secondary(String.format(baseId, "move"), "Move").withEmoji(Emoji.fromUnicode("↕️"));
            Button playNowBtn = Button.success(String.format(baseId, "playnow"), "Play Now").withEmoji(Emoji.fromUnicode("▶️"));

            rows.add(ActionRow.of(removeBtn, playNextBtn, moveBtn, playNowBtn));
        }

        return rows;
    }

    /**
     * Calculates the number of tracks displayed on a given page.
     */
    public static int getTracksOnPage(int page, int totalTracks)
    {
        int startIndex = (page - 1) * TRACKS_PER_PAGE;
        return Math.min(TRACKS_PER_PAGE, totalTracks - startIndex);
    }

    /**
     * Calculates the total number of pages for a given track count.
     */
    public static int getTotalPages(int totalTracks)
    {
        return (int) Math.ceil((double) totalTracks / TRACKS_PER_PAGE);
    }
}
