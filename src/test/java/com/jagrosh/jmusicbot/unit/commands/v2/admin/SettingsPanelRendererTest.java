package com.jagrosh.jmusicbot.unit.commands.v2.admin;

import com.jagrosh.jmusicbot.testutil.TestTranslations;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.admin.SettingsPanelRenderer;
import com.jagrosh.jmusicbot.settings.NowPlayingButtonsMode;
import com.jagrosh.jmusicbot.settings.NowPlayingLayoutMode;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.modals.Modal;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsPanelRendererTest
{
    /**
     * A Bot that resolves real English translations.
     *
     * <p>These builders now take one so their text can follow the guild's language.
     * Using the real translations keeps the assertions below checking the words a user
     * actually sees; a stub returning null would turn them into null checks that pass
     * while proving nothing.
     */
    private static final Bot i18nBot = TestTranslations.mockBot();

    @Test
    void buildSettingsComponents_containsExpectedButtons()
    {
        Settings settings = new Settings(null, 0, 0, 0, 100, null, RepeatMode.OFF, null, -1,
                QueueType.FAIR, NowPlayingLayoutMode.INHERIT, NowPlayingButtonsMode.INHERIT);
        List<ActionRow> rows = SettingsPanelRenderer.buildSettingsComponents(i18nBot, null, settings, 123L);
        assertEquals(4, rows.size());

        List<String> labels = rows.stream()
                .flatMap(row -> row.getButtons().stream())
                .map(b -> b.getLabel())
                .collect(Collectors.toList());

        assertTrue(labels.contains("Toggle Queue"));
        assertTrue(labels.contains("Toggle Layout"));
        assertTrue(labels.contains("Toggle Buttons"));
        assertTrue(labels.contains("Set Text Channel"));
        assertTrue(labels.contains("Set Skip %"));
        assertTrue(labels.contains("Close"));
    }

    @Test
    void buildSettingsMessageComponents_containsDisplayContainerAndActionRows()
    {
        Settings settings = new Settings(null, 0, 0, 0, 100, null, RepeatMode.OFF, null, -1,
                QueueType.FAIR, NowPlayingLayoutMode.INHERIT, NowPlayingButtonsMode.INHERIT);
        var guild = Mockito.mock(net.dv8tion.jda.api.entities.Guild.class);
        Mockito.when(guild.getName()).thenReturn("test guild");

        List<MessageTopLevelComponent> components = SettingsPanelRenderer.buildSettingsMessageComponents(i18nBot,
                guild, settings, Mockito.mock(com.jagrosh.jmusicbot.BotConfig.class), 123L, "tester");
        assertEquals(1, components.size());
        assertTrue(components.get(0) instanceof Container);
        Container container = (Container) components.get(0);
        long actionRows = container.getComponents().stream()
                .filter(c -> c.getType() == Component.Type.ACTION_ROW)
                .count();
        assertEquals(5, actionRows);
        long separators = container.getComponents().stream()
                .filter(c -> c.getType() == Component.Type.SEPARATOR)
                .count();
        assertEquals(4, separators);

        List<ActionRow> rows = container.getComponents().stream()
                .filter(c -> c.getType() == Component.Type.ACTION_ROW)
                .map(c -> c.asActionRow())
                .toList();
        List<String> rowButtonLabels = rows.stream()
                .flatMap(r -> r.getButtons().stream())
                .map(Button::getLabel)
                .toList();
        assertTrue(rowButtonLabels.contains("Set Prefix"));
        assertTrue(rowButtonLabels.contains("Set Skip %"));
        assertTrue(rowButtonLabels.contains("Close"));

        List<String> textDisplays = container.getComponents().stream()
                .filter(c -> c.getType() == Component.Type.TEXT_DISPLAY)
                .map(c -> c.asTextDisplay().getContent())
                .toList();
        // The title now comes from the translation file rather than a literal, so it
        // follows the rebrand — and the guild's language — instead of being frozen here.
        assertTrue(textDisplays.contains("## NextVoiceCord Settings"));
        assertTrue(textDisplays.stream().anyMatch(t -> t.contains("test guild")
                && t.contains("tester")),
                "the panel should name the guild and the invoker");

        List<String> sectionAccessoryButtonIds = container.getComponents().stream()
                .filter(c -> c.getType() == Component.Type.SECTION)
                .map(c -> c.asSection())
                .map(Section::getAccessory)
                .filter(a -> a != null && a.getType() == Component.Type.BUTTON)
                .map(a -> a.asButton().getCustomId())
                .toList();
        assertTrue(sectionAccessoryButtonIds.contains("settings_clear_settc_123"));
        assertTrue(sectionAccessoryButtonIds.contains("settings_clear_setvc_123"));
        assertTrue(sectionAccessoryButtonIds.contains("settings_clear_setdj_123"));
        assertTrue(sectionAccessoryButtonIds.contains("settings_toggle_queue_123"));
        assertTrue(sectionAccessoryButtonIds.contains("settings_toggle_layout_123"));
        assertTrue(sectionAccessoryButtonIds.contains("settings_toggle_npbuttons_123"));
        assertTrue(!sectionAccessoryButtonIds.contains("settings_info_behavior"));
    }

    @Test
    void buildModal_knownKey_returnsModalWithExpectedId()
    {
        Modal modal = SettingsPanelRenderer.buildModal(i18nBot, null, "prefix", 456L);
        assertNotNull(modal);
        assertEquals("settings_modal_prefix_456", modal.getId());
    }

    @Test
    void buildModal_entitySelectKeys_returnNull()
    {
        assertNull(SettingsPanelRenderer.buildModal(i18nBot, null, "settc", 456L));
        assertNull(SettingsPanelRenderer.buildModal(i18nBot, null, "setvc", 456L));
        assertNull(SettingsPanelRenderer.buildModal(i18nBot, null, "setdj", 456L));
    }

    @Test
    void buildEntitySelectMenu_setDj_hasExpectedId()
    {
        EntitySelectMenu menu = SettingsPanelRenderer.buildEntitySelectMenu(i18nBot, null, "setdj", 456L);
        assertNotNull(menu);
        assertEquals("settings_entity_setdj_456", menu.getCustomId());
    }

    @Test
    void buildEntitySelectMenu_withPanelMessage_hasExpectedId()
    {
        EntitySelectMenu menu = SettingsPanelRenderer.buildEntitySelectMenu(i18nBot, null, "setvc", 456L, 999L);
        assertNotNull(menu);
        assertEquals("settings_entity_setvc_999_456", menu.getCustomId());
    }

    @Test
    void buildEntityClearButton_setDj_hasExpectedIdAndLabel()
    {
        Button button = SettingsPanelRenderer.buildEntityClearButton(i18nBot, null, "setdj", 456L);
        assertNotNull(button);
        assertEquals("settings_clear_setdj_456", button.getCustomId());
        assertEquals("None", button.getLabel());
    }

    @Test
    void buildEntityClearButton_withPanelMessage_hasExpectedIdAndLabel()
    {
        Button button = SettingsPanelRenderer.buildEntityClearButton(i18nBot, null, "settc", 456L, 999L);
        assertNotNull(button);
        assertEquals("settings_clear_settc_999_456", button.getCustomId());
        assertEquals("Any", button.getLabel());
    }
}
