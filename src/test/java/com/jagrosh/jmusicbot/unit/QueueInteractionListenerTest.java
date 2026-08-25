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
package com.jagrosh.jmusicbot.unit;

import com.jagrosh.jmusicbot.listener.QueueInteractionListener;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.testutil.listener.ListenerTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("QueueInteractionListener Tests")
class QueueInteractionListenerTest
{
    private ListenerTestFixture fixture;
    private QueueInteractionListener listener;

    @BeforeEach
    void setUp()
    {
        fixture = ListenerTestFixture.create();
        listener = new QueueInteractionListener(fixture.getBot());
        when(fixture.getButtonInteractionEvent().getUser()).thenReturn(fixture.getUser());
    }

    @Test
    @DisplayName("onButtonInteraction() rejects select action not present on current page")
    void onButtonInteraction_selectOutsidePage_repliesError()
    {
        fixture.withButtonId("queue_select11_1_0_" + fixture.getUser().getIdLong())
                .withMemberInVoiceChannel();
        MusicService.QueueInfo info = new MusicService.QueueInfo(
                new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"},
                0L,
                null,
                "",
                RepeatMode.OFF,
                QueueType.LINEAR,
                null,
                null
        );
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(info);

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getButtonInteractionEvent()).reply(org.mockito.ArgumentMatchers.argThat((String s) -> s.contains("isn't on this page")));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }
}
