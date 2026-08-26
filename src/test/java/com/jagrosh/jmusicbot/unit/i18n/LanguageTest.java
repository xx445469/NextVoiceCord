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

import java.util.Optional;

import com.jagrosh.jmusicbot.i18n.Language;

import net.dv8tion.jda.api.interactions.DiscordLocale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link Language}'s mapping onto {@link DiscordLocale}, which slash-command
 * localisation is built on ({@link com.jagrosh.jmusicbot.i18n.CommandLocalizations}).
 */
@DisplayName("Language -> DiscordLocale mapping")
class LanguageTest
{
    @Test
    @DisplayName("every project language maps to the Discord locale of the same language")
    void mapsToMatchingDiscordLocale()
    {
        assertEquals(DiscordLocale.ENGLISH_US, Language.EN.getDiscordLocale().orElseThrow());
        assertEquals(DiscordLocale.GERMAN, Language.DE.getDiscordLocale().orElseThrow());
        assertEquals(DiscordLocale.SPANISH, Language.ES.getDiscordLocale().orElseThrow());
        assertEquals(DiscordLocale.FRENCH, Language.FR.getDiscordLocale().orElseThrow());
        assertEquals(DiscordLocale.JAPANESE, Language.JA.getDiscordLocale().orElseThrow());
        assertEquals(DiscordLocale.KOREAN, Language.KO.getDiscordLocale().orElseThrow());
        assertEquals(DiscordLocale.POLISH, Language.PL.getDiscordLocale().orElseThrow());
        assertEquals(DiscordLocale.RUSSIAN, Language.RU.getDiscordLocale().orElseThrow());
        assertEquals(DiscordLocale.UKRAINIAN, Language.UA.getDiscordLocale().orElseThrow());
        assertEquals(DiscordLocale.VIETNAMESE, Language.VN.getDiscordLocale().orElseThrow());
        assertEquals(DiscordLocale.CHINESE_CHINA, Language.ZHCN.getDiscordLocale().orElseThrow());
        assertEquals(DiscordLocale.CHINESE_TAIWAN, Language.ZHTW.getDiscordLocale().orElseThrow());
    }

    @Test
    @DisplayName("Chinese variants map to distinct Discord locales, not the same one")
    void chineseVariantsAreDistinct()
    {
        // The easiest way for this mapping to silently break: ZHCN and ZHTW both resolving to
        // the same DiscordLocale, so one variant's translations quietly overwrite the other's.
        DiscordLocale simplified = Language.ZHCN.getDiscordLocale().orElseThrow();
        DiscordLocale traditional = Language.ZHTW.getDiscordLocale().orElseThrow();
        assertTrue(simplified != traditional,
                   "ZHCN and ZHTW must not map to the same DiscordLocale: both were " + simplified);
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    @DisplayName("every language maps to a Discord locale (this JDA version has one for all 12)")
    void everyLanguageHasADiscordLocale(Language language)
    {
        // Not a hard guarantee of the API in general — DiscordLocale coverage is a Discord
        // decision, and Language#getDiscordLocale is Optional for exactly this reason — but
        // for the JDA version currently pinned in pom.xml, every language this project ships
        // translations for happens to have one. If upgrading JDA ever removes one, this is the
        // test that should start failing instead of registration silently guessing.
        Optional<DiscordLocale> locale = language.getDiscordLocale();
        assertTrue(locale.isPresent(), language + " has no mapped DiscordLocale");
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    @DisplayName("fromDiscordLocale round-trips getDiscordLocale for every language")
    void discordLocaleRoundTrips(Language language)
    {
        DiscordLocale locale = language.getDiscordLocale().orElseThrow();
        assertEquals(Optional.of(language), Language.fromDiscordLocale(locale));
    }

    @Test
    @DisplayName("an unmapped or null Discord locale resolves to no language")
    void unmappedDiscordLocaleIsEmpty()
    {
        assertEquals(Optional.empty(), Language.fromDiscordLocale(DiscordLocale.THAI));
        assertEquals(Optional.empty(), Language.fromDiscordLocale(null));
    }
}
