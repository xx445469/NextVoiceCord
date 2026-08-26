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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.GCMonitor;
import com.jagrosh.jmusicbot.audio.PerformanceMetrics.MetricsSnapshot;
import com.jagrosh.jmusicbot.audio.PerformanceMetrics.StuckEvent;
import com.jagrosh.jmusicbot.audio.PerformanceMetrics.StutterEvent;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor.HealthSample;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor.HealthSnapshot;
import com.jagrosh.jmusicbot.audio.TrackLoadingMonitor;
import com.jagrosh.jmusicbot.audio.TrackLoadingMonitor.LoadEvent;
import com.jagrosh.jmusicbot.audio.TrackLoadingMonitor.LoadResult;
import com.jagrosh.jmusicbot.audio.TrackLoadingMonitor.LoadingSnapshot;
import com.jagrosh.jmusicbot.audio.TrackLoadingMonitor.SourceStats;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.jagrosh.jmusicbot.config.loader.ConfigLoader;
import com.jagrosh.jmusicbot.config.model.ConfigOption;
import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;

import com.sun.net.httpserver.HttpExchange;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;

import net.dv8tion.jda.api.entities.Guild;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the JSON payload for every {@code GET} endpoint the web panel serves.
 *
 * <p>{@link WebPanel} owns HTTP concerns (auth, routing, framing); this owns turning live bot
 * state into the shapes {@code docs/web-api.md} promises. The split matters because the
 * front-end is being written against that document at the same time this class is, so the field
 * names and nesting here are a contract, not a suggestion — they are copied from the doc, not
 * invented to look reasonable.
 *
 * <p>Every method here runs on a request thread against state a background thread can be
 * mutating at the same time, and some of that state (a monitor that has not started, a JDA that
 * has not connected yet) is simply absent during normal startup. None of that is this class's
 * business to fail loudly over: a dashboard that shows "no data yet" is doing its job, and one
 * that 500s because a guild list was empty is not. Every public method therefore catches broadly
 * and falls back to the documented empty shape rather than letting an exception reach
 * {@link WebPanel}.
 *
 * @author adan (xx445469)
 */
final class WebData
{
    private static final Logger LOG = LoggerFactory.getLogger(WebData.class);

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Compact HOCON for a nested config block shown read-only in the editor. */
    private static final ConfigRenderOptions RENDER_OPTIONS = ConfigRenderOptions.defaults()
            .setOriginComments(false).setComments(false).setFormatted(false).setJson(false);

    /**
     * Mirrors {@link WebWrites}'s own never-writable set. Duplicated rather than shared because
     * the two classes read it for different reasons — one to refuse a write, one to label a
     * read — and a private field in one is not something the other should reach into.
     */
    private static final Set<String> READ_ONLY_KEYS = Set.of(
            "discord.token",
            "web.bindAddress",
            "web.allowConfigEdit");

    /**
     * Keys left out of the listing entirely, rather than shown read-only.
     *
     * <p>{@code lavalink.nodes} is a list of objects, one of which ({@code password}) is a
     * credential. The rest of this panel redacts secrets per-key via {@link WebSecrets}, which
     * assumes one key holds one value; a list of node objects doesn't fit that shape without a
     * per-node-aware editor, which is stage-2 work (the Lavalink web/GUI diagnostics called out
     * as not yet built). Omitting it here is the safe default until that exists: config.txt is
     * still the way to set it, same as it is today.
     */
    private static final Set<String> HIDDEN_KEYS = Set.of("lavalink.nodes");

    /** Keeps a long-running bot's recent-loads list from becoming an unbounded response body. */
    private static final int RECENT_LOADS_LIMIT = 50;

    private static final int MAX_WINDOW_SECONDS = 3600;

