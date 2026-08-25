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
package com.jagrosh.jmusicbot.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jagrosh.jdautilities.command.GuildSettingsManager;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class SettingsManager implements GuildSettingsManager<Settings>
{
    private final static Logger LOG = LoggerFactory.getLogger(SettingsManager.class);
    private final static String SETTINGS_FILE = "serversettings.json";
    private final HashMap<Long,Settings> settings;
    private final static ObjectMapper objectMapper = new ObjectMapper();

    public SettingsManager()
    {
        this.settings = new HashMap<>();

        try {
            String jsonContent = new String(Files.readAllBytes(OtherUtil.getPath(SETTINGS_FILE)));
            JsonNode loadedSettings = objectMapper.readTree(jsonContent);
            
            if (loadedSettings != null && loadedSettings.isObject()) {
                for (Map.Entry<String, JsonNode> entry : loadedSettings.properties()) {
                    String id = entry.getKey();
                    JsonNode o = entry.getValue();

                    // Legacy version support: On versions 0.3.3 and older, the repeat mode was represented as a boolean.
                    if (!o.has("repeat_mode") && o.has("repeat") && o.get("repeat").asBoolean()) {
                        ((ObjectNode) o).put("repeat_mode", RepeatMode.ALL.name());
                    }

                    settings.put(Long.parseLong(id), new Settings(this,
                            o.has("text_channel_id") ? o.get("text_channel_id").asText()            : null,
                            o.has("voice_channel_id")? o.get("voice_channel_id").asText()           : null,
                            o.has("dj_role_id")      ? o.get("dj_role_id").asText()                 : null,
                            o.has("volume")          ? o.get("volume").asInt()                        : 100,
                            o.has("default_playlist")? o.get("default_playlist").asText()           : null,
                            o.has("repeat_mode")     ? RepeatMode.valueOfOrDefault(o.get("repeat_mode").asText(), RepeatMode.OFF) : RepeatMode.OFF,
                            o.has("prefix")          ? o.get("prefix").asText()                     : null,
                            o.has("skip_ratio")      ? o.get("skip_ratio").asDouble()                 : -1,
                            o.has("queue_type")      ? QueueType.valueOfOrDefault(o.get("queue_type").asText(), QueueType.FAIR)  : QueueType.FAIR,
                            o.has("now_playing_layout_mode")
                                    ? NowPlayingLayoutMode.valueOfOrDefault(o.get("now_playing_layout_mode").asText(), NowPlayingLayoutMode.INHERIT)
                                    : NowPlayingLayoutMode.INHERIT,
                            o.has("now_playing_buttons_mode")
                                    ? NowPlayingButtonsMode.valueOfOrDefault(o.get("now_playing_buttons_mode").asText(), NowPlayingButtonsMode.INHERIT)
                                    : NowPlayingButtonsMode.INHERIT));
                }
            }
        } catch (NoSuchFileException e) {
            // create an empty json file
            try {
                LOG.info("serversettings.json will be created in " + OtherUtil.getPath("serversettings.json").toAbsolutePath());
                ObjectNode emptyJson = JsonNodeFactory.instance.objectNode();
                Files.write(OtherUtil.getPath("serversettings.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(emptyJson));
            } catch(IOException ex) {
                LOG.warn("Failed to create new settings file: "+ex);
            }
            return;
        } catch(IOException e) {
            LOG.warn("Failed to load server settings: "+e);
        }

        LOG.info("serversettings.json loaded from " + OtherUtil.getPath("serversettings.json").toAbsolutePath());
    }

    /**
     * Gets non-null settings for a Guild
     *
     * @param guild the guild to get settings for
     * @return the existing settings, or new settings for that guild
     */
    @Override
    public Settings getSettings(Guild guild)
    {
        return getSettings(guild.getIdLong());
    }

    public Settings getSettings(long guildId)
    {
        return settings.computeIfAbsent(guildId, id -> createDefaultSettings());
    }

    private Settings createDefaultSettings()
    {
        return new Settings(this, 0, 0, 0, 100, null, RepeatMode.OFF, null, -1, QueueType.FAIR,
                NowPlayingLayoutMode.INHERIT, NowPlayingButtonsMode.INHERIT);
    }

    protected void writeSettings()
    {
        ObjectNode obj = JsonNodeFactory.instance.objectNode();
        settings.keySet().stream().forEach(key -> {
            ObjectNode o = JsonNodeFactory.instance.objectNode();
            Settings s = settings.get(key);
            if(s.textId!=0)
                o.put("text_channel_id", Long.toString(s.textId));
            if(s.voiceId!=0)
                o.put("voice_channel_id", Long.toString(s.voiceId));
            if(s.roleId!=0)
                o.put("dj_role_id", Long.toString(s.roleId));
            if(s.getVolume()!=100)
                o.put("volume", s.getVolume());
            if(s.getDefaultPlaylist() != null)
                o.put("default_playlist", s.getDefaultPlaylist());
            if(s.getRepeatMode()!=RepeatMode.OFF)
                o.put("repeat_mode", s.getRepeatMode().name());
            if(s.getPrefix() != null)
                o.put("prefix", s.getPrefix());
            if(s.getSkipRatio() != -1)
                o.put("skip_ratio", s.getSkipRatio());
            if(s.getQueueType() != QueueType.FAIR)
                o.put("queue_type", s.getQueueType().name());
            if(s.getNowPlayingLayoutMode() != NowPlayingLayoutMode.INHERIT)
                o.put("now_playing_layout_mode", s.getNowPlayingLayoutMode().name());
            if(s.getNowPlayingButtonsMode() != NowPlayingButtonsMode.INHERIT)
                o.put("now_playing_buttons_mode", s.getNowPlayingButtonsMode().name());
            obj.set(Long.toString(key), o);
        });
        try {
            // Use Jackson with pretty printing (indentation)
            Files.write(OtherUtil.getPath(SETTINGS_FILE), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(obj));
        } catch(IOException ex){
            LOG.warn("Failed to write to file: "+ex);
        }
    }
}
