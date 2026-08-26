/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
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

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.audio.AloneInVoiceHandler;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.AudioLoadWrapper;
import com.jagrosh.jmusicbot.audio.NowPlayingHandler;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.audio.TrackLoadingMonitor;
import com.jagrosh.jmusicbot.audio.lavalink.LavalinkPlaybackEngine;
import com.jagrosh.jmusicbot.config.model.PlaybackEngine;
import com.jagrosh.jmusicbot.entities.UserInteraction;
import javax.swing.JFrame;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.update.SelfUpdater;
import com.jagrosh.jmusicbot.update.UpdateChecker;
import com.jagrosh.jmusicbot.i18n.LanguageManager;
import com.jagrosh.jmusicbot.i18n.UserLanguageStore;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.service.SearchService;
import com.jagrosh.jmusicbot.utils.InstanceLock;
import com.jagrosh.jmusicbot.utils.YoutubeOauth2TokenHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class Bot
{
    private final EventWaiter waiter;
    private final ScheduledExecutorService threadpool;
    private final BotConfig config;
    private final SettingsManager settings;
    private final PlayerManager players;
    private final PlaylistLoader playlists;
    private final NowPlayingHandler nowplaying;
    private final AloneInVoiceHandler aloneInVoiceHandler;
    private final MusicService musicService;
    private final SearchService searchService;
    private final YoutubeOauth2TokenHandler youTubeOauth2TokenHandler;
    private final UserInteraction userInteraction;
    private final Instant startTime;
    private final AudioLoadWrapper audioLoadWrapper;
    private final LanguageManager languages;
    private final UserLanguageStore userLanguages;
    /**
     * Non-null only when {@code playback.engine = lavalink}. See {@link LavalinkPlaybackEngine}
     * for why this lives beside {@link #players} rather than replacing it — the two engines are
     * separate paths, not two implementations of one interface.
     */
    private final LavalinkPlaybackEngine lavalinkEngine;
    private SelfUpdater updater;
    /** Set when --web is used, so the window can offer a link to it. */
    private com.jagrosh.jmusicbot.web.WebPanel webPanel;
    private java.util.Optional<Integer> webPort = java.util.Optional.empty();
    
    private boolean shuttingDown = false;
    private JDA jda;
    private JFrame gui;
    private CommandClient commandClient;
    
    public Bot(EventWaiter waiter, BotConfig config, SettingsManager settings, UserInteraction userInteraction)
    {
        this.waiter = waiter;
        this.config = config;
        this.settings = settings;
        this.userInteraction = userInteraction;
        // Loaded before any subsystem that might need to report to a user, so no code
        // path can reach a message lookup before translations exist.
        this.languages = LanguageManager.load(config.getDefaultLanguage());
        this.userLanguages = new UserLanguageStore();
        this.playlists = new PlaylistLoader(config);
        this.threadpool = Executors.newSingleThreadScheduledExecutor();
        this.startTime = Instant.now();
        this.youTubeOauth2TokenHandler = new YoutubeOauth2TokenHandler();
        this.youTubeOauth2TokenHandler.init();
        this.players = new PlayerManager(this);
        // Delay init of the PlayerManager until the GUI has started
        // this.players.init();
        this.nowplaying = new NowPlayingHandler(this);
        this.nowplaying.init();
        this.aloneInVoiceHandler = new AloneInVoiceHandler(this);
        this.aloneInVoiceHandler.init();
        this.musicService = new MusicService(this);
        this.searchService = new SearchService(this);
        // Config load already normalizes an empty node list back to "lavaplayer" (BotConfig),
        // but this stays defensive rather than trusting that invariant blindly - a unit test
        // that hands Bot a hand-built/mocked BotConfig is not guaranteed to go through load().
        this.lavalinkEngine = config.getPlaybackEngine() == PlaybackEngine.LAVALINK
                && !config.getLavalinkNodes().isEmpty()
                ? new LavalinkPlaybackEngine(this, config.getLavalinkNodes())
                : null;

        // Initialize audio load wrapper - use NO_OP when GUI is disabled to avoid monitoring overhead
        this.audioLoadWrapper = isNoGUI() 
            ? AudioLoadWrapper.NO_OP 
            : TrackLoadingMonitor.getInstance();
    }
    
    public BotConfig getConfig()
    {
        return config;
    }

    /** The web panel, if --web was given. */
    public com.jagrosh.jmusicbot.web.WebPanel getWebPanel()
    {
        return webPanel;
    }

    public void setWebPanel(com.jagrosh.jmusicbot.web.WebPanel webPanel)
    {
        this.webPanel = webPanel;
    }

    /**
     * The port {@code --web} asked for, known before the panel is actually serving.
     *
     * <p>The panel itself only starts once Discord is connected, which is deliberate — it would
     * otherwise spend its first seconds reporting a bot in no servers playing nothing. But the
     * window is built before that, so asking {@link #getWebPanel()} whether to offer the
     * shortcut always answered no, and the button never appeared. The intent is known from the
     * start even though the server is not.
     */
    public java.util.Optional<Integer> getWebPort()
    {
        return webPort;
    }

    public void setWebPort(java.util.Optional<Integer> webPort)
    {
        this.webPort = webPort == null ? java.util.Optional.empty() : webPort;
    }

    /** Update checker, or null before JDA is connected. */
    public SelfUpdater getUpdater()
    {
        return updater;
    }

    /** Translation lookup. Prefer {@link #msg} for anything addressed to a guild. */
    public LanguageManager getLanguages()
    {
        return languages;
    }

    /**
     * Resolves a message key in {@code guild}'s language.
     *
     * <p>The single entry point message-producing code should use. Going through
     * {@code getLanguages().get(...)} directly means resolving the guild's language at the
     * call site, and the failure mode when that is forgotten is silent: everything renders
     * in English and looks correct to an English-speaking developer.
     *
     * @param guild     guild whose language applies; {@code null} uses the global default
     * @param key       dot-separated message key
     * @param arguments values for {0}, {1}, ... in order
     */
    public String msg(Guild guild, String key, Object... arguments)
    {
        return languages.get(resolveLanguage(guild, null), key, arguments);
    }

    /**
     * Resolves a message for a specific person.
     *
     * <p>Use this wherever the reply is addressed to one user — a command response, an
     * ephemeral error. A guild's language is the right default for text everyone sees, such
     * as the now-playing panel, but a reply to one person should be in the language that
     * person chose.
     *
     * @param guild guild for context; {@code null} outside a server
     * @param user  the person being addressed; {@code null} falls back to the guild
     */
    public String msgFor(Guild guild, net.dv8tion.jda.api.entities.User user,
                         String key, Object... arguments)
    {
        return languages.get(resolveLanguage(guild, user), key, arguments);
    }

    /**
     * Picks a language, most specific first.
     *
     * <p>A user's own choice wins over their server's, so a server can set a sensible default
     * for its members without overriding anyone who has stated a preference.
     */
    private Language resolveLanguage(Guild guild, net.dv8tion.jda.api.entities.User user)
    {
        if (user != null)
        {
            Language chosen = userLanguages.get(user.getIdLong()).orElse(null);
            if (chosen != null)
            {
                return chosen;
            }
        }
        return guild == null
                ? config.getDefaultLanguage()
                : settings.getSettings(guild).getLanguage(config);
    }

    /** Per-user language preferences. */
    public UserLanguageStore getUserLanguages()
    {
        return userLanguages;
    }
    
    public SettingsManager getSettingsManager()
    {
        return settings;
    }
    
    public EventWaiter getWaiter()
    {
        return waiter;
    }
    
    public ScheduledExecutorService getThreadpool()
    {
        return threadpool;
    }
    
    public PlayerManager getPlayerManager()
    {
        return players;
    }
    
    public PlaylistLoader getPlaylistLoader()
    {
        return playlists;
    }
    
    public NowPlayingHandler getNowplayingHandler()
    {
        return nowplaying;
    }

    public AloneInVoiceHandler getAloneInVoiceHandler()
    {
        return aloneInVoiceHandler;
    }

    public MusicService getMusicService()
    {
        return musicService;
    }

    /**
     * The Lavalink engine, or {@code null} when {@code playback.engine != lavalink}.
     *
     * <p>Callers that only run in Lavalink mode (the {@code lavalinkStageOneSupported} branch
     * of {@code MusicService}'s play/pause/stop/skip/volume methods, and
     * {@code MusicCommandValidator}) are only reached when this is non-null - see
     * {@link #getConfig()}{@code .isLavalinkMode()}.
     */
    public LavalinkPlaybackEngine getLavalinkEngine()
    {
        return lavalinkEngine;
    }

    public SearchService getSearchService()
    {
        return searchService;
    }

    /**
     * Gets the audio load wrapper for wrapping track load handlers.
     * Returns {@link AudioLoadWrapper#NO_OP} when GUI is disabled,
     * or {@link TrackLoadingMonitor} when GUI is enabled.
     *
     * @return the audio load wrapper
     */
    public AudioLoadWrapper getAudioLoadWrapper()
    {
        return audioLoadWrapper;
    }

    /**
     * Gets the TrackLoadingMonitor instance if monitoring is enabled.
     * Returns null when GUI is disabled (audioLoadWrapper is NO_OP).
     *
     * @return the TrackLoadingMonitor, or null if monitoring is disabled
     */
    public TrackLoadingMonitor getTrackLoadingMonitor()
    {
        return audioLoadWrapper instanceof TrackLoadingMonitor 
            ? (TrackLoadingMonitor) audioLoadWrapper 
            : null;
    }

    public UserInteraction getUserInteraction()
    {
        return userInteraction;
    }

    /**
     * Checks if running in no-GUI mode.
     * GUI is disabled when either the -Dnogui=true property is set,
     * or gui.enabled is set to false in the config.
     *
     * @return true if GUI is disabled, false if GUI is enabled
     */
    public boolean isNoGUI()
    {
        return userInteraction.isNoGUI() || !config.getGuiEnabled();
    }

    public JDA getJDA()
    {
        return jda;
    }
    
    public void closeAudioConnection(long guildId)
    {
        Guild guild = jda.getGuildById(guildId);
        if(guild!=null)
            threadpool.submit(() -> guild.getAudioManager().closeAudioConnection());
    }
    
    public void resetGame()
    {
        Activity game = config.getGame()==null || config.getGame().getName().equalsIgnoreCase("none") ? null : config.getGame();
        if(!Objects.equals(jda.getPresence().getActivity(), game))
            jda.getPresence().setActivity(game);
    }

    /**
     * Performs a full graceful shutdown with complete cleanup.
     * Use this for normal shutdowns (GUI close, /shutdown command).
     */
    public void shutdown()
    {
        if (shuttingDown)
            return;
        shuttingDown = true;

        if (lavalinkEngine != null)
            lavalinkEngine.shutdown();

        // Clean up audio connections first (before shutting down thread pool, as these may trigger events that use it)
        if (jda != null && jda.getStatus() != JDA.Status.SHUTTING_DOWN)
        {
            jda.getGuilds().stream().forEach(g ->
            {
                AudioHandler ah = (AudioHandler) g.getAudioManager().getSendingHandler();
                if (ah != null)
                {
                    ah.stopAndClear();
                    ah.getPlayer().destroy();
                }
                g.getAudioManager().closeAudioConnection();
            });
            jda.shutdown();
        }

        // Shut down thread pool after audio cleanup to avoid RejectedExecutionException
        threadpool.shutdownNow();

        if (gui != null)
            gui.dispose();
        InstanceLock.release();
        System.exit(0);
    }

    public void setJDA(JDA jda)
    {
        this.jda = jda;

        // Started here rather than in the constructor: deciding whether to install requires
        // knowing whether anything is playing, and that needs a live JDA connection.
        if (updater == null)
        {
            updater = new SelfUpdater(
                    this,
                    new UpdateChecker(config.getUpdateRepository(), config.getUpdateGithubToken()),
                    config.isAutoUpdate());
            updater.start(config.getUpdateIntervalHours());
        }
    }
    
    public void setGUI(JFrame gui)
    {
        this.gui = gui;
    }

    public void setCommandClient(CommandClient commandClient)
    {
        this.commandClient = commandClient;
    }

    public CommandClient getCommandClient()
    {
        return commandClient;
    }

    public YoutubeOauth2TokenHandler getYouTubeOauth2Handler() {
        return youTubeOauth2TokenHandler;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
}
