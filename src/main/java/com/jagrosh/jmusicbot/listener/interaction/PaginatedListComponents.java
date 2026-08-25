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
package com.jagrosh.jmusicbot.listener.interaction;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shared builders for paginated list interaction components (selection rows + pagination row).
 */
public final class PaginatedListComponents
{
    private PaginatedListComponents()
    {
    }

    /**
     * Base ID format: {prefix}_{action}_{page}_{selectedIndex}_{userId}
     */
    public static String baseId(String prefix, int page, int selectedIndex, long userId)
    {
        return prefix + "_%s_" + page + "_" + selectedIndex + "_" + userId;
    }

    /**
     * Builds sparse selection rows for the current page.
     * Button labels and select actions use absolute indices to match embed numbering.
     */
    public static List<ActionRow> buildSelectRows(String baseId, int page, int pageSize, int itemsOnPage, int selectedIndex)
    {
        List<ActionRow> rows = new ArrayList<>();
        if (itemsOnPage <= 0)
        {
            return rows;
        }

        int startAbsoluteIndex = (page - 1) * pageSize + 1;
        List<Button> firstRow = new ArrayList<>();
        List<Button> secondRow = new ArrayList<>();
        for (int offset = 0; offset < itemsOnPage; offset++)
        {
            int absoluteIndex = startAbsoluteIndex + offset;
            String action = "select" + absoluteIndex;
            Button btn = Button.secondary(String.format(baseId, action), String.valueOf(absoluteIndex));
            if (absoluteIndex == selectedIndex)
            {
                btn = Button.primary(String.format(baseId, action), String.valueOf(absoluteIndex));
            }

            if (offset < 5)
            {
                firstRow.add(btn);
            }
            else
            {
                secondRow.add(btn);
            }
        }

        if (!firstRow.isEmpty())
        {
            rows.add(ActionRow.of(firstRow));
        }
        if (!secondRow.isEmpty())
        {
            rows.add(ActionRow.of(secondRow));
        }
        return rows;
    }

    /**
     * Builds pagination row with optional extra buttons (e.g. shuffle).
     */
    public static ActionRow buildPaginationRow(String baseId, int page, int totalPages, Button... extraButtons)
    {
        if (totalPages <= 1)
        {
            if (extraButtons.length == 0)
            {
                return null;
            }
            return ActionRow.of(Arrays.asList(extraButtons));
        }

        Button prevBtn = Button.secondary(String.format(baseId, "prev"), Emoji.fromUnicode("⬅️"));
        Button nextBtn = Button.secondary(String.format(baseId, "next"), Emoji.fromUnicode("➡️"));
        if (page <= 1)
        {
            prevBtn = prevBtn.asDisabled();
        }
        if (page >= totalPages)
        {
            nextBtn = nextBtn.asDisabled();
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(prevBtn);
        buttons.add(nextBtn);
        for (Button extraButton : extraButtons)
        {
            buttons.add(extraButton);
        }
        return ActionRow.of(buttons);
    }
}
