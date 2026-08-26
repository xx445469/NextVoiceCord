/*
 * Copyright 2026 adan (xx445469) - NextVoiceCord
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

import java.util.Map;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.i18n.CommandLocalizations;

import net.dv8tion.jda.api.interactions.DiscordLocale;

/**
 * Base for every v2 slash command, wiring the per-locale description Discord shows in a user's
 * own client into {@link SlashCommand#getDescriptionLocalization()}.
 *
 * <p>{@code SlashCommand.buildCommandData()} already calls
 * {@link #getDescriptionLocalization()} and, if it returns anything, attaches it via
 * {@code SlashCommandData.setDescriptionLocalizations}; overriding it here is the only wiring
 * every command needs. {@link #getNameLocalization()} is deliberately left at its library
 * default (empty) — see {@link CommandLocalizations} for why command names are not localised.
 *
 * <p>Every direct extender of the library's {@code SlashCommand} in this project
 * ({@code MusicSlashCommand}, {@code AdminSlashCommand}, {@code OwnerSlashCommand}, and the two
 * standalone commands that had no shared base) extends this instead, so the override lives in
 * one place rather than five.
 *
 * @author adan (xx445469)
 */
public abstract class LocalizedSlashCommand extends SlashCommand
{
    protected final Bot bot;

    protected LocalizedSlashCommand(Bot bot)
    {
        this.bot = bot;
    }

    @Override
    public Map<DiscordLocale, String> getDescriptionLocalization()
    {
        return CommandLocalizations.descriptionLocalizations(bot.getLanguages(), this.name, this.help);
    }
}
