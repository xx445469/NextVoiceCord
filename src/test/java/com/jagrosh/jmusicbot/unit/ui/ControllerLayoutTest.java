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
package com.jagrosh.jmusicbot.unit.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jmusicbot.ui.controller.ControllerLayout;
import com.jagrosh.jmusicbot.ui.controller.ControllerRenderer;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Controller layout")
class ControllerLayoutTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ControllerLayout parse(String json)
    {
        try
        {
            return ControllerLayout.parse(MAPPER.readTree(json), warning -> { });
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private static ControllerRenderer renderer()
    {
        return new ControllerRenderer("np_", Map.of(), (key, args) -> key);
    }

    private static ControllerRenderer.PlaybackState playing()
    {
        return new ControllerRenderer.PlaybackState(false, true, true, true, true, false, "off");
    }

    private static List<Button> allButtons(List<ActionRow> rows)
    {
        List<Button> buttons = new ArrayList<>();
        rows.forEach(row -> row.getComponents().forEach(component ->
        {
            if (component instanceof Button button)
            {
                buttons.add(button);
            }
        }));
        return buttons;
    }

    @Nested
    @DisplayName("parsing")
    class Parsing
    {
        @Test
        @DisplayName("reads rows and buttons in order")
        void readsRowsInOrder()
        {
            ControllerLayout layout = parse("""
                [ { "back": {}, "skip": {} }, { "stop": {} } ]
                """);

            assertEquals(2, layout.getRows().size());
            assertEquals(2, layout.getRows().get(0).size());
            assertEquals("previous", layout.getRows().get(0).get(0).action());
            assertEquals("skip", layout.getRows().get(0).get(1).action());
        }

        @Test
        @DisplayName("accepts the spellings people actually write")
        void acceptsAliases()
        {
            // A layout copied from elsewhere should not be rejected over a hyphen.
            ControllerLayout layout = parse("""
                [ { "play-pause": {}, "playpause": {}, "add-fav": {}, "favourite": {} } ]
                """);

            List<String> actions = layout.getRows().get(0).stream()
                                         .map(ControllerLayout.ButtonSpec::action).toList();
            assertTrue(actions.contains("pause"));
            assertTrue(actions.contains("favorite"));
        }

        @Test
        @DisplayName("skips an unknown button rather than rejecting the layout")
        void unknownButtonIsSkipped()
        {
            // A panel missing one button is still usable; refusing to parse would leave the
            // guild with no controls at all over a single typo.
            ControllerLayout layout = parse("""
                [ { "skip": {}, "teleport": {} } ]
                """);

            assertEquals(1, layout.getRows().get(0).size());
            assertEquals("skip", layout.getRows().get(0).get(0).action());
        }

        @Test
        @DisplayName("falls back to the default when the layout is empty or absent")
        void emptyFallsBackToDefault()
        {
            assertFalse(ControllerLayout.parse(null, w -> { }).getRows().isEmpty());
            assertFalse(parse("[]").getRows().isEmpty());
            assertFalse(parse("[ { \"nonsense\": {} } ]").getRows().isEmpty());
        }

        @Test
        @DisplayName("enforces Discord's row and button limits")
        void enforcesDiscordLimits()
        {
            // Exceeding either is rejected by the API outright, so an over-full layout would
            // mean no message at all rather than a truncated one.
            ControllerLayout wideRow = parse("""
                [ { "back": {}, "play-pause": {}, "skip": {}, "stop": {}, "shuffle": {}, "loop": {} } ]
                """);
            assertEquals(ControllerLayout.MAX_BUTTONS_PER_ROW, wideRow.getRows().get(0).size());

            ControllerLayout tall = parse("""
                [ {"skip":{}}, {"skip":{}}, {"skip":{}}, {"skip":{}}, {"skip":{}}, {"skip":{}} ]
                """);
            assertEquals(ControllerLayout.MAX_ROWS, tall.getRows().size());
        }
    }

    @Nested
    @DisplayName("rendering")
    class Rendering
    {
        @Test
        @DisplayName("uses the state-specific face of a multi-state button")
        void multiStateButton()
        {
            ControllerLayout layout = parse("""
                [ { "play-pause": { "states": {
                      "pause":  { "label": "Pause",  "emoji": "⏸" },
                      "resume": { "label": "Resume", "emoji": "▶" } } } } ]
                """);

            ControllerRenderer.PlaybackState paused =
                    new ControllerRenderer.PlaybackState(true, true, true, true, true, false, "off");

            assertEquals("Pause", allButtons(renderer().render(layout, playing())).get(0).getLabel());
            assertEquals("Resume", allButtons(renderer().render(layout, paused)).get(0).getLabel());
        }

        @Test
        @DisplayName("disables an unavailable action instead of removing it")
        void unavailableActionsAreDisabled()
        {
            // Removing a button shifts everything beside it, so the panel would change shape
            // under the pointer as playback progresses.
            ControllerLayout layout = parse("[ { \"back\": {}, \"skip\": {} } ]");

            ControllerRenderer.PlaybackState atStart =
                    new ControllerRenderer.PlaybackState(false, false, true, true, true, false, "off");

            List<Button> buttons = allButtons(renderer().render(layout, atStart));
            assertEquals(2, buttons.size(), "the button must still be present");
            assertTrue(buttons.get(0).isDisabled());
            assertFalse(buttons.get(1).isDisabled());
        }

        @Test
        @DisplayName("honours a style from the layout")
        void layoutStyleWins()
        {
            ControllerLayout layout = parse("[ { \"skip\": { \"style\": \"red\" } } ]");
            assertEquals(ButtonStyle.DANGER, allButtons(renderer().render(layout, playing())).get(0).getStyle());
        }

        @Test
        @DisplayName("reflects an active mode in the button colour")
        void activeModeIsVisible()
        {
            ControllerLayout layout = parse("[ { \"loop\": {}, \"add-fav\": {} } ]");

            ControllerRenderer.PlaybackState looping =
                    new ControllerRenderer.PlaybackState(false, true, true, true, true, true, "ALL");

            List<Button> buttons = allButtons(renderer().render(layout, looping));
            assertEquals(ButtonStyle.PRIMARY, buttons.get(0).getStyle(), "repeat is engaged");
            assertEquals(ButtonStyle.SUCCESS, buttons.get(1).getStyle(), "track is favourited");
        }

        @Test
        @DisplayName("survives an unusable emoji")
        void badEmojiDoesNotBreakThePanel()
        {
            // A layout can carry any string here. Losing an icon is acceptable; losing the
            // controller is not.
            ControllerLayout layout = parse("[ { \"skip\": { \"label\": \"Skip\", \"emoji\": \"not an emoji\" } } ]");
            assertEquals(1, allButtons(renderer().render(layout, playing())).size());
        }

        @Test
        @DisplayName("falls back to the default layout when given none")
        void nullLayoutFallsBack()
        {
            assertFalse(allButtons(renderer().render(null, playing())).isEmpty());
        }
    }
}
