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
package com.jagrosh.jmusicbot.unit.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagrosh.jmusicbot.gui.GuiPreferences;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the edit {@link GuiPreferences} makes to config.txt.
 *
 * <p>The panel writes a value into a file full of someone's other settings and their comments.
 * A regex that matches one character too far damages a working configuration, and nothing in
 * the window would show it — the setting would appear to have been accepted.
 *
 * @author adan (xx445469)
 */
class GuiPreferencesTest
{
    private static final String CONFIG = """
            # NextVoiceCord configuration

            ui {
              # Not the window's language.
              theme = "classic"
            }

            gui {
              # If true, the GUI will be enabled.
              enabled = true

              # Theme for the GUI window: light, dark, darcula, intellij
              theme = "dark"

              # Base font size for GUI components (8-24)
              fontSize = 12
            }

            playback {
              volume = 100
            }
            """;

    @Test
    @DisplayName("replaces the value and nothing else")
    void replacesOnlyTheValue()
    {
        String result = GuiPreferences.apply(CONFIG, "gui", "theme", "\"light\"");

        assertTrue(result.contains("theme = \"light\""), "the new value should be written");
        assertTrue(result.contains("# Theme for the GUI window: light, dark, darcula, intellij"),
                   "comments explaining the option must survive");
        assertTrue(result.contains("fontSize = 12"), "neighbouring keys must survive");
        assertTrue(result.contains("volume = 100"), "later sections must survive");
    }

    @Test
    @DisplayName("does not write into a same-named key in a different section")
    void staysInsideItsSection()
    {
        // ui.theme comes first in the file and holds the same key name. A pattern that scans
        // for `theme =` without anchoring to the section would overwrite the wrong one, and
        // the GUI theme would appear not to save at all.
        String result = GuiPreferences.apply(CONFIG, "gui", "theme", "\"light\"");

        assertTrue(result.contains("theme = \"classic\""), "ui.theme must be left alone");
        assertEquals(1, count(result, "\"light\""), "exactly one value should have changed");
    }

    @Test
    @DisplayName("adds the key when an older config predates the option")
    void addsMissingKey()
    {
        String result = GuiPreferences.apply(CONFIG, "gui", "language", "\"ZHTW\"");

        assertTrue(result.contains("language = \"ZHTW\""), "the key should be added");
        assertTrue(result.contains("theme = \"dark\""), "existing keys must survive");
        assertTrue(result.indexOf("language = \"ZHTW\"") > result.indexOf("gui {"),
                   "the key belongs inside the gui section");
        assertTrue(result.indexOf("language = \"ZHTW\"") < result.indexOf("playback {"),
                   "and before the section ends");
    }

    @Test
    @DisplayName("replaces an empty value rather than adding a duplicate key")
    void replacesEmptyValue()
    {
        // language = "" is what the shipped reference config carries. Treating an empty value
        // as "key not present" would append a second `language` line every time it changed.
        String config = CONFIG.replace("fontSize = 12", "fontSize = 12\n  language = \"\"");
        String result = GuiPreferences.apply(config, "gui", "language", "\"JA\"");

        assertEquals(1, count(result, "language ="), "the key should not be duplicated");
        assertTrue(result.contains("language = \"JA\""));
    }

    @Test
    @DisplayName("leaves the file untouched when the section is absent")
    void ignoresMissingSection()
    {
        String withoutGui = "playback {\n  volume = 100\n}\n";
        assertEquals(withoutGui, GuiPreferences.apply(withoutGui, "gui", "theme", "\"light\""));
    }

    @Test
    @DisplayName("writes a numeric value unquoted")
    void writesNumbersUnquoted()
    {
        String result = GuiPreferences.apply(CONFIG, "gui", "fontSize", "16");

        assertTrue(result.contains("fontSize = 16"));
        assertTrue(!result.contains("fontSize = \"16\""), "a quoted int would fail to parse as one");
    }

    private static int count(String haystack, String needle)
    {
        int found = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1))
        {
            found++;
        }
        return found;
    }
}
