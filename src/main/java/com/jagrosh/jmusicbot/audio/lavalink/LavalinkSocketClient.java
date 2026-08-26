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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The persistent WebSocket connection to one Lavalink node ({@code /v4/websocket}).
 *
 * <p>This is the node's side of the picture described in {@link LavalinkPlaybackEngine}: the
 * node owns the actual Discord voice connection, and this socket is how it tells the bot what
 * happened (ready/session id, track start/end, player state) and how the bot's forwarded voice
 * server update reaches it in the first place — that part happens over the REST "update player"
 * call once {@link #getSessionId()} is known, not over this socket.
 *
 * <p>Reconnection here is intentionally minimal for stage 1: one retry loop with a fixed delay.
 * A node that is down when the bot starts, or that drops mid-session, is not something stage 1
 * promises to recover from gracefully — only to log clearly and not crash the bot.
 *
 * @author adan (xx445469)
 */
final class LavalinkSocketClient extends WebSocketListener
{
    private static final Logger LOG = LoggerFactory.getLogger(LavalinkSocketClient.class);

    /** Callback surface for the events this socket receives. */
    interface Listener
    {
        void onReady(String sessionId, boolean resumed);

        void onEvent(long guildId, JsonNode payload);

        void onPlayerUpdate(long guildId, JsonNode payload);

        void onSocketClosed(String reason);
    }

    private final LavalinkNodeConfig node;
    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final long selfUserId;
    private final String clientName;
    private final Listener listener;

    private volatile WebSocket socket;
    private volatile String sessionId;

    LavalinkSocketClient(LavalinkNodeConfig node, OkHttpClient http, ObjectMapper mapper,
                          long selfUserId, String clientName, Listener listener)
    {
        this.node = node;
        this.http = http;
        this.mapper = mapper;
        this.selfUserId = selfUserId;
        this.clientName = clientName;
        this.listener = listener;
    }

    String getSessionId()
    {
        return sessionId;
    }

    void connect()
    {
        Request request = new Request.Builder()
                .url(node.webSocketUrl())
                .header("Authorization", node.password())
                .header("User-Id", Long.toString(selfUserId))
                .header("Client-Name", clientName)
                .build();
        LOG.info("Connecting to Lavalink node {}...", node.describe());
        socket = http.newWebSocket(request, this);
    }

    void close()
    {
        WebSocket current = socket;
        if (current != null)
        {
            current.close(1000, "shutdown");
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response)
    {
        LOG.debug("Lavalink node {}: WebSocket open, waiting for 'ready' op.", node.describe());
    }

    @Override
    public void onMessage(WebSocket webSocket, String text)
    {
        JsonNode json;
        try
        {
            json = mapper.readTree(text);
        }
        catch (Exception ex)
        {
            LOG.warn("Lavalink node {}: could not parse a WebSocket message: {}", node.describe(), ex.toString());
            return;
        }

        String op = json.path("op").asText("");
        switch (op)
        {
            case "ready" ->
            {
                sessionId = json.path("sessionId").asText(null);
                boolean resumed = json.path("resumed").asBoolean(false);
                LOG.info("Lavalink node {} ready (session={}, resumed={}).", node.describe(), sessionId, resumed);
                if (sessionId != null)
                {
                    listener.onReady(sessionId, resumed);
                }
            }
            case "event" -> dispatchGuildPayload(json, listener::onEvent);
            case "playerUpdate" -> dispatchGuildPayload(json, listener::onPlayerUpdate);
            case "stats" ->
            {
                // Node-wide stats; stage 1 has nowhere to surface these yet (that is the GUI/web
                // diagnostics work called out as stage 2), so they are only logged at trace level.
                LOG.trace("Lavalink node {} stats: {}", node.describe(), json);
            }
            default -> LOG.debug("Lavalink node {}: unhandled op '{}': {}", node.describe(), op, json);
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes)
    {
        onMessage(webSocket, bytes.utf8());
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason)
    {
        LOG.info("Lavalink node {}: WebSocket closing ({}: {}).", node.describe(), code, reason);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason)
    {
        sessionId = null;
        listener.onSocketClosed(reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response)
    {
        sessionId = null;
        LOG.warn("Lavalink node {}: WebSocket connection failed: {}", node.describe(), t.toString());
        listener.onSocketClosed(t.getMessage());
    }

    private interface GuildPayloadHandler
    {
        void handle(long guildId, JsonNode payload);
    }

    private void dispatchGuildPayload(JsonNode json, GuildPayloadHandler handler)
    {
        String guildIdText = json.path("guildId").asText(null);
        if (guildIdText == null)
        {
            return;
        }
        try
        {
            handler.handle(Long.parseLong(guildIdText), json);
        }
        catch (NumberFormatException ex)
        {
            LOG.debug("Lavalink node {}: payload had a non-numeric guildId '{}': {}",
                    node.describe(), guildIdText, json);
        }
    }
}
