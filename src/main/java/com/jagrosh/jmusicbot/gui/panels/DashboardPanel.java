/*
 * Copyright 2026 adan (xx445469) - NextVoiceCord
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
package com.jagrosh.jmusicbot.gui.panels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.gui.components.Widgets;
import com.jagrosh.jmusicbot.gui.theme.Tokens;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;

import net.dv8tion.jda.api.entities.Guild;

/**
 * The home view: what the bot is doing right now.
 *
 * <p>The window used to open on the console, which answers "what has happened" rather than
 * "what is happening". For a music bot those are different questions, and the second one is
 * the one someone opening the window is nearly always asking.
 *
 * @author adan (xx445469)
 */
public class DashboardPanel extends JPanel
{
    /** One second: fast enough that progress moves visibly, slow enough to cost nothing. */
    private static final int REFRESH_MS = 1000;

    private final Bot bot;

    private final Widgets.StatTile playingTile = new Widgets.StatTile("playing now");
    private final Widgets.StatTile serversTile = new Widgets.StatTile("servers");
    private final Widgets.StatTile listenersTile = new Widgets.StatTile("listeners");
    private final Widgets.StatTile uptimeTile = new Widgets.StatTile("uptime");
    private final Widgets.StatTile memoryTile = new Widgets.StatTile("memory");

    private final JPanel nowPlayingList = Widgets.transparent(null);
    private final Timer timer;

    public DashboardPanel(Bot bot)
    {
        this.bot = bot;

        setLayout(new BorderLayout(0, Tokens.SPACE_MD));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);

