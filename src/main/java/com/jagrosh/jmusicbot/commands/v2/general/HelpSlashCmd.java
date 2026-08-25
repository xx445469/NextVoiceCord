package com.jagrosh.jmusicbot.commands.v2.general;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.help.HelpCategory;
import com.jagrosh.jmusicbot.commands.help.HelpMenuController;
import com.jagrosh.jmusicbot.commands.help.HelpPanelRenderer;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

/**
 * The slash-command half of the help menu — {@code !help} is the other, wired up through
 * {@link com.jagrosh.jdautilities.command.CommandClientBuilder#setHelpConsumer} in
 * {@code CommandFactory}. Both build on {@link HelpPanelRenderer} and {@link HelpMenuController}
 * so they land on the same content; this class only owns how the message gets sent and edited.
 *
 * <p>Someone who only knows slash commands had no way to reach help before this existed — the
 * built-in help consumer only answered to the text prefix.
 */
public class HelpSlashCmd extends SlashCommand
{
    private final Bot bot;

    public HelpSlashCmd(Bot bot)
    {
        this.bot = bot;
        // guildOnly deliberately left unset — unlike settings or playback, nothing about help
        // needs a guild, so someone who DMs the bot can still reach it.
        this.name = "help";
        this.help = "shows an interactive list of commands";
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    protected void execute(SlashCommandEvent event)
    {
        Guild guild = event.getGuild();
        User user = event.getUser();
        Member member = event.getMember();
        List<Command> allCommands = event.getClient().getCommands();
        Set<String> slashNames = event.getClient().getSlashCommands().stream()
                .map(SlashCommand::getName)
                .collect(Collectors.toSet());
        String textPrefix = HelpPanelRenderer.effectivePrefix(bot, guild, event.getJDA());

        MessageEmbed embed = HelpPanelRenderer.buildEmbed(bot, guild, user, HelpCategory.NEWS,
                allCommands, slashNames, textPrefix, false, false, false);
        List<ActionRow> rows = HelpPanelRenderer.buildRows(bot, guild, user, HelpCategory.NEWS);

        // Ephemeral: a help dump in a busy channel is noise for everyone who did not ask.
        event.deferReply(true).queue(hook ->
                hook.editOriginal(new MessageEditBuilder().setEmbeds(embed).setComponents(rows).build())
                        .queue(message -> HelpMenuController.driveSelections(bot, guild, user, member,
                                allCommands, slashNames, textPrefix, message.getId(),
                                (newEmbed, newRows) -> hook.editOriginal(new MessageEditBuilder()
                                        .setEmbeds(newEmbed).setComponents(newRows).build()).queue(),
                                disabledRows -> hook.editOriginalComponents(disabledRows).queue())));
    }
}
