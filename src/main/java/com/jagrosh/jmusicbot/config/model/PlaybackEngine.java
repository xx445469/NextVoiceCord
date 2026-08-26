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
package com.jagrosh.jmusicbot.config.model;

import java.util.Locale;

import org.slf4j.Logger;

/**
 * Which audio backend the bot plays through, set via {@code playback.engine}.
 *
 * <p>{@code LAVAPLAYER} is the bot connecting to Discord's voice gateway itself and pushing
 * Opus frames it decodes locally — everything this project has always done, and the only
 * mode that is exercised in production today. {@code LAVALINK} hands the voice connection and
 * decoding to an external Lavalink node instead; the bot only forwards the voice server/state
 * update and issues REST/WebSocket commands.
 *
 * <p>A third value, {@code fallback} (both engines live, with a handover on node failure), is
 * accepted in config but not implemented — it needs both paths proven independently first. It
 * resolves to {@link #LAVAPLAYER} with a clear warning rather than being silently ignored or
 * treated as a synonym for {@code lavalink}.
 *
 * @author adan (xx445469)
 */
public enum PlaybackEngine
{
    LAVAPLAYER,
    LAVALINK;

    /**
     * Resolves the configured {@code playback.engine} string to an engine that actually runs.
     *
     * <p>This never returns anything other than {@link #LAVAPLAYER} or {@link #LAVALINK}: an
     * unrecognised value, a blank value, or the not-yet-implemented {@code fallback} all fall
     * back to {@link #LAVAPLAYER} with a warning logged, the same "don't abort startup over one
     * cosmetic setting" posture the language and GUI theme options already use.
     *
     * @param raw    the raw {@code playback.engine} config value
     * @param logger where to log a fallback, if one happens
     * @return the engine to actually run
     */
    public static PlaybackEngine resolve(String raw, Logger logger)
    {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);

        return switch (normalized)
        {
            case "lavaplayer" -> LAVAPLAYER;
            case "lavalink" -> LAVALINK;
            case "fallback" ->
            {
                logger.warn("playback.engine = \"fallback\" is not implemented yet (planned for a later "
                        + "stage: both engines live, with a handover on node failure). Falling back to "
                        + "\"lavaplayer\" for now.");
                yield LAVAPLAYER;
            }
            default ->
            {
                logger.warn("Unknown playback.engine '{}'; falling back to \"lavaplayer\". "
                        + "Valid values: lavaplayer, lavalink, fallback.", raw);
                yield LAVAPLAYER;
            }
        };
    }

    /** The raw config value that selects this engine. */
    public String configValue()
    {
        return name().toLowerCase(Locale.ROOT);
    }
}
