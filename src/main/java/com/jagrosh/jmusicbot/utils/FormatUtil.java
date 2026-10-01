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
package com.jagrosh.jmusicbot.utils;

import com.jagrosh.jmusicbot.audio.RequestMetadata.UserInfo;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.List;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class FormatUtil {

    public static String formatUsername(String username, String discrim)
    {
        if(discrim == null || discrim.equals("0000"))
        {
            return username;
        }
        else
        {
            return username + "#" + discrim;
        }
    }

    public static String formatUsername(UserInfo userinfo)
    {
        return formatUsername(userinfo.username, userinfo.discrim);
    }

    public static String formatUsername(User user)
    {
        return formatUsername(user.getName(), user.getDiscriminator());
    }
    
    public static String volumeIcon(int volume)
    {
        if(volume == 0)
            return "\uD83D\uDD07"; // 🔇
        if(volume < 30)
            return "\uD83D\uDD08"; // 🔈
        if(volume < 70)
            return "\uD83D\uDD09"; // 🔉
        return "\uD83D\uDD0A";     // 🔊
    }

    /**
     * Generates a 12-segment progress bar for track playback.
     * 
     * @param percent The progress as a value between 0.0 and 1.0. 
     *                Use negative values (e.g., -1) for "no music" state (all segments empty).
     * @return A string representing the progress bar with 🔘 at the current position and ▬ for other segments.
     */
    public static String progressBar(double percent)
    {
        StringBuilder str = new StringBuilder();
        for(int i = 0; i < 12; i++)
        {
            if(i == (int)(percent * 12))
                str.append("\uD83D\uDD18"); // 🔘
            else
                str.append("▬");
        }
        return str.toString();
    }
    
    public static String listOfTChannels(com.jagrosh.jmusicbot.Bot bot, net.dv8tion.jda.api.entities.Guild guild, List<TextChannel> list, String query)
    {
        String out = bot.msg(guild, "settings.textChannel.multipleFound", query);
        for(int i=0; i<6 && i<list.size(); i++)
            out+="\n - "+list.get(i).getName()+" (<#"+list.get(i).getId()+">)";
        if(list.size()>6)
            out+=bot.msg(guild, "common.andMoreSuffix", list.size()-6);
        return out;
    }

    public static String listOfVChannels(com.jagrosh.jmusicbot.Bot bot, net.dv8tion.jda.api.entities.Guild guild, List<VoiceChannel> list, String query)
    {
        String out = bot.msg(guild, "settings.voiceChannel.multipleFound", query);
        for(int i=0; i<6 && i<list.size(); i++)
            out+="\n - "+list.get(i).getAsMention()+" (ID:"+list.get(i).getId()+")";
        if(list.size()>6)
            out+=bot.msg(guild, "common.andMoreSuffix", list.size()-6);
        return out;
    }

    public static String listOfRoles(com.jagrosh.jmusicbot.Bot bot, net.dv8tion.jda.api.entities.Guild guild, List<Role> list, String query)
    {
        String out = bot.msg(guild, "settings.dj.multipleFound", query);
        for(int i=0; i<6 && i<list.size(); i++)
            out+="\n - "+list.get(i).getName()+" (ID:"+list.get(i).getId()+")";
        if(list.size()>6)
            out+=bot.msg(guild, "common.andMoreSuffix", list.size()-6);
        return out;
    }
    
    public static String filter(String input)
    {
        return input.replace("\u202E","")
                .replace("@everyone", "@\u0435veryone") // cyrillic letter e
                .replace("@here", "@h\u0435re") // cyrillic letter e
                .trim();
    }

    public static String getTrackTitle(AudioTrack track) {
        String title = resolveTitle(track);

        // The filename fallback is shown whole; it is a name someone chose, not a title
        // a source generated, and truncating it loses the part that identifies the file.
        if (title != null && isFilenameFallback(track)) {
            return title;
        }

        return truncateForDisplay(title);
    }

    /**
     * The track as it should read in the bot's Discord status, artist included.
     *
     * <p>Prefixes the artist when the title does not already carry it. A YouTube title
     * usually reads {@code Artist - Title} by itself, so the status line looks right with
     * no help; a local file's title tag holds only the song name, with the artist in a
     * separate field, so the status showed half of what the now-playing embed showed.
     *
     * <p>The comparison ignores case, spacing and punctuation, and drops the suffixes
     * YouTube appends to channel names, so {@code RickAstleyVEVO} still matches a
     * {@code Rick Astley - ...} title rather than being prefixed onto it. An uploader
     * whose name appears nowhere in the title is still prefixed — there is no way to tell
     * a performer from an unrelated uploader without a music database.
     *
     * @param track the playing track
     * @return {@code Artist - Title}, or just the title when it already names the artist,
     *         when there is no usable artist, or when the title itself is unknown
     */
    public static String getStatusText(AudioTrack track) {
        String title = resolveTitle(track);
        if (title == null || title.isBlank()) {
            return getTrackTitle(track);
        }

        String artist = usableArtist(track.getInfo().author);
        if (artist == null || titleNames(title, artist)) {
            return getTrackTitle(track);
        }

        return truncateForDisplay(artist + " - " + title);
    }

    /** The raw title, with a local file falling back to its filename. Never truncated. */
    private static String resolveTitle(AudioTrack track) {
        if (isFilenameFallback(track)) {
            String identifier = track.getIdentifier();
            int lastSeparator = Math.max(identifier.lastIndexOf('/'), identifier.lastIndexOf('\\'));
            return (lastSeparator != -1) ? identifier.substring(lastSeparator + 1) : identifier;
        }
        return track.getInfo().title;
    }

    /** Whether this track has no title of its own and must be named by its filename. */
    private static boolean isFilenameFallback(AudioTrack track) {
        String title = track.getInfo().title;
        return track instanceof LocalAudioTrack && (title == null || title.equals("Unknown title"));
    }

    /** Truncate if too long for Discord displays. */
    private static String truncateForDisplay(String text) {
        return (text != null && text.length() > 100) ? text.substring(0, 97) + "..." : text;
    }

    /**
     * The artist worth showing, or null. Strips the suffixes YouTube adds to channel names
     * — {@code - Topic} on its auto-generated artist channels, {@code VEVO} on label ones —
     * since neither is part of the name a listener would recognise.
     */
    private static String usableArtist(String author) {
        if (author == null) {
            return null;
        }

        String artist = author.trim();
        if (artist.regionMatches(true, artist.length() - 8, " - Topic", 0, 8)) {
            artist = artist.substring(0, artist.length() - 8).trim();
        } else if (artist.regionMatches(true, artist.length() - 4, "VEVO", 0, 4)) {
            artist = artist.substring(0, artist.length() - 4).trim();
        }

        // A name that was nothing but a suffix ("VEVO") leaves nothing to show, and
        // "Unknown artist" is what the now-playing embed already declines to show.
        if (artist.isEmpty() || artist.equalsIgnoreCase("unknown artist")) {
            return null;
        }
        return artist;
    }

    /**
     * Whether the title already names this artist, compared on letters and digits alone so
     * that spacing and punctuation cannot cause a false miss.
     */
    private static boolean titleNames(String title, String artist) {
        String needle = lettersAndDigits(artist);
        // Nothing comparable left — a punctuation-only name matches everything, so decline.
        return needle.isEmpty() || lettersAndDigits(title).contains(needle);
    }

    private static String lettersAndDigits(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    /**
     * Formats a single track line for use in embeds (e.g. playlist details preview), in the same style as
     * Queue/History: duration plus linked title, without the " - @user" part.
     *
     * @param track the loaded track
     * @return string like {@code `[MM:SS]` [**Title**](url)} or {@code `[MM:SS]` **Title**} for non-http URIs
     */
    public static String formatTrackLineForEmbed(AudioTrack track)
    {
        if (track == null)
        {
            return "`[?:??]` **Could not load**";
        }
        String entry = "`[" + TimeUtil.formatTime(track.getDuration()) + "]` ";
        var trackInfo = track.getInfo();
        String title = getTrackTitle(track);
        String safeTitle = filter(title == null ? "" : title);
        return entry + (trackInfo.uri != null && trackInfo.uri.startsWith("http")
                ? "[**" + safeTitle + "**](" + trackInfo.uri + ")"
                : "**" + safeTitle + "**");
    }
}
