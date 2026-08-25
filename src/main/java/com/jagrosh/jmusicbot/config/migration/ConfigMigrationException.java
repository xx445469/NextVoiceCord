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

/**
 * Exception thrown when a configuration migration fails.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigMigrationException extends RuntimeException {
    private final int fromVersion;
    private final int toVersion;
    
    public ConfigMigrationException(int fromVersion, int toVersion, String message) {
        super(String.format("Migration from version %d to %d failed: %s", fromVersion, toVersion, message));
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
    }
    
    public ConfigMigrationException(int fromVersion, int toVersion, String message, Throwable cause) {
        super(String.format("Migration from version %d to %d failed: %s", fromVersion, toVersion, message), cause);
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
    }
    
    public int getFromVersion() {
        return fromVersion;
    }
    
    public int getToVersion() {
        return toVersion;
    }
}
