package com.jagrosh.jmusicbot.unit.utils;

import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FormatUtilTest {

    @Test
    public void testFormatUsername() {
        assertEquals("User#1234", FormatUtil.formatUsername("User", "1234"));
        assertEquals("User", FormatUtil.formatUsername("User", "0000"));
        assertEquals("User", FormatUtil.formatUsername("User", null));
    }

    @Test
    public void testVolumeIcon() {
        assertEquals("\uD83D\uDD07", FormatUtil.volumeIcon(0));
        assertEquals("\uD83D\uDD08", FormatUtil.volumeIcon(20));
        assertEquals("\uD83D\uDD09", FormatUtil.volumeIcon(50));
        assertEquals("\uD83D\uDD0A", FormatUtil.volumeIcon(80));
    }

    @Test
    public void testFilter() {
        assertEquals("safe", FormatUtil.filter("safe"));
        assertEquals("@\u0435veryone", FormatUtil.filter("@everyone"));
        assertEquals("@h\u0435re", FormatUtil.filter("@here"));
    }

    // --- getStatusText: the bot's Discord status line ---------------------------------

    private static AudioTrack track(String title, String author) {
        AudioTrack track = mock(AudioTrack.class);
        when(track.getInfo()).thenReturn(
                new AudioTrackInfo(title, author, 243000L, "id-1", false, "https://example.com/t"));
        return track;
    }

    @Test
    @DisplayName("getStatusText() prefixes the artist a local file keeps in its own tag")
    public void getStatusText_localFileWithSeparateArtistTag_isPrefixed() {
        assertEquals("Pemcy - Riddle to me", FormatUtil.getStatusText(track("Riddle to me", "Pemcy")));
    }

    @Test
    @DisplayName("getStatusText() leaves a YouTube title that already names the artist alone")
    public void getStatusText_titleAlreadyNamesArtist_isUnchanged() {
        assertEquals("Rick Astley - Never Gonna Give You Up",
                FormatUtil.getStatusText(track("Rick Astley - Never Gonna Give You Up", "RickAstleyVEVO")));
        assertEquals("Pemcy - Riddle to me",
                FormatUtil.getStatusText(track("Pemcy - Riddle to me", "Pemcy")));
    }

    @Test
    @DisplayName("getStatusText() matches past differences in spacing and punctuation")
    public void getStatusText_matchIgnoresSpacingAndPunctuation() {
        assertEquals("R.I.C.K. Astley - Never Gonna Give You Up",
                FormatUtil.getStatusText(track("R.I.C.K. Astley - Never Gonna Give You Up", "Rick Astley")));
    }

    @Test
    @DisplayName("getStatusText() strips the suffixes YouTube adds to channel names")
    public void getStatusText_stripsYoutubeChannelSuffixes() {
        assertEquals("Pemcy - Riddle to me", FormatUtil.getStatusText(track("Riddle to me", "Pemcy - Topic")));
        assertEquals("Pemcy - Riddle to me", FormatUtil.getStatusText(track("Riddle to me", "PemcyVEVO")));
    }

    @Test
    @DisplayName("getStatusText() handles non-Latin names, where stripping punctuation must not empty the name")
    public void getStatusText_nonLatinNames() {
        assertEquals("Пемсі - Загадка", FormatUtil.getStatusText(track("Загадка", "Пемсі")));
        assertEquals("Пемсі - Загадка", FormatUtil.getStatusText(track("Пемсі - Загадка", "Пемсі")));
        assertEquals("ペムシー - なぞなぞ", FormatUtil.getStatusText(track("なぞなぞ", "ペムシー")));
    }

    @Test
    @DisplayName("getStatusText() shows the title alone when there is no usable artist")
    public void getStatusText_noUsableArtist_showsTitleAlone() {
        assertEquals("Riddle to me", FormatUtil.getStatusText(track("Riddle to me", null)));
        assertEquals("Riddle to me", FormatUtil.getStatusText(track("Riddle to me", "")));
        assertEquals("Riddle to me", FormatUtil.getStatusText(track("Riddle to me", "   ")));
        assertEquals("Riddle to me", FormatUtil.getStatusText(track("Riddle to me", "Unknown artist")));
        assertEquals("Riddle to me", FormatUtil.getStatusText(track("Riddle to me", "unknown ARTIST")));
        assertEquals("Riddle to me", FormatUtil.getStatusText(track("Riddle to me", "???")));
        assertEquals("Riddle to me", FormatUtil.getStatusText(track("Riddle to me", "VEVO")));
    }

    @Test
    @DisplayName("getStatusText() truncates after the artist is added, not before")
    public void getStatusText_truncatesTheWholeLine() {
        String longTitle = "T".repeat(99);
        String status = FormatUtil.getStatusText(track(longTitle, "Pemcy"));
        assertEquals(100, status.length());
        assertTrue(status.startsWith("Pemcy - TTT"));
        assertTrue(status.endsWith("..."));
    }

    @Test
    @DisplayName("getStatusText() falls back to the title when the title is unknown")
    public void getStatusText_noTitle_fallsBackToTitleBehaviour() {
        assertNull(FormatUtil.getStatusText(track(null, "Pemcy")));
        assertEquals("", FormatUtil.getStatusText(track("", "Pemcy")));
    }

    @Test
    @DisplayName("getTrackTitle() is untouched by the status change")
    public void getTrackTitle_unchanged() {
        assertEquals("Riddle to me", FormatUtil.getTrackTitle(track("Riddle to me", "Pemcy")));
        assertNull(FormatUtil.getTrackTitle(track(null, "Pemcy")));

        String longTitle = "T".repeat(150);
        String truncated = FormatUtil.getTrackTitle(track(longTitle, "Pemcy"));
        assertEquals(100, truncated.length());
        assertTrue(truncated.endsWith("..."));
    }
}
