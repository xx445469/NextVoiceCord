/*
 * Copyright 2026 Arif Banai (arif-banai)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.listener.interaction;

import com.jagrosh.jmusicbot.Bot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;

/**
 * Shared guard logic for interaction handlers: guild/member checks, voice channel checks,
 * and ensuring the bot joins the user's voice channel when needed.
 */
public final class InteractionGuards {

    private InteractionGuards() {
    }

    /**
     * Ensures the interaction is from a guild with a non-null member. If not, replies with an error and returns false.
     */
    public static boolean requireGuildAndMember(ButtonInteractionEvent event) {
        return requireGuildAndMemberImpl(event.getGuild(), event.getMember(), () -> event.reply("This can only be used in a server!").setEphemeral(true).queue());
    }

    /**
     * Ensures the interaction is from a guild with a non-null member. If not, replies with an error and returns false.
     */
    public static boolean requireGuildAndMember(StringSelectInteractionEvent event) {
        return requireGuildAndMemberImpl(event.getGuild(), event.getMember(), () -> event.reply("This can only be used in a server!").setEphemeral(true).queue());
    }

    /**
     * Ensures the interaction is from a guild with a non-null member. If not, replies with an error and returns false.
     */
    public static boolean requireGuildAndMember(ModalInteractionEvent event) {
        return requireGuildAndMemberImpl(event.getGuild(), event.getMember(), () -> event.reply("This can only be used in a server!").setEphemeral(true).queue());
    }

    /**
     * Ensures the interaction is from a guild with a non-null member. If not, replies with an error and returns false.
     */
    public static boolean requireGuildAndMember(EntitySelectInteractionEvent event) {
        return requireGuildAndMemberImpl(event.getGuild(), event.getMember(), () -> event.reply("This can only be used in a server!").setEphemeral(true).queue());
    }

    private static boolean requireGuildAndMemberImpl(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member member, Runnable sendError) {
        if (guild == null || member == null) {
            sendError.run();
            return false;
        }
        return true;
    }

    /**
     * Ensures the user is in the same voice channel as the bot. If not, replies with an error and returns false.
     * Assumes guild and member are non-null.
     */
    public static boolean requireSameVoiceChannel(ButtonInteractionEvent event) {
        return requireSameVoiceChannelImpl(event.getGuild(), event.getMember(), () -> event.reply("You must be in the same voice channel to use this!").setEphemeral(true).queue());
    }

    /**
     * Ensures the user is in the same voice channel as the bot. If not, replies with an error and returns false.
     * Assumes guild and member are non-null.
     */
    public static boolean requireSameVoiceChannel(StringSelectInteractionEvent event) {
        return requireSameVoiceChannelImpl(event.getGuild(), event.getMember(), () -> event.reply("You must be in the same voice channel to use this!").setEphemeral(true).queue());
    }

    private static boolean requireSameVoiceChannelImpl(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member member, Runnable sendError) {
        if (!member.getVoiceState().inAudioChannel()
                || guild.getSelfMember().getVoiceState().getChannel() == null
                || !member.getVoiceState().getChannel().equals(guild.getSelfMember().getVoiceState().getChannel())) {
            sendError.run();
            return false;
        }
        return true;
    }

    /**
     * Ensures the bot is in the user's voice channel for playback actions. If the user is not in a voice channel,
     * or is in a different channel than the bot when the bot is already connected, replies with an error and returns false.
     * If the bot is not connected, joins the user's channel and returns true (or false on permission error).
     *
     * @param event the button interaction event (must be in a guild with a non-null member)
     * @param bot   the bot instance
     * @return true if the bot is now in the user's voice channel, false if an error was replied
     */
    public static boolean ensureBotInUserVoiceChannel(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event, Bot bot) {
        Guild guild = event.getGuild();
        assert guild != null;
        var member = event.getMember();
        assert member != null;

        if (!member.getVoiceState().inAudioChannel()) {
            event.reply("You need to be in a voice channel to use this!").setEphemeral(true).queue();
            return false;
        }

        AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();
        AudioChannel userChannel = member.getVoiceState().getChannel();

        if (botChannel != null) {
            if (!userChannel.equals(botChannel)) {
                event.reply("You must be in the same voice channel to use this!").setEphemeral(true).queue();
                return false;
            }
            return true;
        }

        bot.getPlayerManager().setUpHandler(guild);
        try {
            guild.getAudioManager().openAudioConnection(userChannel);
            return true;
        } catch (PermissionException ex) {
            event.reply("I cannot connect to " + userChannel.getName() + "! Check my permissions.").setEphemeral(true).queue();
            return false;
        }
    }
}
