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
package com.jagrosh.jmusicbot.config.model;

/**
 * Represents the type of config update that occurred.
 * Used to provide appropriate messaging in logs and config file comments.
 * 
 * @author Arif Banai (arif-banai)
 */
public enum ConfigUpdateType {
    /**
     * Migration from legacy (version 0) config to a newer version.
     * This involves restructuring the config format.
     */
    MIGRATION("migrated"),
    
    /**
     * Repair of a current-version config that has missing keys.
     * The config format is correct but some keys need to be added.
     */
    REPAIR("repaired"),
    
    /**
     * Simple update with no migration or repair needed.
     * Only deprecated keys were removed or minor changes made.
     */
    UPDATE("updated");
    
    private final String pastTenseVerb;
    
    ConfigUpdateType(String pastTenseVerb) {
        this.pastTenseVerb = pastTenseVerb;
    }
    
    /**
     * Returns the past tense verb for this update type (e.g., "migrated", "repaired", "updated").
     */
    public String getPastTenseVerb() {
        return pastTenseVerb;
    }
    
    /**
     * Determines the appropriate update type based on the original config version and diagnostics.
     * 
     * @param originalVersion the version of the config before any updates
     * @param latestVersion the latest config version
     * @param hasMissingKeys whether the config has missing required or optional keys
     * @return the appropriate ConfigUpdateType
     */
    public static ConfigUpdateType determine(int originalVersion, int latestVersion, boolean hasMissingKeys) {
        if (originalVersion < latestVersion) {
            // Legacy config needs migration (version 0 -> 1+)
            return MIGRATION;
        } else if (hasMissingKeys) {
            // Current version but missing keys - needs repair
            return REPAIR;
        } else {
            // Only deprecated keys or other minor updates
            return UPDATE;
        }
    }
}
