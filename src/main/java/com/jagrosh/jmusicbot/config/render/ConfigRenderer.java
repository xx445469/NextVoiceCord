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
package com.jagrosh.jmusicbot.config.render;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.jagrosh.jmusicbot.config.model.ConfigOption;
import com.jagrosh.jmusicbot.config.model.ConfigUpdateType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.parser.ConfigDocument;

/**
 * Handles rendering of configuration objects to HOCON strings.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigRenderer.class);
    
    /** Render options for inline HOCON values (no comments, no formatting). */
    private static final ConfigRenderOptions INLINE_VALUE_OPTIONS = ConfigRenderOptions.defaults()
            .setOriginComments(false)
            .setComments(false)
            .setFormatted(false)
            .setJson(false);
    
    /**
     * Generates the HOCON content for the updated config file.
     * Uses ConfigDocument to preserve the style, comments, and ordering from reference.conf.
     * Uses the system clock for timestamp generation.
     * 
     * @param migratedUserConfig the migrated user configuration (without defaults merged)
     * @param diagnostics the diagnostic report
     * @param updateType the type of update (migration, repair, or update)
     * @return the HOCON content as a string
     */
    public static String generateConfigContent(Config migratedUserConfig, ConfigDiagnostics.Report diagnostics,
                                               ConfigUpdateType updateType) {
        return generateConfigContent(migratedUserConfig, diagnostics, updateType, Clock.systemDefaultZone());
    }
    
    /**
     * Generates the HOCON content for the updated config file.
     * Uses ConfigDocument to preserve the style, comments, and ordering from reference.conf.
     * 
     * @param migratedUserConfig the migrated user configuration (without defaults merged)
     * @param diagnostics the diagnostic report
     * @param updateType the type of update (migration, repair, or update)
     * @param clock the clock to use for timestamp generation
     * @return the HOCON content as a string
     */
    public static String generateConfigContent(Config migratedUserConfig, ConfigDiagnostics.Report diagnostics,
                                               ConfigUpdateType updateType, Clock clock) {
        // Load reference.conf as ConfigDocument (template)
        ConfigDocument templateDoc = ConfigIO.loadReferenceConfigAsDocument();
        
        if (templateDoc == null) {
            // Fallback to old behavior if template cannot be loaded
            LOGGER.warn("Could not load reference.conf as ConfigDocument, falling back to rendered config");
            return generateConfigContentFallback(migratedUserConfig, diagnostics, updateType, clock);
        }
        
        // Start with the template document
        ConfigDocument outputDoc = templateDoc;
        
        // Load defaults to check if template has empty nested configs
        Config defaults = ConfigIO.loadDefaults();
        
        // For each ConfigOption, if migratedUserConfig has a value, update the document
        for (ConfigOption option : ConfigOption.values()) {
            String key = option.getKey();
            
            // Check if user config has this key
            if (migratedUserConfig.hasPath(key)) {
                try {
                    // Handle nested configs (CONFIG type) specially
                    if (option.getType() == ConfigOption.ConfigType.CONFIG) {
                        Config nestedConfig = option.getConfig(migratedUserConfig);
                        
                        // Check if the template has an empty nested config for this key
                        // (e.g., transforms = {}). If so, user keys are dynamic and we need
                        // to replace the entire value rather than updating individual keys.
                        boolean templateIsEmpty = false;
                        if (defaults.hasPath(key)) {
                            try {
                                Config templateNested = defaults.getConfig(key);
                                templateIsEmpty = templateNested.isEmpty();
                            } catch (ConfigException e) {
                                // Not a config object, treat as non-empty
                            }
                        }
                        
                        if (templateIsEmpty && !nestedConfig.isEmpty()) {
                            // Template is empty but user has values (like transforms.spotify)
                            // ConfigDocument.withValueText for nested paths doesn't properly
                            // add new keys to an empty object, so we replace the entire value.
                            // Build a HOCON object string with all user's nested values
                            StringBuilder sb = new StringBuilder("{ ");
                            boolean first = true;
                            for (String nestedKey : nestedConfig.root().keySet()) {
                                if (!first) {
                                    sb.append(", ");
                                }
                                first = false;
                                ConfigValue nestedValue = nestedConfig.getValue(nestedKey);
                                String renderedValue = renderValue(nestedValue);
                                sb.append(nestedKey).append(" = ").append(renderedValue);
                            }
                            sb.append(" }");
                            LOGGER.debug("Replacing empty config '{}' with: {}", key, sb.toString());
                            outputDoc = outputDoc.withValueText(key, sb.toString());
                        } else if (!nestedConfig.isEmpty()) {
                            // Template has keys, update individual keys to preserve template defaults
                            // This preserves keys from the template that aren't in the user config
                            for (String nestedKey : nestedConfig.root().keySet()) {
                                String fullPath = key + "." + nestedKey;
                                try {
                                    ConfigValue nestedValue = nestedConfig.getValue(nestedKey);
                                    String renderedValue = renderValue(nestedValue);
                                    outputDoc = outputDoc.withValueText(fullPath, renderedValue);
                                } catch (ConfigException e) {
                                    LOGGER.debug("Could not get nested value for key {}: {}", fullPath, e.getMessage());
                                }
                            }
                        }
                    } else {
                        // For simple values, get the ConfigValue and render it
                        ConfigValue value = migratedUserConfig.getValue(key);
                        String renderedValue = renderValue(value);
                        // Update the document with the user's value
                        outputDoc = outputDoc.withValueText(key, renderedValue);
                    }
                } catch (ConfigException e) {
                    // If we can't get the value, skip it (will use template default)
                    LOGGER.debug("Could not get value for key {}: {}", key, e.getMessage());
                }
            }
            // If user config doesn't have this key, the template default remains
        }
        
        // Build the output with header comments
        StringBuilder sb = buildHeaderComment(diagnostics, updateType, clock);
        
        // Render the document (preserves comments and formatting)
        sb.append(outputDoc.render());
        
        return sb.toString();
    }
    
    /**
     * Fallback method that uses the old rendering approach if ConfigDocument cannot be loaded.
     * Merges migratedUserConfig with defaults to match old behavior.
     * Uses the system clock for timestamp generation.
     * 
     * @param migratedUserConfig the migrated user configuration (without defaults merged)
     * @param diagnostics the diagnostic report
     * @param updateType the type of update (migration, repair, or update)
     * @return the HOCON content as a string
     */
    public static String generateConfigContentFallback(Config migratedUserConfig, ConfigDiagnostics.Report diagnostics,
                                                       ConfigUpdateType updateType) {
        return generateConfigContentFallback(migratedUserConfig, diagnostics, updateType, Clock.systemDefaultZone());
    }
    
    /**
     * Fallback method that uses the old rendering approach if ConfigDocument cannot be loaded.
     * Merges migratedUserConfig with defaults to match old behavior.
     * 
     * @param migratedUserConfig the migrated user configuration (without defaults merged)
     * @param diagnostics the diagnostic report
     * @param updateType the type of update (migration, repair, or update)
     * @param clock the clock to use for timestamp generation
     * @return the HOCON content as a string
     */
    public static String generateConfigContentFallback(Config migratedUserConfig, ConfigDiagnostics.Report diagnostics,
                                                       ConfigUpdateType updateType, Clock clock) {
        // Merge with defaults to match old behavior
        Config defaults = ConfigIO.loadDefaults();
        Config config = migratedUserConfig.withFallback(defaults).resolve();
        
        // Build the output with header comments
        StringBuilder sb = buildHeaderComment(diagnostics, updateType, clock);
        
        // Render the config using old method
        com.typesafe.config.ConfigRenderOptions options = com.typesafe.config.ConfigRenderOptions.defaults()
                .setOriginComments(false)
                .setComments(true)
                .setFormatted(true)
                .setJson(false);
        
        sb.append(config.root().render(options));
        
        return sb.toString();
    }
    
    /**
     * Builds the header comment section for the config file.
     * 
     * @param diagnostics the diagnostic report
     * @param updateType the type of update (migration, repair, or update)
     * @param clock the clock to use for timestamp generation
     * @return a StringBuilder containing the header comment
     */
    private static StringBuilder buildHeaderComment(ConfigDiagnostics.Report diagnostics, ConfigUpdateType updateType, Clock clock) {
        StringBuilder sb = new StringBuilder();
        
        String timestamp = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        sb.append("# This file was automatically ").append(updateType.getPastTenseVerb()).append(" on ").append(timestamp).append("\n");
        sb.append("# Your original config file has been backed up with a .bak extension (or .bak1, .bak2, etc. if previous backups exist).\n");
        sb.append("#\n");
        
        if (diagnostics.hasIssues()) {
            sb.append("# Changes detected:\n");
            if (!diagnostics.getMissingRequired().isEmpty()) {
                sb.append("# - Missing required keys: ").append(diagnostics.getMissingRequired()).append("\n");
            }
            if (!diagnostics.getMissingOptional().isEmpty()) {
                sb.append("# - Missing optional keys (new options): ").append(diagnostics.getMissingOptional()).append("\n");
            }
            if (!diagnostics.getDeprecated().isEmpty()) {
                sb.append("# - Deprecated keys removed: ").append(diagnostics.getDeprecated()).append("\n");
            }
            sb.append("#\n");
        }
        
        return sb;
    }
    
    /**
     * Renders a ConfigValue as a HOCON string suitable for inline use.
     * This preserves HOCON syntax (not JSON) and is appropriate for setting values in a ConfigDocument.
     * 
     * @param value the ConfigValue to render
     * @return the HOCON string representation
     */
    private static String renderValue(ConfigValue value) {
        if (value == null) {
            return "null";
        }
        return value.render(INLINE_VALUE_OPTIONS);
    }
}
