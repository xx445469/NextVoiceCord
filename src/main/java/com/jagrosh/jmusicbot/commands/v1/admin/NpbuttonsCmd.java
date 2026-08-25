package com.jagrosh.jmusicbot.commands.v1.admin;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.AdminCommand;
import com.jagrosh.jmusicbot.settings.NowPlayingButtonsMode;
import com.jagrosh.jmusicbot.settings.Settings;

public class NpbuttonsCmd extends AdminCommand
{
    private final Bot bot;

    public NpbuttonsCmd(Bot bot)
    {
        this.bot = bot;
        this.name = "npbuttons";
        this.help = "sets whether now-playing messages include interactive buttons for this server";
        this.arguments = "<on|off|inherit>";
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    protected void execute(CommandEvent event)
    {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        if (event.getArgs().isEmpty())
        {
            NowPlayingButtonsMode current = settings.getNowPlayingButtonsMode();
            boolean effectiveButtons = settings.showNowPlayingButtons(bot.getConfig());
            event.replySuccess("Now-playing buttons mode is `" + current.getUserInputValue()
                    + "` (effective: `" + (effectiveButtons ? "on" : "off") + "`).");
            return;
        }

        NowPlayingButtonsMode mode = NowPlayingButtonsMode.fromInput(event.getArgs());
        if (mode == null)
        {
            event.replyError("Please provide one of: `on`, `off`, or `inherit`.");
            return;
        }

        settings.setNowPlayingButtonsMode(mode);
        boolean effectiveButtons = settings.showNowPlayingButtons(bot.getConfig());
        event.replySuccess("Now-playing buttons mode set to `" + mode.getUserInputValue()
                + "` (effective: `" + (effectiveButtons ? "on" : "off") + "`).");
    }
}
