/*
 * Copyright 2018 John Grosh (jagrosh)
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
package com.jagrosh.jmusicbot;

import com.jagrosh.jmusicbot.audio.lavalink.LavalinkNodeConfig;
import com.jagrosh.jmusicbot.config.model.PlaybackEngine;
import com.jagrosh.jmusicbot.i18n.Language;

import static com.jagrosh.jmusicbot.config.model.ConfigOption.*;
import static com.jagrosh.jmusicbot.config.model.ConfigOption.GUI_ENABLED;
import static com.jagrosh.jmusicbot.config.model.ConfigOption.GUI_THEME;
import static com.jagrosh.jmusicbot.config.model.ConfigOption.GUI_FONT_SIZE;
import static com.jagrosh.jmusicbot.config.model.ConfigOption.GUI_LANGUAGE;
import static com.jagrosh.jmusicbot.config.model.ConfigOption.WEB_BIND_ADDRESS;
import static com.jagrosh.jmusicbot.config.model.ConfigOption.WEB_ALLOW_CONFIG_EDIT;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

import com.jagrosh.jmusicbot.audio.AudioSource;
import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.jagrosh.jmusicbot.config.loader.ConfigLoader;
import com.jagrosh.jmusicbot.config.update.ConfigUpdater;
import com.jagrosh.jmusicbot.config.validation.ConfigValidator;
import com.jagrosh.jmusicbot.config.validation.ConfigValidator.ValidationResult;
import com.jagrosh.jmusicbot.config.migration.ConfigMigration;
import com.jagrosh.jmusicbot.config.migration.ConfigMigrationException;
import com.jagrosh.jmusicbot.config.model.ConfigUpdateType;
import com.jagrosh.jmusicbot.entities.UserInteraction;
import com.jagrosh.jmusicbot.entities.UserInteraction.Level;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * 
 * @author John Grosh (jagrosh)
 */
public class BotConfig {
    private final static Logger LOGGER = LoggerFactory.getLogger(BotConfig.class);

    private final UserInteraction userInteraction;

    private Path path = null;
    private String token, prefix, altprefix, helpWord, playlistsFolder, logLevel,
            successEmoji, warningEmoji, errorEmoji, loadingEmoji, searchingEmoji,
            evalEngine, guiTheme;
    private boolean stayInChannel, songInGame, npImages, npMinimalMessage, npShowButtons, showNpProgressBar, updatealerts, useEval, dbots, useYouTubeOauth, guiEnabled;
    private long owner, maxSeconds, aloneTimeUntilStop;
    private long clearChannelAgeDays;
    private int maxYTPlaylistPages, maxHistorySize, guiFontSize, nasBufferMs, frameBufferMs, proxyPort, clearChannelDeleteLimit;
    private String proxyHost, proxyUsername, proxyPassword;
    private String youtubePoToken, youtubeVisitorData;
    private java.util.List<String> youtubeClients;
    private String spotifyClientId, spotifyClientSecret;
    private boolean proxyLavaplayer, proxyJda, proxyGithub;
    private double skipratio;
    private Language defaultLanguage;
    private Language guiLanguage;
    private String guiLanguageRaw;
    private String webBindAddress;
    private boolean webAllowConfigEdit;
    private String updateRepository, updateGithubToken;
    private boolean autoUpdate;
    private int updateIntervalHours;
    private OnlineStatus status;
    private Activity game;
    private Config aliases, transforms;
    private Set<AudioSource> enabledAudioSources;
    private PlaybackEngine playbackEngine;
    private String playbackEngineRaw;
    private java.util.List<LavalinkNodeConfig> lavalinkNodes;

    private boolean valid = false;

    public BotConfig(UserInteraction userInteraction) {
        this.userInteraction = userInteraction;
    }

