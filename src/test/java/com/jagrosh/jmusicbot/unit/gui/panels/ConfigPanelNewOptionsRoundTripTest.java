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
package com.jagrosh.jmusicbot.unit.gui.panels;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioSource;
import com.jagrosh.jmusicbot.gui.panels.ConfigPanel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The 28 previously-missing {@link com.jagrosh.jmusicbot.config.model.ConfigOption} fields added
 * to {@link ConfigPanel}, round-tripped through the panel's own field-loading and
 * save-building logic, the same way {@code ConfigPanelProxyRoundTripTest} covers the proxy
 * block.
 *
 * <p>Covers, deliberately, one of each kind the task called out: an int
 * (performance.nasBufferMs), a boolean (nowPlaying.showButtons), a string (updates.repository)
 * and a secret (discord.token) — plus the two controls built specifically to round-trip a
 * structure rather than a scalar: the ordered playback.youtube.clients editor and the
 * playback.audioSources checkboxes.
 */
@DisplayName("ConfigPanel new options")
class ConfigPanelNewOptionsRoundTripTest extends BaseConfigTest
{
    private static final String CONFIG_TEMPLATE = """
            meta {
              configVersion = 2
            }
            discord {
              token = old_token
              owner = 123456789
            }
            performance {
              nasBufferMs = 800
              frameBufferMs = 2000
            }
            nowPlaying {
              images = false
              minimalMessage = false
              showButtons = true
              showProgressBar = false
            }
            updates {
              alerts = true
              repository = "old/repo"
              autoUpdate = false
              checkIntervalHours = 6
              githubToken = ""
            }
            playback {
              youtube {
                poToken = ""
                visitorData = ""
                clients = [ "ANDROID", "IOS" ]
                useOAuth = false
              }
              audioSources {
                youtube = true
                soundcloud = true
                bandcamp = false
              }
              maxHistorySize = 40
            }
            """;

