/*
 * Copyright 2017 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.commands.v1.owner;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.OwnerCommand;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SetgameCmd extends OwnerCommand
{
    private final Bot bot;

    public SetgameCmd(Bot bot)
    {
        this.bot = bot;
        this.name = "setgame";
        this.help = "sets the game the bot is playing";
        this.arguments = "[action] [game]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = false;
        this.children = new OwnerCommand[]{
            new SetlistenCmd(),
            new SetstreamCmd(),
            new SetwatchCmd()
        };
    }

    private Guild guildOrNull(CommandEvent event)
    {
        return event.getChannelType() != ChannelType.PRIVATE ? event.getGuild() : null;
    }

    @Override
    protected void execute(CommandEvent event)
    {
        Guild guild = guildOrNull(event);
        String title = event.getArgs().toLowerCase().startsWith("playing") ? event.getArgs().substring(7).trim() : event.getArgs();
        try
        {
            event.getJDA().getPresence().setActivity(title.isEmpty() ? null : Activity.playing(title));
            event.reply(event.getClient().getSuccess()+" " + (title.isEmpty()
                    ? bot.msg(guild, "owner.setgame.playingCleared", event.getSelfUser().getName())
                    : bot.msg(guild, "owner.setgame.playingSet", event.getSelfUser().getName(), title)));
        }
        catch(Exception e)
        {
            event.reply(event.getClient().getError()+" " + bot.msg(guild, "owner.setgame.errors.setFailed"));
        }
    }

    private class SetstreamCmd extends OwnerCommand
    {
        private SetstreamCmd()
        {
            this.name = "stream";
            this.aliases = new String[]{"twitch","streaming"};
            this.help = "sets the game the bot is playing to a stream";
            this.arguments = "<username> <game>";
            this.guildOnly = false;
        }

        @Override
        protected void execute(CommandEvent event)
        {
            Guild guild = guildOrNull(event);
            String[] parts = event.getArgs().split("\\s+", 2);
            if(parts.length<2)
            {
                event.replyError(bot.msg(guild, "owner.setgame.errors.streamMissingArgs"));
                return;
            }
            try
            {
                event.getJDA().getPresence().setActivity(Activity.streaming(parts[1], "https://twitch.tv/"+parts[0]));
                event.replySuccess(bot.msg(guild, "owner.setgame.streamSet", event.getSelfUser().getName(), parts[1]));
            }
            catch(Exception e)
            {
                event.reply(event.getClient().getError()+" " + bot.msg(guild, "owner.setgame.errors.setFailed"));
            }
        }
    }

    private class SetlistenCmd extends OwnerCommand
    {
        private SetlistenCmd()
        {
            this.name = "listen";
            this.aliases = new String[]{"listening"};
            this.help = "sets the game the bot is listening to";
            this.arguments = "<title>";
            this.guildOnly = false;
        }

        @Override
        protected void execute(CommandEvent event)
        {
            Guild guild = guildOrNull(event);
            if(event.getArgs().isEmpty())
            {
                event.replyError(bot.msg(guild, "owner.setgame.errors.listenMissingTitle"));
                return;
            }
            String title = event.getArgs().toLowerCase().startsWith("to") ? event.getArgs().substring(2).trim() : event.getArgs();
            try
            {
                event.getJDA().getPresence().setActivity(Activity.listening(title));
                event.replySuccess(bot.msg(guild, "owner.setgame.listenSet", event.getSelfUser().getName(), title));
            } catch(Exception e) {
                event.reply(event.getClient().getError()+" " + bot.msg(guild, "owner.setgame.errors.setFailed"));
            }
        }
    }

    private class SetwatchCmd extends OwnerCommand
    {
        private SetwatchCmd()
        {
            this.name = "watch";
            this.aliases = new String[]{"watching"};
            this.help = "sets the game the bot is watching";
            this.arguments = "<title>";
            this.guildOnly = false;
        }

        @Override
        protected void execute(CommandEvent event)
        {
            Guild guild = guildOrNull(event);
            if(event.getArgs().isEmpty())
            {
                event.replyError(bot.msg(guild, "owner.setgame.errors.watchMissingTitle"));
                return;
            }
            String title = event.getArgs();
            try
            {
                event.getJDA().getPresence().setActivity(Activity.watching(title));
                event.replySuccess(bot.msg(guild, "owner.setgame.watchSet", event.getSelfUser().getName(), title));
            } catch(Exception e) {
                event.reply(event.getClient().getError()+" " + bot.msg(guild, "owner.setgame.errors.setFailed"));
            }
        }
    }
}
