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
package com.jagrosh.jmusicbot.listener;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.music.QueueSlashCmd;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.listener.interaction.InteractionGuards;
import com.jagrosh.jmusicbot.listener.interaction.OutputAdapters;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Handles queue embed button and select-menu interactions (queue_*, queue_move_select_*).
 */
public class QueueInteractionListener extends ListenerAdapter {

    private final Bot bot;

    public QueueInteractionListener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("queue_")) {
            return;
        }
        handleQueueButton(event);
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith("queue_move_select_")) {
            return;
        }
        handleQueueMoveSelect(event);
    }

    private void handleQueueButton(ButtonInteractionEvent event) {
        if (!InteractionGuards.requireGuildAndMember(event)) {
            return;
        }

        Optional<ComponentIdParsers.PaginatedButtonId> parsed = ComponentIdParsers.parseQueueButtonId(event.getComponentId());
        if (parsed.isEmpty()) {
            event.reply("Invalid button state.").setEphemeral(true).queue();
            return;
        }
        ComponentIdParsers.PaginatedButtonId id = parsed.get();

        if (event.getUser().getIdLong() != id.userId()) {
            event.reply("Only the user who ran the command can use these buttons!").setEphemeral(true).queue();
            return;
        }

        if (!InteractionGuards.requireSameVoiceChannel(event)) {
            return;
        }

        int page = id.page();
        int selectedTrack = id.selectedTrack();
        long userId = id.userId();
        String action = id.action();

        MusicService musicService = bot.getMusicService();
        MusicService.QueueInfo queueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());

        if (queueInfo == null || queueInfo.isEmpty()) {
            event.editMessage("The queue is now empty!").setEmbeds().setComponents().queue();
            return;
        }

        int totalPages = QueueSlashCmd.getTotalPages(queueInfo.tracks.length);
        page = Math.max(1, Math.min(page, totalPages));

        if (action.startsWith("select")) {
            int newSelectedTrack;
            try {
                newSelectedTrack = Integer.parseInt(action.substring(6));
            } catch (NumberFormatException e) {
                event.reply("Invalid track selection.").setEphemeral(true).queue();
                return;
            }
            int tracksOnPage = QueueSlashCmd.getTracksOnPage(page, queueInfo.tracks.length);
            int firstOnPage = (page - 1) * QueueSlashCmd.TRACKS_PER_PAGE + 1;
            int lastOnPage = firstOnPage + tracksOnPage - 1;
            if (newSelectedTrack < firstOnPage || newSelectedTrack > lastOnPage) {
                event.reply("That track isn't on this page!").setEphemeral(true).queue();
                return;
            }
            if (newSelectedTrack == selectedTrack) {
                newSelectedTrack = 0;
            }
            if (newSelectedTrack > queueInfo.tracks.length) {
                event.reply("That track doesn't exist!").setEphemeral(true).queue();
                return;
            }
            updateQueueEmbed(event, queueInfo, page, totalPages, newSelectedTrack, userId);
        } else if (action.equals("prev")) {
            int newPage = Math.max(1, page - 1);
            updateQueueEmbed(event, queueInfo, newPage, totalPages, 0, userId);
        } else if (action.equals("next")) {
            int newPage = Math.min(totalPages, page + 1);
            updateQueueEmbed(event, queueInfo, newPage, totalPages, 0, userId);
        } else if (action.equals("shuffle")) {
            MusicService.OutputAdapter adapter = OutputAdapters.forQueueEmbed(event);
            musicService.shuffle(event.getGuild(), event.getMember(), 0, adapter);
            MusicService.QueueInfo newQueueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
            if (newQueueInfo != null && !newQueueInfo.isEmpty()) {
                int newTotalPages = QueueSlashCmd.getTotalPages(newQueueInfo.tracks.length);
                int safePage = Math.min(page, newTotalPages);
                updateQueueEmbed(event, newQueueInfo, safePage, newTotalPages, 0, userId);
            }
        } else if (action.equals("remove")) {
            if (selectedTrack <= 0 || selectedTrack > queueInfo.tracks.length) {
                event.reply("No track selected!").setEphemeral(true).queue();
                return;
            }
            MusicService.OutputAdapter adapter = OutputAdapters.forQueueEmbed(event);
            musicService.removeTrack(event.getGuild(), event.getMember(), selectedTrack, adapter);
            MusicService.QueueInfo newQueueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
            if (newQueueInfo == null || newQueueInfo.isEmpty()) {
                event.editMessage("The queue is now empty!").setEmbeds().setComponents().queue();
            } else {
                int newTotalPages = QueueSlashCmd.getTotalPages(newQueueInfo.tracks.length);
                int safePage = Math.min(page, newTotalPages);
                updateQueueEmbed(event, newQueueInfo, safePage, newTotalPages, 0, userId);
            }
        } else if (action.equals("playnext")) {
            if (selectedTrack <= 0 || selectedTrack > queueInfo.tracks.length) {
                event.reply("No track selected!").setEphemeral(true).queue();
                return;
            }
            MusicService.OutputAdapter adapter = OutputAdapters.forQueueEmbed(event);
            musicService.playNext(event.getGuild(), event.getMember(), selectedTrack, adapter);
            MusicService.QueueInfo newQueueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
            if (newQueueInfo != null && !newQueueInfo.isEmpty()) {
                int newTotalPages = QueueSlashCmd.getTotalPages(newQueueInfo.tracks.length);
                updateQueueEmbed(event, newQueueInfo, 1, newTotalPages, 0, userId);
            }
        } else if (action.equals("move")) {
            if (selectedTrack <= 0 || selectedTrack > queueInfo.tracks.length) {
                event.reply("No track selected!").setEphemeral(true).queue();
                return;
            }
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("queue_move_select_" + selectedTrack + "_" + page + "_" + userId)
                    .setPlaceholder("Select new position")
                    .setMinValues(1)
                    .setMaxValues(1);
            int maxOptions = Math.min(queueInfo.tracks.length, 25);
            for (int i = 1; i <= maxOptions; i++) {
                if (i != selectedTrack) {
                    menuBuilder.addOption("Position " + i, String.valueOf(i));
                }
            }
            MessageEmbed embed = QueueSlashCmd.buildQueueEmbed(queueInfo, page, totalPages, selectedTrack,
                    event.getMember().getColor());
            event.editMessageEmbeds(embed)
                    .setComponents(ActionRow.of(menuBuilder.build()))
                    .queue();
        } else if (action.equals("playnow")) {
            if (selectedTrack <= 0 || selectedTrack > queueInfo.tracks.length) {
                event.reply("No track selected!").setEphemeral(true).queue();
                return;
            }
            MusicService.OutputAdapter adapter = OutputAdapters.forQueueEmbed(event);
            musicService.playNow(event.getGuild(), event.getMember(), selectedTrack, adapter);
            MusicService.QueueInfo newQueueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
            if (newQueueInfo == null || newQueueInfo.isEmpty()) {
                event.editMessage("The queue is now empty!").setEmbeds().setComponents().queue();
            } else {
                int newTotalPages = QueueSlashCmd.getTotalPages(newQueueInfo.tracks.length);
                updateQueueEmbed(event, newQueueInfo, 1, newTotalPages, 0, userId);
            }
        }
    }

    private void handleQueueMoveSelect(StringSelectInteractionEvent event) {
        if (!InteractionGuards.requireGuildAndMember(event)) {
            return;
        }

        Optional<ComponentIdParsers.QueueMoveSelectId> parsed = ComponentIdParsers.parseQueueMoveSelectId(event.getComponentId());
        if (parsed.isEmpty()) {
            event.reply("Invalid selection state.").setEphemeral(true).queue();
            return;
        }
        ComponentIdParsers.QueueMoveSelectId id = parsed.get();

        if (event.getUser().getIdLong() != id.userId()) {
            event.reply("Only the user who ran the command can use this!").setEphemeral(true).queue();
            return;
        }

        if (!InteractionGuards.requireSameVoiceChannel(event)) {
            return;
        }

        int fromPosition = id.fromPosition();
        int page = id.page();
        long userId = id.userId();

        int toPosition;
        try {
            toPosition = Integer.parseInt(event.getValues().get(0));
        } catch (NumberFormatException e) {
            event.reply("Invalid position selected.").setEphemeral(true).queue();
            return;
        }

        MusicService musicService = bot.getMusicService();
        MusicService.OutputAdapter adapter = OutputAdapters.forQueueMoveSelect(event);

        musicService.moveTrack(event.getGuild(), event.getMember(), fromPosition, toPosition, adapter);

        MusicService.QueueInfo queueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
        if (queueInfo == null || queueInfo.isEmpty()) {
            event.editMessage("The queue is now empty!").setEmbeds().setComponents().queue();
            return;
        }

        int totalPages = QueueSlashCmd.getTotalPages(queueInfo.tracks.length);
        int safePage = Math.min(page, totalPages);
        int tracksOnPage = QueueSlashCmd.getTracksOnPage(safePage, queueInfo.tracks.length);

        MessageEmbed embed = QueueSlashCmd.buildQueueEmbed(queueInfo, safePage, totalPages, 0,
                event.getMember().getColor());
        List<ActionRow> components = QueueSlashCmd.buildQueueComponents(safePage, totalPages, tracksOnPage, 0, userId);

        event.editMessageEmbeds(embed).setComponents(components).queue();
    }

    private void updateQueueEmbed(ButtonInteractionEvent event, MusicService.QueueInfo queueInfo,
                                  int page, int totalPages, int selectedTrack, long userId) {
        int tracksOnPage = QueueSlashCmd.getTracksOnPage(page, queueInfo.tracks.length);
        MessageEmbed embed = QueueSlashCmd.buildQueueEmbed(queueInfo, page, totalPages, selectedTrack,
                event.getMember().getColor());
        List<ActionRow> components = QueueSlashCmd.buildQueueComponents(page, totalPages, tracksOnPage, selectedTrack, userId);
        event.editMessageEmbeds(embed).setComponents(components).queue();
    }
}
