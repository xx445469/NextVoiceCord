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

import com.jagrosh.jmusicbot.listener.NowPlayingCleanupListener;
import com.jagrosh.jmusicbot.testutil.listener.ListenerTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

@DisplayName("NowPlayingCleanupListener Tests")
public class NowPlayingCleanupListenerTest {

    private ListenerTestFixture fixture;
    private NowPlayingCleanupListener listener;

    @BeforeEach
    void setUp() {
        fixture = ListenerTestFixture.create();
        listener = new NowPlayingCleanupListener(fixture.getBot());
    }

    @Nested
    @DisplayName("onMessageDelete")
    class OnMessageDeleteTests {
        @Test
        @DisplayName("delegates to NowPlayingHandler when from guild")
        void onMessageDelete_delegatesToNowPlayingHandler() {
            listener.onMessageDelete(fixture.getMessageDeleteEvent());

            verify(fixture.getNowPlayingHandler()).onMessageDelete(
                    fixture.getGuild(),
                    ListenerTestFixture.MESSAGE_ID
            );
        }

        @Test
        @DisplayName("does nothing when not from guild")
        void onMessageDelete_doesNothingWhenNotFromGuild() {
            when(fixture.getMessageDeleteEvent().isFromGuild()).thenReturn(false);

            listener.onMessageDelete(fixture.getMessageDeleteEvent());

            verify(fixture.getNowPlayingHandler(), never()).onMessageDelete(any(), anyLong());
        }
    }
}
