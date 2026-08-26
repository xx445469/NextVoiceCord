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

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Talks to one Lavalink node's REST API (v4): loading tracks and updating a guild's player.
 *
 * <p>Deliberately hand-rolled against the documented REST shapes (lavalink.dev/api/rest.html)
 * rather than pulling in a client library — see {@link LavalinkPlaybackEngine} for why. Every
 * call is async (OkHttp's own dispatcher threads); nothing here blocks a JDA gateway or
 * interaction thread.
 *
 * @author adan (xx445469)
 */
final class LavalinkRestClient
{
    private static final Logger LOG = LoggerFactory.getLogger(LavalinkRestClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final LavalinkNodeConfig node;
    private final OkHttpClient http;
    private final ObjectMapper mapper;

    /** Set once the WebSocket receives the "ready" op. Every player call needs it. */
    private volatile String sessionId;

    LavalinkRestClient(LavalinkNodeConfig node, OkHttpClient http, ObjectMapper mapper)
    {
        this.node = node;
        this.http = http;
        this.mapper = mapper;
    }

    void setSessionId(String sessionId)
    {
        this.sessionId = sessionId;
    }

    boolean isReady()
    {
        return sessionId != null;
    }

    /** GET /v4/loadtracks?identifier=... */
    CompletableFuture<JsonNode> loadTracks(String identifier)
    {
        HttpUrl url = HttpUrl.parse(node.httpBaseUrl() + "/v4/loadtracks")
                .newBuilder()
                .addQueryParameter("identifier", identifier)
                .build();
        Request request = baseRequest(url).get().build();
        return execute(request, "loadtracks(" + identifier + ")");
    }

    /**
     * PATCH /v4/sessions/{sessionId}/players/{guildId}
     *
     * @param body the JSON body per the v4 "Update Player" shape (track/voice/volume/paused/...)
     */
    CompletableFuture<JsonNode> updatePlayer(long guildId, ObjectNode body, boolean noReplace)
    {
        if (sessionId == null)
        {
            CompletableFuture<JsonNode> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException(
                    "Not connected to Lavalink node " + node.describe() + " yet (no session)."));
            return failed;
        }

        HttpUrl url = HttpUrl.parse(node.httpBaseUrl() + "/v4/sessions/" + sessionId + "/players/" + guildId)
                .newBuilder()
                .addQueryParameter("noReplace", Boolean.toString(noReplace))
                .build();
        RequestBody requestBody = RequestBody.create(body.toString(), JSON);
        Request request = baseRequest(url).patch(requestBody).build();
        return execute(request, "updatePlayer(guild=" + guildId + ")");
    }

    /** DELETE /v4/sessions/{sessionId}/players/{guildId} */
    void destroyPlayer(long guildId)
    {
        if (sessionId == null)
        {
            return;
        }
        HttpUrl url = HttpUrl.parse(node.httpBaseUrl() + "/v4/sessions/" + sessionId + "/players/" + guildId);
        Request request = baseRequest(url).delete().build();
        http.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                LOG.debug("Lavalink destroyPlayer(guild={}) on {} failed: {}", guildId, node.describe(), e.toString());
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                response.close();
            }
        });
    }

    private Request.Builder baseRequest(HttpUrl url)
    {
        return new Request.Builder()
                .url(url)
                .header("Authorization", node.password());
    }

    private CompletableFuture<JsonNode> execute(Request request, String what)
    {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        http.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                future.completeExceptionally(
                        new IOException("Lavalink node " + node.describe() + " " + what + " failed: " + e.getMessage(), e));
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (ResponseBody responseBody = response.body())
                {
                    if (!response.isSuccessful())
                    {
                        future.completeExceptionally(new IOException(
                                "Lavalink node " + node.describe() + " " + what + " returned HTTP " + response.code()));
                        return;
                    }
                    if (responseBody == null)
                    {
                        future.complete(mapper.nullNode());
                        return;
                    }
                    String text = responseBody.string();
                    future.complete(text.isBlank() ? mapper.nullNode() : mapper.readTree(text));
                }
                catch (IOException ex)
                {
                    future.completeExceptionally(ex);
                }
            }
        });
        return future;
    }
}
