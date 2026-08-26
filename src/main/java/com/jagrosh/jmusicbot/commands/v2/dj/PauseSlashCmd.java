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
package com.jagrosh.jmusicbot.commands.v2.dj;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.DJSlashCommand;
import com.jagrosh.jmusicbot.service.MusicService;

/**
 * Slash command to pause the current song.
 */
public class PauseSlashCmd extends DJSlashCommand
{
    private final MusicService musicService;

    public PauseSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "pause";
        this.help = "pauses the current song";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
        this.lavalinkStageOneSupported = true;
    }

    @Override
    public void doDJCommand(SlashCommandEvent event)
    {
        // Routed through MusicService (rather than reading AudioHandler directly, as this used
        // to) so the lavalink-mode branch inside MusicService.isPaused/setPaused applies here too
        // - see the "Lavalink boundary" comment in MusicService.pause/setPaused.
        if (musicService.isPaused(event.getGuild()))
        {
            event.reply(event.getClient().getWarning() + " " + bot.msg(event.getGuild(), "player.pauseAlready", "/play"))
                    .setEphemeral(true).queue();
            return;
        }

        String trackTitle = musicService.setPaused(event.getGuild(), true);
        event.reply(event.getClient().getSuccess() + " " + bot.msg(event.getGuild(), "player.paused", trackTitle, "/play")).queue();
    }
}
