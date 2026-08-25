/*
 * Copyright 2026 Arif Banai (arif-banai)
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
package com.jagrosh.jmusicbot.listener;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.commands.SlashCommandRegistry;
import com.jagrosh.jmusicbot.entities.UserInteraction.Level;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.utils.YoutubeOauth2TokenHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.CloseCode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Handles startup and lifecycle events: ready, session disconnect, shutdown, guild join.
 */
public class StartupLifecycleListener extends ListenerAdapter {

    private final Bot bot;

    public StartupLifecycleListener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onReady(ReadyEvent event) {
        if (event.getJDA().getGuildCache().isEmpty()) {
            Logger log = LoggerFactory.getLogger("MusicBot");
            String inviteUrl = event.getJDA().getInviteUrl(JMusicBot.RECOMMENDED_PERMS);
            log.warn("This bot is not on any guilds! Use the following link to add the bot to your guilds!");
            log.warn(inviteUrl);
            bot.getUserInteraction().alert(Level.WARNING, "Setup",
                    "This bot is not on any guilds!\n\nUse this link to add the bot to your server:\n" + inviteUrl);
        }

        if (bot.getCommandClient() != null) {
            SlashCommandRegistry.registerIfChanged(event.getJDA(), bot.getCommandClient());
        }

        credit(event.getJDA());
        event.getJDA().getGuilds().forEach((Guild guild) -> {
            try {
                String defpl = bot.getSettingsManager().getSettings(guild).getDefaultPlaylist();
                VoiceChannel vc = bot.getSettingsManager().getSettings(guild).getVoiceChannel(guild);
                if (defpl != null && vc != null && bot.getPlayerManager().setUpHandler(guild).playFromDefault()) {
                    guild.getAudioManager().openAudioConnection(vc);
                }
            } catch (Exception ignore) {
            }
        });
        if (bot.getConfig().useUpdateAlerts()) {
            bot.getThreadpool().scheduleWithFixedDelay(() -> {
                try {
                    User owner = bot.getJDA().retrieveUserById(bot.getConfig().getOwnerId()).complete();
                    String currentVersion = OtherUtil.getCurrentVersion();
                    String latestVersion = OtherUtil.getLatestVersion(bot.getConfig());
                    if (latestVersion != null && OtherUtil.isNewerVersion(currentVersion, latestVersion)) {
                        String msg = String.format(OtherUtil.NEW_VERSION_AVAILABLE, currentVersion, latestVersion);
                        owner.openPrivateChannel().queue(pc -> pc.sendMessage(msg).queue());
                    }
                } catch (Exception ignored) {
                }
            }, 0, 24, TimeUnit.HOURS);
        }
        if (bot.getConfig().useYouTubeOauth()) {
            YoutubeOauth2TokenHandler.Data data = bot.getYouTubeOauth2Handler().getData();
            if (data != null) {
                try {
                    PrivateChannel channel = bot.getJDA().openPrivateChannelById(bot.getConfig().getOwnerId()).complete();
                    channel
                            .sendMessage(
                                    "# DO NOT AUTHORISE THIS WITH YOUR MAIN GOOGLE ACCOUNT!!!\n"
                                            + "## Create or use an alternative/burner Google account!\n"
                                            + "To give JMusicBot access to your Google account, go to "
                                            + data.getAuthorisationUrl()
                                            + " and enter the code **" + data.getCode() + "**")
                            .queue();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void onSessionDisconnect(@NotNull SessionDisconnectEvent event) {
        CloseCode closeCode = event.getCloseCode();
        if (closeCode == CloseCode.DISALLOWED_INTENTS) {
            bot.getUserInteraction().alert(
                    Level.ERROR,
                    "JMusicBot",
                    "Your bot is missing required Discord intents!\n\n"
                            + "To fix this:\n"
                            + "1. Go to https://discord.com/developers/applications\n"
                            + "2. Select your bot application\n"
                            + "3. Go to 'Bot' settings\n"
                            + "4. Enable 'MESSAGE CONTENT INTENT' under Privileged Gateway Intents\n"
                            + "5. Save changes and restart JMusicBot"
            );
        }
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event) {
        bot.shutdown();
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        credit(event.getJDA());
    }

    private void credit(JDA jda) {
        Guild dbots = jda.getGuildById(110373943822540800L);
        if (dbots == null) {
            return;
        }
        if (bot.getConfig().getDBots()) {
            return;
        }
        jda.getTextChannelById(119222314964353025L)
                .sendMessage("This account is running JMusicBot. Please do not list bot clones on this server, <@" + bot.getConfig().getOwnerId() + ">.").complete();
        dbots.leave().queue();
    }
}
