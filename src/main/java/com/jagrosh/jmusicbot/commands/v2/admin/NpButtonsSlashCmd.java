package com.jagrosh.jmusicbot.commands.v2.admin;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.AdminSlashCommand;
import com.jagrosh.jmusicbot.settings.NowPlayingButtonsMode;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

public class NpButtonsSlashCmd extends AdminSlashCommand
{
    public NpButtonsSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "npbuttons";
        this.help = "sets whether now-playing messages include interactive buttons for this server";
        this.options = Collections.singletonList(
                new OptionData(OptionType.STRING, "mode", "on, off, or inherit", true)
                        .addChoice("on", "on")
                        .addChoice("off", "off")
                        .addChoice("inherit", "inherit")
        );
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doAdminCommand(SlashCommandEvent event)
    {
        String value = event.getOption("mode").getAsString();
        NowPlayingButtonsMode mode = NowPlayingButtonsMode.fromInput(value);
        if (mode == null)
        {
            event.reply(event.getClient().getError() + " Valid values are `on`, `off`, or `inherit`.")
                    .setEphemeral(true).queue();
            return;
        }

        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        settings.setNowPlayingButtonsMode(mode);
        boolean effectiveButtons = settings.showNowPlayingButtons(bot.getConfig());
        event.reply(event.getClient().getSuccess() + " Now-playing buttons mode set to `"
                + mode.getUserInputValue() + "` (effective: `" + (effectiveButtons ? "on" : "off") + "`)").queue();
    }
}
