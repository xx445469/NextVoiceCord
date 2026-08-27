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
import javax.swing.JScrollPane;
import javax.swing.Scrollable;

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
 * <p>{@link #addExpandableItem} adds a further level: an entry whose page is itself several
 * titled cards, listed underneath it so a reader can see what the page holds — and jump straight
 * to one of them — without opening the page first and scrolling to find out. A page with a lot of
 * cards, expanded at a small window size, can need more vertical room than the window has, so
 * the column itself scrolls rather than letting the rest of it become unreachable.
 *
 * @author adan (xx445469)
 */
public final class Sidebar extends JPanel
{
    private static final int WIDTH = 188;
    /** Width of the disclosure triangle's own column, so a group's icon lines up under it. */
    private static final int CARET_WIDTH = 18;
    /** Left inset for a category row: past the caret column, under the parent's icon and label. */
    private static final int CHILD_INDENT = CARET_WIDTH + Tokens.SPACE_SM + 2;

    /** The actual list of entries — see the class javadoc for why this is not {@code this}. */
    private final JPanel column;
    private final List<NavEntry> entries = new ArrayList<>();
    private final Consumer<String> onSelect;
    private String selectedKey;

    /**
     * @param onSelect receives the key of the view to show
     */
    public Sidebar(Consumer<String> onSelect)
    {
        this.onSelect = onSelect;

        setLayout(new BorderLayout());
        setBackground(Tokens.surfaceSidebar());
        setPreferredSize(new Dimension(WIDTH, 0));

        column = new Column();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setBackground(Tokens.surfaceSidebar());
        column.setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_SM, Tokens.SPACE_SM, Tokens.SPACE_SM, Tokens.SPACE_SM));

        JScrollPane scroll = Widgets.scrollable(column);
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * A column that fills the width it is given rather than shrinking to its narrowest child —
     * plain {@link JPanel} would otherwise size itself down to whatever the longest label needs
     * once it is inside a {@link JScrollPane}, instead of the fixed {@link #WIDTH} every row here
     * is laid out against — but still reports its own preferred height, so the pane scrolls
     * vertically rather than trying to fit an expanded group into the space it has.
     */
    private static final class Column extends JPanel implements Scrollable
    {
        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
        {
            return orientation == javax.swing.SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    /** Adds a navigation entry. */
    public void addItem(String key, String label, Icon icon)
    {
        Item item = new Item(key, label, icon);
        entries.add(item);
        column.add(item);
        column.add(Box.createVerticalStrut(2));

        if (selectedKey == null)
        {
            select(key);
        }
    }

    /** A group heading, for separating what you watch from what you configure. */
    public void addSection(String label)
    {
        column.add(Box.createVerticalStrut(Tokens.SPACE_MD));

        JLabel heading = new JLabel(label.toUpperCase(java.util.Locale.ROOT));
        heading.setFont(Tokens.font(-3f, java.awt.Font.BOLD));
        heading.setForeground(Tokens.textMuted());
        heading.setBorder(BorderFactory.createEmptyBorder(0, Tokens.SPACE_SM + 2, Tokens.SPACE_XS, 0));
        heading.setAlignmentX(LEFT_ALIGNMENT);

        column.add(heading);
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
        column.add(item);
        column.add(Box.createVerticalStrut(2));
    }

    /** Pushes everything after it to the bottom of the column. */
    public void addSpacer()
    {
        column.add(Box.createVerticalGlue());
    }

    /**
     * Adds a navigation entry that expands to list the categories within the page it opens.
     *
     * <p>The disclosure triangle opens or closes that list on its own, independently of which
     * page is currently showing — so a reader can check what a page holds without leaving
     * whatever they are already looking at. Clicking the rest of the row still opens the page,
     * exactly like {@link #addItem} always has; clicking a category does the same and then runs
     * that category's own action — ordinarily "scroll to this card" — once the switch has
     * happened. Whichever page is current has its list open regardless of the triangle, so a
     * category that is already showing is never left behind a collapsed heading.
     *
     * @param categories this entry's page's cards, in the order they appear on that page
     */
    public void addExpandableItem(String key, String label, Icon icon, List<Category> categories)
    {
        Group group = new Group(key, label, icon, categories);
        entries.add(group);
        column.add(group);
        column.add(Box.createVerticalStrut(2));

        if (selectedKey == null)
        {
            select(key);
        }
    }

    /** One category inside an {@link #addExpandableItem} page: its heading, and what choosing it does. */
    public record Category(String title, Runnable onSelect) { }

    /** Selects an entry and notifies the listener. */
    public void select(String key)
    {
        selectedKey = key;
        entries.forEach(entry -> entry.updateSelection(key));
        onSelect.accept(key);
    }

    /** Common surface for whatever can be "the selected thing" in the column: a plain item or a group. */
    private interface NavEntry
    {
        void updateSelection(String selectedKey);
    }

    /** One navigation entry. */
    private final class Item extends JPanel implements NavEntry
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

        @Override
        public void updateSelection(String selectedKey)
        {
            setSelected(key.equals(selectedKey));
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

    /**
     * An {@link #addItem}-style entry with a list of categories tucked underneath it, shown or
     * hidden behind a disclosure triangle — the same chevron glyph
     * {@link Widgets.CollapsibleCard} uses for the same idea inside a page, so the column does
     * not invent a second visual language for "this can open to show more."
     */
    private final class Group extends JPanel implements NavEntry
    {
        private final String key;
        private final Item header;
        private final JLabel caret;
        private final JPanel childList;
        private final List<ChildRow> rows = new ArrayList<>();
        /** Set by the triangle; independent of whether this group's page is the current view. */
        private boolean manuallyExpanded;
        private boolean pageIsCurrent;

        Group(String key, String label, Icon icon, List<Category> categories)
        {
            this.key = key;

            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);

            JPanel headerRow = new JPanel(new BorderLayout(0, 0));
            headerRow.setOpaque(false);
            headerRow.setAlignmentX(LEFT_ALIGNMENT);
            headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

            caret = new JLabel(caretGlyph());
            caret.setForeground(Tokens.textMuted());
            // The same size and weight Widgets.CollapsibleCard renders its own chevron at — at
            // the smaller sizes used for row text elsewhere in this column, on this JVM's system
            // UI font this exact glyph rendered as a barely-visible dot rather than a triangle.
            caret.setFont(Tokens.fontHeading());
            caret.setHorizontalAlignment(JLabel.CENTER);
            caret.setPreferredSize(new Dimension(CARET_WIDTH, 34));
            caret.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // A separate hit target from the row itself: this toggles the list open or closed
            // without navigating, so checking what a page holds never switches away from
            // whatever is currently showing.
            caret.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    manuallyExpanded = !manuallyExpanded;
                    refreshExpansion();
                }
            });
            headerRow.add(caret, BorderLayout.WEST);

            header = new Item(key, label, icon);
            // Overrides Item's default "select on click": a header click must also drop
            // whichever category was last current, since it is no longer specifically showing —
            // the reader asked for the top of the page, not a card on it.
            header.action = () ->
            {
                rows.forEach(row -> row.setCurrent(false));
                select(key);
            };
            headerRow.add(header, BorderLayout.CENTER);

            add(headerRow);
            add(Box.createVerticalStrut(2));

            childList = new JPanel();
            childList.setLayout(new BoxLayout(childList, BoxLayout.Y_AXIS));
            childList.setOpaque(false);
            childList.setAlignmentX(LEFT_ALIGNMENT);

            for (Category category : categories)
            {
                ChildRow row = new ChildRow(category);
                rows.add(row);
                childList.add(row);
            }
            add(childList);

            refreshExpansion();
        }

        @Override
        public void updateSelection(String selectedKey)
        {
            pageIsCurrent = key.equals(selectedKey);
            header.setSelected(pageIsCurrent);
            if (!pageIsCurrent)
            {
                rows.forEach(row -> row.setCurrent(false));
            }
            refreshExpansion();
        }

        private void refreshExpansion()
        {
            boolean expanded = manuallyExpanded || pageIsCurrent;
            childList.setVisible(expanded);
            caret.setText(caretGlyph());
            revalidate();
            repaint();
        }

        private String caretGlyph()
        {
            return (manuallyExpanded || pageIsCurrent) ? "▾" : "▸"; // ▾ / ▸
        }

        /** One category row, indented under the group's icon and label. */
        private final class ChildRow extends JPanel
        {
            private final JLabel label;
            private boolean current;
            private boolean hovered;

            ChildRow(Category category)
            {
                setLayout(new BorderLayout());
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setBorder(BorderFactory.createEmptyBorder(5, CHILD_INDENT, 5, Tokens.SPACE_SM));
                setAlignmentX(LEFT_ALIGNMENT);
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

                label = new JLabel(category.title());
                label.setFont(Tokens.fontSmall());
                add(label, BorderLayout.CENTER);

                addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(MouseEvent e)
                    {
                        rows.forEach(row -> row.setCurrent(row == ChildRow.this));
                        select(key);
                        category.onSelect().run();
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

            void setCurrent(boolean current)
            {
                this.current = current;
                refreshColors();
                repaint();
            }

            private void refreshColors()
            {
                label.setForeground(current ? Tokens.text() : Tokens.textMuted());
                label.setFont(current ? Tokens.font(-1.5f, java.awt.Font.BOLD) : Tokens.fontSmall());
            }

            @Override
            protected void paintComponent(Graphics g)
            {
                if (!current && !hovered)
                {
                    return;
                }

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color fill = current
                        ? blend(Tokens.accent(), Tokens.surfaceSidebar(), 0.72f)
                        : blend(Tokens.text(), Tokens.surfaceSidebar(), 0.92f);

                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), Tokens.RADIUS_SM, Tokens.RADIUS_SM);
                g2.dispose();
            }
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
