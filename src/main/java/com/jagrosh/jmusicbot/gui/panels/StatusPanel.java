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
package com.jagrosh.jmusicbot.gui.panels;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.gui.GuiLanguage;
import com.jagrosh.jmusicbot.gui.components.Widgets;
import com.jagrosh.jmusicbot.gui.model.BotStatusData;
import com.jagrosh.jmusicbot.gui.theme.Tokens;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Panel listing every connected guild as a card showing its voice and playback status
 * directly, without requiring a click to see details.
 *
 * @author Arif Banai (arif-banai)
 */
public class StatusPanel extends JPanel {

    private final Bot bot;
    private final JPanel serverList = Widgets.transparent(null);

    private List<Guild> currentGuilds = List.of();

    public StatusPanel(Bot bot) {
        this.bot = bot;

        setLayout(new BorderLayout(0, Tokens.SPACE_MD));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG));

        serverList.setLayout(new BoxLayout(serverList, BoxLayout.Y_AXIS));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildScrollArea(), BorderLayout.CENTER);

        rebuild();
    }

    private Component buildHeader() {
        JPanel header = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_XS));
        header.add(Widgets.pageTitle(GuiLanguage.msg("gui.status.title")), BorderLayout.NORTH);
        header.add(Widgets.muted(GuiLanguage.msg("gui.status.subtitle")), BorderLayout.SOUTH);
        return header;
    }

    private Component buildScrollArea() {
        return Widgets.scrollable(serverList);
    }

    /**
     * Updates the panel with shared status data from MainFrame.
     *
     * @param statusData the shared status data
     */
    public void updateStatus(BotStatusData statusData) {
        this.currentGuilds = statusData.guilds();
        rebuild();
    }

    private void rebuild() {
        serverList.removeAll();

        if (currentGuilds.isEmpty()) {
            serverList.add(buildEmptyState());
        } else {
            for (Guild guild : currentGuilds) {
                serverList.add(buildServerCard(guild));
                serverList.add(Box.createVerticalStrut(Tokens.SPACE_SM));
            }
        }

        serverList.revalidate();
        serverList.repaint();
    }

    /** One server: voice status, what it's playing, and where — all visible without a click. */
    private Component buildServerCard(Guild guild) {
        Widgets.Card card = new Widgets.Card();
        card.setLayout(new BorderLayout(0, Tokens.SPACE_SM));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));
        card.setAlignmentX(LEFT_ALIGNMENT);

        boolean inVoice = guild.getAudioManager().isConnected();
        AudioHandler handler = null;
        var sendingHandler = guild.getAudioManager().getSendingHandler();
        if (sendingHandler instanceof AudioHandler ah) {
            handler = ah;
        }

        var player = handler != null ? handler.getPlayer() : null;
        var track = player != null ? player.getPlayingTrack() : null;
        boolean paused = player != null && player.isPaused();

        String badgeText;
        Color badgeColor;
        if (!inVoice) {
            badgeText = GuiLanguage.msg("gui.status.badgeIdle");
            badgeColor = Tokens.textMuted();
        } else if (track == null) {
            badgeText = GuiLanguage.msg("gui.status.badgeConnected");
            badgeColor = Tokens.accent();
        } else if (paused) {
            badgeText = GuiLanguage.msg("gui.status.badgePaused");
            badgeColor = Tokens.warning();
        } else {
            badgeText = GuiLanguage.msg("gui.status.badgePlaying");
            badgeColor = Tokens.success();
        }

        JPanel top = Widgets.transparent(new BorderLayout(Tokens.SPACE_SM, 0));
        top.add(new Widgets.Badge(badgeText, badgeColor), BorderLayout.WEST);

        JLabel name = new JLabel(guild.getName());
        name.setFont(Tokens.fontHeading());
        name.setForeground(Tokens.text());
        top.add(name, BorderLayout.CENTER);

        top.add(Widgets.muted(GuiLanguage.msg("gui.status.serverMeta", guild.getId(), guild.getMemberCount())), BorderLayout.EAST);

        JPanel middle = Widgets.transparent(null);
        middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));

        String titleText = GuiLanguage.msg("gui.status.nothingPlaying");
        Color titleColor = Tokens.textMuted();
        if (track != null) {
            String title = FormatUtil.getTrackTitle(track);
            if (title == null) {
                title = "";
            }
            titleText = title.length() > 60 ? title.substring(0, 57) + "..." : title;
            titleColor = Tokens.text();
        }
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(Tokens.fontBody());
        titleLabel.setForeground(titleColor);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);

        String channelText = GuiLanguage.msg("gui.status.notConnectedToVoice");
        if (inVoice) {
            AudioChannel channel = guild.getAudioManager().getConnectedChannel();
            channelText = channel != null ? channel.getName() : GuiLanguage.msg("gui.status.unknownChannel");
        }

        int queueSize = 0;
        int volume = 100;
        String repeatText = GuiLanguage.msg("gui.status.repeatOff");
        try {
            if (handler != null) {
                queueSize = handler.getQueue().size();
            }
            if (player != null) {
                volume = player.getVolume();
            }
            var repeatMode = bot.getSettingsManager().getSettings(guild).getRepeatMode();
            repeatText = repeatMode != null ? repeatMode.getUserFriendlyName() : GuiLanguage.msg("gui.status.repeatOff");
        } catch (Exception ignored) {
            // Settings unavailable for this guild — fall back to the defaults above.
        }

        JLabel metaLabel = Widgets.muted(GuiLanguage.msg("gui.status.serverMetaLine", channelText, queueSize, volume, repeatText));
        metaLabel.setAlignmentX(LEFT_ALIGNMENT);

        middle.add(titleLabel);
        middle.add(Box.createVerticalStrut(Tokens.SPACE_XS));
        middle.add(metaLabel);

        card.add(top, BorderLayout.NORTH);
        card.add(middle, BorderLayout.CENTER);
        return card;
    }

    private Component buildEmptyState() {
        Widgets.Card card = new Widgets.Card();
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_XL, Tokens.SPACE_MD, Tokens.SPACE_XL, Tokens.SPACE_MD));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JLabel message = new JLabel(GuiLanguage.msg("gui.status.noServersConnected"), JLabel.CENTER);
        message.setFont(Tokens.fontBody());
        message.setForeground(Tokens.textMuted());

        card.add(message, BorderLayout.CENTER);
        return card;
    }
}
