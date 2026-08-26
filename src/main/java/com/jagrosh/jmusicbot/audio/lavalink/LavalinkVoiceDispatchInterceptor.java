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
package com.jagrosh.jmusicbot.audio.lavalink;

import com.fasterxml.jackson.databind.node.ObjectNode;

import net.dv8tion.jda.api.hooks.VoiceDispatchInterceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The other half of the "who holds the voice connection" boundary described in
 * {@link LavalinkPlaybackEngine}.
 *
 * <p>In Lavaplayer mode, {@code AudioHandler implements AudioSendHandler} and JDA both opens the
 * voice connection ({@code AudioManager.openAudioConnection}) and pushes the Opus frames
 * {@code AudioHandler.provide()} hands it. In Lavalink mode, the node does both of those — this
 * class is JDA's documented mechanism for that split ({@code JDABuilder.setVoiceDispatchInterceptor}):
 * installing it stops JDA from opening its own UDP audio socket, and instead routes the raw
 * VOICE_SERVER_UPDATE / VOICE_STATE_UPDATE gateway payloads here so they can be forwarded to the
 * node instead. Joining a channel then goes through
 * {@code guild.getJDA().getDirectAudioController().connect(channel)} rather than
 * {@code AudioManager.openAudioConnection(channel)} — see {@link LavalinkPlaybackEngine#join}.
 *
 * <p>Only installed at all when {@code playback.engine = lavalink}; see
 * {@code DiscordService.createJDA}.
 *
 * @author adan (xx445469)
 */
final class LavalinkVoiceDispatchInterceptor implements VoiceDispatchInterceptor
{
    private static final Logger LOG = LoggerFactory.getLogger(LavalinkVoiceDispatchInterceptor.class);

    private final LavalinkPlaybackEngine engine;

    LavalinkVoiceDispatchInterceptor(LavalinkPlaybackEngine engine)
    {
        this.engine = engine;
    }

    @Override
    public void onVoiceServerUpdate(VoiceServerUpdate update)
    {
        long guildId = update.getGuildIdLong();
        String endpoint = update.getEndpoint();
        String token = update.getToken();
        String sessionId = update.getSessionId();

        if (endpoint == null || token == null || sessionId == null)
        {
            LOG.debug("Lavalink: incomplete voice server update for guild {} (endpoint={}, has token={}, has session={})",
                    guildId, endpoint, token != null, sessionId != null);
            return;
        }

        ObjectNode voice = engine.newPlayerUpdateBody();
        ObjectNode voiceObject = voice.putObject("voice");
        voiceObject.put("token", token);
        voiceObject.put("endpoint", endpoint);
        voiceObject.put("sessionId", sessionId);

        engine.sendVoiceUpdate(guildId, voice);
    }

    @Override
    public boolean onVoiceStateUpdate(VoiceStateUpdate update)
    {
        long guildId = update.getGuildIdLong();
        boolean wasConnected = engine.isConnected(guildId);
        boolean nowConnected = update.getChannel() != null;
        engine.setConnected(guildId, nowConnected);

        // Per JDA's documented contract: true if a connection was previously established.
        return wasConnected;
    }
}
