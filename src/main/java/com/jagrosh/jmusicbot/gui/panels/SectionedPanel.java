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
package com.jagrosh.jmusicbot.gui.panels;

import java.util.List;
import javax.swing.JComponent;

/**
 * A panel built out of titled cards that wants those cards listed somewhere else — the sidebar
 * — so a reader can see what the page holds, and jump straight to one of its cards, without
 * opening the panel first and scrolling to find out.
 *
 * <p>The list is the panel's own to give out, built from the exact title strings it already
 * passes to {@link com.jagrosh.jmusicbot.gui.components.Widgets#titledCard} (or
 * {@link com.jagrosh.jmusicbot.gui.components.Widgets.CollapsibleCard}). Nothing outside the
 * panel should keep a second, separately maintained list of its card names: the moment a card is
 * added, renamed or removed here, a list kept anywhere else goes stale silently. Read it from
 * here instead.
 *
 * @author adan (xx445469)
 */
public interface SectionedPanel {

    /** This panel's cards, in the order they appear on the page. */
    List<Section> getSections();

    /**
     * One card: the heading it is shown under, the component to bring into view, and — for a
     * card that can start collapsed — what to do first so it is actually visible once scrolled
     * to. {@code reveal()} is a no-op for an ordinary card.
     */
    final class Section {
        private final String title;
        private final JComponent anchor;
        private final Runnable reveal;

        public Section(String title, JComponent anchor) {
            this(title, anchor, null);
        }

        public Section(String title, JComponent anchor, Runnable reveal) {
            this.title = title;
            this.anchor = anchor;
            this.reveal = reveal;
        }

        /** The card's own heading — the same string passed to {@code Widgets.titledCard}. */
        public String title() {
            return title;
        }

        /** The component to scroll into view. */
        public JComponent anchor() {
            return anchor;
        }

        /** Opens the card first if it starts collapsed, so scrolling to it does not reveal an empty shell. */
        public void reveal() {
            if (reveal != null) {
                reveal.run();
            }
        }
    }
}
