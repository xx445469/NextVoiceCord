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
import java.util.regex.Pattern;

import net.dv8tion.jda.api.interactions.DiscordLocale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds Discord's per-locale slash-command name and description maps from this project's
 * translation files.
 *
 * <p>Command <em>descriptions</em> carry no format risk: any text is legal there, so a
 * translation either fits the length limit or falls back to English. Command <em>names</em> are
 * different — Discord accepts a wide range of scripts (measured against JDA 6.4.2: CJK, Cyrillic
 * and accented Latin all register fine) but is strict about form: lowercase only, no spaces,
 * 1-32 characters, and nothing outside {@code ^[-_\p{L}\p{N}\p{sc=Deva}\p{sc=Thai}]{1,32}$}. A
 * single rejected name fails the whole command registration, not just that command, so every
 * translated name is validated here and anything that fails degrades to the English name rather
 * than ever reaching Discord.
 *
 * <h2>Source of text</h2>
 * Neither is read from {@code SlashCommand.name}/{@code help} for every language — those fields
 * are the single English source and stay that way. Translations live in each language's
 * {@code commands.<name>.name} and {@code commands.<name>.description} keys, populated (English
 * only, so far) from the existing command names and {@code help} strings. A language missing a
 * key, or whose translation fails validation (name) or overflows the limit (description), simply
 * renders in English for that locale — the same thing that already happens for an unlocalised
 * command today.
 *
 * @author adan (xx445469)
 */
public final class CommandLocalizations
{
    private static final Logger LOG = LoggerFactory.getLogger(CommandLocalizations.class);

    /** Discord rejects a slash-command description longer than this, for every locale. */
    public static final int MAX_DESCRIPTION_LENGTH = 100;

    /** Discord rejects a slash-command name longer than this, for every locale. */
    public static final int MAX_NAME_LENGTH = 32;

    /**
     * Discord's own character-class rule for a command name:
     * {@code ^[-_\p{L}\p{N}\p{sc=Deva}\p{sc=Thai}]{1,32}$}. Java's {@link Pattern} understands
     * {@code \p{sc=...}} script properties directly, so this is the exact rule, not an
     * approximation of it.
     *
     * <p>This alone does not reject uppercase Latin — {@code \p{L}} matches both cases, but
     * Discord (via JDA's client-side check) additionally requires the whole name be lowercase.
     * That is checked separately in {@link #isValidName}, since non-cased scripts such as CJK
     * have no case to violate.
     */
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[-_\\p{L}\\p{N}\\p{sc=Deva}\\p{sc=Thai}]{1,32}$");

    private CommandLocalizations()
    {
    }

    /**
     * Whether {@code name} is a name Discord will accept for a slash command (or subcommand /
     * option) in any locale.
     *
     * <p>Enforces, in order: non-null and non-empty, at most {@value #MAX_NAME_LENGTH}
     * characters, every character drawn from {@code -_\p{L}\p{N}} plus the Devanagari and Thai
     * scripts, and no uppercase letter anywhere in it. Passing this does not by itself prove
     * Discord's live API will accept the name — only that it clears every rule this project has
     * been able to confirm from JDA's own client-side validation.
     *
     * @param name candidate localised (or English) command name
     * @return {@code true} if the name is safe to attach as a localisation
     */
    public static boolean isValidName(String name)
    {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH)
        {
            return false;
        }
        if (!NAME_PATTERN.matcher(name).matches())
        {
            return false;
        }
        for (int i = 0; i < name.length(); i++)
        {
            if (Character.isUpperCase(name.charAt(i)))
            {
                return false;
            }
        }
        return true;
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

    /**
     * Resolves the name Discord should show a client in {@code locale} for {@code commandName},
     * given the raw text found (if any) at {@code commands.<commandName>.name}.
     *
     * <p>Exists apart from {@link #nameLocalizations} so the fallback and validation rules can
     * be tested directly, without needing a loaded {@link LanguageManager} or real translation
     * files to exercise the missing-key and invalid-name cases.
     *
     * <p>A translation that fails {@link #isValidName} is logged at {@code WARN} naming both the
     * command and the locale it would have applied to — that is the one case an over-eager
     * translation could take the whole command set offline at registration, so it is the one
     * case worth a loud log line rather than a silent fallback.
     *
     * @param translation the language's own text for the key, or {@code null}/blank if it does
     *                    not define one
     * @param englishName the command's registered English name — the fallback of last resort,
     *                    and always itself a valid Discord name
     * @param commandName the command's registered name, used only for the warning log
     * @param locale      the locale this name would have applied to, used only for the warning log
     * @return {@code translation} if it is present and valid; {@code englishName} otherwise
     */
    public static String resolveName(String translation, String englishName, String commandName, DiscordLocale locale)
    {
        if (translation == null || translation.isBlank())
        {
            return englishName;
        }
        if (!isValidName(translation))
        {
            LOG.warn("Translated name '{}' for command '{}' ({}) is not a valid Discord slash-command name "
                     + "(must be 1-{} characters, lowercase, no spaces); falling back to English name '{}'",
                     translation, commandName, locale, MAX_NAME_LENGTH, englishName);
            return englishName;
        }
        return translation;
    }

    /**
     * Builds the {@link DiscordLocale} to name map for one slash command, for every language
     * this project has a Discord locale for.
     *
     * <p>English itself is left out: the command's base name already is the English text, so a
     * redundant {@code ENGLISH_US} entry adds nothing. A language with no Discord locale (see
     * {@link Language#getDiscordLocale()}) is skipped entirely rather than guessed. Every
     * translation is validated through {@link #resolveName} before it goes in the map, so this
     * method can never hand back a name that would fail Discord's registration.
     *
     * @param languages   loaded translations to read {@code commands.<commandName>.name} from
     * @param commandName the slash command's registered name, e.g. {@code "play"} — also used
     *                    as the English fallback, since a command's name and its English text
     *                    are the same thing
     * @param englishName the command's registered English name, used as the fallback for any
     *                    language missing the key or whose translation fails validation
     * @return locale to name map, ready for {@code SlashCommandData.setNameLocalizations}
     */
    public static Map<DiscordLocale, String> nameLocalizations(LanguageManager languages,
                                                                 String commandName,
                                                                 String englishName)
    {
        Map<DiscordLocale, String> result = new EnumMap<>(DiscordLocale.class);
        if (languages == null || commandName == null || englishName == null)
        {
            return result;
        }

        String key = "commands." + commandName + ".name";

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
            result.put(locale.get(), resolveName(translation, englishName, commandName, locale.get()));
        }

        return result;
    }
}
