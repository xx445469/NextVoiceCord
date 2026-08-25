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

import com.jagrosh.jmusicbot.commands.v2.music.HistorySlashCmd;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.testutil.commands.SlashCommandTestFixture;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HistorySlashCmd}.
 */
public class HistorySlashCmdTest
{
    private SlashCommandTestFixture fixture;
    private HistorySlashCmd command;

    @BeforeEach
    void setUp()
    {
        fixture = SlashCommandTestFixture.create();
        fixture.withReplyQueueCallback().withRetrieveQueueCallback();
        command = new HistorySlashCmd(fixture.getBot());
    }

    @Test
    void testDoCommand_EmptyHistory_ShowsEphemeralWarning()
    {
        when(fixture.getEvent().getOption("page")).thenReturn(null);
        when(fixture.getMusicService().getHistoryInfo(fixture.getGuild(), fixture.getJda())).thenReturn(null);

        command.doCommand(fixture.getEvent());

        verify(fixture.getEvent()).reply(any(String.class));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }

    @Test
    void testDoCommand_EmptyHistoryInfo_ShowsEphemeralWarning()
    {
        when(fixture.getEvent().getOption("page")).thenReturn(null);
        MusicService.HistoryInfo empty = new MusicService.HistoryInfo(new String[0], 0L, 10);
        when(fixture.getMusicService().getHistoryInfo(fixture.getGuild(), fixture.getJda())).thenReturn(empty);

        command.doCommand(fixture.getEvent());

        verify(fixture.getEvent()).reply(any(String.class));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }

    @Test
    void testDoCommand_DisabledHistory_ShowsDisabledWarning()
    {
        when(fixture.getEvent().getOption("page")).thenReturn(null);
        MusicService.HistoryInfo disabled = new MusicService.HistoryInfo(new String[0], 0L, 0, true);
        when(fixture.getMusicService().getHistoryInfo(fixture.getGuild(), fixture.getJda())).thenReturn(disabled);

        command.doCommand(fixture.getEvent());

        verify(fixture.getEvent()).reply(argThat((String s) -> s.contains("disabled by config")));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }

    @Test
    void testDoCommand_WithHistory_ShowsEmbedAndComponents()
    {
        when(fixture.getEvent().getOption("page")).thenReturn(null);
        MusicService.HistoryInfo historyInfo = new MusicService.HistoryInfo(
                new String[]{"Track 1", "Track 2", "Track 3"},
                300000L,
                10
        );
        when(fixture.getMusicService().getHistoryInfo(fixture.getGuild(), fixture.getJda())).thenReturn(historyInfo);

        command.doCommand(fixture.getEvent());

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(fixture.getEvent()).replyEmbeds(embedCaptor.capture());
        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("History Queue", embed.getTitle());
        assertTrue(embed.getDescription().contains("Track 1"));
        assertTrue(embed.getDescription().contains("Recently played"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ActionRow>> componentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(fixture.getReplyAction()).setComponents(componentsCaptor.capture());
        assertFalse(componentsCaptor.getValue().isEmpty());
    }

    @Test
    void testGetTracksOnPage()
    {
        assertEquals(10, HistorySlashCmd.getTracksOnPage(1, 25));
        assertEquals(5, HistorySlashCmd.getTracksOnPage(3, 25));
        assertEquals(3, HistorySlashCmd.getTracksOnPage(1, 3));
    }

    @Test
    void testGetTotalPages()
    {
        assertEquals(1, HistorySlashCmd.getTotalPages(5));
        assertEquals(2, HistorySlashCmd.getTotalPages(11));
        assertEquals(3, HistorySlashCmd.getTotalPages(25));
    }

    @Test
    void testBuildHistoryComponents_NoSelection_ReturnsFourRowsWithAddAllAndSaveButtons()
    {
        List<ActionRow> rows = HistorySlashCmd.buildHistoryComponents(1, 2, 10, 0, 12345L);
        assertEquals(4, rows.size());
        // Row 4 is action row: "Add all to queue" and "Save as playlist" when no track selected
        ActionRow actionRow = rows.get(3);
        assertEquals(2, actionRow.getComponents().size());
        assertEquals("Add all to queue", ((Button) actionRow.getComponents().get(0)).getLabel());
        assertEquals("Save as playlist", ((Button) actionRow.getComponents().get(1)).getLabel());
    }

    @Test
    void testBuildHistoryComponents_WithSelection_ReturnsFourRows()
    {
        List<ActionRow> rows = HistorySlashCmd.buildHistoryComponents(1, 2, 10, 5, 12345L);
        assertEquals(4, rows.size());
    }

    @Test
    void testBuildHistoryComponents_FewerTracks_UsesSparseButtons()
    {
        List<ActionRow> rows = HistorySlashCmd.buildHistoryComponents(1, 1, 3, 0, 12345L);
        assertEquals(2, rows.size());
        ActionRow selectRow = rows.get(0);
        assertEquals(3, selectRow.getComponents().size());
        assertEquals("1", ((Button) selectRow.getComponents().get(0)).getLabel());
        assertEquals("3", ((Button) selectRow.getComponents().get(2)).getLabel());
    }

    @Test
    void testBuildHistoryComponents_PageTwo_UsesAbsoluteButtonLabels()
    {
        List<ActionRow> rows = HistorySlashCmd.buildHistoryComponents(2, 3, 10, 0, 12345L);
        assertEquals(4, rows.size());
        ActionRow row1 = rows.get(0);
        ActionRow row2 = rows.get(1);
        assertEquals("11", ((Button) row1.getComponents().get(0)).getLabel());
        assertEquals("15", ((Button) row1.getComponents().get(4)).getLabel());
        assertEquals("16", ((Button) row2.getComponents().get(0)).getLabel());
        assertEquals("20", ((Button) row2.getComponents().get(4)).getLabel());
    }
}
