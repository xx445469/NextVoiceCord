package com.jagrosh.jmusicbot.commands.help;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jmusicbot.Bot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;

/**
 * Builds the {@code /help} and {@code !help} menu: a single embed plus a category dropdown and a
 * row of link buttons, shaped after Vocard's help view (see {@code voicelink/views/help.py} in
 * the Vocard project — read for the shape, nothing here is copied from it).
 *
 * <p>Static and stateless like {@link com.jagrosh.jmusicbot.commands.v2.admin.SettingsPanelRenderer}:
 * every method that produces user-visible text takes {@code bot} (and usually {@code guild} and
 * {@code user}) and resolves strings through {@link Bot#msgFor}, because a help reply is always
 * addressed to the one person who asked for it.
 *
 * <p>Decorative marks — the category emoji, and the ✅/🔒 in front of each command — are literal
 * constants rather than translation keys, the same way {@code config.getSuccess()}'s emoji is
 * used directly elsewhere in this codebase. A glyph is not a sentence; there is nothing in it
 * for a translator to translate.
 */
public final class HelpPanelRenderer
{
    private static final String COMPONENT_PREFIX = "help_";
    private static final Color EMBED_COLOR = new Color(0x5865F2); // Discord blurple; no brand color of our own yet.

    // Left room for the code fence and the "and N more" suffix so a category never has to be
    // measured against the raw 1024-char field limit at the call site.
    private static final int COMMANDS_FIELD_BUDGET = 900;

    private static final String MARK_USABLE = "✅";
    private static final String MARK_LOCKED = "🔒";

    // Add another Button.link(...) here once the owner has a Buy Me a Coffee / GitHub Sponsors
    // handle to point it at — nothing else about this row needs to change.
    private static final String GITHUB_URL = "https://github.com/xx445469/NextVoiceCord";

    private HelpPanelRenderer() { }

    public static String selectId(long ownerUserId)
    {
        return COMPONENT_PREFIX + "category_" + ownerUserId;
    }

    /**
     * The prefix to show in front of a text-only command's name.
     *
     * <p>A guild-specific prefix wins over the configured default, matching how the command
     * client itself resolves it. The mention prefix is spelled out as an {@code @mention} rather
     * than shown as the literal configuration keyword, which would mean nothing to a reader.
     */
    public static String effectivePrefix(Bot bot, Guild guild, JDA jda)
    {
        String prefix = guild != null ? bot.getSettingsManager().getSettings(guild).getPrefix() : null;
        if (prefix == null)
        {
            prefix = bot.getConfig().getPrefix();
        }
        if ("@mention".equals(prefix))
        {
            return "@" + jda.getSelfUser().getName() + " ";
        }
        return prefix;
    }

    public static ActionRow linkButtonsRow(Bot bot, Guild guild, User user)
    {
        return ActionRow.of(Button.link(GITHUB_URL, bot.msgFor(guild, user, "help.buttons.github")));
    }

    public static StringSelectMenu categorySelect(Bot bot, Guild guild, User user, String current)
    {
        StringSelectMenu.Builder menu = StringSelectMenu.create(selectId(user.getIdLong()))
                .setPlaceholder(bot.msgFor(guild, user, "help.selectMenu.placeholder"))
                .setMinValues(1)
                .setMaxValues(1);

        menu.addOption(bot.msgFor(guild, user, "help.selectMenu.news.label"), HelpCategory.NEWS,
                bot.msgFor(guild, user, "help.selectMenu.news.description"), Emoji.fromUnicode("🆕"));
        menu.addOption(bot.msgFor(guild, user, "help.selectMenu.tutorial.label"), HelpCategory.TUTORIAL,
                bot.msgFor(guild, user, "help.selectMenu.tutorial.description"), Emoji.fromUnicode("🕹️"));

        for (String key : HelpCategory.REAL)
        {
            menu.addOption(bot.msgFor(guild, user, "help.selectMenu." + key + ".label"), key,
                    bot.msgFor(guild, user, "help.selectMenu." + key + ".description"), Emoji.fromUnicode(realCategoryEmoji(key)));
        }

        menu.setDefaultValues(current);
        return menu.build();
    }

    public static List<ActionRow> buildRows(Bot bot, Guild guild, User user, String current)
    {
        return List.of(linkButtonsRow(bot, guild, user), ActionRow.of(categorySelect(bot, guild, user, current)));
    }

    /** Same rows, with the dropdown disabled — used once the menu times out. */
    public static List<ActionRow> buildDisabledRows(Bot bot, Guild guild, User user, String current)
    {
        return List.of(linkButtonsRow(bot, guild, user),
                ActionRow.of(categorySelect(bot, guild, user, current).asDisabled()));
    }

    public static MessageEmbed buildEmbed(Bot bot, Guild guild, User user, String categoryKey,
            List<Command> allCommands, Set<String> slashNames, String textPrefix,
            boolean isOwner, boolean canManageServer, boolean djPermission)
    {
        return switch (categoryKey)
        {
            case HelpCategory.NEWS -> newsEmbed(bot, guild, user);
            case HelpCategory.TUTORIAL -> tutorialEmbed(bot, guild, user);
            default -> categoryEmbed(bot, guild, user, categoryKey, allCommands, slashNames, textPrefix,
                    isOwner, canManageServer, djPermission);
        };
    }

