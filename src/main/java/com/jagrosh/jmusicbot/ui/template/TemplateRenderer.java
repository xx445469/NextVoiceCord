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
package com.jagrosh.jmusicbot.ui.template;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Renders controller templates.
 *
 * <p>Three layers of syntax, evaluated in a fixed order:
 *
 * <table>
 *   <caption>Template syntax</caption>
 *   <tr><td>{@code {{ cond ?? yes // no }}}</td><td>conditional section</td></tr>
 *   <tr><td>{@code @@t_some.key@@}</td><td>translated text</td></tr>
 *   <tr><td>{@code @@variable@@}</td><td>substituted value</td></tr>
 * </table>
 *
 * <p>The order matters and is not arbitrary. Conditionals run first because they decide which
 * text survives at all, and rendering a branch that is about to be discarded would resolve
 * variables — and translations — for output nobody sees. Translations run before variables so
 * that a translated string containing {@code @@track_name@@} still has it filled in; the
 * reverse order would leave that placeholder visible in every language but English.
 *
 * <p>The syntax is Vocard's, so a layout written for Vocard renders here unchanged. That
 * compatibility is the point: it makes existing layouts and the documentation people have
 * already written apply to this bot.
 *
 * @author adan (xx445469)
 */
public final class TemplateRenderer
{
    private static final String CONDITION_OPEN = "{{";
    private static final String CONDITION_CLOSE = "}}";
    private static final String CONDITION_THEN = "??";
    private static final String CONDITION_ELSE = "//";

    private static final String MARKER = "@@";
    private static final String TRANSLATION_PREFIX = "t_";

    private final Map<String, String> variables;
    private final BiFunction<String, Object[], String> translator;

    /**
     * @param variables  values for {@code @@name@@} references
     * @param translator resolves a message key to translated text
     */
    public TemplateRenderer(Map<String, String> variables,
                            BiFunction<String, Object[], String> translator)
    {
        this.variables = variables;
        this.translator = translator;
    }

    /**
     * Renders {@code template}.
     *
     * @return the rendered text, or an empty string if {@code template} is null
     */
    public String render(String template)
    {
        if (template == null)
        {
            return "";
        }
        return substituteVariables(resolveTranslations(resolveConditions(template)));
    }

    /**
     * Resolves {@code {{ … }}} blocks, innermost first.
     *
     * <p>Scanning for the last opener before the first closer after it handles nesting
     * without recursion into a parser: an inner block is always the last one opened.
     */
    private String resolveConditions(String template)
    {
        String current = template;
        // Bounded rather than while(true): a template with unbalanced braces would otherwise
        // spin forever, and a layout can be edited by a server admin.
        for (int pass = 0; pass < 32; pass++)
        {
            int close = current.indexOf(CONDITION_CLOSE);
            if (close < 0)
            {
                return current;
            }
            int open = current.lastIndexOf(CONDITION_OPEN, close);
            if (open < 0)
            {
                return current;
            }

            String body = current.substring(open + CONDITION_OPEN.length(), close);
            current = current.substring(0, open) + evaluateBlock(body)
                    + current.substring(close + CONDITION_CLOSE.length());
        }
        return current;
    }

    /** Evaluates one {@code cond ?? yes // no} body. */
    private String evaluateBlock(String body)
    {
        int then = body.indexOf(CONDITION_THEN);
        if (then < 0)
        {
            // No branch at all: nothing sensible to render, and emitting the raw source
            // would put template syntax in front of users.
            return "";
        }

        String condition = body.substring(0, then);
        String branches = body.substring(then + CONDITION_THEN.length());

        int otherwise = branches.indexOf(CONDITION_ELSE);
        // Branches are trimmed. Templates put a space after ?? for readability while the
        // text before the block usually ends in one already, so preserving it produces a
        // double space in exactly the common case:
        //   "Volume: 100% {{ loop != 'Off' ?? | Repeat }}"  ->  "Volume: 100%  | Repeat"
        String ifTrue = (otherwise < 0 ? branches : branches.substring(0, otherwise)).trim();
        String ifFalse = otherwise < 0
                ? ""
                : branches.substring(otherwise + CONDITION_ELSE.length()).trim();

        String resolved = ConditionEvaluator.substituteVariables(condition, variables);
        return ConditionEvaluator.evaluate(resolved) ? ifTrue : ifFalse;
    }

    /**
     * Resolves {@code @@t_key@@} references, repeatedly.
     *
     * <p>Separate from variable substitution, and deliberately re-scanned: translated text
     * legitimately contains variable references, and a translation may compose another. That
     * content comes from this project's own language files, so expanding it again is safe in
     * a way that expanding a track title is not.
     *
     * <p>Bounded because a translation that referenced itself would otherwise loop forever.
     */
    private String resolveTranslations(String template)
    {
        String current = template;
        for (int pass = 0; pass < 8; pass++)
        {
            String next = substituteMarkers(current, true);
            if (next.equals(current))
            {
                return next;
            }
            current = next;
        }
        return current;
    }

    /**
     * Resolves {@code @@variable@@} references in exactly one pass.
     *
     * <p>Single-pass by design, and this is the security boundary: a substituted value that
     * happens to contain {@code @@} — a track title can contain anything a user types — is
     * emitted literally rather than rescanned. Without that, a title could name a variable,
     * or a translation key, and have it expanded.
     */
    private String substituteVariables(String template)
    {
        return substituteMarkers(template, false);
    }

    /**
     * One scan for {@code @@…@@} markers.
     *
     * @param translationsOnly when true, resolves only {@code t_} references and leaves
     *                         variables untouched for the later pass
     */
    private String substituteMarkers(String template, boolean translationsOnly)
    {
        StringBuilder out = new StringBuilder(template.length() + 64);
        int index = 0;

        while (index < template.length())
        {
            int open = template.indexOf(MARKER, index);
            if (open < 0)
            {
                out.append(template, index, template.length());
                break;
            }

            int close = template.indexOf(MARKER, open + MARKER.length());
            if (close < 0)
            {
                // Unterminated marker: emit the rest as written rather than swallowing it,
                // so the mistake is visible to whoever edits the template.
                out.append(template, index, template.length());
                break;
            }

            out.append(template, index, open);
            String name = template.substring(open + MARKER.length(), close);
            boolean isTranslation = name.startsWith(TRANSLATION_PREFIX);

            if (translationsOnly && !isTranslation)
            {
                // Left intact for the variable pass. Re-emitted verbatim rather than
                // resolved here, which is what keeps the two passes separate.
                out.append(MARKER).append(name).append(MARKER);
            }
            else
            {
                out.append(resolve(name));
            }
            index = close + MARKER.length();
        }
        return out.toString();
    }

    /** Resolves one reference, which is either a translation key or a variable. */
    private String resolve(String name)
    {
        if (name.startsWith(TRANSLATION_PREFIX))
        {
            String key = name.substring(TRANSLATION_PREFIX.length());
            String translated = translator.apply(key, new Object[0]);
            return translated == null ? key : translated;
        }

        // An unknown variable renders empty rather than as its own name. Leaving "@@foo@@"
        // in place would put template syntax in front of users, and a typo in a layout is
        // more likely than a deliberate literal.
        return variables.getOrDefault(name, "");
    }
}
