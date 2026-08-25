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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import com.jagrosh.jmusicbot.ui.template.TemplateRenderer;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.emoji.Emoji;

/**
 * Turns a {@link ControllerLayout} into Discord components.
 *
 * <p>Which buttons exist and how they look comes from the layout; which ones are usable right
 * now comes from playback state. Keeping those separate is what lets the layout be edited
 * without touching the rules — "you cannot skip backwards past the start" is not a matter of
 * taste, while "the skip button is second in the row" is.
 *
 * @author adan (xx445469)
 */
public final class ControllerRenderer
{
    /** Prefix identifying a now-playing button, matched by the interaction listener. */
    private final String idPrefix;
    private final TemplateRenderer templates;

    /**
     * @param idPrefix  component-id prefix the button listener recognises
     * @param variables template variables available to labels
     * @param translator resolves message keys
     */
    public ControllerRenderer(String idPrefix,
                              Map<String, String> variables,
                              BiFunction<String, Object[], String> translator)
    {
        this.idPrefix = idPrefix;
        this.templates = new TemplateRenderer(variables, translator);
    }

    /** Playback facts that decide a button's state and whether it is usable. */
    public record PlaybackState(boolean paused,
                                boolean canGoPrevious,
                                boolean canShuffle,
                                boolean canVolumeUp,
                                boolean canVolumeDown,
                                boolean favorited,
                                String repeatMode) { }

    /**
     * Builds the action rows for {@code layout}.
     *
     * <p>Rows that end up empty are omitted rather than sent as empty rows, which Discord
     * rejects outright.
     */
    public List<ActionRow> render(ControllerLayout layout, PlaybackState state)
    {
        // A missing layout falls back rather than throwing. The alternative is that any gap
        // in how settings were loaded costs a guild its entire controller, and losing every
        // button is a far worse outcome than showing the standard ones.
        ControllerLayout effective = layout == null ? ControllerLayout.defaultLayout() : layout;

        List<ActionRow> rows = new ArrayList<>();

        for (List<ControllerLayout.ButtonSpec> specRow : effective.getRows())
        {
            List<Button> buttons = new ArrayList<>();
            for (ControllerLayout.ButtonSpec spec : specRow)
            {
                Button button = toButton(spec, state);
                if (button != null)
                {
                    buttons.add(button);
                }
            }
            if (!buttons.isEmpty())
            {
                rows.add(ActionRow.of(buttons));
            }
        }
        return rows;
    }

    private Button toButton(ControllerLayout.ButtonSpec spec, PlaybackState state)
    {
        ControllerLayout.ButtonSpec active = spec.forState(stateNameFor(spec.action(), state));

        String rendered = templates.render(active.label());
        String label = rendered == null || rendered.isBlank() ? null : rendered;
        Emoji emoji = parseEmoji(active.emoji());

        // Discord requires at least one of the two, and JDA enforces it at construction —
        // so both are resolved before the button is built, not added afterwards. A layout
        // supplying neither yields nothing rather than an unclickable sliver.
        if (label == null && emoji == null)
        {
            return null;
        }

        ButtonStyle style = styleFor(active.style(), spec.action(), state);
        String id = idPrefix + spec.action();

        Button button = emoji == null
                ? Button.of(style, id, label)
                : Button.of(style, id, label, emoji);

        return button.withDisabled(!isUsable(spec.action(), state));
    }

    /**
     * Parses an emoji from a layout, tolerating anything.
     *
     * <p>A layout is editable data and can carry any string here. Losing an icon is an
     * acceptable outcome; losing the guild's controller to an exception is not.
     */
    private static Emoji parseEmoji(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        try
        {
            return Emoji.fromFormatted(value);
        }
        catch (RuntimeException ex)
        {
            return null;
        }
    }

    /** Which named state a button is currently in, for layouts that define several. */
    private static String stateNameFor(String action, PlaybackState state)
    {
        return switch (action)
        {
            case "pause" -> state.paused() ? "resume" : "pause";
            case "repeat" -> state.repeatMode() == null
                    ? "off"
                    : state.repeatMode().toLowerCase(Locale.ROOT);
            case "favorite" -> state.favorited() ? "on" : "off";
            default -> null;
        };
    }

    /**
     * Resolves the button colour.
     *
     * <p>A layout may name one. Where it does not, buttons that reflect an active mode —
     * repeat engaged, track favourited — take the accent colour, so the panel shows state
     * rather than only offering actions.
     */
    private static ButtonStyle styleFor(String style, String action, PlaybackState state)
    {
        if (style != null && !style.isBlank())
        {
            return switch (style.trim().toLowerCase(Locale.ROOT))
            {
                case "red", "danger" -> ButtonStyle.DANGER;
                case "green", "success" -> ButtonStyle.SUCCESS;
                case "blue", "primary" -> ButtonStyle.PRIMARY;
                case "grey", "gray", "secondary" -> ButtonStyle.SECONDARY;
                default -> ButtonStyle.SECONDARY;
            };
        }

        return switch (action)
        {
            case "pause" -> ButtonStyle.PRIMARY;
            case "stop" -> ButtonStyle.DANGER;
            case "favorite" -> state.favorited() ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY;
            case "repeat" -> state.repeatMode() != null && !"off".equalsIgnoreCase(state.repeatMode())
                    ? ButtonStyle.PRIMARY
                    : ButtonStyle.SECONDARY;
            default -> ButtonStyle.SECONDARY;
        };
    }

    /**
     * Whether an action can be taken right now.
     *
     * <p>Disabled rather than hidden: a button that vanishes moves everything beside it, so
     * the panel's shape would shift under the pointer as playback changes.
     */
    private static boolean isUsable(String action, PlaybackState state)
    {
        return switch (action)
        {
            case "previous" -> state.canGoPrevious();
            case "shuffle" -> state.canShuffle();
            case "volup" -> state.canVolumeUp();
            case "voldown" -> state.canVolumeDown();
            default -> true;
        };
    }
}
