/*
 * Copyright 2022 John Grosh <john.a.grosh@gmail.com>.
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

import com.jagrosh.jmusicbot.queue.*;
import com.jagrosh.jmusicbot.utils.EnumUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author Wolfgang Schwendtbauer
 */
public enum QueueType
{
    LINEAR("\u23E9", "Linear", LinearQueue::create),     // ⏩
    FAIR("\uD83D\uDD22", "Fair", FairQueue::create);     // 🔢

    private final String userFriendlyName;
    private final String emoji;
    private final QueueSupplier supplier;

    QueueType(final String emoji, final String userFriendlyName, QueueSupplier supplier)
    {
        this.userFriendlyName = userFriendlyName;
        this.emoji = emoji;
        this.supplier = supplier;
    }

    /**
     * Parses a QueueType from a string, returning the default value if parsing fails.
     * This handles invalid values gracefully (e.g., from manual editing or data corruption)
     * instead of throwing IllegalArgumentException.
     *
     * @param value the string value to parse
     * @param defaultValue the default value to return if parsing fails
     * @return the parsed QueueType, or defaultValue if parsing fails
     */
    public static QueueType valueOfOrDefault(String value, QueueType defaultValue)
    {
        return EnumUtil.valueOfOrDefault(QueueType.class, value, defaultValue);
    }

    public static List<String> getNames()
    {
        return Arrays.stream(QueueType.values())
                .map(type -> type.name().toLowerCase())
                .collect(Collectors.toList());
    }

    /**
     * Creates a new queue instance.
     * 
     * @param previous The previous queue to copy state from, or null for initial creation
     * @param maxHistorySize The maximum history size (only used when previous is null)
     * @param <T> The type of items in the queue
     * @return A new queue instance
     */
    public <T extends Queueable> AbstractQueue<T> createInstance(AbstractQueue<T> previous, int maxHistorySize)
    {
        return supplier.apply(previous, maxHistorySize);
    }

    public String getUserFriendlyName()
    {
        return userFriendlyName;
    }

    public String getEmoji()
    {
        return emoji;
    }
}
