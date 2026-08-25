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

import com.typesafe.config.Config;
import com.typesafe.config.parser.ConfigDocument;

/**
 * Common interface for all config builders.
 * 
 * <p>This interface defines the contract that all config version builders must implement.
 * It allows for polymorphic usage of builders and ensures consistency across different
 * config versions (legacy/v0, v1, and future versions).
 * 
 * <p>Builders use ConfigDocument as the primary representation (matching application code),
 * and fall back to Config only when there's an error parsing that needs to be migrated/updated.
 * 
 * <p>Example usage:
 * <pre>{@code
 * ConfigBuilder builder = LegacyConfigBuilder.create();
 * ConfigDocument document = builder.buildDocument();
 * Config config = builder.build(); // Fallback for error cases
 * String configString = builder.buildAsString();
 * }</pre>
 */
public interface ConfigBuilder {
    
    /**
     * Builds the ConfigDocument from the configured values.
     * This is the primary method, matching how application code works.
     * 
     * @return the built ConfigDocument
     */
    ConfigDocument buildDocument();
    
    /**
     * Builds the Config object from the configured values.
     * This is a fallback method for cases where ConfigDocument parsing fails
     * or when Config is needed for migration/validation.
     * 
     * @return the built Config object
     */
    Config build();
    
    /**
     * Builds the config as a HOCON string representation.
     * Uses ConfigDocument when possible to preserve formatting.
     * 
     * @return the config as a HOCON-formatted string
     */
    String buildAsString();
}
