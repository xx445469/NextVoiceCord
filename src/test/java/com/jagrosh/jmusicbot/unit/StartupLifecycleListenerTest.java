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

import com.jagrosh.jmusicbot.entities.UserInteraction.Level;
import com.jagrosh.jmusicbot.listener.StartupLifecycleListener;
import com.jagrosh.jmusicbot.testutil.listener.ListenerTestFixture;
import net.dv8tion.jda.api.requests.CloseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("StartupLifecycleListener Tests")
public class StartupLifecycleListenerTest {

    private ListenerTestFixture fixture;
    private StartupLifecycleListener listener;

    @BeforeEach
    void setUp() {
        fixture = ListenerTestFixture.create();
        listener = new StartupLifecycleListener(fixture.getBot());
    }

    @Nested
    @DisplayName("onSessionDisconnect")
    class OnSessionDisconnectTests {
        @Test
        @DisplayName("shows error alert when close code is DISALLOWED_INTENTS")
        void onSessionDisconnect_showsAlertForDisallowedIntents() {
            fixture.withCloseCode(CloseCode.DISALLOWED_INTENTS);

            listener.onSessionDisconnect(fixture.getSessionDisconnectEvent());

            verify(fixture.getUserInteraction()).alert(
                    eq(Level.ERROR),
                    eq("JMusicBot"),
                    contains("missing required Discord intents")
            );
        }

        @Test
        @DisplayName("does not show alert for null close code")
        void onSessionDisconnect_doesNothingForNullCloseCode() {
            listener.onSessionDisconnect(fixture.getSessionDisconnectEvent());

            verify(fixture.getUserInteraction(), never()).alert(any(), any(), any());
        }

        @Test
        @DisplayName("does not show alert for other close codes")
        void onSessionDisconnect_doesNothingForOtherCloseCodes() {
            fixture.withCloseCode(CloseCode.GRACEFUL_CLOSE);

            listener.onSessionDisconnect(fixture.getSessionDisconnectEvent());

            verify(fixture.getUserInteraction(), never()).alert(any(), any(), any());
        }

        @Test
        @DisplayName("error message includes instructions for enabling intents")
        void onSessionDisconnect_messageIncludesInstructions() {
            fixture.withCloseCode(CloseCode.DISALLOWED_INTENTS);

            listener.onSessionDisconnect(fixture.getSessionDisconnectEvent());

            verify(fixture.getUserInteraction()).alert(
                    eq(Level.ERROR),
                    eq("JMusicBot"),
                    argThat(message ->
                            message.contains("discord.com/developers/applications") &&
                                    message.contains("MESSAGE CONTENT INTENT") &&
                                    message.contains("Privileged Gateway Intents")
                    )
            );
        }
    }

    @Nested
    @DisplayName("onShutdown")
    class OnShutdownTests {
        @Test
        @DisplayName("calls bot.shutdown()")
        void onShutdown_callsBotShutdown() {
            listener.onShutdown(fixture.getShutdownEvent());

            verify(fixture.getBot()).shutdown();
        }
    }
}
