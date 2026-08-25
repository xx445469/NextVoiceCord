/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for enum operations.
 */
public final class EnumUtil
{
    private static final Logger LOG = LoggerFactory.getLogger(EnumUtil.class);

    private EnumUtil()
    {
        // Prevent instantiation
    }

    /**
     * Parses an enum value from a string, returning the default value if parsing fails.
     * This handles invalid values gracefully (e.g., from manual editing or data corruption)
     * instead of throwing IllegalArgumentException.
     *
     * @param enumClass the enum class to parse into
     * @param value the string value to parse
     * @param defaultValue the default value to return if parsing fails
     * @param <T> the enum type
     * @return the parsed enum value, or defaultValue if parsing fails
     */
    public static <T extends Enum<T>> T valueOfOrDefault(Class<T> enumClass, String value, T defaultValue)
    {
        if (value == null || value.isEmpty())
        {
            return defaultValue;
        }
        try
        {
            return Enum.valueOf(enumClass, value);
        }
        catch (IllegalArgumentException e)
        {
            LOG.warn("Invalid {} value '{}', using default: {}", enumClass.getSimpleName(), value, defaultValue);
            return defaultValue;
        }
    }
}
