package com.jagrosh.jmusicbot.unit.commands.help;

import org.junit.jupiter.api.Test;

import com.jagrosh.jmusicbot.commands.help.HelpAccess;
import com.jagrosh.jmusicbot.commands.help.HelpCategory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpAccessTest
{
    @Test
    void generalAndMusic_alwaysUsable()
    {
        assertTrue(HelpAccess.canUse(HelpCategory.GENERAL, false, false, false));
        assertTrue(HelpAccess.canUse(HelpCategory.MUSIC, false, false, false));
    }

    @Test
    void dj_followsDjPermissionExactly()
    {
        assertTrue(HelpAccess.canUse(HelpCategory.DJ, false, false, true));
        assertFalse(HelpAccess.canUse(HelpCategory.DJ, false, false, false));
    }

    @Test
    void admin_ownerOrManageServer()
    {
        assertTrue(HelpAccess.canUse(HelpCategory.ADMIN, true, false, false));
        assertTrue(HelpAccess.canUse(HelpCategory.ADMIN, false, true, false));
        assertFalse(HelpAccess.canUse(HelpCategory.ADMIN, false, false, false));
    }

    @Test
    void admin_djRoleAloneIsNotEnough()
    {
        // Holding the DJ role does not imply Manage Server or ownership — those are separate
        // gates, and DJ commands staying usable must not leak into Admin looking usable too.
        assertFalse(HelpAccess.canUse(HelpCategory.ADMIN, false, false, true));
    }

    @Test
    void owner_onlyOwner()
    {
        assertTrue(HelpAccess.canUse(HelpCategory.OWNER, true, false, false));
        assertFalse(HelpAccess.canUse(HelpCategory.OWNER, false, true, false));
        assertFalse(HelpAccess.canUse(HelpCategory.OWNER, false, false, true));
    }
}
