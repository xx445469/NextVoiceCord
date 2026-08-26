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
package com.jagrosh.jmusicbot.unit.i18n;

import java.util.Map;

import com.jagrosh.jmusicbot.i18n.CommandLocalizations;
import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.i18n.LanguageManager;

import net.dv8tion.jda.api.interactions.DiscordLocale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CommandLocalizations")
class CommandLocalizationsTest
{
    private static LanguageManager languages;

    @BeforeAll
    static void loadTranslations()
    {
        languages = LanguageManager.load(Language.DEFAULT);
    }

    @Nested
    @DisplayName("resolveDescription")
    class Resolve
    {
        @Test
        @DisplayName("uses the translation when present and within the 100-character limit")
        void usesTranslationWhenValid()
        {
            String result = CommandLocalizations.resolveDescription("joue le morceau demandé", "plays the provided song");
            assertEquals("joue le morceau demandé", result);
        }

        @Test
        @DisplayName("falls back to English when no translation exists (null)")
        void fallsBackWhenMissing()
        {
            String result = CommandLocalizations.resolveDescription(null, "plays the provided song");
            assertEquals("plays the provided song", result);
        }

        @Test
        @DisplayName("falls back to English when the translation is blank")
        void fallsBackWhenBlank()
        {
            String result = CommandLocalizations.resolveDescription("   ", "plays the provided song");
            assertEquals("plays the provided song", result);
        }

        @Test
        @DisplayName("accepts a translation exactly at the 100-character limit")
        void acceptsExactlyAtLimit()
        {
            String exactly100 = "a".repeat(100);
            assertEquals(100, exactly100.length());
            String result = CommandLocalizations.resolveDescription(exactly100, "fallback");
            assertEquals(exactly100, result);
        }

        @Test
        @DisplayName("falls back to English when the translation overflows 100 characters, rather than registering an invalid command")
        void fallsBackWhenOverLimit()
        {
            String tooLong = "a".repeat(101);
            String result = CommandLocalizations.resolveDescription(tooLong, "plays the provided song");
            assertEquals("plays the provided song", result,
                         "An overflowing translation must fall back to English instead of being sent to Discord, "
                         + "which would reject the whole command set at registration");
        }
    }

    @Nested
    @DisplayName("descriptionLocalizations")
    class Map_
    {
        @Test
        @DisplayName("never includes English — the base description already is English")
        void excludesEnglish()
        {
            Map<DiscordLocale, String> result =
                    CommandLocalizations.descriptionLocalizations(languages, "play", "plays the provided song");
            assertFalse(result.containsKey(DiscordLocale.ENGLISH_US));
        }

        @Test
        @DisplayName("covers every language that has a mapped Discord locale")
        void coversEveryMappedLanguage()
        {
            long mappedNonEnglish = java.util.Arrays.stream(Language.values())
                    .filter(l -> l != Language.EN)
                    .filter(l -> l.getDiscordLocale().isPresent())
                    .count();

            Map<DiscordLocale, String> result =
                    CommandLocalizations.descriptionLocalizations(languages, "play", "plays the provided song");

            assertEquals(mappedNonEnglish, result.size());
        }

        @Test
        @DisplayName("a command with no key in any language falls back to English for every locale, rather than throwing")
        void unknownCommandFallsBackEverywhere()
        {
            Map<DiscordLocale, String> result = CommandLocalizations.descriptionLocalizations(
                    languages, "no-such-command", "this is the english fallback text");

            assertFalse(result.isEmpty());
            result.values().forEach(description ->
                    assertEquals("this is the english fallback text", description));
        }

        @Test
        @DisplayName("a null LanguageManager, command name, or English fallback yields an empty map rather than throwing")
        void nullInputsAreSafe()
        {
            assertTrue(CommandLocalizations.descriptionLocalizations(null, "play", "help").isEmpty());
            assertTrue(CommandLocalizations.descriptionLocalizations(languages, null, "help").isEmpty());
            assertTrue(CommandLocalizations.descriptionLocalizations(languages, "play", null).isEmpty());
        }
    }

