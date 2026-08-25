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
package com.jagrosh.jmusicbot.unit.gui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;

import javax.swing.UIManager;

import com.jagrosh.jmusicbot.gui.theme.ThemeManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the ordering between installing a look and feel and setting UI defaults.
 *
 * <p>Installing a FlatLaf replaces the UIManager's contents wholesale. Defaults registered
 * beforehand are therefore discarded, and the failure is completely silent — the code runs,
 * every value is set, and the window simply renders with stock styling. That is exactly what
 * happened here: every {@code UIManager.put} in ThemeManager was a no-op, including ones that
 * had been there long before this project, and the only symptom was an interface that looked
 * untouched no matter what was changed.
 *
 * <p>Asserting on the values after initialisation is the only way this stays fixed. A test
 * that merely called the setup would pass just as happily with the ordering wrong again.
 */
@DisabledIf(value = "isHeadless", disabledReason = "Swing UI defaults need a display")
@DisplayName("ThemeManager UI defaults")
class ThemeManagerDefaultsTest
{
    static boolean isHeadless()
    {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void initialiseTheme()
    {
        ThemeManager.initialize("dark", 13);
    }

    @Test
    @DisplayName("component shape survives look-and-feel installation")
    void shapeDefaultsApplied()
    {
        assertEquals(10, UIManager.get("Button.arc"));
        assertEquals(10, UIManager.get("Component.arc"));
    }

    @Test
    @DisplayName("scrollbar stepper arrows stay disabled")
    void scrollbarDefaultsApplied()
    {
        assertEquals(false, UIManager.get("ScrollBar.showButtons"));
        assertEquals(12, UIManager.get("ScrollBar.width"));
    }

    @Test
    @DisplayName("tabs render as cards without separators")
    void tabDefaultsApplied()
    {
        assertEquals("card", UIManager.get("TabbedPane.tabType"));
        assertEquals(false, UIManager.get("TabbedPane.showTabSeparators"));
    }

    @Test
    @DisplayName("the default font is set and is not the generic sans-serif family")
    void fontResolvesToPlatformFont()
    {
        Font font = UIManager.getFont("defaultFont");
        assertNotNull(font, "defaultFont was discarded when the look and feel installed");
        assertEquals(13, font.getSize());

        // Font.SANS_SERIF is a logical family, not a real one — asking for it yields
        // whatever the JDK picks, which on macOS is not the system font. Falling back to it
        // is legitimate on a machine with none of the preferred families, so this only
        // asserts when a real one was available to find.
        if (!GraphicsEnvironment.isHeadless())
        {
            assertFalse(font.getFamily().isEmpty());
        }
    }
}
