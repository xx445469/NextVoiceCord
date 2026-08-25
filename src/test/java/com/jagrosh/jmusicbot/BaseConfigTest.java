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
package com.jagrosh.jmusicbot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for config-related tests.
 * Provides common setup/teardown and utility methods.
 */
public abstract class BaseConfigTest {
    
    @TempDir
    protected Path tempDir;
    
    protected List<Path> tempFiles;
    protected MockUserInteraction mockUserInteraction;
    
    // System property backup for config.file and config
    private String originalConfigFile;
    private String originalConfig;
    
    @BeforeEach
    protected void setUpBase() {
        tempFiles = new ArrayList<>();
        mockUserInteraction = new MockUserInteraction();
        saveSystemProperties();
    }
    
    @AfterEach
    protected void tearDownBase() throws IOException {
        restoreSystemProperties();
        cleanupTempFiles();
    }
    
    /**
     * Saves the current system properties for restoration after test.
     */
    protected void saveSystemProperties() {
        originalConfigFile = System.getProperty("config.file");
        originalConfig = System.getProperty("config");
    }
    
    /**
     * Restores system properties to their original values.
     */
    protected void restoreSystemProperties() {
        if (originalConfigFile != null) {
            System.setProperty("config.file", originalConfigFile);
        } else {
            System.clearProperty("config.file");
        }
        if (originalConfig != null) {
            System.setProperty("config", originalConfig);
        } else {
            System.clearProperty("config");
        }
    }
    
    /**
     * Cleans up temporary files created during the test.
     */
    private void cleanupTempFiles() {
        for (Path tempFile : tempFiles) {
            try {
                if (Files.exists(tempFile)) {
                    Files.delete(tempFile);
                }
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
        tempFiles.clear();
    }
    
    /**
     * Sets the config.file system property to point to the given file.
     */
    protected void setConfigFileProperty(Path configFile) {
        System.setProperty("config.file", configFile.toString());
    }
    
    /**
     * Creates a temporary config file in the test's temp directory.
     */
    protected Path createTempConfigFile(String content) throws IOException {
        Path configFile = tempDir.resolve("config-" + System.currentTimeMillis() + ".conf");
        Files.writeString(configFile, content, java.nio.charset.StandardCharsets.UTF_8);
        tempFiles.add(configFile);
        return configFile;
    }
    
    /**
     * Creates a temporary config file with minimal valid config.
     */
    protected Path createMinimalTempConfigFile() throws IOException {
        return createTempConfigFile("token = test_token_12345\nowner = 123456789");
    }
    
    /**
     * Reads the content of a file.
     */
    protected String readFileContent(Path file) throws IOException {
        return Files.readString(file);
    }
    
    /**
     * Writes content to a file.
     */
    protected void writeFileContent(Path file, String content) throws IOException {
        Files.writeString(file, content, java.nio.charset.StandardCharsets.UTF_8);
    }
    
    /**
     * Asserts that a file exists.
     */
    protected void assertFileExists(Path file) {
        org.junit.jupiter.api.Assertions.assertTrue(
            Files.exists(file),
            "Expected file to exist: " + file
        );
    }
    
    /**
     * Asserts that a file does not exist.
     */
    protected void assertFileNotExists(Path file) {
        org.junit.jupiter.api.Assertions.assertFalse(
            Files.exists(file),
            "Expected file to not exist: " + file
        );
    }
    
    /**
     * Asserts that file content contains the given string.
     */
    protected void assertFileContains(Path file, String expectedContent) throws IOException {
        String content = readFileContent(file);
        org.junit.jupiter.api.Assertions.assertTrue(
            content.contains(expectedContent),
            String.format("Expected file to contain '%s', but content was: %s", expectedContent, content)
        );
    }
}
