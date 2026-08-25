/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.jagrosh.jmusicbot.ui.controller.ControllerLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.jagrosh.jdautilities.command.GuildSettingsProvider;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.i18n.Language;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.Collection;
import java.util.Collections;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class Settings implements GuildSettingsProvider
{
    private static final Logger LOG = LoggerFactory.getLogger(Settings.class);

    private final SettingsManager manager;
    protected long textId;
    protected long voiceId;
    protected long roleId;
    private int volume;
    private String defaultPlaylist;
    private RepeatMode repeatMode;
    private QueueType queueType;
    private String prefix;
    private double skipRatio;
    private NowPlayingLayoutMode nowPlayingLayoutMode;
    private NowPlayingButtonsMode nowPlayingButtonsMode;

    /**
     * Bot language for this guild, or {@code null} to inherit the global default.
     *
     * <p>Set through {@link #setLanguage} rather than a constructor parameter. Both
     * constructors already take twelve positional arguments of mostly similar types, and a
     * thirteenth would be easy to pass in the wrong slot with no compiler error. Null-as-
     * inherit also matches how {@code prefix} and {@code defaultPlaylist} already behave.
     */
    private Language language;

    /**
     * This guild's controller layout, or {@code null} to use the built-in default.
     *
     * <p>Held as parsed JSON rather than a {@link ControllerLayout} so the settings file can
     * round-trip a layout it does not fully understand — a layout written for a newer version
     * survives being loaded and saved by an older one instead of being silently flattened.
     */
    private JsonNode controllerLayoutJson;

    public Settings(SettingsManager manager, String textId, String voiceId, String roleId, int volume, String defaultPlaylist, RepeatMode repeatMode, String prefix, double skipRatio, QueueType queueType, NowPlayingLayoutMode nowPlayingLayoutMode, NowPlayingButtonsMode nowPlayingButtonsMode)
    {
        this.manager = manager;
        try
        {
            this.textId = Long.parseLong(textId);
        }
        catch(NumberFormatException e)
        {
            this.textId = 0;
        }
        try
        {
            this.voiceId = Long.parseLong(voiceId);
        }
        catch(NumberFormatException e)
        {
            this.voiceId = 0;
        }
        try
        {
            this.roleId = Long.parseLong(roleId);
        }
        catch(NumberFormatException e)
        {
            this.roleId = 0;
        }
        this.volume = volume;
        this.defaultPlaylist = defaultPlaylist;
        this.repeatMode = repeatMode;
        this.prefix = prefix;
        this.skipRatio = skipRatio;
        this.queueType = queueType;
        this.nowPlayingLayoutMode = nowPlayingLayoutMode;
        this.nowPlayingButtonsMode = nowPlayingButtonsMode;
    }
    
    public Settings(SettingsManager manager, long textId, long voiceId, long roleId, int volume, String defaultPlaylist, RepeatMode repeatMode, String prefix, double skipRatio, QueueType queueType, NowPlayingLayoutMode nowPlayingLayoutMode, NowPlayingButtonsMode nowPlayingButtonsMode)
    {
        this.manager = manager;
        this.textId = textId;
        this.voiceId = voiceId;
        this.roleId = roleId;
        this.volume = volume;
        this.defaultPlaylist = defaultPlaylist;
        this.repeatMode = repeatMode;
        this.prefix = prefix;
        this.skipRatio = skipRatio;
        this.queueType = queueType;
        this.nowPlayingLayoutMode = nowPlayingLayoutMode;
        this.nowPlayingButtonsMode = nowPlayingButtonsMode;
    }
    
    // Getters
    public TextChannel getTextChannel(Guild guild)
    {
        return guild == null ? null : guild.getTextChannelById(textId);
    }
    
    public VoiceChannel getVoiceChannel(Guild guild)
    {
        return guild == null ? null : guild.getVoiceChannelById(voiceId);
    }
    
    public Role getRole(Guild guild)
    {
        return guild == null ? null : guild.getRoleById(roleId);
    }
    
    public int getVolume()
    {
        return volume;
    }
    
    public String getDefaultPlaylist()
    {
        return defaultPlaylist;
    }
    
    public RepeatMode getRepeatMode()
    {
        return repeatMode;
    }
    
    public String getPrefix()
    {
        return prefix;
    }
    
    public double getSkipRatio()
    {
        return skipRatio;
    }

    public QueueType getQueueType()
    {
        return queueType;
    }

    public NowPlayingLayoutMode getNowPlayingLayoutMode()
    {
        return nowPlayingLayoutMode;
    }

    public NowPlayingButtonsMode getNowPlayingButtonsMode()
    {
        return nowPlayingButtonsMode;
    }

    public boolean useMinimalNowPlayingMessage(BotConfig config)
    {
        return nowPlayingLayoutMode.resolve(config.useMinimalNowPlayingMessage());
    }

    public boolean showNowPlayingButtons(BotConfig config)
    {
        return nowPlayingButtonsMode.resolve(config.showNowPlayingButtons());
    }

    /**
     * This guild's configured language, or {@code null} if it inherits the global default.
     *
     * <p>Callers rendering a message want {@link #getLanguage(BotConfig)}, which resolves the
     * inheritance. This accessor exists so the settings UI can tell "explicitly set to
     * English" apart from "never configured", and so persistence only writes an override.
     */
    public Language getLanguageOverride()
    {
        return language;
    }

    /**
     * The controller layout to render for this guild.
     *
     * <p>Parsed on each call rather than cached: layouts are small, the panel is rebuilt at
     * most every few seconds, and caching would need invalidating the moment an admin edits
     * one — a stale panel after an edit is exactly the bug people report.
     */
    public ControllerLayout getControllerLayout()
    {
        return ControllerLayout.parse(controllerLayoutJson,
                warning -> LOG.warn("Controller layout for guild {}: {}", "custom", warning));
    }

    /** Raw layout JSON, or null when this guild uses the default. Used by persistence. */
    public JsonNode getControllerLayoutJson()
    {
        return controllerLayoutJson;
    }

    /** Replaces this guild's layout; null restores the default. */
    public void setControllerLayoutJson(JsonNode layout)
    {
        this.controllerLayoutJson = layout;
        this.manager.writeSettings();
    }

    /** Sets the layout without persisting, for use while loading from disk. */
    void applyLoadedControllerLayout(JsonNode layout)
    {
        this.controllerLayoutJson = layout;
    }

    /** This guild's effective language, falling back to the global default. */
    public Language getLanguage(BotConfig config)
    {
        return language != null ? language : config.getDefaultLanguage();
    }

    @Override
    public Collection<String> getPrefixes()
    {
        return prefix == null ? Collections.emptySet() : Collections.singleton(prefix);
    }
    
    // Setters
    public void setTextChannel(TextChannel tc)
    {
        this.textId = tc == null ? 0 : tc.getIdLong();
        this.manager.writeSettings();
    }
    
    public void setVoiceChannel(VoiceChannel vc)
    {
        this.voiceId = vc == null ? 0 : vc.getIdLong();
        this.manager.writeSettings();
    }
    
    public void setDJRole(Role role)
    {
        this.roleId = role == null ? 0 : role.getIdLong();
        this.manager.writeSettings();
    }
    
    public void setVolume(int volume)
    {
        this.volume = volume;
        this.manager.writeSettings();
    }
    
    public void setDefaultPlaylist(String defaultPlaylist)
    {
        this.defaultPlaylist = defaultPlaylist;
        this.manager.writeSettings();
    }
    
    public void setRepeatMode(RepeatMode mode)
    {
        this.repeatMode = mode;
        this.manager.writeSettings();
    }
    
    public void setPrefix(String prefix)
    {
        this.prefix = prefix;
        this.manager.writeSettings();
    }

    /**
     * Sets this guild's language.
     *
     * @param language the language, or {@code null} to inherit the global default
     */
    public void setLanguage(Language language)
    {
        this.language = language;
        this.manager.writeSettings();
    }

    /**
     * Sets the language without persisting, for use while loading from disk.
     *
     * <p>{@link #setLanguage} writes the settings file on every call, which during startup
     * would mean rewriting the file once per guild for values that were just read from it.
     */
    void applyLoadedLanguage(Language language)
    {
        this.language = language;
    }

    public void setSkipRatio(double skipRatio)
    {
        this.skipRatio = skipRatio;
        this.manager.writeSettings();
    }

    public void setQueueType(QueueType queueType)
    {
        this.queueType = queueType;
        this.manager.writeSettings();
    }

    public void setNowPlayingLayoutMode(NowPlayingLayoutMode nowPlayingLayoutMode)
    {
        this.nowPlayingLayoutMode = nowPlayingLayoutMode;
        this.manager.writeSettings();
    }

    public void setNowPlayingButtonsMode(NowPlayingButtonsMode nowPlayingButtonsMode)
    {
        this.nowPlayingButtonsMode = nowPlayingButtonsMode;
        this.manager.writeSettings();
    }
}
