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
package com.jagrosh.jmusicbot.listener;

import com.jagrosh.jmusicbot.Bot;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * Handles message deletion for now-playing embed cleanup.
 */
public class NowPlayingCleanupListener extends ListenerAdapter {

    private final Bot bot;

    public NowPlayingCleanupListener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        if (event.isFromGuild()) {
            bot.getNowplayingHandler().onMessageDelete(event.getGuild(), event.getMessageIdLong());
        }
    }
}
