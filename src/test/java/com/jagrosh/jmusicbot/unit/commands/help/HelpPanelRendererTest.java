package com.jagrosh.jmusicbot.unit.commands.help;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.help.HelpCategory;
import com.jagrosh.jmusicbot.commands.help.HelpPanelRenderer;
import com.jagrosh.jmusicbot.testutil.TestTranslations;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpPanelRendererTest
{
    private static final Bot bot = TestTranslations.mockBot();
    private static final User user = Mockito.mock(User.class);

    static
    {
        Mockito.when(user.getId()).thenReturn("999");
        Mockito.when(user.getIdLong()).thenReturn(999L);
    }

    @Test
    void commandsForCategory_filtersAndSortsAlphabetically()
    {
        List<Command> commands = List.of(
                new FakeCommand("skip", "h", new Command.Category("DJ")),
                new FakeCommand("play", "h", new Command.Category("Music")),
                new FakeCommand("about", "h", null),
                new FakeCommand("forceskip", "h", new Command.Category("DJ")));

        List<Command> dj = HelpPanelRenderer.commandsForCategory(commands, HelpCategory.DJ);
        assertEquals(List.of("forceskip", "skip"), dj.stream().map(Command::getName).toList());

        List<Command> general = HelpPanelRenderer.commandsForCategory(commands, HelpCategory.GENERAL);
        assertEquals(List.of("about"), general.stream().map(Command::getName).toList());
    }

    @Test
    void commandsFieldValue_marksUsableCommandsWithCheckmark()
    {
        List<Command> commands = List.of(new FakeCommand("play", "plays a song", new Command.Category("Music")));
        String value = HelpPanelRenderer.commandsFieldValue(bot, null, user, commands, Set.of("play"), "!", true);
        assertTrue(value.contains("✅"));
        assertTrue(value.contains("/play"));
        assertTrue(value.contains("plays a song"));
        assertFalse(value.contains("🔒"));
    }

    @Test
    void commandsFieldValue_marksLockedCommandsWithLock()
    {
        List<Command> commands = List.of(new FakeCommand("forceskip", "skips the current song", new Command.Category("DJ")));
        String value = HelpPanelRenderer.commandsFieldValue(bot, null, user, commands, Set.of("forceskip"), "!", false);
        assertTrue(value.contains("🔒"));
        assertFalse(value.contains("✅"));
    }

    @Test
    void commandsFieldValue_usesTextPrefixWhenNoSlashEquivalent()
    {
        List<Command> commands = List.of(new FakeCommand("scsearch", "searches SoundCloud", new Command.Category("Music")));
        // "scsearch" has no v2 slash command, so it should show with the text prefix, not "/".
        String value = HelpPanelRenderer.commandsFieldValue(bot, null, user, commands, Set.of(), "!", true);
        assertTrue(value.contains("!scsearch"));
        assertFalse(value.contains("/scsearch"));
    }

    @Test
    void commandsFieldValue_appendsAliases()
    {
        List<Command> commands = List.of(new FakeCommand("play", "plays a song", new Command.Category("Music"), "p", "pl"));
        String value = HelpPanelRenderer.commandsFieldValue(bot, null, user, commands, Set.of("play"), "!", true);
        assertTrue(value.contains("aliases: p, pl"), value);
    }

    @Test
    void commandsFieldValue_truncatesWithAndMoreSuffix()
    {
        List<Command> commands = java.util.stream.IntStream.range(0, 60)
                .mapToObj(i -> (Command) new FakeCommand("command" + i,
                        "a fairly long help description to burn through the field budget quickly",
                        new Command.Category("Music")))
                .toList();
        String value = HelpPanelRenderer.commandsFieldValue(bot, null, user, commands, Set.of(), "!", true);
        assertTrue(value.contains("more..."), value);
        // The field text itself (excluding the surrounding code fence added by the caller)
        // must stay well clear of Discord's 1024-char field value limit.
        assertTrue(value.length() < 1024, "length was " + value.length());
    }

    @Test
    void categoriesFieldValue_pointsAtSelectedCategory()
    {
        String value = HelpPanelRenderer.categoriesFieldValue(bot, null, user, HelpCategory.DJ);
        assertTrue(value.contains("👉 DJ"));
        assertFalse(value.contains("👉 Music"));
        // Every category still appears even when it is not the selected one.
        for (String key : HelpCategory.ALL)
        {
            assertTrue(value.contains(HelpPanelRenderer.categoryLabel(bot, null, user, key)));
        }
    }

    @Test
    void categorySelect_offersNewsTutorialAndEveryRealCategory()
    {
        StringSelectMenu menu = HelpPanelRenderer.categorySelect(bot, null, user, HelpCategory.NEWS);
        List<String> values = menu.getOptions().stream().map(SelectOption::getValue).toList();
        assertEquals(7, values.size());
        assertTrue(values.contains(HelpCategory.NEWS));
        assertTrue(values.contains(HelpCategory.MUSIC));
        assertTrue(values.contains(HelpCategory.DJ));
        assertTrue(values.contains(HelpCategory.ADMIN));
        assertTrue(values.contains(HelpCategory.OWNER));
        assertTrue(values.contains(HelpCategory.GENERAL));
        // Discord allows at most 25 options; nowhere close, but worth pinning down.
        assertTrue(values.size() <= 25);
    }

    @Test
    void buildDisabledRows_disablesOnlyTheSelectMenu()
    {
        List<ActionRow> rows = HelpPanelRenderer.buildDisabledRows(bot, null, user, HelpCategory.NEWS);
        ActionRow selectRow = rows.get(1);
        StringSelectMenu menu = (StringSelectMenu) selectRow.getComponents().get(0);
        assertTrue(menu.isDisabled());

        ActionRow linkRow = rows.get(0);
        assertFalse(linkRow.getButtons().get(0).isDisabled(), "link buttons have nothing to disable");
    }

    @Test
    void buildEmbed_newsView_isLocalized()
    {
        MessageEmbed embed = HelpPanelRenderer.buildEmbed(bot, null, user, HelpCategory.NEWS,
                List.of(), Set.of(), "!", false, false, false);
        assertEquals(TestTranslations.english("help.news.title"), embed.getTitle());
    }

    @Test
    void buildEmbed_categoryView_titlesWithCategoryLabel()
    {
        MessageEmbed embed = HelpPanelRenderer.buildEmbed(bot, null, user, HelpCategory.DJ,
                List.of(), Set.of(), "!", false, false, false);
        assertEquals(TestTranslations.english("help.category.title", "DJ"), embed.getTitle());
    }

    @Test
    void effectivePrefix_mentionPrefixIsSpelledOutWithBotName()
    {
        Bot mentionBot = Mockito.mock(Bot.class);
        com.jagrosh.jmusicbot.BotConfig config = Mockito.mock(com.jagrosh.jmusicbot.BotConfig.class);
        Mockito.when(mentionBot.getConfig()).thenReturn(config);
        Mockito.when(config.getPrefix()).thenReturn("@mention");

        net.dv8tion.jda.api.JDA jda = Mockito.mock(net.dv8tion.jda.api.JDA.class);
        net.dv8tion.jda.api.entities.SelfUser self = Mockito.mock(net.dv8tion.jda.api.entities.SelfUser.class);
        Mockito.when(jda.getSelfUser()).thenReturn(self);
        Mockito.when(self.getName()).thenReturn("NextVoiceCord");

        assertEquals("@NextVoiceCord ", HelpPanelRenderer.effectivePrefix(mentionBot, null, jda));
    }
}
