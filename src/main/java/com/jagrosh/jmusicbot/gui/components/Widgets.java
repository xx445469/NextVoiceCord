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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import com.jagrosh.jmusicbot.gui.theme.Tokens;

/**
 * The building blocks the redesigned interface is assembled from.
 *
 * <p>Panels compose these rather than drawing their own boxes. Two panels each rounding their
 * own corners is how a window ends up with two radii that differ by a pixel, and every widget
 * that reads a colour directly is one that stops following a theme switch.
 *
 * @author adan (xx445469)
 */
public final class Widgets
{
    private Widgets() { }

    /**
     * A rounded surface that sits above the page background.
     *
     * <p>Painted rather than bordered because Swing borders are drawn inside a rectangular
     * component: a rounded border leaves the original square corners showing behind it,
     * which is visible against any background that is not the same colour.
     */
    public static class Card extends JPanel
    {
        private final int radius;

        public Card()
        {
            this(Tokens.RADIUS);
        }

        public Card(int radius)
        {
            this.radius = radius;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(
                    Tokens.SPACE_MD, Tokens.SPACE_MD, Tokens.SPACE_MD, Tokens.SPACE_MD));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(Tokens.surfaceRaised());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

            g2.setColor(Tokens.border());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A card holding a heading and a body, which is most of what the panels need. */
    public static Card titledCard(String title, Component body)
    {
        Card card = new Card();
        card.setLayout(new BorderLayout(0, Tokens.SPACE_SM));

        JLabel heading = new JLabel(title);
        heading.setFont(Tokens.fontHeading());
        heading.setForeground(Tokens.text());

        card.add(heading, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /**
     * A titled card whose body can be tucked away behind its own heading.
     *
     * <p>For settings a first-time reader does not need to see. The heading — a chevron plus
     * the title, rendered as one clickable button rather than a separate icon and a separate
     * label — stays visible either way; only the body beneath it collapses. Anything placed in
     * that body, warning included, is hidden or shown as a unit: opening the section always
     * shows the whole of it, never a part with the warning left out.
     */
    public static class CollapsibleCard extends Card
    {
        private final JButton header;
        private final JPanel bodyWrapper;
        private final String title;
        private boolean userExpanded;
        private boolean filterActive;
        private boolean filterExpanded;
        private Consumer<Boolean> onToggle;

        public CollapsibleCard(String title, Component body, boolean initiallyExpanded)
        {
            this.title = title;
            this.userExpanded = initiallyExpanded;

            setLayout(new BorderLayout(0, Tokens.SPACE_SM));

            header = new JButton();
            header.setFont(Tokens.fontHeading());
            header.setForeground(Tokens.text());
            header.setHorizontalAlignment(SwingConstants.LEFT);
            header.setBorderPainted(false);
            header.setContentAreaFilled(false);
            header.setFocusPainted(false);
            header.setOpaque(false);
            header.setMargin(new Insets(0, 0, 0, 0));
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            header.addActionListener(e ->
            {
                userExpanded = !userExpanded;
                // A click is the user overriding whatever the search filter was showing —
                // their choice should stick, not be clobbered the next time applyVisibility runs.
                filterActive = false;
                applyVisibility();
                if (onToggle != null)
                {
                    onToggle.accept(userExpanded);
                }
            });

            bodyWrapper = new JPanel(new BorderLayout());
            bodyWrapper.setOpaque(false);
            bodyWrapper.add(body, BorderLayout.CENTER);

            add(header, BorderLayout.NORTH);
            add(bodyWrapper, BorderLayout.CENTER);

            applyVisibility();
        }

        /**
         * Shows or hides the body regardless of the stored preference, without changing it —
         * for the search filter to call while a query is active.
         */
        public void setFilterExpanded(boolean expanded)
        {
            filterActive = true;
            filterExpanded = expanded;
            applyVisibility();
        }

        /** Goes back to whatever the user (or the persisted preference) had this set to. */
        public void clearFilterOverride()
        {
            filterActive = false;
            applyVisibility();
        }

        /** Whether the body is currently shown — for tests, and for the filter's own bookkeeping. */
        public boolean isBodyVisible()
        {
            return bodyWrapper.isVisible();
        }

        /** Notified with the new user-chosen state whenever the heading is clicked. */
        public void onToggle(Consumer<Boolean> listener)
        {
            this.onToggle = listener;
        }

        private void applyVisibility()
        {
            boolean expanded = filterActive ? filterExpanded : userExpanded;
            bodyWrapper.setVisible(expanded);
            header.setText((expanded ? "▾  " : "▸  ") + title);
            revalidate();
            repaint();
        }
    }

    /**
     * One number and what it means.
     *
     * <p>The figure is set several steps larger than the label rather than one, because a
     * tile whose number and caption are nearly the same size reads as two labels instead of a
     * measurement.
     */
    public static class StatTile extends Card
    {
        private final JLabel value = new JLabel("—");
        private final JLabel caption;

        public StatTile(String caption)
        {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            value.setFont(Tokens.fontStat());
            value.setForeground(Tokens.text());
            value.setAlignmentX(LEFT_ALIGNMENT);

            this.caption = new JLabel(caption);
            this.caption.setFont(Tokens.fontSmall());
            this.caption.setForeground(Tokens.textMuted());
            this.caption.setAlignmentX(LEFT_ALIGNMENT);

            add(value);
            add(Box.createVerticalStrut(Tokens.SPACE_XS / 2));
            add(this.caption);
        }

        public void setValue(String text)
        {
            value.setText(text);
        }

        /** Tints the figure — for a count that means something is wrong. */
        public void setValueColor(Color color)
        {
            value.setForeground(color);
        }

        public void setCaption(String text)
        {
            caption.setText(text);
        }
    }

    /**
     * A small coloured pill: playing, idle, connected.
     *
     * <p>Reads at a glance in a way a coloured word does not, which is the point on a page
     * listing many servers at once.
     */
    public static class Badge extends JLabel
    {
        private Color fill;

        public Badge(String text, Color fill)
        {
            super(text, SwingConstants.CENTER);
            this.fill = fill;
            setFont(Tokens.font(-2f, java.awt.Font.BOLD));
            setForeground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
            setOpaque(false);
        }

        public void set(String text, Color fill)
        {
            setText(text);
            this.fill = fill;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * A thin progress bar.
     *
     * <p>Not a {@link javax.swing.JProgressBar}: that component carries platform chrome —
     * bevels, gradients, a border — which is exactly the dated look this redesign is removing.
     */
    public static class Meter extends JPanel
    {
        private double fraction;
        private Color fill = Tokens.accent();

        public Meter()
        {
            setOpaque(false);
            setPreferredSize(new Dimension(0, 6));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        }

        /** @param fraction 0 to 1; values outside are clamped rather than drawn overflowing */
        public void setFraction(double fraction)
        {
            this.fraction = Math.max(0, Math.min(1, fraction));
            repaint();
        }

        public void setFill(Color fill)
        {
            this.fill = fill;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int h = getHeight();
            g2.setColor(Tokens.border());
            g2.fillRoundRect(0, 0, getWidth(), h, h, h);

            int filled = (int) Math.round(getWidth() * fraction);
            if (filled > 0)
            {
                g2.setColor(fill);
                // Never narrower than its own height, or a small fraction paints as a sliver
                // that reads as a rendering fault rather than a low value.
                g2.fillRoundRect(0, 0, Math.max(filled, h), h, h, h);
            }
            g2.dispose();
        }
    }

    /** A page heading, used once at the top of each view. */
    public static JLabel pageTitle(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(Tokens.fontTitle());
        label.setForeground(Tokens.text());
        return label;
    }

    /** Secondary text. */
    public static JLabel muted(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(Tokens.fontSmall());
        label.setForeground(Tokens.textMuted());
        return label;
    }

    /** A transparent container, so nested panels do not paint over a card's rounded corners. */
    public static JPanel transparent(java.awt.LayoutManager layout)
    {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }
}
