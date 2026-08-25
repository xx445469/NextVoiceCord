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
package com.jagrosh.jmusicbot.testutil.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared constants for config test utilities.
 * 
 * <p>This class contains constants shared between {@link LegacyConfigBuilder}
 * and {@link V1ConfigBuilder} to avoid duplication.
 */
public final class ConfigTestConstants {
    
    private ConfigTestConstants() {
        // Prevent instantiation
    }
    
    /**
     * All available audio source names (matching reference.conf and reference-legacy.conf).
     */
    public static final String[] ALL_AUDIO_SOURCES = {
        "youtube", "soundcloud", "bandcamp", "vimeo", "twitch", 
        "beam", "getyarn", "nico", "http", "local"
    };
    
    /**
     * Default command aliases matching reference.conf.
     * This is an unmodifiable map to prevent accidental modification.
     */
    public static final Map<String, List<String>> DEFAULT_ALIASES;
    
    static {
        Map<String, List<String>> aliases = new HashMap<>();
        aliases.put("settings", List.of("status"));
        aliases.put("lyrics", List.of());
        aliases.put("nowplaying", List.of("np", "current"));
        aliases.put("play", List.of());
        aliases.put("playlists", List.of("pls"));
        aliases.put("queue", List.of("list"));
        aliases.put("remove", List.of("delete"));
        aliases.put("scsearch", List.of());
        aliases.put("search", List.of("ytsearch"));
        aliases.put("shuffle", List.of());
        aliases.put("skip", List.of("voteskip"));
        aliases.put("prefix", List.of("setprefix"));
        aliases.put("setdj", List.of());
        aliases.put("setskip", List.of("setskippercent", "skippercent", "setskipratio"));
        aliases.put("settc", List.of());
        aliases.put("setvc", List.of());
        aliases.put("forceremove", List.of("forcedelete", "modremove", "moddelete", "modelete"));
        aliases.put("forceskip", List.of("modskip"));
        aliases.put("movetrack", List.of("move"));
        aliases.put("pause", List.of());
        aliases.put("playnext", List.of());
        aliases.put("queuetype", List.of());
        aliases.put("repeat", List.of());
        aliases.put("skipto", List.of("jumpto"));
        aliases.put("stop", List.of("leave"));
        aliases.put("volume", List.of("vol"));
        DEFAULT_ALIASES = Collections.unmodifiableMap(aliases);
    }
    
    /**
     * Creates a mutable copy of the default aliases map.
     * Use this when you need to modify the aliases.
     * 
     * @return a mutable copy of the default aliases
     */
    public static Map<String, List<String>> copyDefaultAliases() {
        return new HashMap<>(DEFAULT_ALIASES);
    }
}
