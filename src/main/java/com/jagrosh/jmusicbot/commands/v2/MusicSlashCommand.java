package com.jagrosh.jmusicbot.commands.v2;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.MusicCommandValidator;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

public abstract class MusicSlashCommand extends LocalizedSlashCommand
{
    protected boolean bePlaying;
    protected boolean beListening;

    /** See {@link com.jagrosh.jmusicbot.commands.v1.MusicCommand#lavalinkStageOneSupported}. */
    protected boolean lavalinkStageOneSupported = false;

    public MusicSlashCommand(Bot bot)
    {
        super(bot);
        this.guildOnly = true;
        this.category = new Category("Music");
    }

    @Override
    protected void execute(SlashCommandEvent event)
    {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        String errorEmoji = event.getClient().getError();

        boolean valid = MusicCommandValidator.validate(
                event.getGuild(),
                event.getMember(),
                event.getChannel().asGuildMessageChannel(),
                settings,
                bot,
                event.getJDA(),
                bePlaying,
                beListening,
                new MusicCommandValidator.ErrorHandler()
                {
                    @Override
                    public void onTextChannelError(TextChannel requiredChannel)
                    {
                        event.reply(errorEmoji + " " + bot.msg(event.getGuild(), "common.errors.mustBeInChannel", requiredChannel.getAsMention()))
                                .setEphemeral(true).queue();
                    }

                    @Override
                    public void onNotPlayingError()
                    {
                        event.reply(errorEmoji + " " + bot.msg(event.getGuild(), "common.errors.noMusicPlayingToUse"))
                                .setEphemeral(true).queue();
                    }

                    @Override
                    public void onNotListeningError(AudioChannel requiredChannel)
                    {
                        String channelName = requiredChannel == null ? "a voice channel" : requiredChannel.getAsMention();
                        event.reply(errorEmoji + " " + bot.msg(event.getGuild(), "common.errors.mustBeListening", channelName))
                                .setEphemeral(true).queue();
                    }

                    @Override
                    public void onAfkChannelError()
                    {
                        event.reply(errorEmoji + " " + bot.msg(event.getGuild(), "common.errors.afkChannel"))
                                .setEphemeral(true).queue();
                    }

                    @Override
                    public void onVoiceConnectError(AudioChannel channel)
                    {
                        event.reply(errorEmoji + " " + bot.msg(event.getGuild(), "common.errors.voiceConnectFailed", channel.getAsMention()))
                                .setEphemeral(true).queue();
                    }
                }
        );

        if (valid)
        {
            if (bot.getConfig().isLavalinkMode() && !lavalinkStageOneSupported)
            {
                event.reply(bot.getConfig().getWarning() + " " + bot.msg(event.getGuild(), "lavalink.notYetSupported"))
                        .setEphemeral(true).queue();
                return;
            }
            doCommand(event);
        }
    }

    public abstract void doCommand(SlashCommandEvent event);
}