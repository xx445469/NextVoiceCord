/*
 * Copyright 2026 Arif Banai (arif-banai)
 *
 * Modifications copyright 2026 adan (xx445469) - NextVoiceCord.
 * Changes: exposed buildYoutubeClients() so the YouTube smoke check can
 * exercise the exact same client list the bot uses at runtime; added
 * proof-of-origin token support and a configurable client list.
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
package com.jagrosh.jmusicbot.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.nico.NicoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.AndroidMusicWithThumbnail;
import dev.lavalink.youtube.clients.AndroidWithThumbnail;
import dev.lavalink.youtube.clients.IosWithThumbnail;
import dev.lavalink.youtube.clients.MusicWithThumbnail;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbeddedWithThumbnail;
import dev.lavalink.youtube.clients.AndroidVrWithThumbnail;
import dev.lavalink.youtube.clients.ClientOptions;
import dev.lavalink.youtube.clients.MWebWithThumbnail;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.TvHtml5SimplyWithThumbnail;
import dev.lavalink.youtube.clients.WebWithThumbnail;
import dev.lavalink.youtube.clients.skeleton.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.spotify.SpotifyAudioSourceManager;
import com.jagrosh.jmusicbot.utils.OtherUtil;

/**
 * Enum representing available audio sources that can be listed in the config file.
 * 
 * <p><b>Registration Order Matters:</b> Sources are registered with the audio player manager
 * in order of their {@link #getRegistrationPriority() registrationPriority}. When a URL is
 * played, sources are checked in registration order until one claims the URL. This means:
 * <ul>
 *   <li>Platform-specific sources (YouTube, SoundCloud, etc.) should have LOW priority numbers
 *       so they are registered FIRST and get first chance to claim their URLs</li>
 *   <li>Catch-all sources (HTTP, LOCAL) should have HIGH priority numbers so they are
 *       registered LAST and only claim URLs that no specific source wanted</li>
 * </ul>
 * 
 * <p>If HTTP is registered before SoundCloud, it will claim SoundCloud URLs and fail to play them.
 * 
 * @author Arif Banai (arif-banai)
 */
public enum AudioSource
{
    // Platform-specific sources (priority 10-90) - registered first to claim their URLs
    YOUTUBE(
        "youtube",
        "YouTube videos and playlists",
        10,
        (manager, config) -> {
            YoutubeAudioSourceManager yt = setupYoutubeAudioSourceManager(config);
            manager.registerSourceManager(yt);
        }
    ),
    SPOTIFY(
        "spotify",
        "Spotify track/album/playlist/artist links, matched and played from YouTube",
        15,
        (manager, config) -> {
            if (config.hasSpotifyCredentials())
            {
                manager.registerSourceManager(new SpotifyAudioSourceManager(config));
            }
            else
            {
                LoggerFactory.getLogger(AudioSource.class).info(
                        "Spotify link support is off: set spotify.clientId and spotify.clientSecret to enable it.");
            }
        }
    ),
    SOUNDCLOUD(
        "soundcloud",
        "SoundCloud tracks",
        20,
        (manager, config) -> manager.registerSourceManager(SoundCloudAudioSourceManager.createDefault())
    ),
    BANDCAMP(
        "bandcamp",
        "Bandcamp albums and tracks",
        30,
        (manager, config) -> manager.registerSourceManager(new BandcampAudioSourceManager())
    ),
    VIMEO(
        "vimeo",
        "Vimeo videos",
        40,
        (manager, config) -> manager.registerSourceManager(new VimeoAudioSourceManager())
    ),
    TWITCH(
        "twitch",
        "Twitch streams",
        50,
        (manager, config) -> manager.registerSourceManager(new TwitchStreamAudioSourceManager())
    ),
    BEAM(
        "beam",
        "Beam.pro streams",
        60,
        (manager, config) -> manager.registerSourceManager(new BeamAudioSourceManager())
    ),
    GETYARN(
        "getyarn",
        "Getyarn.io clips",
        70,
        (manager, config) -> manager.registerSourceManager(new GetyarnAudioSourceManager())
    ),
    NICO(
        "nico",
        "NicoNico videos",
        80,
        (manager, config) -> manager.registerSourceManager(new NicoAudioSourceManager())
    ),
    
