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
package com.jagrosh.jmusicbot.unit.config.validation;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.config.validation.ConfigValidator;
import com.jagrosh.jmusicbot.config.validation.ConfigValidator.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigValidator Unit Tests")
class ConfigValidatorTest extends BaseConfigTest {
    
    @Nested
    @DisplayName("validateToken() Tests")
    class ValidateTokenTests {
        
        @Test
        @DisplayName("validateToken() accepts valid token")
        void validateTokenAcceptsValidToken() {
            Path configPath = tempDir.resolve("config.conf");
            String validToken = "valid_token_12345";
            
            ValidationResult result = ConfigValidator.validateToken(validToken, mockUserInteraction, configPath);
            
            assertTrue(result.isValid());
            assertEquals(validToken, result.getValue());
            assertFalse(result.needsWrite());
            assertEquals(0, mockUserInteraction.getPromptCalls().size());
        }
        
        @Test
        @DisplayName("validateToken() prompts for null token")
        void validateTokenPromptsForNullToken() {
            Path configPath = tempDir.resolve("config.conf");
            mockUserInteraction.setPromptResponse("new_token_from_user");
            
            ValidationResult result = ConfigValidator.validateToken(null, mockUserInteraction, configPath);
            
            assertTrue(result.isValid());
            assertEquals("new_token_from_user", result.getValue());
            assertTrue(result.needsWrite());
            assertEquals(1, mockUserInteraction.getPromptCalls().size());
            assertTrue(mockUserInteraction.getLastPrompt().contains("bot token"));
        }
        
        @Test
        @DisplayName("validateToken() prompts for empty token")
        void validateTokenPromptsForEmptyToken() {
            Path configPath = tempDir.resolve("config.conf");
            mockUserInteraction.setPromptResponse("new_token_from_user");
            
            ValidationResult result = ConfigValidator.validateToken("", mockUserInteraction, configPath);
            
            assertTrue(result.isValid());
            assertEquals("new_token_from_user", result.getValue());
            assertTrue(result.needsWrite());
        }
        
        @Test
        @DisplayName("validateToken() shows alert with config location on cancel")
        void validateTokenShowsAlertWithConfigLocationOnCancel() {
            Path configPath = tempDir.resolve("config.conf");
            mockUserInteraction.setPromptCancelled();
            
            ConfigValidator.validateToken(null, mockUserInteraction, configPath);
            
            var alert = mockUserInteraction.getLastAlert();
            assertNotNull(alert);
            assertTrue(alert.getMessage().contains("Config Location"));
            assertTrue(alert.getMessage().contains(configPath.toString()));
        }
    }
    
    @Nested
    @DisplayName("validateOwner() Tests")
    class ValidateOwnerTests {
        
        @Test
        @DisplayName("validateOwner() accepts valid owner ID")
        void validateOwnerAcceptsValidOwnerId() {
            Path configPath = tempDir.resolve("config.conf");
            Long validOwner = 123456789L;
            
            ValidationResult result = ConfigValidator.validateOwner(validOwner, mockUserInteraction, configPath);
            
            assertTrue(result.isValid());
            assertEquals(validOwner, result.getValue());
            assertFalse(result.needsWrite());
            assertEquals(0, mockUserInteraction.getPromptCalls().size());
        }
        
        @Test
        @DisplayName("validateOwner() prompts for null owner")
        void validateOwnerPromptsForNullOwner() {
            Path configPath = tempDir.resolve("config.conf");
            mockUserInteraction.setPromptResponse("987654321");
            
            ValidationResult result = ConfigValidator.validateOwner(null, mockUserInteraction, configPath);
            
            assertTrue(result.isValid());
            assertEquals(987654321L, (Long) result.getValue());
            assertTrue(result.needsWrite());
            assertEquals(1, mockUserInteraction.getPromptCalls().size());
        }
        
        @Test
        @DisplayName("validateOwner() prompts for negative owner")
        void validateOwnerPromptsForNegativeOwner() {
            Path configPath = tempDir.resolve("config.conf");
            mockUserInteraction.setPromptResponse("987654321");
            
            ValidationResult result = ConfigValidator.validateOwner(-1L, mockUserInteraction, configPath);
            
            assertTrue(result.isValid());
            assertEquals(987654321L, (Long) result.getValue());
            assertTrue(result.needsWrite());
        }
        
        @Test
        @DisplayName("validateOwner() returns invalid for non-numeric input")
        void validateOwnerReturnsInvalidForNonNumericInput() {
            Path configPath = tempDir.resolve("config.conf");
            mockUserInteraction.setPromptResponse("not_a_number");
            
            ValidationResult result = ConfigValidator.validateOwner(null, mockUserInteraction, configPath);
            
            assertFalse(result.isValid());
            assertNull(result.getValue());
            assertFalse(result.needsWrite());
            assertEquals(1, mockUserInteraction.getAlertCalls().size());
        }
        
        @Test
        @DisplayName("validateOwner() returns invalid for zero input")
        void validateOwnerReturnsInvalidForZeroInput() {
            Path configPath = tempDir.resolve("config.conf");
            mockUserInteraction.setPromptResponse("0");
            
            ValidationResult result = ConfigValidator.validateOwner(null, mockUserInteraction, configPath);
            
            assertFalse(result.isValid());
            assertNull(result.getValue());
            assertFalse(result.needsWrite());
        }
        
        @Test
        @DisplayName("validateOwner() returns invalid when user cancels")
        void validateOwnerReturnsInvalidWhenUserCancels() {
            Path configPath = tempDir.resolve("config.conf");
            mockUserInteraction.setPromptCancelled();
            
            ValidationResult result = ConfigValidator.validateOwner(null, mockUserInteraction, configPath);
            
            assertFalse(result.isValid());
            assertNull(result.getValue());
            assertFalse(result.needsWrite());
        }
        
    }
    
    @Nested
    @DisplayName("ValidationResult Tests")
    class ValidationResultTests {
        
        @Test
        @DisplayName("ValidationResult stores value correctly")
        void validationResultStoresValueCorrectly() {
            ValidationResult result = new ValidationResult("test_value", true, false);
            
            assertEquals("test_value", result.getValue());
            assertTrue(result.isValid());
            assertFalse(result.needsWrite());
        }
        
        @Test
        @DisplayName("ValidationResult handles different value types")
        void validationResultHandlesDifferentValueTypes() {
            ValidationResult stringResult = new ValidationResult("string", true, false);
            ValidationResult longResult = new ValidationResult(123L, true, false);
            
            assertEquals("string", stringResult.getValue());
            assertEquals(123L, (Long) longResult.getValue());
        }
        
        @Test
        @DisplayName("ValidationResult with needsWrite flag")
        void validationResultWithNeedsWriteFlag() {
            ValidationResult result = new ValidationResult("value", true, true);
            
            assertTrue(result.needsWrite());
        }
        
        @Test
        @DisplayName("ValidationResult invalid result")
        void validationResultInvalidResult() {
            ValidationResult result = new ValidationResult(null, false, false);
            
            assertFalse(result.isValid());
            assertNull(result.getValue());
        }
    }
}
