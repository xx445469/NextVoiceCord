package com.jagrosh.jmusicbot.commands.v2.admin;

import com.jagrosh.jmusicbot.Bot;
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

/**
 * Static rendering helpers for the /settings panel.
 *
 * <p>This is a static utility class with no {@link Bot} field of its own, so every method
 * that produces user-visible text takes {@code bot} (and {@code guild}) as a parameter and
 * resolves strings through {@link Bot#msg}. Callers live in {@code listener/} and
 * {@code commands/v1|v2/}.
 */
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
            Bot bot, Guild guild, Settings settings, BotConfig config, java.awt.Color color)
    {
        return buildSettingsEmbed(bot, guild, settings, config, color, null);
    }

    public static net.dv8tion.jda.api.entities.MessageEmbed buildSettingsEmbed(
            Bot bot, Guild guild, Settings settings, BotConfig config, java.awt.Color color, String invokerName)
    {
        TextChannel textChannel = settings.getTextChannel(guild);
        VoiceChannel voiceChannel = settings.getVoiceChannel(guild);
        Role djRole = settings.getRole(guild);

        String valueNone = bot.msg(guild, "settings.panel.valueNone");
        String valueAny = bot.msg(guild, "settings.panel.valueAny");

        String prefix = settings.getPrefix() == null ? valueNone : "`" + settings.getPrefix() + "`";
        int skipRatio = (int) Math.round(settings.getSkipRatio() * 100);
        String skipRatioDisplay = settings.getSkipRatio() < 0
                ? bot.msg(guild, "settings.panel.skipDefault")
                : "`" + skipRatio + "%`";
        String textChannelDisplay = textChannel == null ? valueAny : textChannel.getAsMention();
        String voiceChannelDisplay = voiceChannel == null ? valueAny : voiceChannel.getAsMention();
        String djRoleDisplay = djRole == null ? valueNone : "**" + djRole.getName() + "**";

        boolean effectiveMinimal = settings.useMinimalNowPlayingMessage(config);
        boolean effectiveButtons = settings.showNowPlayingButtons(config);
        String nowPlayingLayout = "`" + settings.getNowPlayingLayoutMode().getUserInputValue()
                + "` (effective: `" + (effectiveMinimal ? "minimal" : "full") + "`)";
        String nowPlayingButtons = "`" + settings.getNowPlayingButtonsMode().getUserInputValue()
                + "` (effective: `" + (effectiveButtons ? "on" : "off") + "`)";
        String nowPlayingValue = bot.msg(guild, "settings.panel.field.layout") + ": " + nowPlayingLayout
                + "\n" + bot.msg(guild, "settings.panel.field.buttons") + ": " + nowPlayingButtons;

        String footer = invokerName != null && !invokerName.isBlank()
                ? bot.msg(guild, "settings.panel.footerNamed", invokerName)
                : bot.msg(guild, "settings.panel.footerGeneric");

        return new EmbedBuilder()
                .setTitle(bot.msg(guild, "settings.panel.title"))
                .setColor(color)
                .setDescription(bot.msg(guild, "settings.panel.description", guild.getName()))
                .addField(bot.msg(guild, "settings.panel.field.textChannel"), textChannelDisplay, true)
                .addField(bot.msg(guild, "settings.panel.field.voiceChannel"), voiceChannelDisplay, true)
                .addField(bot.msg(guild, "settings.panel.field.djRole"), djRoleDisplay, true)
                .addField(bot.msg(guild, "settings.panel.field.prefix"), prefix, true)
                .addField(bot.msg(guild, "settings.panel.field.skipRatio"), skipRatioDisplay, true)
                .addField(bot.msg(guild, "settings.panel.field.queueType"), "`" + settings.getQueueType().getUserFriendlyName() + "`", true)
                .addField(bot.msg(guild, "settings.panel.field.nowPlaying"), nowPlayingValue, false)
                .setFooter(footer)
                .build();
    }

    public static List<ActionRow> buildSettingsComponents(Bot bot, Guild guild, Settings settings, long userId)
    {
        Button queueToggle = toggleButton("queue", bot.msg(guild, "settings.panel.button.toggleQueue"), userId);
        Button layoutToggle = toggleButton("layout", bot.msg(guild, "settings.panel.button.toggleLayout"), userId);
        Button npButtonsToggle = toggleButton("npbuttons", bot.msg(guild, "settings.panel.button.toggleButtons"), userId);

        Button setTextChannel = Button.secondary(buttonId(ACTION_OPEN, "settc", null, userId), bot.msg(guild, "settings.panel.button.setTextChannel"));
        Button setVoiceChannel = Button.secondary(buttonId(ACTION_OPEN, "setvc", null, userId), bot.msg(guild, "settings.panel.button.setVoiceChannel"));
        Button setDjRole = Button.secondary(buttonId(ACTION_OPEN, "setdj", null, userId), bot.msg(guild, "settings.panel.button.setDjRole"));
        Button setPrefix = Button.secondary(buttonId(ACTION_OPEN, "prefix", null, userId), bot.msg(guild, "settings.panel.button.setPrefix"));
        Button setSkip = Button.secondary(buttonId(ACTION_OPEN, "setskip", null, userId), bot.msg(guild, "settings.panel.button.setSkip"));

        Button close = Button.danger(buttonId(ACTION_CLOSE, "main", null, userId), bot.msg(guild, "settings.panel.button.close"));

        return List.of(
                ActionRow.of(queueToggle),
                ActionRow.of(layoutToggle, npButtonsToggle),
                ActionRow.of(setTextChannel, setVoiceChannel, setDjRole, setPrefix, setSkip),
                ActionRow.of(close)
        );
    }

    public static List<MessageTopLevelComponent> buildSettingsMessageComponents(
            Bot bot, Guild guild, Settings settings, BotConfig config, long userId, String invokerName)
    {
        return buildSettingsMessageComponents(bot, guild, settings, config, userId, invokerName, null);
    }

    /**
     * @param switcherFor the person to attach the menu crossing to their own settings for, or
     *                    {@code null} for no menu. Only passed for someone who can manage the
     *                    server, since both of its entries have to lead somewhere they are
     *                    allowed to go.
     */
    public static List<MessageTopLevelComponent> buildSettingsMessageComponents(
            Bot bot, Guild guild, Settings settings, BotConfig config, long userId, String invokerName,
            net.dv8tion.jda.api.entities.User switcherFor)
    {
        List<ContainerChildComponent> children = new ArrayList<>(buildSettingsDisplayChildren(bot, guild, settings, config, invokerName, userId));

        if (switcherFor != null)
        {
            // Inserted before the Close row, so the way out of the panel stays last.
            children.add(children.size() - 1, Separator.createDivider(Separator.Spacing.SMALL));
            children.add(children.size() - 1, ActionRow.of(
                    UserSettingsPanelRenderer.scopeSelect(bot, guild, switcherFor, UserSettingsPanelRenderer.SCOPE_SERVER)));
        }

        return List.of(Container.of(children));
    }

    public static Modal buildModal(Bot bot, Guild guild, String key, long userId)
    {
        return switch (key)
        {
            case "prefix" -> buildSingleInputModal(
                    modalId("prefix", userId),
                    bot.msg(guild, "settings.modal.setPrefixTitle"),
                    "prefix_value",
                    bot.msg(guild, "settings.modal.setPrefixPlaceholder"),
                    bot.msg(guild, "settings.modal.labelValue")
            );
            case "setskip" -> buildSingleInputModal(
                    modalId("setskip", userId),
                    bot.msg(guild, "settings.modal.setSkipTitle"),
                    "setskip_value",
                    bot.msg(guild, "settings.modal.setSkipPlaceholder"),
                    bot.msg(guild, "settings.modal.labelValue")
            );
            default -> null;
        };
    }

    public static EntitySelectMenu buildEntitySelectMenu(Bot bot, Guild guild, String key, long userId)
    {
        return buildEntitySelectMenu(bot, guild, key, userId, -1L);
    }

    public static EntitySelectMenu buildEntitySelectMenu(Bot bot, Guild guild, String key, long userId, long originalPanelMessageId)
    {
        String id = originalPanelMessageId > 0
                ? entitySelectId(key, originalPanelMessageId, userId)
                : entitySelectId(key, userId);
        return switch (key)
        {
            case "settc" -> EntitySelectMenu.create(id, SelectTarget.CHANNEL)
                    .setChannelTypes(ChannelType.TEXT)
                    .setPlaceholder(bot.msg(guild, "settings.panel.select.textChannelPlaceholder"))
                    .setRequiredRange(1, 1)
                    .build();
            case "setvc" -> EntitySelectMenu.create(id, SelectTarget.CHANNEL)
                    .setChannelTypes(ChannelType.VOICE)
                    .setPlaceholder(bot.msg(guild, "settings.panel.select.voiceChannelPlaceholder"))
                    .setRequiredRange(1, 1)
                    .build();
            case "setdj" -> EntitySelectMenu.create(id, SelectTarget.ROLE)
                    .setPlaceholder(bot.msg(guild, "settings.panel.select.djRolePlaceholder"))
                    .setRequiredRange(1, 1)
                    .build();
            default -> null;
        };
    }

    public static Button buildEntityClearButton(Bot bot, Guild guild, String key, long userId)
    {
        return buildEntityClearButton(bot, guild, key, userId, -1L);
    }

    public static Button buildEntityClearButton(Bot bot, Guild guild, String key, long userId, long originalPanelMessageId)
    {
        String value = originalPanelMessageId > 0 ? String.valueOf(originalPanelMessageId) : null;
        return switch (key)
        {
            case "settc", "setvc" -> Button.secondary(buttonId(ACTION_CLEAR, key, value, userId), bot.msg(guild, "settings.panel.valueAny"));
            case "setdj" -> Button.secondary(buttonId(ACTION_CLEAR, key, value, userId), bot.msg(guild, "settings.panel.valueNone"));
            default -> null;
        };
    }

    private static Modal buildSingleInputModal(String modalId, String title, String inputId, String placeholder, String label)
    {
        TextInput input = TextInput.create(inputId, TextInputStyle.SHORT)
                .setRequired(false)
                .setPlaceholder(placeholder)
                .setMaxLength(100)
                .build();
        return Modal.create(modalId, title)
                .addComponents(Label.of(label, input))
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
            Bot bot, Guild guild, Settings settings, BotConfig config, String invokerName, long userId)
    {
        TextChannel textChannel = settings.getTextChannel(guild);
        VoiceChannel voiceChannel = settings.getVoiceChannel(guild);
        Role djRole = settings.getRole(guild);

        String valueNone = bot.msg(guild, "settings.panel.valueNone");
        String valueAny = bot.msg(guild, "settings.panel.valueAny");

        String prefix = settings.getPrefix() == null ? "`" + valueNone + "`" : "`" + settings.getPrefix() + "`";
        int skipRatio = (int) Math.round(settings.getSkipRatio() * 100);
        String skipRatioDisplay = settings.getSkipRatio() < 0 ? bot.msg(guild, "settings.panel.skipDefault") : "`" + skipRatio + "%`";
        String textChannelDisplay = textChannel == null ? valueAny : textChannel.getAsMention();
        String voiceChannelDisplay = voiceChannel == null ? valueAny : voiceChannel.getAsMention();
        String djRoleDisplay = djRole == null ? valueNone : "**" + FormatUtil.filter(djRole.getName()) + "**";

        boolean effectiveMinimal = settings.useMinimalNowPlayingMessage(config);
        boolean effectiveButtons = settings.showNowPlayingButtons(config);
        String nowPlayingLayout = "`" + settings.getNowPlayingLayoutMode().getUserInputValue()
                + "` (effective: `" + (effectiveMinimal ? "minimal" : "full") + "`)";
        String nowPlayingButtons = "`" + settings.getNowPlayingButtonsMode().getUserInputValue()
                + "` (effective: `" + (effectiveButtons ? "on" : "off") + "`)";
        String queueType = "`" + settings.getQueueType().getUserFriendlyName() + "`";

        String footer = invokerName != null && !invokerName.isBlank()
                ? bot.msg(guild, "settings.panel.footerNamed", "**" + FormatUtil.filter(invokerName) + "**")
                : bot.msg(guild, "settings.panel.footerGeneric");

        Button queueToggle = toggleButton("queue", bot.msg(guild, "settings.panel.button.toggleQueue"), userId);
        Button layoutToggle = toggleButton("layout", bot.msg(guild, "settings.panel.button.toggleLayout"), userId);
        Button npButtonsToggle = toggleButton("npbuttons", bot.msg(guild, "settings.panel.button.toggleButtons"), userId);
        Button setPrefix = Button.secondary(buttonId(ACTION_OPEN, "prefix", null, userId), bot.msg(guild, "settings.panel.button.setPrefix"));
        Button setSkip = Button.secondary(buttonId(ACTION_OPEN, "setskip", null, userId), bot.msg(guild, "settings.panel.button.setSkip"));
        Button close = Button.danger(buttonId(ACTION_CLOSE, "main", null, userId), bot.msg(guild, "settings.panel.button.close"));
        Button clearText = Button.secondary(buttonId(ACTION_CLEAR, "settc", null, userId), bot.msg(guild, "settings.panel.button.clearText"));
        Button clearVoice = Button.secondary(buttonId(ACTION_CLEAR, "setvc", null, userId), bot.msg(guild, "settings.panel.button.clearVoice"));
        Button clearDj = Button.secondary(buttonId(ACTION_CLEAR, "setdj", null, userId), bot.msg(guild, "settings.panel.button.clearDj"));

        EntitySelectMenu textChannelSelect = buildEntitySelectMenu(bot, guild, "settc", userId);
        EntitySelectMenu voiceChannelSelect = buildEntitySelectMenu(bot, guild, "setvc", userId);
        EntitySelectMenu djRoleSelect = buildEntitySelectMenu(bot, guild, "setdj", userId);

        return List.of(
                TextDisplay.of("## " + bot.msg(guild, "settings.panel.title")),
                TextDisplay.of(bot.msg(guild, "settings.panel.description", FormatUtil.filter(guild.getName()))
                        + "\n" + footer),
                Separator.createDivider(Separator.Spacing.SMALL),
                Section.of(
                        clearText,
                        TextDisplay.of(bot.msg(guild, "settings.panel.field.textChannel") + ": " + textChannelDisplay)
                ),
                ActionRow.of(textChannelSelect),
                Section.of(
                        clearVoice,
                        TextDisplay.of(bot.msg(guild, "settings.panel.field.voiceChannel") + ": " + voiceChannelDisplay)
                ),
                ActionRow.of(voiceChannelSelect),
                Section.of(
                        clearDj,
                        TextDisplay.of(bot.msg(guild, "settings.panel.field.djRole") + ": " + djRoleDisplay)
                ),
                ActionRow.of(djRoleSelect),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(bot.msg(guild, "settings.panel.field.prefix") + ": " + prefix
                        + "\n" + bot.msg(guild, "settings.panel.field.skipRatio") + ": " + skipRatioDisplay),
                ActionRow.of(setPrefix, setSkip),
                Separator.createDivider(Separator.Spacing.SMALL),
                Section.of(
                        queueToggle,
                        TextDisplay.of(bot.msg(guild, "settings.panel.field.queueType") + ": " + queueType)
                ),
                Section.of(
                        layoutToggle,
                        TextDisplay.of(bot.msg(guild, "settings.panel.field.layout") + ": " + nowPlayingLayout
                        )
                ),
                Section.of(
                        npButtonsToggle,
                        TextDisplay.of(bot.msg(guild, "settings.panel.field.buttons") + ": " + nowPlayingButtons)
                ),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(bot.msg(guild, "settings.panel.field.language") + ": **"
                        + effectiveLanguage(settings, config).getNativeName() + "**\n"
                        + bot.msg(guild, "settings.panel.languageHint")),
                ActionRow.of(serverLanguageSelect(bot, guild, settings, config, userId)),
                Separator.createDivider(Separator.Spacing.SMALL),
                ActionRow.of(close)
        );
    }

    /**
     * The language everyone on this server sees unless they have chosen their own.
     *
     * <p>Lives here rather than in a command of its own. It used to be {@code /language
     * scope:server}; folding that command into this panel is what removed the only way to set
     * it, until this went in.
     *
     * <p>Each option is labelled in its own language — 日本語 rather than Japanese — because
     * someone changing away from a language they cannot read has to recognise their own to
     * find it.
     */
    private static net.dv8tion.jda.api.components.selections.StringSelectMenu serverLanguageSelect(
            Bot bot, Guild guild, Settings settings, BotConfig config, long userId)
    {
        var menu = net.dv8tion.jda.api.components.selections.StringSelectMenu
                .create(languageSelectId(userId))
                .setPlaceholder(bot.msg(guild, "settings.panel.select.languagePlaceholder"));

        // Only languages whose file loaded. Offering a missing one would let someone pick a
        // language that then renders entirely in English.
        for (com.jagrosh.jmusicbot.i18n.Language option : bot.getLanguages().getAvailableLanguages())
        {
            menu.addOption(option.getNativeName(), option.name(), option.getEnglishName());
        }

        menu.setDefaultValues(effectiveLanguage(settings, config).name());
        return menu.build();
    }

    /**
     * This guild's language, never null.
     *
     * <p>It cannot be null in a running bot — the config always resolves one. But this panel
     * renders a dozen unrelated settings, and a null here would throw and take all of them off
     * the screen rather than showing one wrong word.
     */
    private static com.jagrosh.jmusicbot.i18n.Language effectiveLanguage(Settings settings, BotConfig config)
    {
        com.jagrosh.jmusicbot.i18n.Language language = settings.getLanguage(config);
        return language == null ? com.jagrosh.jmusicbot.i18n.Language.DEFAULT : language;
    }

    /** Component id for the server language menu. */
    public static String languageSelectId(long userId)
    {
        return "settings_lang_" + userId;
    }
}
