package com.jagrosh.jmusicbot.commands.help;

import java.util.List;

import com.jagrosh.jdautilities.command.Command;

/**
 * The categories the help menu's dropdown offers.
 *
 * <p>{@link #NEWS} and {@link #TUTORIAL} are not backed by any command — they are the landing
 * view and a short how-to-use page, the same pair Vocard's help menu leads with. Everything
 * after them is backed by a real {@link Command.Category} on the registered commands.
 *
 * <p>{@link #GENERAL} exists because three commands (about, ping, settings) set no category at
 * all, so {@link Command#getCategory()} returns {@code null} for them. Dropping them from the
 * menu would mean "every command is listed" was not actually true, so they get a tab of their
 * own rather than being hidden.
 */
public final class HelpCategory
{
    public static final String NEWS = "news";
    public static final String TUTORIAL = "tutorial";
    public static final String GENERAL = "general";
    public static final String MUSIC = "music";
    public static final String DJ = "dj";
    public static final String ADMIN = "admin";
    public static final String OWNER = "owner";

    /** Categories backed by real commands, in dropdown order. */
    public static final List<String> REAL = List.of(GENERAL, MUSIC, DJ, ADMIN, OWNER);

    /** Every dropdown option, pseudo-categories first. */
    public static final List<String> ALL = List.of(NEWS, TUTORIAL, GENERAL, MUSIC, DJ, ADMIN, OWNER);

    private HelpCategory() { }

    /**
     * Maps a command's jda-utilities category to one of the keys above.
     *
     * @param command the command to classify
     * @return {@link #GENERAL} for a command with no category (or an unrecognised one), the
     *         matching key otherwise
     */
    public static String keyFor(Command command)
    {
        Command.Category category = command.getCategory();
        if (category == null || category.getName() == null)
        {
            return GENERAL;
        }
        return switch (category.getName())
        {
            case "Music" -> MUSIC;
            case "DJ" -> DJ;
            case "Admin" -> ADMIN;
            case "Owner" -> OWNER;
            default -> GENERAL;
        };
    }
}
