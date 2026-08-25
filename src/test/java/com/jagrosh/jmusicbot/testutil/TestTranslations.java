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
package com.jagrosh.jmusicbot.testutil;

import java.util.Arrays;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.i18n.LanguageManager;

import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;

/**
 * Wires real translations into a mocked {@link Bot}.
 *
 * <p>Tests assert on the wording users actually see — {@code assertTrue(msg.contains("begin
 * playing"))} and similar. A mocked {@code msg()} returns null, which turns those into
 * NullPointerExceptions; stubbing it to echo the key back would be worse, because the
 * assertions would quietly stop checking anything real while still passing.
 *
 * <p>Loading the actual English file keeps those assertions meaningful and adds a second
 * benefit: a message key that exists in code but not in {@code EN.json} now fails the test
 * that renders it, rather than reaching production and rendering as a raw key.
 *
 * @author adan (xx445469)
 */
public final class TestTranslations
{
    /** Loaded once; the files are immutable and parsing them per fixture is wasteful. */
    private static final LanguageManager LANGUAGES = LanguageManager.load(Language.DEFAULT);

    private TestTranslations() { }

    /** The shared manager, for tests that need to resolve keys directly. */
    public static LanguageManager languages()
    {
        return LANGUAGES;
    }

    /** Resolves a key in English, as the test fixtures do. */
    public static String english(String key, Object... arguments)
    {
        return LANGUAGES.get(Language.EN, key, arguments);
    }

    /**
     * Creates a {@link Bot} mock whose {@code msg(...)} resolves real English translations.
     *
     * <p>Argument matchers cannot express this. {@code msg} is varargs, and a matcher in that
     * position matches exactly one argument — measured directly: a stub written with
     * {@code (Object[]) any()} answered {@code msg(guild, key, arg)} but returned null for
     * both {@code msg(guild, key)} and {@code msg(guild, key, a, b)}. Since most calls pass
     * no template arguments at all, most of them silently returned null and every assertion
     * on the resulting text failed with a NullPointerException.
     *
     * <p>A default answer sees the invocation after varargs are already collected, so arity
     * stops mattering. Everything other than {@code msg} falls through to Mockito's normal
     * defaults, leaving the mock otherwise ordinary.
     */
    public static Bot mockBot()
    {
        return mock(Bot.class, invocation ->
        {
            if ("msg".equals(invocation.getMethod().getName()))
            {
                Object[] args = invocation.getArguments();
                String key = (String) args[1];
                Object[] params = args.length > 2
                        ? flatten(Arrays.copyOfRange(args, 2, args.length))
                        : new Object[0];
                return LANGUAGES.get(Language.EN, key, params);
            }
            // msgFor(guild, user, key, args...) — the key sits one argument further along than
            // msg(guild, key, args...). Resolved the same way; a per-user language preference
            // is not something a mock has, so this always answers in English like msg does.
            if ("msgFor".equals(invocation.getMethod().getName()))
            {
                Object[] args = invocation.getArguments();
                String key = (String) args[2];
                Object[] params = args.length > 3
                        ? flatten(Arrays.copyOfRange(args, 3, args.length))
                        : new Object[0];
                return LANGUAGES.get(Language.EN, key, params);
            }
            if ("getLanguages".equals(invocation.getMethod().getName()))
            {
                return LANGUAGES;
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
    }

    /** Unwraps the single-array form Mockito produces when varargs are collected. */
    private static Object[] flatten(Object[] params)
    {
        if (params.length == 1 && params[0] instanceof Object[] nested)
        {
            return nested;
        }
        return params;
    }
}
