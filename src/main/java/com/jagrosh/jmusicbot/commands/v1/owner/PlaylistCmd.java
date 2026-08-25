/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
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

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.OwnerCommand;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist;

import java.io.IOException;
import java.util.List;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PlaylistCmd extends OwnerCommand 
{
    private final Bot bot;
    public PlaylistCmd(Bot bot)
    {
        this.bot = bot;
        this.guildOnly = false;
        this.name = "playlist";
        this.arguments = "<append|delete|make|setdefault>";
        this.help = "playlist management";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.children = new OwnerCommand[]{
            new ListCmd(),
            new AppendlistCmd(),
            new DeletelistCmd(),
            new MakelistCmd(),
            new DefaultlistCmd(bot)
        };
    }

    @Override
    public void execute(CommandEvent event)
    {
        StringBuilder builder = new StringBuilder(event.getClient().getWarning()+" " + bot.msg(event.getGuild(), "playlist.owner.helpTitle") + "\n");
        for(Command cmd: this.children)
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" ").append(cmd.getName())
                    .append(" ").append(cmd.getArguments()==null ? "" : cmd.getArguments()).append("` - ").append(cmd.getHelp());
        event.reply(builder.toString());
    }

    public class MakelistCmd extends OwnerCommand
    {
        public MakelistCmd()
        {
            this.name = "make";
            this.aliases = new String[]{"create"};
            this.help = "makes a new playlist";
            this.arguments = "<name>";
            this.guildOnly = false;
        }

        @Override
        protected void execute(CommandEvent event)
        {
            String pname = event.getArgs().replaceAll("\\s+", "_");
            pname = pname.replaceAll("[*?|\\/\":<>]", "");
            if(pname == null || pname.isEmpty())
            {
                event.replyError(bot.msg(event.getGuild(), "playlist.owner.errors.noName"));
            }
            else if(bot.getPlaylistLoader().getPlaylist(pname) == null)
            {
                try
                {
                    bot.getPlaylistLoader().createPlaylist(pname);
                    event.reply(event.getClient().getSuccess()+" " + bot.msg(event.getGuild(), "playlist.owner.created", pname));
                }
                catch(IOException e)
                {
                    event.reply(event.getClient().getError()+" " + bot.msg(event.getGuild(), "playlist.owner.errors.createFailed", e.getLocalizedMessage()));
                }
            }
            else
                event.reply(event.getClient().getError()+" " + bot.msg(event.getGuild(), "playlist.owner.errors.alreadyExists", pname));
        }
    }

    public class DeletelistCmd extends OwnerCommand
    {
        public DeletelistCmd()
        {
            this.name = "delete";
            this.aliases = new String[]{"remove"};
            this.help = "deletes an existing playlist";
            this.arguments = "<name>";
            this.guildOnly = false;
        }

        @Override
        protected void execute(CommandEvent event)
        {
            String pname = event.getArgs().replaceAll("\\s+", "_");
            if(bot.getPlaylistLoader().getPlaylist(pname)==null)
                event.reply(event.getClient().getError()+" " + bot.msg(event.getGuild(), "playlist.owner.errors.doesNotExist", pname));
            else
            {
                try
                {
                    bot.getPlaylistLoader().deletePlaylist(pname);
                    event.reply(event.getClient().getSuccess()+" " + bot.msg(event.getGuild(), "playlist.owner.deleted", pname));
                }
                catch(IOException e)
                {
                    event.reply(event.getClient().getError()+" " + bot.msg(event.getGuild(), "playlist.owner.errors.deleteFailed", e.getLocalizedMessage()));
                }
            }
        }
    }

    public class AppendlistCmd extends OwnerCommand
    {
        public AppendlistCmd()
        {
            this.name = "append";
            this.aliases = new String[]{"add"};
            this.help = "appends songs to an existing playlist";
            this.arguments = "<name> <URL> | <URL> | ...";
            this.guildOnly = false;
        }

        @Override
        protected void execute(CommandEvent event)
        {
            String[] parts = event.getArgs().split("\\s+", 2);
            if(parts.length<2)
            {
                event.reply(event.getClient().getError()+" " + bot.msg(event.getGuild(), "playlist.owner.errors.appendMissingArgs"));
                return;
            }
            String pname = parts[0];
            Playlist playlist = bot.getPlaylistLoader().getPlaylist(pname);
            if(playlist==null)
                event.reply(event.getClient().getError()+" " + bot.msg(event.getGuild(), "playlist.owner.errors.doesNotExist", pname));
            else
            {
                StringBuilder builder = new StringBuilder();
                playlist.getItems().forEach(item -> builder.append("\r\n").append(item));
                String[] urls = parts[1].split("\\|");
                for(String url: urls)
                {
                    String u = url.trim();
                    if(u.startsWith("<") && u.endsWith(">"))
                        u = u.substring(1, u.length()-1);
                    builder.append("\r\n").append(u);
                }
                try
                {
                    bot.getPlaylistLoader().writePlaylist(pname, builder.toString());
                    event.reply(event.getClient().getSuccess()+" " + bot.msg(event.getGuild(), "playlist.owner.appended", urls.length, pname));
                }
                catch(IOException e)
                {
                    event.reply(event.getClient().getError()+" " + bot.msg(event.getGuild(), "playlist.owner.errors.appendFailed", e.getLocalizedMessage()));
                }
            }
        }
    }

    public class DefaultlistCmd extends AutoplaylistCmd
    {
        public DefaultlistCmd(Bot bot)
        {
            super(bot);
            this.name = "setdefault";
            this.aliases = new String[]{"default"};
            this.arguments = "<playlistname|NONE>";
            this.guildOnly = true;
        }
    }

    public class ListCmd extends OwnerCommand
    {
        public ListCmd()
        {
            this.name = "all";
            this.aliases = new String[]{"available","list"};
            this.help = "lists all available playlists";
            this.guildOnly = true;
        }

        @Override
        protected void execute(CommandEvent event)
        {
            if(!bot.getPlaylistLoader().folderExists())
                bot.getPlaylistLoader().createFolder();
            if(!bot.getPlaylistLoader().folderExists())
            {
                event.reply(event.getClient().getWarning()+" " + bot.msg(event.getGuild(), "playlist.owner.errors.folderMissing"));
                return;
            }
            List<String> list = bot.getPlaylistLoader().getPlaylistNames();
            if(list==null)
                event.reply(event.getClient().getError()+" " + bot.msg(event.getGuild(), "playlist.owner.errors.loadFailed"));
            else if(list.isEmpty())
                event.reply(event.getClient().getWarning()+" " + bot.msg(event.getGuild(), "playlist.errors.noneInFolder"));
            else
            {
                StringBuilder builder = new StringBuilder(event.getClient().getSuccess()+" " + bot.msg(event.getGuild(), "playlist.owner.availableList") + "\n");
                list.forEach(str -> builder.append("`").append(str).append("` "));
                event.reply(builder.toString());
            }
        }
    }
}
