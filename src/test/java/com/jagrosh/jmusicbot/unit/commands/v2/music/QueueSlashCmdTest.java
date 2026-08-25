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
package com.jagrosh.jmusicbot.unit.commands.v2.music;

import com.jagrosh.jmusicbot.commands.v2.music.QueueSlashCmd;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.testutil.commands.SlashCommandTestFixture;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link QueueSlashCmd}.
 * Uses SlashCommandTestFixture for cleaner, more maintainable tests.
 */
public class QueueSlashCmdTest
{
    private SlashCommandTestFixture fixture;
    private QueueSlashCmd command;

    @Mock
    private OptionMapping pageOption;
    @Mock
    private MessageCreateData noMusicMsg;
    @Mock
    private MessageCreateData nowPlayingMsg;
    @Mock
    private MessageEmbed embed;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        fixture = SlashCommandTestFixture.create();
        fixture.withReplyQueueCallback().withRetrieveQueueCallback();
        command = new QueueSlashCmd(fixture.getBot());
    }

    @Test
    void testDoCommand_EmptyQueue_ShowsNoMusicMessage()
    {
        // Given
        when(fixture.getEvent().getOption("page")).thenReturn(null);
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(null);

        when(noMusicMsg.getEmbeds()).thenReturn(Collections.singletonList(embed));
        MusicService.NowPlayingInfo npInfo = new MusicService.NowPlayingInfo(null, noMusicMsg, false);
        when(fixture.getMusicService().getNowPlayingInfo(fixture.getGuild(), fixture.getJda())).thenReturn(npInfo);
        when(fixture.getEvent().reply(any(MessageCreateData.class))).thenReturn(fixture.getReplyAction());

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply(any(MessageCreateData.class));
    }

    @Test
    void testDoCommand_EmptyQueueWithPlaying_ShowsNowPlayingMessage()
    {
        // Given
        when(fixture.getEvent().getOption("page")).thenReturn(null);
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(null);

        when(nowPlayingMsg.getEmbeds()).thenReturn(Collections.singletonList(embed));
        MusicService.NowPlayingInfo npInfo = new MusicService.NowPlayingInfo(nowPlayingMsg, null, true);
        when(fixture.getMusicService().getNowPlayingInfo(fixture.getGuild(), fixture.getJda())).thenReturn(npInfo);
        when(fixture.getEvent().reply(any(MessageCreateData.class))).thenReturn(fixture.getReplyAction());

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply(any(MessageCreateData.class));
        verify(fixture.getNowPlayingHandler()).setLastNPMessage(fixture.getMessage());
    }

    @Test
    void testDoCommand_EmptyQueueNoNowPlayingInfo_ShowsEphemeralWarning()
    {
        // Given
        when(fixture.getEvent().getOption("page")).thenReturn(null);
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(null);
        when(fixture.getMusicService().getNowPlayingInfo(fixture.getGuild(), fixture.getJda())).thenReturn(null);
        when(fixture.getReplyAction().setEphemeral(true)).thenReturn(fixture.getReplyAction());

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply(contains("There is no music in the queue"));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }

    @Test
    void testDoCommand_WithQueue_ShowsFirstPageWithButtons()
    {
        // Given
        when(fixture.getEvent().getOption("page")).thenReturn(null);
        MusicService.QueueInfo queueInfo = new MusicService.QueueInfo(
                new String[]{"Track 1", "Track 2", "Track 3"},
                300000L,
                "Now Playing",
                "✅",
                RepeatMode.OFF,
                QueueType.LINEAR,
                null,
                null
        );
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(queueInfo);

        // When
        command.doCommand(fixture.getEvent());

        // Then - verify replyEmbeds was called with embed
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(fixture.getEvent()).replyEmbeds(embedCaptor.capture());

        MessageEmbed capturedEmbed = embedCaptor.getValue();
        assertEquals("Current Queue", capturedEmbed.getTitle());
        assertTrue(capturedEmbed.getDescription().contains("Now Playing"));
        assertTrue(capturedEmbed.getDescription().contains("Track 1"));

        // Verify components were set (sparse selection rows + pagination/actions)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ActionRow>> componentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(fixture.getReplyAction()).setComponents(componentsCaptor.capture());
        List<ActionRow> components = componentsCaptor.getValue();
        assertEquals(2, components.size()); // 3 tracks => one select row + pagination row
    }

    @Test
    void testDoCommand_WithQueueAndPageNumber_ShowsSpecifiedPage()
    {
        // Given
        int page = 2;
        when(fixture.getEvent().getOption("page")).thenReturn(pageOption);
        when(pageOption.getAsLong()).thenReturn((long) page);

        MusicService.QueueInfo queueInfo = new MusicService.QueueInfo(
                new String[]{"Track 1", "Track 2", "Track 3", "Track 4", "Track 5",
                        "Track 6", "Track 7", "Track 8", "Track 9", "Track 10",
                        "Track 11", "Track 12"},
                600000L,
                "Now Playing",
                "✅",
                RepeatMode.OFF,
                QueueType.LINEAR,
                null,
                null
        );
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(queueInfo);

        // When
        command.doCommand(fixture.getEvent());

        // Then - verify embed footer contains page 2
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(fixture.getEvent()).replyEmbeds(embedCaptor.capture());
        MessageEmbed capturedEmbed = embedCaptor.getValue();
        assertNotNull(capturedEmbed.getFooter());
        assertTrue(capturedEmbed.getFooter().getText().contains("Page 2"));
    }

    @Test
    void testDoCommand_PageNumberExceedsTotalPages_ShowsLastPage()
    {
        // Given
        int page = 5;
        when(fixture.getEvent().getOption("page")).thenReturn(pageOption);
        when(pageOption.getAsLong()).thenReturn((long) page);

        MusicService.QueueInfo queueInfo = new MusicService.QueueInfo(
                new String[]{"Track 1", "Track 2", "Track 3"},
                300000L,
                "Now Playing",
                "✅",
                RepeatMode.OFF,
                QueueType.LINEAR,
                null,
                null
        );
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(queueInfo);

        // When
        command.doCommand(fixture.getEvent());

        // Then - should clamp to last page (1)
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(fixture.getEvent()).replyEmbeds(embedCaptor.capture());
        MessageEmbed capturedEmbed = embedCaptor.getValue();
        assertNotNull(capturedEmbed.getFooter());
        assertTrue(capturedEmbed.getFooter().getText().contains("Page 1"));
    }

    @Test
    void testDoCommand_QueueWithManyTracks_PaginatesCorrectly()
    {
        // Given
        when(fixture.getEvent().getOption("page")).thenReturn(null);

        String[] tracks = new String[25]; // 25 tracks = 3 pages
        for (int i = 0; i < 25; i++)
        {
            tracks[i] = "Track " + (i + 1);
        }
        MusicService.QueueInfo queueInfo = new MusicService.QueueInfo(
                tracks,
                1500000L,
                "Now Playing",
                "✅",
                RepeatMode.OFF,
                QueueType.LINEAR,
                null,
                null
        );
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(queueInfo);

        // When
        command.doCommand(fixture.getEvent());

        // Then
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(fixture.getEvent()).replyEmbeds(embedCaptor.capture());
        MessageEmbed capturedEmbed = embedCaptor.getValue();

        // Verify footer shows page 1 of 3
        assertNotNull(capturedEmbed.getFooter());
        assertTrue(capturedEmbed.getFooter().getText().contains("Page 1 of 3"));

        // Verify description contains first page tracks
        assertTrue(capturedEmbed.getDescription().contains("Track 1"));
        assertTrue(capturedEmbed.getDescription().contains("Track 10"));
    }

    @Test
    void testBuildQueueComponents_NoSelection_Returns3Rows()
    {
        // Given
        int page = 1;
        int totalPages = 2;
        int tracksOnPage = 10;
        int selectedTrack = 0; // No selection
        long userId = 123456789L;

        // When
        List<ActionRow> components = QueueSlashCmd.buildQueueComponents(page, totalPages, tracksOnPage, selectedTrack, userId);

        // Then
        assertEquals(3, components.size()); // Track row 1-5, Track row 6-10, Pagination+Shuffle
    }

    @Test
    void testBuildQueueComponents_WithSelection_Returns4Rows()
    {
        // Given
        int page = 1;
        int totalPages = 2;
        int tracksOnPage = 10;
        int selectedTrack = 5; // Track 5 selected
        long userId = 123456789L;

        // When
        List<ActionRow> components = QueueSlashCmd.buildQueueComponents(page, totalPages, tracksOnPage, selectedTrack, userId);

        // Then
        assertEquals(4, components.size()); // Track row 1-5, Track row 6-10, Pagination+Shuffle, Actions
    }

    @Test
    void testBuildQueueComponents_FewerTracks_UsesSparseButtons()
    {
        // Given
        int page = 1;
        int totalPages = 1;
        int tracksOnPage = 3; // Only 3 tracks
        int selectedTrack = 0;
        long userId = 123456789L;

        // When
        List<ActionRow> components = QueueSlashCmd.buildQueueComponents(page, totalPages, tracksOnPage, selectedTrack, userId);

        // Then
        assertEquals(2, components.size());
        ActionRow firstRow = components.get(0);
        assertEquals(3, firstRow.getComponents().size());
        assertEquals("1", ((Button) firstRow.getComponents().get(0)).getLabel());
        assertEquals("3", ((Button) firstRow.getComponents().get(2)).getLabel());
        ActionRow paginationAndActionsRow = components.get(1);
        assertEquals(1, paginationAndActionsRow.getComponents().size());
        assertEquals("Shuffle", ((Button) paginationAndActionsRow.getComponents().get(0)).getLabel());
    }

    @Test
    void testBuildQueueComponents_PageTwo_UsesAbsoluteButtonLabels()
    {
        List<ActionRow> components = QueueSlashCmd.buildQueueComponents(2, 3, 10, 0, 123456789L);
        assertEquals(3, components.size());
        ActionRow row1 = components.get(0);
        ActionRow row2 = components.get(1);
        assertEquals("11", ((Button) row1.getComponents().get(0)).getLabel());
        assertEquals("15", ((Button) row1.getComponents().get(4)).getLabel());
        assertEquals("16", ((Button) row2.getComponents().get(0)).getLabel());
        assertEquals("20", ((Button) row2.getComponents().get(4)).getLabel());
    }

    @Test
    void testGetTracksOnPage_FirstPage()
    {
        assertEquals(10, QueueSlashCmd.getTracksOnPage(1, 25));
        assertEquals(10, QueueSlashCmd.getTracksOnPage(1, 10));
        assertEquals(5, QueueSlashCmd.getTracksOnPage(1, 5));
    }

    @Test
    void testGetTracksOnPage_LastPage()
    {
        assertEquals(5, QueueSlashCmd.getTracksOnPage(3, 25)); // Page 3 of 25 tracks = 5 remaining
        assertEquals(2, QueueSlashCmd.getTracksOnPage(2, 12)); // Page 2 of 12 tracks = 2 remaining
    }

    @Test
    void testGetTotalPages()
    {
        assertEquals(1, QueueSlashCmd.getTotalPages(5));
        assertEquals(1, QueueSlashCmd.getTotalPages(10));
        assertEquals(2, QueueSlashCmd.getTotalPages(11));
        assertEquals(3, QueueSlashCmd.getTotalPages(25));
    }
}
