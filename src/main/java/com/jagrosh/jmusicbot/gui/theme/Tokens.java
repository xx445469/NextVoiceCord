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
package com.jagrosh.jmusicbot.gui.theme;

import java.awt.Color;
import java.awt.Font;

import javax.swing.UIManager;

/**
 * The design tokens the interface is built from.
 *
 * <p>Every colour, size and weight in one place. Scattering them across panels is what
 * produces a window where three greys are almost the same and two paddings are almost equal —
 * differences too small to be intentional and too large to be invisible.
 *
 * <p>The palette follows Discord's, because this is a Discord bot and the two are looked at
 * side by side. Matching it means the window reads as part of the same tool rather than a
 * separate utility that happens to control one.
 *
 * <p>Surfaces are named by depth rather than by colour, so the same names work in both
 * themes: {@link #surfaceSunken()} is darker than {@link #surface()} in dark mode and lighter
 * in light mode. Panels ask for a role, never for a specific grey.
 *
 * @author adan (xx445469)
 */
public final class Tokens
{
    // ---- Spacing -----------------------------------------------------------
    // A four-point scale. Anything between two steps is a decision nobody made on purpose.

    public static final int SPACE_XS = 4;
    public static final int SPACE_SM = 8;
    public static final int SPACE_MD = 16;
    public static final int SPACE_LG = 24;
    public static final int SPACE_XL = 32;

    /** Corner radius for cards and inputs. */
    public static final int RADIUS = 10;
    /** Corner radius for small controls: pills, badges, sidebar items. */
    public static final int RADIUS_SM = 6;

    // ---- Discord's palette --------------------------------------------------

    private static final Color BLURPLE = new Color(0x58, 0x65, 0xF2);
    private static final Color BLURPLE_HOVER = new Color(0x4A, 0x52, 0xE0);

    private static final Color GREEN = new Color(0x23, 0xA5, 0x5A);
    private static final Color YELLOW = new Color(0xF0, 0xB2, 0x32);
    private static final Color RED = new Color(0xF2, 0x3F, 0x43);

    // Dark surfaces, from Discord's own hierarchy.
    private static final Color DARK_SUNKEN = new Color(0x1E, 0x1F, 0x22);
    private static final Color DARK_SIDEBAR = new Color(0x2B, 0x2D, 0x31);
    private static final Color DARK_SURFACE = new Color(0x31, 0x33, 0x38);
    private static final Color DARK_RAISED = new Color(0x38, 0x3A, 0x40);
    private static final Color DARK_BORDER = new Color(0x3F, 0x42, 0x48);
    private static final Color DARK_TEXT = new Color(0xF2, 0xF3, 0xF5);
    private static final Color DARK_MUTED = new Color(0x94, 0x9B, 0xA4);

    private static final Color LIGHT_SUNKEN = new Color(0xE3, 0xE5, 0xE8);
    private static final Color LIGHT_SIDEBAR = new Color(0xF2, 0xF3, 0xF5);
    private static final Color LIGHT_SURFACE = new Color(0xFF, 0xFF, 0xFF);
    private static final Color LIGHT_RAISED = new Color(0xF7, 0xF8, 0xF9);
    private static final Color LIGHT_BORDER = new Color(0xE0, 0xE2, 0xE6);
    private static final Color LIGHT_TEXT = new Color(0x06, 0x06, 0x07);
    private static final Color LIGHT_MUTED = new Color(0x5C, 0x5E, 0x66);

    private Tokens() { }

    /**
     * Whether the active theme is dark.
     *
     * <p>Read from the look and feel's own background rather than tracked separately, so a
     * theme switch is reflected without anything having to be told about it.
     */
    public static boolean isDark()
    {
        Color background = UIManager.getColor("Panel.background");
        if (background == null)
        {
            return true;
        }
        // Rec. 601 luma. A plain channel average misreads strongly tinted themes, since
        // green contributes far more to perceived brightness than blue.
        double luma = (0.299 * background.getRed()
                     + 0.587 * background.getGreen()
                     + 0.114 * background.getBlue()) / 255;
        return luma < 0.5;
    }

    // ---- Surfaces, by depth -------------------------------------------------

    /** Deepest layer: behind everything, and the log background. */
    public static Color surfaceSunken()
    {
        return isDark() ? DARK_SUNKEN : LIGHT_SUNKEN;
    }

    /** Navigation column. */
    public static Color surfaceSidebar()
    {
        return isDark() ? DARK_SIDEBAR : LIGHT_SIDEBAR;
    }

    /** Default content background. */
    public static Color surface()
    {
        return isDark() ? DARK_SURFACE : LIGHT_SURFACE;
    }

    /** Cards and anything sitting above the content background. */
    public static Color surfaceRaised()
    {
        return isDark() ? DARK_RAISED : LIGHT_RAISED;
    }

    /** Hairlines between regions. */
    public static Color border()
    {
        return isDark() ? DARK_BORDER : LIGHT_BORDER;
    }

    // ---- Text ---------------------------------------------------------------

    public static Color text()
    {
        return isDark() ? DARK_TEXT : LIGHT_TEXT;
    }

    /** Secondary text: labels, metadata, anything supporting the primary content. */
    public static Color textMuted()
    {
        return isDark() ? DARK_MUTED : LIGHT_MUTED;
    }

    // ---- Accents ------------------------------------------------------------

    public static Color accent()
    {
        return BLURPLE;
    }

    public static Color accentHover()
    {
        return BLURPLE_HOVER;
    }

    /** Playing, connected, healthy. */
    public static Color success()
    {
        return GREEN;
    }

    /** Degraded but working. */
    public static Color warning()
    {
        return YELLOW;
    }

    /** Failed, disconnected. */
    public static Color danger()
    {
        return RED;
    }

    // ---- Type ---------------------------------------------------------------

    /**
     * A type scale derived from the configured base size.
     *
     * <p>Relative rather than fixed, so the GUI font-size setting continues to work — a fixed
     * scale would silently ignore it.
     */
    public static Font font(float relativeSize, int style)
    {
        Font base = UIManager.getFont("defaultFont");
        if (base == null)
        {
            base = UIManager.getFont("Label.font");
        }
        if (base == null)
        {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        }
        return base.deriveFont(style, Math.max(9f, base.getSize2D() + relativeSize));
    }

    /** Page titles. */
    public static Font fontTitle()
    {
        return font(7f, Font.BOLD);
    }

    /** Card headings and section labels. */
    public static Font fontHeading()
    {
        return font(1f, Font.BOLD);
    }

    /** Body text. */
    public static Font fontBody()
    {
        return font(0f, Font.PLAIN);
    }

    /** Metadata, captions, anything secondary. */
    public static Font fontSmall()
    {
        return font(-1.5f, Font.PLAIN);
    }

    /** Large figures on stat tiles. */
    public static Font fontStat()
    {
        return font(11f, Font.BOLD);
    }

    /** Log output and anything that must align in columns. */
    public static Font fontMono()
    {
        Font base = UIManager.getFont("defaultFont");
        float size = base == null ? 12f : base.getSize2D() - 0.5f;
        return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(Math.max(10f, size)));
    }
}