        timer = new Timer(REFRESH_MS, e -> refresh());
        timer.setRepeats(true);
    }

    private Component buildHeader()
    {
        JPanel header = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_XS));
        header.add(Widgets.pageTitle("Overview"), BorderLayout.NORTH);
        header.add(Widgets.muted("Live status across every server this bot is in"), BorderLayout.SOUTH);
        return header;
    }

    private Component buildBody()
    {
        JPanel body = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_MD));

        JPanel stats = Widgets.transparent(new GridLayout(1, 5, Tokens.SPACE_SM, 0));
        stats.add(playingTile);
        stats.add(serversTile);
        stats.add(listenersTile);
        stats.add(uptimeTile);
        stats.add(memoryTile);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        body.add(stats, BorderLayout.NORTH);

        nowPlayingList.setLayout(new BoxLayout(nowPlayingList, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(nowPlayingList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // Without this the list scrolls a few pixels per notch, because the default unit is
        // derived from a component that is not there.
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        body.add(scroll, BorderLayout.CENTER);
        return body;
    }

    /** Starts refreshing. Called when the view becomes visible. */
    public void start()
    {
        refresh();
        timer.start();
    }

    /** Stops refreshing. Called when another view takes over, so hidden views cost nothing. */
    public void stop()
    {
        timer.stop();
    }

    private void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        int playing = 0;
        int listeners = 0;
        nowPlayingList.removeAll();

        if (bot.getJDA() != null)
        {
            // Playing guilds first: on a bot in many servers, the active ones are the only
            // rows worth scrolling to.
            var guilds = new java.util.ArrayList<>(bot.getJDA().getGuilds());
            guilds.sort((a, b) -> Boolean.compare(isPlaying(b), isPlaying(a)));

            for (Guild guild : guilds)
            {
                AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                if (handler == null || !handler.isMusicPlaying(bot.getJDA()))
                {
                    continue;
                }

                playing++;
                var voice = guild.getSelfMember().getVoiceState();
                if (voice != null && voice.getChannel() != null)
                {
                    // Minus the bot, which occupies the channel without listening.
                    listeners += Math.max(0, voice.getChannel().getMembers().size() - 1);
                }

                nowPlayingList.add(buildTrackCard(guild, handler));
                nowPlayingList.add(Box.createVerticalStrut(Tokens.SPACE_SM));
            }
        }

        if (playing == 0)
        {
            nowPlayingList.add(buildEmptyState());
        }

        playingTile.setValue(String.valueOf(playing));
        playingTile.setValueColor(playing > 0 ? Tokens.success() : Tokens.text());
        serversTile.setValue(bot.getJDA() == null ? "—" : String.valueOf(bot.getJDA().getGuilds().size()));
        listenersTile.setValue(String.valueOf(listeners));
        uptimeTile.setValue(formatUptime());

        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1048576;
        long maxMb = runtime.maxMemory() / 1048576;
        memoryTile.setValue(usedMb + " MB");
        memoryTile.setCaption("of " + maxMb + " MB");

        nowPlayingList.revalidate();
        nowPlayingList.repaint();
    }

    private boolean isPlaying(Guild guild)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        return handler != null && handler.isMusicPlaying(bot.getJDA());
    }

    /** One playing server: what, where, and how far through. */
    private Component buildTrackCard(Guild guild, AudioHandler handler)
    {
        var track = handler.getPlayer().getPlayingTrack();
        boolean stream = track.getInfo().isStream;

        Widgets.Card card = new Widgets.Card();
        card.setLayout(new BorderLayout(0, Tokens.SPACE_SM));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));

        JPanel top = Widgets.transparent(new BorderLayout(Tokens.SPACE_SM, 0));
        top.add(new Widgets.Badge(handler.getPlayer().isPaused() ? "PAUSED" : "PLAYING",
                                  handler.getPlayer().isPaused() ? Tokens.warning() : Tokens.success()),
                BorderLayout.WEST);

        JLabel name = new JLabel(guild.getName());
        name.setFont(Tokens.fontHeading());
        name.setForeground(Tokens.text());
        top.add(name, BorderLayout.CENTER);

        var voice = guild.getSelfMember().getVoiceState();
        String where = voice != null && voice.getChannel() != null ? voice.getChannel().getName() : "—";
        top.add(Widgets.muted(where + "  ·  " + handler.getQueue().size() + " queued  ·  vol "
                              + handler.getPlayer().getVolume()), BorderLayout.EAST);

        JPanel middle = Widgets.transparent(null);
        middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(FormatUtil.getTrackTitle(track));
        title.setFont(Tokens.fontBody());
        title.setForeground(Tokens.text());
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel author = Widgets.muted(track.getInfo().author == null ? "" : track.getInfo().author);
        author.setAlignmentX(LEFT_ALIGNMENT);

        Widgets.Meter meter = new Widgets.Meter();
        meter.setFraction(stream || track.getDuration() <= 0
                ? 1
                : (double) track.getPosition() / track.getDuration());
        meter.setFill(stream ? Tokens.danger() : Tokens.accent());
        meter.setAlignmentX(LEFT_ALIGNMENT);

        JPanel times = Widgets.transparent(new BorderLayout());
        times.add(Widgets.muted(TimeUtil.formatTime(track.getPosition())), BorderLayout.WEST);
        times.add(Widgets.muted(stream ? "LIVE" : TimeUtil.formatTime(track.getDuration())), BorderLayout.EAST);
        times.setAlignmentX(LEFT_ALIGNMENT);
        times.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        middle.add(title);
        middle.add(author);
        middle.add(Box.createVerticalStrut(Tokens.SPACE_SM));
        middle.add(meter);
        middle.add(Box.createVerticalStrut(Tokens.SPACE_XS));
        middle.add(times);

        card.add(top, BorderLayout.NORTH);
        card.add(middle, BorderLayout.CENTER);
        return card;
    }

    private Component buildEmptyState()
    {
        Widgets.Card card = new Widgets.Card();
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_XL, Tokens.SPACE_MD, Tokens.SPACE_XL, Tokens.SPACE_MD));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        JLabel message = new JLabel(
                bot.getJDA() == null ? "Connecting to Discord…" : "Nothing is playing right now.",
                JLabel.CENTER);
        message.setFont(Tokens.fontBody());
        message.setForeground(Tokens.textMuted());

        card.add(message, BorderLayout.CENTER);
        return card;
    }

    private String formatUptime()
    {
        long seconds = Duration.between(bot.getStartTime(), Instant.now()).toSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0)
        {
            return days + "d " + hours + "h";
        }
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
