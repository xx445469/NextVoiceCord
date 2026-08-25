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
package com.jagrosh.jmusicbot.config.migration;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.config.migration.versions.LegacyToV1;
import com.jagrosh.jmusicbot.config.migration.versions.V1ToV2;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

/**
 * Core migration engine that applies versioned migrations sequentially.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMigration.class);
    private static final String META_CONFIG_VERSION_KEY = "meta.configVersion";
    
    private static final Map<Integer, Migration> MIGRATIONS = new HashMap<>();
    
    static {
        // Register all available migrations
        MIGRATIONS.put(0, new LegacyToV1());
        MIGRATIONS.put(1, new V1ToV2());
        // Future migrations can be added here.
    }
    
    /**
     * Detects the version of a configuration.
     * Configs without meta.configVersion are assumed to be version 0 (legacy).
     * 
     * @param config the configuration to check
     * @return the version number (0 if missing)
     */
    public static int detectVersion(Config config) {
        try {
            if (config.hasPath(META_CONFIG_VERSION_KEY)) {
                return config.getInt(META_CONFIG_VERSION_KEY);
            }
        } catch (ConfigException e) {
            LOGGER.warn("Invalid configVersion in config, assuming version 0: {}", e.getMessage());
        }
        return 0; // Legacy configs have no version field
    }
    
    /**
     * Gets the latest config version from the defaults.
     * 
     * @param defaults the default configuration
     * @return the latest version number (2 if missing)
     */
    public static int getLatestVersion(Config defaults) {
        try {
            if (defaults.hasPath(META_CONFIG_VERSION_KEY)) {
                return defaults.getInt(META_CONFIG_VERSION_KEY);
            }
        } catch (ConfigException e) {
            LOGGER.warn("Invalid configVersion in defaults, assuming version 2: {}", e.getMessage());
        }
        return 2; // Default to version 2 if not specified
    }
    
    /**
     * Migrates a configuration from one version to another.
     * Applies migrations sequentially (e.g., 0→1, then 1→2).
     * 
     * @param rawUserConfig the raw user configuration to migrate
     * @param fromVersion the source version
     * @param toVersion the target version
     * @return the migrated configuration
     * @throws ConfigMigrationException if migration fails
     */
    public static Config migrate(Config rawUserConfig, int fromVersion, int toVersion) {
        if (fromVersion >= toVersion) {
            // No migration needed
            return rawUserConfig;
        }
        
        LOGGER.info("Migrating config from version {} → {}", fromVersion, toVersion);
        
        Config current = rawUserConfig;
        
        // Apply migrations sequentially
        for (int version = fromVersion; version < toVersion; version++) {
            Migration migration = MIGRATIONS.get(version);
            if (migration == null) {
                throw new ConfigMigrationException(version, version + 1, 
                    "No migration found for version " + version);
            }
            
            if (migration.getToVersion() != version + 1) {
                throw new ConfigMigrationException(version, version + 1,
                    "Migration from version " + version + " does not target version " + (version + 1));
            }
            
            try {
                LOGGER.debug("Applying migration {} → {}", migration.getFromVersion(), migration.getToVersion());
                current = migration.migrate(current);
            } catch (Exception e) {
                throw new ConfigMigrationException(version, version + 1,
                    "Migration step failed", e);
            }
        }
        
        LOGGER.info("Config migration from version {} → {} completed successfully", fromVersion, toVersion);
        return current;
    }
}
