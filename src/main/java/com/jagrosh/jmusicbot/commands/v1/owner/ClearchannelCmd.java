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
package com.jagrosh.jmusicbot.commands.v1.owner;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.OwnerCommand;
import com.jagrosh.jmusicbot.utils.ChannelClearHelper;

/**
 * Owner-only prefix command to clear all messages in the guild's configured text channel (set via settc).
 */
public class ClearchannelCmd extends OwnerCommand
{
    private final Bot bot;

    public ClearchannelCmd(Bot bot)
    {
        this.bot = bot;
        this.name = "clearchannel";
        this.help = "clears all messages in the command channel set with settc";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = true;
    }

    @Override
    protected void execute(CommandEvent event)
    {
        ChannelClearHelper.ClearPolicy clearPolicy = ChannelClearHelper.ClearPolicy.of(
                bot.getConfig().getClearChannelDeleteLimit(),
                bot.getConfig().getClearChannelAgeDays()
        );
        ChannelClearHelper.clearConfiguredTextChannel(
                event.getGuild(),
                event.getClient().getSettingsFor(event.getGuild()),
                clearPolicy,
                new ChannelClearHelper.ClearChannelCallback()
                {
                    @Override
                    public void onNoChannelConfigured()
                    {
                        event.reply(event.getClient().getError() + " " + bot.msg(event.getGuild(), "clearchannel.errors.noChannelConfigured", "settc"));
                    }

                    @Override
                    public void onNoPermission()
                    {
                        event.reply(event.getClient().getError() + " " + bot.msg(event.getGuild(), "permissions.errors.noManageMessages"));
                    }

                    @Override
                    public void onClearingStarted()
                    {
                        event.reply(event.getClient().getWarning() + " " + bot.msg(event.getGuild(), "clearchannel.clearing"));
                    }

                    @Override
                    public void onCleared(int count)
                    {
                        event.getChannel().sendMessage(event.getClient().getSuccess() + " " + bot.msg(event.getGuild(), "clearchannel.cleared", count)).queue();
                    }

                    @Override
                    public void onError(Throwable t)
                    {
                        String msg = t.getMessage() != null ? t.getMessage() : t.toString();
                        event.getChannel().sendMessage(event.getClient().getError() + " " + bot.msg(event.getGuild(), "clearchannel.error", msg)).queue();
                    }
                });
    }
}
