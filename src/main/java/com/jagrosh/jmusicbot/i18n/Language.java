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

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import net.dv8tion.jda.api.interactions.DiscordLocale;

/**
 * The languages NextVoiceCord ships translations for.
 *
 * <p>Codes follow the translation files this project inherits from Vocard, so a file can be
 * carried across without renaming. They are not BCP 47 tags: {@code ZHTW}, not {@code zh-TW}.
 *
 * <p>Each entry also carries the {@link DiscordLocale} Discord uses for the same language.
 * Discord's own locale list is the constraint on slash-command localisation, and it does not
 * line up with this list — {@code VN} is one example Discord has no counterpart for — so the
 * mapping is {@link Optional} rather than assumed.
 *
 * @author adan (xx445469)
 */
public enum Language
{
    EN("English",    "English",   DiscordLocale.ENGLISH_US),
    DE("German",     "Deutsch",   DiscordLocale.GERMAN),
    ES("Spanish",    "Español",   DiscordLocale.SPANISH),
    FR("French",     "Français",  DiscordLocale.FRENCH),
    JA("Japanese",   "日本語",     DiscordLocale.JAPANESE),
    KO("Korean",     "한국어",      DiscordLocale.KOREAN),
    PL("Polish",     "Polski",    DiscordLocale.POLISH),
    RU("Russian",    "Русский",   DiscordLocale.RUSSIAN),
    UA("Ukrainian",  "Українська", DiscordLocale.UKRAINIAN),
    VN("Vietnamese", "Tiếng Việt", DiscordLocale.VIETNAMESE),
    ZHCN("Chinese (Simplified)",  "简体中文", DiscordLocale.CHINESE_CHINA),
    ZHTW("Chinese (Traditional)", "繁體中文", DiscordLocale.CHINESE_TAIWAN);

    /** The language every lookup falls back to, and the only one guaranteed complete. */
    public static final Language DEFAULT = EN;

    private final String englishName;
    private final String nativeName;
    private final DiscordLocale discordLocale;

    Language(String englishName, String nativeName, DiscordLocale discordLocale)
    {
        this.englishName = englishName;
        this.nativeName = nativeName;
        this.discordLocale = discordLocale;
    }

    /** English name, for logs and English-language documentation. */
    public String getEnglishName()
    {
        return englishName;
    }

    /**
     * The language's name in itself — "日本語", not "Japanese".
     *
     * <p>Language pickers should show this. Someone who needs to switch away from a language
     * they cannot read has to recognise their own language's name to escape.
     */
    public String getNativeName()
    {
        return nativeName;
    }

    /** The matching Discord locale, or empty if Discord does not offer this language. */
    public Optional<DiscordLocale> getDiscordLocale()
    {
        return Optional.ofNullable(discordLocale);
    }

    /** Resource file this language loads from, relative to the classpath root. */
    public String getResourcePath()
    {
        return "langs/" + name() + ".json";
    }

    /**
     * Resolves a language code, accepting the spellings people actually type.
     *
     * <p>{@code zh-TW}, {@code zh_tw} and {@code ZHTW} all mean the same thing to a user, so
     * separators are stripped before matching.
     */
    public static Optional<Language> fromCode(String code)
    {
        if (code == null || code.isBlank())
        {
            return Optional.empty();
        }
        String normalised = code.replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                     .filter(lang -> lang.name().equals(normalised))
                     .findFirst();
    }

    /**
     * Best language for a Discord locale, so a user's Discord setting can seed a sensible
     * default before anyone configures one.
     */
    public static Optional<Language> fromDiscordLocale(DiscordLocale locale)
    {
        if (locale == null)
        {
            return Optional.empty();
        }
        return Arrays.stream(values())
                     .filter(lang -> locale.equals(lang.discordLocale))
                     .findFirst();
    }
}
