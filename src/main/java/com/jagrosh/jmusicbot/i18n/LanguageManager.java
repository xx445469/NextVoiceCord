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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads translation files and resolves message keys against them.
 *
 * <p>Every language is read once at startup and held in memory. Translation files are small
 * — tens of kilobytes each — and lookups happen on the path of every user-visible message,
 * so trading a little memory for zero I/O per message is worth it.
 *
 * <h2>File format</h2>
 * Translation files are nested JSON, flattened on load into dot-separated keys:
 *
 * <pre>
 *   {"player": {"buttons": {"pause": "Pause"}}}   -&gt;   player.buttons.pause
 * </pre>
 *
 * Nesting is for the humans editing the files; flat keys are for the code reading them. The
 * layout is deliberately identical to Vocard's, so its translation files port across without
 * restructuring.
 *
 * <h2>Placeholders</h2>
 * Arguments are positional: {@code {0}}, {@code {1}}, and so on.
 *
 * <p>{@link java.text.MessageFormat} understands exactly this syntax and is not used anyway,
 * because it also assigns meaning to the apostrophe: {@code don't} silently swallows the
 * following text, and {@code l''utilisateur} is required to render one apostrophe. Translators
 * write natural prose, apostrophes included, and would have no idea why French and English
 * strings started losing their tails. A plain positional substitution has no such trap.
 *
 * <h2>Fallback</h2>
 * Resolution is per key, not per language:
 *
 * <ol>
 *   <li>the requested language</li>
 *   <li>{@link Language#DEFAULT} (English)</li>
 *   <li>the key itself</li>
 * </ol>
 *
 * <p>Per-key fallback is what lets a partially translated language ship: an untranslated
 * string appears in English while everything around it stays translated. Falling back a whole
 * language at a time would mean one missing key reverts the entire interface.
 *
 * <p>Step 3 returns the key rather than a placeholder like "Not found!", which is what Vocard
 * does. A user seeing {@code queue.errors.empty} still learns roughly what happened, and can
 * report something actionable; "Not found!" is indistinguishable from a real message and
 * tells nobody anything.
 *
 * @author adan (xx445469)
 */
public final class LanguageManager
{
    private static final Logger LOG = LoggerFactory.getLogger(LanguageManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<Language, Map<String, String>> translations;
    private final Set<Language> unreviewed;
    private final Language defaultLanguage;

    private LanguageManager(Map<Language, Map<String, String>> translations,
                            Set<Language> unreviewed,
                            Language defaultLanguage)
    {
        this.translations = translations;
        this.unreviewed = unreviewed;
        this.defaultLanguage = defaultLanguage;
    }

    /**
     * Reads every translation file from the classpath.
     *
     * <p>A language whose file is missing or malformed is logged and skipped rather than
     * failing startup: one bad translation file should not take the bot offline when every
     * lookup already falls back to English.
     *
     * @param defaultLanguage language used when none is configured, and the fallback target;
     *        {@code null} resolves to {@link Language#DEFAULT}
     * @throws IllegalStateException if the default language itself fails to load, which
     *         leaves nothing to fall back to
     */
    public static LanguageManager load(Language requestedDefault)
    {
        // Null is tolerated for the same reason an unparseable ui.language is: this module
        // degrades rather than refuses. A missing language setting must not be the thing
        // that stops the bot from starting, and English is always the safe answer.
        Language defaultLanguage = requestedDefault == null ? Language.DEFAULT : requestedDefault;

        Map<Language, Map<String, String>> loaded = new EnumMap<>(Language.class);
        Set<Language> unreviewed = EnumSet.noneOf(Language.class);

        for (Language language : Language.values())
        {
            try
            {
                Map<String, String> entries = readLanguageFile(language);
                if (entries.isEmpty())
                {
                    LOG.warn("Translation file for {} is empty or missing: {}",
                             language, language.getResourcePath());
                    continue;
                }
                loaded.put(language, entries);
                if (!readReviewedFlag(language))
                {
                    unreviewed.add(language);
                }
                LOG.debug("Loaded {} translation keys for {}", entries.size(), language);
            }
            catch (IOException | RuntimeException ex)
            {
                LOG.error("Could not load translations for {} ({}). Falling back to {} for this language.",
                          language, ex.getMessage(), defaultLanguage);
            }
        }

        if (!loaded.containsKey(defaultLanguage))
        {
            throw new IllegalStateException(
                    "The default language " + defaultLanguage + " failed to load from "
                    + defaultLanguage.getResourcePath() + ". Without it there is no fallback, "
                    + "so every message would render as a raw key.");
        }

        reportCoverage(loaded, defaultLanguage);
        reportReviewStatus(unreviewed);
        return new LanguageManager(loaded, unreviewed, defaultLanguage);
    }

    /**
     * Logs how complete each language is against the default.
     *
     * <p>Missing translations are invisible in normal use — that is the point of per-key
     * fallback — so without this, a language that quietly lost half its keys during an edit
     * would look perfectly healthy.
     */
    private static void reportCoverage(Map<Language, Map<String, String>> loaded, Language reference)
    {
        int total = loaded.get(reference).size();
        if (total == 0)
        {
            return;
        }

        for (Map.Entry<Language, Map<String, String>> entry : loaded.entrySet())
        {
            if (entry.getKey() == reference)
            {
                continue;
            }
            int present = (int) loaded.get(reference).keySet().stream()
                                      .filter(entry.getValue()::containsKey)
                                      .count();
            int percent = present * 100 / total;
            if (percent < 100)
            {
                LOG.info("Translations {}: {}% ({}/{} keys; the rest render in {})",
                         entry.getKey(), percent, present, total, reference);
            }
        }
    }

    /**
     * Reads {@code _meta.reviewed} from a translation file.
     *
     * <p>Absent metadata counts as unreviewed. Defaulting the other way would mean a file
     * added without metadata quietly claims to be verified.
     */
    private static boolean readReviewedFlag(Language language)
    {
        try (InputStream in = LanguageManager.class.getClassLoader()
                                                   .getResourceAsStream(language.getResourcePath()))
        {
            if (in == null)
            {
                return false;
            }
            JsonNode meta = MAPPER.readTree(in).path("_meta").path("reviewed");
            return meta.isBoolean() && meta.asBoolean();
        }
        catch (IOException | RuntimeException ex)
        {
            return false;
        }
    }

    /**
     * Warns the operator which languages are machine-generated.
     *
     * <p>An unreviewed translation is indistinguishable from a good one at a glance — it
     * renders fluently and completely. Saying so at startup is the only point where whoever
     * runs the bot finds out before their users do.
     */
    private static void reportReviewStatus(Set<Language> unreviewed)
    {
        if (unreviewed.isEmpty())
        {
            return;
        }
        LOG.warn("Machine-generated, not reviewed by a native speaker: {}. "
                 + "Wording may be unnatural or wrong. Corrections welcome.", unreviewed);
    }

    private static Map<String, String> readLanguageFile(Language language) throws IOException
    {
        try (InputStream in = LanguageManager.class.getClassLoader()
                                                   .getResourceAsStream(language.getResourcePath()))
        {
            if (in == null)
            {
                return Map.of();
            }
            Map<String, String> flat = new HashMap<>();
            flatten(MAPPER.readTree(in), "", flat);
            return Collections.unmodifiableMap(flat);
        }
    }

    /** Collapses nested objects into dot-separated keys. */
    private static void flatten(JsonNode node, String prefix, Map<String, String> out)
    {
        node.fields().forEachRemaining(field ->
        {
            // Underscore-prefixed top-level entries are file metadata, not messages. Keeping
            // them out of the key set matters: otherwise "_meta.reviewed" would count toward
            // coverage and show up as an orphan key in every language whose metadata differs.
            if (prefix.isEmpty() && field.getKey().startsWith("_"))
            {
                return;
            }

            String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
            JsonNode value = field.getValue();
            if (value.isObject())
            {
                flatten(value, key, out);
            }
            else if (value.isValueNode())
            {
                out.put(key, value.asText());
            }
            // Arrays are ignored: no message needs one, and silently joining them would
            // invent formatting the translator never chose.
        });
    }

    /**
     * Resolves {@code key} in {@code language}, substituting positional arguments.
     *
     * @param language  language to resolve in; {@code null} uses the default
     * @param key       dot-separated message key
     * @param arguments values for {@code {0}}, {@code {1}}, ... in order
     * @return the translated string, or the key itself if no language defines it
     */
    public String get(Language language, String key, Object... arguments)
    {
        if (key == null || key.isBlank())
        {
            return "";
        }

        String template = lookup(language == null ? defaultLanguage : language, key);
        return arguments == null || arguments.length == 0 ? template : substitute(template, arguments);
    }

    /**
     * Resolves in the default language.
     *
     * <p>Named rather than overloading {@code get}, because {@code get(Language, String, ...)}
     * and {@code get(String, ...)} are ambiguous the moment a caller passes a literal
     * {@code null} language — and a nullable language is explicitly supported. Use this only
     * where no guild context exists, such as startup messages and console output; anything
     * addressed to a guild should pass that guild's language.
     */
    public String getDefault(String key, Object... arguments)
    {
        return get(defaultLanguage, key, arguments);
    }

    private String lookup(Language language, String key)
    {
        Map<String, String> primary = translations.get(language);
        if (primary != null)
        {
            String value = primary.get(key);
            if (value != null)
            {
                return value;
            }
        }

        if (language != defaultLanguage)
        {
            String fallback = translations.get(defaultLanguage).get(key);
            if (fallback != null)
            {
                return fallback;
            }
        }

        // Logged at warn because an undefined key is a bug in the calling code, not a
        // translation gap: it is absent even from the language that defines everything.
        LOG.warn("No translation for key '{}' in any language, including {}", key, defaultLanguage);
        return key;
    }

    /**
     * Replaces {@code {0}}, {@code {1}}, ... with the given arguments.
     *
     * <p>Scans the template once rather than looping over arguments, so a value that itself
     * contains something like {@code {1}} — a track title can contain anything — is inserted
     * literally instead of being re-scanned as a placeholder.
     *
     * <p>Out-of-range indices are left as written. That surfaces a mismatch between template
     * and call site in the message itself, where it will be noticed, rather than throwing and
     * turning a cosmetic bug into a failed command.
     */
    private static String substitute(String template, Object[] arguments)
    {
        StringBuilder out = new StringBuilder(template.length() + 32);

        for (int i = 0; i < template.length(); i++)
        {
            char c = template.charAt(i);
            if (c != '{')
            {
                out.append(c);
                continue;
            }

            int close = template.indexOf('}', i);
            if (close < 0)
            {
                out.append(template, i, template.length());
                break;
            }

            String inner = template.substring(i + 1, close);
            int index = parseIndex(inner);

            if (index >= 0 && index < arguments.length)
            {
                out.append(arguments[index] == null ? "" : arguments[index].toString());
            }
            else
            {
                out.append('{').append(inner).append('}');
            }
            i = close;
        }
        return out.toString();
    }

    private static int parseIndex(String text)
    {
        if (text.isEmpty() || text.length() > 2)
        {
            return -1;
        }
        for (int i = 0; i < text.length(); i++)
        {
            if (!Character.isDigit(text.charAt(i)))
            {
                return -1;
            }
        }
        return Integer.parseInt(text);
    }

    /**
     * Languages whose translations are machine-generated and unverified.
     *
     * <p>Exposed so the interface can label them, rather than presenting every language as
     * equally trustworthy.
     */
    public Set<Language> getUnreviewedLanguages()
    {
        return Collections.unmodifiableSet(unreviewed);
    }

    /** Whether {@code language} has been reviewed by a native speaker. */
    public boolean isReviewed(Language language)
    {
        return translations.containsKey(language) && !unreviewed.contains(language);
    }

    /** Languages that loaded successfully. */
    public Set<Language> getAvailableLanguages()
    {
        return Collections.unmodifiableSet(translations.keySet());
    }

    /** Language used when none is configured. */
    public Language getDefaultLanguage()
    {
        return defaultLanguage;
    }

    /** Whether {@code language} defines {@code key} itself, without falling back. */
    public boolean hasOwnTranslation(Language language, String key)
    {
        Map<String, String> entries = translations.get(language);
        return entries != null && entries.containsKey(key);
    }

    /** Every key the default language defines, sorted. Used by the coverage test. */
    public Set<String> getAllKeys()
    {
        return Collections.unmodifiableSet(new TreeMap<>(translations.get(defaultLanguage)).keySet());
    }

    /**
     * Every key {@code language} defines itself, sorted.
     *
     * <p>Exists for tooling — coverage reports and the test that hunts orphaned keys. Message
     * lookups should go through {@link #get}, which applies fallback; enumerating a single
     * language's keys and reading them directly would bypass it.
     *
     * @return the language's own keys, or an empty set if it did not load
     */
    public Set<String> getOwnKeys(Language language)
    {
        Map<String, String> entries = translations.get(language);
        return entries == null
                ? Set.of()
                : Collections.unmodifiableSet(new TreeMap<>(entries).keySet());
    }
}