    public void load() {
        valid = false;

        try {
            path = ConfigIO.getConfigPath();
            
            // Load and migrate config
            ConfigLoadResult loadResult = loadAndMigrateConfig();
            
            // Run diagnostics and update config file if needed
            ConfigLoadResult updatedResult = runDiagnosticsAndUpdate(loadResult);
            
            // Load all config values
            loadConfigValues(updatedResult.mergedConfig, updatedResult.migratedUserConfig);

            // Validate required fields and write if needed
            if (!validateRequiredFields()) {
                return;
            }

            valid = true;
        } catch (ConfigException ex) {
            userInteraction.alert(Level.ERROR, "Config",
                    ex + ": " + ex.getMessage() + "\n\nConfig Location: " + path.toAbsolutePath().toString());
        } catch (ConfigMigrationException ex) {
            LOGGER.error("Config migration failed: {}", ex.getMessage());
            userInteraction.alert(Level.ERROR, "Config Migration",
                    "Failed to migrate configuration: " + ex.getMessage() + "\n\nConfig Location: " + path.toAbsolutePath().toString());
        }
    }
    
    /**
     * Loads raw config, detects migration need, and returns migrated + merged configs.
     * Parses each resource only once to avoid redundant I/O.
     */
    private ConfigLoadResult loadAndMigrateConfig() {
        // Parse each resource exactly once
        Config rawUserConfig = ConfigLoader.loadRawUserConfig(path);
        Config defaults = ConfigIO.loadDefaults();
        
        // Detect versions for migration check
        int userVersion = ConfigMigration.detectVersion(rawUserConfig);
        int latestVersion = ConfigMigration.getLatestVersion(defaults);

        // Use overloads that accept already-parsed configs to avoid re-parsing
        Config migratedUserConfig = ConfigLoader.loadMigratedUserConfig(rawUserConfig, defaults);
        Config mergedConfig = ConfigLoader.mergeWithDefaults(migratedUserConfig, defaults);

        return new ConfigLoadResult(migratedUserConfig, mergedConfig, defaults, userVersion, latestVersion);
    }
    
    /**
     * Runs diagnostics and updates config file if migration occurred or issues detected.
     * Returns updated configs if file was modified.
     */
    private ConfigLoadResult runDiagnosticsAndUpdate(ConfigLoadResult loadResult) {
        // Fresh install - skip diagnostics since user will be prompted for required fields
        if (loadResult.migratedUserConfig.isEmpty()) {
            return loadResult;
        }
        
        ConfigDiagnostics.Report diagnostics = ConfigDiagnostics.analyze(
                loadResult.migratedUserConfig, loadResult.mergedConfig, loadResult.defaults);
        
        logDiagnostics(diagnostics);
        
        if (loadResult.migrationOccurred() || diagnostics.hasIssues()) {
            // Determine update type based on original version and diagnostics
            boolean hasMissingKeys = !diagnostics.getMissingRequired().isEmpty() 
                    || !diagnostics.getMissingOptional().isEmpty();
            ConfigUpdateType updateType = ConfigUpdateType.determine(
                    loadResult.originalVersion, loadResult.latestVersion, hasMissingKeys);
            
            Path updatedConfigPath = ConfigUpdater.generateUpdatedConfig(
                    path, loadResult.migratedUserConfig, diagnostics, updateType);
            if (updatedConfigPath != null) {
                logConfigUpdate(updateType, updatedConfigPath);
                
                // Reload configs from the updated file - reuse defaults since they haven't changed
                Config rawUserConfig = ConfigLoader.loadRawUserConfig(path);
                Config migratedUserConfig = ConfigLoader.loadMigratedUserConfig(rawUserConfig, loadResult.defaults);
                Config mergedConfig = ConfigLoader.mergeWithDefaults(migratedUserConfig, loadResult.defaults);
                return new ConfigLoadResult(migratedUserConfig, mergedConfig, 
                        loadResult.defaults, loadResult.originalVersion, loadResult.latestVersion);
            }
        }
        return loadResult;
    }
    
