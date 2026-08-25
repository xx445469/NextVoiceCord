/*
 * Copyright 2026 Arif Banai (arif-banai)
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
package com.jagrosh.jmusicbot.gui.theme;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Manages FlatLaf theme initialization and runtime theme switching.
 * This class must be initialized BEFORE any Swing components are created.
 *
 * @author Arif Banai (arif-banai)
 */
public final class ThemeManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(ThemeManager.class);
    
    /**
     * Available themes for the application.
     */
    public enum Theme {
        LIGHT("Light", "light"),
        DARK("Dark", "dark"),
        DARCULA("Darcula", "darcula"),
        INTELLIJ("IntelliJ", "intellij");
        
        private final String displayName;
        private final String configKey;
        
        Theme(String displayName, String configKey) {
            this.displayName = displayName;
            this.configKey = configKey;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getConfigKey() {
            return configKey;
        }
        
        /**
         * Parses a theme from its config key, defaulting to DARK if not found.
         */
        public static Theme fromConfigKey(String key) {
            if (key == null || key.isBlank()) {
                return DARK;
            }
            String normalizedKey = key.toLowerCase().trim();
            for (Theme theme : values()) {
                if (theme.configKey.equals(normalizedKey)) {
                    return theme;
                }
            }
            LOG.warn("Unknown theme '{}', defaulting to Dark", key);
            return DARK;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    private static Theme currentTheme = Theme.DARK;
    private static boolean initialized = false;
    private static int baseFontSize = 12;
    
    private ThemeManager() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Initializes FlatLaf with the specified theme.
     * MUST be called before any Swing components are created.
     *
     * @param themeName the theme config key (light, dark, darcula, intellij)
     */
    public static void initialize(String themeName) {
        initialize(Theme.fromConfigKey(themeName), 12);
    }
    
    /**
     * Initializes FlatLaf with the specified theme and font size.
     * MUST be called before any Swing components are created.
     *
     * @param themeName the theme config key (light, dark, darcula, intellij)
     * @param fontSize the base font size
     */
    public static void initialize(String themeName, int fontSize) {
        initialize(Theme.fromConfigKey(themeName), fontSize);
    }
    
    /**
     * Initializes FlatLaf with the specified theme.
     * MUST be called before any Swing components are created.
     *
     * @param theme the theme to apply
     * @param fontSize the base font size
     */
    public static void initialize(Theme theme, int fontSize) {
        if (initialized) {
            LOG.debug("ThemeManager already initialized, switching theme instead");
            setTheme(theme);
            return;
        }
        
        currentTheme = theme;
        baseFontSize = Math.max(8, Math.min(24, fontSize)); // Clamp between 8-24
        
        // Configure FlatLaf before initialization
        configureFlatLaf();
        
        try {
            applyThemeLookAndFeel(theme);
            initialized = true;
            LOG.info("Initialized FlatLaf with {} theme (font size: {})", theme.getDisplayName(), baseFontSize);
        } catch (Exception e) {
            LOG.error("Failed to initialize FlatLaf, falling back to system L&F", e);
            tryFallbackLookAndFeel();
        }
    }
    
    /**
     * Configures FlatLaf global settings before initialization.
     */
    private static void configureFlatLaf() {
        // --- Shape -------------------------------------------------------------
        // A single radius across every component is what makes a set of controls read as
        // one design rather than several. 10 rather than 8: at small sizes a smaller
        // radius is barely distinguishable from a square corner.
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("ProgressBar.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("CheckBox.arc", 6);

        // --- Type --------------------------------------------------------------
        // The platform UI font, not Font.SANS_SERIF. The generic family resolves to
        // whatever the JDK picks — on macOS that is not the system font, and a window in
        // the wrong typeface reads as foreign no matter what else is done to it.
        UIManager.put("defaultFont", resolveUiFont(baseFontSize));

        // --- Spacing -----------------------------------------------------------
        // Swing defaults are cramped by modern standards. Most of the impression of a
        // "dated" interface is density rather than colour.
        UIManager.put("Button.margin", new Insets(6, 14, 6, 14));
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 1);
        UIManager.put("TextComponent.margin", new Insets(4, 8, 4, 8));
        UIManager.put("Table.rowHeight", baseFontSize + 14);
        UIManager.put("List.cellHeight", baseFontSize + 12);

        // --- Tabs --------------------------------------------------------------
        // Card tabs with an underline, which is the current convention. Separators are
        // dropped: with cards they draw a second boundary around something already bounded.
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("TabbedPane.tabType", "card");
        UIManager.put("TabbedPane.tabHeight", baseFontSize + 22);
        UIManager.put("TabbedPane.tabInsets", new Insets(6, 14, 6, 14));
        UIManager.put("TabbedPane.selectedBackground", null);
        UIManager.put("TabbedPane.tabSeparatorsFullHeight", false);

        // --- Scrollbars --------------------------------------------------------
        // Stepper arrows were removed from every major platform years ago; keeping them
        // is one of the strongest "old Java application" signals a window can send.
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollPane.smoothScrolling", true);

        // --- Titled borders ----------------------------------------------------
        // The etched groove is the single most dated thing in a Swing window. Flattened to
        // a hairline so existing TitledBorder use reads as a modern section rule without
        // having to rewrite every panel.
        UIManager.put("TitledBorder.titleColor", UIManager.getColor("Label.foreground"));

        // --- Separators and popups ---------------------------------------------
        UIManager.put("Separator.stripeWidth", 1);
        UIManager.put("PopupMenu.borderInsets", new Insets(4, 2, 4, 2));
        UIManager.put("MenuItem.selectionArc", 6);

        // --- Platform ----------------------------------------------------------
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "NextVoiceCord");
        // Lets the title bar adopt the theme instead of staying light above a dark window.
        System.setProperty("apple.awt.application.appearance", "system");
    }

    /**
     * Picks the platform's UI font, falling back only when nothing suitable exists.
     *
     * <p>Asking for {@code Font.SANS_SERIF} yields a generic family that is not what the
     * rest of the desktop uses. Matching the platform font is the cheapest single change
     * that stops a Swing window looking out of place.
     */
    private static Font resolveUiFont(int size) {
        String[] preferred = {
            "SF Pro Text", ".AppleSystemUIFont", "Helvetica Neue",   // macOS
            "Segoe UI Variable Text", "Segoe UI",                    // Windows
            "Inter", "Ubuntu", "Cantarell", "Noto Sans"              // Linux
        };

        java.util.Set<String> available = new java.util.HashSet<>(java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));

        for (String family : preferred) {
            if (available.contains(family)) {
                return new Font(family, Font.PLAIN, size);
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }
    
    /**
     * Applies the specified theme's Look and Feel.
     */
    private static void applyThemeLookAndFeel(Theme theme) throws UnsupportedLookAndFeelException {
        FlatLaf laf = switch (theme) {
            case LIGHT -> new FlatLightLaf();
            case DARK -> new FlatDarkLaf();
            case DARCULA -> new FlatDarculaLaf();
            case INTELLIJ -> new FlatIntelliJLaf();
        };
        UIManager.setLookAndFeel(laf);
    }
    
    /**
     * Falls back to system Look and Feel if FlatLaf fails.
     */
    private static void tryFallbackLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            initialized = true;
        } catch (Exception e) {
            LOG.error("Failed to set system L&F, using default", e);
        }
    }
    
    /**
     * Changes the theme at runtime and updates all windows.
     *
     * @param theme the new theme to apply
     */
    public static void setTheme(Theme theme) {
        if (theme == currentTheme) {
            return;
        }
        
        try {
            applyThemeLookAndFeel(theme);
            currentTheme = theme;
            
            // Update all existing windows
            updateAllWindows();
            
            LOG.info("Switched to {} theme", theme.getDisplayName());
        } catch (Exception e) {
            LOG.error("Failed to switch to {} theme", theme.getDisplayName(), e);
        }
    }
    
    /**
     * Updates the Look and Feel of all existing windows.
     */
    private static void updateAllWindows() {
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            window.pack();
        }
    }
    
    /**
     * Gets the current theme.
     */
    public static Theme getCurrentTheme() {
        return currentTheme;
    }
    
    /**
     * Gets all available themes.
     */
    public static List<Theme> getAvailableThemes() {
        return List.of(Theme.values());
    }
    
    /**
     * Checks if a dark theme is currently active.
     */
    public static boolean isDarkTheme() {
        return currentTheme == Theme.DARK || currentTheme == Theme.DARCULA;
    }
    
    /**
     * Gets the current base font size.
     */
    public static int getBaseFontSize() {
        return baseFontSize;
    }
    
    /**
     * Sets the base font size and updates all windows.
     */
    public static void setBaseFontSize(int size) {
        int newSize = Math.max(8, Math.min(24, size));
        if (newSize == baseFontSize) {
            return;
        }
        
        baseFontSize = newSize;
        
        // Update font defaults
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, baseFontSize));
        
        // Reapply theme to update fonts
        try {
            applyThemeLookAndFeel(currentTheme);
            updateAllWindows();
            LOG.info("Updated base font size to {}", baseFontSize);
        } catch (Exception e) {
            LOG.error("Failed to update font size", e);
        }
    }
    
    /**
     * Checks if ThemeManager has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
