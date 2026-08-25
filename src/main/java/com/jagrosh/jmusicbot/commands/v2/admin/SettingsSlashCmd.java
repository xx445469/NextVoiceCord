package com.jagrosh.jmusicbot.commands.v2.admin;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.settings.Settings;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

/**
 * The one place settings live, for the server and for the person running it.
 *
 * <p>No longer admin-only. Gating the whole command on Manage Server meant the only setting an
 * ordinary member has — the language the bot answers them in — needed a command of its own, and
 * two settings commands is one too many. What someone can change is decided per control now,
 * not at the door.
 *
 * <p>Which view opens depends on what they can do: server settings for someone who can manage
 * the server, their own settings for everyone else. Opening the server view for a member who
 * cannot use any of it would be a page of controls that all refuse them.
 */
public class SettingsSlashCmd extends SlashCommand
{
    private final Bot bot;

    public SettingsSlashCmd(Bot bot)
    {
        this.bot = bot;
        this.name = "settings";
        this.help = "opens the settings panel";
        this.guildOnly = true;
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    protected void execute(SlashCommandEvent event)
    {
        boolean isOwner = event.getUser().getId().equals(event.getClient().getOwnerId());
        boolean canManage = isOwner
                || (event.getMember() != null && event.getMember().hasPermission(Permission.MANAGE_SERVER));

        // Ephemeral either way. A server-settings panel in the channel invites everyone else to
        // press its buttons and be told no, and a personal preference is nobody else's business.
        event.reply(new MessageCreateBuilder()
                        .setComponents(canManage ? serverView(event) : personalView(event))
                        .useComponentsV2()
                        .build())
                .setEphemeral(true)
                .queue();
    }

    private java.util.List<net.dv8tion.jda.api.components.MessageTopLevelComponent> serverView(SlashCommandEvent event)
    {
        Settings settings = bot.getSettingsManager().getSettings(event.getGuild());
        String invokerName = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getUser().getName();

        return SettingsPanelRenderer.buildSettingsMessageComponents(
                bot, event.getGuild(), settings, bot.getConfig(),
                event.getUser().getIdLong(), invokerName, event.getUser());
    }

    private java.util.List<net.dv8tion.jda.api.components.MessageTopLevelComponent> personalView(SlashCommandEvent event)
    {
        return UserSettingsPanelRenderer.buildPersonalMessageComponents(
                bot, event.getGuild(), event.getUser(), false);
    }
}
