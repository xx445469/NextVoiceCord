package com.jagrosh.jmusicbot.commands.v1.general;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.help.HelpCategory;
import com.jagrosh.jmusicbot.commands.help.HelpMenuController;
import com.jagrosh.jmusicbot.commands.help.HelpPanelRenderer;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

/**
 * Replaces jda-utilities' built-in {@code !help} — a flat plain-text dump of every command in
 * one message — with the same category menu {@code /help} shows, installed via
 * {@link com.jagrosh.jdautilities.command.CommandClientBuilder#setHelpConsumer}.
 *
 * <p>Sent by DM rather than into the channel it was typed in, for the same reason the slash
 * version replies ephemerally: nobody else in the channel asked to see it. This is also the
 * classic jda-utilities convention for text-command help, so it does not surprise anyone who has
 * used another bot built on the same library.
 */
public class HelpTextConsumer implements Consumer<CommandEvent>
{
    private final Bot bot;

    public HelpTextConsumer(Bot bot)
    {
        this.bot = bot;
    }

    @Override
    public void accept(CommandEvent event)
    {
        Guild guild = event.getGuild();
        User user = event.getAuthor();
        Member member = event.getMember();
        List<Command> allCommands = event.getClient().getCommands();
        Set<String> slashNames = event.getClient().getSlashCommands().stream()
                .map(SlashCommand::getName)
                .collect(Collectors.toSet());
        String textPrefix = HelpPanelRenderer.effectivePrefix(bot, guild, event.getJDA());

        MessageEmbed embed = HelpPanelRenderer.buildEmbed(bot, guild, user, HelpCategory.NEWS,
                allCommands, slashNames, textPrefix, false, false, false);
        List<ActionRow> rows = HelpPanelRenderer.buildRows(bot, guild, user, HelpCategory.NEWS);
        var data = new MessageCreateBuilder().setEmbeds(embed).setComponents(rows).build();

        user.openPrivateChannel().queue(
                channel -> channel.sendMessage(data).queue(
                        message ->
                        {
                            if (guild != null)
                            {
                                event.reply(bot.msgFor(guild, user, "help.dm.sent"));
                            }
                            HelpMenuController.driveSelections(bot, guild, user, member, allCommands, slashNames,
                                    textPrefix, message.getId(),
                                    (newEmbed, newRows) -> editMessage(message, newEmbed, newRows),
                                    disabledRows -> message.editMessageComponents(disabledRows).queue());
                        },
                        error -> reportDmFailure(event, guild, user)),
                error -> reportDmFailure(event, guild, user));
    }

    private void editMessage(Message message, MessageEmbed embed, List<ActionRow> rows)
    {
        message.editMessage(new MessageEditBuilder().setEmbeds(embed).setComponents(rows).build()).queue();
    }

    private void reportDmFailure(CommandEvent event, Guild guild, User user)
    {
        event.reply(bot.getConfig().getError() + " " + bot.msgFor(guild, user, "help.dm.blocked"));
    }
}
