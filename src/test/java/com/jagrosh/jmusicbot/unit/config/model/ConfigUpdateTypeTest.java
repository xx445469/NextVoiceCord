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
package com.jagrosh.jmusicbot.unit.config.model;

import com.jagrosh.jmusicbot.config.model.ConfigUpdateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigUpdateType Unit Tests")
class ConfigUpdateTypeTest {
    
    @Nested
    @DisplayName("determine() method")
    class DetermineMethodTests {
        
        @Test
        @DisplayName("Returns MIGRATION when original version is less than latest version")
        void returnsMigrationWhenVersionIsLessThanLatest() {
            // Version 0 -> 1 should be MIGRATION (legacy config)
            ConfigUpdateType result = ConfigUpdateType.determine(0, 1, false);
            assertEquals(ConfigUpdateType.MIGRATION, result,
                "Should return MIGRATION when upgrading from version 0 to 1");
            
            // Version 0 -> 2 should also be MIGRATION
            result = ConfigUpdateType.determine(0, 2, false);
            assertEquals(ConfigUpdateType.MIGRATION, result,
                "Should return MIGRATION when upgrading from version 0 to 2");
            
            // Version 1 -> 2 should also be MIGRATION
            result = ConfigUpdateType.determine(1, 2, false);
            assertEquals(ConfigUpdateType.MIGRATION, result,
                "Should return MIGRATION when upgrading from version 1 to 2");
        }
        
        @Test
        @DisplayName("Returns MIGRATION even when missing keys exist if version upgrade needed")
        void returnsMigrationEvenWithMissingKeysWhenVersionUpgradeNeeded() {
            // Version upgrade takes precedence over missing keys
            ConfigUpdateType result = ConfigUpdateType.determine(0, 1, true);
            assertEquals(ConfigUpdateType.MIGRATION, result,
                "Should return MIGRATION when version upgrade needed, even with missing keys");
        }
        
        @Test
        @DisplayName("Returns REPAIR when version is current but missing keys exist")
        void returnsRepairWhenCurrentVersionWithMissingKeys() {
            // Version 1 -> 1 with missing keys should be REPAIR
            ConfigUpdateType result = ConfigUpdateType.determine(1, 1, true);
            assertEquals(ConfigUpdateType.REPAIR, result,
                "Should return REPAIR when at current version but missing keys");
            
            // Version 2 -> 2 with missing keys should also be REPAIR
            result = ConfigUpdateType.determine(2, 2, true);
            assertEquals(ConfigUpdateType.REPAIR, result,
                "Should return REPAIR when at current version but missing keys");
        }
        
        @Test
        @DisplayName("Returns UPDATE when version is current and no missing keys")
        void returnsUpdateWhenCurrentVersionNoMissingKeys() {
            // Version 1 -> 1 with no missing keys should be UPDATE
            ConfigUpdateType result = ConfigUpdateType.determine(1, 1, false);
            assertEquals(ConfigUpdateType.UPDATE, result,
                "Should return UPDATE when at current version with no missing keys");
        }
    }
    
    @Nested
    @DisplayName("getPastTenseVerb() method")
    class GetPastTenseVerbTests {
        
        @Test
        @DisplayName("MIGRATION returns 'migrated'")
        void migrationReturnsMigrated() {
            assertEquals("migrated", ConfigUpdateType.MIGRATION.getPastTenseVerb(),
                "MIGRATION should return 'migrated'");
        }
        
        @Test
        @DisplayName("REPAIR returns 'repaired'")
        void repairReturnsRepaired() {
            assertEquals("repaired", ConfigUpdateType.REPAIR.getPastTenseVerb(),
                "REPAIR should return 'repaired'");
        }
        
        @Test
        @DisplayName("UPDATE returns 'updated'")
        void updateReturnsUpdated() {
            assertEquals("updated", ConfigUpdateType.UPDATE.getPastTenseVerb(),
                "UPDATE should return 'updated'");
        }
    }
}
