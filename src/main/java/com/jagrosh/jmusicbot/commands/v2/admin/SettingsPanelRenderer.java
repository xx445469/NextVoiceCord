package com.jagrosh.jmusicbot.commands.v2.admin;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.modals.Modal;

import java.util.List;
import java.util.ArrayList;

public final class SettingsPanelRenderer
{
    public static final String ACTION_ENUM = "enum";
    public static final String ACTION_TOGGLE = "toggle";
    public static final String ACTION_OPEN = "open";
    public static final String ACTION_CLEAR = "clear";
    public static final String ACTION_REFRESH = "refresh";
    public static final String ACTION_CLOSE = "close";

    private SettingsPanelRenderer()
    {
    }

    public static String buttonId(String action, String key, String value, long userId)
    {
        if (value == null || value.isBlank())
            return "settings_" + action + "_" + key + "_" + userId;
        return "settings_" + action + "_" + key + "_" + value + "_" + userId;
    }

    public static String modalId(String key, long userId)
    {
        return "settings_modal_" + key + "_" + userId;
    }

    public static String entitySelectId(String key, long userId)
    {
        return "settings_entity_" + key + "_" + userId;
    }

    public static String entitySelectId(String key, long originalPanelMessageId, long userId)
    {
        return "settings_entity_" + key + "_" + originalPanelMessageId + "_" + userId;
    }

    public static net.dv8tion.jda.api.entities.MessageEmbed buildSettingsEmbed(
            Guild guild, Settings settings, BotConfig config, java.awt.Color color)
    {
        return buildSettingsEmbed(guild, settings, config, color, null);
    }

    public static net.dv8tion.jda.api.entities.MessageEmbed buildSettingsEmbed(
            Guild guild, Settings settings, BotConfig config, java.awt.Color color, String invokerName)
    {
        TextChannel textChannel = settings.getTextChannel(guild);
        VoiceChannel voiceChannel = settings.getVoiceChannel(guild);
        Role djRole = settings.getRole(guild);

        String prefix = settings.getPrefix() == null ? "None" : "`" + settings.getPrefix() + "`";
        int skipRatio = (int) Math.round(settings.getSkipRatio() * 100);
        String skipRatioDisplay = settings.getSkipRatio() < 0
                ? "Default (`55%`)"
                : "`" + skipRatio + "%`";
        String textChannelDisplay = textChannel == null ? "Any" : textChannel.getAsMention();
        String voiceChannelDisplay = voiceChannel == null ? "Any" : voiceChannel.getAsMention();
        String djRoleDisplay = djRole == null ? "None" : "**" + djRole.getName() + "**";

        boolean effectiveMinimal = settings.useMinimalNowPlayingMessage(config);
        boolean effectiveButtons = settings.showNowPlayingButtons(config);
        String nowPlayingLayout = "`" + settings.getNowPlayingLayoutMode().getUserInputValue()
                + "` (effective: `" + (effectiveMinimal ? "minimal" : "full") + "`)";
        String nowPlayingButtons = "`" + settings.getNowPlayingButtonsMode().getUserInputValue()
                + "` (effective: `" + (effectiveButtons ? "on" : "off") + "`)";
        String nowPlayingValue = "Layout: " + nowPlayingLayout + "\nButtons: " + nowPlayingButtons;

        String footer = invokerName != null && !invokerName.isBlank()
                ? "Only " + invokerName + " can use these controls."
                : "Only the command invoker can use these controls.";

        return new EmbedBuilder()
                .setTitle("MusicBot Settings")
                .setColor(color)
                .setDescription("Settings panel for **" + guild.getName() + "**.\n"
                        + "Use the buttons below to update values.")
                .addField("Text Channel", textChannelDisplay, true)
                .addField("Voice Channel", voiceChannelDisplay, true)
                .addField("DJ Role", djRoleDisplay, true)
                .addField("Prefix", prefix, true)
                .addField("Skip Ratio", skipRatioDisplay, true)
                .addField("Queue Type", "`" + settings.getQueueType().getUserFriendlyName() + "`", true)
                .addField("Now-Playing", nowPlayingValue, false)
                .setFooter(footer)
                .build();
    }

