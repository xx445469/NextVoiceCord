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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.jagrosh.jmusicbot.Bot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import okhttp3.OkHttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage-1 Lavalink playback engine: the facade {@link Bot} and {@code MusicService} use in place
 * of {@link com.jagrosh.jmusicbot.audio.AudioHandler} when {@code playback.engine = lavalink}.
 *
 * <h2>Why this exists as its own path rather than a rewrite of AudioHandler</h2>
 *
 * <p>Lavaplayer and Lavalink disagree about who holds the Discord voice connection. In
 * Lavaplayer mode the bot opens the connection itself and {@code AudioHandler}, as an
 * {@code AudioSendHandler}, pushes Opus frames JDA reads on every tick. In Lavalink mode the
 * <em>node</em> opens the voice connection and sends the frames; the bot's job shrinks to
 * forwarding the voice server/state update to the node ({@link LavalinkVoiceDispatchInterceptor})
 * and issuing REST commands ({@link LavalinkRestClient}). Threading one abstraction through both
 * would mean rewriting every one of the ~48 call sites that currently assume the bot holds the
 * connection — the opposite of what a stage-1 change should do. Instead, this package is a
 * self-contained second path, and the only places that branch on which one is active are the
 * small number of call sites stage 1 actually promises: see {@code MusicCommandValidator},
 * {@code MusicSlashCommand}/{@code MusicCommand} (the {@code lavalinkStageOneSupported} gate),
 * and the handful of {@code MusicService} methods for play/pause/resume/stop/skip/volume.
 *
 * <h2>What stage 1 deliberately does not have</h2>
 *
 * <p>No repeat modes, no favorites, no playback history, no queue reordering, no multi-node
 * routing or failover — only the first configured node is used, and only a plain FIFO queue.
 * Everything else keeps working normally in {@code lavaplayer} mode; in {@code lavalink} mode,
 * commands outside this set are refused with a clear message rather than silently no-opping or
 * reading Lavaplayer state that was never populated.
 *
 * @author adan (xx445469)
 */
public final class LavalinkPlaybackEngine
{
    private static final Logger LOG = LoggerFactory.getLogger(LavalinkPlaybackEngine.class);

    /** Outcome of a {@link #play} call. */
    public record PlayResult(Kind kind, String title, int queuePosition, String errorDetail)
    {
        public enum Kind { PLAYING_NOW, QUEUED, NO_MATCHES, LOAD_FAILED, TOO_LONG, NODE_NOT_READY }

        static PlayResult playingNow(String title)
        {
            return new PlayResult(Kind.PLAYING_NOW, title, 0, null);
        }

        static PlayResult queued(String title, int position)
        {
            return new PlayResult(Kind.QUEUED, title, position, null);
        }

        static PlayResult noMatches()
        {
            return new PlayResult(Kind.NO_MATCHES, null, 0, null);
        }

        static PlayResult loadFailed(String detail)
        {
            return new PlayResult(Kind.LOAD_FAILED, null, 0, detail);
        }

        static PlayResult tooLong(String title)
        {
            return new PlayResult(Kind.TOO_LONG, title, 0, null);
        }

        static PlayResult notReady()
        {
            return new PlayResult(Kind.NODE_NOT_READY, null, 0, null);
        }
    }

    private final Bot bot;
    private final LavalinkNodeConfig node;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http;
    private final LavalinkRestClient rest;
    private final LavalinkSocketClient socket;
    private final Map<Long, LavalinkGuildPlayer> players = new ConcurrentHashMap<>();