    @BeforeEach
    @Override
    protected void setUpBase()
    {
        super.setUpBase();
        // As in ConfigPanelProxyRoundTripTest: these tests build Swing components but never
        // show one, so headless keeps the result independent of the machine running it.
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    @DisplayName("loads the token, owner, an int, a boolean, a string, the client list and the audio source flags from config.txt")
    void loadsNewFieldsFromConfig() throws IOException, ReflectiveOperationException
    {
        ConfigPanel panel = buildPanel(CONFIG_TEMPLATE);

        assertEquals("old_token", passwordOf(panel, "discordTokenField"));
        assertEquals(123456789L, spinnerValue(panel, "discordOwnerSpinner"));
        assertEquals(800, spinnerValue(panel, "nasBufferMsSpinner"));
        assertTrue(checkBoxOf(panel, "npShowButtonsCheckBox"));
        assertEquals("old/repo", textOf(panel, "updateRepositoryField"));
        assertEquals(List.of("ANDROID", "IOS"), listModelValues(panel, "youtubeClientsModel"));
        assertTrue(audioSourceSelected(panel, AudioSource.YOUTUBE));
        assertTrue(audioSourceSelected(panel, AudioSource.SOUNDCLOUD));
        assertFalse(audioSourceSelected(panel, AudioSource.BANDCAMP));
    }

    @Test
    @DisplayName("edited fields survive buildUpdatesMap, the regex rewrite, and a fresh BotConfig load — and the token never reaches a log")
    void editedFieldsRoundTripThroughSave() throws Exception
    {
        Path configFile = createTempConfigFile(CONFIG_TEMPLATE);
        setConfigFileProperty(configFile);

        BotConfig config = new BotConfig(mockUserInteraction);
        config.load();

        Bot bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(config);

        ConfigPanel panel = new ConfigPanel(bot);

        String newToken = "brand-new-secret-token-value";
        setPassword(panel, "discordTokenField", newToken);
        setSpinner(panel, "nasBufferMsSpinner", 950);
        setCheckBox(panel, "npShowButtonsCheckBox", false);
        setText(panel, "updateRepositoryField", "newowner/newrepo");
        setListModelValues(panel, "youtubeClientsModel", List.of("TV", "MUSIC"));
        setAudioSourceSelected(panel, AudioSource.YOUTUBE, false);
        setAudioSourceSelected(panel, AudioSource.BANDCAMP, true);

        // The plaintext token only exists for the moment it takes to build this map and splice
        // it into the file content; nothing on that path should be logging it — same guarantee
        // ConfigPanelProxyRoundTripTest checks for the proxy password.
        Logger configPanelLogger = (Logger) org.slf4j.LoggerFactory.getLogger(ConfigPanel.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        configPanelLogger.addAppender(appender);

        Map<String, String> updates;
        String updatedContent;
        try
        {
            updates = buildUpdatesMap(panel);
            String originalContent = readFileContent(configFile);
            updatedContent = applyConfigUpdates(panel, originalContent, updates);
        }
        finally
        {
            configPanelLogger.detachAppender(appender);
        }

        for (ILoggingEvent event : appender.list)
        {
            assertFalse(event.getFormattedMessage().contains(newToken),
                    "Discord token leaked into a log message: " + event.getFormattedMessage());
        }

        assertEquals("\"" + newToken + "\"", updates.get("discord.token"));
        assertEquals("950", updates.get("performance.nasBufferMs"));
        assertEquals("false", updates.get("nowPlaying.showButtons"));
        assertEquals("\"newowner/newrepo\"", updates.get("updates.repository"));
        assertEquals("[ \"TV\", \"MUSIC\" ]", updates.get("playback.youtube.clients"));
        assertEquals("false", updates.get("playback.audioSources.youtube"));
        assertEquals("true", updates.get("playback.audioSources.bandcamp"));

        writeFileContent(configFile, updatedContent);

        // The real proof of a round trip: reloading through BotConfig, independent of the
        // panel entirely, sees exactly what was just typed/toggled/reordered in.
        BotConfig reloaded = new BotConfig(mockUserInteraction);
        reloaded.load();

        assertEquals(newToken, reloaded.getToken());
        assertEquals(950, reloaded.getNasBufferMs());
        assertFalse(reloaded.showNowPlayingButtons());
        assertEquals("newowner/newrepo", reloaded.getUpdateRepository());
        assertEquals(List.of("TV", "MUSIC"), reloaded.getYoutubeClients());
        assertFalse(reloaded.isAudioSourceEnabled(AudioSource.YOUTUBE));
        assertTrue(reloaded.isAudioSourceEnabled(AudioSource.BANDCAMP));
    }

    private ConfigPanel buildPanel(String content) throws IOException
    {
        Path configFile = createTempConfigFile(content);
        setConfigFileProperty(configFile);

        BotConfig config = new BotConfig(mockUserInteraction);
        config.load();

        Bot bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(config);

        return new ConfigPanel(bot);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> buildUpdatesMap(ConfigPanel panel) throws ReflectiveOperationException
    {
        Method method = ConfigPanel.class.getDeclaredMethod("buildUpdatesMap");
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(panel);
    }

    private static String applyConfigUpdates(ConfigPanel panel, String content, Map<String, String> updates)
            throws ReflectiveOperationException
    {
        Method method = ConfigPanel.class.getDeclaredMethod("applyConfigUpdates", String.class, Map.class);
        method.setAccessible(true);
        return (String) method.invoke(panel, content, updates);
    }

    private static Object fieldValue(ConfigPanel panel, String name) throws ReflectiveOperationException
    {
        Field field = ConfigPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(panel);
    }

    private static String textOf(ConfigPanel panel, String name) throws ReflectiveOperationException
    {
        return ((JTextField) fieldValue(panel, name)).getText();
    }

    private static String passwordOf(ConfigPanel panel, String name) throws ReflectiveOperationException
    {
        return new String(((JPasswordField) fieldValue(panel, name)).getPassword());
    }

    private static Object spinnerValue(ConfigPanel panel, String name) throws ReflectiveOperationException
    {
        return ((JSpinner) fieldValue(panel, name)).getValue();
    }

    private static boolean checkBoxOf(ConfigPanel panel, String name) throws ReflectiveOperationException
    {
        return ((JCheckBox) fieldValue(panel, name)).isSelected();
    }

    private static void setText(ConfigPanel panel, String name, String value) throws ReflectiveOperationException
    {
        ((JTextField) fieldValue(panel, name)).setText(value);
    }

    private static void setPassword(ConfigPanel panel, String name, String value) throws ReflectiveOperationException
    {
        ((JPasswordField) fieldValue(panel, name)).setText(value);
    }

    private static void setSpinner(ConfigPanel panel, String name, int value) throws ReflectiveOperationException
    {
        ((JSpinner) fieldValue(panel, name)).setValue(value);
    }

    private static void setCheckBox(ConfigPanel panel, String name, boolean value) throws ReflectiveOperationException
    {
        ((JCheckBox) fieldValue(panel, name)).setSelected(value);
    }

    @SuppressWarnings("unchecked")
    private static List<String> listModelValues(ConfigPanel panel, String name) throws ReflectiveOperationException
    {
        DefaultListModel<String> model = (DefaultListModel<String>) fieldValue(panel, name);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < model.size(); i++)
        {
            values.add(model.get(i));
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static void setListModelValues(ConfigPanel panel, String name, List<String> values)
            throws ReflectiveOperationException
    {
        DefaultListModel<String> model = (DefaultListModel<String>) fieldValue(panel, name);
        model.clear();
        for (String value : values)
        {
            model.addElement(value);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean audioSourceSelected(ConfigPanel panel, AudioSource source)
            throws ReflectiveOperationException
    {
        Map<AudioSource, JCheckBox> checkBoxes = (Map<AudioSource, JCheckBox>) fieldValue(panel, "audioSourceCheckBoxes");
        return checkBoxes.get(source).isSelected();
    }

    @SuppressWarnings("unchecked")
    private static void setAudioSourceSelected(ConfigPanel panel, AudioSource source, boolean selected)
            throws ReflectiveOperationException
    {
        Map<AudioSource, JCheckBox> checkBoxes = (Map<AudioSource, JCheckBox>) fieldValue(panel, "audioSourceCheckBoxes");
        checkBoxes.get(source).setSelected(selected);
    }
}
