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
package com.jagrosh.jmusicbot.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.config.model.ConfigOption;
import com.jagrosh.jmusicbot.gui.GuiPreferences;
import com.jagrosh.jmusicbot.i18n.Language;

import net.dv8tion.jda.api.entities.Guild;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything the panel can change, and the checks each change goes through first.
 *
 * <p>Separated from {@link WebPanel} because the reasoning is different in kind. That class
 * decides who is allowed to speak; this one decides whether what they said is something the bot
 * should do. Keeping them apart means a change to routing cannot quietly loosen a validation,
 * and the rules below can be read without wading through HTTP.
 *
 * <p>Every accepted write is logged with the address it came from. The token identifies nobody —
 * it is one shared string — so the address is the only account of who did what there is, and a
 * config that changed with no record of it changing is the thing an operator cannot recover from.
 *
 * @author adan (xx445469)
 */
final class WebWrites
{
    private static final Logger LOG = LoggerFactory.getLogger(WebWrites.class);

    /**
     * Keys the panel refuses to write regardless of anything else.
     *
     * <p>{@code discord.token} is not here to protect the token's confidentiality — it is never
     * sent, so it cannot be read back. It is here because overwriting it stops the bot from
     * starting, and a browser tab is the wrong place to do that by accident. The web keys are
     * excluded because a panel that can move its own bind address or grant itself write access
     * is a panel with no meaningful restrictions at all.
     */
    private static final java.util.Set<String> NEVER_WRITABLE = java.util.Set.of(
            "discord.token",
            "web.bindAddress",
            "web.allowConfigEdit");

    private final Bot bot;

    /**
     * Whether this run has already taken a copy of config.txt.
     *
     * <p>Once, not per write. The desktop panel backs up on every save because a save there is
     * one deliberate press of a button; here a single page can send a dozen changed fields, and
     * a dozen backups of a file containing the Discord token is a worse outcome than none. One
     * copy of what the file looked like before the panel first touched it is what someone
     * actually needs to undo a mistake.
     */
    private volatile boolean backedUp;

    WebWrites(Bot bot)
    {
        this.bot = bot;
    }

    /** Copies config.txt aside, the first time this run is about to change it. */
    private synchronized void backUpOnce()
    {
        if (backedUp)
        {
            return;
        }
        backedUp = true;
        try
        {
            java.nio.file.Path config = com.jagrosh.jmusicbot.config.io.ConfigIO.getConfigPath();
            if (config == null || !java.nio.file.Files.exists(config))
            {
                return;
            }
            java.nio.file.Path backup =
                    com.jagrosh.jmusicbot.config.update.ConfigUpdater.findAvailableBackupPath(config);
            java.nio.file.Files.copy(config, backup);
            LOG.info("Web panel: copied config.txt to {} before its first change this run.",
                     backup.getFileName());
        }
        catch (java.io.IOException | RuntimeException ex)
        {
            // Not fatal. A missing backup is worth saying out loud, but refusing an edit the
            // operator asked for because the copy failed helps nobody.
            LOG.warn("Web panel: could not back up config.txt first: {}", ex.toString());
        }
    }

    // ==================== config.txt ====================

    Result applyConfig(Map<String, String> updates, String from)
    {
        List<String> written = new ArrayList<>();
        List<String> refused = new ArrayList<>();

        for (Map.Entry<String, String> entry : updates.entrySet())
        {
            String key = entry.getKey();
            String value = entry.getValue();

            if (NEVER_WRITABLE.contains(key))
            {
                refused.add(key + " (not editable from the panel)");
                continue;
            }

            // The panel was sent a mask for this and has handed it straight back. Writing it
            // would replace a working credential with a row of dots, which is exactly what
            // opening the editor and pressing save would otherwise do.
            if (WebSecrets.isUnchangedMask(value))
            {
                continue;
            }

            ConfigOption option = optionFor(key);
            if (option == null)
            {
                refused.add(key + " (unknown setting)");
                continue;
            }

            String problem = validate(option, value);
            if (problem != null)
            {
                refused.add(key + " (" + problem + ")");
                continue;
            }

            backUpOnce();

            if (!GuiPreferences.write(sectionOf(key), leafOf(key), format(option, value)))
            {
                refused.add(key + " (could not be written)");
                continue;
            }
            written.add(key);
        }

        if (!written.isEmpty())
        {
            // Values deliberately not logged: some of these are secrets, and a log line is a
            // second place a credential can leak from.
            LOG.warn("Web panel: {} changed {} in config.txt: {}", from, written.size(), written);
        }
        if (!refused.isEmpty())
        {
            LOG.info("Web panel: {} sent {} change(s) that were refused: {}", from, refused.size(), refused);
        }

        return new Result(refused.isEmpty(), written, refused,
                          written.isEmpty() ? "Nothing changed." : "Saved. Most settings need a restart.");
    }

    // ==================== Desktop preferences ====================