    private static MessageEmbed newsEmbed(Bot bot, Guild guild, User user)
    {
        return new EmbedBuilder()
                .setTitle(bot.msgFor(guild, user, "help.news.title"))
                .setColor(EMBED_COLOR)
                .addField(bot.msgFor(guild, user, "help.fields.categories", HelpCategory.ALL.size()),
                        categoriesFieldValue(bot, guild, user, HelpCategory.NEWS), true)
                .addField(bot.msgFor(guild, user, "help.fields.information"),
                        bot.msgFor(guild, user, "help.news.information"), true)
                .addField(bot.msgFor(guild, user, "help.fields.getStarted"),
                        bot.msgFor(guild, user, "help.news.getStarted"), false)
                .build();
    }

    private static MessageEmbed tutorialEmbed(Bot bot, Guild guild, User user)
    {
        return new EmbedBuilder()
                .setTitle(bot.msgFor(guild, user, "help.category.title", categoryLabel(bot, guild, user, HelpCategory.TUTORIAL)))
                .setColor(EMBED_COLOR)
                .addField(bot.msgFor(guild, user, "help.fields.categories", HelpCategory.ALL.size()),
                        categoriesFieldValue(bot, guild, user, HelpCategory.TUTORIAL), true)
                .addField(bot.msgFor(guild, user, "help.fields.information"),
                        bot.msgFor(guild, user, "help.tutorial.information"), true)
                .addField(bot.msgFor(guild, user, "help.fields.tutorialSteps"),
                        bot.msgFor(guild, user, "help.tutorial.steps"), false)
                .build();
    }

    private static MessageEmbed categoryEmbed(Bot bot, Guild guild, User user, String categoryKey,
            List<Command> allCommands, Set<String> slashNames, String textPrefix,
            boolean isOwner, boolean canManageServer, boolean djPermission)
    {
        List<Command> commands = commandsForCategory(allCommands, categoryKey);
        boolean usable = HelpAccess.canUse(categoryKey, isOwner, canManageServer, djPermission);
        String label = categoryLabel(bot, guild, user, categoryKey);

        return new EmbedBuilder()
                .setTitle(bot.msgFor(guild, user, "help.category.title", label))
                .setColor(EMBED_COLOR)
                .addField(bot.msgFor(guild, user, "help.fields.categories", HelpCategory.ALL.size()),
                        categoriesFieldValue(bot, guild, user, categoryKey), true)
                .addField(bot.msgFor(guild, user, "help.fields.information"),
                        bot.msgFor(guild, user, "help.category.info." + categoryKey), true)
                .addField(bot.msgFor(guild, user, "help.fields.commands", label, commands.size()),
                        "```\n" + commandsFieldValue(bot, guild, user, commands, slashNames, textPrefix, usable) + "```",
                        false)
                .build();
    }

    /** The pointer list on the left — every category, with 👉 marking the one being viewed. */
    public static String categoriesFieldValue(Bot bot, Guild guild, User user, String selected)
    {
        StringBuilder sb = new StringBuilder("```py\n");
        int index = 1;
        for (String key : HelpCategory.ALL)
        {
            sb.append(key.equals(selected) ? "👉 " : index + ". ")
                    .append(categoryLabel(bot, guild, user, key))
                    .append('\n');
            index++;
        }
        return sb.append("```").toString();
    }

    public static String categoryLabel(Bot bot, Guild guild, User user, String key)
    {
        return bot.msgFor(guild, user, "help.category.label." + key);
    }

    /** Commands in {@code categoryKey}, alphabetical for a stable, scannable order. */
    public static List<Command> commandsForCategory(List<Command> allCommands, String categoryKey)
    {
        List<Command> result = new ArrayList<>();
        for (Command command : allCommands)
        {
            if (HelpCategory.keyFor(command).equals(categoryKey))
            {
                result.add(command);
            }
        }
        result.sort(Comparator.comparing(Command::getName));
        return result;
    }

    /**
     * The command list itself, one line per command, truncated with an "and N more" if a
     * category ever grows past the field budget.
     *
     * @param usable whether the viewer can use commands in this category — uniform across the
     *               whole list, since no command in this bot sets a permission of its own beyond
     *               its category's
     */
    public static String commandsFieldValue(Bot bot, Guild guild, User user, List<Command> commands,
            Set<String> slashNames, String textPrefix, boolean usable)
    {
        String mark = usable ? MARK_USABLE : MARK_LOCKED;
        StringBuilder sb = new StringBuilder();
        int shown = 0;

        for (Command command : commands)
        {
            String invocation = slashNames.contains(command.getName())
                    ? "/" + command.getName()
                    : textPrefix + command.getName();
            StringBuilder line = new StringBuilder(mark).append(' ').append(invocation)
                    .append(" — ").append(command.getHelp());

            String[] aliases = command.getAliases();
            if (aliases != null && aliases.length > 0)
            {
                line.append(bot.msgFor(guild, user, "help.command.aliasesSuffix", String.join(", ", aliases)));
            }
            line.append('\n');

            if (sb.length() + line.length() > COMMANDS_FIELD_BUDGET)
            {
                break;
            }
            sb.append(line);
            shown++;
        }

        if (shown < commands.size())
        {
            sb.append(bot.msgFor(guild, user, "common.andMoreSuffix", commands.size() - shown));
        }
        return sb.toString();
    }

    private static String realCategoryEmoji(String key)
    {
        return switch (key)
        {
            case HelpCategory.MUSIC -> "🎵";
            case HelpCategory.DJ -> "🎧";
            case HelpCategory.ADMIN -> "🛠️";
            case HelpCategory.OWNER -> "👑";
            default -> "🌐"; // General
        };
    }
}
