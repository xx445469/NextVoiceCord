package com.jagrosh.jmusicbot.utils;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingInfo;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import com.jagrosh.jmusicbot.ui.controller.ControllerLayout;
import com.jagrosh.jmusicbot.ui.controller.ControllerRenderer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class MessageFormatter {
    private static final String NP_PREFIX = "np_";
    private static final int MIN_VOLUME = 0;
    private static final int MAX_VOLUME = 150;

    public static MessageCreateData buildNowPlayingMessage(Bot bot, NowPlayingInfo info) {
        if (info.track == null)
            return buildNoMusicPlayingMessage(bot, info);

        Settings settings = bot.getSettingsManager().getSettings(info.guild);
        boolean minimalMessage = settings.useMinimalNowPlayingMessage(bot.getConfig());
        boolean showButtons = settings.showNowPlayingButtons(bot.getConfig());

        return minimalMessage
                ? buildMinimalNowPlayingMessage(bot, info, showButtons)
                : buildFullNowPlayingMessage(bot, info, showButtons);
    }

    private static MessageCreateData buildFullNowPlayingMessage(Bot bot, NowPlayingInfo info, boolean showButtons) {
        MessageCreateBuilder mb = new MessageCreateBuilder();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(info.guild.getSelfMember().getColors().getPrimary());
        eb.setAuthor(info.guild.getName(), null, info.guild.getIconUrl());

        String title = FormatUtil.filter(FormatUtil.getTrackTitle(info.track));
        try {
            eb.setTitle(title, info.track.getInfo().uri);
        } catch (Exception ignored) {
            eb.setTitle(title);
        }

        RepeatMode repeatMode = bot.getSettingsManager().getSettings(info.guild).getRepeatMode();
        eb.setDescription(buildPlaybackStatusDescription(bot, info, repeatMode, false));

        String rawAuthor = info.track.getInfo().author;
        String author = rawAuthor == null ? null : FormatUtil.filter(rawAuthor);
        if (author != null && (!author.isEmpty() && !author.equalsIgnoreCase("unknown artist"))) {
            eb.addField(bot.msg(info.guild, "nowplaying.embed.author"), author, false);
        }

        eb.addField(bot.msg(info.guild, "nowplaying.embed.duration"), TimeUtil.formatTime(info.duration), true);
        eb.addField(bot.msg(info.guild, "nowplaying.embed.queue"), String.valueOf(info.queueSize), true);
        eb.addField(bot.msg(info.guild, "nowplaying.embed.volume"), info.volume + "%", true);

        if (repeatMode != RepeatMode.OFF) {
            eb.addField(bot.msg(info.guild, "nowplaying.embed.repeat"), repeatMode.getEmoji() + " " + repeatMode.getUserFriendlyName(), true);
        }

        String requesterDetails = buildRequesterDetails(info);
        if (requesterDetails != null) {
            eb.addField(bot.msg(info.guild, "nowplaying.embed.requester"), requesterDetails, false);
        }

        String artworkUrl = resolveArtworkUrl(bot, info);
        if (artworkUrl != null) {
            eb.setThumbnail(artworkUrl);
        }
        if (info.footerInfo != null && !info.footerInfo.isEmpty()) {
            eb.setFooter(info.footerInfo);
        }

        mb.setEmbeds(eb.build());
        if (showButtons) {
            applyNowPlayingButtons(bot, mb, info, repeatMode);
        }
        return mb.build();
    }

    private static MessageCreateData buildMinimalNowPlayingMessage(Bot bot, NowPlayingInfo info, boolean showButtons) {
        MessageCreateBuilder mb = new MessageCreateBuilder();
        mb.setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " "
                + bot.msg(info.guild, "nowplaying.contentMinimal", getNowPlayingLocationName(info))));

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(info.guild.getSelfMember().getColors().getPrimary());
        String title = FormatUtil.filter(FormatUtil.getTrackTitle(info.track));
        try {
            eb.setTitle(title, info.track.getInfo().uri);
        } catch (Exception ignored) {
            eb.setTitle(title);
        }
        RepeatMode repeatMode = bot.getSettingsManager().getSettings(info.guild).getRepeatMode();
        eb.setDescription(buildPlaybackStatusDescription(bot, info, repeatMode, true));

        if (showButtons) {
            applyNowPlayingButtons(bot, mb, info, repeatMode);
        }

        mb.setEmbeds(eb.build());
        return mb.build();
    }

    public static MessageCreateData buildNoMusicPlayingMessage(Bot bot, NowPlayingInfo info) {
        Settings settings = bot.getSettingsManager().getSettings(info.guild);
        boolean minimalMessage = settings.useMinimalNowPlayingMessage(bot.getConfig());

        if (minimalMessage) {
            return buildNoMusicPlayingMessageMinimal(bot, info);
        }

        String descriptionText = bot.getConfig().showNpProgressBar()
                ? AudioHandler.STOP_EMOJI + " " + FormatUtil.progressBar(-1) + " " + FormatUtil.volumeIcon(info.volume)
                : AudioHandler.STOP_EMOJI + " " + FormatUtil.volumeIcon(info.volume);

        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " " + bot.msg(info.guild, "nowplaying.contentFull")))
                .setEmbeds(new EmbedBuilder()
                        .setTitle(bot.msg(info.guild, "nowplaying.embed.noMusicTitle"))
                        .setDescription(descriptionText)
                        .setColor(info.guild.getSelfMember().getColors().getPrimary())
                        .build())
                .build();
    }

    private static MessageCreateData buildNoMusicPlayingMessageMinimal(Bot bot, NowPlayingInfo info) {
        String descriptionText = bot.getConfig().showNpProgressBar()
                ? AudioHandler.STOP_EMOJI + " " + FormatUtil.progressBar(-1) + " " + FormatUtil.volumeIcon(info.volume)
                : AudioHandler.STOP_EMOJI + " " + FormatUtil.volumeIcon(info.volume);

        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " "
                        + bot.msg(info.guild, "nowplaying.contentMinimal", getNowPlayingLocationName(info))))
                .setEmbeds(new EmbedBuilder()
                        .setTitle(bot.msg(info.guild, "nowplaying.embed.noMusicTitle"))
                        .setDescription(descriptionText)
                        .setColor(info.guild.getSelfMember().getColors().getPrimary())
                        .build())
                .build();
    }

    private static String buildPlaybackStatusLine(Bot bot, NowPlayingInfo info) {
        String statusEmoji = info.isPaused ? AudioHandler.PAUSE_EMOJI : AudioHandler.PLAY_EMOJI;
        String timeDisplay = "`[" + TimeUtil.formatTime(info.position) + "/" + TimeUtil.formatTime(info.duration) + "]`";
        if (bot.getConfig().showNpProgressBar()) {
            double progress = info.duration > 0 ? (double) info.position / info.duration : 0;
            return statusEmoji + " " + FormatUtil.progressBar(progress) + " " + timeDisplay + " " + FormatUtil.volumeIcon(info.volume);
        }
        return statusEmoji + " " + timeDisplay + " " + FormatUtil.volumeIcon(info.volume);
    }

    private static String buildMetadataSummaryLine(Bot bot, NowPlayingInfo info, RepeatMode repeatMode, boolean minimalLayout) {
        StringBuilder summary = new StringBuilder(bot.msg(info.guild, "nowplaying.source", sourceNameForTrack(bot, info)));
        if (!minimalLayout) {
            return summary.toString();
        }

        String queuedLabel = formatQueuedLabel(bot, info.guild, info.queueSize);
        if (queuedLabel != null) {
            summary.append(" • ").append(queuedLabel);
        }
        summary.append("\n").append(bot.msg(info.guild, "nowplaying.volumeLine", info.volume));
        if (repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.SINGLE) {
            summary.append(" • ").append(bot.msg(info.guild, "nowplaying.repeatLine", repeatMode.getUserFriendlyName()));
        }
        return summary.toString();
    }

    private static String buildPlaybackStatusDescription(Bot bot, NowPlayingInfo info, RepeatMode repeatMode, boolean minimalLayout) {
        return buildPlaybackStatusLine(bot, info) + "\n" + buildMetadataSummaryLine(bot, info, repeatMode, minimalLayout);
    }

    private static String formatQueuedLabel(Bot bot, Guild guild, int queueSize) {
        if (queueSize <= 0) {
            return null;
        }
        return queueSize == 1
                ? bot.msg(guild, "nowplaying.queuedLabelOne")
                : bot.msg(guild, "nowplaying.queuedLabelMany", queueSize);
    }

    private static String getNowPlayingLocationName(NowPlayingInfo info) {
        if (info.guild.getSelfMember() != null
                && info.guild.getSelfMember().getVoiceState() != null
                && info.guild.getSelfMember().getVoiceState().getChannel() != null) {
            return info.guild.getSelfMember().getVoiceState().getChannel().getName();
        }
        return info.guild.getName();
    }

    private static String sourceNameForTrack(Bot bot, NowPlayingInfo info)
    {
        return info.track.getSourceManager() != null
                ? info.track.getSourceManager().getSourceName()
                : bot.msg(info.guild, "nowplaying.sourceUnknown");
    }

    private static String resolveArtworkUrl(Bot bot, NowPlayingInfo info)
    {
        if (info.track instanceof LocalAudioTrack || !bot.getConfig().useNPImages())
            return null;
        var artworkUrl = info.track.getInfo().artworkUrl;
        if (artworkUrl == null || artworkUrl.isEmpty())
            artworkUrl = "https://img.youtube.com/vi/" + info.track.getIdentifier() + "/mqdefault.jpg";
        return artworkUrl;
    }

    private static String buildRequesterDetails(NowPlayingInfo info)
    {
        RequestMetadata rm = info.track.getUserData(RequestMetadata.class);
        if (rm != null && rm.getOwner() != 0L)
        {
            User u = info.guild.getJDA().getUserById(rm.user.id);
            return (u == null) ? FormatUtil.formatUsername(rm.user) : u.getAsMention();
        }
        return null;
    }

    /**
     * Applies the controller buttons.
     *
     * <p>Which buttons exist and how they look comes from the guild's layout; which are
     * usable right now comes from playback state. Separating those is what lets the layout be
     * rearranged without touching rules that are not a matter of taste — you cannot skip
     * backwards past the start of the first track regardless of where the button sits.
     */
    private static void applyNowPlayingButtons(Bot bot, MessageCreateBuilder mb,
                                               NowPlayingInfo info, RepeatMode repeatMode) {
        Guild guild = info.guild;

        ControllerRenderer renderer = new ControllerRenderer(
                NP_PREFIX,
                controllerVariables(info, repeatMode),
                (key, args) -> bot.msg(guild, key, args));

        ControllerRenderer.PlaybackState state = new ControllerRenderer.PlaybackState(
                info.isPaused,
                info.position > 5000 || info.previousTrackCount > 0,
                info.queueSize > 1,
                info.volume < MAX_VOLUME,
                info.volume > MIN_VOLUME,
                info.isCurrentTrackFavorited,
                repeatMode == null ? "off" : repeatMode.name());

        ControllerLayout layout = bot.getSettingsManager()
                                     .getSettings(guild)
                                     .getControllerLayout();

        List<ActionRow> rows = renderer.render(layout, state);
        if (!rows.isEmpty()) {
            mb.setComponents(rows);
        }
    }

    /** Values a layout's labels may reference through template syntax. */
    private static Map<String, String> controllerVariables(NowPlayingInfo info, RepeatMode repeatMode) {
        Map<String, String> variables = new HashMap<>();
        variables.put("volume", String.valueOf(info.volume));
        variables.put("queue_length", String.valueOf(info.queueSize));
        variables.put("loop_mode", repeatMode == null ? "Off" : repeatMode.getUserFriendlyName());
        variables.put("track_name", info.track == null ? "None" : FormatUtil.getTrackTitle(info.track));
        return variables;
    }

    private static String nowPlayingButtonId(String action) {
        return NP_PREFIX + action;
    }
}