    /**
     * Logs diagnostic issues at appropriate level.
     */
    private void logDiagnostics(ConfigDiagnostics.Report diagnostics) {
        if (diagnostics.hasIssues()) {
            if (diagnostics.hasErrors()) {
                LOGGER.error("Config diagnostics - {}", diagnostics.generateMessage());
            } else if (diagnostics.hasWarnings()) {
                LOGGER.warn("Config diagnostics - {}", diagnostics.generateMessage());
            }
        }
    }
    
    /**
     * Logs config update message with appropriate wording based on update type.
     */
    private void logConfigUpdate(ConfigUpdateType updateType, Path updatedConfigPath) {
        LOGGER.info("Config file {} and updated: {}. Original backed up with .bak extension.", 
                updateType.getPastTenseVerb(), updatedConfigPath);
    }
    
    /**
     * Validates token and owner, prompting user if needed. Returns false if validation fails.
     */
    private boolean validateRequiredFields() {
        ValidationResult tokenResult = ConfigValidator.validateToken(token, userInteraction, path);
        if (!tokenResult.isValid()) {
            return false;
        }
        token = tokenResult.getValue();
        boolean needsWrite = tokenResult.needsWrite();

        ValidationResult ownerResult = ConfigValidator.validateOwner(owner, userInteraction, path);
        if (!ownerResult.isValid()) {
            return false;
        }
        owner = ownerResult.getValue();
        needsWrite = needsWrite || ownerResult.needsWrite();

        if (needsWrite) {
            writeToFile();
        }
        return true;
    }
    
    /**
     * Holds the result of loading and migrating config.
     */
    private static class ConfigLoadResult {
        final Config migratedUserConfig;
        final Config mergedConfig;
        final Config defaults;
        final int originalVersion;
        final int latestVersion;
        
        ConfigLoadResult(Config migratedUserConfig, Config mergedConfig, 
                        Config defaults, int originalVersion, int latestVersion) {
            this.migratedUserConfig = migratedUserConfig;
            this.mergedConfig = mergedConfig;
            this.defaults = defaults;
            this.originalVersion = originalVersion;
            this.latestVersion = latestVersion;
        }
        
        boolean migrationOccurred() {
            return originalVersion < latestVersion;
        }
    }
    
