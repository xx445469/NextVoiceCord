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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
