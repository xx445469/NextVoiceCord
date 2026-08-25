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
package com.jagrosh.jmusicbot.config.migration.versions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.audio.AudioSource;
import com.jagrosh.jmusicbot.config.migration.Migration;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

/**
 * Migration from version 0 (legacy config) to version 1 (new config format).
 * 
 * @author Arif Banai (arif-banai)
 */
public class LegacyToV1 implements Migration {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyToV1.class);
    
    @Override
    public int getFromVersion() {
        return 0;
    }
    
    @Override
    public int getToVersion() {
        return 1;
    }
    
    @Override
    public Config migrate(Config source) {
        LOGGER.debug("Starting migration from version 0 to version 1");
        
        Map<String, Object> migrated = new HashMap<>();
        
        // Build each section
        addSection(migrated, "meta", buildMetaSection());
        addSection(migrated, "discord", buildDiscordSection(source));
        addSection(migrated, "commands", buildCommandsSection(source));
        addSection(migrated, "presence", buildPresenceSection(source));
        addSection(migrated, "ui", buildUiSection(source));
        addSection(migrated, "nowPlaying", buildNowPlayingSection(source));
        addSection(migrated, "voice", buildVoiceSection(source));
        addSection(migrated, "playback", buildPlaybackSection(source));
        addSection(migrated, "paths", buildPathsSection(source));
        addSection(migrated, "updates", buildUpdatesSection(source));
        addSection(migrated, "logging", buildLoggingSection(source));
        addSection(migrated, "dangerous", buildDangerousSection(source));
        
        Config migratedConfig = ConfigFactory.parseMap(migrated);
        LOGGER.debug("Migration from version 0 to version 1 completed");
        
        return migratedConfig;
    }
    
    /** Adds a section to the migrated config if not empty. */
    private void addSection(Map<String, Object> migrated, String key, Map<String, Object> section) {
        if (!section.isEmpty()) {
            migrated.put(key, section);
        }
    }
    
    private Map<String, Object> buildMetaSection() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("configVersion", 1);
        return meta;
    }
    
    private Map<String, Object> buildDiscordSection(Config source) {
        Map<String, Object> discord = new HashMap<>();
        if (source.hasPath("token")) {
            discord.put("token", source.getString("token"));
        }
        if (source.hasPath("owner")) {
            discord.put("owner", source.getLong("owner"));
        }
        return discord;
    }
    
    private Map<String, Object> buildCommandsSection(Config source) {
        Map<String, Object> commands = new HashMap<>();
        if (source.hasPath("prefix")) {
            commands.put("prefix", source.getString("prefix"));
        }
        if (source.hasPath("altprefix")) {
            commands.put("altPrefix", source.getString("altprefix"));
        }
        if (source.hasPath("help")) {
            commands.put("help", source.getString("help"));
        }
        if (source.hasPath("aliases")) {
            commands.put("aliases", source.getConfig("aliases").root().unwrapped());
        }
        return commands;
    }
    
    private Map<String, Object> buildPresenceSection(Config source) {
        Map<String, Object> presence = new HashMap<>();
        if (source.hasPath("game")) {
            presence.put("game", source.getString("game"));
        }
        if (source.hasPath("status")) {
            String status = source.getString("status");
            presence.put("status", status != null ? status.toUpperCase() : null);
        }
        if (source.hasPath("songinstatus")) {
            presence.put("songInStatus", source.getBoolean("songinstatus"));
        }
        return presence;
    }
    
    private Map<String, Object> buildUiSection(Config source) {
        Map<String, Object> ui = new HashMap<>();
        Map<String, Object> emojis = new HashMap<>();
        
        if (source.hasPath("success")) {
            emojis.put("success", source.getString("success"));
        }
        if (source.hasPath("warning")) {
            emojis.put("warning", source.getString("warning"));
        }
        if (source.hasPath("error")) {
            emojis.put("error", source.getString("error"));
        }
        if (source.hasPath("loading")) {
            emojis.put("loading", source.getString("loading"));
        }
        if (source.hasPath("searching")) {
            emojis.put("searching", source.getString("searching"));
        }
        
        if (!emojis.isEmpty()) {
            ui.put("emojis", emojis);
        }
        return ui;
    }
    
    private Map<String, Object> buildNowPlayingSection(Config source) {
        Map<String, Object> nowPlaying = new HashMap<>();
        if (source.hasPath("npimages")) {
            nowPlaying.put("images", source.getBoolean("npimages"));
        }
        return nowPlaying;
    }
    
    private Map<String, Object> buildVoiceSection(Config source) {
        Map<String, Object> voice = new HashMap<>();
        if (source.hasPath("stayinchannel")) {
            voice.put("stayInChannel", source.getBoolean("stayinchannel"));
        }
        if (source.hasPath("alonetimeuntilstop")) {
            voice.put("aloneTimeUntilStopSeconds", source.getLong("alonetimeuntilstop"));
        }
        return voice;
    }
    
    private Map<String, Object> buildPlaybackSection(Config source) {
        Map<String, Object> playback = new HashMap<>();
        
        if (source.hasPath("maxtime")) {
            playback.put("maxTrackSeconds", source.getLong("maxtime"));
        }
        if (source.hasPath("maxytplaylistpages")) {
            playback.put("maxYouTubePlaylistPages", source.getInt("maxytplaylistpages"));
        }
        if (source.hasPath("skipratio")) {
            playback.put("skipRatio", source.getDouble("skipratio"));
        }
        if (source.hasPath("useyoutubeoauth")) {
            Map<String, Object> youtube = new HashMap<>();
            youtube.put("useOAuth", source.getBoolean("useyoutubeoauth"));
            playback.put("youtube", youtube);
        }
        
        Map<String, Boolean> audioSourcesMap = migrateAudioSources(source);
        if (!audioSourcesMap.isEmpty()) {
            playback.put("audioSources", audioSourcesMap);
        }
        
        if (source.hasPath("transforms")) {
            playback.put("transforms", source.getConfig("transforms").root().unwrapped());
        }
        
        return playback;
    }
    
    private Map<String, Object> buildPathsSection(Config source) {
        Map<String, Object> paths = new HashMap<>();
        if (source.hasPath("playlistsfolder")) {
            paths.put("playlistsFolder", source.getString("playlistsfolder"));
        }
        return paths;
    }
    
    private Map<String, Object> buildUpdatesSection(Config source) {
        Map<String, Object> updates = new HashMap<>();
        if (source.hasPath("updatealerts")) {
            updates.put("alerts", source.getBoolean("updatealerts"));
        }
        return updates;
    }
    
    private Map<String, Object> buildLoggingSection(Config source) {
        Map<String, Object> logging = new HashMap<>();
        if (source.hasPath("loglevel")) {
            logging.put("level", source.getString("loglevel"));
        }
        return logging;
    }
    
    private Map<String, Object> buildDangerousSection(Config source) {
        Map<String, Object> dangerous = new HashMap<>();
        if (source.hasPath("eval")) {
            dangerous.put("eval", source.getBoolean("eval"));
        }
        if (source.hasPath("evalengine")) {
            dangerous.put("evalEngine", source.getString("evalengine"));
        }
        return dangerous;
    }
    
    /**
     * Migrates audio sources from legacy list format to new boolean map format.
     * 
     * @param source the legacy config
     * @return a map of audio source names to boolean values
     */
    private Map<String, Boolean> migrateAudioSources(Config source) {
        Map<String, Boolean> audioSourcesMap = new HashMap<>();
        
        // Initialize all sources to false
        for (AudioSource audioSource : AudioSource.values()) {
            audioSourcesMap.put(audioSource.getConfigName(), false);
        }
        
        if (source.hasPath("audiosources")) {
            try {
                List<String> enabledSources = source.getStringList("audiosources");
                LOGGER.debug("Migrating audio sources list: {}", enabledSources);
                
                // Set enabled sources to true
                for (String sourceName : enabledSources) {
                    AudioSource.fromConfigName(sourceName).ifPresent(sourceEnum -> {
                        audioSourcesMap.put(sourceEnum.getConfigName(), true);
                        LOGGER.debug("Enabled audio source: {}", sourceEnum.getConfigName());
                    });
                }
            } catch (ConfigException e) {
                LOGGER.warn("Failed to read audiosources list, defaulting to all enabled: {}", e.getMessage());
                // If parsing fails, enable all sources (default behavior)
                for (AudioSource audioSource : AudioSource.values()) {
                    audioSourcesMap.put(audioSource.getConfigName(), true);
                }
            }
        } else {
            // No audiosources key means all enabled (default behavior)
            LOGGER.debug("No audiosources key found, defaulting to all enabled");
            for (AudioSource audioSource : AudioSource.values()) {
                audioSourcesMap.put(audioSource.getConfigName(), true);
            }
        }
        
        return audioSourcesMap;
    }
    
}