    /**
     * Loads all configuration values from the merged config.
     */
    private void loadConfigValues(Config config, Config migratedUserConfig) {
        // set values using ConfigOption enum for type safety and standardization
        token = TOKEN.getString(config);
        prefix = PREFIX.getString(config);
        youtubePoToken = YOUTUBE_PO_TOKEN.getString(config);
        youtubeVisitorData = YOUTUBE_VISITOR_DATA.getString(config);
        youtubeClients = YOUTUBE_CLIENTS.getStringList(config);
        spotifyClientId = SPOTIFY_CLIENT_ID.getString(config);
        spotifyClientSecret = SPOTIFY_CLIENT_SECRET.getString(config);
        proxyUsername = PROXY_USERNAME.getString(config);
        proxyPassword = PROXY_PASSWORD.getString(config);
        updateRepository = UPDATE_REPOSITORY.getString(config);
        updateGithubToken = UPDATE_GITHUB_TOKEN.getString(config);
        autoUpdate = UPDATE_AUTO.getBoolean(config);
        updateIntervalHours = UPDATE_INTERVAL_HOURS.getInt(config);
        // An unrecognised code falls back to English with a warning rather than aborting
        // startup: a typo in one cosmetic setting should not stop the bot from running.
        defaultLanguage = Language.fromCode(LANGUAGE.getString(config)).orElseGet(() -> {
            LOGGER.warn("Unknown ui.language '{}'; falling back to {}. Available: {}",
                     LANGUAGE.getString(config), Language.DEFAULT,
                     java.util.Arrays.toString(Language.values()));
            return Language.DEFAULT;
        });
        // Handle altPrefix null value by defaulting to "NONE"
        altprefix = ALTPREFIX.hasValue(config) ? ALTPREFIX.getString(config) : "NONE";
        helpWord = HELP_WORD.getString(config);
        owner = OWNER.getLong(config);
        successEmoji = SUCCESS_EMOJI.getString(config);
        warningEmoji = WARNING_EMOJI.getString(config);
        errorEmoji = ERROR_EMOJI.getString(config);
        loadingEmoji = LOADING_EMOJI.getString(config);
        searchingEmoji = SEARCHING_EMOJI.getString(config);
        game = OtherUtil.parseGame(GAME.getString(config));
        status = OtherUtil.parseStatus(STATUS.getString(config));
        stayInChannel = STAY_IN_CHANNEL.getBoolean(config);
        songInGame = SONG_IN_GAME.getBoolean(config);
        npImages = NP_IMAGES.getBoolean(config);
        npMinimalMessage = NP_MINIMAL_MESSAGE.getBoolean(config);
        npShowButtons = NP_SHOW_BUTTONS.getBoolean(config);
        showNpProgressBar = NP_SHOW_PROGRESS_BAR.getBoolean(config);
        updatealerts = UPDATE_ALERTS.getBoolean(config);
        logLevel = LOG_LEVEL.getString(config);
        useEval = USE_EVAL.getBoolean(config);
        evalEngine = EVAL_ENGINE.getString(config);
        maxSeconds = MAX_SECONDS.getLong(config);
        maxYTPlaylistPages = MAX_YT_PLAYLIST_PAGES.getInt(config);
        maxHistorySize = MAX_HISTORY_SIZE.getInt(config);
        useYouTubeOauth = USE_YOUTUBE_OAUTH.getBoolean(config);
        aloneTimeUntilStop = ALONE_TIME_UNTIL_STOP.getLong(config);
        playlistsFolder = PLAYLISTS_FOLDER.getString(config);
        aliases = ALIASES.getConfig(config);
        transforms = TRANSFORMS.getConfig(config);
        
        // Handle audiosources - pass migrated user config to check which sources were explicitly set
        loadAudioSources(config, migratedUserConfig);
        
        skipratio = SKIP_RATIO.getDouble(config);
        clearChannelDeleteLimit = Math.max(0, CLEAR_CHANNEL_DELETE_LIMIT.getInt(config));
        clearChannelAgeDays = Math.max(0L, CLEAR_CHANNEL_AGE_DAYS.getLong(config));
        dbots = owner == 113156185389092864L;
        
        // GUI options
        guiEnabled = GUI_ENABLED.hasValue(config) ? GUI_ENABLED.getBoolean(config) : true;
        guiTheme = GUI_THEME.getString(config);
        guiFontSize = GUI_FONT_SIZE.getInt(config);
        // Left blank the window follows the bot's language, which is the right default: most
        // people run the bot in the language they read. Set, it wins — the operator watching
        // this window is not necessarily in any of the servers the bot serves.
        // Defaults chosen to be the safe ones. Reaching the panel from another device and
        // letting it write the config are both reasonable things to want, but both widen what
        // a leaked token is worth, so neither happens unless it was asked for.
        webBindAddress = WEB_BIND_ADDRESS.hasValue(config) ? WEB_BIND_ADDRESS.getString(config) : "127.0.0.1";
        webAllowConfigEdit = WEB_ALLOW_CONFIG_EDIT.hasValue(config) && WEB_ALLOW_CONFIG_EDIT.getBoolean(config);
        guiLanguage = GUI_LANGUAGE.hasValue(config)
                ? Language.fromCode(GUI_LANGUAGE.getString(config)).orElse(defaultLanguage)
                : defaultLanguage;
        // Kept alongside the resolved guiLanguage above rather than instead of it: a blank
        // gui.language means "follow ui.language" and resolving it eagerly (as guiLanguage
        // does, for every other reader) would make an editor showing only the resolved value
        // unable to tell "explicitly pinned to English" from "left blank, currently English
        // because that's what ui.language happens to be" — and silently pin it on next save.
        guiLanguageRaw = GUI_LANGUAGE.hasValue(config) ? GUI_LANGUAGE.getString(config) : "";
        
        // Performance options
        nasBufferMs = NAS_BUFFER_MS.getInt(config);
        frameBufferMs = FRAME_BUFFER_MS.getInt(config);
        
        // Proxy options
        proxyHost = PROXY_HOST.hasValue(config) ? PROXY_HOST.getString(config) : "";
        proxyPort = PROXY_PORT.hasValue(config) ? PROXY_PORT.getInt(config) : 0;
        proxyLavaplayer = PROXY_LAVAPLAYER.hasValue(config) && PROXY_LAVAPLAYER.getBoolean(config);
        proxyJda = PROXY_JDA.hasValue(config) && PROXY_JDA.getBoolean(config);
        proxyGithub = PROXY_GITHUB.hasValue(config) && PROXY_GITHUB.getBoolean(config);
        
        // Log proxy configuration if enabled
        if (hasProxy()) {
            if (proxyLavaplayer || proxyJda || proxyGithub) {
                LOGGER.info("Proxy configured: {}:{} [lavaplayer={}, jda={}, github={}]",
                        proxyHost, proxyPort, proxyLavaplayer, proxyJda, proxyGithub);
            }
        }

        // Playback engine (Lavaplayer vs. Lavalink)
        String rawEngine = PLAYBACK_ENGINE.hasValue(config) ? PLAYBACK_ENGINE.getString(config) : "lavaplayer";
        playbackEngine = PlaybackEngine.resolve(rawEngine, LOGGER);
        // Kept alongside the resolved playbackEngine above for the same reason guiLanguageRaw is
        // kept alongside guiLanguage: PlaybackEngine.resolve() collapses "fallback" (and any
        // garbage value) into LAVAPLAYER, which is correct for what the bot actually runs but
        // would make a config editor that reads only the resolved engine unable to show
        // "fallback" back to someone who typed it — the UI is supposed to agree with what the
        // log already told them, not paper over it. Normalized to one of the three known values
        // so the combo box always has a matching entry to select.
        String normalizedRawEngine = rawEngine == null ? "" : rawEngine.trim().toLowerCase(Locale.ROOT);
        playbackEngineRaw = switch (normalizedRawEngine) {
            case "lavaplayer", "lavalink", "fallback" -> normalizedRawEngine;
            default -> "lavaplayer";
        };
        lavalinkNodes = LavalinkNodeConfig.parseList(config, LOGGER);
        if (playbackEngine == PlaybackEngine.LAVALINK && lavalinkNodes.isEmpty()) {
            LOGGER.error("playback.engine = \"lavalink\" but no valid lavalink.nodes are configured "
                    + "(or all entries failed validation - see the errors above). "
                    + "Falling back to \"lavaplayer\" until at least one valid node is configured.");
            playbackEngine = PlaybackEngine.LAVAPLAYER;
        }
        if (playbackEngine == PlaybackEngine.LAVALINK) {
            LOGGER.info("Playback engine: lavalink, node(s): {}",
                    lavalinkNodes.stream().map(LavalinkNodeConfig::describe).collect(Collectors.toList()));
        }
    }
    
