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
package com.jagrosh.jmusicbot.gui.components;

import java.awt.Color;
import java.awt.Insets;

import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * The console log view.
 *
 * <p>Replaces a {@link javax.swing.JTextArea}, which can only render one colour. The console
 * is the largest thing in the window and, undifferentiated, it is a wall of identical text —
 * a warning about a broken client looks exactly like a line reporting that a thread pool
 * started. Colouring by level is the difference between reading it and scanning past it.
 *
 * <p>Colours are derived from the theme's own foreground rather than fixed, so they stay
 * legible in both light and dark and follow a theme switch. Only warnings and errors take a
 * hue; everything else is graded by importance, because if every line is coloured then none
 * of them stand out.
 *
 * @author adan (xx445469)
 */
public final class LogView extends JTextPane
{
    /** Amber and red, desaturated enough not to glare on a dark background. */
    private static final Color WARN_LIGHT = new Color(0xB3, 0x6B, 0x00);
    private static final Color WARN_DARK = new Color(0xE8, 0xA3, 0x3D);
    private static final Color ERROR_LIGHT = new Color(0xC0, 0x39, 0x2B);
    private static final Color ERROR_DARK = new Color(0xF2, 0x6D, 0x6D);

    public LogView()
    {
        setEditable(false);
        setMargin(new Insets(8, 10, 8, 10));
    }

    /** Appends one chunk, styling each line by its log level. */
    public void appendLog(String text)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }

        StyledDocument document = getStyledDocument();
        // Split keeping terminators, so a chunk arriving mid-line does not gain a break the
        // logger never wrote.
        for (String line : text.split("(?<=\n)"))
        {
            try
            {
                document.insertString(document.getLength(), line, styleFor(line));
            }
            catch (BadLocationException ex)
            {
                // The only way to get here is a concurrent edit of the document; dropping the
                // line is better than letting logging throw into the caller.
                return;
            }
        }
    }

    /** Removes everything. */
    public void clearLog()
    {
        setText("");
    }

    /**
     * Drops the oldest lines once the buffer exceeds {@code maxLines}.
     *
     * <p>A long-running bot logs continuously, and an unbounded document is a slow memory
     * leak that also makes the pane progressively less responsive.
     */
    public void trimTo(int maxLines)
    {
        StyledDocument document = getStyledDocument();
        javax.swing.text.Element root = document.getDefaultRootElement();

        int excess = root.getElementCount() - maxLines;
        if (excess <= 0)
        {
            return;
        }

        try
        {
            int cut = root.getElement(excess - 1).getEndOffset();
            document.remove(0, cut);
        }
        catch (BadLocationException ex)
        {
            // Same reasoning as above: trimming is housekeeping, not something to fail on.
        }
    }

    private SimpleAttributeSet styleFor(String line)
    {
        SimpleAttributeSet style = new SimpleAttributeSet();
        boolean dark = isDarkTheme();

        if (line.contains("[ERROR]"))
        {
            StyleConstants.setForeground(style, dark ? ERROR_DARK : ERROR_LIGHT);
            StyleConstants.setBold(style, true);
        }
        else if (line.contains("[WARN]"))
        {
            StyleConstants.setForeground(style, dark ? WARN_DARK : WARN_LIGHT);
        }
        else if (line.contains("[DEBUG]") || line.contains("[TRACE]"))
        {
            // Pushed back rather than coloured: debug output is context, and it should not
            // compete with the lines someone is actually looking for.
            StyleConstants.setForeground(style, blend(foreground(), background(), 0.45f));
        }
        else
        {
            StyleConstants.setForeground(style, foreground());
        }
        return style;
    }

    private Color foreground()
    {
        Color color = UIManager.getColor("TextPane.foreground");
        return color != null ? color : Color.DARK_GRAY;
    }

    private Color background()
    {
        Color color = UIManager.getColor("TextPane.background");
        return color != null ? color : Color.WHITE;
    }

    /** Decides light or dark from the actual background, so it follows any theme. */
    private boolean isDarkTheme()
    {
        Color bg = background();
        // Rec. 601 luma: green dominates perceived brightness, so a plain average would
        // misjudge strongly tinted themes.
        double luma = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return luma < 0.5;
    }

    private static Color blend(Color from, Color to, float amount)
    {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }
}
