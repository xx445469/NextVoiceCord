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
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jagrosh.jmusicbot.utils.OtherUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-user language preferences.
 *
 * <p>A guild-wide language is the wrong unit on its own. Servers are not monolingual — a
 * Taiwanese server has members who read English, and an English server has members who would
 * rather read Japanese — and one setting for everyone means somebody is always reading a
 * language they did not choose.
 *
 * <p>Kept separate from guild settings rather than folded into them, because the two are
 * scoped differently: a guild setting belongs to a server and is edited by its admins, while
 * this belongs to a person and follows them into every server the bot shares with them.
 *
 * <p>Resolution order is most specific first: the user's own choice, then the server's, then
 * the global default. That way a server can pick a sensible default for its members without
 * overriding anyone who has stated a preference.
 *
 * @author adan (xx445469)
 */
public final class UserLanguageStore
{
    private static final Logger LOG = LoggerFactory.getLogger(UserLanguageStore.class);
    private static final String FILE = "userlanguages.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Concurrent because commands run on JDA's threads while the file is written on another. */
    private final Map<Long, Language> preferences = new ConcurrentHashMap<>();

    public UserLanguageStore()
    {
        load();
    }

    /** This user's chosen language, or empty if they have not chosen one. */
    public Optional<Language> get(long userId)
    {
        return Optional.ofNullable(preferences.get(userId));
    }

    /**
     * Records a choice.
     *
     * @param language the language, or {@code null} to go back to following the server
     */
    public void set(long userId, Language language)
    {
        if (language == null)
        {
            preferences.remove(userId);
        }
        else
        {
            preferences.put(userId, language);
        }
        save();
    }

    private void load()
    {
        try
        {
            JsonNode root = MAPPER.readTree(Files.readAllBytes(OtherUtil.getPath(FILE)));
            if (!root.isObject())
            {
                return;
            }

            root.fields().forEachRemaining(entry ->
            {
                try
                {
                    // An unreadable entry is skipped rather than aborting the load: one
                    // hand-edited line should not cost everyone else their preference.
                    Language.fromCode(entry.getValue().asText())
                            .ifPresent(language ->
                                    preferences.put(Long.parseLong(entry.getKey()), language));
                }
                catch (NumberFormatException ignored)
                {
                    LOG.warn("Skipping malformed user id in {}: {}", FILE, entry.getKey());
                }
            });

            LOG.info("Loaded {} user language preference(s)", preferences.size());
        }
        catch (NoSuchFileException ex)
        {
            // Expected until someone sets one. Creating the file eagerly would just leave an
            // empty file next to the bot for no reason.
        }
        catch (IOException | RuntimeException ex)
        {
            LOG.warn("Could not read {}: {}. Starting with no user preferences.", FILE, ex.toString());
        }
    }

    private void save()
    {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        preferences.forEach((userId, language) -> root.put(String.valueOf(userId), language.name()));

        try
        {
            Files.write(OtherUtil.getPath(FILE),
                        MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
        }
        catch (IOException ex)
        {
            // Logged, not thrown. Failing to persist a display preference should not fail the
            // command the user was running.
            LOG.warn("Could not save {}: {}", FILE, ex.toString());
        }
    }
}
