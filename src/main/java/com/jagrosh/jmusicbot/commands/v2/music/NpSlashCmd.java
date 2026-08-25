package com.jagrosh.jmusicbot.commands.v2.music;

import com.jagrosh.jmusicbot.Bot;

/**
 * Slash alias for now-playing output.
 */
public class NpSlashCmd extends NowPlayingSlashCmd
{
    public NpSlashCmd(Bot bot)
    {
        super(bot, "np", "shows the song that is currently playing");
    }
}