    /**
     * @param bot   owning bot (for settings/config lookups only — never touches AudioHandler)
     * @param nodes validated node list from config; only the first is used in stage 1
     */
    public LavalinkPlaybackEngine(Bot bot, List<LavalinkNodeConfig> nodes)
    {
        this.bot = bot;
        this.node = nodes.get(0);
        if (nodes.size() > 1)
        {
            LOG.warn("{} Lavalink nodes configured; stage 1 only connects to the first ({}). "
                    + "Routing across multiple nodes is planned for a later stage.",
                    nodes.size(), node.describe());
        }

        this.http = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(20))
                .pingInterval(Duration.ofSeconds(30))
                .build();
        this.rest = new LavalinkRestClient(node, http, mapper);
        this.socket = new LavalinkSocketClient(node, http, mapper, botUserId(), clientName(), new SocketListener());
    }

    private long botUserId()
    {
        return bot.getJDA() == null ? 0L : bot.getJDA().getSelfUser().getIdLong();
    }

    private static String clientName()
    {
        return "NextVoiceCord/lavalink-stage1";
    }

    /** Opens the WebSocket to the node. Call once JDA is connected (the self user id is needed). */
    public void start()
    {
        socket.connect();
    }

    /**
     * The interceptor {@code DiscordService} installs via {@code JDABuilder.setVoiceDispatchInterceptor}
     * when this engine is active. Returned rather than the class being public: nothing outside
     * this package needs to know how the interception is implemented, only that this engine can
     * supply one.
     */
    public net.dv8tion.jda.api.hooks.VoiceDispatchInterceptor newVoiceDispatchInterceptor()
    {
        return new LavalinkVoiceDispatchInterceptor(this);
    }

    /** Closes the node connection. Does not disconnect any guild from voice. */
    public void shutdown()
    {
        socket.close();
        http.dispatcher().executorService().shutdown();
    }

    private LavalinkGuildPlayer playerFor(long guildId)
    {
        return players.computeIfAbsent(guildId, LavalinkGuildPlayer::new);
    }

    ObjectNode newPlayerUpdateBody()
    {
        return mapper.createObjectNode();
    }

    // ==================== Voice connection ====================

    /** Joins {@code channel} via JDA's direct audio controller (bypasses AudioManager entirely). */
    public void join(Guild guild, AudioChannel channel)
    {
        guild.getJDA().getDirectAudioController().connect(channel);
    }

    /** Leaves voice and asks the node to discard this guild's player. */
    public void leave(Guild guild)
    {
        guild.getJDA().getDirectAudioController().disconnect(guild);
        LavalinkGuildPlayer player = players.remove(guild.getIdLong());
        if (player != null)
        {
            player.setConnected(false);
        }
        rest.destroyPlayer(guild.getIdLong());
    }

    boolean isConnected(long guildId)
    {
        LavalinkGuildPlayer player = players.get(guildId);
        return player != null && player.isConnected();
    }

    void setConnected(long guildId, boolean connected)
    {
        playerFor(guildId).setConnected(connected);
    }

    /** Forwards a voice server update (the "voice" object) to the node for this guild. */
    void sendVoiceUpdate(long guildId, ObjectNode body)
    {
        if (!rest.isReady())
        {
            LOG.warn("Lavalink node {} is not ready yet; a voice update for guild {} was dropped. "
                    + "Playback in that guild will not start until the node connects.", node.describe(), guildId);
            return;
        }
        rest.updatePlayer(guildId, body, true).exceptionally(ex ->
        {
            LOG.warn("Lavalink: failed to forward voice update for guild {} to {}: {}",
                    guildId, node.describe(), ex.toString());
            return null;
        });
    }

    // ==================== Playback ====================

    public boolean isPlaying(Guild guild)
    {
        LavalinkGuildPlayer player = players.get(guild.getIdLong());
        return player != null && player.isPlaying();
    }

    public boolean isPaused(Guild guild)
    {
        LavalinkGuildPlayer player = players.get(guild.getIdLong());
        return player != null && player.isPaused();
    }

    public String getCurrentTitle(Guild guild)
    {
        LavalinkGuildPlayer player = players.get(guild.getIdLong());
        LavalinkTrack track = player == null ? null : player.getCurrent();
        return track == null ? null : track.title();
    }

    public long getCurrentRequesterId(Guild guild)
    {
        LavalinkGuildPlayer player = players.get(guild.getIdLong());
        LavalinkTrack track = player == null ? null : player.getCurrent();
        return track == null ? 0L : track.requesterId();
    }

    public java.util.Set<String> getVotes(Guild guild)
    {
        return playerFor(guild.getIdLong()).getVotes();
    }

    public int getVolume(Guild guild)
    {
        return playerFor(guild.getIdLong()).getVolume();
    }

    /**
     * Loads {@code query} from the node and either starts it immediately (nothing else playing)
     * or appends it to the guild's simple FIFO queue.
     *
     * <p>Mirrors {@code MusicService.play}'s two-phase lookup: {@code query} is tried as-is
     * first (a direct URL/identifier), and only re-tried as {@code ytsearch:<query>} if that
     * comes back empty — the same fallback {@code AudioLoadResultHandlers} uses for Lavaplayer.
     */
    public CompletableFuture<PlayResult> play(Guild guild, long requesterId, String query)
    {
        if (!rest.isReady())
        {
            return CompletableFuture.completedFuture(PlayResult.notReady());
        }

        return rest.loadTracks(query).thenCompose(json ->
        {
            String loadType = json.path("loadType").asText("");
            if ("empty".equals(loadType) && !query.startsWith("ytsearch:") && !looksLikeUrl(query))
            {
                return rest.loadTracks("ytsearch:" + query);
            }
            return CompletableFuture.completedFuture(json);
        }).thenCompose(json -> handleLoadResult(guild, requesterId, json));
    }

    private static boolean looksLikeUrl(String query)
    {
        return query.startsWith("http://") || query.startsWith("https://");
    }

    private CompletableFuture<PlayResult> handleLoadResult(Guild guild, long requesterId, JsonNode json)
    {
        String loadType = json.path("loadType").asText("");
        JsonNode data = json.path("data");

        LavalinkTrack track = switch (loadType)
        {
            case "track" -> trackFrom(data, requesterId);
            case "search" -> data.isArray() && !data.isEmpty() ? trackFrom(data.get(0), requesterId) : null;
            case "playlist" ->
            {
                JsonNode tracks = data.path("tracks");
                yield tracks.isArray() && !tracks.isEmpty() ? trackFrom(tracks.get(0), requesterId) : null;
            }
            default -> null;
        };

        if ("error".equals(loadType))
        {
            return CompletableFuture.completedFuture(
                    PlayResult.loadFailed(data.path("message").asText("unknown error")));
        }
        if (track == null)
        {
            return CompletableFuture.completedFuture(PlayResult.noMatches());
        }

        long maxSeconds = bot.getConfig().getMaxSeconds();
        if (maxSeconds > 0 && Math.round(track.length() / 1000.0) > maxSeconds)
        {
            return CompletableFuture.completedFuture(PlayResult.tooLong(track.title()));
        }

        LavalinkGuildPlayer player = playerFor(guild.getIdLong());
        if (player.getCurrent() == null)
        {
            return startTrack(guild, player, track).thenApply(v -> PlayResult.playingNow(track.title()));
        }
        else
        {
            int position = player.enqueue(track);
            return CompletableFuture.completedFuture(PlayResult.queued(track.title(), position));
        }
    }

    private LavalinkTrack trackFrom(JsonNode trackJson, long requesterId)
    {
        JsonNode info = trackJson.path("info");
        return new LavalinkTrack(
                trackJson.path("encoded").asText(null),
                info.path("identifier").asText(null),
                info.path("title").asText("Unknown title"),
                info.path("author").asText(""),
                info.hasNonNull("uri") ? info.path("uri").asText() : null,
                info.path("length").asLong(0L),
                info.path("isStream").asBoolean(false),
                requesterId);
    }

    private CompletableFuture<Void> startTrack(Guild guild, LavalinkGuildPlayer player, LavalinkTrack track)
    {
        player.setCurrent(track);
        int volume = bot.getSettingsManager().getSettings(guild).getVolume();
        player.setVolume(volume);
        player.setPaused(false);

        ObjectNode body = newPlayerUpdateBody();
        body.putObject("track").put("encoded", track.encoded());
        body.put("volume", volume);
        body.put("paused", false);

        return rest.updatePlayer(guild.getIdLong(), body, false).thenAccept(response -> { }).exceptionally(ex ->
        {
            LOG.warn("Lavalink: failed to start track on guild {}: {}", guild.getIdLong(), ex.toString());
            return null;
        });
    }

    /** Sets pause state. Returns the title of the track this affected, or {@code null}. */
    public String setPaused(Guild guild, boolean paused)
    {
        LavalinkGuildPlayer player = players.get(guild.getIdLong());
        if (player == null || player.getCurrent() == null)
        {
            return null;
        }
        player.setPaused(paused);
        ObjectNode body = newPlayerUpdateBody();
        body.put("paused", paused);
        rest.updatePlayer(guild.getIdLong(), body, true).exceptionally(ex ->
        {
            LOG.warn("Lavalink: failed to set paused={} on guild {}: {}", paused, guild.getIdLong(), ex.toString());
            return null;
        });
        return player.getCurrent().title();
    }

    /** Absolute volume set. Returns old/new, or {@code null} if out of range. */
    public int[] setVolume(Guild guild, int volume)
    {
        if (volume < 0 || volume > 150)
        {
            return null;
        }
        LavalinkGuildPlayer player = playerFor(guild.getIdLong());
        int old = player.getVolume();
        player.setVolume(volume);
        bot.getSettingsManager().getSettings(guild).setVolume(volume);

        ObjectNode body = newPlayerUpdateBody();
        body.put("volume", volume);
        rest.updatePlayer(guild.getIdLong(), body, true).exceptionally(ex ->
        {
            LOG.warn("Lavalink: failed to set volume on guild {}: {}", guild.getIdLong(), ex.toString());
            return null;
        });
        return new int[] { old, volume };
    }

    /** Stops the current track and clears the queue; does not disconnect from voice. */
    public void stop(Guild guild)
    {
        LavalinkGuildPlayer player = players.get(guild.getIdLong());
        if (player == null)
        {
            return;
        }
        player.clearQueue();
        player.setCurrent(null);

        ObjectNode body = newPlayerUpdateBody();
        body.putObject("track").putNull("encoded");
        rest.updatePlayer(guild.getIdLong(), body, false).exceptionally(ex ->
        {
            LOG.warn("Lavalink: failed to stop playback on guild {}: {}", guild.getIdLong(), ex.toString());
            return null;
        });
    }

    /** Immediately skips to the next queued track, or stops if the queue is empty. Returns the skipped title. */
    public String skip(Guild guild)
    {
        LavalinkGuildPlayer player = players.get(guild.getIdLong());
        if (player == null || player.getCurrent() == null)
        {
            return null;
        }
        String skippedTitle = player.getCurrent().title();
        advance(guild, player);
        return skippedTitle;
    }

    private void advance(Guild guild, LavalinkGuildPlayer player)
    {
        LavalinkTrack next = player.pollNext();
        player.setCurrent(next);

        ObjectNode body = newPlayerUpdateBody();
        if (next == null)
        {
            body.putObject("track").putNull("encoded");
        }
        else
        {
            body.putObject("track").put("encoded", next.encoded());
        }
        rest.updatePlayer(guild.getIdLong(), body, false).exceptionally(ex ->
        {
            LOG.warn("Lavalink: failed to advance playback on guild {}: {}", guild.getIdLong(), ex.toString());
            return null;
        });

        if (next == null && !bot.getConfig().getStay())
        {
            leave(guild);
        }
    }

    // ==================== Node event handling ====================

    private final class SocketListener implements LavalinkSocketClient.Listener
    {
        @Override
        public void onReady(String sessionId, boolean resumed)
        {
            rest.setSessionId(sessionId);
        }

        @Override
        public void onEvent(long guildId, JsonNode payload)
        {
            String type = payload.path("type").asText("");
            if ("TrackEndEvent".equals(type))
            {
                String reason = payload.path("reason").asText("");
                // "replaced" means something else (our own stop/skip) already changed the track;
                // advancing again here would skip an extra track.
                if (!"replaced".equals(reason))
                {
                    Guild guild = bot.getJDA() == null ? null : bot.getJDA().getGuildById(guildId);
                    LavalinkGuildPlayer player = players.get(guildId);
                    if (guild != null && player != null)
                    {
                        advance(guild, player);
                    }
                }
            }
            else if ("TrackExceptionEvent".equals(type))
            {
                LOG.warn("Lavalink node {}: track exception in guild {}: {}",
                        node.describe(), guildId, payload.path("exception").path("message").asText(""));
            }
            else if ("TrackStuckEvent".equals(type))
            {
                LOG.warn("Lavalink node {}: track stuck in guild {} (threshold {}ms)",
                        node.describe(), guildId, payload.path("thresholdMs").asLong(0));
            }
        }

        @Override
        public void onPlayerUpdate(long guildId, JsonNode payload)
        {
            // Position/ping telemetry; stage 1 has nowhere to surface this yet (dashboard/web
            // diagnostics parity is stage 2).
        }

        @Override
        public void onSocketClosed(String reason)
        {
            // The socket client logs this; nothing further to do in stage 1 beyond not crashing.
            // A future stage adds reconnect-with-resume using the last known sessionId.
        }
    }
}
