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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.jagrosh.jmusicbot.gui.theme.Tokens;

/**
 * The navigation column.
 *
 * <p>Replaces a row of tabs. Tabs work while there are three or four of them and there is no
 * hierarchy between them; there were seven here, of two clearly different kinds — things you
 * watch, and things you configure — and a horizontal strip cannot express that difference.
 * A column can, by grouping and separating.
 *
 * <p>It also matches Discord, which is the window this one sits beside.
 *
 * @author adan (xx445469)
 */
public final class Sidebar extends JPanel
{
    private static final int WIDTH = 188;

    private final List<Item> items = new ArrayList<>();
    private final Consumer<String> onSelect;
    private String selectedKey;

    /**
     * @param onSelect receives the key of the view to show
     */
    public Sidebar(Consumer<String> onSelect)
    {
        this.onSelect = onSelect;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Tokens.surfaceSidebar());
        setPreferredSize(new Dimension(WIDTH, 0));
        setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_SM, Tokens.SPACE_SM, Tokens.SPACE_SM, Tokens.SPACE_SM));
    }

    /** Adds a navigation entry. */
    public void addItem(String key, String label, Icon icon)
    {
        Item item = new Item(key, label, icon);
        items.add(item);
        add(item);
        add(Box.createVerticalStrut(2));

        if (selectedKey == null)
        {
            select(key);
        }
    }

    /** A group heading, for separating what you watch from what you configure. */
    public void addSection(String label)
    {
        add(Box.createVerticalStrut(Tokens.SPACE_MD));

        JLabel heading = new JLabel(label.toUpperCase(java.util.Locale.ROOT));
        heading.setFont(Tokens.font(-3f, java.awt.Font.BOLD));
        heading.setForeground(Tokens.textMuted());
        heading.setBorder(BorderFactory.createEmptyBorder(0, Tokens.SPACE_SM + 2, Tokens.SPACE_XS, 0));
        heading.setAlignmentX(LEFT_ALIGNMENT);

        add(heading);
    }

    /**
     * Adds an action rather than a view — something that happens instead of somewhere to go.
     *
     * <p>Styled like the navigation entries so the column reads as one list, but never takes
     * the selected state, because nothing here stays selected.
     */
    public void addAction(String label, Icon icon, Runnable action)
    {
        Item item = new Item("__action__" + label, label, icon);
        item.action = action;
        add(item);
        add(Box.createVerticalStrut(2));
    }

    /** Pushes everything after it to the bottom of the column. */
    public void addSpacer()
    {
        add(Box.createVerticalGlue());
    }

    /** Selects an entry and notifies the listener. */
    public void select(String key)
    {
        selectedKey = key;
        items.forEach(item -> item.setSelected(item.key.equals(key)));
        onSelect.accept(key);
    }

    /** One navigation entry. */
    private final class Item extends JPanel
    {
        private final String key;
        private final JLabel label;
        /** Set for action entries; when present, clicking runs this instead of selecting. */
        private Runnable action;
        private boolean selected;
        private boolean hovered;

        Item(String key, String text, Icon icon)
        {
            this.key = key;

            setLayout(new BorderLayout(Tokens.SPACE_SM, 0));
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(7, Tokens.SPACE_SM + 2, 7, Tokens.SPACE_SM));
            setAlignmentX(LEFT_ALIGNMENT);
            // Height is fixed so BoxLayout does not stretch entries to fill the column, which
            // would space them out differently depending on how many there are.
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

            label = new JLabel(text, icon, JLabel.LEFT);
            label.setIconTextGap(Tokens.SPACE_SM + 2);
            label.setFont(Tokens.fontBody());
            add(label, BorderLayout.CENTER);

            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    if (action != null)
                    {
                        action.run();
                        return;
                    }
                    select(Item.this.key);
                }

                @Override
                public void mouseEntered(MouseEvent e)
                {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    hovered = false;
                    repaint();
                }
            });

            refreshColors();
        }

        void setSelected(boolean selected)
        {
            this.selected = selected;
            refreshColors();
            repaint();
        }

        private void refreshColors()
        {
            // Selected text goes to full contrast, unselected stays muted. Colour alone would
            // carry the state, but the weight difference is what makes it obvious at a glance.
            label.setForeground(selected ? Tokens.text() : Tokens.textMuted());
            label.setFont(selected ? Tokens.font(0f, java.awt.Font.BOLD) : Tokens.fontBody());
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            if (!selected && !hovered)
            {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color fill = selected
                    ? blend(Tokens.accent(), Tokens.surfaceSidebar(), 0.72f)
                    : blend(Tokens.text(), Tokens.surfaceSidebar(), 0.92f);

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), Tokens.RADIUS_SM, Tokens.RADIUS_SM);
            g2.dispose();
        }
    }

    private static Color blend(Color from, Color to, float amount)
    {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }
}