    Result applyPreferences(Map<String, String> updates, String from)
    {
        List<String> written = new ArrayList<>();
        List<String> refused = new ArrayList<>();

        for (Map.Entry<String, String> entry : updates.entrySet())
        {
            switch (entry.getKey())
            {
                case "language" -> {
                    Language language = Language.fromCode(entry.getValue()).orElse(null);
                    if (language == null || !bot.getLanguages().getAvailableLanguages().contains(language))
                    {
                        refused.add("language (not an available language)");
                        continue;
                    }
                    GuiPreferences.saveLanguage(language.name());
                    written.add("gui.language");
                }
                case "theme" -> {
                    String theme = entry.getValue() == null ? "" : entry.getValue().toLowerCase(java.util.Locale.ROOT);
                    if (!List.of("light", "dark", "darcula", "intellij").contains(theme))
                    {
                        refused.add("theme (must be light, dark, darcula or intellij)");
                        continue;
                    }
                    GuiPreferences.saveTheme(theme);
                    written.add("gui.theme");
                }
                case "fontSize" -> {
                    Integer size = parseInt(entry.getValue());
                    if (size == null || size < 8 || size > 24)
                    {
                        refused.add("fontSize (must be a whole number from 8 to 24)");
                        continue;
                    }
                    GuiPreferences.saveFontSize(size);
                    written.add("gui.fontSize");
                }
                default -> refused.add(entry.getKey() + " (unknown preference)");
            }
        }

        if (!written.isEmpty())
        {
            LOG.info("Web panel: {} changed {}", from, written);
        }
        return new Result(refused.isEmpty(), written, refused,
                          written.isEmpty() ? "Nothing changed." : "Saved.");
    }

    // ==================== Playback ====================

    /**
     * Playback control.
     *
     * <p>Deliberately limited to what is reversible. Pause, resume, skip and volume all undo by
     * doing them again; nothing here deletes a queue or leaves a channel permanently. The panel
     * cannot say who pressed the button — Discord can — so it should not be able to do the
     * things where that matters.
     */
    Result control(Map<String, String> command, String from)
    {
        String action = command.get("action");
        String guildId = command.get("guild");

        if (action == null || guildId == null)
        {
            return Result.failed("Both action and guild are required.");
        }
        if (bot.getJDA() == null)
        {
            return Result.failed("Not connected to Discord yet.");
        }

        Guild guild = bot.getJDA().getGuildById(guildId);
        if (guild == null)
        {
            return Result.failed("No such server.");
        }

        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        if (handler == null || handler.getPlayer().getPlayingTrack() == null)
        {
            return Result.failed("Nothing is playing there.");
        }

        switch (action)
        {
            case "pause" -> handler.getPlayer().setPaused(true);
            case "resume" -> handler.getPlayer().setPaused(false);
            case "skip" -> handler.getPlayer().stopTrack();
            case "volume" -> {
                Integer volume = parseInt(command.get("value"));
                if (volume == null || volume < 0 || volume > 150)
                {
                    return Result.failed("Volume must be a whole number from 0 to 150.");
                }
                handler.getPlayer().setVolume(volume);
            }
            default -> {
                return Result.failed("Unknown action.");
            }
        }

        LOG.info("Web panel: {} sent '{}' to {}", from, action, guild.getName());
        return new Result(true, List.of(action), List.of(), "Done.");
    }

    // ==================== Validation ====================

    private static ConfigOption optionFor(String key)
    {
        for (ConfigOption option : ConfigOption.values())
        {
            if (option.getKey().equals(key))
            {
                return option;
            }
        }
        return null;
    }

    /** Returns why the value is unacceptable, or null if it is fine. */
    private static String validate(ConfigOption option, String value)
    {
        if (value == null)
        {
            return "no value";
        }

        return switch (option.getType())
        {
            case INT, LONG -> parseInt(value) == null ? "must be a whole number" : null;
            case BOOLEAN -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                    ? null : "must be true or false";
            case DOUBLE -> parseDouble(value) == null ? "must be a number" : null;
            // A newline would let one value close its own quote and open a second setting.
            case STRING -> value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                    ? "must not contain a line break" : null;
            // A list or a nested block is not one text field, and guessing at how to serialise
            // one from a string is how a config file gets corrupted.
            case STRING_LIST, CONFIG, CONFIG_LIST -> "must be edited in config.txt directly";
        };
    }

    /** Renders the value as HOCON: quoted for strings, bare for everything else. */
    private static String format(ConfigOption option, String value)
    {
        return switch (option.getType())
        {
            case INT, LONG, BOOLEAN, DOUBLE -> value.trim();
            default -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        };
    }

    private static String sectionOf(String key)
    {
        int at = key.lastIndexOf('.');
        return at < 0 ? key : key.substring(0, at);
    }

    private static String leafOf(String key)
    {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    private static Integer parseInt(String value)
    {
        try
        {
            return value == null ? null : Integer.valueOf(value.trim());
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    private static Double parseDouble(String value)
    {
        try
        {
            return value == null ? null : Double.valueOf(value.trim());
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    /** What happened, in the shape the page renders. */
    record Result(boolean ok, List<String> written, List<String> refused, String message)
    {
        static Result failed(String message)
        {
            return new Result(false, List.of(), List.of(message), message);
        }

        Map<String, Object> asMap()
        {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", ok);
            out.put("written", written);
            out.put("refused", refused);
            out.put("message", message);
            return out;
        }
    }
}
