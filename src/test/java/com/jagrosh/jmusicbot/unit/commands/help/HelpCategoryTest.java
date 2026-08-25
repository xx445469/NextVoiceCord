package com.jagrosh.jmusicbot.unit.commands.help;

import org.junit.jupiter.api.Test;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jmusicbot.commands.help.HelpCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelpCategoryTest
{
    @Test
    void keyFor_mapsKnownCategoriesByName()
    {
        assertEquals(HelpCategory.MUSIC, HelpCategory.keyFor(new FakeCommand("play", "h", new Command.Category("Music"))));
        assertEquals(HelpCategory.DJ, HelpCategory.keyFor(new FakeCommand("skip", "h", new Command.Category("DJ"))));
        assertEquals(HelpCategory.ADMIN, HelpCategory.keyFor(new FakeCommand("prefix", "h", new Command.Category("Admin"))));
        assertEquals(HelpCategory.OWNER, HelpCategory.keyFor(new FakeCommand("shutdown", "h", new Command.Category("Owner"))));
    }

    @Test
    void keyFor_noCategoryFallsBackToGeneral()
    {
        assertEquals(HelpCategory.GENERAL, HelpCategory.keyFor(new FakeCommand("ping", "h", null)));
    }

    @Test
    void keyFor_unrecognisedCategoryNameFallsBackToGeneral()
    {
        // Defensive: nothing in this codebase creates a category outside the four jda-utilities
        // ones, but a future one should not silently vanish from the menu.
        assertEquals(HelpCategory.GENERAL, HelpCategory.keyFor(new FakeCommand("x", "h", new Command.Category("Mystery"))));
    }

    @Test
    void all_containsNewsAndTutorialFirst()
    {
        assertEquals(HelpCategory.NEWS, HelpCategory.ALL.get(0));
        assertEquals(HelpCategory.TUTORIAL, HelpCategory.ALL.get(1));
        assertEquals(7, HelpCategory.ALL.size());
    }

    @Test
    void real_isEveryCommandBackedCategory()
    {
        assertEquals(5, HelpCategory.REAL.size());
        assertEquals(HelpCategory.ALL.subList(2, 7), HelpCategory.REAL);
    }
}
