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
package com.jagrosh.jmusicbot.config.migration.versions;

import com.jagrosh.jmusicbot.config.migration.Migration;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

/**
 * Migration from version 1 to version 2.
 *
 * Renames nowPlaying.updateProgressBar to nowPlaying.showProgressBar.
 *
 * @author Arif Banai (arif-banai)
 */
public class V1ToV2 implements Migration {
    private static final String META_CONFIG_VERSION_KEY = "meta.configVersion";
    private static final String OLD_PROGRESS_BAR_KEY = "nowPlaying.updateProgressBar";
    private static final String NEW_PROGRESS_BAR_KEY = "nowPlaying.showProgressBar";

    @Override
    public int getFromVersion() {
        return 1;
    }

    @Override
    public int getToVersion() {
        return 2;
    }

    @Override
    public Config migrate(Config source) {
        Config migrated = source;

        // Preserve user intent when only the old key is present.
        if (source.hasPath(OLD_PROGRESS_BAR_KEY) && !source.hasPath(NEW_PROGRESS_BAR_KEY)) {
            migrated = migrated.withValue(
                    NEW_PROGRESS_BAR_KEY,
                    ConfigValueFactory.fromAnyRef(source.getBoolean(OLD_PROGRESS_BAR_KEY))
            );
        }

        // Remove old key to complete rename.
        if (migrated.hasPath(OLD_PROGRESS_BAR_KEY)) {
            migrated = migrated.withoutPath(OLD_PROGRESS_BAR_KEY);
        }

        return migrated.withValue(META_CONFIG_VERSION_KEY, ConfigValueFactory.fromAnyRef(getToVersion()));
    }
}
