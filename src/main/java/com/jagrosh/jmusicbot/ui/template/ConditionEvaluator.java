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

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates the conditional expressions used inside {@code {{ … ?? … // … }}} blocks.
 *
 * <p>Deliberately not a scripting engine. Vocard, whose template syntax this follows, hands
 * the condition to Python's {@code eval()}; the equivalent here would be handing a config
 * file to Rhino, which is already on the classpath. A controller layout is data — sometimes
 * data a server admin can edit through a slash command — and data should not be able to read
 * files or open sockets because someone pasted a layout they found online.
 *
 * <p>What it supports is what conditions in templates actually need:
 *
 * <pre>
 *   loop_mode != 'Off'
 *   queue_length &gt; 0
 *   volume &gt;= 100 &amp;&amp; track_name != 'None'
 * </pre>
 *
 * <p>Operands are literals or already-substituted values. Numeric-looking operands compare as
 * numbers, so {@code queue_length > 9} does not become the string comparison that would make
 * "10" smaller than "9".
 *
 * @author adan (xx445469)
 */
final class ConditionEvaluator
{
    /** Splits on || and && while keeping which one matched, so precedence can be applied. */
    private static final Pattern OR = Pattern.compile("\\|\\|");
    private static final Pattern AND = Pattern.compile("&&");

    private static final Pattern COMPARISON = Pattern.compile(
            "^\\s*(.+?)\\s*(==|!=|>=|<=|>|<)\\s*(.+?)\\s*$");

    private ConditionEvaluator() { }

    /**
     * Evaluates {@code expression}.
     *
     * @return the result, or false if the expression cannot be understood — a malformed
     *         condition hides that section rather than breaking the whole message
     */
    static boolean evaluate(String expression)
    {
        if (expression == null || expression.isBlank())
        {
            return false;
        }

        String trimmed = expression.trim();

        // OR binds loosest, so it splits first; each side is then an AND-chain.
        String[] orParts = OR.split(trimmed);
        if (orParts.length > 1)
        {
            for (String part : orParts)
            {
                if (evaluate(part))
                {
                    return true;
                }
            }
            return false;
        }

        String[] andParts = AND.split(trimmed);
        if (andParts.length > 1)
        {
            for (String part : andParts)
            {
                if (!evaluate(part))
                {
                    return false;
                }
            }
            return true;
        }

        return evaluateComparison(trimmed);
    }

    private static boolean evaluateComparison(String expression)
    {
        Matcher matcher = COMPARISON.matcher(expression);
        if (!matcher.matches())
        {
            // No operator: treat the bare value as a truth test, so `{{ track_name ?? … }}`
            // works without writing `track_name != ''`.
            return isTruthy(unquote(expression.trim()));
        }

        String left = unquote(matcher.group(1));
        String operator = matcher.group(2);
        String right = unquote(matcher.group(3));

        Double leftNumber = asNumber(left);
        Double rightNumber = asNumber(right);

        if (leftNumber != null && rightNumber != null)
        {
            int cmp = Double.compare(leftNumber, rightNumber);
            return switch (operator)
            {
                case "==" -> cmp == 0;
                case "!=" -> cmp != 0;
                case ">"  -> cmp > 0;
                case "<"  -> cmp < 0;
                case ">=" -> cmp >= 0;
                case "<=" -> cmp <= 0;
                default   -> false;
            };
        }

        // Ordering two non-numbers is almost always a mistake in a template rather than an
        // intent, so only equality is honoured for strings.
        return switch (operator)
        {
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            default   -> false;
        };
    }

    /**
     * Whether a bare value counts as true.
     *
     * <p>"None" and "0" are included because the variables these templates read use them for
     * absence — an empty queue reports "0", and a stopped player reports "None".
     */
    private static boolean isTruthy(String value)
    {
        if (value == null || value.isBlank())
        {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return !lower.equals("false") && !lower.equals("none") && !lower.equals("0");
    }

    /** Strips one matching pair of surrounding quotes, if present. */
    private static String unquote(String value)
    {
        String trimmed = value.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                    || (trimmed.startsWith("\"") && trimmed.endsWith("\""))))
        {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static Double asNumber(String value)
    {
        try
        {
            return Double.valueOf(value);
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    /** Resolves {@code @@name@@} references inside a condition before evaluating it. */
    static String substituteVariables(String expression, Map<String, String> variables)
    {
        StringBuilder out = new StringBuilder(expression.length());
        int index = 0;

        while (index < expression.length())
        {
            int open = expression.indexOf("@@", index);
            if (open < 0)
            {
                out.append(expression, index, expression.length());
                break;
            }
            int close = expression.indexOf("@@", open + 2);
            if (close < 0)
            {
                out.append(expression, index, expression.length());
                break;
            }

            out.append(expression, index, open);
            String name = expression.substring(open + 2, close);
            String value = variables.getOrDefault(name, "");

            // Quoted so a value containing spaces or an operator cannot restructure the
            // expression around it — a track title is arbitrary user input.
            out.append('\'').append(value.replace("'", "")).append('\'');
            index = close + 2;
        }
        return out.toString();
    }
}
