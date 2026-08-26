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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.jagrosh.jmusicbot.config.io.ConfigIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores purely cosmetic, per-window Swing state that is not a bot setting and so does not
 * belong in {@code config.txt}.
 *
 * <p>The config panel's collapsed/expanded advanced-section state used to be written into
 * {@code config.txt} as {@code gui.configPanelAdvancedSections}, on the same write-immediately
 * path {@link GuiPreferences} still uses for theme/font/language. Those are different in kind:
 * they are real, documented {@link com.jagrosh.jmusicbot.config.model.ConfigOption} entries
 * with defaults in {@code reference.conf}. The advanced-section state was not — nobody ever
 * registered it as an option — which meant
 * {@link com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics} correctly flagged it as an
 * unknown key on every subsequent load. That triggered a config repair, which writes a fresh
 * {@code config.txt.bakN} backup — and because the panel wrote the key straight back on the
 * very next section toggle, the cycle repeated on every restart, leaving a trail of backups
 * behind for something that was never bot configuration in the first place.
 *
 * <p>The state lives instead in a small sidecar file next to {@code config.txt} (same
 * directory, same base name, {@code .gui-state} appended) — never parsed as config, never
 * touched by diagnostics or repair, and never something a {@code cat config.txt} or a
 * "share this file for troubleshooting" accidentally includes. A plain OS-level preferences
 * store (e.g. {@link java.util.prefs.Preferences}) was considered and rejected: it is scoped to
 * the OS user account, not to a particular {@code config.txt}, so two bot installations for two
 * different Discord bots running as the same OS user would silently share one "which sections
 * are open" setting instead of each remembering its own — the sidecar file avoids that because
 * it lives beside the specific config it describes.
 *
 * @author adan (xx445469)
 */
public final class GuiWindowState
{
    private static final Logger LOG = LoggerFactory.getLogger(GuiWindowState.class);

    /** Appended to config.txt's own file name to get the sidecar's path. */
    private static final String SUFFIX = ".gui-state";
    // The panel's section keys are fixed identifiers ("proxy", "dangerous", ...) that never
    // contain a comma, so a plain comma is a safe, human-readable join/split separator.
    private static final String SEPARATOR = ",";

    private GuiWindowState() { }

    /** The sidecar file's path, or {@code null} if there is no config file to sit beside. */
    private static Path statePath()
    {
        Path configPath = ConfigIO.getConfigPath();
        if (configPath == null)
        {
            return null;
        }
        return configPath.resolveSibling(configPath.getFileName().toString() + SUFFIX);
    }

    /** Which advanced sections were left open last session. Never throws. */
    public static Set<String> loadExpandedAdvancedSections()
    {
        Set<String> result = new LinkedHashSet<>();
        try
        {
            Path path = statePath();
            if (path == null || !Files.exists(path))
            {
                return result;
            }
            String raw = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (!raw.isEmpty())
            {
                for (String key : raw.split(SEPARATOR))
                {
                    if (!key.isBlank())
                    {
                        result.add(key);
                    }
                }
            }
        }
        catch (IOException | RuntimeException ex)
        {
            LOG.warn("Could not read saved advanced-section expand state: {}", ex.toString());
        }
        return result;
    }

    /**
     * Persists which advanced sections are open.
     *
     * <p>Runs on every toggle rather than waiting for a Save button, the same reasoning
     * {@link GuiPreferences} documents for its own fields: opening a section is not something
     * anyone thinks of as "a config edit" they need to remember to save.
     */
    public static void saveExpandedAdvancedSections(Set<String> expanded)
    {
        try
        {
            Path path = statePath();
            if (path == null)
            {
                LOG.warn("No config file to derive a GUI state path from; "
                        + "not persisting advanced-section expand state.");
                return;
            }
            Files.writeString(path, String.join(SEPARATOR, expanded), StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException ex)
        {
            // Logged, never thrown — losing a display preference should not interrupt someone
            // who is in the middle of changing it.
            LOG.warn("Could not save advanced-section expand state: {}", ex.toString());
        }
    }
}
