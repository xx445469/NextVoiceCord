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
package com.jagrosh.jmusicbot.config.loader;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.jagrosh.jmusicbot.config.migration.ConfigMigration;
import com.jagrosh.jmusicbot.config.migration.ConfigMigrationException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

/**
 * Handles loading and parsing configuration files with migration support.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);
    
    /**
     * Loads the raw user config file without merging with defaults.
     * This is used for version detection and migration.
     * 
     * @param configPath the path to the config file
     * @return the raw parsed config, or empty config if file doesn't exist
     */
    public static Config loadRawUserConfig(Path configPath) {
        if (!configPath.toFile().exists()) {
            return ConfigFactory.empty();
        }
        try {
            return ConfigFactory.parseFile(configPath.toFile());
        } catch (ConfigException.Parse e) {
            LOGGER.error("Failed to parse config file at {}: {}", configPath, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Loads the migrated user config (before merging with defaults).
     * This is useful for diagnostics to check what the user actually provided.
     * 
     * @param configPath the path to the config file
     * @return the migrated user config (without defaults merged)
     */
    public static Config loadMigratedUserConfig(Path configPath) {
        Config rawUserConfig = loadRawUserConfig(configPath);
        Config defaults = ConfigIO.loadDefaults();
        return migrateIfNeeded(rawUserConfig, defaults, false);
    }
    
    /**
     * Loads the migrated user config using already-parsed raw config and defaults.
     * This avoids redundant parsing when these are already available.
     * 
     * @param rawUserConfig the already-parsed raw user config
     * @param defaults the already-parsed defaults
     * @return the migrated user config (without defaults merged)
     */
    public static Config loadMigratedUserConfig(Config rawUserConfig, Config defaults) {
        return migrateIfNeeded(rawUserConfig, defaults, false);
    }
    
    /**
     * Loads the merged config (user config with defaults as fallback).
     * This method now handles version detection and migration automatically.
     * 
     * @param configPath the path to the config file
     * @return the merged config with migrations applied
     */
    public static Config loadMergedConfig(Path configPath) {
        Config rawUserConfig = loadRawUserConfig(configPath);
        Config defaults = ConfigIO.loadDefaults();
        Config migratedUserConfig = migrateIfNeeded(rawUserConfig, defaults, true);
        
        // Merge with defaults (migrated user config takes precedence)
        return migratedUserConfig.withFallback(defaults).resolve();
    }
    
    /**
     * Applies migrations to the raw config if needed.
     * 
     * @param rawUserConfig the raw user config before migration
     * @param defaults the default configuration containing the latest version
     * @param logVersionInfo whether to log version detection info
     * @return the migrated config, or the original if no migration needed or migration failed
     */
    static Config migrateIfNeeded(Config rawUserConfig, Config defaults, boolean logVersionInfo) {
        // Empty config means no file exists - this is a fresh install, not a v0 legacy config
        if (rawUserConfig.isEmpty()) {
            if (logVersionInfo) {
                LOGGER.info("No config file found, using defaults");
            }
            return rawUserConfig; // Skip migration, will use defaults
        }
        
        int userVersion = ConfigMigration.detectVersion(rawUserConfig);
        int latestVersion = ConfigMigration.getLatestVersion(defaults);
        
        if (logVersionInfo) {
            LOGGER.info("Config version detected: {}, latest version: {}", userVersion, latestVersion);
        }
        
        if (userVersion > latestVersion) {
            LOGGER.warn("Config version {} is newer than the latest known version {}. " +
                "This may indicate a corrupted or manually edited config.", userVersion, latestVersion);
            return rawUserConfig;
        }
        
        if (userVersion < latestVersion) {
            try {
                Config migrated = ConfigMigration.migrate(rawUserConfig, userVersion, latestVersion);
                if (logVersionInfo) {
                    LOGGER.info("Config migrated from version {} to version {}", userVersion, latestVersion);
                }
                return migrated;
            } catch (ConfigMigrationException e) {
                LOGGER.error("Config migration failed: {}", e.getMessage());
                throw e;  // Re-throw to let caller handle
            }
        }
        return rawUserConfig;
    }
    
    /**
     * Loads the merged config using an already-migrated user config.
     * This avoids re-running migration when the migrated config is already available.
     * 
     * @param migratedUserConfig the already-migrated user config
     * @return the merged config with defaults
     */
    public static Config loadMergedConfig(Config migratedUserConfig) {
        Config defaults = ConfigIO.loadDefaults();
        return mergeWithDefaults(migratedUserConfig, defaults);
    }
    
    /**
     * Merges a migrated user config with already-parsed defaults.
     * This is the most efficient method when both configs are already available.
     * 
     * @param migratedUserConfig the already-migrated user config
     * @param defaults the already-parsed defaults
     * @return the merged config
     */
    public static Config mergeWithDefaults(Config migratedUserConfig, Config defaults) {
        return migratedUserConfig.withFallback(defaults).resolve();
    }
}
