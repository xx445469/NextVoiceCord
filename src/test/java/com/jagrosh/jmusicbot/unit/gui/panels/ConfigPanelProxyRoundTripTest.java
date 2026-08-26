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
import com.jagrosh.jmusicbot.gui.panels.ConfigPanel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The proxy fields added to {@link ConfigPanel}, round-tripped through the panel's own
 * field-loading and save-building logic — not just the {@link BotConfig} getters those fields
 * ultimately read from.
 *
 * <p>{@code saveConfiguration()} itself is not called here: on success it pops a
 * {@code JOptionPane}, which under a headless AWT throws rather than blocks, but on a real
 * display would sit waiting for a click that never comes. {@link ConfigPanel#buildUpdatesMap}
 * and the regex substitution it feeds are exercised directly instead — that is the part
 * specific to the proxy block, and the part a test that only calls {@link BotConfig} directly
 * would never touch.
 */
@DisplayName("ConfigPanel proxy fields")
class ConfigPanelProxyRoundTripTest extends BaseConfigTest
{
    private static final String CONFIG_TEMPLATE = """
            meta {
              configVersion = 2
            }
            discord.token = test_token
            discord.owner = 123456789
            proxy {
              host = "proxy.example.com"
              port = 8080
              username = "proxy_user"
              password = "s3cr3t!"
              lavaplayer = false
              jda = false
              github = true
            }
            """;

    @BeforeEach
    @Override
    protected void setUpBase()
    {
        super.setUpBase();
        // These tests build Swing components but never show one, so this keeps the result
        // from depending on whether the machine running it happens to have a display.
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    @DisplayName("loads host, port, credentials and routing flags from config.txt")
    void loadsProxyFieldsFromConfig() throws IOException, ReflectiveOperationException
    {
        ConfigPanel panel = buildPanel(CONFIG_TEMPLATE);

        assertEquals("proxy.example.com", textOf(panel, "proxyHostField"));
        assertEquals(8080, spinnerValue(panel, "proxyPortSpinner"));
        assertEquals("proxy_user", textOf(panel, "proxyUsernameField"));
        assertEquals("s3cr3t!", passwordOf(panel, "proxyPasswordField"));
        assertFalse(checkBoxOf(panel, "proxyLavaplayerCheckBox"));
        assertFalse(checkBoxOf(panel, "proxyJdaCheckBox"));
        assertTrue(checkBoxOf(panel, "proxyGithubCheckBox"));
    }

    @Test
    @DisplayName("edited proxy fields survive buildUpdatesMap, the regex rewrite, and a fresh BotConfig load")
    void editedProxyFieldsRoundTripThroughSave() throws Exception
    {
        Path configFile = createTempConfigFile(CONFIG_TEMPLATE);
        setConfigFileProperty(configFile);

        BotConfig config = new BotConfig(mockUserInteraction);
        config.load();

        Bot bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(config);

        ConfigPanel panel = new ConfigPanel(bot);

        setText(panel, "proxyHostField", "10.0.0.5");
        setSpinner(panel, "proxyPortSpinner", 9090);
        setText(panel, "proxyUsernameField", "new_user");
        setPassword(panel, "proxyPasswordField", "correct horse battery staple!");
        setCheckBox(panel, "proxyLavaplayerCheckBox", true);
        setCheckBox(panel, "proxyJdaCheckBox", true);
        setCheckBox(panel, "proxyGithubCheckBox", false);

        // The plaintext password only exists for the moment it takes to build this map and
        // splice it into the file content; nothing on that path should be logging it.
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
            assertFalse(event.getFormattedMessage().contains("correct horse battery staple!"),
                    "Proxy password leaked into a log message: " + event.getFormattedMessage());
        }

        assertEquals("\"10.0.0.5\"", updates.get("proxy.host"));
        assertEquals("\"correct horse battery staple!\"", updates.get("proxy.password"));

        writeFileContent(configFile, updatedContent);

        // The real proof of a round trip: reloading through BotConfig, independent of the
        // panel entirely, sees exactly what was just typed in.
        BotConfig reloaded = new BotConfig(mockUserInteraction);
        reloaded.load();

        assertEquals("10.0.0.5", reloaded.getProxyHost());
        assertEquals(9090, reloaded.getProxyPort());
        assertEquals("new_user", reloaded.getProxyUsername());
        assertEquals("correct horse battery staple!", reloaded.getProxyPassword());
        assertTrue(reloaded.proxyLavaplayer());
        assertTrue(reloaded.proxyJda());
        assertFalse(reloaded.proxyGithub());
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
}
