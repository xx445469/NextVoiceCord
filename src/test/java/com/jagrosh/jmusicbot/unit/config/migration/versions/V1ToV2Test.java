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
package com.jagrosh.jmusicbot.unit.config.migration.versions;

import com.jagrosh.jmusicbot.config.migration.versions.V1ToV2;
import com.jagrosh.jmusicbot.testutil.config.V1ConfigBuilder;
import com.typesafe.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MigrationV1ToV2 Unit Tests")
class V1ToV2Test {
    private final V1ToV2 migration = new V1ToV2();

    @Test
    @DisplayName("migrate renames updateProgressBar to showProgressBar and bumps version")
    void migrate_renamesProgressBarKey_andBumpsVersion() {
        Config source = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withCustom("nowPlaying", "updateProgressBar", true)
                .build();

        Config migrated = migration.migrate(source);

        assertEquals(2, migrated.getInt("meta.configVersion"));
        assertFalse(migrated.hasPath("nowPlaying.updateProgressBar"));
        assertTrue(migrated.hasPath("nowPlaying.showProgressBar"));
        assertTrue(migrated.getBoolean("nowPlaying.showProgressBar"));
    }
}