    @Nested
    @DisplayName("isValidName")
    class ValidName
    {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"播放", "再生", "재생", "играть", "écouter",
                                                                    "play", "queue_type", "set-dj", "a"})
        @DisplayName("accepts lowercase names in scripts Discord is known to accept, including hyphen/underscore")
        void acceptsValidNames(String name)
        {
            assertTrue(CommandLocalizations.isValidName(name), "expected '" + name + "' to be a valid name");
        }

        @Test
        @DisplayName("rejects a name containing uppercase Latin letters")
        void rejectsUppercase()
        {
            assertFalse(CommandLocalizations.isValidName("Abspielen"));
        }

        @Test
        @DisplayName("rejects a name containing a space")
        void rejectsSpace()
        {
            assertFalse(CommandLocalizations.isValidName("play song"));
        }

        @Test
        @DisplayName("rejects an empty name")
        void rejectsEmpty()
        {
            assertFalse(CommandLocalizations.isValidName(""));
        }

        @Test
        @DisplayName("rejects a null name")
        void rejectsNull()
        {
            assertFalse(CommandLocalizations.isValidName(null));
        }

        @Test
        @DisplayName("rejects a name over 32 characters")
        void rejectsTooLong()
        {
            String thirtyThree = "a".repeat(33);
            assertEquals(33, thirtyThree.length());
            assertFalse(CommandLocalizations.isValidName(thirtyThree));
        }

        @Test
        @DisplayName("accepts a name exactly at the 32-character limit")
        void acceptsExactlyAtLimit()
        {
            String thirtyTwo = "a".repeat(32);
            assertEquals(32, thirtyTwo.length());
            assertTrue(CommandLocalizations.isValidName(thirtyTwo));
        }
    }

    @Nested
    @DisplayName("resolveName")
    class ResolveName
    {
        @Test
        @DisplayName("uses the translation when present and valid")
        void usesTranslationWhenValid()
        {
            String result = CommandLocalizations.resolveName("播放", "play", "play", DiscordLocale.CHINESE_TAIWAN);
            assertEquals("播放", result);
        }

        @Test
        @DisplayName("falls back to English when no translation exists (null)")
        void fallsBackWhenMissing()
        {
            String result = CommandLocalizations.resolveName(null, "play", "play", DiscordLocale.GERMAN);
            assertEquals("play", result);
        }

        @Test
        @DisplayName("falls back to English when the translation is blank")
        void fallsBackWhenBlank()
        {
            String result = CommandLocalizations.resolveName("   ", "play", "play", DiscordLocale.GERMAN);
            assertEquals("play", result);
        }

        @Test
        @DisplayName("falls back to English rather than throwing when the translation is uppercase")
        void fallsBackOnUppercase()
        {
            String result = CommandLocalizations.resolveName("Abspielen", "play", "play", DiscordLocale.GERMAN);
            assertEquals("play", result,
                         "An uppercase translated name must fall back to English instead of being sent to "
                         + "Discord, which would reject the whole command set at registration");
        }

        @Test
        @DisplayName("falls back to English rather than throwing when the translation contains a space")
        void fallsBackOnSpace()
        {
            String result = CommandLocalizations.resolveName("play song", "play", "play", DiscordLocale.ENGLISH_UK);
            assertEquals("play", result);
        }

        @Test
        @DisplayName("falls back to English rather than throwing when the translation is over 32 characters")
        void fallsBackOnTooLong()
        {
            String tooLong = "a".repeat(33);
            String result = CommandLocalizations.resolveName(tooLong, "play", "play", DiscordLocale.GERMAN);
            assertEquals("play", result);
        }

        @Test
        @DisplayName("never throws for any of the rejection cases, always returning the English name")
        void neverThrows()
        {
            String[] invalid = {"Abspielen", "play song", "", "a".repeat(33)};
            for (String candidate : invalid)
            {
                assertEquals("play",
                             CommandLocalizations.resolveName(candidate, "play", "play", DiscordLocale.GERMAN));
            }
        }
    }

    @Nested
    @DisplayName("nameLocalizations")
    class NameMap
    {
        @Test
        @DisplayName("never includes English — the base name already is English")
        void excludesEnglish()
        {
            Map<DiscordLocale, String> result =
                    CommandLocalizations.nameLocalizations(languages, "play", "play");
            assertFalse(result.containsKey(DiscordLocale.ENGLISH_US));
        }

        @Test
        @DisplayName("covers every language that has a mapped Discord locale")
        void coversEveryMappedLanguage()
        {
            long mappedNonEnglish = java.util.Arrays.stream(Language.values())
                    .filter(l -> l != Language.EN)
                    .filter(l -> l.getDiscordLocale().isPresent())
                    .count();

            Map<DiscordLocale, String> result =
                    CommandLocalizations.nameLocalizations(languages, "play", "play");

            assertEquals(mappedNonEnglish, result.size());
        }

        @Test
        @DisplayName("every value produced is itself a valid Discord name, ready to attach without further checking")
        void everyValueIsValid()
        {
            Map<DiscordLocale, String> result =
                    CommandLocalizations.nameLocalizations(languages, "play", "play");

            result.forEach((locale, name) ->
                    assertTrue(CommandLocalizations.isValidName(name),
                               "locale " + locale + " produced invalid name '" + name + "'"));
        }

        @Test
        @DisplayName("a command with no key in any language falls back to English for every locale, rather than throwing")
        void unknownCommandFallsBackEverywhere()
        {
            Map<DiscordLocale, String> result =
                    CommandLocalizations.nameLocalizations(languages, "no-such-command", "no-such-command");

            assertFalse(result.isEmpty());
            result.values().forEach(name -> assertEquals("no-such-command", name));
        }

        @Test
        @DisplayName("a null LanguageManager, command name, or English fallback yields an empty map rather than throwing")
        void nullInputsAreSafe()
        {
            assertTrue(CommandLocalizations.nameLocalizations(null, "play", "play").isEmpty());
            assertTrue(CommandLocalizations.nameLocalizations(languages, null, "play").isEmpty());
            assertTrue(CommandLocalizations.nameLocalizations(languages, "play", null).isEmpty());
        }
    }

    @Nested
    @DisplayName("end-to-end: a real SlashCommandData built with these localisations")
    class EndToEnd
    {
        @Test
        @DisplayName("attaching a CJK name localisation to a real SlashCommandData builds without throwing")
        void cjkNameLocalizationBuildsWithoutThrowing()
        {
            // Built through resolveName (not nameLocalizations against the loaded language
            // files) because no language file carries a commands.play.name translation yet —
            // that population is a separate translation pass. This exercises exactly what
            // production code will do once a translation exists: validate, then hand the result
            // straight to JDA.
            Map<DiscordLocale, String> names = new java.util.EnumMap<>(DiscordLocale.class);
            names.put(DiscordLocale.CHINESE_TAIWAN,
                      CommandLocalizations.resolveName("播放", "play", "play", DiscordLocale.CHINESE_TAIWAN));
            names.put(DiscordLocale.JAPANESE,
                      CommandLocalizations.resolveName("再生", "play", "play", DiscordLocale.JAPANESE));
            names.put(DiscordLocale.KOREAN,
                      CommandLocalizations.resolveName("재생", "play", "play", DiscordLocale.KOREAN));
            names.put(DiscordLocale.RUSSIAN,
                      CommandLocalizations.resolveName("играть", "play", "play", DiscordLocale.RUSSIAN));
            names.put(DiscordLocale.FRENCH,
                      CommandLocalizations.resolveName("écouter", "play", "play", DiscordLocale.FRENCH));
            // A rejected translation mixed in alongside the valid ones, to prove the fallback
            // path degrades this one locale rather than ever reaching JDA with the bad value.
            names.put(DiscordLocale.GERMAN,
                      CommandLocalizations.resolveName("Abspielen", "play", "play", DiscordLocale.GERMAN));

            net.dv8tion.jda.api.interactions.commands.build.SlashCommandData data =
                    assertDoesNotThrow(() -> net.dv8tion.jda.api.interactions.commands.build.Commands
                            .slash("play", "plays the provided song")
                            .setNameLocalizations(names),
                            "a validated name localisation map must never fail JDA's own client-side checks");

            assertEquals("play", data.getName());
            Map<DiscordLocale, String> built = data.getNameLocalizations().toMap();
            assertEquals("播放", built.get(DiscordLocale.CHINESE_TAIWAN));
            assertEquals("再生", built.get(DiscordLocale.JAPANESE));
            assertEquals("재생", built.get(DiscordLocale.KOREAN));
            assertEquals("играть", built.get(DiscordLocale.RUSSIAN));
            assertEquals("écouter", built.get(DiscordLocale.FRENCH));
            // The rejected "Abspielen" must have degraded to the English name before it ever
            // reached JDA — this is the fallback path actually being exercised, not merely
            // written.
            assertEquals("play", built.get(DiscordLocale.GERMAN));
        }

        @Test
        @DisplayName("directly attaching a rejected name to SlashCommandData throws — proving what the validator guards against")
        void rejectedNameThrowsWhenAttachedDirectly()
        {
            Map<DiscordLocale, String> invalidNames = new java.util.EnumMap<>(DiscordLocale.class);
            invalidNames.put(DiscordLocale.GERMAN, "Abspielen");

            assertThrows(IllegalArgumentException.class, () ->
                    net.dv8tion.jda.api.interactions.commands.build.Commands
                            .slash("play", "plays the provided song")
                            .setNameLocalizations(invalidNames));
        }
    }
}
