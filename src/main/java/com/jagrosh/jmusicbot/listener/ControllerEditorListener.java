/*
 * Copyright 2026 adan (xx445469) - NextVoiceCord
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
package com.jagrosh.jmusicbot.listener;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.ui.controller.ControllerEditor;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.jetbrains.annotations.NotNull;

/**
 * Handles the controller-layout editor.
 *
 * @author adan (xx445469)
 */
public class ControllerEditorListener extends ListenerAdapter
{
    private final Bot bot;

    public ControllerEditorListener(Bot bot)
    {
        this.bot = bot;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event)
    {
        String id = event.getComponentId();
        if (!id.startsWith(ControllerEditor.PREFIX) || event.getGuild() == null)
        {
            return;
        }

        if (!canEdit(event))
        {
            return;
        }

        switch (id)
        {
            case ControllerEditor.ACTION_EDIT ->
                    event.replyModal(ControllerEditor.buildModal(bot, event.getGuild())).queue();

            case ControllerEditor.ACTION_RESET ->
            {
                bot.getSettingsManager().getSettings(event.getGuild()).setControllerLayoutJson(null);
                event.editMessage(ControllerEditor.rebuild(bot, event.getGuild())).queue();
            }

            case ControllerEditor.ACTION_REFRESH ->
                    event.editMessage(ControllerEditor.rebuild(bot, event.getGuild())).queue();

            default -> { }
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event)
    {
        if (!ControllerEditor.MODAL_ID.equals(event.getModalId()) || event.getGuild() == null)
        {
            return;
        }

        if (!canEdit(event))
        {
            return;
        }

        var value = event.getValue(ControllerEditor.INPUT_ID);
        ControllerEditor.ApplyResult result =
                ControllerEditor.apply(bot, event.getGuild(), value == null ? null : value.getAsString());

        String message = bot.msg(event.getGuild(), result.messageKey());

        // Warnings are shown, not swallowed. The parser is forgiving, so without this a typo
        // would be dropped in silence and the editor would show a layout that does not match
        // what was typed, with nothing explaining the difference.
        if (!result.warnings().isEmpty())
        {
            message += "\n\n" + bot.msg(event.getGuild(), "controller.editor.warnings") + "\n• "
                     + String.join("\n• ", result.warnings());
        }

        if (!result.accepted())
        {
            event.reply(bot.getConfig().getError() + " " + message).setEphemeral(true).queue();
            return;
        }

        event.reply(bot.getConfig().getSuccess() + " " + message).setEphemeral(true).queue();

        // Edited after replying so the editor reflects the change immediately; leaving it
        // stale is the difference between "did that work?" and seeing the new panel.
        event.getMessage().editMessage(ControllerEditor.rebuild(bot, event.getGuild())).queue(
                success -> { },
                failure -> { /* the editor message may have been dismissed; nothing to update */ });
    }

    /**
     * Guards the editor.
     *
     * <p>The layout is guild-wide, so anyone able to change it changes what every member
     * sees. Manage Server is the permission that already means "may reconfigure this server".
     */
    private boolean canEdit(net.dv8tion.jda.api.interactions.callbacks.IReplyCallback event)
    {
        var member = event.getMember();
        if (member != null && member.hasPermission(Permission.MANAGE_SERVER))
        {
            return true;
        }
        event.reply(bot.getConfig().getError() + " "
                    + bot.msg(event.getGuild(), "permissions.errors.needManageServer"))
             .setEphemeral(true).queue();
        return false;
    }
}
