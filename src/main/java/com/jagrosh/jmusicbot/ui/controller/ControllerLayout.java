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
package com.jagrosh.jmusicbot.ui.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The button layout of the now-playing controller, as data.
 *
 * <p>Previously the panel was assembled in Java: two hardcoded rows, fixed labels, fixed
 * emoji, fixed order. Changing anything about it meant editing and rebuilding, which is the
 * wrong shape for something whose whole purpose is to be arranged to taste.
 *
 * <p>Shape follows Vocard's, so its layouts and the documentation people have written for
 * them apply here directly: a list of rows, each row a map of button type to that button's
 * appearance.
 *
 * <pre>
 * [
 *   { "back": {}, "play-pause": { "states": { "pause": {…}, "resume": {…} } }, "stop": { "style": "red" } },
 *   { "shuffle": {}, "loop": {}, "add-fav": {} }
 * ]
 * </pre>
 *
 * <p>Labels may use template syntax, so {@code "@@t_nowplaying.button.skip@@"} follows the
 * guild's language rather than pinning the panel to English.
 *
 * @author adan (xx445469)
 */
public final class ControllerLayout
{
    /** Discord's hard limits. Exceeding either is rejected by the API, not merely ignored. */
    public static final int MAX_ROWS = 5;
    public static final int MAX_BUTTONS_PER_ROW = 5;

    private final List<List<ButtonSpec>> rows;

    private ControllerLayout(List<List<ButtonSpec>> rows)
    {
        this.rows = rows;
    }

    /** One button: which action it performs, and how it looks. */
    public record ButtonSpec(String action, String label, String emoji, String style,
                             Map<String, ButtonSpec> states)
    {
        /** Appearance for a given state name, falling back to this button's own. */
        public ButtonSpec forState(String state)
        {
            if (states == null || state == null)
            {
                return this;
            }
            return states.getOrDefault(state, this);
        }
    }

    /**
     * Parses a layout.
     *
     * <p>Malformed entries are skipped rather than rejecting the whole layout: a panel with
     * one button missing is still usable, while refusing to render leaves a guild with no
     * controls at all over a typo.
     *
     * @param node   the layout array, or null for the built-in default
     * @param onWarn receives a description of anything skipped
     */
    public static ControllerLayout parse(JsonNode node, java.util.function.Consumer<String> onWarn)
    {
        if (node == null || !node.isArray() || node.isEmpty())
        {
            return defaultLayout();
        }

        List<List<ButtonSpec>> rows = new ArrayList<>();

        for (JsonNode rowNode : node)
        {
            if (!rowNode.isObject())
            {
                onWarn.accept("A controller row was not an object; skipping it.");
                continue;
            }

            List<ButtonSpec> row = new ArrayList<>();
            rowNode.fields().forEachRemaining(field ->
            {
                String action = normaliseAction(field.getKey());
                if (action == null)
                {
                    onWarn.accept("Unknown controller button '" + field.getKey() + "'; skipping it.");
                    return;
                }
                if (row.size() >= MAX_BUTTONS_PER_ROW)
                {
                    onWarn.accept("Row already holds " + MAX_BUTTONS_PER_ROW
                                  + " buttons, which is Discord's maximum; '" + field.getKey()
                                  + "' was dropped.");
                    return;
                }
                row.add(parseButton(action, field.getValue()));
            });

            if (row.isEmpty())
            {
                continue;
            }
            if (rows.size() >= MAX_ROWS)
            {
                onWarn.accept("Layout has more than " + MAX_ROWS
                              + " rows, which is Discord's maximum; the extras were dropped.");
                break;
            }
            rows.add(row);
        }

        return rows.isEmpty() ? defaultLayout() : new ControllerLayout(rows);
    }

    private static ButtonSpec parseButton(String action, JsonNode node)
    {
        // An entry that names a button without describing it — `"skip": {}` — means "the
        // skip button, as it normally looks", which is what anyone writing that expects.
        // Treating it as a button with no face would silently drop it from the panel.
        ButtonSpec fallback = defaultAppearance(action);

        String label = textOr(node, "label", fallback == null ? null : fallback.label());
        String emoji = textOr(node, "emoji", fallback == null ? null : fallback.emoji());
        String style = text(node, "style");

        Map<String, ButtonSpec> states = fallback == null ? null : fallback.states();
        JsonNode statesNode = node == null ? null : node.get("states");
        if (statesNode != null && statesNode.isObject())
        {
            states = new LinkedHashMap<>();
            Map<String, ButtonSpec> target = states;
            statesNode.fields().forEachRemaining(state ->
                    target.put(state.getKey(), parseButton(action, state.getValue())));
        }

        return new ButtonSpec(action, label, emoji, style, states);
    }