    /**
     * Loads audio sources configuration.
     * All sources are enabled by default (from reference.conf).
     * Users can disable specific sources by setting them to false.
     */
    private void loadAudioSources(Config config, Config migratedUserConfig) {
        if (AUDIO_SOURCES.hasValue(config)) {
            try {
                Config audioSourcesConfig = AUDIO_SOURCES.getConfig(config);
                Set<AudioSource> enabled = new java.util.LinkedHashSet<>();
                
                // Iterate sources in priority order (platform-specific first, catch-alls last)
                // This ensures the LinkedHashSet maintains the correct registration order
                for (AudioSource source : AudioSource.valuesSortedByPriority()) {
                    String sourceKey = source.getConfigName();
                    if (audioSourcesConfig.hasPath(sourceKey) && audioSourcesConfig.getBoolean(sourceKey)) {
                        enabled.add(source);
                    }
                }
                
                // If no sources ended up enabled (all set to false), enable all sources
                enabledAudioSources = enabled.isEmpty() ? allAudioSourcesInOrder() : enabled;
            } catch (ConfigException e) {
                LOGGER.warn("Failed to parse audioSources config, defaulting to all enabled: {}", e.getMessage());
                enabledAudioSources = allAudioSourcesInOrder();
            }
        } else {
            // Key not found, enable all sources
            enabledAudioSources = allAudioSourcesInOrder();
        }
        
        LOGGER.info("Enabled audio sources: {}", 
                    enabledAudioSources.stream()
                            .map(AudioSource::getConfigName)
                            .collect(Collectors.toList()));
    }
    
