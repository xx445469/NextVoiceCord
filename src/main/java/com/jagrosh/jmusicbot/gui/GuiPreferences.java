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
package com.jagrosh.jmusicbot.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jagrosh.jmusicbot.config.io.ConfigIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists the window's own settings.
 *
 * <p>They were never saved. Theme, font size and language applied immediately and were gone
 * on the next start — the Preferences panel even carried a line admitting it. A setting that
 * does not survive a restart is not really a setting; it is a temporary override that looks
 * like one, which is worse than not offering it.
 *
 * <p>Written back into {@code config.txt} rather than a separate preferences store, because
 * {@code gui.theme} and {@code gui.fontSize} already exist there. A second store would mean
 * two files disagreeing about the same value, and no obvious answer to which one wins.
 *
 * <p>Each change is written on its own rather than batched behind a Save button. These are
 * single-choice settings applied the moment they are picked; a button that has to be pressed
 * afterwards to keep what is already visible is a step people skip.
 *
 * @author adan (xx445469)
 */
public final class GuiPreferences
{
    private static final Logger LOG = LoggerFactory.getLogger(GuiPreferences.class);

    private GuiPreferences() { }

    /** Stores the GUI theme. */
    public static void saveTheme(String theme)
    {
        write("gui", "theme", quote(theme));
    }

    /** Stores the base font size. */
    public static void saveFontSize(int size)
    {
        write("gui", "fontSize", String.valueOf(size));
    }

    /** Stores the window's display language. */
    public static void saveLanguage(String languageCode)
    {
        write("gui", "language", quote(languageCode));
    }

    /**
     * Updates one key in {@code config.txt}, leaving the rest of the file untouched.
     *
     * <p>Edited as text rather than parsed and rewritten, because the file is full of
     * comments explaining every option and a round trip through a config library would
     * discard all of them.
     */
    private static void write(String section, String key, String value)
    {
        try
        {
            Path path = ConfigIO.getConfigPath();
            if (path == null || !Files.exists(path))
            {
                LOG.warn("No config file to save {}.{} into.", section, key);
                return;
            }

            String content = Files.readString(path);
            String updated = apply(content, section, key, value);

            if (!updated.equals(content))
            {
                ConfigIO.writeConfigFile(path, updated);
                LOG.debug("Saved {}.{}", section, key);
            }
        }
        catch (IOException | RuntimeException ex)
        {
            // Logged, never thrown. Failing to persist a display preference should not
            // interrupt someone who is in the middle of changing it.
            LOG.warn("Could not save {}.{}: {}", section, key, ex.toString());
        }
    }

    /**
     * Returns {@code content} with {@code section.key} set to {@code value}.
     *
     * <p>Pure, and public so it can be tested against real config files. The regex is the part
     * that can quietly do the wrong thing — write into the wrong section, match a key that only
     * shares a suffix — and that is not visible from the panel.
     */
    public static String apply(String content, String section, String key, String value)
    {
        String updated = replaceKey(content, section, key, value);

        // The key is absent — an older config predating the option. Appending is better than
        // silently doing nothing, which is what the panel used to do.
        return updated.equals(content) ? appendToSection(content, section, key, value) : updated;
    }

    /** Replaces {@code key} inside {@code section}, if both exist. */
    private static String replaceKey(String content, String section, String key, String value)
    {
        // [^}]*? cannot cross the closing brace, so the search stays inside the section: a
        // `theme` key in some later block is not a candidate. The newline before the key stops
        // `theme` from matching the tail of a longer name like `accentTheme`.
        Pattern pattern = Pattern.compile(
                "(?s)(\\b" + Pattern.quote(section) + "\\s*\\{[^}]*?[\r\n][ \t]*"
                + Pattern.quote(key) + "\\s*=\\s*)([^\r\n]*)");

        Matcher matcher = pattern.matcher(content);
        return matcher.find()
                ? matcher.replaceFirst("$1" + Matcher.quoteReplacement(value))
                : content;
    }

    /** Adds {@code key} to an existing section that does not yet contain it. */
    private static String appendToSection(String content, String section, String key, String value)
    {
        Pattern pattern = Pattern.compile("(?m)^([ \t]*\\b" + Pattern.quote(section) + "\\s*\\{)");
        Matcher matcher = pattern.matcher(content);

        return matcher.find()
                ? matcher.replaceFirst("$1\n  " + Matcher.quoteReplacement(key + " = " + value))
                : content;
    }

    private static String quote(String value)
    {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
