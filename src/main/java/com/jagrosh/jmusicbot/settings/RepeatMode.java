/*
 * Copyright 2020 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.settings;

import com.jagrosh.jmusicbot.utils.EnumUtil;

/**
 *
 * @author Michaili K
 */
public enum RepeatMode
{
    OFF(null, "Off"),
    ALL("\uD83D\uDD01", "All"), // 🔁
    SINGLE("\uD83D\uDD02", "Single"); // 🔂

    private final String emoji;
    private final String userFriendlyName;

    RepeatMode(String emoji, String userFriendlyName)
    {
        this.emoji = emoji;
        this.userFriendlyName = userFriendlyName;
    }

    /**
     * Parses a RepeatMode from a string, returning the default value if parsing fails.
     * This handles invalid values gracefully (e.g., from manual editing or data corruption)
     * instead of throwing IllegalArgumentException.
     *
     * @param value the string value to parse
     * @param defaultValue the default value to return if parsing fails
     * @return the parsed RepeatMode, or defaultValue if parsing fails
     */
    public static RepeatMode valueOfOrDefault(String value, RepeatMode defaultValue)
    {
        return EnumUtil.valueOfOrDefault(RepeatMode.class, value, defaultValue);
    }

    public String getEmoji()
    {
        return emoji;
    }

    public String getUserFriendlyName()
    {
        return userFriendlyName;
    }
}
