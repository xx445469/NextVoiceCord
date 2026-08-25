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
package com.jagrosh.jmusicbot.unit.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.spi.FilterReply;
import com.jagrosh.jmusicbot.utils.YoutubeOauth2TokenHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for YoutubeOauth2TokenHandler.
 * 
 * This handler captures OAuth authorization messages from the YouTube source library
 * and stores the authorization URL and code so they can be sent to the bot owner.
 * 
 * @see YoutubeOauth2TokenHandler
 */
@DisplayName("YoutubeOauth2TokenHandler Tests")
class YoutubeOauth2TokenHandlerTest {

    private YoutubeOauth2TokenHandler handler;
    private Logger youtubeOauthLogger;

    @BeforeEach
    void setUp() {
        handler = new YoutubeOauth2TokenHandler();
        // Get the logger that the YouTube library uses
        youtubeOauthLogger = (Logger) LoggerFactory.getLogger("dev.lavalink.youtube.http.YoutubeOauth2Handler");
    }

    @Nested
    @DisplayName("OAuth Authorization Message Capture")
    class OAuthAuthorizationMessageCapture {

        @Test
        @DisplayName("captures authorization URL and code from OAuth message")
        void capturesAuthorizationDataFromOAuthMessage() {
            String expectedUrl = "https://www.google.com/device";
            String expectedCode = "ABC-123-XYZ";
            String format = "OAUTH INTEGRATION: To give youtube-source access to your account, go to {} and enter code {}";
            Object[] params = new Object[] { expectedUrl, expectedCode };

            // Before the message, data should be null
            assertNull(handler.getData(), "Data should be null before OAuth message");

            // Process the OAuth authorization message
            FilterReply reply = handler.decide(null, youtubeOauthLogger, Level.INFO, format, params, null);

            // Should return NEUTRAL (allow the message to be logged)
            assertEquals(FilterReply.NEUTRAL, reply, "Should return NEUTRAL for OAuth auth message");

            // Data should now be captured
            YoutubeOauth2TokenHandler.Data data = handler.getData();
            assertNotNull(data, "Data should be captured after OAuth message");
            assertEquals(expectedUrl, data.getAuthorisationUrl(), "Authorization URL should match");
            assertEquals(expectedCode, data.getCode(), "Code should match");
        }

        @Test
        @DisplayName("ignores messages from other loggers")
        void ignoresMessagesFromOtherLoggers() {
            Logger otherLogger = (Logger) LoggerFactory.getLogger("com.example.OtherClass");
            String format = "OAUTH INTEGRATION: To give youtube-source access to your account, go to {} and enter code {}";
            Object[] params = new Object[] { "https://example.com", "CODE123" };

            FilterReply reply = handler.decide(null, otherLogger, Level.INFO, format, params, null);

            assertEquals(FilterReply.NEUTRAL, reply, "Should return NEUTRAL for other loggers");
            assertNull(handler.getData(), "Data should not be captured from other loggers");
        }

        @Test
        @DisplayName("ignores messages with different format")
        void ignoresMessagesWithDifferentFormat() {
            String format = "Some other message format";
            Object[] params = new Object[] { "https://example.com", "CODE123" };

            FilterReply reply = handler.decide(null, youtubeOauthLogger, Level.INFO, format, params, null);

            assertEquals(FilterReply.NEUTRAL, reply, "Should return NEUTRAL for different format");
            assertNull(handler.getData(), "Data should not be captured from different format");
        }
    }

    @Nested
    @DisplayName("OAuth Token Success Message")
    class OAuthTokenSuccessMessage {

        @Test
        @DisplayName("denies (suppresses) token success message to hide sensitive data")
        void deniesTokenSuccessMessage() {
            String format = "OAUTH INTEGRATION: Token retrieved successfully. Store your refresh token as this can be reused. ({})";
            String token = "ya29.a0AfH6SMBxxxxxxxxxxxxxx";
            Object[] params = new Object[] { token };

            FilterReply reply = handler.decide(null, youtubeOauthLogger, Level.INFO, format, params, null);

            // Should return DENY to suppress the token from being logged
            assertEquals(FilterReply.DENY, reply, "Should return DENY for token success message to hide token");
        }