    // Catch-all sources (priority 100+) - registered last as fallbacks
    HTTP(
        "http",
        "Direct HTTP audio links",
        100,
        (manager, config) -> manager.registerSourceManager(new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY))
    ),
    LOCAL(
        "local",
        "Local file playback",
        110,
        (manager, config) -> AudioSourceManagers.registerLocalSource(manager)
    );

    private final String configName;
    private final String description;
    private final int registrationPriority;
    private final BiConsumer<DefaultAudioPlayerManager, BotConfig> registrationAction;

    AudioSource(String configName, String description, int registrationPriority, 
                BiConsumer<DefaultAudioPlayerManager, BotConfig> registrationAction)
    {
        this.configName = configName;
        this.description = description;
        this.registrationPriority = registrationPriority;
        this.registrationAction = registrationAction;
    }

    /**
     * Gets the configuration name for this audio source (used in config files).
     * 
     * @return the lowercase configuration name
     */
    public String getConfigName()
    {
        return configName;
    }

    /**
     * Gets a human-readable description of this audio source.
     * 
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }
    
    /**
     * Gets the registration priority for this audio source.
     * Lower numbers are registered first. Platform-specific sources should have
     * low priorities (10-90), while catch-all sources like HTTP should have
     * high priorities (100+).
     * 
     * @return the registration priority
     */
    public int getRegistrationPriority()
    {
        return registrationPriority;
    }
    
    /**
     * Returns all audio sources sorted by registration priority (lowest first).
     * This ensures platform-specific sources are registered before catch-all sources.
     * 
     * @return list of audio sources in registration order
     */
    public static java.util.List<AudioSource> valuesSortedByPriority()
    {
        return Arrays.stream(values())
                .sorted(java.util.Comparator.comparingInt(AudioSource::getRegistrationPriority))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Parses an audio source from its configuration name.
     * 
     * @param configName the configuration name (case-insensitive)
     * @return an Optional containing the AudioSource if found, empty otherwise
     */
    public static Optional<AudioSource> fromConfigName(String configName)
    {
        if(configName == null)
            return Optional.empty();
        
        return Arrays.stream(values())
                .filter(source -> source.configName.equalsIgnoreCase(configName.trim()))
                .findFirst();
    }
    
    /**
     * Registers this audio source with the given player manager.
     * 
     * @param manager the player manager to register with
     * @param config the bot configuration
     */
    public void register(DefaultAudioPlayerManager manager, BotConfig config)
    {
        registrationAction.accept(manager, config);
    }
    
    /**
     * Sets up and configures a YouTube audio source manager.
     * 
     * @param config the bot configuration
     * @return the configured YouTube audio source manager
     */
    private static YoutubeAudioSourceManager setupYoutubeAudioSourceManager(BotConfig config)
    {
        final Logger logger = LoggerFactory.getLogger(AudioSource.class);
        
        boolean useOauth = config.useYouTubeOauth();

        // Applied before any client is constructed: Web reads the static field when it builds
        // its request context, so setting it afterwards would leave the first requests
        // unauthenticated.
        applyPoToken(config, logger);

        YoutubeSourceOptions options = buildYoutubeOptions(config);
        Client[] clients = buildYoutubeClients(config, useOauth, logger);

        YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager(options, clients);
        yt.setPlaylistPageCount(config.getMaxYTPlaylistPages());

        if (useOauth)
        {
            applyOAuth(yt, logger);
        }
        return yt;
    }
    
    /**
     * Builds YouTube source options.
     */
    private static YoutubeSourceOptions buildYoutubeOptions(BotConfig config)
    {
        YoutubeSourceOptions options = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setAllowDirectVideoIds(true)
                .setAllowDirectPlaylistIds(true);
        
        if (config.useYouTubeOauth())
        {
            options.setRemoteCipher("https://cipher.kikkia.dev/", null, "jmusicbot");
        }
        
        return options;
    }
    
    /**
     * Builds the appropriate YouTube clients based on OAuth setting.
     * 
     * <p>When OAuth is enabled, we use a combination of clients:
     * <ul>
     *   <li><b>AndroidVrWithThumbnail</b> - Metadata loading (non-embedded, non-OAuth)</li>
     *   <li><b>MWebWithThumbnail</b> - Metadata loading (non-embedded, non-OAuth)</li>
     *   <li><b>Web</b> - Metadata loading (non-embedded, non-OAuth)</li>
     *   <li><b>Tv</b> - OAuth-compatible streaming-only client. Used as fallback for loading
     *       audio stream formats during playback.</li>
     *   <li><b>TvHtml5SimplyWithThumbnail</b> - *Not oAuth compatible* Used as fallback for loading
     *       audio stream formats during playback.</li>
     * </ul>
     * 
     * <p>
     *
     * <p><b>NextVoiceCord:</b> visibility widened from {@code private} to {@code public}
     * so {@code com.jagrosh.jmusicbot.diagnostics.YoutubeSmokeCheck} can verify this exact
     * client list. Keeping a single definition matters: a smoke check that duplicated the
     * list could pass while real playback fails.
     */
    /**
     * Supplies the proof-of-origin token, if one is configured.
     *
     * <p>YouTube increasingly refuses requests carrying neither a poToken nor an OAuth login,
     * either with a bot check or by returning only formats that cannot be played. This is the
     * mechanism upstream documents for it, and it is a no-op when unconfigured.
     */
    private static void applyPoToken(BotConfig config, Logger logger)
    {
        if (!config.hasYoutubePoToken())
        {
            return;
        }
        // Paired deliberately: a poToken is bound to the visitorData it was minted with and
        // is rejected alongside any other, so the config requires both or neither.
        Web.setPoTokenAndVisitorData(config.getYoutubePoToken(), config.getYoutubeVisitorData());
        logger.info("YouTube proof-of-origin token applied.");
    }

    /**
     * Builds the client list, from config where given.
     *
     * <p>YouTube breaks clients one at a time rather than all at once, so being able to
     * reorder this without rebuilding is what makes a same-day workaround possible.
     */
    public static Client[] buildYoutubeClients(BotConfig config, boolean useOauth, Logger logger)
    {
        java.util.List<String> names = config.getYoutubeClients();
        if (names == null || names.isEmpty())
        {
            return buildYoutubeClients(useOauth);
        }

        java.util.List<Client> clients = new java.util.ArrayList<>();
        for (String name : names)
        {
            Client client = clientByName(name, useOauth);
            if (client == null)
            {
                logger.warn("Unknown YouTube client '{}' in playback.youtube.clients; skipping it.", name);
                continue;
            }
            clients.add(client);
        }

        // ANDROID cannot load a playlist and upstream logs "broken with no known fix" on
        // startup, so a list built around it fails in a way whose cause is nowhere near the
        // error. Said here, next to the setting that caused it, rather than left for someone
        // to correlate two unrelated log lines twenty seconds apart.
        boolean canLoadPlaylists = names.stream()
                .map(n -> n.trim().toUpperCase(Locale.ROOT).replace("_", ""))
                .anyMatch(n -> n.equals("ANDROIDVR"));
        if (!clients.isEmpty() && !canLoadPlaylists)
        {
            logger.warn("playback.youtube.clients has no ANDROIDVR: playlist links will fail to load.");
            logger.warn("  YouTube changed its playlist response and ANDROIDVR is currently the only");
            logger.warn("  client that still reads it. Suggested: clients = [ANDROIDVR, IOS, MUSIC, WEB, TV]");
        }

        if (clients.isEmpty())
        {
            // Falling back rather than starting with no clients: an empty list would make
            // every YouTube request fail with an error that never mentions the config.
            logger.warn("No usable clients in playback.youtube.clients; using the built-in defaults.");
            return buildYoutubeClients(useOauth);
        }

        // Under OAuth, most clients are registered for metadata only — they cannot stream
        // while authenticated. If the configured list contains none that can, every playback
        // attempt fails and the only clue is a warning from deep inside youtube-source that
        // never mentions which setting caused it. Appending a capable client is better than
        // honouring a list that cannot work.
        if (useOauth && clients.stream().noneMatch(AudioSource::canStreamWithOauth))
        {
            clients.add(new Tv());
            logger.warn("playback.youtube.clients contains no OAuth-capable streaming client, "
                        + "so TV was added. Add TV or TVHTML5SIMPLY to the list to silence this.");
        }

        logger.info("YouTube clients: {}", names);
        return clients.toArray(new Client[0]);
    }

    /**
     * Whether a client can stream while OAuth is active.
     *
     * <p>The rest are registered with playback disabled under OAuth: they still resolve track
     * information, but YouTube will not serve them a stream for an authenticated session.
     */
    private static boolean canStreamWithOauth(Client client)
    {
        return client instanceof Tv || client instanceof TvHtml5SimplyWithThumbnail;
    }

    /** Maps a configured name to a client, or null if the name is not recognised. */
    private static Client clientByName(String name, boolean useOauth)
    {
        // Metadata-only options mirror the OAuth path below: those clients cannot stream
        // under OAuth, but are still the best at resolving track information.
        ClientOptions metadataOnly = new ClientOptions();
        metadataOnly.setPlayback(false);

        return switch (name.trim().toUpperCase(Locale.ROOT).replace("_", ""))
        {
            case "MUSIC"          -> new MusicWithThumbnail();
            case "WEB"            -> useOauth ? new WebWithThumbnail(metadataOnly) : new WebWithThumbnail();
            case "WEBEMBEDDED"    -> new WebEmbeddedWithThumbnail();
            case "ANDROID"        -> new AndroidWithThumbnail();
            case "ANDROIDVR"      -> useOauth ? new AndroidVrWithThumbnail(metadataOnly) : new AndroidVrWithThumbnail();
            case "ANDROIDMUSIC"   -> new AndroidMusicWithThumbnail();
            case "IOS"            -> new IosWithThumbnail();
            case "MWEB"           -> useOauth ? new MWebWithThumbnail(metadataOnly) : new MWebWithThumbnail();
            case "TV"             -> new Tv();
            case "TVHTML5SIMPLY"  -> new TvHtml5SimplyWithThumbnail();
            default               -> null;
        };
    }

    public static Client[] buildYoutubeClients(boolean useOauth)
    {
        if (useOauth)
        {
            // Clients configured for metadata loading only (no playback/streaming)
            // This handles direct URLs without embedded player restrictions
            ClientOptions metadataOnly = new ClientOptions();
            metadataOnly.setPlayback(false);
            
            return new Client[] {
                new AndroidVrWithThumbnail(metadataOnly), // metadata loading (non-embedded, non-OAuth)
                new MWebWithThumbnail(metadataOnly),      // metadata loading (non-embedded, non-OAuth)
                new WebWithThumbnail(metadataOnly),       // metadata loading (non-embedded, non-OAuth)
                new Tv(),
                new TvHtml5SimplyWithThumbnail()
            };
        }
        // ANDROID and IOS lead because they are what actually works. Measured on five
        // videos from a residential connection: both play every one, while WEB reaches
        // format selection and finds only SABR entries carrying no usable URL. Kept in
        // step with the default in reference.conf, so an unconfigured bot and the smoke
        // check behave identically.
        return new Client[] {
            new AndroidWithThumbnail(),
            new IosWithThumbnail(),
            new MusicWithThumbnail(),
            new WebWithThumbnail(),
            new TvHtml5SimplyWithThumbnail()
        };
    }
    
    /**
     * Reads OAuth token and applies it to the YouTube source manager.
     * If no token exists, triggers the OAuth device flow to obtain one.
     */
    private static void applyOAuth(YoutubeAudioSourceManager yt, Logger logger)
    {
        String token = readOAuthToken(logger);
        if (token != null)
        {
            logger.debug("Read YouTube OAuth2 refresh token from youtubetoken.txt");
        }
        
        try
        {
            // Call useOauth2 with null token to trigger the device flow.
            // When token is null, this initiates the OAuth device code flow which
            // logs the authorization URL and code that YoutubeOauth2TokenHandler captures.
            yt.useOauth2(token, false);
        }
        catch (Exception e)
        {
            logger.warn("Failed to authorise with YouTube. If this issue persists, delete the youtubetoken.txt file to reauthorise.", e);
        }
    }
    
    /**
     * Reads the OAuth token from file.
     * 
     * @return the token, or null if file doesn't exist or read failed
     */
    private static String readOAuthToken(Logger logger)
    {
        try
        {
            return Files.readString(OtherUtil.getPath("youtubetoken.txt"));
        }
        catch (NoSuchFileException e)
        {
            return null;
        }
        catch (IOException e)
        {
            logger.warn("Failed to read YouTube OAuth2 token file: {}", e.getMessage());
            return null;
        }
    }
}
