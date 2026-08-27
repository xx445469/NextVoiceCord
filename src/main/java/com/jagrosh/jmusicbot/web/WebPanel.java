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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.update.SelfUpdater;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The web panel: the desktop window's views, served over HTTP.
 *
 * <p>Built on the JDK's own {@code HttpServer} rather than a framework. Pulling in a web stack
 * would add more to the 65 MB jar than the feature is worth, and every dependency here is one
 * more thing that has to keep working for the bot to start.
 *
 * <h2>What this is trusted with</h2>
 *
 * <p>The panel can read every guild the bot is in, control playback, and — when
 * {@code web.allowConfigEdit} is on — rewrite {@code config.txt}. It speaks plain HTTP and
 * authenticates with a single bearer token, which means anyone who can both reach the port and
 * see the token has all of that. Three things follow, and each is enforced below rather than
 * left to the operator:
 *
 * <ul>
 *   <li>It binds {@code 127.0.0.1} unless told otherwise, so the default install is not on the
 *       network at all.</li>
 *   <li>Writes require the token in an {@code Authorization} header. A query-string token is
 *       accepted for the initial page load, because that is what makes the printed link work by
 *       pasting, but never for anything that changes state — a header cannot be forged by a
 *       page on another site, and a URL can.</li>
 *   <li>Installing a downloaded release — the one endpoint that makes the process replace its
 *       own jar and restart — is held to a stricter bar again: refused outright unless the
 *       panel is bound to loopback or {@code web.allowConfigEdit} is on. See {@link
 *       #handleUpdateInstall} for the rest of what gates it.</li>
 *   <li>Secrets are never sent. Not masked in the browser; not included in the response. See
 *       {@link WebSecrets}.</li>
 * </ul>
 *
 * @author adan (xx445469)
 */
public final class WebPanel
{
    private static final Logger LOG = LoggerFactory.getLogger(WebPanel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Small: this serves one person looking at a dashboard, not the public. */
    private static final int THREADS = 4;

    /** Refuses a body large enough to be an attack rather than a config edit. */
    private static final int MAX_BODY_BYTES = 256 * 1024;

    private final Bot bot;
    private final int port;
    private final WebAuth auth;
    private final WebRateLimit rateLimit = new WebRateLimit();
    private final WebData data;
    private final WebWrites writes;
    private HttpServer server;

    /**
     * Runs the outbound work {@code /api/update-check} triggers — see {@link
     * #handleUpdateCheck}. Kept apart from {@link #server}'s own executor, which is sized for
     * answering local reads instantly, not for a thread that can now sit through
     * {@link SelfUpdater#checkAndStage()}'s full check-then-download: a GitHub round trip, then,
     * once an update exists, a ~68 MB transfer that can run for minutes.
     */
    private final ExecutorService updateCheckExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public WebPanel(Bot bot, int port)
    {
        this.bot = bot;
        this.port = port;
        this.auth = new WebAuth();
        this.data = new WebData(bot);
        this.writes = new WebWrites(bot);
    }

    /**
     * Starts serving.
     *
     * <p>A failure to bind is reported and swallowed. The panel is a convenience; a port
     * already in use should not stop the bot from playing music.
     */
    public void start()
    {
        String bindAddress = bot.getConfig().getWebBindAddress();

        try
        {
            server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);

            server.createContext("/", this::handlePage);
            server.createContext("/api/status", exchange -> serveJson(exchange, data::status));
            server.createContext("/api/labels", exchange -> serveJson(exchange, data::labels));
            server.createContext("/api/console", exchange -> serveJson(exchange, data::console));
            server.createContext("/api/performance", exchange -> serveJson(exchange, data::performance));
            server.createContext("/api/system", exchange -> serveJson(exchange, data::system));
            server.createContext("/api/sources", exchange -> serveJson(exchange, data::sources));
            server.createContext("/api/config", this::handleConfig);
            server.createContext("/api/prefs", this::handlePrefs);
            server.createContext("/api/control", this::handleControl);
            server.createContext("/api/youtube-oauth", this::handleYoutubeOauth);
            server.createContext("/api/update-check", this::handleUpdateCheck);
            server.createContext("/api/update-install", this::handleUpdateInstall);

            server.setExecutor(Executors.newFixedThreadPool(THREADS));
            server.start();

            announce(bindAddress);
        }
        catch (IOException ex)
        {
            LOG.warn("Could not start the web panel on {}:{} — {}. The bot continues without it.",
                     bindAddress, port, ex.getMessage());
        }
    }

    /**
     * Says what was just opened, and to whom.
     *
     * <p>Spelled out rather than left in the config file. Binding to every interface is a
     * reasonable thing to want and an easy thing to forget, and the difference between "only
     * this machine" and "everyone on this network" is not visible from a URL.
     */
    private void announce(String bindAddress)
    {
        boolean everyInterface = "0.0.0.0".equals(bindAddress) || "::".equals(bindAddress);
        String host = everyInterface ? localAddress() : bindAddress;

        LOG.info("");
        LOG.info("  Web panel: http://{}:{}/?token={}", host, port, auth.getToken());
        LOG.info("  The token changes every restart and is not stored anywhere.");

        if (everyInterface)
        {
            LOG.warn("  Listening on every interface: anything on your network can reach this port.");
            LOG.warn("  It is plain HTTP, so the token is visible to that network. Do not forward the port.");
        }
        if (bot.getConfig().isWebConfigEditAllowed())
        {
            LOG.warn("  Config editing is ON: whoever has the token can rewrite config.txt.");
        }
        LOG.info("");
    }

    /** Best-effort LAN address, so the printed link is one another device can actually open. */
    private static String localAddress()
    {
        try
        {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        }
        catch (IOException ex)
        {
            return "localhost";
        }
    }

    /**
     * The panel's URL including its token, or empty if it is not running.
     *
     * <p>Includes the token because the alternative is asking someone to copy it out of a
     * console that has since scrolled — which is the whole reason a button exists.
     */
    public java.util.Optional<String> getUrl()
    {
        if (server == null)
        {
            return java.util.Optional.empty();
        }
        String bind = bot.getConfig().getWebBindAddress();
        String host = "0.0.0.0".equals(bind) || "::".equals(bind) ? "localhost" : bind;
        return java.util.Optional.of("http://" + host + ":" + port + "/?token=" + auth.getToken());
    }

    /** Stops serving, if started. */
    public void stop()
    {
        if (server != null)
        {
            server.stop(0);
            server = null;
        }
        updateCheckExecutor.shutdownNow();
    }

    // ==================== Routing ====================

    private void handlePage(HttpExchange exchange) throws IOException
    {
        if (!authorised(exchange, false))
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

    /** Read endpoints all look the same: authorise, build a payload, send it. */
    private void serveJson(HttpExchange exchange, java.util.function.Function<HttpExchange, Object> builder)
            throws IOException
    {
        if (!authorised(exchange, false))
        {
            return;
        }
        send(exchange, 200, "application/json; charset=utf-8",
             MAPPER.writeValueAsString(builder.apply(exchange)));
    }

    private void handleConfig(HttpExchange exchange) throws IOException
    {
        if ("GET".equals(exchange.getRequestMethod()))
        {
            serveJson(exchange, e -> data.config());
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod()))
        {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed.");
            return;
        }

        if (!authorised(exchange, true))
        {
            return;
        }

        if (!bot.getConfig().isWebConfigEditAllowed())
        {
            // Refused here rather than hidden in the page. A panel that shows the controls and
            // then quietly ignores them is worse than one that says no.
            send(exchange, 403, "application/json; charset=utf-8",
                 MAPPER.writeValueAsString(Map.of(
                         "ok", false,
                         "reason", "disabled",
                         "message", "Config editing is off. Set web.allowConfigEdit = true in config.txt.")));
            return;
        }

        Map<String, String> updates = readStringMap(exchange);
        if (updates == null)
        {
            send(exchange, 400, "text/plain; charset=utf-8", "Malformed request body.");
            return;
        }

        WebWrites.Result result = writes.applyConfig(updates, clientAddress(exchange));
        send(exchange, result.ok() ? 200 : 400, "application/json; charset=utf-8",
             MAPPER.writeValueAsString(result.asMap()));
    }

    private void handlePrefs(HttpExchange exchange) throws IOException
    {
        if (!"POST".equals(exchange.getRequestMethod()))
        {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed.");
            return;
        }
        if (!authorised(exchange, true))
        {
            return;
        }

        Map<String, String> updates = readStringMap(exchange);
        if (updates == null)
        {
            send(exchange, 400, "text/plain; charset=utf-8", "Malformed request body.");
            return;
        }

        WebWrites.Result result = writes.applyPreferences(updates, clientAddress(exchange));
        send(exchange, result.ok() ? 200 : 400, "application/json; charset=utf-8",
             MAPPER.writeValueAsString(result.asMap()));
    }

    private void handleControl(HttpExchange exchange) throws IOException
    {
        if (!"POST".equals(exchange.getRequestMethod()))
        {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed.");
            return;
        }
        if (!authorised(exchange, true))
        {
            return;
        }

        Map<String, String> command = readStringMap(exchange);
        if (command == null)
        {
            send(exchange, 400, "text/plain; charset=utf-8", "Malformed request body.");
            return;
        }

        WebWrites.Result result = writes.control(command, clientAddress(exchange));
        send(exchange, result.ok() ? 200 : 400, "application/json; charset=utf-8",
             MAPPER.writeValueAsString(result.asMap()));
    }

    /**
     * GET reports the current sign-in phase (see {@link WebData#youtubeOauth}); POST signs the
     * bot out by deleting the stored token. Both are covered by the same authorisation GET/POST
     * split every other endpoint uses — a query-string token is enough to read the phase (that's
     * what makes the page load at all), but signing out requires the header, the same CSRF
     * defence {@link #handleConfig} relies on.
     */
    private void handleYoutubeOauth(HttpExchange exchange) throws IOException
    {
        if ("GET".equals(exchange.getRequestMethod()))
        {
            serveJson(exchange, e -> data.youtubeOauth());
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod()))
        {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed.");
            return;
        }
        if (!authorised(exchange, true))
        {
            return;
        }

        WebWrites.Result result = writes.youtubeSignOut(clientAddress(exchange));
        send(exchange, result.ok() ? 200 : 400, "application/json; charset=utf-8",
             MAPPER.writeValueAsString(result.asMap()));
    }

    /**
     * Checks GitHub for a newer release and, if one exists, downloads it — matching what the
     * desktop window's own "Check for updates" button does, see
     * {@link com.jagrosh.jmusicbot.gui.panels.UpdatesPanel} and
     * {@link SelfUpdater#checkAndStage()}. This used to only ever check: a person told "version
     * X is available" with no way to act on that until the hourly background check happened to
     * catch up was told about an update they could not use. Installing still happens only
     * through {@link #handleUpdateInstall}, on request — this never does more than {@link
     * SelfUpdater#checkAndStage()} itself ever does, which is check and download, nothing more.
     *
     * <p>GET only, authorised the same way every other read here is — a query-string token is
     * enough. That is deliberately what stops this from being usable to make the bot hammer
     * GitHub (or download unbounded releases) from an unauthenticated caller: {@link
     * #authorised} runs, and can refuse, before {@link SelfUpdater#checkAndStage()} is ever
     * reached, so a missing or wrong token never reaches the network.
     *
     * <p>The work itself is dispatched to {@link #updateCheckExecutor} rather than run inline.
     * Running it on this thread — one of the four the whole server answers every request with —
     * would let a slow connection to GitHub, or a slow ~68 MB download once an update is found,
     * tie that thread up for minutes, leaving every other open tab of the panel unable to load
     * anything for the same stretch. Dispatching returns this thread to the pool immediately;
     * the exchange is completed later, from the background thread, once the work finishes.
     *
     * <p>Answers 409 rather than running anything if {@link Bot#getUpdater()} is not there yet —
     * the same "not connected to Discord" state {@link #handleUpdateInstall} already reports,
     * since staging now lives on that same object and there is nothing to check-and-stage with
     * before it exists.
     */
    private void handleUpdateCheck(HttpExchange exchange) throws IOException
    {
        if (!"GET".equals(exchange.getRequestMethod()))
        {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed.");
            return;
        }
        if (!authorised(exchange, false))
        {
            return;
        }

        SelfUpdater updater = bot.getUpdater();
        if (updater == null)
        {
            send(exchange, 409, "application/json; charset=utf-8",
                 MAPPER.writeValueAsString(Map.of(
                         "ok", false, "status", "notReady",
                         "message", "Not connected to Discord yet.")));
            return;
        }

        updateCheckExecutor.execute(() -> {
            SelfUpdater.CheckAndStageOutcome outcome = updater.checkAndStage();
            try
            {
                send(exchange, 200, "application/json; charset=utf-8",
                     MAPPER.writeValueAsString(WebData.checkAndStagePayload(outcome)));
            }
            catch (IOException ex)
            {
                // Most likely the caller's tab was closed or navigated away before this
                // finished — nothing is listening for a response any more, and there is
                // nothing more useful to do here than note it and move on.
                LOG.debug("Web panel: could not deliver an update-check response: {}", ex.toString());
            }
        });
    }

    /**
     * Installs whatever {@link SelfUpdater}'s own hourly background check has already staged,
     * restarting the process into it — the web panel's equivalent of the desktop window's
     * "Install and restart" button, and the riskiest single endpoint this class exposes: unlike
     * everything else here, it makes the bot replace its own jar and restart, dropping every
     * voice connection the instant it does. Three things gate that beyond the ordinary token
     * check:
     *
     * <ul>
     *   <li><b>POST only</b>, and only with the {@code Authorization} header — never a
     *       query-string token — for the same CSRF reason {@link #handleConfig} requires it: a
     *       page on another site can make a browser issue a cross-origin request carrying
     *       whatever is in the URL, but not set a header.</li>
     *   <li><b>Refused whenever the panel is reachable from outside this machine and {@code
     *       web.allowConfigEdit} is off</b> — see {@link #installGateOpen()}. Restarting the
     *       process is at least as consequential as rewriting {@code config.txt}, so it is held
     *       to at least the bar {@link #handleConfig} already enforces for that, not a lower
     *       one.</li>
     *   <li><b>Never installs on this call alone.</b> Every call is a dry run — {@link
     *       SelfUpdater#decideInstall(boolean)} runs and its answer is returned, but nothing is
     *       scheduled — unless the request body says {@code "confirm":true}. The page always
     *       makes an unconfirmed call first, shows what {@code decideInstall} found (idle, or
     *       exactly who is playing), and only sends {@code confirm} — with {@code force} too, if
     *       the first call came back {@code "blocked"} — after a person has read that and said
     *       to continue. The same two-step confirmation the desktop window's own dialog walks
     *       through, just spread across two requests instead of one synchronous call.</li>
     * </ul>
     *
     * <p>When the decision is {@code Ready} and the call was confirmed, the response is sent
     * first and {@link SelfUpdater#installNow()} runs a moment later from the shared thread
     * pool — never inline on this request thread, which would have no chance to flush a
     * response before the process it is running in exits.
     */
    private void handleUpdateInstall(HttpExchange exchange) throws IOException
    {
        if (!"POST".equals(exchange.getRequestMethod()))
        {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed.");
            return;
        }
        if (!authorised(exchange, true))
        {
            return;
        }
        if (!installGateOpen())
        {
            send(exchange, 403, "application/json; charset=utf-8",
                 MAPPER.writeValueAsString(Map.of(
                         "ok", false,
                         "status", "disabled",
                         "message", "Installing from the web panel is off while it is reachable from "
                                 + "outside this machine and web.allowConfigEdit is off. Set "
                                 + "web.allowConfigEdit = true in config.txt, bind the panel to "
                                 + "127.0.0.1, or use the desktop window instead.")));
            return;
        }

        SelfUpdater updater = bot.getUpdater();
        if (updater == null)
        {
            send(exchange, 409, "application/json; charset=utf-8",
                 MAPPER.writeValueAsString(Map.of(
                         "ok", false, "status", "notReady",
                         "message", "Not connected to Discord yet.")));
            return;
        }

        Map<String, String> body = readStringMap(exchange);
        boolean force = body != null && "true".equalsIgnoreCase(body.get("force"));
        boolean confirmed = body != null && "true".equalsIgnoreCase(body.get("confirm"));

        SelfUpdater.InstallDecision decision = updater.decideInstall(force);
        send(exchange, 200, "application/json; charset=utf-8",
             MAPPER.writeValueAsString(WebData.installDecisionPayload(decision)));

        if (confirmed && decision instanceof SelfUpdater.InstallDecision.Ready)
        {
            // Scheduled rather than run inline: the response just sent above has to actually
            // reach the browser before this process replaces itself, which calling
            // installNow() directly on this request thread could not guarantee.
            bot.getThreadpool().schedule(updater::installNow, 500, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Whether installing from the web panel is allowed at all right now — see {@link
     * #handleUpdateInstall}. Mirrors the bar {@link #handleConfig} already holds writes to: a
     * panel reachable only from this machine is trusted the way a shell on this machine already
     * is, and one where the operator has separately opted into {@code web.allowConfigEdit} has
     * already accepted that whoever holds the token can materially change how the bot runs.
     * Neither holding, the safest default is to refuse.
     */
    private boolean installGateOpen()
    {
        return isLoopback(bot.getConfig().getWebBindAddress()) || bot.getConfig().isWebConfigEditAllowed();
    }

    /** Whether {@code bindAddress} only ever accepts connections from this machine. */
    private static boolean isLoopback(String bindAddress)
    {
        if (bindAddress == null || bindAddress.isBlank())
        {
            return true;
        }
        String trimmed = bindAddress.trim();
        return "127.0.0.1".equals(trimmed) || "::1".equals(trimmed) || "localhost".equals(trimmed);
    }

    // ==================== Request helpers ====================

    private Map<String, String> readStringMap(HttpExchange exchange)
    {
        try (InputStream in = exchange.getRequestBody())
        {
            byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES)
            {
                return null;
            }
            var type = MAPPER.getTypeFactory()
                             .constructMapType(java.util.LinkedHashMap.class, String.class, String.class);
            Map<String, String> parsed = MAPPER.readValue(body, type);
            return parsed == null ? Map.of() : parsed;
        }
        catch (IOException | RuntimeException ex)
        {
            return null;
        }
    }

    /**
     * Picks the language for one request.
     *
     * <p>An explicit {@code ?lang=} wins, then the browser's own Accept-Language, then the
     * bot's default. Honouring the browser means the panel arrives already readable for most
     * people without anyone configuring anything.
     */
    Language languageFor(HttpExchange exchange)
    {
        String explicit = queryParam(exchange, "lang");
        if (explicit != null)
        {
            var matched = Language.fromCode(explicit);
            if (matched.isPresent())
            {
                return matched.get();
            }
        }

        List<String> header = exchange.getRequestHeaders().get("Accept-Language");
        if (header != null && !header.isEmpty())
        {
            // "zh-TW,zh;q=0.9,en;q=0.8" — take tags in order and use the first one that maps.
            for (String tag : header.get(0).split(","))
            {
                var matched = Language.fromCode(tag.split(";")[0].trim());
                if (matched.isPresent())
                {
                    return matched.get();
                }
            }
        }

        return bot.getConfig().getGuiLanguage();
    }

    static String queryParam(HttpExchange exchange, String name)
    {
        String query = exchange.getRequestURI().getQuery();
        if (query == null)
        {
            return null;
        }
        String prefix = name + "=";
        for (String pair : query.split("&"))
        {
            if (pair.startsWith(prefix))
            {
                return java.net.URLDecoder.decode(pair.substring(prefix.length()), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String clientAddress(HttpExchange exchange)
    {
        var remote = exchange.getRemoteAddress();
        return remote == null || remote.getAddress() == null
                ? "unknown"
                : remote.getAddress().getHostAddress();
    }

    // ==================== Authentication ====================

    /**
     * Checks the token.
     *
     * @param mutating when true, only an {@code Authorization} header is accepted. A page on
     *                 another site can make the browser issue a cross-origin POST carrying
     *                 whatever is in the URL, but it cannot set this header without a preflight
     *                 that nothing here answers — so requiring it is what stops a link from
     *                 rewriting someone's config.
     */
    private boolean authorised(HttpExchange exchange, boolean mutating) throws IOException
    {
        String address = clientAddress(exchange);

        if (rateLimit.isLockedOut(address))
        {
            send(exchange, 429, "text/plain; charset=utf-8",
                 "Too many failed attempts. Try again later, or restart the bot for a new token.");
            return false;
        }

        String supplied = bearerToken(exchange);
        if (supplied == null && !mutating)
        {
            supplied = queryParam(exchange, "token");
        }

        if (auth.matches(supplied))
        {
            rateLimit.recordSuccess(address);
            return true;
        }

        if (rateLimit.recordFailure(address))
        {
            LOG.warn("Web panel: too many bad tokens from {}; locking that address out.", address);
        }

        // The same response either way. Distinguishing "no token" from "wrong token" tells an
        // attacker which half of the problem they have solved.
        send(exchange, 401, "text/plain; charset=utf-8",
             "Unauthorized. Append ?token=… using the token printed in the bot's console at startup.");
        return false;
    }

    private static String bearerToken(HttpExchange exchange)
    {
        List<String> header = exchange.getRequestHeaders().get("Authorization");
        if (header == null || header.isEmpty() || !header.get(0).startsWith("Bearer "))
        {
            return null;
        }
        return header.get(0).substring(7);
    }

    // ==================== Sending ====================

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
        // Nothing here should ever be cached: it is live status, and the page carries a token.
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(body);
        }
    }
}