    public static List<ActionRow> buildSettingsComponents(Settings settings, long userId)
    {
        Button queueToggle = toggleButton("queue", "Toggle Queue", userId);
        Button layoutToggle = toggleButton("layout", "Toggle Layout", userId);
        Button npButtonsToggle = toggleButton("npbuttons", "Toggle Buttons", userId);

        Button setTextChannel = Button.secondary(buttonId(ACTION_OPEN, "settc", null, userId), "Set Text Channel");
        Button setVoiceChannel = Button.secondary(buttonId(ACTION_OPEN, "setvc", null, userId), "Set Voice Channel");
        Button setDjRole = Button.secondary(buttonId(ACTION_OPEN, "setdj", null, userId), "Set DJ Role");
        Button setPrefix = Button.secondary(buttonId(ACTION_OPEN, "prefix", null, userId), "Set Prefix");
        Button setSkip = Button.secondary(buttonId(ACTION_OPEN, "setskip", null, userId), "Set Skip %");

        Button close = Button.danger(buttonId(ACTION_CLOSE, "main", null, userId), "Close");

        return List.of(
                ActionRow.of(queueToggle),
                ActionRow.of(layoutToggle, npButtonsToggle),
                ActionRow.of(setTextChannel, setVoiceChannel, setDjRole, setPrefix, setSkip),
                ActionRow.of(close)
        );
    }

    public static List<MessageTopLevelComponent> buildSettingsMessageComponents(
            Guild guild, Settings settings, BotConfig config, long userId, String invokerName)
    {
        List<ContainerChildComponent> children = new ArrayList<>(buildSettingsDisplayChildren(guild, settings, config, invokerName, userId));
        return List.of(Container.of(children));
    }

    public static Modal buildModal(String key, long userId)
    {
        return switch (key)
        {
            case "prefix" -> buildSingleInputModal(
                    modalId("prefix", userId),
                    "Set prefix",
                    "prefix_value",
                    "Custom prefix (empty clears)"
            );
            case "setskip" -> buildSingleInputModal(
                    modalId("setskip", userId),
                    "Set skip percentage",
                    "setskip_value",
                    "Integer from 0 to 100"
            );
            default -> null;
        };
    }

    public static EntitySelectMenu buildEntitySelectMenu(String key, long userId)
    {
        return buildEntitySelectMenu(key, userId, -1L);
    }

    public static EntitySelectMenu buildEntitySelectMenu(String key, long userId, long originalPanelMessageId)
    {
        String id = originalPanelMessageId > 0
                ? entitySelectId(key, originalPanelMessageId, userId)
                : entitySelectId(key, userId);
        return switch (key)
        {
            case "settc" -> EntitySelectMenu.create(id, SelectTarget.CHANNEL)
                    .setChannelTypes(ChannelType.TEXT)
                    .setPlaceholder("Choose a text channel")
                    .setRequiredRange(1, 1)
                    .build();
            case "setvc" -> EntitySelectMenu.create(id, SelectTarget.CHANNEL)
                    .setChannelTypes(ChannelType.VOICE)
                    .setPlaceholder("Choose a voice channel")
                    .setRequiredRange(1, 1)
                    .build();
            case "setdj" -> EntitySelectMenu.create(id, SelectTarget.ROLE)
                    .setPlaceholder("Choose the DJ role")
                    .setRequiredRange(1, 1)
                    .build();
            default -> null;
        };
    }

    public static Button buildEntityClearButton(String key, long userId)
    {
        return buildEntityClearButton(key, userId, -1L);
    }

    public static Button buildEntityClearButton(String key, long userId, long originalPanelMessageId)
    {
        String value = originalPanelMessageId > 0 ? String.valueOf(originalPanelMessageId) : null;
        return switch (key)
        {
            case "settc", "setvc" -> Button.secondary(buttonId(ACTION_CLEAR, key, value, userId), "Any");
            case "setdj" -> Button.secondary(buttonId(ACTION_CLEAR, key, value, userId), "None");
            default -> null;
        };
    }

    private static Modal buildSingleInputModal(String modalId, String title, String inputId, String placeholder)
    {
        TextInput input = TextInput.create(inputId, TextInputStyle.SHORT)
                .setRequired(false)
                .setPlaceholder(placeholder)
                .setMaxLength(100)
                .build();
        return Modal.create(modalId, title)
                .addComponents(Label.of("Value", input))
                .build();
    }

    private static Button toggleButton(String key, String label, long userId)
    {
        String id = buttonId(ACTION_TOGGLE, key, null, userId);
        return Button.secondary(id, label);
    }

    public static String modalValue(net.dv8tion.jda.api.events.interaction.ModalInteractionEvent event, String inputId)
    {
        return event.getValues().stream()
                .filter(m -> inputId.equals(m.getCustomId()))
                .findFirst()
                .map(net.dv8tion.jda.api.interactions.modals.ModalMapping::getAsString)
                .orElse("")
                .trim();
    }

    public static MessageChannel asMessageChannel(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event)
    {
        return event.getChannel();
    }