    /**
     * Returns all audio sources sorted by registration priority.
     * Platform-specific sources come first, catch-all sources (HTTP, LOCAL) come last.
     */
    private static Set<AudioSource> allAudioSourcesInOrder() {
        return new java.util.LinkedHashSet<>(AudioSource.valuesSortedByPriority());
    }

    private void writeToFile() {
        try {
            String content = ConfigIO.loadDefaultConfig()
                    .replace("BOT_TOKEN_HERE", token)
                    .replace("0 # OWNER ID", Long.toString(owner))
                    .trim();
            ConfigIO.writeConfigFile(path, content);
        } catch (Exception ex) {
            userInteraction.alert(Level.WARNING, "Config", "Failed to write new config options to config.txt: " + ex
                    + "\nPlease make sure that the files are not on your desktop or some other restricted area.\n\nConfig Location: "
                    + path.toAbsolutePath().toString());
        }
    }

    /**
     * Generates a default configuration file.
     * 
     * @param userInteraction The user interaction handler for displaying progress and errors
     */
    public static void writeDefaultConfig(UserInteraction userInteraction) {
        userInteraction.alert(Level.INFO, "NextVoiceCord Config", "Generating default config file");
        Path path = ConfigIO.getConfigPath();
        try {
            userInteraction.alert(Level.INFO, "NextVoiceCord Config",
                    "Writing default config file to " + path.toAbsolutePath().toString());
            ConfigIO.writeConfigFile(path, ConfigIO.loadDefaultConfig());
        } catch (Exception ex) {
            userInteraction.alert(Level.ERROR, "NextVoiceCord Config",
                    "An error occurred writing the default config file: " + ex.getMessage());
        }
    }

    public boolean isValid() {
        return valid;
    }

    public String getConfigLocation() {
        return path.toFile().getAbsolutePath();
    }

    /** Default language for servers that have not chosen one. */
    public Language getDefaultLanguage() {
        return defaultLanguage;
    }

    /** Repository checked for new releases, as owner/name. */
    public String getUpdateRepository() {
        return updateRepository;
    }

    /** GitHub token for release lookups; empty for a public repository. */
    public String getUpdateGithubToken() {
        return updateGithubToken;
    }

    /** Whether the bot installs new releases and restarts into them by itself. */
    public boolean isAutoUpdate() {
        return autoUpdate;
    }

    /** Hours between update checks. */
    public int getUpdateIntervalHours() {
        return updateIntervalHours;
    }

    /** Proxy username, or empty when the proxy needs no authentication. */
    public String getProxyUsername() {
        return proxyUsername;
    }

    /** Proxy password, or empty when the proxy needs no authentication. */
    public String getProxyPassword() {
        return proxyPassword;
    }

    /** Proof-of-origin token, or empty if none is configured. */
    public String getYoutubePoToken() {
        return youtubePoToken;
    }

