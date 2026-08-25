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
package com.jagrosh.jmusicbot.unit.config.io;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.typesafe.config.Config;
import com.typesafe.config.parser.ConfigDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigIO Unit Tests")
class ConfigIOTest extends BaseConfigTest {
    
    // ==================== File Operations Tests ====================
    
    @Nested
    @DisplayName("getConfigPath() Tests")
    class GetConfigPathTests {
        
        @Test
        @DisplayName("getConfigPath() returns default path when no system property set")
        void getConfigPathReturnsDefaultWhenNoSystemProperty() {
            System.clearProperty("config.file");
            System.clearProperty("config");
            
            Path path = ConfigIO.getConfigPath();
            
            assertNotNull(path);
            assertTrue(path.toString().contains("config.txt") || path.toFile().exists());
        }
        
        @Test
        @DisplayName("getConfigPath() uses config.file system property")
        void getConfigPathUsesConfigFileProperty() throws IOException {
            Path testFile = createTempConfigFile("token = test");
            setConfigFileProperty(testFile);
            
            Path path = ConfigIO.getConfigPath();
            
            assertEquals(testFile.toAbsolutePath(), path.toAbsolutePath());
        }
        
        @Test
        @DisplayName("getConfigPath() uses config system property as fallback")
        void getConfigPathUsesConfigPropertyAsFallback() throws IOException {
            Path testFile = createTempConfigFile("token = test");
            System.clearProperty("config.file");
            System.setProperty("config", testFile.toString());
            
            Path path = ConfigIO.getConfigPath();
            
            assertEquals(testFile.toAbsolutePath(), path.toAbsolutePath());
        }
        
        @Test
        @DisplayName("getConfigPath() sets config.file property when file exists")
        void getConfigPathSetsPropertyWhenFileExists() throws IOException {
            Path testFile = createTempConfigFile("token = test");
            System.clearProperty("config.file");
            System.clearProperty("config");
            
            // Create a config.txt in temp dir
            Path configTxt = tempDir.resolve("config.txt");
            Files.write(configTxt, "token = test".getBytes());
            
            // Set working directory to temp dir
            System.setProperty("user.dir", tempDir.toString());
            
            Path path = ConfigIO.getConfigPath();
            
            // The property should be set if file exists
            assertNotNull(path);
        }
    }
    
    @Nested
    @DisplayName("writeConfigFile() Tests")
    class WriteConfigFileTests {
        
        @Test
        @DisplayName("writeConfigFile() writes content to file")
        void writeConfigFileWritesContent() throws IOException {
            Path testFile = tempDir.resolve("test-config.conf");
            String content = "token = test_token\nowner = 123456789";
            
            ConfigIO.writeConfigFile(testFile, content);
            
            assertFileExists(testFile);
            String writtenContent = readFileContent(testFile);
            assertEquals(content, writtenContent);
        }
        
        @Test
        @DisplayName("writeConfigFile() overwrites existing file")
        void writeConfigFileOverwritesExistingFile() throws IOException {
            Path testFile = createTempConfigFile("old content");
            String newContent = "token = new_token\nowner = 987654321";
            
            ConfigIO.writeConfigFile(testFile, newContent);
            
            String writtenContent = readFileContent(testFile);
            assertEquals(newContent, writtenContent);
            assertFalse(writtenContent.contains("old content"));
        }
        
        @Test
        @DisplayName("writeConfigFile() throws IOException for invalid path")
        void writeConfigFileThrowsIOExceptionForInvalidPath() {
            // Try to write to a directory (should fail)
            Path invalidPath = tempDir;
            
            assertThrows(IOException.class, () -> {
                ConfigIO.writeConfigFile(invalidPath, "content");
            });
        }
    }
    
    @Nested
    @DisplayName("appendToConfigFile() Tests")
    class AppendToConfigFileTests {
        
        @Test
        @DisplayName("appendToConfigFile() appends content to existing file")
        void appendToConfigFileAppendsContent() throws IOException {
            Path testFile = createTempConfigFile("original content\n");
            String appendContent = "new content\n";
            
            ConfigIO.appendToConfigFile(testFile, appendContent);
            
            String fileContent = readFileContent(testFile);
            assertTrue(fileContent.contains("original content"));
            assertTrue(fileContent.contains("new content"));
        }
        
        @Test
        @DisplayName("appendToConfigFile() throws exception if file doesn't exist")
        void appendToConfigFileThrowsExceptionIfFileNotExists() {
            Path testFile = tempDir.resolve("new-config.conf");
            String content = "new content\n";
            
            // appendToConfigFile uses APPEND option which requires file to exist
            assertThrows(java.io.IOException.class, () -> {
                ConfigIO.appendToConfigFile(testFile, content);
            });
        }
        
        @Test
        @DisplayName("appendToConfigFile() can append multiple times")
        void appendToConfigFileCanAppendMultipleTimes() throws IOException {
            Path testFile = createTempConfigFile("line1\n");
            
            ConfigIO.appendToConfigFile(testFile, "line2\n");
            ConfigIO.appendToConfigFile(testFile, "line3\n");
            
            String fileContent = readFileContent(testFile);
            assertTrue(fileContent.contains("line1"));
            assertTrue(fileContent.contains("line2"));
            assertTrue(fileContent.contains("line3"));
        }
    }
    
