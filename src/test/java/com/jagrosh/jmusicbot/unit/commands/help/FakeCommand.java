package com.jagrosh.jmusicbot.unit.commands.help;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

/**
 * A minimal, do-nothing {@link Command} for tests that only need to inspect metadata (name,
 * category, aliases, help text) — never to actually run it.
 */
class FakeCommand extends Command
{
    FakeCommand(String name, String help, Category category, String... aliases)
    {
        this.name = name;
        this.help = help;
        this.category = category;
        this.aliases = aliases;
    }

    @Override
    protected void execute(CommandEvent event)
    {
        throw new UnsupportedOperationException("not needed for these tests");
    }
}
