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
package com.jagrosh.jmusicbot.commands.v2;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;

/**
 * Base class for owner-only slash commands.
 * Only the bot owner can use these commands (no MANAGE_SERVER fallback).
 */
public abstract class OwnerSlashCommand extends SlashCommand
{
    protected final Bot bot;

    public OwnerSlashCommand(Bot bot)
    {
        this.bot = bot;
        this.guildOnly = true;
        this.category = new Category("Owner");
    }

    @Override
    protected void execute(SlashCommandEvent event)
    {
        if (!event.getUser().getId().equals(event.getClient().getOwnerId()))
        {
            event.reply(event.getClient().getError() + " Only the bot owner can use this command!")
                    .setEphemeral(true).queue();
            return;
        }

        doOwnerCommand(event);
    }

    /**
     * Override this method to implement the owner command logic.
     */
    public abstract void doOwnerCommand(SlashCommandEvent event);
}
