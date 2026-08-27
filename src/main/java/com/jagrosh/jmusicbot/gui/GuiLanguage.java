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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.i18n.LanguageManager;

/**
 * The language the desktop window is displayed in.
 *
 * <p>Separate from the language the bot replies in on Discord, and deliberately so: the
 * person watching this window is the operator, who is not necessarily in any of the servers
 * the bot serves and has no reason to read whatever language those servers chose.
 *
 * <p>Held statically because Swing components are created all over the tree and threading a
 * translator through every constructor would be a large change for a small gain. The trade is
 * that this is process-wide state, which is acceptable here: a desktop application has exactly
 * one window and exactly one person looking at it.
 *
 * @author adan (xx445469)
 */
public final class GuiLanguage
{
    private static LanguageManager languages;
    private static Language current = Language.DEFAULT;

    /** Notified on change, so open panels can relabel themselves without being rebuilt. */
    private static final List<Consumer<Language>> listeners = new ArrayList<>();

    private GuiLanguage() { }

    /**
     * Supplies the translations and the starting language.
     *
     * <p>Called once during startup, before any window exists.
     */
    public static void initialise(LanguageManager manager, Language initial)
    {
        languages = manager;
        current = initial == null ? Language.DEFAULT : initial;
        tellTheThemeWhatToRender();
    }

    /** The language the window is currently showing. */
    public static Language get()
    {
        return current;
    }

    /** Changes the language and tells everything listening. */
    public static void set(Language language)
    {
        if (language == null || language == current)
        {
            return;
        }
        current = language;
        tellTheThemeWhatToRender();
        // A copy, because a listener relabelling a panel may add or remove listeners as it
        // rebuilds — iterating the live list would fail halfway through the update.
        for (Consumer<Language> listener : new ArrayList<>(listeners))
        {
            listener.accept(language);
        }
    }

    /** Registers a listener that relabels when the language changes. */
    public static void onChange(Consumer<Language> listener)
    {
        listeners.add(listener);
    }

    /**
     * Resolves a message key.
     *
     * <p>Returns the key itself before {@link #initialise} has run rather than throwing,
     * because a component built early enough to hit that has a labelling problem, not a
     * reason to stop the window from opening.
     */
    public static String msg(String key, Object... arguments)
    {
        return languages == null ? key : languages.get(current, key, arguments);
    }

    /**
     * Hands the theme a sample of the language so it can check the font can draw it.
     *
     * <p>The interface font is chosen before any of this runs, and several platforms' candidates
     * are Latin-only families. Picking one and then asking it to render Chinese produces a row of
     * empty boxes, which is what this exists to prevent.
     *
     * <p>Sends every distinct character the language can display, not one specimen string. A
     * single nav label was not enough: a font that draws those two characters can still be
     * missing hundreds of others, and the window then renders mostly correctly with boxes
     * scattered through it — which is harder to recognise as a font problem than if it had
     * failed outright.
     */
    private static void tellTheThemeWhatToRender()
    {
        String sample = languages == null ? "" : languages.getDistinctCharacters(current);
        com.jagrosh.jmusicbot.gui.theme.ThemeManager.setRequiredGlyphs(sample);
    }

    /** Languages that loaded, for the picker. */
    public static List<Language> available()
    {
        return languages == null
                ? List.of(Language.DEFAULT)
                : new ArrayList<>(languages.getAvailableLanguages());
    }
}
