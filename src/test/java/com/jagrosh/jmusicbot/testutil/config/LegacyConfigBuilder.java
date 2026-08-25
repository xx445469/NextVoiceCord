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

import com.typesafe.config.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jagrosh.jmusicbot.testutil.config.ConfigTestConstants.ALL_AUDIO_SOURCES;
import static com.jagrosh.jmusicbot.testutil.config.ConfigTestConstants.DEFAULT_ALIASES;

/**
 * Builder for creating legacy (version 0) config objects with flat key structure.
 * 
 * <p>This builder provides a fluent API for creating legacy configs used in migration tests.
 * All keys are flat (not nested) as in the original config format.
 * 
 * <p>Example usage:
 * <pre>{@code
 * Config legacy = LegacyConfigBuilder.create()
 *     .withToken("test_token")
 *     .withOwner(123456789L)
 *     .withPrefix("!!")
 *     .withAudioSources("youtube", "soundcloud")
 *     .build();
 * }</pre>
 * 
 * <p>This builder is based on the reference-legacy.conf file structure.
 */
public class LegacyConfigBuilder extends AbstractConfigBuilder {
    private final Map<String, Object> config = new HashMap<>();
    
    @Override
    protected Map<String, Object> getConfigMap() {
        return config;
    }
    
    /**
     * Sets the bot token.
     */
    public LegacyConfigBuilder withToken(String token) {
        config.put("token", token);
        return this;
    }
    
    /**
     * Sets the owner ID.
     */
    public LegacyConfigBuilder withOwner(Long owner) {
        config.put("owner", owner);
        return this;
    }
    
    /**
     * Sets the command prefix.
     */
    public LegacyConfigBuilder withPrefix(String prefix) {
        config.put("prefix", prefix);
        return this;
    }
    
    /**
     * Sets the alternate prefix (use "NONE" to disable).
     */
    public LegacyConfigBuilder withAltPrefix(String altPrefix) {
        config.put("altprefix", altPrefix);
        return this;
    }
    
    /**
     * Sets the help command name.
     */
    public LegacyConfigBuilder withHelp(String help) {
        config.put("help", help);
        return this;
    }
    
    /**
     * Sets the game status.
     */
    public LegacyConfigBuilder withGame(String game) {
        config.put("game", game);
        return this;
    }
    
    /**
     * Sets the bot status (ONLINE, IDLE, DND, etc.).
     */
    public LegacyConfigBuilder withStatus(String status) {
        config.put("status", status);
        return this;
    }
    
    /**
     * Sets whether to show song in status.
     */
    public LegacyConfigBuilder withSongInStatus(boolean songInStatus) {
        config.put("songinstatus", songInStatus);
        return this;
    }
    
    /**
     * Sets the success emoji.
     */
    public LegacyConfigBuilder withSuccess(String success) {
        config.put("success", success);
        return this;
    }
    
    /**
     * Sets the warning emoji.
     */
    public LegacyConfigBuilder withWarning(String warning) {
        config.put("warning", warning);
        return this;
    }
    
    /**
     * Sets the error emoji.
     */
    public LegacyConfigBuilder withError(String error) {
        config.put("error", error);
        return this;
    }
    
    /**
     * Sets the loading emoji.
     */
    public LegacyConfigBuilder withLoading(String loading) {
        config.put("loading", loading);
        return this;
    }
    
    /**
     * Sets the searching emoji.
     */
    public LegacyConfigBuilder withSearching(String searching) {
        config.put("searching", searching);
        return this;
    }
    
    /**
     * Sets whether to stay in channel.
     */
    public LegacyConfigBuilder withStayInChannel(boolean stayInChannel) {
        config.put("stayinchannel", stayInChannel);
        return this;
    }
    
    /**
     * Sets the maximum track time in seconds.
     */
    public LegacyConfigBuilder withMaxTime(Long maxTime) {
        config.put("maxtime", maxTime);
        return this;
    }
    
    /**
     * Sets the skip ratio.
     */
    public LegacyConfigBuilder withSkipRatio(Double skipRatio) {
        config.put("skipratio", skipRatio);
        return this;
    }
    
    /**
     * Sets the log level.
     */
    public LegacyConfigBuilder withLogLevel(String logLevel) {
        config.put("loglevel", logLevel);
        return this;
    }
    
    /**
     * Sets whether to enable eval.
     */
    public LegacyConfigBuilder withEval(boolean eval) {
        config.put("eval", eval);
        return this;
    }
    
    /**
     * Sets the eval engine.
     */
    public LegacyConfigBuilder withEvalEngine(String evalEngine) {
        config.put("evalengine", evalEngine);
        return this;
    }
    
