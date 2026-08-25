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
package com.jagrosh.jmusicbot.testutil.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builder for creating config files in different formats.
 * 
 * <p>This builder helps create temporary config files for integration tests.
 * It can work with both legacy and v1 config formats.
 * 
 * <p>Example usage:
 * <pre>{@code
 * Path configFile = ConfigFileBuilder.legacy()
 *     .withToken("test_token")
 *     .withOwner(123456789L)
 *     .buildFile(tempDir);
 * }</pre>
 */
public class ConfigFileBuilder {
    private final StringBuilder content = new StringBuilder();
    private boolean isLegacy = false;
    
    private ConfigFileBuilder(boolean isLegacy) {
        this.isLegacy = isLegacy;
        if (!isLegacy) {
            // Add meta section for v1
            content.append("meta {\n");
            content.append("  configVersion = 1\n");
            content.append("}\n");
        }
    }
    
    /**
     * Creates a builder for legacy format config files.
     */
    public static ConfigFileBuilder legacy() {
        return new ConfigFileBuilder(true);
    }
    
    /**
     * Creates a builder for v1 format config files.
     */
    public static ConfigFileBuilder v1() {
        return new ConfigFileBuilder(false);
    }
    
    /**
     * Creates a builder with custom content.
     */
    public static ConfigFileBuilder withContent(String content) {
        ConfigFileBuilder builder = new ConfigFileBuilder(false);
        builder.content.setLength(0);
        builder.content.append(content);
        return builder;
    }
    
    /**
     * Adds a simple key-value pair (for legacy format).
     */
    public ConfigFileBuilder addKeyValue(String key, Object value) {
        if (value instanceof String) {
            content.append(key).append(" = \"").append(value).append("\"\n");
        } else if (value instanceof Boolean || value instanceof Number) {
            content.append(key).append(" = ").append(value).append("\n");
        } else {
            content.append(key).append(" = ").append(value).append("\n");
        }
        return this;
    }
    
    /**
     * Adds a nested key-value pair (for v1 format).
     */
    public ConfigFileBuilder addNestedKeyValue(String path, Object value) {
        String[] parts = path.split("\\.");
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            content.append(indent).append(parts[i]).append(" {\n");
            indent.append("  ");
        }
        String lastKey = parts[parts.length - 1];
        if (value instanceof String) {
            content.append(indent).append(lastKey).append(" = \"").append(value).append("\"\n");
        } else if (value instanceof Boolean || value instanceof Number) {
            content.append(indent).append(lastKey).append(" = ").append(value).append("\n");
        } else {
            content.append(indent).append(lastKey).append(" = ").append(value).append("\n");
        }
        for (int i = 0; i < parts.length - 1; i++) {
            indent.setLength(indent.length() - 2);
            content.append(indent).append("}\n");
        }
        return this;
    }
    
    /**
     * Adds raw content to the config file.
     */
    public ConfigFileBuilder addRaw(String rawContent) {
        content.append(rawContent);
        return this;
    }
    
    /**
     * Builds the config file content as a string.
     */
    public String buildAsString() {
        return content.toString();
    }
    
    /**
     * Builds a temporary config file in the given directory.
     * 
     * @param tempDir the temporary directory to create the file in
     * @return the path to the created config file
     * @throws IOException if file creation fails
     */
    public Path buildFile(Path tempDir) throws IOException {
        String fileName = "config-" + System.currentTimeMillis() + ".conf";
        Path configFile = tempDir.resolve(fileName);
        Files.writeString(configFile, content.toString());
        return configFile;
    }
    
    /**
     * Builds a temporary config file with a specific name.
     * 
     * @param tempDir the temporary directory to create the file in
     * @param fileName the name of the file
     * @return the path to the created config file
     * @throws IOException if file creation fails
     */
    public Path buildFile(Path tempDir, String fileName) throws IOException {
        Path configFile = tempDir.resolve(fileName);
        Files.writeString(configFile, content.toString());
        return configFile;
    }
}
