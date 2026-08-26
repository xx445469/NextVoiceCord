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
package com.jagrosh.jmusicbot.gui.panels;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.jagrosh.jmusicbot.config.update.ConfigUpdater;
import com.jagrosh.jmusicbot.gui.GuiLanguage;
import com.jagrosh.jmusicbot.gui.components.Widgets;
import com.jagrosh.jmusicbot.gui.theme.Tokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration panel for viewing and editing bot configuration.
 * Allows editing of safe configuration options and saving to config.txt.
 * Sensitive options (token, owner) and dangerous options (eval) are excluded.
 *
 * @author Arif Banai (arif-banai)
 */
public class ConfigPanel extends JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigPanel.class);

    private final BotConfig config;

    // Commands section
    private final JTextField prefixField;
    private final JTextField altPrefixField;
    private final JTextField helpWordField;

    // Presence section
    private final JTextField gameField;
    private final JComboBox<String> statusComboBox;
    private final JCheckBox songInStatusCheckBox;

    // Voice section
    private final JCheckBox stayInChannelCheckBox;
    private final JSpinner aloneTimeSpinner;

    // Playback section
    private final JSpinner maxSecondsSpinner;
    private final JSpinner maxYTPlaylistPagesSpinner;
    private final JSpinner skipRatioSpinner;
    private final JCheckBox useYouTubeOAuthCheckBox;

    // UI/Emojis section
    private final JTextField successEmojiField;
    private final JTextField warningEmojiField;
    private final JTextField errorEmojiField;
    private final JTextField loadingEmojiField;
    private final JTextField searchingEmojiField;

    // Other section
    private final JCheckBox npImagesCheckBox;
    private final JCheckBox updateAlertsCheckBox;
    private final JComboBox<String> logLevelComboBox;
    private final JTextField playlistsFolderField;

    // Proxy section
    private final JTextField proxyHostField;
    private final JSpinner proxyPortSpinner;
    private final JTextField proxyUsernameField;
    // A password, not a JTextField: see WebSecrets.isSecret, which the web panel applies to
    // this same value. Rendering it in plain text here would make the mask over there theatre.
    private final JPasswordField proxyPasswordField;
    private final JCheckBox proxyLavaplayerCheckBox;
    private final JCheckBox proxyJdaCheckBox;
    private final JCheckBox proxyGithubCheckBox;

    /**
     * Creates the configuration panel.
     *
     * @param bot the bot instance
     */
    public ConfigPanel(Bot bot) {
        this.config = bot.getConfig();

        setLayout(new BorderLayout(0, Tokens.SPACE_MD));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG));

        // Initialize all input components
        // Commands
        prefixField = new JTextField(15);
        altPrefixField = new JTextField(15);
        helpWordField = new JTextField(15);

        // Presence
        gameField = new JTextField(20);
        statusComboBox = new JComboBox<>(new String[]{"ONLINE", "IDLE", "DND", "INVISIBLE"});
        songInStatusCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.showCurrentSongInStatus"));

        // Voice
        stayInChannelCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.stayInChannel"));
        aloneTimeSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 3600, 1));

        // Playback
        maxSecondsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 86400, 60));
        maxYTPlaylistPagesSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        skipRatioSpinner = new JSpinner(new SpinnerNumberModel(0.55, 0.0, 1.0, 0.05));
        useYouTubeOAuthCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.useYouTubeOAuth"));

        // UI/Emojis
        successEmojiField = new JTextField(5);
        warningEmojiField = new JTextField(5);
        errorEmojiField = new JTextField(5);
        loadingEmojiField = new JTextField(5);
        searchingEmojiField = new JTextField(5);

        // Other
        npImagesCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.showYouTubeThumbnails"));
        updateAlertsCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.alertOwnerAboutUpdates"));
        logLevelComboBox = new JComboBox<>(new String[]{"off", "error", "warn", "info", "debug", "trace", "all"});
        playlistsFolderField = new JTextField(20);

        // Proxy
        proxyHostField = new JTextField(20);
        proxyPortSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 65535, 1));
        proxyUsernameField = new JTextField(15);
        proxyPasswordField = new JPasswordField(15);
        proxyLavaplayerCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.proxyLavaplayer"));
        proxyJdaCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.proxyJda"));
        proxyGithubCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.proxyGithub"));

        applyFieldStyle(prefixField, altPrefixField, helpWordField, gameField,
                successEmojiField, warningEmojiField, errorEmojiField, loadingEmojiField,
                searchingEmojiField, playlistsFolderField, proxyHostField, proxyUsernameField,
                proxyPasswordField);
        applyFieldStyle(statusComboBox, logLevelComboBox);
        applyFieldStyle(songInStatusCheckBox, stayInChannelCheckBox, useYouTubeOAuthCheckBox,
                npImagesCheckBox, updateAlertsCheckBox, proxyLavaplayerCheckBox, proxyJdaCheckBox,
                proxyGithubCheckBox);
        applyFieldStyle(aloneTimeSpinner, maxSecondsSpinner, maxYTPlaylistPagesSpinner, skipRatioSpinner,
                proxyPortSpinner);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildScrollArea(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        // Load current values
        loadCurrentValues();
    }

    private void applyFieldStyle(JComponent... components) {
        for (JComponent c : components) {
            c.setFont(Tokens.fontBody());
        }
    }

    private Component buildHeader() {
        JPanel header = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_XS));
        header.add(Widgets.pageTitle(GuiLanguage.msg("gui.config.title")), BorderLayout.NORTH);
        header.add(Widgets.muted(GuiLanguage.msg("gui.config.subtitle")),
                BorderLayout.SOUTH);
        return header;
    }

    private Component buildScrollArea() {
        JPanel content = Widgets.transparent(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(createCommandsSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createPresenceSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createVoiceSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createPlaybackSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createEmojisSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createOtherSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createProxySection());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scrollPane;
    }

    /** A form panel: label column left, control column right, growing horizontally. */
    private JPanel formPanel() {
        JPanel panel = Widgets.transparent(new GridBagLayout());
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private GridBagConstraints rowConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(Tokens.SPACE_XS, 0, Tokens.SPACE_XS, Tokens.SPACE_MD);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent control) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel l = new JLabel(label);
        l.setFont(Tokens.fontBody());
        l.setForeground(Tokens.textMuted());
        panel.add(l, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(gbc.insets.top, 0, gbc.insets.bottom, 0);
        panel.add(control, gbc);
        gbc.insets = new Insets(gbc.insets.top, 0, gbc.insets.bottom, Tokens.SPACE_MD);
    }

    private void addSpanningRow(JPanel panel, GridBagConstraints gbc, int row, JComponent control) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(control, gbc);
        gbc.gridwidth = 1;
    }

    /**
     * Creates the Commands configuration section.
     */
    private Component createCommandsSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.prefix"), prefixField);
        addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.altPrefix"), altPrefixField);
        addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.helpWord"), helpWordField);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.commands"), panel);
    }

    /**
     * Creates the Presence configuration section.
     */
    private Component createPresenceSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.gameStatus"), gameField);
        addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.onlineStatus"), statusComboBox);
        addSpanningRow(panel, gbc, 2, songInStatusCheckBox);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.presence"), panel);
    }

    /**
     * Creates the Voice configuration section.
     */
    private Component createVoiceSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addSpanningRow(panel, gbc, 0, stayInChannelCheckBox);
        addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.aloneTimeUntilStop"), aloneTimeSpinner);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.voice"), panel);
    }

    /**
     * Creates the Playback configuration section.
     */
    private Component createPlaybackSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.maxTrackSeconds"), maxSecondsSpinner);
        addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.skipRatio"), skipRatioSpinner);
        addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.maxYouTubePlaylistPages"), maxYTPlaylistPagesSpinner);
        addSpanningRow(panel, gbc, 3, useYouTubeOAuthCheckBox);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.playback"), panel);
    }

    /**
     * Creates the UI/Emojis configuration section.
     */
    private Component createEmojisSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.success"), successEmojiField);
        addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.warning"), warningEmojiField);
        addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.error"), errorEmojiField);
        addRow(panel, gbc, 3, GuiLanguage.msg("gui.config.loading"), loadingEmojiField);
        addRow(panel, gbc, 4, GuiLanguage.msg("gui.config.searching"), searchingEmojiField);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.uiEmojis"), panel);
    }

    /**
     * Creates the Other configuration section.
     */
    private Component createOtherSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addSpanningRow(panel, gbc, 0, npImagesCheckBox);
        addSpanningRow(panel, gbc, 1, updateAlertsCheckBox);
        addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.logLevel"), logLevelComboBox);
        addRow(panel, gbc, 3, GuiLanguage.msg("gui.config.playlistsFolder"), playlistsFolderField);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.other"), panel);
    }

    /**
     * Creates the Proxy configuration section.
     *
     * <p>The three checkboxes are labelled by what they route rather than by their raw config
     * keys — "lavaplayer", "jda" and "github" mean nothing to someone who has not read the
     * source, but "audio playback", "Discord connection" and "update checks" are the things
     * they actually chose to proxy or not.
     */
    private Component createProxySection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.proxyHost"), proxyHostField);
        addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.proxyPort"), proxyPortSpinner);
        addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.proxyUsername"), proxyUsernameField);
        addRow(panel, gbc, 3, GuiLanguage.msg("gui.config.proxyPassword"), proxyPasswordField);
        addSpanningRow(panel, gbc, 4, proxyLavaplayerCheckBox);
        addSpanningRow(panel, gbc, 5, proxyJdaCheckBox);
        addSpanningRow(panel, gbc, 6, proxyGithubCheckBox);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.proxy"), panel);
    }

    /**
     * Creates the bottom panel with save/reset buttons and warning.
     */
    private Component buildBottomPanel() {
        Widgets.Card card = new Widgets.Card();
        card.setLayout(new BorderLayout(0, Tokens.SPACE_XS));

        JPanel buttonPanel = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_SM, 0));

        JButton saveButton = new JButton(GuiLanguage.msg("gui.config.saveChanges"));
        saveButton.setFont(Tokens.fontBody());
        saveButton.putClientProperty("JButton.buttonType", "default");
        saveButton.addActionListener(e -> saveConfiguration());
        buttonPanel.add(saveButton);

        JButton resetButton = new JButton(GuiLanguage.msg("gui.config.resetToCurrent"));
        resetButton.setFont(Tokens.fontBody());
        resetButton.addActionListener(e -> loadCurrentValues());
        buttonPanel.add(resetButton);

        card.add(buttonPanel, BorderLayout.NORTH);
        card.add(Widgets.muted(GuiLanguage.msg("gui.config.restartRequired")), BorderLayout.SOUTH);

        return card;
    }

    /**
     * Loads current configuration values into the input fields.
     */
    private void loadCurrentValues() {
        // Commands
        prefixField.setText(config.getPrefix());
        String altPrefix = config.getAltPrefix();
        altPrefixField.setText(altPrefix != null ? altPrefix : "NONE");
        helpWordField.setText(config.getHelp());

        // Presence
        String gameStatus = config.isGameNone() ? "NONE" :
            (config.getGame() != null ? config.getGame().getName() : "DEFAULT");
        gameField.setText(gameStatus);
        statusComboBox.setSelectedItem(config.getStatus().name());
        songInStatusCheckBox.setSelected(config.getSongInStatus());

        // Voice
        stayInChannelCheckBox.setSelected(config.getStay());
        aloneTimeSpinner.setValue((int) config.getAloneTimeUntilStop());

        // Playback
        maxSecondsSpinner.setValue((int) config.getMaxSeconds());
        maxYTPlaylistPagesSpinner.setValue(config.getMaxYTPlaylistPages());
        skipRatioSpinner.setValue(config.getSkipRatio());
        useYouTubeOAuthCheckBox.setSelected(config.useYouTubeOauth());

        // UI/Emojis
        successEmojiField.setText(config.getSuccess());
        warningEmojiField.setText(config.getWarning());
        errorEmojiField.setText(config.getError());
        loadingEmojiField.setText(config.getLoading());
        searchingEmojiField.setText(config.getSearching());

        // Other
        npImagesCheckBox.setSelected(config.useNPImages());
        updateAlertsCheckBox.setSelected(config.useUpdateAlerts());
        logLevelComboBox.setSelectedItem(config.getLogLevel());
        playlistsFolderField.setText(config.getPlaylistsFolder());

        // Proxy
        proxyHostField.setText(config.getProxyHost());
        proxyPortSpinner.setValue(config.getProxyPort());
        proxyUsernameField.setText(config.getProxyUsername());
        // setText, not a log line: this is the one field on the panel that must never be
        // written anywhere it could be read back other than by typing it into this box.
        proxyPasswordField.setText(config.getProxyPassword());
        proxyLavaplayerCheckBox.setSelected(config.proxyLavaplayer());
        proxyJdaCheckBox.setSelected(config.proxyJda());
        proxyGithubCheckBox.setSelected(config.proxyGithub());
    }

    /**
     * Saves the configuration to the config file.
     */
    private void saveConfiguration() {
        try {
            Path configPath = ConfigIO.getConfigPath();

            // Read current config file
            String content = Files.readString(configPath, StandardCharsets.UTF_8);

            // Build updates map
            Map<String, String> updates = buildUpdatesMap();

            // Apply updates to config content
            String updatedContent = applyConfigUpdates(content, updates);

            // Create backup using the same resolution logic as config migration
            Path backupPath = ConfigUpdater.findAvailableBackupPath(configPath);
            Files.copy(configPath, backupPath);

            // Write updated config
            ConfigIO.writeConfigFile(configPath, updatedContent);

            LOG.info("Configuration saved successfully to {}", configPath);

            JOptionPane.showMessageDialog(
                this,
                GuiLanguage.msg("gui.config.savedDialogMessage", backupPath.getFileName()),
                GuiLanguage.msg("gui.config.savedDialogTitle"),
                JOptionPane.INFORMATION_MESSAGE
            );

        } catch (IOException ex) {
            LOG.error("Failed to save configuration", ex);
            JOptionPane.showMessageDialog(
                this,
                GuiLanguage.msg("gui.config.saveErrorMessage", ex.getMessage()),
                GuiLanguage.msg("gui.config.saveErrorTitle"),
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Builds a map of config keys to their new values.
     */
    private Map<String, String> buildUpdatesMap() {
        Map<String, String> updates = new HashMap<>();

        // Commands
        updates.put("commands.prefix", quoteString(prefixField.getText()));
        updates.put("commands.altPrefix", quoteString(altPrefixField.getText()));
        updates.put("commands.help", quoteString(helpWordField.getText()));

        // Presence
        updates.put("presence.game", quoteString(gameField.getText()));
        updates.put("presence.status", quoteString((String) statusComboBox.getSelectedItem()));
        updates.put("presence.songInStatus", String.valueOf(songInStatusCheckBox.isSelected()));

        // Voice
        updates.put("voice.stayInChannel", String.valueOf(stayInChannelCheckBox.isSelected()));
        updates.put("voice.aloneTimeUntilStopSeconds", String.valueOf(aloneTimeSpinner.getValue()));

        // Playback
        updates.put("playback.maxTrackSeconds", String.valueOf(maxSecondsSpinner.getValue()));
        updates.put("playback.maxYouTubePlaylistPages", String.valueOf(maxYTPlaylistPagesSpinner.getValue()));
        updates.put("playback.skipRatio", String.valueOf(skipRatioSpinner.getValue()));
        updates.put("playback.youtube.useOAuth", String.valueOf(useYouTubeOAuthCheckBox.isSelected()));

        // UI/Emojis
        updates.put("ui.emojis.success", quoteString(successEmojiField.getText()));
        updates.put("ui.emojis.warning", quoteString(warningEmojiField.getText()));
        updates.put("ui.emojis.error", quoteString(errorEmojiField.getText()));
        updates.put("ui.emojis.loading", quoteString(loadingEmojiField.getText()));
        updates.put("ui.emojis.searching", quoteString(searchingEmojiField.getText()));

        // Other
        updates.put("nowPlaying.images", String.valueOf(npImagesCheckBox.isSelected()));
        updates.put("updates.alerts", String.valueOf(updateAlertsCheckBox.isSelected()));
        updates.put("logging.level", quoteString((String) logLevelComboBox.getSelectedItem()));
        updates.put("paths.playlistsFolder", quoteString(playlistsFolderField.getText()));

        // Proxy
        updates.put("proxy.host", quoteString(proxyHostField.getText()));
        updates.put("proxy.port", String.valueOf(proxyPortSpinner.getValue()));
        updates.put("proxy.username", quoteString(proxyUsernameField.getText()));
        updates.put("proxy.password", quoteString(readAndClearPassword()));
        updates.put("proxy.lavaplayer", String.valueOf(proxyLavaplayerCheckBox.isSelected()));
        updates.put("proxy.jda", String.valueOf(proxyJdaCheckBox.isSelected()));
        updates.put("proxy.github", String.valueOf(proxyGithubCheckBox.isSelected()));

        return updates;
    }

    /**
     * Reads the proxy password and wipes the char array Swing handed back.
     *
     * <p>The resulting String still ends up in the updates map and, briefly, in the rewritten
     * config.txt content — Java offers no way around that once a String exists at all — but
     * the mutable buffer this came from does not have to sit in memory a moment longer than
     * it takes to copy it out.
     */
    private String readAndClearPassword() {
        char[] password = proxyPasswordField.getPassword();
        try {
            return new String(password);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    /**
     * Quotes a string value for HOCON format.
     */
    private String quoteString(String value) {
        if (value == null) {
            return "\"\"";
        }
        // Escape backslashes and quotes
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    /**
     * Applies config updates to the content using regex pattern matching.
     * Handles both nested (e.g., presence.status) and flat key formats.
     */
    private String applyConfigUpdates(String content, Map<String, String> updates) {
        String result = content;

        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Extract the leaf key (e.g., "status" from "presence.status")
            String leafKey = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;

            // Try to find and replace the value in the config
            // Match pattern: leafKey = value (with optional quotes and whitespace)
            String pattern = "(?m)^(\\s*" + java.util.regex.Pattern.quote(leafKey) + "\\s*=\\s*)([^\r\n]+)";
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = regex.matcher(result);

            // Find the right occurrence based on context (section)
            String section = key.contains(".") ? key.substring(0, key.indexOf('.')) : null;

            if (section != null) {
                // Find section and update within it
                result = updateKeyInSection(result, section, leafKey, value);
            } else {
                // Simple replacement for top-level keys
                result = matcher.replaceFirst("$1" + java.util.regex.Matcher.quoteReplacement(value));
            }
        }

        return result;
    }

    /**
     * Updates a key within a specific section of the config.
     */
    private String updateKeyInSection(String content, String section, String leafKey, String value) {
        // Handle nested sections like "playback.youtube.useOAuth"
        String[] sectionParts = section.split("\\.");

        // Find the section start
        StringBuilder sectionPattern = new StringBuilder();
        for (String part : sectionParts) {
            sectionPattern.append("(?s).*?").append(java.util.regex.Pattern.quote(part)).append("\\s*\\{");
        }

        java.util.regex.Pattern sectionRegex = java.util.regex.Pattern.compile(
            "(?s)(" + java.util.regex.Pattern.quote(sectionParts[sectionParts.length - 1]) + "\\s*\\{[^}]*?" +
            java.util.regex.Pattern.quote(leafKey) + "\\s*=\\s*)([^\r\n]+)"
        );

        java.util.regex.Matcher matcher = sectionRegex.matcher(content);

        if (matcher.find()) {
            return matcher.replaceFirst("$1" + java.util.regex.Matcher.quoteReplacement(value));
        }

        // Fallback: simple pattern match for the key
        String pattern = "(?m)^(\\s*" + java.util.regex.Pattern.quote(leafKey) + "\\s*=\\s*)([^\r\n]+)";
        return content.replaceFirst(pattern, "$1" + java.util.regex.Matcher.quoteReplacement(value));
    }
}
