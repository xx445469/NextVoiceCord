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
package com.jagrosh.jmusicbot.unit.listener.interaction;

import com.jagrosh.jmusicbot.listener.interaction.PaginatedListComponents;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaginatedListComponentsTest
{
    @Test
    void buildSelectRows_pageTwoUsesAbsoluteLabelsAndActions()
    {
        String baseId = PaginatedListComponents.baseId("queue", 2, 0, 42L);
        List<ActionRow> rows = PaginatedListComponents.buildSelectRows(baseId, 2, 10, 10, 0);
        assertEquals(2, rows.size());

        Button first = (Button) rows.get(0).getComponents().get(0);
        Button last = (Button) rows.get(1).getComponents().get(4);
        assertEquals("11", first.getLabel());
        assertEquals("20", last.getLabel());
    }

    @Test
    void buildSelectRows_partialPageUsesSparseRows()
    {
        String baseId = PaginatedListComponents.baseId("history", 1, 0, 42L);
        List<ActionRow> rows = PaginatedListComponents.buildSelectRows(baseId, 1, 10, 3, 0);
        assertEquals(1, rows.size());
        assertEquals(3, rows.get(0).getComponents().size());
    }

    @Test
    void buildPaginationRow_singlePageWithoutExtras_returnsNull()
    {
        String baseId = PaginatedListComponents.baseId("history", 1, 0, 42L);
        assertNull(PaginatedListComponents.buildPaginationRow(baseId, 1, 1));
    }

    @Test
    void buildPaginationRow_singlePageWithExtras_returnsExtrasOnly()
    {
        String baseId = PaginatedListComponents.baseId("queue", 1, 0, 42L);
        Button shuffle = Button.secondary(String.format(baseId, "shuffle"), "Shuffle");
        ActionRow row = PaginatedListComponents.buildPaginationRow(baseId, 1, 1, shuffle);
        assertEquals(1, row.getComponents().size());
        assertEquals("Shuffle", ((Button) row.getComponents().get(0)).getLabel());
    }
}
