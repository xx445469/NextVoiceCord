/*
 * Copyright 2017 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.commands.v1.general;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.admin.SettingsPanelRenderer;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

/**
 * Shows server settings. Users with Manage Server (or the bot owner) receive
 * the interactive settings panel; others see a read-only summary.
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SettingsCmd extends Command
{
    private final static String EMOJI = "\uD83C\uDFA7"; // 🎧

    private final Bot bot;

    public SettingsCmd(Bot bot)
    {
        this.bot = bot;
        this.name = "settings";
        this.help = "shows the bots settings (interactive panel for admins)";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = true;
    }

    @Override
    protected void execute(CommandEvent event)
    {
        Settings s = event.getClient().getSettingsFor(event.getGuild());
        boolean canManage = event.getMember() != null && event.getMember().hasPermission(Permission.MANAGE_SERVER)
                || event.getAuthor().getId().equals(event.getClient().getOwnerId());

        if (canManage)
        {
            long userId = event.getAuthor().getIdLong();
            String invokerName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getAuthor().getName();
            event.getChannel()
                    .sendMessage(new MessageCreateBuilder()
                            .setComponents(SettingsPanelRenderer.buildSettingsMessageComponents(
                                    event.getGuild(), s, bot.getConfig(), userId, invokerName))
                            .useComponentsV2()
                            .build())
                    .queue();
            return;
        }

        MessageCreateBuilder builder = new MessageCreateBuilder()
                .addContent(EMOJI + " **")
                .addContent(FormatUtil.filter(event.getSelfUser().getName()))
                .addContent("** settings:");
        TextChannel tchan = s.getTextChannel(event.getGuild());
        VoiceChannel vchan = s.getVoiceChannel(event.getGuild());
        Role role = s.getRole(event.getGuild());
        EmbedBuilder ebuilder = new EmbedBuilder()
                .setColor(event.getSelfMember().getColors().getPrimary())
                .setDescription("Text Channel: " + (tchan == null ? "Any" : "**#" + tchan.getName() + "**")
                        + "\nVoice Channel: " + (vchan == null ? "Any" : vchan.getAsMention())
                        + "\nDJ Role: " + (role == null ? "None" : "**" + role.getName() + "**")
                        + "\nCustom Prefix: " + (s.getPrefix() == null ? "None" : "`" + s.getPrefix() + "`")
                        + "\nRepeat Mode: " + (s.getRepeatMode() == RepeatMode.OFF
                                ? s.getRepeatMode().getUserFriendlyName()
                                : "**" + s.getRepeatMode().getUserFriendlyName() + "**")
                        + "\nQueue Type: " + (s.getQueueType() == QueueType.FAIR
                                ? s.getQueueType().getUserFriendlyName()
                                : "**" + s.getQueueType().getUserFriendlyName() + "**")
                        + "\nDefault Playlist: " + (s.getDefaultPlaylist() == null ? "None" : "**" + s.getDefaultPlaylist() + "**")
                )
                .setFooter(event.getJDA().getGuilds().size() + " servers | "
                        + event.getJDA().getGuilds().stream().filter(g -> g.getSelfMember().getVoiceState().getChannel() != null).count()
                        + " audio connections", null);
        event.getChannel().sendMessage(builder.setEmbeds(ebuilder.build()).build()).queue();
    }
}
