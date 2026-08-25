package com.jagrosh.jmusicbot.commands.v1.admin;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.AdminCommand;
import com.jagrosh.jmusicbot.settings.NowPlayingLayoutMode;
import com.jagrosh.jmusicbot.settings.Settings;

public class NplayoutCmd extends AdminCommand
{
    private final Bot bot;

    public NplayoutCmd(Bot bot)
    {
        this.bot = bot;
        this.name = "nplayout";
        this.help = "sets the now-playing layout mode for this server";
        this.arguments = "<full|minimal|inherit>";
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    protected void execute(CommandEvent event)
    {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        if (event.getArgs().isEmpty())
        {
            NowPlayingLayoutMode current = settings.getNowPlayingLayoutMode();
            boolean effectiveMinimal = settings.useMinimalNowPlayingMessage(bot.getConfig());
            event.replySuccess(bot.msg(event.getGuild(), "settings.npLayout.current",
                    current.getUserInputValue(), effectiveMinimal ? "minimal" : "full"));
            return;
        }

        NowPlayingLayoutMode mode = NowPlayingLayoutMode.fromInput(event.getArgs());
        if (mode == null)
        {
            event.replyError(bot.msg(event.getGuild(), "settings.npLayout.invalid"));
            return;
        }

        settings.setNowPlayingLayoutMode(mode);
        boolean effectiveMinimal = settings.useMinimalNowPlayingMessage(bot.getConfig());
        event.replySuccess(bot.msg(event.getGuild(), "settings.npLayout.set",
                mode.getUserInputValue(), effectiveMinimal ? "minimal" : "full"));
    }
}