    /**
     * Every label the page's own chrome needs, resolved fresh per request by
     * {@link #labels(HttpExchange)}.
     *
     * <p>Almost all of this is {@code gui.*} — the same keys the desktop window's own panels
     * already use, so the web panel says exactly what the desktop window says rather than
     * maintaining a second, inevitably-diverging translation of the same eight views. Only
     * concepts the desktop window has no equivalent for — the polling status dot, the playback
     * buttons a Swing dashboard never needed, empty states unique to a browser tab that can be
     * closed and reopened mid-download — live under {@code web.*} instead, in {@code EN.json}
     * only; the other eleven languages pick those up from a separate translation pass.
     */
    private static final String[] PAGE_KEYS = {
            // Sidebar navigation and section labels.
            "gui.nav.overview", "gui.nav.console", "gui.nav.servers", "gui.nav.performance",
            "gui.nav.system", "gui.nav.sources", "gui.nav.preferences", "gui.nav.botConfig",
            "gui.section.diagnostics", "gui.section.configure",

            // Overview / Servers.
            "gui.overview.connecting", "gui.overview.queued", "gui.overview.volume",
            "gui.overview.memoryOf", "gui.status.title", "gui.status.subtitle",

            // Performance.
            "gui.performance.title", "gui.performance.subtitle", "gui.performance.windowLabel",
            "gui.performance.guild", "gui.performance.colStatus", "gui.performance.colMissRate",
            "gui.performance.colStutters", "gui.performance.colStuck", "gui.performance.statFrames",
            "gui.performance.statMissedFrames", "gui.performance.cardEvents", "gui.performance.colTime",
            "gui.performance.colType", "gui.performance.colDetails", "gui.performance.statusIdle",
            "gui.performance.statusPlaying", "gui.performance.statusPaused",
            "gui.performance.eventTypeStutter", "gui.performance.eventTypeStuck", "gui.performance.eventTypeGc",

            // System.
            "gui.system.title", "gui.system.subtitle", "gui.system.window", "gui.system.cpuUsage",
            "gui.system.heapMemory", "gui.system.threads", "gui.system.gcCount", "gui.system.gcTime",

            // Sources.
            "gui.sources.title", "gui.sources.subtitle", "gui.sources.windowLabel",
            "gui.sources.colSource", "gui.sources.failed", "gui.sources.successRate",
            "gui.sources.colSuccessPercent", "gui.sources.colAvgMs", "gui.sources.colP95Ms",
            "gui.sources.colTime", "gui.sources.colResult", "gui.sources.colDuration",
            "gui.sources.recentLoads",

            // Preferences / appearance.
            "gui.language.label", "gui.language.hint", "gui.appearance.theme", "gui.appearance.fontSize",

            // Bot config.
            "gui.config.title", "gui.config.subtitle", "gui.config.loading", "gui.config.saveChanges",
            "gui.config.commands", "gui.config.presence", "gui.config.voice", "gui.config.playback",
            "gui.config.uiEmojis",

            // Web-only: the polling status dot.
            "web.status.connecting", "web.status.live", "web.status.disconnected",
            "web.status.unauthorised", "web.status.error",

            // Web-only: Servers view search.
            "web.servers.filterPlaceholder", "web.servers.count", "web.servers.noMatch",

            // Web-only: Console ring-buffer note.
            "web.console.dropped",

            // Web-only: Performance empty states.
            "web.performance.empty", "web.performance.noGuildsActive", "web.performance.noEvents",

            // Web-only: System empty states and stats the desktop page does not break out.
            "web.system.empty", "web.system.noSamples", "web.system.cpuSystem",
            "web.system.driftAvg", "web.system.driftMax", "web.system.cpuSparkLabel",
            "web.system.heapSparkLabel",

            // Web-only: Sources stats/columns the desktop page does not break out, and empty states.
            "web.sources.loaded", "web.sources.noMatches", "web.sources.colQuery",
            "web.sources.noActivity", "web.sources.noLookups",

            // Web-only: guild card member count.
            "web.guild.members",

            // Web-only: playback controls (the desktop dashboard is read-only; only the panel
            // needs button captions for them).
            "web.control.pause", "web.control.resume", "web.control.skip", "web.control.volume",

            // Web-only: Preferences page furniture.
            "web.preferences.hint", "web.preferences.panelLanguage", "web.preferences.panelLanguageHint",
            "web.preferences.leaveUnchanged", "web.preferences.save",

            // Web-only: Bot config page furniture.
            "web.config.loadError", "web.config.networkError", "web.config.editingOff",
            "web.config.editInFileOnly", "web.config.secretSet", "web.config.secretNotSet",
            "web.config.notEditable",

            // Web-only: shared form-result feedback.
            "web.result.nothingChanged", "web.result.saving", "web.result.networkError",
            "web.result.requestFailed", "web.result.refusedPrefix",

            // Web-only: footer.
            "web.footer",
    };

