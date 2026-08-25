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
package com.jagrosh.jmusicbot.commands.v2.admin;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.AdminSlashCommand;
import com.jagrosh.jmusicbot.ui.controller.ControllerEditor;

/**
 * Opens the controller-layout editor.
 *
 * @author adan (xx445469)
 */
public class ControllerSlashCmd extends AdminSlashCommand
{
    public ControllerSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "controller";
        this.help = "customises the now-playing control panel for this server";
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doAdminCommand(SlashCommandEvent event)
    {
        // Ephemeral: the editor shows a preview panel of disabled buttons, which sitting in
        // the channel would be mistaken for a real, broken controller.
        event.reply(ControllerEditor.build(bot, event.getGuild()))
             .setEphemeral(true)
             .queue();
    }
}
