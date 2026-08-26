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
import com.jagrosh.jmusicbot.audio.lavalink.LavalinkNodeConfig;
import com.jagrosh.jmusicbot.gui.panels.ConfigPanel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code playback.engine} and {@code lavalink.nodes}, round-tripped through {@link ConfigPanel}'s
 * own field-loading and save-building logic — the same reflection-driven approach
 * {@code ConfigPanelProxyRoundTripTest} and {@code ConfigPanelNewOptionsRoundTripTest} use for the
 * rest of the panel's fields.
 *
 * <p>The node list is the part of this feature most able to corrupt config.txt (see
 * {@code ConfigPanel.replaceLavalinkNodesValue}): reference.conf ships {@code lavalink.nodes}
 * spread across many lines, so {@link #nodeListSurvivesLoadEditSaveReload()} specifically edits
 * (adds one, removes one) rather than only ever roundtripping the untouched default, and reloads
 * through a fresh {@link BotConfig} independent of the panel entirely.
 */
@DisplayName("ConfigPanel: Lavalink section")
class ConfigPanelLavalinkRoundTripTest extends BaseConfigTest {

    private static final String CONFIG_TEMPLATE = """
            meta {
              configVersion = 2
            }
            discord {
              token = test_token
              owner = 123456789
            }
            playback {
              engine = "lavalink"
            }
            lavalink {
              nodes = [
                {
                  name = "alpha"
                  host = "alpha.example.com"
                  port = 1111
                  password = "alpha-secret-password"
                  secure = false
                }
                {
                  name = "beta"
                  host = "beta.example.com"
                  port = 2222
                  password = "beta-secret-password"
                  secure = true
                }
              ]
            }
            """;

    @BeforeEach
    @Override
    protected void setUpBase() {
        super.setUpBase();
        // As in the other ConfigPanel round-trip tests: these build Swing components but never
        // show one, so headless keeps the result independent of the machine running it.
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    @DisplayName("loads playback.engine and every lavalink.nodes entry from config.txt")
    void loadsLavalinkFieldsFromConfig() throws IOException, ReflectiveOperationException {
        ConfigPanel panel = buildPanel(CONFIG_TEMPLATE);

        assertEquals("lavalink", engineSelection(panel));

        List<LavalinkNodeConfig> nodes = nodeListValues(panel);
        assertEquals(2, nodes.size());
        assertEquals("alpha", nodes.get(0).name());
        assertEquals("alpha.example.com", nodes.get(0).host());
        assertEquals(1111, nodes.get(0).port());
        assertEquals("alpha-secret-password", nodes.get(0).password());
        assertFalse(nodes.get(0).secure());
        assertEquals("beta", nodes.get(1).name());
        assertTrue(nodes.get(1).secure());
    }

    @Test
    @DisplayName("the node list survives load -> edit (add one, remove one) -> save -> reload")
    void nodeListSurvivesLoadEditSaveReload() throws Exception {
        Path configFile = createTempConfigFile(CONFIG_TEMPLATE);
        setConfigFileProperty(configFile);

        BotConfig config = new BotConfig(mockUserInteraction);
        config.load();

        Bot bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(config);

        ConfigPanel panel = new ConfigPanel(bot);

        // Loaded exactly what config.txt had.
        assertEquals(2, nodeListValues(panel).size());

        // Edit: remove "alpha" (index 0), keep "beta", add a new "gamma" node — the same shape
        // of edit the task calls out (a node added and one removed), done directly against the
        // list model the way the youtube-clients round-trip test edits that list, rather than
        // through the interactive add/edit dialog.
        removeNode(panel, 0);
        LavalinkNodeConfig gamma = new LavalinkNodeConfig(
                "gamma", "gamma.example.com", 3333, "gamma-secret-password", false);
        addNode(panel, gamma);

        List<LavalinkNodeConfig> edited = nodeListValues(panel);
        assertEquals(2, edited.size());
        assertEquals("beta", edited.get(0).name());
        assertEquals("gamma", edited.get(1).name());

        Map<String, String> updates = buildUpdatesMap(panel);
        String originalContent = readFileContent(configFile);
        String updatedContent = applyConfigUpdates(panel, originalContent, updates);

        // The generated replacement is well-formed HOCON on its own, and it did not leave any
        // fragment of the old multi-line array (the node named "alpha") behind.
        assertFalse(updatedContent.contains("alpha"),
                "the removed node's name leaked into the saved file: " + updatedContent);

        writeFileContent(configFile, updatedContent);

        // The real proof of a round trip: reloading through BotConfig, independent of the panel
        // entirely, sees exactly what was just edited in.
        BotConfig reloaded = new BotConfig(mockUserInteraction);
        reloaded.load();

        assertTrue(reloaded.isValid());
        List<LavalinkNodeConfig> reloadedNodes = reloaded.getLavalinkNodes();
        assertEquals(2, reloadedNodes.size());
        assertEquals("beta", reloadedNodes.get(0).name());
        assertEquals("beta.example.com", reloadedNodes.get(0).host());
        assertEquals(2222, reloadedNodes.get(0).port());
        assertEquals("beta-secret-password", reloadedNodes.get(0).password());
        assertTrue(reloadedNodes.get(0).secure());
        assertEquals("gamma", reloadedNodes.get(1).name());
        assertEquals("gamma.example.com", reloadedNodes.get(1).host());
        assertEquals(3333, reloadedNodes.get(1).port());
        assertEquals("gamma-secret-password", reloadedNodes.get(1).password());
        assertFalse(reloadedNodes.get(1).secure());
    }

    @ParameterizedTest(name = "playback.engine = \"{0}\" round-trips through save and reload")
    @ValueSource(strings = {"lavaplayer", "lavalink", "fallback"})
    @DisplayName("the engine selector round-trips all three values")
    void engineSelectorRoundTripsAllThreeValues(String engine) throws Exception {
        Path configFile = createTempConfigFile(CONFIG_TEMPLATE);
        setConfigFileProperty(configFile);

        BotConfig config = new BotConfig(mockUserInteraction);
        config.load();

        Bot bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(config);

        ConfigPanel panel = new ConfigPanel(bot);
        setEngineSelection(panel, engine);

        Map<String, String> updates = buildUpdatesMap(panel);
        assertEquals("\"" + engine + "\"", updates.get("playback.engine"));

        String updatedContent = applyConfigUpdates(panel, readFileContent(configFile), updates);
        writeFileContent(configFile, updatedContent);

        BotConfig reloaded = new BotConfig(mockUserInteraction);
        reloaded.load();

        assertTrue(reloaded.isValid());
        // getPlaybackEngineRaw, not getPlaybackEngine: "fallback" deliberately resolves to
        // LAVAPLAYER for what the bot actually runs (see PlaybackEngine.resolve), but the raw
        // value is what a config editor must show back — this is the round trip the UI itself
        // owes the reader, independent of what the engine resolves to at runtime.
        assertEquals(engine, reloaded.getPlaybackEngineRaw());
    }

    @Test
    @DisplayName("no lavalink node password appears in any ConfigPanel log event across load and save")
    void nodePasswordNeverAppearsInAnyLogEvent() throws Exception {
        Path configFile = createTempConfigFile(CONFIG_TEMPLATE);
        setConfigFileProperty(configFile);

        BotConfig config = new BotConfig(mockUserInteraction);
        config.load();

        Bot bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(config);

        Logger configPanelLogger = (Logger) org.slf4j.LoggerFactory.getLogger(ConfigPanel.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        configPanelLogger.addAppender(appender);

        String newPassword = "brand-new-node-secret-password";
        Map<String, String> updates;
        String updatedContent;
        try {
            ConfigPanel panel = new ConfigPanel(bot);

            // A freshly typed-in node, as the add dialog would hand back — this is the value
            // most likely to leak if any code path on the save side ever logs a node verbatim
            // instead of going through LavalinkNodeConfig.describe().
            addNode(panel, new LavalinkNodeConfig("secret-node", "secret.example.com", 4444, newPassword, true));

            updates = buildUpdatesMap(panel);
            String originalContent = readFileContent(configFile);
            updatedContent = applyConfigUpdates(panel, originalContent, updates);
        } finally {
            configPanelLogger.detachAppender(appender);
        }

        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage().contains(newPassword),
                    "a lavalink node password leaked into a ConfigPanel log message: "
                            + event.getFormattedMessage());
        }

        // Also true of the two passwords that were already in config.txt before this test
        // touched it, and of the file content itself never ending up in a log line.
        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage().contains("alpha-secret-password"));
            assertFalse(event.getFormattedMessage().contains("beta-secret-password"));
        }

        writeFileContent(configFile, updatedContent);

        // Reloading logs "Playback engine: lavalink, node(s): ..." (see BotConfig) using
        // LavalinkNodeConfig.describe(), which never includes the password — confirm that
        // holds for the reload path too, not just ConfigPanel's own save path.
        Logger botConfigLogger = (Logger) org.slf4j.LoggerFactory.getLogger(BotConfig.class);
        ListAppender<ILoggingEvent> botAppender = new ListAppender<>();
        botAppender.start();
        botConfigLogger.addAppender(botAppender);
        try {
            BotConfig reloaded = new BotConfig(mockUserInteraction);
            reloaded.load();
            assertTrue(reloaded.isValid());
        } finally {
            botConfigLogger.detachAppender(botAppender);
        }

        for (ILoggingEvent event : botAppender.list) {
            assertFalse(event.getFormattedMessage().contains(newPassword),
                    "a lavalink node password leaked into a BotConfig log message on reload: "
                            + event.getFormattedMessage());
        }
    }

    // ==================== helpers ====================

    private ConfigPanel buildPanel(String content) throws IOException {
        Path configFile = createTempConfigFile(content);
        setConfigFileProperty(configFile);

        BotConfig config = new BotConfig(mockUserInteraction);
        config.load();

        Bot bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(config);

        return new ConfigPanel(bot);
    }

    private static Object fieldValue(ConfigPanel panel, String name) throws ReflectiveOperationException {
        Field field = ConfigPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(panel);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> buildUpdatesMap(ConfigPanel panel) throws ReflectiveOperationException {
        Method method = ConfigPanel.class.getDeclaredMethod("buildUpdatesMap");
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(panel);
    }

    private static String applyConfigUpdates(ConfigPanel panel, String content, Map<String, String> updates)
            throws ReflectiveOperationException {
        Method method = ConfigPanel.class.getDeclaredMethod("applyConfigUpdates", String.class, Map.class);
        method.setAccessible(true);
        return (String) method.invoke(panel, content, updates);
    }

    private static String engineSelection(ConfigPanel panel) throws ReflectiveOperationException {
        return (String) ((JComboBox<?>) fieldValue(panel, "lavalinkEngineComboBox")).getSelectedItem();
    }

    private static void setEngineSelection(ConfigPanel panel, String value) throws ReflectiveOperationException {
        ((JComboBox<String>) fieldValue(panel, "lavalinkEngineComboBox")).setSelectedItem(value);
    }

    @SuppressWarnings("unchecked")
    private static List<LavalinkNodeConfig> nodeListValues(ConfigPanel panel) throws ReflectiveOperationException {
        DefaultListModel<LavalinkNodeConfig> model =
                (DefaultListModel<LavalinkNodeConfig>) fieldValue(panel, "lavalinkNodesModel");
        List<LavalinkNodeConfig> nodes = new java.util.ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            nodes.add(model.get(i));
        }
        return nodes;
    }

    @SuppressWarnings("unchecked")
    private static void addNode(ConfigPanel panel, LavalinkNodeConfig node) throws ReflectiveOperationException {
        DefaultListModel<LavalinkNodeConfig> model =
                (DefaultListModel<LavalinkNodeConfig>) fieldValue(panel, "lavalinkNodesModel");
        model.addElement(node);
    }

    @SuppressWarnings("unchecked")
    private static void removeNode(ConfigPanel panel, int index) throws ReflectiveOperationException {
        DefaultListModel<LavalinkNodeConfig> model =
                (DefaultListModel<LavalinkNodeConfig>) fieldValue(panel, "lavalinkNodesModel");
        model.remove(index);
    }
}
