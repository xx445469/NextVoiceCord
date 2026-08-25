package com.jagrosh.jmusicbot.commands.v2.admin;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.AdminSlashCommand;
import com.jagrosh.jmusicbot.settings.NowPlayingLayoutMode;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

public class NpLayoutSlashCmd extends AdminSlashCommand
{
    public NpLayoutSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "nplayout";
        this.help = "sets the now-playing layout mode for this server";
        this.options = Collections.singletonList(
                new OptionData(OptionType.STRING, "mode", "full, minimal, or inherit", true)
                        .addChoice("full", "full")
                        .addChoice("minimal", "minimal")
                        .addChoice("inherit", "inherit")
        );
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doAdminCommand(SlashCommandEvent event)
    {
        String value = event.getOption("mode").getAsString();
        NowPlayingLayoutMode mode = NowPlayingLayoutMode.fromInput(value);
        if (mode == null)
        {
            event.reply(event.getClient().getError() + " Valid values are `full`, `minimal`, or `inherit`.")
                    .setEphemeral(true).queue();
            return;
        }

        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        settings.setNowPlayingLayoutMode(mode);
        boolean effectiveMinimal = settings.useMinimalNowPlayingMessage(bot.getConfig());
        event.reply(event.getClient().getSuccess() + " Now-playing layout mode set to `"
                + mode.getUserInputValue() + "` (effective: `" + (effectiveMinimal ? "minimal" : "full") + "`)").queue();
    }
}
