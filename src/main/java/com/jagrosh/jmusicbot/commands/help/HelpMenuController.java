package com.jagrosh.jmusicbot.commands.help;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/**
 * Drives the category dropdown on an already-sent help message, for both {@code !help} (a DM)
 * and {@code /help} (an ephemeral reply).
 *
 * <p>Uses {@link com.jagrosh.jdautilities.commons.waiter.EventWaiter} rather than a listener
 * registered in {@code DiscordService} — the same choice {@code SearchSlashCmd} and
 * {@code SearchCmd} already make for a one-off, single-viewer interaction with a timeout. A
 * durable listener is worth it for something like the settings panel, which has to keep working
 * after a restart; a help menu that goes stale in fifteen minutes either way does not.
 *
 * <p>Re-arms itself after every selection instead of using one fixed 60-second window, so the
 * timeout is 60 seconds of inactivity rather than 60 seconds from the first click — matching
 * Vocard's {@code View(timeout=60)}, which resets the same way.
 */
public final class HelpMenuController
{
    private static final long TIMEOUT_SECONDS = 60;

    private HelpMenuController() { }

    /**
     * @param messageId  id of the message the dropdown lives on, so a select on some other
     *                   message (a second {@code /help} from the same person, for instance)
     *                   is not mistaken for this one
     * @param onSelect   called with the new embed and rows after a category is chosen; must
     *                   push both to the message
     * @param onTimeout  called with disabled rows once nobody has touched the menu for a while;
     *                   must push only the components, leaving the embed as it was
     */
    public static void driveSelections(Bot bot, Guild guild, User user, Member member,
            List<Command> allCommands, Set<String> slashNames, String textPrefix, String messageId,
            BiConsumer<MessageEmbed, List<ActionRow>> onSelect, Consumer<List<ActionRow>> onTimeout)
    {
        boolean isOwner = String.valueOf(bot.getConfig().getOwnerId()).equals(user.getId());
        boolean canManageServer = member != null && member.hasPermission(Permission.MANAGE_SERVER);
        boolean djPermission = guild != null && member != null && DJCommand.checkDJPermission(bot, guild, member);

        awaitNext(bot, guild, user, member, allCommands, slashNames, textPrefix, messageId,
                isOwner, canManageServer, djPermission, onSelect, onTimeout, HelpCategory.NEWS);
    }

    private static void awaitNext(Bot bot, Guild guild, User user, Member member,
            List<Command> allCommands, Set<String> slashNames, String textPrefix, String messageId,
            boolean isOwner, boolean canManageServer, boolean djPermission,
            BiConsumer<MessageEmbed, List<ActionRow>> onSelect, Consumer<List<ActionRow>> onTimeout,
            String lastShownCategory)
    {
        bot.getWaiter().waitForEvent(
                StringSelectInteractionEvent.class,
                e -> messageId.equals(e.getMessageId())
                        && HelpPanelRenderer.selectId(user.getIdLong()).equals(e.getComponentId())
                        && e.getUser().getIdLong() == user.getIdLong(),
                e ->
                {
                    String category = e.getValues().isEmpty() ? HelpCategory.NEWS : e.getValues().get(0);
                    MessageEmbed embed = HelpPanelRenderer.buildEmbed(bot, guild, user, category,
                            allCommands, slashNames, textPrefix, isOwner, canManageServer, djPermission);
                    List<ActionRow> rows = HelpPanelRenderer.buildRows(bot, guild, user, category);

                    e.deferEdit().queue();
                    onSelect.accept(embed, rows);

                    awaitNext(bot, guild, user, member, allCommands, slashNames, textPrefix, messageId,
                            isOwner, canManageServer, djPermission, onSelect, onTimeout, category);
                },
                TIMEOUT_SECONDS, TimeUnit.SECONDS,
                () -> onTimeout.accept(HelpPanelRenderer.buildDisabledRows(bot, guild, user, lastShownCategory))
        );
    }
}
