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
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.PaginatedListEmbedUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Slash command to show available playlists with interactive pagination and actions.
 */
public class PlaylistsSlashCmd extends MusicSlashCommand
{
    public static final int PLAYLISTS_PER_PAGE = 10;
    public static final int PLAYLIST_TRACKS_PER_PAGE = 10;
    private static final String FAVORITES_PLAYLIST_NAME = "favorites";
    private static final String FAVORITES_DISPLAY_LABEL = "⭐ favorites ⭐";

    public PlaylistsSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "playlists";
        this.help = "shows the available playlists";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = false;
        this.bePlaying = false;
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        MusicService.PlaylistNamesInfo playlistNamesInfo = bot.getMusicService().getAvailablePlaylistNames();
        if (playlistNamesInfo.hasError())
        {
            event.reply(event.getClient().getError() + " " + playlistNamesInfo.errorMessage)
                    .setEphemeral(true).queue();
        }
        else if (playlistNamesInfo.names.isEmpty())
        {
            event.reply(event.getClient().getWarning() + " There are no playlists in the Playlists folder!")
                    .setEphemeral(true).queue();
        }
        else
        {
            List<String> list = playlistNamesInfo.names;
            int totalPages = getTotalPages(list.size());
            int page = 1;
            long userId = event.getUser().getIdLong();
            int playlistsOnPage = getPlaylistsOnPage(page, list.size());
            Color color = event.getMember() == null ? null : event.getMember().getColor();

            MessageEmbed embed = buildPlaylistsEmbed(list, page, totalPages, 0, color);
            List<ActionRow> components = buildPlaylistsComponents(page, totalPages, playlistsOnPage, 0, userId);
            event.replyEmbeds(embed).setComponents(components).queue();
        }
    }

    /**
     * Builds the playlists embed with paginated playlist list.
     */
    public static MessageEmbed buildPlaylistsEmbed(List<String> playlists, int page, int totalPages,
                                                   int selectedIndex, Color memberColor)
    {
        int startIndex = (page - 1) * PLAYLISTS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYLISTS_PER_PAGE, playlists.size());
        List<String> lineContents = playlists.subList(startIndex, endIndex).stream()
                .map(name -> "`" + formatPlaylistDisplayLabel(name) + "`")
                .collect(Collectors.toList());

        String description = PaginatedListEmbedUtil.buildNumberedListSection(
                "**Available playlists** *(select one below to queue or play now)*", lineContents, selectedIndex, startIndex + 1);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Playlists")
                .setDescription(description)
                .addField("Entries", String.valueOf(playlists.size()), true);
        PaginatedListEmbedUtil.applyStandardEmbedOptions(embed, "Page " + page + " of " + totalPages, memberColor);
        return embed.build();
    }

    /**
     * Builds the playlist details embed with preview lines in the same style as Queue/History.
     * Preview items are shown as markdown links to avoid Discord URL unfurling and misalignment.
     */
    public static MessageEmbed buildPlaylistDetailsEmbed(MusicService.PlaylistDetailsInfo details, Color memberColor)
    {
        int previewSize = details.previewItems.size();
        List<String> lineContents = new ArrayList<>(previewSize);
        for (String url : details.previewItems)
        {
            String label = formatPreviewLinkLabel(url);
            lineContents.add("[**" + label + "**](" + url + ")");
        }

        StringBuilder description = new StringBuilder();
        if (previewSize > 0)
        {
            description.append(PaginatedListEmbedUtil.buildNumberedListSection(
                    "**Preview** *(first " + previewSize + " entries)*", lineContents, 0, 1));
            if (details.hasMore)
            {
                description.append("...");
            }
        }

        String footer = previewSize > 0 ? "Preview: first " + previewSize + " entries" : null;
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Playlist: " + details.playlistName)
                .addField("Entries", String.valueOf(details.totalItems), true);
        if (description.length() > 0)
        {
            embed.setDescription(description.toString());
        }
        PaginatedListEmbedUtil.applyStandardEmbedOptions(embed, footer, memberColor);
        return embed.build();
    }

    /**
     * Builds the playlist details embed from pre-formatted track lines (e.g. from async load), in the same
     * style as Queue/History. Use this when lines are already in the format {@code `[MM:SS]` [**Title**](url)}.
     */
    public static MessageEmbed buildPlaylistDetailsEmbed(String playlistName, int totalItems,
                                                         List<String> formattedLineContents, boolean hasMore,
                                                         Color memberColor)
    {
        int previewSize = formattedLineContents != null ? formattedLineContents.size() : 0;
        StringBuilder description = new StringBuilder();
        if (previewSize > 0)
        {
            description.append(PaginatedListEmbedUtil.buildNumberedListSection(
                    "**Preview** *(first " + previewSize + " entries)*", formattedLineContents, 0, 1));
            if (hasMore)
            {
                description.append("...");
            }
        }

        String footer = previewSize > 0 ? "Preview: first " + previewSize + " entries" : null;
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Playlist: " + playlistName)
                .addField("Entries", String.valueOf(totalItems), true);
        if (description.length() > 0)
        {
            embed.setDescription(description.toString());
        }
        PaginatedListEmbedUtil.applyStandardEmbedOptions(embed, footer, memberColor);
        return embed.build();
    }

    /**
     * Builds an interactive, paginated playlist details embed (Queue-style).
     */
    public static MessageEmbed buildPlaylistTracksEmbed(String playlistName, int totalItems,
                                                        int page, int totalPages,
                                                        List<String> formattedTrackLines,
                                                        int selectedTrack, boolean draftDirty,
                                                        Color memberColor)
    {
        int startIndex = (page - 1) * PLAYLIST_TRACKS_PER_PAGE;
        String description = PaginatedListEmbedUtil.buildNumberedListSection(
                "**Tracks** *(select one below)*",
                formattedTrackLines,
                selectedTrack,
                startIndex + 1
        );

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Playlist: " + playlistName)
                .setDescription(description)
                .addField("Entries", String.valueOf(totalItems), true);
        String footer = "Page " + page + " of " + totalPages;
        if (draftDirty)
        {
            footer += " • Unsaved changes";
        }
        PaginatedListEmbedUtil.applyStandardEmbedOptions(embed, footer, memberColor);
        return embed.build();
    }

    /**
     * Returns a short label for a preview link. Extracts YouTube video ID when possible; otherwise "Link".
     */
    private static String formatPreviewLinkLabel(String url)
    {
        if (url == null)
        {
            return "Link";
        }
        // YouTube: ...?v=VIDEO_ID or youtu.be/VIDEO_ID
        int v = url.indexOf("?v=");
        if (v >= 0)
        {
            int end = url.indexOf('&', v + 3);
            String id = end >= 0 ? url.substring(v + 3, end) : url.substring(v + 3);
            if (!id.isEmpty())
                return FormatUtil.filter(id.length() <= 20 ? id : id.substring(0, 17) + "...");
        }
        int be = url.indexOf("youtu.be/");
        if (be >= 0)
        {
            int start = be + 9;
            int end = url.indexOf('?', start);
            String id = end >= 0 ? url.substring(start, end) : url.substring(start);
            if (!id.isEmpty())
                return FormatUtil.filter(id.length() <= 20 ? id : id.substring(0, 17) + "...");
        }
        return "Link";
    }

    private static String formatPlaylistDisplayLabel(String playlistName)
    {
        if (playlistName != null && playlistName.equalsIgnoreCase(FAVORITES_PLAYLIST_NAME))
        {
            return FAVORITES_DISPLAY_LABEL;
        }
        return playlistName;
    }

    /**
     * Builds interactive button rows for playlist list navigation and actions.
     * Component ID format: playlists_{action}_{page}_{selectedIndex}_{userId}
     */
    public static List<ActionRow> buildPlaylistsComponents(int page, int totalPages, int playlistsOnPage,
                                                           int selectedIndex, long userId)
    {
        List<ActionRow> rows = new ArrayList<>();
        String baseId = PaginatedListComponents.baseId("playlists", page, selectedIndex, userId);
        rows.addAll(PaginatedListComponents.buildSelectRows(baseId, page, PLAYLISTS_PER_PAGE, playlistsOnPage, selectedIndex));
        ActionRow paginationRow = PaginatedListComponents.buildPaginationRow(baseId, page, totalPages);
        if (paginationRow != null)
        {
            rows.add(paginationRow);
        }

        Button refreshBtn = Button.secondary(String.format(baseId, "refresh"), "Refresh").withEmoji(Emoji.fromUnicode("🔄"));
        if (selectedIndex > 0)
        {
            Button queueBtn = Button.secondary(String.format(baseId, "queue"), "Queue").withEmoji(Emoji.fromUnicode("➕"));
            Button playNowBtn = Button.success(String.format(baseId, "playnow"), "Play Now").withEmoji(Emoji.fromUnicode("▶️"));
            Button detailsBtn = Button.primary(String.format(baseId, "details"), "Details").withEmoji(Emoji.fromUnicode("ℹ️"));
            rows.add(ActionRow.of(queueBtn, playNowBtn, detailsBtn, refreshBtn));
        }
        else
        {
            rows.add(ActionRow.of(refreshBtn));
        }

        return rows;
    }

    /**
     * Builds interactive components for playlist track-details sub-view.
     */
    public static List<ActionRow> buildPlaylistDetailsComponents(int playlistIndex, int listPage,
                                                                 int detailsPage, int totalDetailsPages,
                                                                 int tracksOnPage, int selectedTrack,
                                                                 long userId, boolean canEdit,
                                                                 boolean draftDirty)
    {
        List<ActionRow> rows = new ArrayList<>();
        String baseId = "playlistdetails_" + playlistIndex + "_" + listPage + "_%s_" + detailsPage
                + "_" + selectedTrack + "_" + userId;
        List<Button> finalRowButtons = new ArrayList<>();
        if (canEdit && draftDirty)
        {
            finalRowButtons.add(Button.success(String.format(baseId, "save"), "Save"));
            finalRowButtons.add(Button.secondary(String.format(baseId, "discard"), "Discard"));
        }
        if (selectedTrack > 0)
        {
            // Selected mode: grouped 4-row layout (playback, move, utility, final state).
            Button moveTopBtn = Button.secondary(String.format(baseId, "movetop"), "Move top")
                    .withEmoji(Emoji.fromUnicode("⏫"));
            Button moveUpBtn = Button.secondary(String.format(baseId, "moveup"), "Up 1")
                    .withEmoji(Emoji.fromUnicode("⬆️"));
            Button moveBottomBtn = Button.secondary(String.format(baseId, "movebottom"), "Move bottom")
                    .withEmoji(Emoji.fromUnicode("⏬"));
            Button moveDownBtn = Button.secondary(String.format(baseId, "movedown"), "Down 1")
                    .withEmoji(Emoji.fromUnicode("⬇️"));
            Button moveToBtn = Button.secondary(String.format(baseId, "move"), "Move to")
                    .withEmoji(Emoji.fromUnicode("↕️"));

            Button queueBtn = Button.primary(String.format(baseId, "queue"), "Queue Track").withEmoji(Emoji.fromUnicode("➕"));
            Button playNextBtn = Button.primary(String.format(baseId, "playnext"), "Play Next").withEmoji(Emoji.fromUnicode("⏭️"));
            Button playNowBtn = Button.success(String.format(baseId, "playnow"), "Play Now").withEmoji(Emoji.fromUnicode("▶️"));
            Button removeBtn = Button.danger(String.format(baseId, "remove"), "Remove")
                    .withEmoji(Emoji.fromUnicode("🗑️"));

            rows.add(ActionRow.of(queueBtn, playNextBtn, playNowBtn));
            if (canEdit)
            {
                rows.add(ActionRow.of(moveTopBtn, moveUpBtn));
                rows.add(ActionRow.of(moveBottomBtn, moveDownBtn));
                rows.add(ActionRow.of(moveToBtn, removeBtn));
            }
            finalRowButtons.add(0, Button.danger(String.format(baseId, "unselect"), "Unselect song"));
            rows.add(ActionRow.of(finalRowButtons));
            return rows;
        }

        // Unselected mode: browsing-focused layout.
        rows.addAll(PaginatedListComponents.buildSelectRows(
                baseId, detailsPage, PLAYLIST_TRACKS_PER_PAGE, tracksOnPage, selectedTrack));
        ActionRow paginationRow = PaginatedListComponents.buildPaginationRow(baseId, detailsPage, totalDetailsPages);
        if (paginationRow != null)
        {
            rows.add(paginationRow);
        }
        Button queueAllBtn = Button.secondary(String.format(baseId, "queueall"), "Queue Playlist")
                .withEmoji(Emoji.fromUnicode("🎵"));
        Button playAllBtn = Button.success(String.format(baseId, "playall"), "Play Playlist")
                .withEmoji(Emoji.fromUnicode("📀"));
        rows.add(ActionRow.of(queueAllBtn, playAllBtn));
        finalRowButtons.add(0, Button.danger("playlistdetails_back_" + listPage + "_" + playlistIndex + "_" + userId, "Back"));
        rows.add(ActionRow.of(finalRowButtons));
        return rows;
    }

    public static int getPlaylistsOnPage(int page, int totalPlaylists)
    {
        int startIndex = (page - 1) * PLAYLISTS_PER_PAGE;
        return Math.min(PLAYLISTS_PER_PAGE, totalPlaylists - startIndex);
    }

    public static int getTotalPages(int totalPlaylists)
    {
        return (int) Math.ceil((double) totalPlaylists / PLAYLISTS_PER_PAGE);
    }

    public static int getPlaylistTracksOnPage(int page, int totalTracks)
    {
        int startIndex = (page - 1) * PLAYLIST_TRACKS_PER_PAGE;
        return Math.min(PLAYLIST_TRACKS_PER_PAGE, totalTracks - startIndex);
    }

    public static int getPlaylistTrackTotalPages(int totalTracks)
    {
        return (int) Math.ceil((double) totalTracks / PLAYLIST_TRACKS_PER_PAGE);
    }
}
