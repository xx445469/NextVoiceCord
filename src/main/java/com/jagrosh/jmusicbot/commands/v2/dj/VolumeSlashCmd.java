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
import com.jagrosh.jmusicbot.utils.FormatUtil;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

/**
 * Slash command to get or set the player volume.
 */
public class VolumeSlashCmd extends DJSlashCommand
{
    private final MusicService musicService;

    public VolumeSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "volume";
        this.help = "sets or shows the player volume";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.options = Collections.singletonList(
                new OptionData(OptionType.INTEGER, "level", "Volume level (0-150)", false)
                        .setMinValue(0)
                        .setMaxValue(150)
        );
        this.lavalinkStageOneSupported = true;
    }

    @Override
    public void doDJCommand(SlashCommandEvent event)
    {
        // Routed through MusicService (rather than reading AudioHandler directly, as this used
        // to) so the lavalink-mode branch inside MusicService.getVolume/setVolume applies here
        // too - see the "Lavalink boundary" comment there.
        int currentVolume = musicService.getVolume(event.getGuild());

        if (event.getOption("level") == null)
        {
            // Show current volume
            event.reply(FormatUtil.volumeIcon(currentVolume) + " " + bot.msg(event.getGuild(), "player.volumeCurrent", currentVolume)).queue();
        }
        else
        {
            int newVolume = (int) event.getOption("level").getAsLong();
            MusicService.VolumeResult result = musicService.setVolume(event.getGuild(), newVolume);
            if (result == null)
            {
                event.reply(event.getClient().getError() + " " + bot.msg(event.getGuild(), "player.errors.volumeInvalid"))
                        .setEphemeral(true).queue();
                return;
            }
            event.reply(FormatUtil.volumeIcon(result.newVolume) + " " + bot.msg(event.getGuild(), "player.volumeChanged", result.oldVolume, result.newVolume)).queue();
        }
    }
}
