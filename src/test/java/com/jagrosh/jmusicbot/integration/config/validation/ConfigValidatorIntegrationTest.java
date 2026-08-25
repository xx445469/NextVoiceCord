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
package com.jagrosh.jmusicbot.integration.config.validation;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.config.validation.ConfigValidator;
import com.jagrosh.jmusicbot.config.validation.ConfigValidator.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigValidator Integration Tests")
class ConfigValidatorIntegrationTest extends BaseConfigTest {
    
    @Nested
    @DisplayName("Token Validation with File System")
    class TokenValidationWithFileSystemTests {
        
        @Test
        @DisplayName("Validates token and writes to file when needed")
        void validatesTokenAndWritesToFileWhenNeeded() throws IOException {
            Path configFile = createTempConfigFile("token = BOT_TOKEN_HERE\nowner = 123456789");
            mockUserInteraction.setPromptResponse("new_token_from_prompt");
            
            ValidationResult result = ConfigValidator.validateToken("BOT_TOKEN_HERE", mockUserInteraction, configFile);
            
            assertTrue(result.isValid());
            assertEquals("new_token_from_prompt", result.getValue());
            assertTrue(result.needsWrite());
            
            // Verify prompt was called
            assertEquals(1, mockUserInteraction.getPromptCalls().size());
            assertTrue(mockUserInteraction.getLastPrompt().contains("bot token"));
        }
        
        @Test
        @DisplayName("Shows alert with config location on validation failure")
        void showsAlertWithConfigLocationOnValidationFailure() throws IOException {
            Path configFile = createTempConfigFile("token = BOT_TOKEN_HERE\nowner = 123456789");
            mockUserInteraction.setPromptCancelled();
            
            ValidationResult result = ConfigValidator.validateToken("BOT_TOKEN_HERE", mockUserInteraction, configFile);
            
            assertFalse(result.isValid());
            var alert = mockUserInteraction.getLastAlert();
            assertNotNull(alert);
            assertTrue(alert.getMessage().contains("Config Location"));
            assertTrue(alert.getMessage().contains(configFile.toString()));
        }
    }
    
    @Nested
    @DisplayName("Owner Validation with File System")
    class OwnerValidationWithFileSystemTests {
        
        @Test
        @DisplayName("Validates owner and writes to file when needed")
        void validatesOwnerAndWritesToFileWhenNeeded() throws IOException {
            Path configFile = createTempConfigFile("token = test_token\nowner = 0");
            mockUserInteraction.setPromptResponse("987654321");
            
            ValidationResult result = ConfigValidator.validateOwner(0L, mockUserInteraction, configFile);
            
            assertTrue(result.isValid());
            assertEquals(987654321L, (Long) result.getValue());
            assertTrue(result.needsWrite());
        }
        
        @Test
        @DisplayName("Shows error alert with config location on invalid owner")
        void showsErrorAlertWithConfigLocationOnInvalidOwner() throws IOException {
            Path configFile = createTempConfigFile("token = test_token\nowner = 0");
            mockUserInteraction.setPromptResponse("invalid_number");
            
            ValidationResult result = ConfigValidator.validateOwner(0L, mockUserInteraction, configFile);
            
            assertFalse(result.isValid());
            var alert = mockUserInteraction.getLastAlert();
            assertNotNull(alert);
            assertEquals("Config", alert.getContext());
            assertTrue(alert.getMessage().contains("Invalid User ID"));
            assertTrue(alert.getMessage().contains("Config Location"));
        }
    }
    
    @Nested
    @DisplayName("Validation Flow Integration")
    class ValidationFlowIntegrationTests {
        
        @Test
        @DisplayName("Validation stops on first failure")
        void validationStopsOnFirstFailure() throws IOException {
            Path configFile = createTempConfigFile("token = BOT_TOKEN_HERE\nowner = 0");
            mockUserInteraction.setPromptCancelled();
            
            ValidationResult tokenResult = ConfigValidator.validateToken("BOT_TOKEN_HERE", mockUserInteraction, configFile);
            assertFalse(tokenResult.isValid());
            
            // Should have shown alert
            assertEquals(1, mockUserInteraction.getAlertCalls().size());
        }
    }
}