    /**
     * Sets whether to use YouTube OAuth.
     */
    public LegacyConfigBuilder withUseYouTubeOAuth(boolean useYouTubeOAuth) {
        config.put("useyoutubeoauth", useYouTubeOAuth);
        return this;
    }
    
    /**
     * Sets the maximum YouTube playlist pages.
     */
    public LegacyConfigBuilder withMaxYTPlaylistPages(Integer maxYTPlaylistPages) {
        config.put("maxytplaylistpages", maxYTPlaylistPages);
        return this;
    }
    
    /**
     * Sets the alone time until stop in seconds.
     */
    public LegacyConfigBuilder withAloneTimeUntilStop(Long aloneTimeUntilStop) {
        config.put("alonetimeuntilstop", aloneTimeUntilStop);
        return this;
    }
    
    /**
     * Sets the playlists folder path.
     */
    public LegacyConfigBuilder withPlaylistsFolder(String playlistsFolder) {
        config.put("playlistsfolder", playlistsFolder);
        return this;
    }
    
    /**
     * Sets whether to show update alerts.
     */
    public LegacyConfigBuilder withUpdateAlerts(boolean updateAlerts) {
        config.put("updatealerts", updateAlerts);
        return this;
    }
    
    /**
     * Sets whether to show now playing images.
     */
    public LegacyConfigBuilder withNPImages(boolean npImages) {
        config.put("npimages", npImages);
        return this;
    }
    
    /**
     * Sets the audio sources as a list of strings.
     * 
     * <p>Available sources (from reference-legacy.conf):
     * youtube, soundcloud, bandcamp, vimeo, twitch, beam, getyarn, nico, http, local
     * 
     * @param sources audio source names (e.g., "youtube", "soundcloud", "local", "bandcamp")
     */
    public LegacyConfigBuilder withAudioSources(String... sources) {
        config.put("audiosources", List.of(sources));
        return this;
    }
    
    /**
     * Sets all audio sources enabled (matching reference-legacy.conf defaults).
     */
    public LegacyConfigBuilder withAllAudioSources() {
        return withAudioSources(ALL_AUDIO_SOURCES);
    }
    
    /**
     * Sets the audio sources as a list.
     */
    public LegacyConfigBuilder withAudioSources(List<String> sources) {
        config.put("audiosources", sources);
        return this;
    }
    
    /**
     * Sets the command aliases.
     * 
     * @param aliases map of command names to lists of aliases
     */
    public LegacyConfigBuilder withAliases(Map<String, List<String>> aliases) {
        config.put("aliases", aliases);
        return this;
    }
    
    /**
     * Sets the transforms configuration.
     */
    public LegacyConfigBuilder withTransforms(Map<String, Object> transforms) {
        config.put("transforms", transforms);
        return this;
    }
    
    /**
     * Sets a custom key-value pair.
     */
    public LegacyConfigBuilder withCustom(String key, Object value) {
        config.put(key, value);
        return this;
    }
    
    /**
     * Creates a new builder instance.
     */
    public static LegacyConfigBuilder create() {
        return new LegacyConfigBuilder();
    }
    
    /**
     * Creates a minimal valid legacy config with only required fields.
     */
    public static Config minimal() {
        return new LegacyConfigBuilder()
            .withToken("test_token_12345")
            .withOwner(123456789L)
            .build();
    }
    
    /**
     * Creates a legacy config matching the reference-legacy.conf defaults.
     * This includes all default values from the reference file.
     */
    public static Config withReferenceDefaults() {
        return new LegacyConfigBuilder()
            .withToken("BOT_TOKEN_HERE")
            .withOwner(0L)
            .withPrefix("@mention")
            .withAltPrefix("NONE")
            .withHelp("help")
            .withGame("DEFAULT")
            .withStatus("ONLINE")
            .withSongInStatus(false)
            .withSuccess("🎶")
            .withWarning("💡")
            .withError("🚫")
            .withLoading("⌚")
            .withSearching("🔎")
            .withNPImages(false)
            .withStayInChannel(false)
            .withMaxTime(0L)
            .withMaxYTPlaylistPages(10)
            .withUseYouTubeOAuth(false)
            .withSkipRatio(0.55)
            .withAloneTimeUntilStop(0L)
            .withPlaylistsFolder("Playlists")
            .withUpdateAlerts(true)
            .withLogLevel("info")
            .withEval(false)
            .withEvalEngine("Nashorn")
            .withAllAudioSources()
            .withAliases(new HashMap<>(DEFAULT_ALIASES))
            .withTransforms(new HashMap<>())
            .build();
    }
}