    private final Bot bot;

    WebData(Bot bot)
    {
        this.bot = bot;
    }

    // ==================== /api/status ====================

    Object status(HttpExchange exchange)
    {
        try
        {
            Language language = languageFor(exchange);
            List<Map<String, Object>> guilds = new ArrayList<>();
            long listeners = 0;

            if (bot.getJDA() != null)
            {
                for (Guild guild : bot.getJDA().getGuilds())
                {
                    AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                    boolean playing = handler != null && handler.isMusicPlaying(bot.getJDA());

                    var entry = new LinkedHashMap<String, Object>();
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

            // Playing guilds first: on a bot in many servers, those are the only rows worth
            // scrolling to.
            guilds.sort((a, b) -> Boolean.compare(
                    Boolean.TRUE.equals(b.get("playing")), Boolean.TRUE.equals(a.get("playing"))));

            var runtime = Runtime.getRuntime();
            // Labels travel with the data rather than being duplicated in the page, so the panel
            // and the bot cannot drift into saying different things in the same language.
            var labels = new LinkedHashMap<String, String>();
            for (String key : new String[] { "playingNow", "servers", "listeners", "uptime",
                                             "memory", "nothingPlaying", "connecting",
                                             "playing", "paused", "subtitle" })
            {
                labels.put(key, bot.getLanguages().get(language, "gui.overview." + key));
            }

            var payload = new LinkedHashMap<String, Object>();
            payload.put("labels", labels);
            payload.put("language", language.name());
            payload.put("version", OtherUtil.getCurrentVersion());
            payload.put("connected", bot.getJDA() != null);
            payload.put("guilds", guilds);
            payload.put("guildCount", guilds.size());
            payload.put("playingCount", guilds.stream().filter(g -> Boolean.TRUE.equals(g.get("playing"))).count());
            payload.put("listeners", listeners);
            payload.put("memoryUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1048576);
            payload.put("memoryMaxMb", runtime.maxMemory() / 1048576);
            payload.put("uptime", formatUptime());
            return payload;
        }
        catch (RuntimeException ex)
        {
            LOG.warn("Web panel: could not build /api/status: {}", ex.toString());
            var empty = new LinkedHashMap<String, Object>();
            empty.put("labels", Map.of());
            empty.put("language", "EN");
            empty.put("version", "");
            empty.put("connected", false);
            empty.put("guilds", List.of());
            empty.put("guildCount", 0);
            empty.put("playingCount", 0);
            empty.put("listeners", 0);
            empty.put("memoryUsedMb", 0);
            empty.put("memoryMaxMb", 0);
            empty.put("uptime", "");
            return empty;
        }
    }

    // ==================== /api/labels ====================

    /**
     * Every string the page's own interface needs, in the caller's language, plus the list of
     * languages there are strings for at all.
     *
     * <p>The page has no translation data of its own — it cannot, there is nothing running in
     * the browser that could read a classpath resource — so it fetches this once at startup and
     * again whenever the viewer picks a different display language, then renders from what comes
     * back rather than doing any lookup itself.
     */
    Object labels(HttpExchange exchange)
    {
        try
        {
            Language language = languageFor(exchange);

            var labels = new LinkedHashMap<String, String>();
            for (String key : PAGE_KEYS)
            {
                labels.put(key, bot.getLanguages().get(language, key));
            }

            List<Map<String, String>> available = new ArrayList<>();
            for (Language lang : bot.getLanguages().getAvailableLanguages())
            {
                var entry = new LinkedHashMap<String, String>();
                entry.put("code", lang.name());
                entry.put("native", lang.getNativeName());
                entry.put("english", lang.getEnglishName());
                available.add(entry);
            }

            var payload = new LinkedHashMap<String, Object>();
            payload.put("language", language.name());
            payload.put("available", available);
            payload.put("labels", labels);
            return payload;
        }
        catch (RuntimeException ex)
        {
            LOG.warn("Web panel: could not build /api/labels: {}", ex.toString());
            return Map.of("language", "EN", "available", List.of(), "labels", Map.of());
        }
    }

    /** Uptime as a short human string, computed here so Bot keeps exposing only the instant. */
    private String formatUptime()
    {
        long seconds = java.time.Duration.between(bot.getStartTime(), Instant.now()).toSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0)
        {
            return days + "d " + hours + "h";
        }
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }

    // ==================== /api/console ====================

    Object console(HttpExchange exchange)
    {
        try
        {
            long since = Math.max(0, longParam(exchange, "since", 0));
            int limit = Math.min(2000, Math.max(0, intParam(exchange, "limit", 500)));

            LogBuffer buffer = LogBuffer.getInstance();
            List<Map<String, Object>> lines = new ArrayList<>();
            for (LogBuffer.Entry entry : buffer.since(since, limit))
            {
                var line = new LinkedHashMap<String, Object>();
                line.put("seq", entry.sequence());
                line.put("text", entry.text());
                line.put("level", entry.level().name());
                lines.add(line);
            }

            var payload = new LinkedHashMap<String, Object>();
            payload.put("lines", lines);
            payload.put("latest", buffer.highestSequence());
            payload.put("dropped", buffer.droppedCount());
            return payload;
        }
        catch (RuntimeException ex)
        {
            LOG.warn("Web panel: could not build /api/console: {}", ex.toString());
            return Map.of("lines", List.of(), "latest", 0, "dropped", 0);
        }
    }

    // ==================== /api/performance ====================

    Object performance(HttpExchange exchange)
    {
        try
        {
            int windowSeconds = clampWindow(intParam(exchange, "window", 30));
            long cutoff = System.currentTimeMillis() - windowSeconds * 1000L;

            List<Map<String, Object>> guildList = new ArrayList<>();
            List<RawEvent> rawEvents = new ArrayList<>();
            long totalSent = 0, totalMissed = 0;

            if (bot.getJDA() != null)
            {
                for (Guild guild : bot.getJDA().getGuilds())
                {
                    if (!guild.getAudioManager().isConnected())
                    {
                        continue;
                    }
                    var sending = guild.getAudioManager().getSendingHandler();
                    if (!(sending instanceof AudioHandler handler))
                    {
                        continue;
                    }

                    MetricsSnapshot snap = handler.getPerformanceMetrics().getSnapshot(windowSeconds);

                    var g = new LinkedHashMap<String, Object>();
                    g.put("id", guild.getId());
                    g.put("name", guild.getName());
                    g.put("state", stateOf(handler));
                    g.put("framesSent", snap.totalFramesProvided());
                    g.put("framesMissed", snap.totalFramesMissed());
                    g.put("missRate", round2(snap.missRatePercent()));
                    g.put("stutters", snap.stutterCount());
                    g.put("stuck", snap.stuckCount());
                    guildList.add(g);

                    totalSent += snap.totalFramesProvided();
                    totalMissed += snap.totalFramesMissed();

                    for (StutterEvent e : snap.stutterEvents())
                    {
                        if (e.timestamp() >= cutoff)
                        {
                            rawEvents.add(new RawEvent(e.timestamp(), guild.getName(), "stutter",
                                    e.missedFrames() + " frame(s) missed (" + e.durationMs() + "ms)"));
                        }
                    }
                    for (StuckEvent e : snap.stuckEvents())
                    {
                        String detail = e.trackTitle() != null
                                ? e.trackTitle() + " stuck for " + e.thresholdMs() + "ms"
                                : "Stuck for " + e.thresholdMs() + "ms";
                        rawEvents.add(new RawEvent(e.timestamp(), guild.getName(), "stuck", detail));
                    }
                }
            }

            boolean available = !guildList.isEmpty();
            if (!available)
            {
                var payload = new LinkedHashMap<String, Object>();
                payload.put("available", false);
                payload.put("guilds", List.of());
                payload.put("events", List.of());
                payload.put("totals", Map.of("framesSent", 0, "framesMissed", 0, "missRate", 0.0));
                return payload;
            }

            // GC is process-wide, not per guild: collected once here rather than once per
            // guild's snapshot, which would repeat the same events once for every playing guild.
            for (GCMonitor.GCEvent e : GCMonitor.getInstance().getRecentEvents(windowSeconds))
            {
                rawEvents.add(new RawEvent(e.timestamp(), "", "gc",
                        e.collectorName() + " (" + e.durationMs() + "ms)"));
            }
            rawEvents.sort(Comparator.comparingLong(RawEvent::timestamp));

            List<Map<String, Object>> events = new ArrayList<>();
            for (RawEvent e : rawEvents)
            {
                var entry = new LinkedHashMap<String, Object>();
                entry.put("time", TIME_FMT.format(Instant.ofEpochMilli(e.timestamp())));
                entry.put("guild", e.guild());
                entry.put("type", e.type());
                entry.put("detail", e.detail());
                events.add(entry);
            }

            long totalFrames = totalSent + totalMissed;
            var totals = new LinkedHashMap<String, Object>();
            totals.put("framesSent", totalSent);
            totals.put("framesMissed", totalMissed);
            totals.put("missRate", totalFrames > 0 ? round2((totalMissed * 100.0) / totalFrames) : 0.0);

            var payload = new LinkedHashMap<String, Object>();
            payload.put("available", true);
            payload.put("guilds", guildList);
            payload.put("events", events);
            payload.put("totals", totals);
            return payload;
        }
        catch (RuntimeException ex)
        {
            LOG.warn("Web panel: could not build /api/performance: {}", ex.toString());
            return Map.of("available", false, "guilds", List.of(), "events", List.of(),
                          "totals", Map.of("framesSent", 0, "framesMissed", 0, "missRate", 0.0));
        }
    }

    private static String stateOf(AudioHandler handler)
    {
        var track = handler.getPlayer().getPlayingTrack();
        if (track == null)
        {
            return "idle";
        }
        return handler.getPlayer().isPaused() ? "paused" : "playing";
    }

    /** One performance/GC event before it is sorted and rendered. */
    private record RawEvent(long timestamp, String guild, String type, String detail) { }

    // ==================== /api/system ====================

    Object system(HttpExchange exchange)
    {
        try
        {
            int windowSeconds = clampWindow(intParam(exchange, "window", 60));
            SystemHealthMonitor monitor = SystemHealthMonitor.getInstance();
            HealthSnapshot snap = monitor.getSnapshot(windowSeconds);

            if (snap.isEmpty())
            {
                var payload = new LinkedHashMap<String, Object>();
                payload.put("available", false);
                payload.put("samples", List.of());
                return payload;
            }

            boolean cpuAvailable = monitor.isCpuMetricsAvailable();
            long now = System.currentTimeMillis();

            List<Map<String, Object>> samples = new ArrayList<>();
            for (HealthSample s : monitor.getRecentSamples(windowSeconds))
            {
                var entry = new LinkedHashMap<String, Object>();
                entry.put("t", Math.round((s.timestamp() - now) / 1000.0));
                entry.put("cpu", cpuAvailable ? round2(s.processCpuPercent()) : null);
                entry.put("heapMb", s.heapUsedBytes() / 1048576);
                samples.add(entry);
            }

            GCMonitor.GCEvent[] gcEvents = GCMonitor.getInstance().getRecentEvents(windowSeconds);
            long gcTimeMs = 0;
            for (GCMonitor.GCEvent e : gcEvents)
            {
                gcTimeMs += e.durationMs();
            }

            var payload = new LinkedHashMap<String, Object>();
            payload.put("available", true);
            payload.put("cpuProcess", cpuAvailable ? round2(snap.currentProcessCpu()) : null);
            payload.put("cpuSystem", cpuAvailable ? round2(snap.currentSystemCpu()) : null);
            payload.put("heapUsedMb", snap.heapUsedBytes() / 1048576);
            payload.put("heapMaxMb", snap.heapMaxBytes() / 1048576);
            payload.put("threads", snap.currentThreadCount());
            payload.put("gcCount", gcEvents.length);
            payload.put("gcTimeMs", gcTimeMs);
            payload.put("driftAvgMs", round2(monitor.getAvgDriftMs()));
            payload.put("driftMaxMs", monitor.getMaxDriftMs());
            payload.put("samples", samples);
            return payload;
        }
        catch (RuntimeException ex)
        {
            LOG.warn("Web panel: could not build /api/system: {}", ex.toString());
            return Map.of("available", false, "samples", List.of());
        }
    }

    // ==================== /api/sources ====================

    Object sources(HttpExchange exchange)
    {
        try
        {
            int windowSeconds = clampWindow(intParam(exchange, "window", 60));
            TrackLoadingMonitor monitor = bot.getTrackLoadingMonitor();

            // Null when the GUI (and so monitoring) is disabled with --nogui. Same "nothing to
            // report yet" shape as a monitor that exists but has not seen a load.
            if (monitor == null)
            {
                var payload = new LinkedHashMap<String, Object>();
                payload.put("sources", List.of());
                payload.put("recent", List.of());
                payload.put("totals", Map.of("loaded", 0, "failed", 0, "noMatches", 0, "successPercent", 100.0));
                return payload;
            }

            LoadingSnapshot snap = monitor.getSnapshot(windowSeconds);

            // Per-source figures are all-time (the only thing SourceStats tracks), matching what
            // the desktop panel's source table shows regardless of the selected window.
            List<Map<String, Object>> sourceList = new ArrayList<>();
            for (String name : snap.trackedSources())
            {
                SourceStats stats = monitor.getSourceStats(name);
                if (stats == null)
                {
                    continue;
                }

                long loaded = stats.getSuccessfulLoads();
                long failed = stats.getFailedLoads();
                long noMatches = Math.max(0, stats.getTotalLoads() - loaded - failed);

                var s = new LinkedHashMap<String, Object>();
                s.put("name", name);
                s.put("loaded", loaded);
                s.put("failed", failed);
                s.put("noMatches", noMatches);
                s.put("successPercent", round2(stats.getSuccessRate()));
                s.put("avgMs", Math.round(stats.getAverageDurationMs()));
                s.put("p95Ms", Math.round(stats.getP95DurationMs()));
                sourceList.add(s);
            }

            List<Map<String, Object>> recent = new ArrayList<>();
            LoadEvent[] events = snap.recentEvents();
            int start = Math.max(0, events.length - RECENT_LOADS_LIMIT);
            for (int i = events.length - 1; i >= start; i--)
            {
                LoadEvent e = events[i];
                var r = new LinkedHashMap<String, Object>();
                r.put("time", TIME_FMT.format(Instant.ofEpochMilli(e.timestamp())));
                r.put("source", e.source());
                r.put("result", resultLabel(e.result()));
                r.put("ms", e.loadDurationMs());
                r.put("query", e.query());
                recent.add(r);
            }

            var totals = new LinkedHashMap<String, Object>();
            totals.put("loaded", snap.successInWindow());
            totals.put("failed", snap.failedInWindow());
            totals.put("noMatches", snap.noMatchInWindow());
            totals.put("successPercent", round2(snap.successRatePercent()));

            var payload = new LinkedHashMap<String, Object>();
            payload.put("sources", sourceList);
            payload.put("recent", recent);
            payload.put("totals", totals);
            return payload;
        }
        catch (RuntimeException ex)
        {
            LOG.warn("Web panel: could not build /api/sources: {}", ex.toString());
            return Map.of("sources", List.of(), "recent", List.of(),
                          "totals", Map.of("loaded", 0, "failed", 0, "noMatches", 0, "successPercent", 100.0));
        }
    }

    /** Track and playlist loads read as one outcome on the page; only the failure modes differ. */
    private static String resultLabel(LoadResult result)
    {
        return switch (result)
        {
            case TRACK_LOADED, PLAYLIST_LOADED -> "LOADED";
            case NO_MATCHES -> "NO_MATCHES";
            case LOAD_FAILED -> "LOAD_FAILED";
        };
    }

    // ==================== /api/config ====================

    Object config()
    {
        try
        {
            Config merged = ConfigLoader.loadMergedConfig(ConfigIO.getConfigPath());
            boolean editable = bot.getConfig().isWebConfigEditAllowed();

            // LinkedHashMap so sections appear in the order their first option is declared in
            // ConfigOption, which already groups related settings together.
            Map<String, List<Map<String, Object>>> bySection = new LinkedHashMap<>();
            for (ConfigOption option : ConfigOption.values())
            {
                String key = option.getKey();
                if (HIDDEN_KEYS.contains(key))
                {
                    continue;
                }
                String display = WebSecrets.forDisplay(key, readValue(option, merged));

                var entry = new LinkedHashMap<String, Object>();
                entry.put("key", key);
                entry.put("leaf", leafOf(key));
                entry.put("type", option.getType().name());
                entry.put("value", display);
                entry.put("secret", WebSecrets.isSecret(key));
                entry.put("description", option.getDescription());
                if (READ_ONLY_KEYS.contains(key))
                {
                    entry.put("writable", false);
                }

                bySection.computeIfAbsent(sectionOf(key), s -> new ArrayList<>()).add(entry);
            }

            List<Map<String, Object>> sections = new ArrayList<>();
            for (var e : bySection.entrySet())
            {
                var section = new LinkedHashMap<String, Object>();
                section.put("name", e.getKey());
                section.put("options", e.getValue());
                sections.add(section);
            }

            var payload = new LinkedHashMap<String, Object>();
            payload.put("editable", editable);
            payload.put("sections", sections);
            return payload;
        }
        catch (RuntimeException ex)
        {
            LOG.warn("Web panel: could not build /api/config: {}", ex.toString());
            return Map.of("editable", false, "sections", List.of());
        }
    }

    /**
     * The current value of one option as a display string, or {@code ""} if it cannot be read.
     *
     * <p>{@code ""} rather than the exception's message: for a key that failed to parse because
     * of what it contains, the message can quote the very value {@link WebSecrets} exists to
     * keep out of this response.
     */
    private static String readValue(ConfigOption option, Config config)
    {
        try
        {
            // getValue() already swallows a missing path or a type mismatch and returns null;
            // deprecated as a general-purpose accessor, but exactly the "any option, generically"
            // shape this method needs.
            Object raw = option.getValue(config);
            if (raw == null)
            {
                return "";
            }
            return switch (option.getType())
            {
                case STRING_LIST -> ((List<?>) raw).stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(", "));
                case CONFIG -> ((Config) raw).root().render(RENDER_OPTIONS);
                default -> String.valueOf(raw);
            };
        }
        catch (RuntimeException ex)
        {
            return "";
        }
    }

    private static String sectionOf(String key)
    {
        int at = key.lastIndexOf('.');
        return at < 0 ? key : key.substring(0, at);
    }

    private static String leafOf(String key)
    {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    // ==================== Shared request/format helpers ====================

    /**
     * Picks the language for one request, going through {@link WebPanel} so every endpoint
     * agrees on the same rules (explicit {@code ?lang=}, then Accept-Language, then the bot's
     * default) rather than this class guessing independently.
     *
     * <p>{@link WebPanel} does not exist yet for the sliver of time between its HTTP server
     * starting and {@code Bot.setWebPanel} running, so this falls back to the bot's own
     * configured language rather than risk a null dereference on that race.
     */
    private Language languageFor(HttpExchange exchange)
    {
        WebPanel panel = bot.getWebPanel();
        return panel != null ? panel.languageFor(exchange) : bot.getConfig().getGuiLanguage();
    }

    private static int intParam(HttpExchange exchange, String name, int defaultValue)
    {
        String raw = WebPanel.queryParam(exchange, name);
        if (raw == null)
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException ex)
        {
            return defaultValue;
        }
    }

    private static long longParam(HttpExchange exchange, String name, long defaultValue)
    {
        String raw = WebPanel.queryParam(exchange, name);
        if (raw == null)
        {
            return defaultValue;
        }
        try
        {
            return Long.parseLong(raw.trim());
        }
        catch (NumberFormatException ex)
        {
            return defaultValue;
        }
    }

    /** Keeps a client-supplied window from asking a monitor to scan hours of retained samples. */
    private static int clampWindow(int seconds)
    {
        return Math.max(1, Math.min(MAX_WINDOW_SECONDS, seconds));
    }

    private static double round2(double value)
    {
        return Math.round(value * 100.0) / 100.0;
    }
}
