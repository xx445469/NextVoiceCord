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
package com.jagrosh.jmusicbot.i18n;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import net.dv8tion.jda.api.interactions.DiscordLocale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds Discord's per-locale slash-command description map from this project's translation
 * files.
 *
 * <p>Slash command <em>names</em> are deliberately left alone. Discord requires a lowercase,
 * 1-32 character, space-free name, and rejects names outright for scripts it decides not to
 * accept — verifying that for eleven scripts against Discord's live validation is its own
 * project, and getting even one wrong takes the whole command set down at registration. Command
 * <em>descriptions</em> carry none of that risk: any text is legal there, so they are the
 * localisation that is safe to ship.
 *
 * <h2>Source of text</h2>
 * Descriptions are not read from {@code SlashCommand.help} for every language — that field is
 * still the single English source and stays that way. Translations live in each language's
 * {@code commands.<name>.description} key, populated (English only, so far) from the existing
 * {@code help} strings. A language missing that key, or whose translation would not fit
 * Discord's 100-character description limit, simply renders in English for that locale — the
 * same thing that already happens for an unlocalised command today.
 *
 * @author adan (xx445469)
 */
public final class CommandLocalizations
{
    private static final Logger LOG = LoggerFactory.getLogger(CommandLocalizations.class);

    /** Discord rejects a slash-command description longer than this, for every locale. */
    public static final int MAX_DESCRIPTION_LENGTH = 100;

    private CommandLocalizations()
    {
    }

    /**
     * Resolves the description Discord should show a client in {@code language} for
     * {@code commandName}, given the raw text found (if any) at
     * {@code commands.<commandName>.description}.
     *
     * <p>Exists apart from {@link #descriptionLocalizations} so the fallback and length rules
     * can be tested directly, without needing a loaded {@link LanguageManager} or real
     * translation files to exercise the missing-key and overflow cases.
     *
     * @param translation   the language's own text for the key, or {@code null}/blank if it
     *                      does not define one
     * @param englishHelp   the command's English {@code help} field — the fallback of last resort
     * @return {@code translation} if it is present and fits the limit; {@code englishHelp}
     *         otherwise
     */
    public static String resolveDescription(String translation, String englishHelp)
    {
        if (translation == null || translation.isBlank())
        {
            return englishHelp;
        }
        if (translation.length() > MAX_DESCRIPTION_LENGTH)
        {
            LOG.warn("Translated description for '{}' is {} characters (max {}); falling back to English: {}",
                     englishHelp, translation.length(), MAX_DESCRIPTION_LENGTH, translation);
            return englishHelp;
        }
        return translation;
    }

    /**
     * Builds the {@link DiscordLocale} to description map for one slash command, for every
     * language this project has a Discord locale for.
     *
     * <p>English itself is left out: the command's base description already is the English
     * text, so a redundant {@code ENGLISH_US} entry adds nothing. A language with no Discord
     * locale (see {@link Language#getDiscordLocale()}) is skipped entirely rather than guessed.
     *
     * @param languages   loaded translations to read {@code commands.<commandName>.description} from
     * @param commandName the slash command's registered name, e.g. {@code "play"}
     * @param englishHelp the command's English {@code help} field, used as the fallback for any
     *                    language missing the key or whose translation overflows the limit
     * @return locale to description map, ready for {@code SlashCommandData.setDescriptionLocalizations}
     */
    public static Map<DiscordLocale, String> descriptionLocalizations(LanguageManager languages,
                                                                        String commandName,
                                                                        String englishHelp)
    {
        Map<DiscordLocale, String> result = new EnumMap<>(DiscordLocale.class);
        if (languages == null || commandName == null || englishHelp == null)
        {
            return result;
        }

        String key = "commands." + commandName + ".description";

        for (Language language : Language.values())
        {
            if (language == Language.EN)
            {
                continue;
            }

            Optional<DiscordLocale> locale = language.getDiscordLocale();
            if (locale.isEmpty())
            {
                continue;
            }

            String translation = languages.hasOwnTranslation(language, key)
                    ? languages.get(language, key)
                    : null;
            result.put(locale.get(), resolveDescription(translation, englishHelp));
        }

        return result;
    }
}
