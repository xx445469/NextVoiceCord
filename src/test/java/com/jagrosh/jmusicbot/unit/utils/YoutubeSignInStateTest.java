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
package com.jagrosh.jmusicbot.unit.utils;

import com.jagrosh.jmusicbot.utils.YoutubeSignInState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("YoutubeSignInState Tests")
class YoutubeSignInStateTest {

    @Nested
    @DisplayName("compute() — the outcomes derived from ground truth")
    class Compute {

        @Test
        @DisplayName("OAuth off wins over everything else")
        void oauthOffWinsOverEverythingElse() {
            YoutubeSignInState state = YoutubeSignInState.compute(false, true, "https://www.google.com/device", "CODE");
            assertEquals(YoutubeSignInState.Phase.OAUTH_DISABLED, state.phase());
        }

        @Test
        @DisplayName("a stored token means signed in, even if a stale code is still around")
        void tokenExistsMeansSignedIn() {
            YoutubeSignInState state = YoutubeSignInState.compute(true, true, "https://www.google.com/device", "CODE");
            assertEquals(YoutubeSignInState.Phase.SIGNED_IN, state.phase());
        }

        @Test
        @DisplayName("OAuth on, no token, no code yet: waiting, not broken")
        void noTokenNoCodeMeansWaiting() {
            YoutubeSignInState state = YoutubeSignInState.compute(true, false, null, null);
            assertEquals(YoutubeSignInState.Phase.WAITING_FOR_CODE, state.phase());
        }

        @Test
        @DisplayName("OAuth on, no token, a code arrived: ready, with the code folded into the URL")
        void codeArrivedMeansReady() {
            YoutubeSignInState state = YoutubeSignInState.compute(true, false, "https://www.google.com/device", "ABCD-EFGH");
            assertEquals(YoutubeSignInState.Phase.CODE_READY, state.phase());
            assertEquals("ABCD-EFGH", state.code());
            assertEquals("https://www.google.com/device?user_code=ABCD-EFGH", state.url());
        }

        @Test
        @DisplayName("a blank code does not count as one having arrived")
        void blankCodeDoesNotCount() {
            YoutubeSignInState state = YoutubeSignInState.compute(true, false, "https://www.google.com/device", "  ");
            assertEquals(YoutubeSignInState.Phase.WAITING_FOR_CODE, state.phase());
        }
    }

    @Nested
    @DisplayName("Action outcomes — set directly by the button, not derived from compute()")
    class ActionOutcomes {

        @Test
        @DisplayName("browserOpened() keeps showing the code, and records a real browser opening")
        void browserOpenedWithoutFallback() {
            YoutubeSignInState state = YoutubeSignInState.browserOpened("https://www.google.com/device", "ABCD-EFGH", false);
            assertEquals(YoutubeSignInState.Phase.BROWSER_OPENED, state.phase());
            assertEquals("ABCD-EFGH", state.code());
            assertFalse(state.clipboardFallback());
        }

        @Test
        @DisplayName("browserOpened() records the clipboard fallback distinctly from a real browser opening")
        void browserOpenedWithFallback() {
            YoutubeSignInState state = YoutubeSignInState.browserOpened("https://www.google.com/device", "ABCD-EFGH", true);
            assertEquals(YoutubeSignInState.Phase.BROWSER_OPENED, state.phase());
            assertTrue(state.clipboardFallback());
            // The code must survive the fallback — it's the whole point of showing it at all.
            assertEquals("ABCD-EFGH", state.code());
        }

        @Test
        @DisplayName("failed() carries the reason and nothing else")
        void failedCarriesTheReason() {
            YoutubeSignInState state = YoutubeSignInState.failed("disk full");
            assertEquals(YoutubeSignInState.Phase.FAILED, state.phase());
            assertEquals("disk full", state.message());
            assertNull(state.code());
            assertNull(state.url());
        }

        @Test
        @DisplayName("signedIn() and disabled() carry no code or URL — nothing left to show")
        void terminalStatesCarryNothing() {
            assertNull(YoutubeSignInState.signedIn().code());
            assertNull(YoutubeSignInState.disabled().code());
        }
    }
}