    /** Visitor id the poToken was minted for. */
    public String getYoutubeVisitorData() {
        return youtubeVisitorData;
    }

    /** InnerTube clients to try, in order. */
    public java.util.List<String> getYoutubeClients() {
        return youtubeClients;
    }

    /** Whether a usable poToken pair is configured. */
    public boolean hasYoutubePoToken() {
        return youtubePoToken != null && !youtubePoToken.isBlank()
                && youtubeVisitorData != null && !youtubeVisitorData.isBlank();
    }

    /** Spotify Web API client ID, or empty if Spotify link support is not configured. */
    public String getSpotifyClientId() {
        return spotifyClientId;
    }

    /** Spotify Web API client secret, or empty if Spotify link support is not configured. */
    public String getSpotifyClientSecret() {
        return spotifyClientSecret;
    }

    /** Whether both Spotify credentials are set, enabling Spotify link support. */
    public boolean hasSpotifyCredentials() {
        return spotifyClientId != null && !spotifyClientId.isBlank()
                && spotifyClientSecret != null && !spotifyClientSecret.isBlank();
    }

    public String getPrefix() {
        return prefix;
    }

    public String getAltPrefix() {
        return "NONE".equalsIgnoreCase(altprefix) ? null : altprefix;
    }

    public String getToken() {
        return token;
    }

    public double getSkipRatio() {
        return skipratio;
    }

    public int getClearChannelDeleteLimit() {
        return clearChannelDeleteLimit;
    }

    public long getClearChannelAgeDays() {
        return clearChannelAgeDays;
    }

    public long getOwnerId() {
        return owner;
    }

    public String getSuccess() {
        return successEmoji;
    }

    public String getWarning() {
        return warningEmoji;
    }

    public String getError() {
        return errorEmoji;
    }

    public String getLoading() {
        return loadingEmoji;
    }

    public String getSearching() {
        return searchingEmoji;
    }

    public Activity getGame() {
        return game;
    }

    public boolean isGameNone() {
        return game != null && game.getName().equalsIgnoreCase("none");
    }

    public OnlineStatus getStatus() {
        return status;
    }

    public String getHelp() {
        return helpWord;
    }

    public boolean getStay() {
        return stayInChannel;
    }

    public boolean getSongInStatus() {
        return songInGame;
    }

    public String getPlaylistsFolder() {
        return playlistsFolder;
    }

    public boolean getDBots() {
        return dbots;
    }

