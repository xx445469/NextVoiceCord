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
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import java.awt.Container;
import java.awt.LayoutManager;
import java.awt.Window;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

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
     *
     * <p>Carries its own soft shadow along the bottom edge and a stronger outline than the
     * hairline used between flat regions ({@link Tokens#cardBorder()} rather than {@link
     * Tokens#border()}) — together what makes a group of controls read as a card rather than a
     * faint rectangle. The shadow is drawn inside the component's own bounds, trimmed from the
     * fill rather than added outside it, so it never depends on the container leaving spare
     * space around the card.
     */
    public static class Card extends JPanel
    {
        private static final int SHADOW = 3;

        private final int radius;

        public Card()
        {
            this(Tokens.RADIUS);
        }

        public Card(int radius)
        {
            this(radius, Tokens.SPACE_LG);
        }

        /** @param padding this card's own outer padding, on all four sides */
        public Card(int radius, int padding)
        {
            this.radius = radius;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int fillHeight = getHeight() - SHADOW;

            // A soft shadow along the bottom edge, faded over a few 1px bands rather than one
            // flat rectangle — a hard-edged shadow reads as a second border, not depth. Inset
            // slightly from both sides so it stays under the card's own rounded corners.
            for (int i = 0; i < SHADOW; i++)
            {
                int alpha = (Tokens.isDark() ? 30 : 18) - i * 8;
                if (alpha <= 0)
                {
                    continue;
                }
                g2.setColor(new Color(0, 0, 0, alpha));
                g2.fillRect(radius / 2, fillHeight + i, Math.max(0, w - radius), 1);
            }

            g2.setColor(Tokens.surfaceRaised());
            g2.fillRoundRect(0, 0, w, fillHeight, radius, radius);

            g2.setColor(Tokens.cardBorder());
            g2.drawRoundRect(0, 0, w - 1, fillHeight - 1, radius, radius);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A card holding a heading and a body, which is most of what the panels need. */
    public static Card titledCard(String title, Component body)
    {
        return titledCard(title, body, Tokens.SPACE_LG, Tokens.SPACE_MD);
    }

    /**
     * {@link #titledCard(String, Component)}, with the outer padding and the heading-to-body
     * gap both spelled out — for a page with its own, tighter density budget rather than the
     * comfortable default every other card on the page uses.
     *
     * @param padding this card's own outer padding, on all four sides
     * @param headingGap the gap between the heading and the body beneath it
     */
    public static Card titledCard(String title, Component body, int padding, int headingGap)
    {
        Card card = new Card(Tokens.RADIUS, padding);
        card.setLayout(new BorderLayout(0, headingGap));

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
            this(title, body, initiallyExpanded, Tokens.SPACE_LG, Tokens.SPACE_MD);
        }

        /**
         * {@link #CollapsibleCard(String, Component, boolean)}, with the outer padding and the
         * heading-to-body gap both spelled out — see {@link Widgets#titledCard(String,
         * Component, int, int)}, which this mirrors for the collapsible case.
         */
        public CollapsibleCard(String title, Component body, boolean initiallyExpanded, int padding, int headingGap)
        {
            super(Tokens.RADIUS, padding);
            this.title = title;
            this.userExpanded = initiallyExpanded;

            setLayout(new BorderLayout(0, headingGap));

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

        /**
         * Opens the section exactly as if its heading had been clicked while collapsed — for
         * navigation that jumps straight here (the sidebar) rather than a person reading down
         * the page and clicking it themselves. A no-op if it is already open, so jumping back to
         * an already-expanded section never collapses it.
         */
        public void expand()
        {
            if (!userExpanded)
            {
                userExpanded = true;
                filterActive = false;
                applyVisibility();
                if (onToggle != null)
                {
                    onToggle.accept(true);
                }
            }
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

    /**
     * A hint beneath a control: what it does or where it is saved, in the reader's eye rather
     * than the control's. Carries its own top margin, so a call site does not need a separate
     * {@code Box.createVerticalStrut} between the two just to keep them from touching.
     */
    public static JLabel hint(String text)
    {
        JLabel label = muted(text);
        label.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_XS, 0, 0, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * The action a panel exists for. Filled with the accent colour so it is found first, as
     * opposed to {@link #secondaryButton(String)} for everything beside it that is not that
     * action.
     *
     * <p>Coloured with an explicit background/foreground rather than FlatLaf's {@code
     * JButton.buttonType = default} alone: that styling only takes effect for a button that is
     * also its {@link javax.swing.JRootPane}'s registered default button (the one Enter
     * activates), which none of these are — the window has no single "the" default action, and
     * making one of these it would mean Enter submitting a form the reader may not have been
     * looking at. An explicit colour reads as primary regardless.
     */
    public static JButton primaryButton(String text)
    {
        JButton button = new JButton(text);
        button.setFont(Tokens.fontBody());
        button.setForeground(Color.WHITE);
        button.setBackground(Tokens.accent());
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /** An ordinary action: present, but not what the panel is for. */
    public static JButton secondaryButton(String text)
    {
        JButton button = new JButton(text);
        button.setFont(Tokens.fontBody());
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /**
     * The outer, page-level scroll area every panel wraps its content in.
     *
     * <p>Leaves a gutter between the content and the scrollbar's own track, which a bare
     * {@link JScrollPane} does not: without it, a card's right edge sits flush against the
     * viewport edge and the scrollbar draws directly on top of it. Centralised here rather than
     * repeated per panel so the gutter — and the rest of the scroll styling — stays one
     * decision instead of eight slightly different copies of it.
     */
    public static JScrollPane scrollable(Component content)
    {
        JPanel gutter = transparent(new BorderLayout());
        gutter.add(content, BorderLayout.CENTER);
        gutter.add(Box.createHorizontalStrut(Tokens.SPACE_SM), BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(gutter);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // Without this the content scrolls a few pixels per notch, because the default unit is
        // derived from a component that is not there.
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /**
     * A boolean control that reads as a switch rather than a box with a tick in it — the more
     * modern convention for a setting that is simply on or off.
     *
     * <p>Still a {@link JCheckBox} underneath: {@code isSelected()}, {@code setSelected()},
     * {@link JCheckBox#addActionListener} and every other {@code AbstractButton} API a caller
     * already relies on keep working exactly as before. Only the painted icon changes, via the
     * same "set a custom icon on the button" mechanism Swing's own look and feel uses to draw
     * the default box — so this never needs its own {@code ButtonUI} and keeps the platform's
     * keyboard and screen-reader behaviour for a checkbox intact.
     */
    public static JCheckBox toggleSwitch(String text)
    {
        JCheckBox box = new JCheckBox(text);
        Icon icon = new SwitchIcon();
        box.setIcon(icon);
        box.setSelectedIcon(icon);
        box.setRolloverIcon(icon);
        box.setRolloverSelectedIcon(icon);
        box.setDisabledIcon(icon);
        box.setDisabledSelectedIcon(icon);
        box.setIconTextGap(Tokens.SPACE_SM);
        box.setOpaque(false);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return box;
    }

    /** {@link #toggleSwitch(String)}, initially selected. */
    public static JCheckBox toggleSwitch(String text, boolean selected)
    {
        JCheckBox box = toggleSwitch(text);
        box.setSelected(selected);
        return box;
    }

    /** The track-and-thumb icon {@link #toggleSwitch(String)} paints in place of a checkbox. */
    private static final class SwitchIcon implements Icon
    {
        private static final int W = 34;
        private static final int H = 18;

        @Override
        public int getIconWidth()
        {
            return W;
        }

        @Override
        public int getIconHeight()
        {
            return H;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            AbstractButton button = (AbstractButton) c;
            boolean selected = button.isSelected();

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color track = selected ? Tokens.accent() : Tokens.surfaceSunken();
            if (!button.isEnabled())
            {
                track = Tokens.border();
            }
            g2.setColor(track);
            g2.fillRoundRect(x, y + 1, W, H - 2, H - 2, H - 2);

            if (!selected)
            {
                g2.setColor(Tokens.cardBorder());
                g2.drawRoundRect(x, y + 1, W - 1, H - 3, H - 2, H - 2);
            }

            int thumbSize = H - 6;
            int thumbX = selected ? x + W - thumbSize - 3 : x + 3;
            g2.setColor(Color.WHITE);
            g2.fillOval(thumbX, y + 3, thumbSize, thumbSize);

            g2.dispose();
        }
    }

    /**
     * A row of mutually exclusive options in one pill, for a short fixed set — the theme, say.
     * Reads faster than a combo box for two to four choices: every option is visible at once,
     * with no menu to open before the current one can even be compared against the others.
     */
    public static final class Segmented extends JPanel
    {
        private final List<JToggleButton> buttons = new java.util.ArrayList<>();
        private int selectedIndex;
        private IntConsumer onChange;

        public Segmented(List<String> labels, int initialIndex)
        {
            setOpaque(false);
            setLayout(new GridLayout(1, labels.size(), 2, 0));
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

            ButtonGroup group = new ButtonGroup();
            for (int i = 0; i < labels.size(); i++)
            {
                int index = i;
                JToggleButton button = new JToggleButton(labels.get(i));
                button.setFont(Tokens.fontSmall());
                button.setFocusPainted(false);
                button.setContentAreaFilled(false);
                button.setBorderPainted(false);
                button.setOpaque(false);
                button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                button.setSelected(index == initialIndex);
                button.addActionListener(e ->
                {
                    selectedIndex = index;
                    refreshColors();
                    if (onChange != null)
                    {
                        onChange.accept(index);
                    }
                });
                group.add(button);
                buttons.add(button);
                add(button);
            }
            selectedIndex = initialIndex;
            refreshColors();
        }

        /** Notified with the newly chosen index whenever the selection changes by click. */
        public void onChange(IntConsumer listener)
        {
            this.onChange = listener;
        }

        public int getSelectedIndex()
        {
            return selectedIndex;
        }

        public void setSelectedIndex(int index)
        {
            if (index < 0 || index >= buttons.size())
            {
                return;
            }
            selectedIndex = index;
            buttons.get(index).setSelected(true);
            refreshColors();
        }

        private void refreshColors()
        {
            for (int i = 0; i < buttons.size(); i++)
            {
                buttons.get(i).setForeground(i == selectedIndex ? Color.WHITE : Tokens.textMuted());
            }
        }

        @Override
        public void paint(java.awt.Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(Tokens.surfaceSunken());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), Tokens.RADIUS_SM, Tokens.RADIUS_SM);

            if (selectedIndex >= 0 && selectedIndex < buttons.size())
            {
                Component selected = buttons.get(selectedIndex);
                g2.setColor(Tokens.accent());
                g2.fillRoundRect(selected.getX(), selected.getY(),
                        selected.getWidth(), selected.getHeight(), Tokens.RADIUS_SM, Tokens.RADIUS_SM);
            }

            g2.dispose();
            super.paint(g);
        }
    }

    /** A transparent container, so nested panels do not paint over a card's rounded corners. */
    public static JPanel transparent(java.awt.LayoutManager layout)
    {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    /**
     * A form panel that packs its rows two to a line once it is wide enough, and falls back to
     * one row per line — the every-page-is-one-column layout every settings card used before —
     * when it is not.
     *
     * <p>A row is added as either {@link #addField(Component)} (a label paired with a short
     * value: a prefix, a port, a toggle's own row) or {@link #addFull(Component)} (something
     * that should always own the whole line regardless of width — a warning, a multi-line note,
     * a list editor). Only {@code addField} rows are ever paired; an {@code addFull} row always
     * starts a fresh line and always ends the one before it.
     *
     * <p>Swing asks a container how tall it wants to be before that container has necessarily
     * been given a final width — for a panel inside a {@link BoxLayout}, well before. Reading
     * this panel's own {@link #getWidth()} at that point would answer with whatever stale value
     * (often zero) is left over from the last layout, which is why {@link #resolveWidth} instead
     * reads the width of the nearest {@link JViewport} ancestor: by the time anything below a
     * scroll pane is asked for a preferred size, the viewport itself has already been given its
     * final size by whatever laid out the scroll pane — one level higher, earlier in the same
     * top-down pass. Actually positioning the rows ({@link #layoutContainer}) runs later still,
     * once this panel's own width is no longer stale, so it reads that directly instead.
     */
    public static final class FormGrid extends JPanel
    {
        /** Below this, a column reads as cramped rather than merely narrow — checked in German. */
        private static final int MIN_COLUMN_WIDTH = 280;
        /**
         * The chrome between a viewport's width and this panel's own: the scrollbar track, the
         * gutter {@link #scrollable} adds beside it, and the padding a {@link Card} and a page's
         * own outer border each contribute. Approximate and deliberately generous — overestimating
         * it only ever costs a little unused space at the bottom of a card; underestimating it is
         * what would let a two-column guess turn out too wide for the space actually available.
         */
        private static final int VIEWPORT_CHROME = 120;
        private static final int COLUMN_GAP = Tokens.SPACE_LG;
        private static final int ROW_GAP = Tokens.SPACE_SM;

        private final Set<Component> fullWidth = Collections.newSetFromMap(new IdentityHashMap<>());

        public FormGrid()
        {
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);
            setLayout(new Flow());
        }

        /** A row paired two-to-a-line once the panel is wide enough for that. */
        public void addField(Component c)
        {
            add(c);
        }

        /** A row that always takes the whole line, at any width. */
        public void addFull(Component c)
        {
            fullWidth.add(c);
            add(c);
        }

        @Override
        public void remove(Component comp)
        {
            fullWidth.remove(comp);
            super.remove(comp);
        }

        private int resolveWidth(boolean exact)
        {
            if (exact)
            {
                int w = getWidth() - getInsets().left - getInsets().right;
                if (w > 0)
                {
                    return w;
                }
            }
            int hint = 0;
            Container viewport = SwingUtilities.getAncestorOfClass(JViewport.class, this);
            if (viewport != null)
            {
                hint = viewport.getWidth();
            }
            if (hint <= 0)
            {
                Window window = SwingUtilities.getWindowAncestor(this);
                hint = window != null ? window.getWidth() : 0;
            }
            if (hint > 0)
            {
                return Math.max(hint - VIEWPORT_CHROME, MIN_COLUMN_WIDTH);
            }
            // Nothing sized anywhere above this yet (a dialog on its first pack, say) — a
            // single, generously wide column is the safe guess rather than reaching for two
            // columns sight unseen.
            return getWidth() > 0 ? getWidth() : 480;
        }

        /**
         * The layout itself. Two public entry points ({@link #preferredLayoutSize} and
         * {@link #layoutContainer}) share one measuring pass ({@link #place}) that both sizes
         * and, when told to, positions every visible row.
         */
        private final class Flow implements LayoutManager
        {
            @Override
            public void addLayoutComponent(String name, Component comp) { }

            @Override
            public void removeLayoutComponent(Component comp) { }

            @Override
            public Dimension preferredLayoutSize(Container parent)
            {
                return place(resolveWidth(false), false);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent)
            {
                return place(MIN_COLUMN_WIDTH, false);
            }

            @Override
            public void layoutContainer(Container parent)
            {
                place(resolveWidth(true), true);
            }

            private Dimension place(int width, boolean apply)
            {
                boolean twoColumns = width >= MIN_COLUMN_WIDTH * 2 + COLUMN_GAP;
                int columnWidth = twoColumns ? (width - COLUMN_GAP) / 2 : width;

                int y = 0;
                int maxWidth = 0;
                // Set once a "field" row claims column 0, waiting for a second one to share the
                // line; a "full" row (or the end of the loop) always closes it out first.
                boolean pairPending = false;
                int pairHeight = 0;

                for (Component c : getComponents())
                {
                    if (!c.isVisible())
                    {
                        continue;
                    }
                    boolean full = fullWidth.contains(c) || !twoColumns;
                    int h = c.getPreferredSize().height;

                    if (full)
                    {
                        if (pairPending)
                        {
                            y += pairHeight + ROW_GAP;
                            pairPending = false;
                        }
                        if (apply)
                        {
                            c.setBounds(0, y, width, h);
                        }
                        y += h + ROW_GAP;
                        maxWidth = Math.max(maxWidth, width);
                    }
                    else if (!pairPending)
                    {
                        if (apply)
                        {
                            c.setBounds(0, y, columnWidth, h);
                        }
                        pairPending = true;
                        pairHeight = h;
                        maxWidth = Math.max(maxWidth, width);
                    }
                    else
                    {
                        if (apply)
                        {
                            c.setBounds(columnWidth + COLUMN_GAP, y, columnWidth, h);
                        }
                        pairHeight = Math.max(pairHeight, h);
                        y += pairHeight + ROW_GAP;
                        pairPending = false;
                    }
                }
                if (pairPending)
                {
                    y += pairHeight + ROW_GAP;
                }
                return new Dimension(maxWidth, Math.max(0, y - ROW_GAP));
            }
        }
    }
}
