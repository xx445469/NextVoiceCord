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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.i18n.LanguageManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LanguageManager")
class LanguageManagerTest
{
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d{1,2})}");

    private static LanguageManager manager;

    @BeforeAll
    static void loadTranslations()
    {
        manager = LanguageManager.load(Language.DEFAULT);
    }

    /** Languages with a translation file present, so absent ones do not fail the suite. */
    static Stream<Language> availableLanguages()
    {
        return manager.getAvailableLanguages().stream()
                      .filter(lang -> lang != Language.DEFAULT);
    }

    @Nested
    @DisplayName("substitution")
    class Substitution
    {
        @Test
        @DisplayName("replaces positional placeholders in order")
        void replacesPlaceholders()
        {
            assertEquals("Volume changed from `10` to `50`",
                         manager.get(Language.EN, "player.volumeChanged", 10, 50));
        }

        @Test
        @DisplayName("keeps apostrophes intact")
        void keepsApostrophes()
        {
            // The reason MessageFormat is not used. Under MessageFormat, "don't" swallows
            // the rest of the string and "{0}" inside quotes stops being a placeholder.
            String rendered = manager.get(Language.EN, "queue.errors.noSongsToRemove");
            assertTrue(rendered.contains("don't"),
                       "Apostrophe was mangled — substitution is treating quotes as syntax: " + rendered);
        }

        @Test
        @DisplayName("inserts argument values literally, without rescanning them")
        void doesNotRescanArguments()
        {
            // Track titles are arbitrary user data and can contain anything, including
            // something that looks like a placeholder. It must not be expanded.
            String rendered = manager.get(Language.EN, "player.skippedTrack", "Song {0} Remix");
            assertEquals("Skipped **Song {0} Remix**", rendered);
        }

        @Test
        @DisplayName("leaves an out-of-range placeholder visible rather than throwing")
        void toleratesMissingArguments()
        {
            // A cosmetic defect is preferable to an exception that fails the whole command.
            String rendered = manager.get(Language.EN, "player.volumeChanged", 10);
            assertTrue(rendered.contains("{1}"), "Expected the unfilled placeholder to survive: " + rendered);
        }

        @Test
        @DisplayName("renders a null argument as empty text")
        void handlesNullArgument()
        {
            assertEquals("Skipped ****", manager.get(Language.EN, "player.skippedTrack", (Object) null));
        }
    }

    @Nested
    @DisplayName("fallback")
    class Fallback
    {
        @Test
        @DisplayName("falls back to English per key, not per language")
        void fallsBackPerKey()
        {
            // Per-key fallback is what allows a partially translated language to ship.
            String key = "player.skipped";
            for (Language language : manager.getAvailableLanguages())
            {
                String rendered = manager.get(language, key);
                assertNotNull(rendered);
                assertFalse(rendered.equals(key),
                            language + " resolved '" + key + "' to the raw key, so even the "
                            + "English fallback failed");
            }
        }

        @Test
        @DisplayName("returns the key itself when nothing defines it")
        void unknownKeyReturnsKey()
        {
            // Returning the key keeps the failure diagnosable. A generic placeholder such as
            // "Not found!" is indistinguishable from a real message and reports nothing.
            String key = "no.such.key.exists";
            assertEquals(key, manager.get(Language.ZHTW, key));
        }

        @Test
        @DisplayName("treats a null language as the default")
        void nullLanguageUsesDefault()
        {
            assertEquals(manager.get(Language.EN, "player.skipped"),
                         manager.get(null, "player.skipped"));
        }
    }

    @Nested
    @DisplayName("translation files")
    class TranslationFiles
    {
        @Test
        @DisplayName("the default language loads and is non-empty")
        void defaultLanguageLoads()
        {
            assertTrue(manager.getAvailableLanguages().contains(Language.DEFAULT));
            assertFalse(manager.getAllKeys().isEmpty());
        }

        @ParameterizedTest(name = "{0} defines no keys absent from English")
        @MethodSource("com.jagrosh.jmusicbot.unit.i18n.LanguageManagerTest#availableLanguages")
        @DisplayName("no orphan keys")
        void noOrphanKeys(Language language)
        {
            // An orphan is dead weight: nothing reads it, and it usually means a key was
            // renamed in English while the translation kept the old spelling — which shows
            // up in production as English text, silently.
            Set<String> english = manager.getAllKeys();
            Set<String> orphans = new TreeSet<>(manager.getOwnKeys(language));
            orphans.removeAll(english);

            assertTrue(orphans.isEmpty(),
                       language + " defines keys that English does not, so they are unreachable "
                       + "and likely renamed upstream: " + orphans);
        }

        @ParameterizedTest(name = "{0} uses the same placeholders as English")
        @MethodSource("com.jagrosh.jmusicbot.unit.i18n.LanguageManagerTest#availableLanguages")
        @DisplayName("placeholder arity matches English")
        void placeholderArityMatches(Language language)
        {
            // The failure this catches is ugly and user-visible: a translation referencing
            // {2} when the call site passes two arguments renders a literal "{2}" in chat.
            // Word order legitimately differs between languages, so the SET of indices is
            // compared, never their order.
            List<String> mismatches = new ArrayList<>();

            for (String key : manager.getAllKeys())
            {
                if (!manager.hasOwnTranslation(language, key))
                {
                    continue;
                }
                Set<Integer> expected = placeholdersIn(manager.get(Language.EN, key));
                Set<Integer> actual = placeholdersIn(manager.get(language, key));

                if (!expected.equals(actual))
                {
                    mismatches.add(key + " — English " + expected + " vs " + language + " " + actual);
                }
            }

            assertTrue(mismatches.isEmpty(),
                       "Placeholder mismatch in " + language + ":\n  " + String.join("\n  ", mismatches));
        }
    }

    private static Set<Integer> placeholdersIn(String template)
    {
        Set<Integer> indices = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find())
        {
            indices.add(Integer.parseInt(matcher.group(1)));
        }
        return indices;
    }

    @Test
    @DisplayName("an untranslated key falls back to English whatever the default language is")
    void fallsBackToEnglishNotToTheConfiguredDefault()
    {
        // The fallback used to target the configured default language, so setting ui.language
        // to anything but English made it fall back to that same language, find nothing, and
        // render the raw key. The per-key fallback this project promises worked only for
        // people who had left the default on English.
        for (Language configured : new Language[] { Language.EN, Language.ZHTW, Language.JA })
        {
            LanguageManager manager = LanguageManager.load(configured);
            String value = manager.get(Language.ZHTW, "gui.nav.overview");
            assertNotEquals("gui.nav.overview", value,
                    "a key present in English must resolve when the default is " + configured);
        }
    }
}
