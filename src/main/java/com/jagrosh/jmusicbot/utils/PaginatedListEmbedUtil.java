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
package com.jagrosh.jmusicbot.utils;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

/**
 * Shared utilities for building paginated list embeds (Queue, History, Playlists slash commands).
 */
public final class PaginatedListEmbedUtil
{
    private PaginatedListEmbedUtil()
    {
    }

    /**
     * Builds the "section header + numbered bullet lines" string used by Queue, History, and Playlists embeds.
     *
     * @param sectionHeader  e.g. "**Up Next** *(select a track below)*"
     * @param lineContents   pre-formatted line strings for the current page (e.g. track strings, or "`name`", or "[**label**](url)")
     * @param selectedIndex 1-based index of the selected line, or 0 if none
     * @param firstDisplayNum 1-based number of the first line (for pagination, e.g. page 2 starts at 11)
     * @return sectionHeader + "\n\n" + numbered lines; when selectedIndex &gt; 0 the matching line uses ▶️ and bold
     */
    public static String buildNumberedListSection(String sectionHeader, List<String> lineContents,
                                                  int selectedIndex, int firstDisplayNum)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(sectionHeader).append("\n\n");
        if (lineContents != null)
        {
            for (int i = 0; i < lineContents.size(); i++)
            {
                int displayNum = firstDisplayNum + i;
                String content = lineContents.get(i);
                if (selectedIndex > 0 && displayNum == selectedIndex)
                {
                    sb.append("▶️ **`").append(displayNum).append(".`** ").append(content).append("\n");
                }
                else
                {
                    sb.append("⬛ `").append(displayNum).append(".` ").append(content).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Applies standard embed options (timestamp, color, optional footer) used by all list embeds.
     */
    public static EmbedBuilder applyStandardEmbedOptions(EmbedBuilder embed, String footer, Color memberColor)
    {
        embed.setTimestamp(Instant.now()).setColor(memberColor);
        if (footer != null)
        {
            embed.setFooter(footer);
        }
        return embed;
    }
}
