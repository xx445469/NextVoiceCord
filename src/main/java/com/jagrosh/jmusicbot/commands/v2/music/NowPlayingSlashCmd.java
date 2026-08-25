package com.jagrosh.jmusicbot.commands.v2.music;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.v2.MusicSlashCommand;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class NowPlayingSlashCmd extends MusicSlashCommand
{
    public NowPlayingSlashCmd(Bot bot)
    {
        this(bot, "nowplaying", "shows the song that is currently playing");
    }

    protected NowPlayingSlashCmd(Bot bot, String commandName, String helpText)
    {
        super(bot);
        this.name = commandName;
        this.help = helpText;
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler == null)
        {
            event.reply(event.getClient().getWarning() + " There is no music playing in this server.").setEphemeral(true).queue();
            return;
        }

        MessageCreateData nowPlayingMsg = handler.getNowPlaying(event.getJDA());
        if (nowPlayingMsg == null)
        {
            event.reply(handler.getNoMusicPlaying(event.getJDA())).setEphemeral(true).queue();
            bot.getNowplayingHandler().clearLastNPMessage(event.getGuild());
        }
        else
        {
            event.reply(nowPlayingMsg).queue(hook -> hook.retrieveOriginal().queue(msg -> bot.getNowplayingHandler().setLastNPMessage(msg)));
        }
    }
}
