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
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigException;
import com.typesafe.config.parser.ConfigDocument;
import com.typesafe.config.parser.ConfigDocumentFactory;

import java.util.Map;

/**
 * Abstract base class for config builders providing common build functionality.
 * 
 * <p>This class implements the shared logic for building configs, including:
 * <ul>
 *   <li>Building ConfigDocument from map representation</li>
 *   <li>Building Config as fallback</li>
 *   <li>Building string representation</li>
 * </ul>
 * 
 * <p>Subclasses must implement {@link #getConfigMap()} to provide their
 * internal configuration map.
 */
public abstract class AbstractConfigBuilder implements ConfigBuilder {
    
    /**
     * Returns the internal configuration map used for building.
     * 
     * @return the configuration map
     */
    protected abstract Map<String, Object> getConfigMap();
    
    /**
     * Builds the ConfigDocument from the configured values.
     * This is the primary method, matching how application code works.
     * 
     * @return the built ConfigDocument
     */
    @Override
    public ConfigDocument buildDocument() {
        try {
            // First try to build as ConfigDocument from the string representation
            String hoconString = buildAsStringFromConfig();
            return ConfigDocumentFactory.parseString(hoconString);
        } catch (ConfigException e) {
            // If ConfigDocument parsing fails, we'll fall back to Config in build()
            // This matches the application pattern where ConfigDocument is preferred
            // but Config is used as fallback for error cases
            throw new IllegalStateException("Failed to build ConfigDocument, use build() as fallback", e);
        }
    }
    
    /**
     * Builds the Config object from the configured values.
     * This is a fallback method for cases where ConfigDocument parsing fails
     * or when Config is needed for migration/validation.
     * 
     * @return the built Config object
     */
    @Override
    public Config build() {
        return ConfigFactory.parseMap(getConfigMap());
    }
    
    /**
     * Builds the config as a HOCON string.
     * Uses ConfigDocument when possible to preserve formatting.
     * 
     * @return the config as a HOCON-formatted string
     */
    @Override
    public String buildAsString() {
        try {
            return buildDocument().render();
        } catch (Exception e) {
            // Fallback to Config rendering if ConfigDocument fails
            return buildAsStringFromConfig();
        }
    }
    
    /**
     * Internal helper to build string from Config (fallback method).
     */
    protected String buildAsStringFromConfig() {
        return build().root().render();
    }
}
