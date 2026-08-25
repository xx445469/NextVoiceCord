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

import java.util.List;
import java.util.stream.Collectors;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.i18n.Language;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * Sets the display language, for one person or for the whole server.
 *
 * <p>Not an admin-only command. A server-wide language is the wrong unit on its own — servers
 * are not monolingual, and one setting for everyone means somebody is always reading a
 * language they did not choose. Anyone can set their own; changing the server's default still
 * requires Manage Server, because that decides what everyone who has not chosen will see.
 *
 * @author adan (xx445469)
 */
public class LanguageSlashCmd extends SlashCommand
{
    private static final String SCOPE_ME = "me";
    private static final String SCOPE_SERVER = "server";

    private final Bot bot;

    public LanguageSlashCmd(Bot bot)
    {
        this.bot = bot;
        this.name = "language";
        this.help = "sets the language the bot replies to you in";
        this.guildOnly = true;

        OptionData language = new OptionData(OptionType.STRING, "language",
                                             "language to use (leave empty to see the current one)", false);

        // Only languages that actually loaded. Offering one whose file is missing would let
        // someone select a language that then renders entirely in English. Each is labelled in
        // itself, since anyone escaping a language they cannot read has to recognise their own.
        for (Language option : bot.getLanguages().getAvailableLanguages())
        {
            language.addChoice(option.getNativeName() + " (" + option.getEnglishName() + ")", option.name());
        }

        OptionData scope = new OptionData(OptionType.STRING, "scope",
                                          "who this applies to (default: just you)", false)
                .addChoice("Just me", SCOPE_ME)
                .addChoice("Everyone on this server", SCOPE_SERVER);

        this.options = List.of(language, scope);
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    protected void execute(SlashCommandEvent event)
    {
        boolean serverScope = SCOPE_SERVER.equals(
                event.getOption("scope") == null ? SCOPE_ME : event.getOption("scope").getAsString());

        if (event.getOption("language") == null)
        {
            showCurrent(event, serverScope);
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
            reply(event, bot.getConfig().getError() + " "
                  + bot.msgFor(event.getGuild(), event.getUser(),
                               "settings.language.invalid", requested, available), true);
            return;
        }

        if (serverScope)
        {
            setServerLanguage(event, language);
            return;
        }

        bot.getUserLanguages().set(event.getUser().getIdLong(), language);

        // Replied to after the change, so the confirmation arrives in the new language — the
        // shortest possible proof it worked.
        reply(event, bot.getConfig().getSuccess() + " "
              + bot.msgFor(event.getGuild(), event.getUser(),
                           "settings.language.setPersonal", language.getNativeName()), true);
    }

    private void setServerLanguage(SlashCommandEvent event, Language language)
    {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER))
        {
            reply(event, bot.getConfig().getError() + " "
                  + bot.msgFor(event.getGuild(), event.getUser(), "permissions.errors.needManageServer"),
                  true);
            return;
        }

        bot.getSettingsManager().getSettings(event.getGuild()).setLanguage(language);

        reply(event, bot.getConfig().getSuccess() + " "
              + bot.msg(event.getGuild(), "settings.language.set", language.getNativeName()), false);
    }

    private void showCurrent(SlashCommandEvent event, boolean serverScope)
    {
        if (serverScope)
        {
            Language current = bot.getSettingsManager().getSettings(event.getGuild()).getLanguage(bot.getConfig());
            reply(event, bot.msgFor(event.getGuild(), event.getUser(),
                                    "settings.language.current", current.getNativeName()), true);
            return;
        }

        Language personal = bot.getUserLanguages().get(event.getUser().getIdLong()).orElse(null);

        if (personal == null)
        {
            Language inherited = bot.getSettingsManager().getSettings(event.getGuild()).getLanguage(bot.getConfig());
            reply(event, bot.msgFor(event.getGuild(), event.getUser(),
                                    "settings.language.currentInherited", inherited.getNativeName()), true);
            return;
        }

        reply(event, bot.msgFor(event.getGuild(), event.getUser(),
                                "settings.language.currentPersonal", personal.getNativeName()), true);
    }

    /**
     * Replies, ephemerally for anything concerning one person.
     *
     * <p>A personal preference is nobody else's business, and a channel full of "your language
     * is now X" notices is noise for everyone who did not run the command.
     */
    private static void reply(SlashCommandEvent event, String message, boolean ephemeral)
    {
        event.reply(message).setEphemeral(ephemeral).queue();
    }
}
