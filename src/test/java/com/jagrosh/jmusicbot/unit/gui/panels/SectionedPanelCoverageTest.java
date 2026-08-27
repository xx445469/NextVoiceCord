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
package com.jagrosh.jmusicbot.unit.gui.panels;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.gui.components.Widgets;
import com.jagrosh.jmusicbot.gui.panels.ConfigPanel;
import com.jagrosh.jmusicbot.gui.panels.SectionedPanel;
import com.jagrosh.jmusicbot.gui.panels.SettingsPanel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The sidebar builds a page's category list entirely from {@link SectionedPanel#getSections()} —
 * see {@code MainFrame.categoriesOf} — specifically so that list can never drift out of sync with
 * what the page actually shows. This is what enforces that: it fails the moment a panel grows (or
 * loses) a {@link Widgets.Card} that {@link SectionedPanel#getSections()} does not also report,
 * which is exactly the silent-drift failure mode a hand-maintained list in the sidebar itself
 * would have allowed.
 *
 * <p>Every {@link Widgets.Card} actually present in the built component tree — found by walking
 * it, not by trusting the panel to have registered what it built — is compared against
 * {@link SectionedPanel#getSections()}'s anchors by identity: same cards, same count, nothing on
 * one side that is missing from the other.
 */
@DisplayName("Sidebar-visible sections cover every card")
class SectionedPanelCoverageTest extends BaseConfigTest
{
    @BeforeEach
    @Override
    protected void setUpBase()
    {
        super.setUpBase();
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    @DisplayName("SettingsPanel.getSections() names exactly the cards on the page")
    void settingsPanelSectionsCoverEveryCard()
    {
        SettingsPanel panel = new SettingsPanel(mock(Bot.class));

        assertSectionsCoverEveryCard(panel);
    }

    @Test
    @DisplayName("ConfigPanel.getSections() names exactly the cards on the page")
    void configPanelSectionsCoverEveryCard() throws IOException
    {
        Path configFile = createTempConfigFile("""
                meta {
                  configVersion = 2
                }
                discord.token = test_token
                discord.owner = 123456789
                """);
        setConfigFileProperty(configFile);

        BotConfig config = new BotConfig(mockUserInteraction);
        config.load();

        Bot bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(config);
        // bot.getYouTubeOauth2Handler() is left unstubbed (null): ConfigPanel's constructor
        // treats that as "no live handler to listen on" and continues, same as every other
        // ConfigPanel test in this package that does not exercise YouTube sign-in.

        ConfigPanel panel = new ConfigPanel(bot);

        assertSectionsCoverEveryCard(panel);
    }

    /**
     * Fails with the specific mismatch — a card with no matching section, or a section pointing
     * at a component that is not actually on the page — rather than just a count, so a future
     * failure here says which card went missing instead of just that something did.
     */
    private static <P extends Component & SectionedPanel> void assertSectionsCoverEveryCard(P panel)
    {
        List<SectionedPanel.Section> sections = panel.getSections();
        assertFalse(sections.isEmpty(), "expected at least one card");

        Set<Component> anchors = identitySet();
        for (SectionedPanel.Section section : sections)
        {
            anchors.add(section.anchor());
        }
        Set<Component> cardsOnPage = identitySet();
        cardsOnPage.addAll(findCards(panel));

        for (Component anchor : anchors)
        {
            assertTrue(cardsOnPage.contains(anchor),
                    "getSections() names a card that is not actually on the page: " + describe(anchor));
        }
        for (Component card : cardsOnPage)
        {
            assertTrue(anchors.contains(card),
                    "a card on the page has no matching entry from getSections(): " + describe(card));
        }
        assertEquals(cardsOnPage.size(), anchors.size(),
                "getSections() and the page's actual cards disagree in number");

        // Titles are what a reader sees in the sidebar; a blank or duplicated one would be
        // visible there even though every card is technically accounted for above.
        Set<String> titles = new HashSet<>();
        for (SectionedPanel.Section section : sections)
        {
            assertFalse(section.title() == null || section.title().isBlank(),
                    "a section has a blank title");
            assertTrue(titles.add(section.title()), "duplicate section title: " + section.title());
        }
    }

    private static Set<Component> identitySet()
    {
        return java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    }

    /**
     * Every {@link Widgets.Card} (including a {@link Widgets.CollapsibleCard}) inside the page's
     * scrollable list of settings — a {@link JScrollPane}, built by {@code Widgets.scrollable} —
     * not the whole component tree. {@link ConfigPanel} also builds one {@link Widgets.Card} of
     * its own outside that list, for the persistent "Save changes" bar pinned below it: that one
     * is chrome, not a setting a reader would ask the sidebar to jump to, and correctly has no
     * entry in {@link SectionedPanel#getSections()} — scoping the search to the scrollable area
     * is what keeps this test from flagging it as a false positive.
     */
    private static List<Widgets.Card> findCards(Component root)
    {
        List<Widgets.Card> found = new ArrayList<>();
        for (JScrollPane scroll : findScrollPanes(root))
        {
            Component view = scroll.getViewport().getView();
            if (view != null)
            {
                collectCards(view, found);
            }
        }
        return found;
    }

    private static List<JScrollPane> findScrollPanes(Component c)
    {
        List<JScrollPane> found = new ArrayList<>();
        collectScrollPanes(c, found);
        return found;
    }

    private static void collectScrollPanes(Component c, List<JScrollPane> found)
    {
        if (c instanceof JScrollPane scroll)
        {
            found.add(scroll);
            return;
        }
        if (c instanceof Container container)
        {
            for (Component child : container.getComponents())
            {
                collectScrollPanes(child, found);
            }
        }
    }

    private static void collectCards(Component c, List<Widgets.Card> found)
    {
        if (c instanceof Widgets.Card card)
        {
            found.add(card);
        }
        if (c instanceof Container container)
        {
            for (Component child : container.getComponents())
            {
                collectCards(child, found);
            }
        }
    }

    private static String describe(Component c)
    {
        return c.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(c));
    }
}