        @Test
        @DisplayName("ignores token success message from other loggers")
        void ignoresTokenSuccessFromOtherLoggers() {
            Logger otherLogger = (Logger) LoggerFactory.getLogger("com.example.OtherClass");
            String format = "OAUTH INTEGRATION: Token retrieved successfully. Store your refresh token as this can be reused. ({})";
            Object[] params = new Object[] { "token123" };

            FilterReply reply = handler.decide(null, otherLogger, Level.INFO, format, params, null);

            assertEquals(FilterReply.NEUTRAL, reply, "Should return NEUTRAL for other loggers");
        }
    }

    @Nested
    @DisplayName("Data Class Tests")
    class DataClassTests {

        @Test
        @DisplayName("Data class stores authorization URL correctly")
        void dataClassStoresAuthorizationUrl() {
            String format = "OAUTH INTEGRATION: To give youtube-source access to your account, go to {} and enter code {}";
            String url = "https://www.google.com/device";
            Object[] params = new Object[] { url, "CODE" };

            handler.decide(null, youtubeOauthLogger, Level.INFO, format, params, null);

            assertEquals(url, handler.getData().getAuthorisationUrl());
        }

        @Test
        @DisplayName("Data class stores code correctly")
        void dataClassStoresCode() {
            String format = "OAUTH INTEGRATION: To give youtube-source access to your account, go to {} and enter code {}";
            String code = "ABCD-1234-EFGH";
            Object[] params = new Object[] { "https://example.com", code };

            handler.decide(null, youtubeOauthLogger, Level.INFO, format, params, null);

            assertEquals(code, handler.getData().getCode());
        }
    }

    @Nested
    @DisplayName("Handler Initialization")
    class HandlerInitialization {

        @Test
        @DisplayName("getData returns null before any OAuth message")
        void getDataReturnsNullInitially() {
            YoutubeOauth2TokenHandler newHandler = new YoutubeOauth2TokenHandler();
            assertNull(newHandler.getData(), "getData should return null initially");
        }

        @Test
        @DisplayName("init method does not throw")
        void initMethodDoesNotThrow() {
            YoutubeOauth2TokenHandler newHandler = new YoutubeOauth2TokenHandler();
            assertDoesNotThrow(() -> newHandler.init(), "init should not throw");
        }
    }

    @Nested
    @DisplayName("Message Format Matching - Regression Prevention")
    class MessageFormatMatching {

        @Test
        @DisplayName("exact format string match is required for OAuth auth message")
        void exactFormatStringMatchRequired() {
            // These slightly different formats should NOT match
            String[] wrongFormats = {
                "OAUTH INTEGRATION: To give youtube-source access to your account, go to {} and enter code {} ",
                "OAUTH INTEGRATION: To give youtube-source access to your account, go to {} and enter code",
                "oauth integration: to give youtube-source access to your account, go to {} and enter code {}",
                "OAUTH INTEGRATION:To give youtube-source access to your account, go to {} and enter code {}",
            };

            Object[] params = new Object[] { "https://example.com", "CODE" };

            for (String wrongFormat : wrongFormats) {
                handler = new YoutubeOauth2TokenHandler(); // Reset handler
                handler.decide(null, youtubeOauthLogger, Level.INFO, wrongFormat, params, null);
                assertNull(handler.getData(), 
                    "Data should not be captured for format: " + wrongFormat);
            }
        }

        @Test
        @DisplayName("exact format string match is required for token success message")
        void exactFormatStringMatchRequiredForTokenSuccess() {
            String correctFormat = "OAUTH INTEGRATION: Token retrieved successfully. Store your refresh token as this can be reused. ({})";
            Object[] params = new Object[] { "token" };

            FilterReply reply = handler.decide(null, youtubeOauthLogger, Level.INFO, correctFormat, params, null);
            assertEquals(FilterReply.DENY, reply, "Correct format should be denied");

            // Wrong format should return NEUTRAL
            String wrongFormat = "OAUTH INTEGRATION: Token retrieved successfully. ({})";
            reply = handler.decide(null, youtubeOauthLogger, Level.INFO, wrongFormat, params, null);
            assertEquals(FilterReply.NEUTRAL, reply, "Wrong format should return NEUTRAL");
        }
    }
}