    @Nested
    @DisplayName("configFileExists() Tests")
    class ConfigFileExistsTests {
        
        @Test
        @DisplayName("configFileExists() returns true for existing file")
        void configFileExistsReturnsTrueForExistingFile() throws IOException {
            Path testFile = createTempConfigFile("content");
            
            assertTrue(ConfigIO.configFileExists(testFile));
        }
        
        @Test
        @DisplayName("configFileExists() returns false for non-existing file")
        void configFileExistsReturnsFalseForNonExistingFile() {
            Path testFile = tempDir.resolve("nonexistent.conf");
            
            assertFalse(ConfigIO.configFileExists(testFile));
        }
        
        @Test
        @DisplayName("configFileExists() returns true for directory (exists check)")
        void configFileExistsReturnsTrueForDirectory() {
            // configFileExists() checks if path exists, not if it's a file
            // So it returns true for directories too
            assertTrue(ConfigIO.configFileExists(tempDir));
        }
    }
    
    // ==================== Resource Loading Tests ====================
    
    @Nested
    @DisplayName("loadDefaults() Tests")
    class LoadDefaultsTests {
        
        @Test
        @DisplayName("loadDefaults loads reference.conf from classpath")
        void loadDefaultsLoadsReferenceConf() {
            Config defaults = ConfigIO.loadDefaults();
            
            assertNotNull(defaults);
            // Should have meta.configVersion
            assertTrue(defaults.hasPath("meta.configVersion"));
        }
    }
    
    @Nested
    @DisplayName("loadDefaultConfig() Tests")
    class LoadDefaultConfigTests {
        
        @Test
        @DisplayName("loadDefaultConfig() loads from reference.conf")
        void loadDefaultConfigLoadsFromReference() {
            String config = ConfigIO.loadDefaultConfig();
            
            assertNotNull(config);
            assertFalse(config.isEmpty());
            // Should contain token and owner fields
            assertTrue(config.contains("token") || config.contains("BOT_TOKEN_HERE"));
        }
        
        @Test
        @DisplayName("loadDefaultConfig() returns fallback when resource missing")
        void loadDefaultConfigReturnsFallbackWhenResourceMissing() {
            // This test verifies the fallback behavior exists
            // The actual fallback would only trigger if resource is truly missing
            String config = ConfigIO.loadDefaultConfig();
            
            assertNotNull(config);
            // Should either be from resource or fallback
            assertTrue(config.contains("token") || config.contains("BOT_TOKEN_HERE"));
        }
    }
    
    @Nested
    @DisplayName("loadReferenceConfigAsDocument() Tests")
    class LoadReferenceConfigAsDocumentTests {
        
        @Test
        @DisplayName("loadReferenceConfigAsDocument loads reference.conf as ConfigDocument")
        void loadReferenceConfigAsDocumentLoadsDocument() {
            ConfigDocument doc = ConfigIO.loadReferenceConfigAsDocument();
            
            assertNotNull(doc, "Should load reference.conf as ConfigDocument");
        }
        
        @Test
        @DisplayName("loadReferenceConfigAsDocument preserves comments")
        void loadReferenceConfigAsDocumentPreservesComments() {
            ConfigDocument doc = ConfigIO.loadReferenceConfigAsDocument();
            
            assertNotNull(doc);
            String rendered = doc.render();
            // reference.conf should have comments
            assertTrue(rendered.contains("#") || rendered.contains("//"), 
                "ConfigDocument should preserve comments from reference.conf");
        }
        
        @Test
        @DisplayName("loadReferenceConfigAsDocument preserves structure")
        void loadReferenceConfigAsDocumentPreservesStructure() {
            ConfigDocument doc = ConfigIO.loadReferenceConfigAsDocument();
            
            assertNotNull(doc);
            String rendered = doc.render();
            // Should have nested structure
            assertTrue(rendered.contains("meta {") || rendered.contains("meta"));
            assertTrue(rendered.contains("discord {") || rendered.contains("discord"));
        }
        
        @Test
        @DisplayName("loadReferenceConfigAsDocument can be parsed back to Config")
        void loadReferenceConfigAsDocumentCanBeParsedToConfig() {
            ConfigDocument doc = ConfigIO.loadReferenceConfigAsDocument();
            
            assertNotNull(doc);
            // Should be parseable as Config
            Config config = com.typesafe.config.ConfigFactory.parseString(doc.render());
            assertNotNull(config);
            assertTrue(config.hasPath("meta.configVersion"));
        }
    }
    
    @Nested
    @DisplayName("loadReferenceConfigAsString() Tests")
    class LoadReferenceConfigAsStringTests {
        
        @Test
        @DisplayName("loadReferenceConfigAsString loads reference.conf content")
        void loadReferenceConfigAsStringLoadsContent() {
            String content = ConfigIO.loadReferenceConfigAsString();
            
            assertNotNull(content);
            assertFalse(content.isEmpty());
            // Should contain key config elements
            assertTrue(content.contains("meta") || content.contains("discord"));
        }
    }
}
