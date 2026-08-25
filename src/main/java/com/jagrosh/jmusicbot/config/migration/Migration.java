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

import com.typesafe.config.Config;

/**
 * Interface for versioned configuration migrations.
 * Each migration transforms a config from one version to the next.
 * 
 * @author Arif Banai (arif-banai)
 */
public interface Migration {
    /**
     * Gets the source version for this migration.
     * 
     * @return the version number this migration migrates from
     */
    int getFromVersion();
    
    /**
     * Gets the target version for this migration.
     * 
     * @return the version number this migration migrates to
     */
    int getToVersion();
    
    /**
     * Migrates a configuration from the source version to the target version.
     * 
     * @param source the configuration to migrate
     * @return the migrated configuration
     * @throws ConfigMigrationException if the migration fails
     */
    Config migrate(Config source);
}