    /** Reads a field, or returns {@code fallback} when it is absent. */
    private static String textOr(JsonNode node, String field, String fallback)
    {
        String value = text(node, field);
        return value == null ? fallback : value;
    }

    /**
     * How a button looks when the layout does not say.
     *
     * <p>Shared with {@link #defaultLayout()}, so a hand-written layout and the built-in one
     * describe the same buttons the same way rather than drifting apart.
     */
    private static ButtonSpec defaultAppearance(String action)
    {
        return switch (action)
        {
            case "previous" -> button("previous", "nowplaying.button.prev", "⏮", null);
            case "pause" -> pauseButton();
            case "skip" -> button("skip", "nowplaying.button.skip", "⏭", null);
            case "stop" -> button("stop", "nowplaying.button.stop", "⏹", "red");
            case "shuffle" -> button("shuffle", "nowplaying.button.shuffle", "🔀", null);
            case "repeat" -> repeatButton();
            case "favorite" -> button("favorite", "nowplaying.button.favorite", "⭐", null);
            case "volup" -> button("volup", "nowplaying.button.volumeUp", "🔊", null);
            case "voldown" -> button("voldown", "nowplaying.button.volumeDown", "🔉", null);
            default -> null;
        };
    }

    private static String text(JsonNode node, String field)
    {
        if (node == null)
        {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    /**
     * Canonicalises a button name.
     *
     * <p>Accepts the spellings that appear across Vocard layouts and plain English, so a
     * layout copied from elsewhere is not rejected over a hyphen.
     */
    private static String normaliseAction(String name)
    {
        return switch (name.trim().toLowerCase(Locale.ROOT).replace("_", "-"))
        {
            case "back", "previous", "prev" -> "previous";
            case "play-pause", "playpause", "pause", "resume" -> "pause";
            case "skip", "next", "forward" -> "skip";
            case "stop", "leave" -> "stop";
            case "shuffle", "random" -> "shuffle";
            case "loop", "repeat" -> "repeat";
            case "add-fav", "addfav", "favorite", "favourite" -> "favorite";
            case "volumeup", "volume-up", "volup" -> "volup";
            case "volumedown", "volume-down", "voldown" -> "voldown";
            default -> null;
        };
    }

    /**
     * The layout used when none is configured.
     *
     * <p>Reproduces what the hardcoded panel showed, so upgrading changes nothing visible
     * until someone chooses to change it. Labels are translation keys rather than English,
     * which the hardcoded version could not express at all.
     */
    public static ControllerLayout defaultLayout()
    {
        return new ControllerLayout(List.of(
                List.of(defaultAppearance("previous"), defaultAppearance("pause"),
                        defaultAppearance("skip"), defaultAppearance("stop")),
                List.of(defaultAppearance("shuffle"), defaultAppearance("repeat"),
                        defaultAppearance("favorite"), defaultAppearance("voldown"),
                        defaultAppearance("volup"))));
    }

    private static ButtonSpec button(String action, String key, String emoji, String style)
    {
        return new ButtonSpec(action, "@@t_" + key + "@@", emoji, style, null);
    }

    /** Play/pause swaps face with playback state, which is why it carries states. */
    private static ButtonSpec pauseButton()
    {
        return new ButtonSpec("pause", "@@t_nowplaying.button.pause@@", "⏸", "blue",
                Map.of(
                        "pause", new ButtonSpec("pause", "@@t_nowplaying.button.pause@@", "⏸", "blue", null),
                        "resume", new ButtonSpec("pause", "@@t_nowplaying.button.resume@@", "▶", "blue", null)));
    }

    /** Repeat shows which mode is active rather than only that the button exists. */
    private static ButtonSpec repeatButton()
    {
        return new ButtonSpec("repeat", "@@t_nowplaying.button.repeat@@", "🔁", null,
                Map.of(
                        "off", new ButtonSpec("repeat", "@@t_nowplaying.button.repeat@@", "🔁", null, null),
                        "all", new ButtonSpec("repeat", "@@t_nowplaying.button.repeatAll@@", "🔁", "blue", null),
                        "single", new ButtonSpec("repeat", "@@t_nowplaying.button.repeatOne@@", "🔂", "blue", null)));
    }

    /** Rows of buttons, in display order. */
    public List<List<ButtonSpec>> getRows()
    {
        return rows;
    }
}
