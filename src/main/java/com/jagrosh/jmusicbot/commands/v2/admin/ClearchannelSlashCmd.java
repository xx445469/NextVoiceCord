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
package com.jagrosh.jmusicbot.commands.v2.admin;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.OwnerSlashCommand;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.ChannelClearHelper;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * Owner-only slash command to clear all messages in the guild's configured text channel (set via /settc).
 */
public class ClearchannelSlashCmd extends OwnerSlashCommand
{
    public ClearchannelSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "clearchannel";
        this.help = "clears all messages in the command channel set with /settc (owner only)";
    }

    @Override
    public void doOwnerCommand(SlashCommandEvent event)
    {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        TextChannel channel = settings.getTextChannel(event.getGuild());
        ChannelClearHelper.ClearPolicy clearPolicy = ChannelClearHelper.ClearPolicy.of(
                bot.getConfig().getClearChannelDeleteLimit(),
                bot.getConfig().getClearChannelAgeDays()
        );

        if (channel == null)
        {
            event.reply(event.getClient().getError() + " No text channel is configured for this server. An admin can set one with `/settc`.")
                    .setEphemeral(true).queue();
            return;
        }

        if (!event.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE))
        {
            event.reply(event.getClient().getError() + " I don't have permission to manage messages in that channel.")
                    .setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue(hook ->
                ChannelClearHelper.purgeChannel(channel, new ChannelClearHelper.PurgeCallback()
                {
                    @Override
                    public void onCleared(int count)
                    {
                        hook.editOriginal("Cleared " + count + " messages.").queue();
                    }

                    @Override
                    public void onError(Throwable t)
                    {
                        String msg = t.getMessage() != null ? t.getMessage() : t.toString();
                        hook.editOriginal(event.getClient().getError() + " Error: " + msg).queue();
                    }
                }, clearPolicy));
    }
}