    private static List<ContainerChildComponent> buildSettingsDisplayChildren(
            Guild guild, Settings settings, BotConfig config, String invokerName, long userId)
    {
        TextChannel textChannel = settings.getTextChannel(guild);
        VoiceChannel voiceChannel = settings.getVoiceChannel(guild);
        Role djRole = settings.getRole(guild);

        String prefix = settings.getPrefix() == null ? "`None`" : "`" + settings.getPrefix() + "`";
        int skipRatio = (int) Math.round(settings.getSkipRatio() * 100);
        String skipRatioDisplay = settings.getSkipRatio() < 0 ? "Default (`55%`)" : "`" + skipRatio + "%`";
        String textChannelDisplay = textChannel == null ? "Any" : textChannel.getAsMention();
        String voiceChannelDisplay = voiceChannel == null ? "Any" : voiceChannel.getAsMention();
        String djRoleDisplay = djRole == null ? "None" : "**" + FormatUtil.filter(djRole.getName()) + "**";

        boolean effectiveMinimal = settings.useMinimalNowPlayingMessage(config);
        boolean effectiveButtons = settings.showNowPlayingButtons(config);
        String nowPlayingLayout = "`" + settings.getNowPlayingLayoutMode().getUserInputValue()
                + "` (effective: `" + (effectiveMinimal ? "minimal" : "full") + "`)";
        String nowPlayingButtons = "`" + settings.getNowPlayingButtonsMode().getUserInputValue()
                + "` (effective: `" + (effectiveButtons ? "on" : "off") + "`)";
        String queueType = "`" + settings.getQueueType().getUserFriendlyName() + "`";

        String footer = invokerName != null && !invokerName.isBlank()
                ? "Only **" + FormatUtil.filter(invokerName) + "** can use these controls."
                : "Only the command invoker can use these controls.";

        Button queueToggle = toggleButton("queue", "Toggle Queue", userId);
        Button layoutToggle = toggleButton("layout", "Toggle Layout", userId);
        Button npButtonsToggle = toggleButton("npbuttons", "Toggle Buttons", userId);
        Button setPrefix = Button.secondary(buttonId(ACTION_OPEN, "prefix", null, userId), "Set Prefix");
        Button setSkip = Button.secondary(buttonId(ACTION_OPEN, "setskip", null, userId), "Set Skip %");
        Button close = Button.danger(buttonId(ACTION_CLOSE, "main", null, userId), "Close");
        Button clearText = Button.secondary(buttonId(ACTION_CLEAR, "settc", null, userId), "Clear Text");
        Button clearVoice = Button.secondary(buttonId(ACTION_CLEAR, "setvc", null, userId), "Clear Voice");
        Button clearDj = Button.secondary(buttonId(ACTION_CLEAR, "setdj", null, userId), "Clear DJ");

        EntitySelectMenu textChannelSelect = buildEntitySelectMenu("settc", userId);
        EntitySelectMenu voiceChannelSelect = buildEntitySelectMenu("setvc", userId);
        EntitySelectMenu djRoleSelect = buildEntitySelectMenu("setdj", userId);

        return List.of(
                TextDisplay.of("## MusicBot Settings"),
                TextDisplay.of("Interactive settings panel for **" + FormatUtil.filter(guild.getName()) + "**.\n"
                        + footer),
                Separator.createDivider(Separator.Spacing.SMALL),
                Section.of(
                        clearText,
                        TextDisplay.of("Text Channel: " + textChannelDisplay)
                ),
                ActionRow.of(textChannelSelect),
                Section.of(
                        clearVoice,
                        TextDisplay.of("Voice Channel: " + voiceChannelDisplay)
                ),
                ActionRow.of(voiceChannelSelect),
                Section.of(
                        clearDj,
                        TextDisplay.of("DJ Role: " + djRoleDisplay)
                ),
                ActionRow.of(djRoleSelect),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("Prefix: " + prefix
                        + "\nSkip Ratio: " + skipRatioDisplay),
                ActionRow.of(setPrefix, setSkip),
                Separator.createDivider(Separator.Spacing.SMALL),
                Section.of(
                        queueToggle,
                        TextDisplay.of("Queue Type: " + queueType)
                ),
                Section.of(
                        layoutToggle,
                        TextDisplay.of("Layout: " + nowPlayingLayout
                        )
                ),
                Section.of(
                        npButtonsToggle,
                        TextDisplay.of("Buttons: " + nowPlayingButtons)
                ),
                Separator.createDivider(Separator.Spacing.SMALL),
                ActionRow.of(close)
        );
    }
}
