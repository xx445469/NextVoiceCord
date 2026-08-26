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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

import org.slf4j.Logger;

/**
 * One entry from {@code lavalink.nodes}: how to reach a Lavalink node and the credential to
 * authenticate with it.
 *
 * <p>{@code password} is a credential exactly like {@code proxy.password} or a Spotify client
 * secret, so it is never included in {@link #toString()} or in any log line. {@link #describe()}
 * is what logging code should use instead.
 *
 * @param name   a label for this node, used only in logs (does not need to be unique, but should be)
 * @param host   hostname or IP the node listens on
 * @param port   port the node listens on
 * @param password the node's configured Lavalink password ({@code server.password} on the node side)
 * @param secure whether to speak https/wss to this node rather than http/ws
 *
 * @author adan (xx445469)
 */
public record LavalinkNodeConfig(String name, String host, int port, String password, boolean secure)
{
    /** The base REST URL for this node, e.g. {@code http://localhost:2333}. */
    public String httpBaseUrl()
    {
        return (secure ? "https://" : "http://") + host + ":" + port;
    }

    /** The WebSocket URL for this node's {@code /v4/websocket} endpoint. */
    public String webSocketUrl()
    {
        return (secure ? "wss://" : "ws://") + host + ":" + port + "/v4/websocket";
    }

    /** A log/diagnostics-safe description: everything except the password. */
    public String describe()
    {
        return name + " (" + host + ":" + port + ", secure=" + secure + ")";
    }

    @Override
    public String toString()
    {
        // Overridden so an accidental LOGGER.info("{}", node) or string-concatenation of this
        // record can never leak the password the way the generated record toString() would.
        return describe();
    }

    /**
     * Parses and validates {@code lavalink.nodes} from the merged config.
     *
     * <p>Missing entirely, or an empty list, is not an error here: {@code playback.engine} may
     * legitimately be {@code lavaplayer}, in which case nothing reads this. The caller decides
     * whether an empty result is a problem for the engine actually selected.
     *
     * <p>An individual node with a blank host, an out-of-range port, or a duplicated name is
     * skipped with a warning rather than aborting startup — the same "one bad optional setting
     * should not stop the bot" posture as the rest of config loading.
     *
     * @param config the merged config (user config with defaults applied)
     * @param logger where to log a skipped/invalid entry
     * @return the valid nodes, in the order they appeared; never {@code null}
     */
    public static List<LavalinkNodeConfig> parseList(Config config, Logger logger)
    {
        List<LavalinkNodeConfig> nodes = new ArrayList<>();
        if (config == null || !config.hasPath("lavalink.nodes"))
        {
            return nodes;
        }

        List<? extends Config> raw;
        try
        {
            raw = config.getConfigList("lavalink.nodes");
        }
        catch (ConfigException ex)
        {
            logger.error("lavalink.nodes is not a list of node objects: {}. No Lavalink nodes loaded.",
                    ex.getMessage());
            return nodes;
        }

        java.util.Set<String> seenNames = new java.util.HashSet<>();
        for (int i = 0; i < raw.size(); i++)
        {
            Config entry = raw.get(i);
            String label = "lavalink.nodes[" + i + "]";

            String name = getStringOrDefault(entry, "name", "node-" + i);
            String host = getStringOrDefault(entry, "host", "");
            int port = getIntOrDefault(entry, "port", -1);
            String password = getStringOrDefault(entry, "password", "");
            boolean secure = getBooleanOrDefault(entry, "secure", false);

            if (host.isBlank())
            {
                logger.error("{} ('{}') has no host configured; skipping this node.", label, name);
                continue;
            }
            if (port < 1 || port > 65535)
            {
                logger.error("{} ('{}') has an invalid port ({}); must be 1-65535. Skipping this node.",
                        label, name, port);
                continue;
            }
            String normalizedName = name == null || name.isBlank() ? "node-" + i : name;
            if (!seenNames.add(normalizedName.toLowerCase(Locale.ROOT)))
            {
                logger.warn("{} reuses the name '{}' already used by an earlier node; "
                        + "it will still be used, but logs will be ambiguous. Consider unique names.",
                        label, normalizedName);
            }

            nodes.add(new LavalinkNodeConfig(normalizedName, host, port, password, secure));
        }

        return nodes;
    }

    private static String getStringOrDefault(Config config, String path, String fallback)
    {
        try
        {
            return config.hasPath(path) ? config.getString(path) : fallback;
        }
        catch (ConfigException ex)
        {
            return fallback;
        }
    }

    private static int getIntOrDefault(Config config, String path, int fallback)
    {
        try
        {
            return config.hasPath(path) ? config.getInt(path) : fallback;
        }
        catch (ConfigException ex)
        {
            return fallback;
        }
    }

    private static boolean getBooleanOrDefault(Config config, String path, boolean fallback)
    {
        try
        {
            return config.hasPath(path) ? config.getBoolean(path) : fallback;
        }
        catch (ConfigException ex)
        {
            return fallback;
        }
    }
}
