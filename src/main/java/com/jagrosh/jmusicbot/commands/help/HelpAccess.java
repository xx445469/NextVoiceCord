package com.jagrosh.jmusicbot.commands.help;

/**
 * Whether the person looking at the help menu could actually run a command in a given category.
 *
 * <p>Deliberately independent of JDA's {@code Guild}/{@code Member} so it can be exercised
 * without a live bot. Callers resolve the three booleans from real permission checks — the DJ
 * one should come from {@link com.jagrosh.jmusicbot.commands.v1.DJCommand#checkDJPermission}, so
 * this file never drifts from the gate that actually runs a DJ command — and this class only
 * decides what those checks mean for each tab.
 */
public final class HelpAccess
{
    private HelpAccess() { }

    /**
     * @param categoryKey     one of the keys in {@link HelpCategory}
     * @param isOwner         whether the viewer is the configured bot owner
     * @param canManageServer whether the viewer has the Manage Server permission in this guild
     * @param djPermission    the result of {@code DJCommand.checkDJPermission} for this viewer —
     *                        already true for the owner and anyone with Manage Server, so it is
     *                        used as-is rather than re-combined with the other two flags
     * @return whether the viewer could run a command in that category right now
     */
    public static boolean canUse(String categoryKey, boolean isOwner, boolean canManageServer, boolean djPermission)
    {
        return switch (categoryKey)
        {
            case HelpCategory.OWNER -> isOwner;
            case HelpCategory.ADMIN -> isOwner || canManageServer;
            case HelpCategory.DJ -> djPermission;
            // General and Music: nothing beyond being in the server is required.
            default -> true;
        };
    }
}