    public boolean useUpdateAlerts() {
        return updatealerts;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public boolean useEval() {
        return useEval;
    }

    public String getEvalEngine() {
        return evalEngine;
    }

    public boolean useNPImages() {
        return npImages;
    }

    public boolean useMinimalNowPlayingMessage() {
        return npMinimalMessage;
    }

    public boolean showNowPlayingButtons() {
        return npShowButtons;
    }

    public boolean showNpProgressBar() {
        return showNpProgressBar;
    }

    public long getMaxSeconds() {
        return maxSeconds;
    }

    public int getMaxYTPlaylistPages() {
        return maxYTPlaylistPages;
    }

    public int getMaxHistorySize() {
        return maxHistorySize;
    }

    public boolean useYouTubeOauth() {
        return useYouTubeOauth;
    }

    public String getMaxTime() {
        return TimeUtil.formatTime(maxSeconds * 1000);
    }

    public long getAloneTimeUntilStop() {
        return aloneTimeUntilStop;
    }

    public boolean isTooLong(AudioTrack track) {
        if (maxSeconds <= 0)
            return false;
        return Math.round(track.getDuration() / 1000.0) > maxSeconds;
    }

    public String[] getAliases(String command) {
        try {
            return aliases.getStringList(command).toArray(new String[0]);
        } catch (NullPointerException | ConfigException.Missing e) {
            return new String[0];
        }
    }

    public Config getTransforms() {
        return transforms;
    }

    /**
     * The raw commands.aliases config, for read-only display.
     *
     * <p>Per-command lookups should use {@link #getAliases(String)} instead — this exists for
     * a config editor that needs to show the whole nested structure at once rather than one
     * command's list at a time.
     */
    public Config getAliasesConfig() {
        return aliases;
    }


    public Set<AudioSource> getEnabledAudioSources() {
        return enabledAudioSources;
    }

    public boolean isAudioSourceEnabled(AudioSource source) {
        // If the set is empty, no sources are enabled
        if (enabledAudioSources.isEmpty())
            return false;
        return enabledAudioSources.contains(source);
    }

    public boolean getGuiEnabled() {
        return guiEnabled;
    }

    public String getGuiTheme() {
        return guiTheme;
    }

    public int getGuiFontSize() {
        return guiFontSize;
    }

    /** The interface the web panel binds to. */
    public String getWebBindAddress() {
        return webBindAddress == null || webBindAddress.isBlank() ? "127.0.0.1" : webBindAddress;
    }

    /** Whether the web panel may write config.txt. */
    public boolean isWebConfigEditAllowed() {
        return webAllowConfigEdit;
    }

    /** The language the desktop window is displayed in. */
    public Language getGuiLanguage() {
        return guiLanguage;
    }

    /**
     * The raw gui.language value: blank when unset, a language code when pinned.
     *
     * <p>Unlike {@link #getGuiLanguage()}, this is not resolved against ui.language, so a
     * config editor can round-trip "left blank" without turning it into "pinned to whatever
     * ui.language currently is" the next time it saves.
     */
    public String getGuiLanguageRaw() {
        return guiLanguageRaw == null ? "" : guiLanguageRaw;
    }

    public int getNasBufferMs() {
        return nasBufferMs;
    }
    
    public int getFrameBufferMs() {
        return frameBufferMs;
    }
    
    // Proxy getters
    
    /**
     * Returns true if a valid proxy is configured (non-empty host and port > 0).
     */
    public boolean hasProxy() {
        return proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0;
    }
    
    public String getProxyHost() {
        return proxyHost;
    }
    
    public int getProxyPort() {
        return proxyPort;
    }
    
    public boolean proxyLavaplayer() {
        return proxyLavaplayer;
    }
    
    public boolean proxyJda() {
        return proxyJda;
    }
    
    public boolean proxyGithub() {
        return proxyGithub;
    }

    // Playback engine getters

    /** Which audio backend to actually play through. Never {@code null}; never {@code FALLBACK}. */
    public PlaybackEngine getPlaybackEngine() {
        return playbackEngine;
    }

    /** Convenience for {@code getPlaybackEngine() == PlaybackEngine.LAVALINK}. */
    public boolean isLavalinkMode() {
        return playbackEngine == PlaybackEngine.LAVALINK;
    }

    /**
     * The raw {@code playback.engine} value: {@code "lavaplayer"}, {@code "lavalink"} or
     * {@code "fallback"}, never anything else and never {@code null}.
     *
     * <p>Unlike {@link #getPlaybackEngine()}, this does not collapse {@code "fallback"} (or an
     * unrecognised value) into {@code lavaplayer} — it is what a config editor should show
     * selected, so that setting {@code fallback} in config.txt and then opening the window shows
     * the window agreeing with what startup already logged, rather than silently reverting the
     * displayed choice to lavaplayer.
     */
    public String getPlaybackEngineRaw() {
        return playbackEngineRaw == null ? "lavaplayer" : playbackEngineRaw;
    }

    /**
     * Validated {@code lavalink.nodes}. Empty when none are configured or {@code playback.engine}
     * is not {@code lavalink}'s point of consuming this. Never {@code null}.
     */
    public java.util.List<LavalinkNodeConfig> getLavalinkNodes() {
        return lavalinkNodes;
    }
}
