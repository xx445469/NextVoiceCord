package com.jagrosh.jmusicbot.unit.commands.v2.admin;

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
    @Test
    void buildSettingsComponents_containsExpectedButtons()
    {
        Settings settings = new Settings(null, 0, 0, 0, 100, null, RepeatMode.OFF, null, -1,
                QueueType.FAIR, NowPlayingLayoutMode.INHERIT, NowPlayingButtonsMode.INHERIT);
        List<ActionRow> rows = SettingsPanelRenderer.buildSettingsComponents(settings, 123L);
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

        List<MessageTopLevelComponent> components = SettingsPanelRenderer.buildSettingsMessageComponents(
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
        assertTrue(textDisplays.contains("## MusicBot Settings"));
        assertTrue(textDisplays.stream().anyMatch(t -> t.contains("Interactive settings panel for **test guild**.")
                && t.contains("Only **tester** can use these controls.")));

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
        Modal modal = SettingsPanelRenderer.buildModal("prefix", 456L);
        assertNotNull(modal);
        assertEquals("settings_modal_prefix_456", modal.getId());
    }

    @Test
    void buildModal_entitySelectKeys_returnNull()
    {
        assertNull(SettingsPanelRenderer.buildModal("settc", 456L));
        assertNull(SettingsPanelRenderer.buildModal("setvc", 456L));
        assertNull(SettingsPanelRenderer.buildModal("setdj", 456L));
    }

    @Test
    void buildEntitySelectMenu_setDj_hasExpectedId()
    {
        EntitySelectMenu menu = SettingsPanelRenderer.buildEntitySelectMenu("setdj", 456L);
        assertNotNull(menu);
        assertEquals("settings_entity_setdj_456", menu.getCustomId());
    }

    @Test
    void buildEntitySelectMenu_withPanelMessage_hasExpectedId()
    {
        EntitySelectMenu menu = SettingsPanelRenderer.buildEntitySelectMenu("setvc", 456L, 999L);
        assertNotNull(menu);
        assertEquals("settings_entity_setvc_999_456", menu.getCustomId());
    }

    @Test
    void buildEntityClearButton_setDj_hasExpectedIdAndLabel()
    {
        Button button = SettingsPanelRenderer.buildEntityClearButton("setdj", 456L);
        assertNotNull(button);
        assertEquals("settings_clear_setdj_456", button.getCustomId());
        assertEquals("None", button.getLabel());
    }

    @Test
    void buildEntityClearButton_withPanelMessage_hasExpectedIdAndLabel()
    {
        Button button = SettingsPanelRenderer.buildEntityClearButton("settc", 456L, 999L);
        assertNotNull(button);
        assertEquals("settings_clear_settc_999_456", button.getCustomId());
        assertEquals("Any", button.getLabel());
    }
}
