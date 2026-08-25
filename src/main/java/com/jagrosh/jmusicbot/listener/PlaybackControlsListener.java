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
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.listener.interaction.OutputAdapters;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Handles playback control button interactions (stop, pause, skip, previous, shuffle, repeat, favorite, volume).
 */
public class PlaybackControlsListener extends ListenerAdapter {

    private final Bot bot;

    public PlaybackControlsListener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        var parsedId = ComponentIdParsers.parseNowPlayingButtonId(event.getComponentId());
        if (parsedId.isEmpty()) {
            return;
        }

        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }

        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler == null) {
            event.reply("There is no music playing!").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().getVoiceState().inAudioChannel()
                || !event.getMember().getVoiceState().getChannel().equals(event.getGuild().getSelfMember().getVoiceState().getChannel())) {
            event.reply("You must be in the same voice channel to use this!").setEphemeral(true).queue();
            return;
        }

        MusicService musicService = bot.getMusicService();

        switch (parsedId.get().action()) {
            case "previous":
                musicService.previous(event.getGuild(), event.getMember(),
                        suppressSuccess(OutputAdapters.forPlaybackButton(event)));
                break;
            case "shuffle":
                musicService.shuffle(event.getGuild(), event.getMember(), 0, OutputAdapters.forPlaybackButton(event));
                break;
            case "repeat":
                musicService.cycleRepeatMode(event.getGuild(), event.getMember(), OutputAdapters.forPlaybackButton(event));
                break;
            case "favorite":
                musicService.addCurrentTrackToFavorites(event.getGuild(), event.getMember(), OutputAdapters.forFavoritePlaybackButton(event));
                break;
            case "voldown":
                musicService.adjustVolume(event.getGuild(), event.getMember(), -10, OutputAdapters.forPlaybackButton(event));
                break;
            case "volup":
                musicService.adjustVolume(event.getGuild(), event.getMember(), 10, OutputAdapters.forPlaybackButton(event));
                break;
            case "stop":
                musicService.stop(event.getGuild(), event.getMember(), OutputAdapters.forPlaybackButton(event));
                break;
            case "pause":
                musicService.pause(event.getGuild(), event.getMember(), OutputAdapters.forPlaybackButton(event));
                break;
            case "skip":
                musicService.skip(event.getGuild(), event.getMember(),
                        suppressSuccess(OutputAdapters.forPlaybackButton(event)));
                break;
            default:
                break;
        }
    }

    private static MusicService.OutputAdapter suppressSuccess(MusicService.OutputAdapter delegate) {
        return new MusicService.OutputAdapter() {
            @Override
            public void replySuccess(String content) {
                // Intentionally silent for targeted now-playing actions.
            }

            @Override
            public void replyError(String content) {
                delegate.replyError(content);
            }

            @Override
            public void replyWarning(String content) {
                delegate.replyWarning(content);
            }

            @Override
            public void editMessage(String content) {
                delegate.editMessage(content);
            }

            @Override
            public void editMessage(String content, java.util.function.Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) {
                delegate.editMessage(content, onSuccess);
            }

            @Override
            public void editNowPlaying(AudioHandler handler) {
                delegate.editNowPlaying(handler);
            }

            @Override
            public void editNoMusic(AudioHandler handler) {
                delegate.editNoMusic(handler);
            }

            @Override
            public void onShowHelp() {
                delegate.onShowHelp();
            }
        };
    }
}
