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
package com.jagrosh.jmusicbot.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.dv8tion.jda.api.entities.Guild;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The web panel: a status dashboard for the bot, served over HTTP.
 *
 * <p>Built on the JDK's own {@code HttpServer} rather than a framework. The panel serves one
 * page and two endpoints; pulling in a web stack to do that would add more to the 65 MB jar
 * than the feature is worth, and every dependency here is one more thing that has to keep
 * working for the bot to start.
 *
 * <p>Read-only. Playback control from a browser is a different proposition to reading status:
 * it needs to answer "who did that?" in a way a shared token cannot, and Discord already
 * offers controls that know who pressed them. The panel answers "what is this bot doing right
 * now", which is the part Discord answers badly.
 *
 * @author adan (xx445469)
 */
public final class WebPanel
{
    private static final Logger LOG = LoggerFactory.getLogger(WebPanel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Threads for serving. Small: this is a status page, not a service. */
    private static final int THREADS = 2;

    private final Bot bot;
    private final int port;
    private final WebAuth auth;
    private HttpServer server;

    public WebPanel(Bot bot, int port)
    {
        this.bot = bot;
        this.port = port;
        this.auth = new WebAuth();
    }

    /**
     * Starts serving.
     *
     * <p>A failure to bind is reported and swallowed. The panel is a convenience; a port
     * already in use should not stop the bot from playing music.
     */
    public void start()
    {
        try
        {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", this::handlePage);
            server.createContext("/api/status", this::handleStatus);
            server.setExecutor(Executors.newFixedThreadPool(THREADS));
            server.start();

            LOG.info("");
            LOG.info("  Web panel: http://localhost:{}/?token={}", port, auth.getToken());
            LOG.info("  The token changes every restart and is not stored anywhere.");
            LOG.info("");
        }
        catch (IOException ex)
        {
            LOG.warn("Could not start the web panel on port {}: {}. The bot continues without it.",
                     port, ex.getMessage());
        }
    }

    /** Stops serving, if started. */
    public void stop()
    {
        if (server != null)
        {
            server.stop(0);
            server = null;
        }
    }

    private void handlePage(HttpExchange exchange) throws IOException
    {
        if (!authorised(exchange))
        {
            return;
        }
        byte[] body = readResource("web/panel.html");
        if (body == null)
        {
            send(exchange, 500, "text/plain; charset=utf-8", "Panel resources are missing from this build.");
            return;
        }
        send(exchange, 200, "text/html; charset=utf-8", body);
    }

    private void handleStatus(HttpExchange exchange) throws IOException
    {
        if (!authorised(exchange))
        {
            return;
        }
        send(exchange, 200, "application/json; charset=utf-8", MAPPER.writeValueAsString(buildStatus()));
    }

    /** The status payload. Deliberately flat — the page renders it directly. */
    private Map<String, Object> buildStatus()
    {
        var guilds = new java.util.ArrayList<Map<String, Object>>();
        long listeners = 0;

        if (bot.getJDA() != null)
        {
            for (Guild guild : bot.getJDA().getGuilds())
            {
                AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                boolean playing = handler != null && handler.isMusicPlaying(bot.getJDA());

                var entry = new java.util.LinkedHashMap<String, Object>();
                entry.put("name", guild.getName());
                entry.put("id", guild.getId());
                entry.put("playing", playing);
                entry.put("members", guild.getMemberCount());

                if (playing)
                {
                    var track = handler.getPlayer().getPlayingTrack();
                    entry.put("track", FormatUtil.getTrackTitle(track));
                    entry.put("author", track.getInfo().author);
                    entry.put("position", TimeUtil.formatTime(track.getPosition()));
                    entry.put("duration", track.getInfo().isStream
                            ? "LIVE"
                            : TimeUtil.formatTime(track.getDuration()));
                    entry.put("progress", track.getInfo().isStream || track.getDuration() <= 0
                            ? 0
                            : (int) (track.getPosition() * 100 / track.getDuration()));
                    entry.put("paused", handler.getPlayer().isPaused());
                    entry.put("volume", handler.getPlayer().getVolume());
                    entry.put("queue", handler.getQueue().size());

                    var channel = guild.getSelfMember().getVoiceState() == null
                            ? null : guild.getSelfMember().getVoiceState().getChannel();
                    if (channel != null)
                    {
                        entry.put("channel", channel.getName());
                        // Minus the bot itself, which is in the channel but not listening.
                        listeners += Math.max(0, channel.getMembers().size() - 1);
                    }
                }
                guilds.add(entry);
            }
        }

        // Sorted so anything playing is at the top: on a bot in many guilds, the active ones
        // are the only rows worth scrolling to.
        guilds.sort((a, b) -> Boolean.compare(
                Boolean.TRUE.equals(b.get("playing")), Boolean.TRUE.equals(a.get("playing"))));

        var runtime = Runtime.getRuntime();
        return Map.of(
                "version", OtherUtil.getCurrentVersion(),
                "guilds", guilds,
                "guildCount", guilds.size(),
                "playingCount", guilds.stream().filter(g -> Boolean.TRUE.equals(g.get("playing"))).count(),
                "listeners", listeners,
                "memoryUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1048576,
                "memoryMaxMb", runtime.maxMemory() / 1048576,
                "uptime", formatUptime());
    }

    /** Uptime as a short human string, computed here so Bot keeps exposing only the instant. */
    private String formatUptime()
    {
        long seconds = java.time.Duration.between(bot.getStartTime(), java.time.Instant.now()).toSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0)
        {
            return days + "d " + hours + "h";
        }
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }

    /**
     * Checks the token, from either the query string or the Authorization header.
     *
     * <p>The query string is what makes the printed link work by pasting; the header is what
     * makes the JSON endpoint usable from a script without putting a credential in a URL that
     * ends up in shell history.
     */
    private boolean authorised(HttpExchange exchange) throws IOException
    {
        String query = exchange.getRequestURI().getQuery();
        String supplied = null;

        if (query != null)
        {
            for (String pair : query.split("&"))
            {
                if (pair.startsWith("token="))
                {
                    supplied = java.net.URLDecoder.decode(pair.substring(6), StandardCharsets.UTF_8);
                }
            }
        }

        if (supplied == null)
        {
            List<String> header = exchange.getRequestHeaders().get("Authorization");
            if (header != null && !header.isEmpty() && header.get(0).startsWith("Bearer "))
            {
                supplied = header.get(0).substring(7);
            }
        }

        if (auth.matches(supplied))
        {
            return true;
        }

        // The same response either way. Distinguishing "no token" from "wrong token" tells an
        // attacker which half of the problem they have solved.
        send(exchange, 401, "text/plain; charset=utf-8",
             "Unauthorized. Append ?token=… using the token printed in the bot's console at startup.");
        return false;
    }

    private static byte[] readResource(String path)
    {
        try (InputStream in = WebPanel.class.getClassLoader().getResourceAsStream(path))
        {
            return in == null ? null : in.readAllBytes();
        }
        catch (IOException ex)
        {
            return null;
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body)
            throws IOException
    {
        send(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException
    {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        // The panel loads no third-party anything, so the strictest policy costs nothing and
        // removes injected-content questions entirely.
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        // Without this the token in the URL is sent to any site the page links to.
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(body);
        }
    }
}
