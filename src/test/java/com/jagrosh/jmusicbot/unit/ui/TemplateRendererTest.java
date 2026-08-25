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

import java.util.Map;

import com.jagrosh.jmusicbot.ui.template.TemplateRenderer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TemplateRenderer")
class TemplateRendererTest
{
    private static final Map<String, String> VARIABLES = Map.of(
            "track_name", "Never Gonna Give You Up",
            "queue_length", "3",
            "volume", "100",
            "loop_mode", "Off",
            "empty_value", ""
    );

    private static TemplateRenderer renderer()
    {
        return new TemplateRenderer(VARIABLES, (key, args) -> switch (key)
        {
            case "player.buttons.pause" -> "Pause";
            case "greeting" -> "Now playing: @@track_name@@";
            default -> null;
        });
    }

    @Nested
    @DisplayName("variables")
    class Variables
    {
        @Test
        @DisplayName("substitutes a reference")
        void substitutes()
        {
            assertEquals("Playing Never Gonna Give You Up now",
                         renderer().render("Playing @@track_name@@ now"));
        }

        @Test
        @DisplayName("renders an unknown variable as nothing")
        void unknownRendersEmpty()
        {
            // Leaving "@@nope@@" visible would show template syntax to users, and a typo in
            // a layout is far more likely than a deliberate literal.
            assertEquals("before  after", renderer().render("before @@nope@@ after"));
        }

        @Test
        @DisplayName("does not rescan a substituted value")
        void substitutedValuesAreNotRescanned()
        {
            // Track titles are arbitrary user input and can contain the marker itself.
            TemplateRenderer hostile = new TemplateRenderer(
                    Map.of("title", "@@volume@@", "volume", "999"), (k, a) -> null);
            assertEquals("@@volume@@", hostile.render("@@title@@"));
        }

        @Test
        @DisplayName("leaves an unterminated marker visible")
        void unterminatedMarkerSurvives()
        {
            // Silently swallowing the rest would hide the mistake from whoever wrote it.
            assertEquals("text @@unclosed", renderer().render("text @@unclosed"));
        }
    }

    @Nested
    @DisplayName("translations")
    class Translations
    {
        @Test
        @DisplayName("resolves a t_ reference")
        void resolvesTranslation()
        {
            assertEquals("Pause", renderer().render("@@t_player.buttons.pause@@"));
        }

        @Test
        @DisplayName("fills variables inside translated text")
        void variablesInsideTranslations()
        {
            // Translations run before variables precisely so this works. The reverse order
            // would leave the placeholder visible in every language.
            assertEquals("Now playing: Never Gonna Give You Up",
                         renderer().render("@@t_greeting@@"));
        }

        @Test
        @DisplayName("falls back to the key when a translation is missing")
        void missingTranslationShowsKey()
        {
            assertEquals("some.missing.key", renderer().render("@@t_some.missing.key@@"));
        }
    }

    @Nested
    @DisplayName("conditionals")
    class Conditionals
    {
        @Test
        @DisplayName("keeps the true branch")
        void trueBranch()
        {
            assertEquals("shown", renderer().render("{{ @@queue_length@@ > 0 ?? shown // hidden }}"));
        }

        @Test
        @DisplayName("keeps the false branch")
        void falseBranch()
        {
            assertEquals("hidden", renderer().render("{{ @@queue_length@@ > 5 ?? shown // hidden }}"));
        }

        @Test
        @DisplayName("an omitted else branch renders nothing")
        void omittedElseBranch()
        {
            assertEquals("", renderer().render("{{ @@loop_mode@@ != 'Off' ?? | Repeat }}"));
            assertEquals("| Repeat", renderer().render("{{ @@loop_mode@@ == 'Off' ?? | Repeat }}"));
        }

        @Test
        @DisplayName("compares numerically, not as text")
        void numericComparison()
        {
            // String comparison would make "9" larger than "10", so a ten-track queue would
            // read as smaller than a nine-track one.
            TemplateRenderer ten = new TemplateRenderer(Map.of("n", "10"), (k, a) -> null);
            assertEquals("bigger", ten.render("{{ @@n@@ > 9 ?? bigger // smaller }}"));
        }

        @Test
        @DisplayName("supports && and ||")
        void logicalOperators()
        {
            assertEquals("yes", renderer().render(
                    "{{ @@volume@@ == 100 && @@queue_length@@ > 0 ?? yes // no }}"));
            assertEquals("yes", renderer().render(
                    "{{ @@volume@@ == 5 || @@queue_length@@ == 3 ?? yes // no }}"));
            assertEquals("no", renderer().render(
                    "{{ @@volume@@ == 5 && @@queue_length@@ == 3 ?? yes // no }}"));
        }

        @Test
        @DisplayName("a bare value is a truth test")
        void bareValueIsTruthTest()
        {
            assertEquals("present", renderer().render("{{ @@track_name@@ ?? present // absent }}"));
            assertEquals("absent", renderer().render("{{ @@empty_value@@ ?? present // absent }}"));
        }

        @Test
        @DisplayName("a value containing an operator cannot restructure the condition")
        void hostileValueCannotBreakOut()
        {
            // The substituted value is quoted, so a title like "x' == 'x" compares as text
            // rather than becoming part of the expression.
            TemplateRenderer hostile = new TemplateRenderer(
                    Map.of("title", "x' == 'x"), (k, a) -> null);
            assertEquals("no", hostile.render("{{ @@title@@ == 'safe' ?? yes // no }}"));
        }

        @Test
        @DisplayName("an unbalanced block terminates instead of looping")
        void unbalancedBlockTerminates()
        {
            // A layout is editable by a server admin, so malformed input must not hang the
            // thread rendering the panel.
            String rendered = renderer().render("{{ {{ {{ broken");
            assertTrue(rendered.contains("broken"));
        }
    }
}
