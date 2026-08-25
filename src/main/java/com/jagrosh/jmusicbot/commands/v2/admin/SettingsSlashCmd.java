package com.jagrosh.jmusicbot.commands.v2.admin;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.AdminSlashCommand;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

public class SettingsSlashCmd extends AdminSlashCommand
{
    public SettingsSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "settings";
        this.help = "opens an interactive panel for server settings";
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doAdminCommand(SlashCommandEvent event)
    {
        Settings settings = bot.getSettingsManager().getSettings(event.getGuild());
        long userId = event.getUser().getIdLong();
        String invokerName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
        event.reply(new MessageCreateBuilder()
                        .setComponents(SettingsPanelRenderer.buildSettingsMessageComponents(
                                event.getGuild(), settings, bot.getConfig(), userId, invokerName))
                        .useComponentsV2()
                        .build())
                .setEphemeral(true)
                .queue();
    }
}
