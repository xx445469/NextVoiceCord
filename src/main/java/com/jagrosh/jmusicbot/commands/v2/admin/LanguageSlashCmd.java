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
package com.jagrosh.jmusicbot.commands.v2.admin;

import java.util.Collections;
import java.util.stream.Collectors;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.AdminSlashCommand;
import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.settings.Settings;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * Sets the bot's language for this server.
 *
 * @author adan (xx445469)
 */
public class LanguageSlashCmd extends AdminSlashCommand
{
    public LanguageSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "language";
        this.help = "sets the bot language for this server";

        OptionData option = new OptionData(OptionType.STRING, "language",
                                           "language to use (leave empty to show the current one)", false);

        // Choices are limited to languages that actually loaded. Offering one whose file is
        // missing would let a user select a language that silently renders entirely in
        // English. Each is labelled in itself — someone who needs to switch away from a
        // language they cannot read has to recognise their own language's name to escape.
        for (Language language : bot.getLanguages().getAvailableLanguages())
        {
            option.addChoice(language.getNativeName() + " (" + language.getEnglishName() + ")",
                             language.name());
        }

        this.options = Collections.singletonList(option);
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doAdminCommand(SlashCommandEvent event)
    {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());

        if (event.getOption("language") == null)
        {
            Language current = settings.getLanguage(bot.getConfig());
            event.reply(bot.msg(event.getGuild(), "settings.language.current",
                                current.getNativeName())).queue();
            return;
        }

        String requested = event.getOption("language").getAsString();
        Language language = Language.fromCode(requested).orElse(null);

        if (language == null || !bot.getLanguages().getAvailableLanguages().contains(language))
        {
            // Reachable despite the choice list: an alias route or a stale client can still
            // deliver an arbitrary string.
            String available = bot.getLanguages().getAvailableLanguages().stream()
                                  .map(Language::name)
                                  .collect(Collectors.joining(", "));
            event.reply(event.getClient().getError() + " "
                        + bot.msg(event.getGuild(), "settings.language.invalid", requested, available))
                 .setEphemeral(true).queue();
            return;
        }

        settings.setLanguage(language);

        // Replied to after the change, so the confirmation itself arrives in the new
        // language — immediate proof the switch took effect.
        event.reply(event.getClient().getSuccess() + " "
                    + bot.msg(event.getGuild(), "settings.language.set", language.getNativeName()))
             .queue();
    }
}
