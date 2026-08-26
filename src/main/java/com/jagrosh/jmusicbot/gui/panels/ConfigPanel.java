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
import com.jagrosh.jmusicbot.audio.AudioSource;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.jagrosh.jmusicbot.config.update.ConfigUpdater;
import com.jagrosh.jmusicbot.gui.GuiLanguage;
import com.jagrosh.jmusicbot.gui.components.Widgets;
import com.jagrosh.jmusicbot.gui.theme.Tokens;
import com.jagrosh.jmusicbot.i18n.Language;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration panel for viewing and editing bot configuration.
 * Allows editing of every option in {@link com.jagrosh.jmusicbot.config.model.ConfigOption}.
 *
 * <p>Secrets (the Discord token, the update GitHub token, the YouTube poToken pair) use
 * {@link JPasswordField} rather than a plain text field, on the same reasoning as the Proxy
 * section below: a value masked in the web panel and printed in plain text here would make
 * that masking theatre. {@code dangerous.eval} is shown with an explicit, unmissable warning
 * rather than as an ordinary checkbox, because what it enables is arbitrary code execution.
 * {@code commands.aliases} and {@code playback.transforms} are nested structures that are shown
 * read-only rather than through a text field that could silently corrupt them on save — the same
 * reasoning the web panel's {@code WebWrites.validate} uses to refuse editing them at all.
 *
 * @author Arif Banai (arif-banai)
 */
public class ConfigPanel extends JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigPanel.class);

    /**
     * Known InnerTube client names, in the canonical (underscore-free) form
     * {@code AudioSource.clientByName} matches against. Offered as suggestions in the "add" box
     * only — the list itself holds whatever strings were loaded from config.txt, including the
     * underscored spellings the comment above {@code playback.youtube.clients} documents, so a
     * value written by hand is preserved exactly rather than silently dropped because it isn't
     * one of these.
     */
    private static final String[] KNOWN_YOUTUBE_CLIENTS = {
            "MUSIC", "WEB", "WEBEMBEDDED", "ANDROID", "ANDROIDVR", "ANDROIDMUSIC",
            "IOS", "MWEB", "TV", "TVHTML5SIMPLY"
    };

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

    // Discord section — discord.token, discord.owner
    private final JPasswordField discordTokenField;
    private final JSpinner discordOwnerSpinner;

    // Localization section — ui.language
    private final JComboBox<String> botLanguageComboBox;

    // Now Playing section — nowPlaying.minimalMessage/showButtons/showProgressBar
    private final JCheckBox npMinimalMessageCheckBox;
    private final JCheckBox npShowButtonsCheckBox;
    private final JCheckBox npShowProgressBarCheckBox;

    // YouTube Advanced section — playback.youtube.poToken/visitorData/clients
    private final JPasswordField youtubePoTokenField;
    private final JPasswordField youtubeVisitorDataField;
    private final DefaultListModel<String> youtubeClientsModel;
    private final JList<String> youtubeClientsJList;
    private final JComboBox<String> youtubeClientsAddComboBox;

    // Playback Advanced section — playback.maxHistorySize, playback.audioSources, playback.transforms
    private final JSpinner maxHistorySizeSpinner;
    private final Map<AudioSource, JCheckBox> audioSourceCheckBoxes;
    private final JTextArea transformsTextArea;

    // Commands Advanced section — commands.clearChannel.*, commands.aliases
    private final JSpinner clearChannelDeleteLimitSpinner;
    private final JSpinner clearChannelAgeDaysSpinner;
    private final JTextArea aliasesTextArea;

    // Updates section — updates.repository/autoUpdate/checkIntervalHours/githubToken
    private final JTextField updateRepositoryField;
    private final JCheckBox updateAutoUpdateCheckBox;
    private final JSpinner updateCheckIntervalSpinner;
    private final JPasswordField updateGithubTokenField;

    // Dangerous section — dangerous.evalEngine, dangerous.eval
    private final JTextField evalEngineField;
    private final JCheckBox evalCheckBox;

    // GUI & Web section — gui.enabled/fontSize/language, web.bindAddress/allowConfigEdit
    private final JCheckBox guiEnabledCheckBox;
    private final JSpinner guiFontSizeSpinner;
    private final JComboBox<String> guiLanguageComboBox;
    private final JTextField webBindAddressField;
    private final JCheckBox webAllowConfigEditCheckBox;

    // Performance section — performance.nasBufferMs/frameBufferMs
    private final JSpinner nasBufferMsSpinner;
    private final JSpinner frameBufferMsSpinner;

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

        // Discord — a spinner rather than a text field for the owner id: a Discord snowflake
        // is unquoted in HOCON, so a spinner backed by a Long model is what keeps a stray
        // non-digit character from ever reaching the file and breaking the parse.
        discordTokenField = new JPasswordField(20);
        discordOwnerSpinner = new JSpinner(new SpinnerNumberModel(0L, 0L, Long.MAX_VALUE, 1L));

        // Localization
        botLanguageComboBox = new JComboBox<>(languageCodes());

        // Now Playing
        npMinimalMessageCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.npMinimalMessage"));
        npShowButtonsCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.npShowButtons"));
        npShowProgressBarCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.npShowProgressBar"));

        // YouTube Advanced
        youtubePoTokenField = new JPasswordField(20);
        youtubeVisitorDataField = new JPasswordField(20);
        youtubeClientsModel = new DefaultListModel<>();
        youtubeClientsJList = new JList<>(youtubeClientsModel);
        youtubeClientsJList.setVisibleRowCount(4);
        youtubeClientsJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        youtubeClientsAddComboBox = new JComboBox<>(KNOWN_YOUTUBE_CLIENTS);

        // Playback Advanced — one checkbox per known AudioSource, which is what
        // playback.audioSources actually is: a nested map of booleans over a fixed, known key
        // set. That fixed set is what makes checkboxes a genuine round trip rather than a
        // guess, unlike commands.aliases or playback.transforms below.
        maxHistorySizeSpinner = new JSpinner(new SpinnerNumberModel(40, 0, 100000, 10));
        audioSourceCheckBoxes = new LinkedHashMap<>();
        for (AudioSource source : AudioSource.valuesSortedByPriority()) {
            audioSourceCheckBoxes.put(source, new JCheckBox(source.getDescription()));
        }
        transformsTextArea = readOnlyConfigArea();

        // Commands Advanced
        clearChannelDeleteLimitSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 100000, 10));
        clearChannelAgeDaysSpinner = new JSpinner(new SpinnerNumberModel(14, 0, 36500, 1));
        aliasesTextArea = readOnlyConfigArea();

        // Updates
        updateRepositoryField = new JTextField(20);
        updateAutoUpdateCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.updateAutoUpdate"));
        updateCheckIntervalSpinner = new JSpinner(new SpinnerNumberModel(6, 1, 8760, 1));
        updateGithubTokenField = new JPasswordField(20);

        // Dangerous
        evalEngineField = new JTextField(15);
        evalCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.useEval"));

        // GUI & Web
        guiEnabledCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.guiEnabled"));
        guiFontSizeSpinner = new JSpinner(new SpinnerNumberModel(12, 8, 24, 1));
        guiLanguageComboBox = new JComboBox<>(guiLanguageChoices());
        guiLanguageComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean cellHasFocus) {
                String display = (value == null || ((String) value).isEmpty())
                        ? GuiLanguage.msg("gui.config.guiLanguageFollowLabel")
                        : (String) value;
                return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus);
            }
        });
        webBindAddressField = new JTextField(20);
        webAllowConfigEditCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.webAllowConfigEdit"));

        // Performance
        nasBufferMsSpinner = new JSpinner(new SpinnerNumberModel(800, 0, 60000, 100));
        frameBufferMsSpinner = new JSpinner(new SpinnerNumberModel(2000, 0, 60000, 100));

        applyFieldStyle(prefixField, altPrefixField, helpWordField, gameField,
                successEmojiField, warningEmojiField, errorEmojiField, loadingEmojiField,
                searchingEmojiField, playlistsFolderField, proxyHostField, proxyUsernameField,
                proxyPasswordField, discordTokenField, youtubePoTokenField, youtubeVisitorDataField,
                updateRepositoryField, updateGithubTokenField, evalEngineField, webBindAddressField);
        applyFieldStyle(statusComboBox, logLevelComboBox, botLanguageComboBox, guiLanguageComboBox,
                youtubeClientsAddComboBox);
        applyFieldStyle(songInStatusCheckBox, stayInChannelCheckBox, useYouTubeOAuthCheckBox,
                npImagesCheckBox, updateAlertsCheckBox, proxyLavaplayerCheckBox, proxyJdaCheckBox,
                proxyGithubCheckBox, npMinimalMessageCheckBox, npShowButtonsCheckBox,
                npShowProgressBarCheckBox, updateAutoUpdateCheckBox, evalCheckBox, guiEnabledCheckBox,
                webAllowConfigEditCheckBox);
        applyFieldStyle(aloneTimeSpinner, maxSecondsSpinner, maxYTPlaylistPagesSpinner, skipRatioSpinner,
                proxyPortSpinner, discordOwnerSpinner, maxHistorySizeSpinner, clearChannelDeleteLimitSpinner,
                clearChannelAgeDaysSpinner, updateCheckIntervalSpinner, guiFontSizeSpinner,
                nasBufferMsSpinner, frameBufferMsSpinner);
        applyFieldStyle(youtubeClientsJList, aliasesTextArea, transformsTextArea);
        for (JCheckBox checkBox : audioSourceCheckBoxes.values()) {
            applyFieldStyle(checkBox);
        }

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

    /** Every language code this build ships translations for, in declaration order. */
    private static String[] languageCodes() {
        Language[] languages = Language.values();
        String[] codes = new String[languages.length];
        for (int i = 0; i < languages.length; i++) {
            codes[i] = languages[i].name();
        }
        return codes;
    }

    /** Language codes plus a leading blank entry meaning "follow ui.language". */
    private static String[] guiLanguageChoices() {
        String[] codes = languageCodes();
        String[] choices = new String[codes.length + 1];
        choices[0] = "";
        System.arraycopy(codes, 0, choices, 1, codes.length);
        return choices;
    }

    /** A non-editable, monospaced area for showing a nested config value as HOCON text. */
    private JTextArea readOnlyConfigArea() {
        JTextArea area = new JTextArea(5, 30);
        area.setEditable(false);
        area.setLineWrap(false);
        area.setFont(Tokens.fontMono());
        return area;
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
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createDiscordSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createLocalizationSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createNowPlayingSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createYoutubeAdvancedSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createPlaybackAdvancedSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createCommandsAdvancedSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createUpdatesSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createDangerousSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createGuiWebSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createPerformanceSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createAdvancedReadOnlySection());

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
     * Creates the Discord configuration section: discord.token, discord.owner.
     *
     * <p>Both are required fields the bot cannot start without. The token in particular is
     * shown masked, exactly like the proxy password above, and carries an explicit warning:
     * unlike most settings on this panel, getting it wrong does not degrade a feature — it
     * stops the bot from starting at all.
     */
    private Component createDiscordSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.discordToken"), discordTokenField);
        addSpanningRow(panel, gbc, 1, warningLabel(GuiLanguage.msg("gui.config.discordTokenWarning")));
        addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.discordOwner"), discordOwnerSpinner);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.discord"), panel);
    }

    /**
     * Creates the Localization section: ui.language, the language the bot speaks on Discord.
     *
     * <p>Separate from gui.language below, which is what this desktop window itself is shown
     * in — the two are independent on purpose (see {@link GuiLanguage}).
     */
    private Component createLocalizationSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.botLanguage"), botLanguageComboBox);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.localization"), panel);
    }

    /**
     * Creates the Now Playing configuration section.
     */
    private Component createNowPlayingSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addSpanningRow(panel, gbc, 0, npMinimalMessageCheckBox);
        addSpanningRow(panel, gbc, 1, npShowButtonsCheckBox);
        addSpanningRow(panel, gbc, 2, npShowProgressBarCheckBox);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.nowPlaying"), panel);
    }

    /**
     * Creates the YouTube Advanced configuration section: poToken, visitorData, clients.
     *
     * <p>The clients list is the setting users most often need to change — YouTube breaks
     * individual InnerTube clients rather than all at once — so it gets a real ordered editor
     * rather than a read-only dump, built from {@link #KNOWN_YOUTUBE_CLIENTS}, a known and
     * bounded set of names read from {@code AudioSource.clientByName}.
     */
    private Component createYoutubeAdvancedSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.youtubePoToken"), youtubePoTokenField);
        addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.youtubeVisitorData"), youtubeVisitorDataField);
        addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.youtubeClients"), buildYoutubeClientsEditor());

        return Widgets.titledCard(GuiLanguage.msg("gui.config.youtubeAdvanced"), panel);
    }

    /** The ordered list, add/remove/reorder controls for playback.youtube.clients. */
    private JPanel buildYoutubeClientsEditor() {
        JPanel wrapper = Widgets.transparent(new BorderLayout(Tokens.SPACE_SM, 0));

        JScrollPane listScroll = new JScrollPane(youtubeClientsJList);
        listScroll.setPreferredSize(new Dimension(200, 90));
        wrapper.add(listScroll, BorderLayout.CENTER);

        JPanel controls = Widgets.transparent(new GridLayout(0, 1, 0, Tokens.SPACE_XS));

        JButton addButton = new JButton(GuiLanguage.msg("gui.config.youtubeClientsAdd"));
        addButton.setFont(Tokens.fontSmall());
        addButton.addActionListener(e -> {
            String choice = (String) youtubeClientsAddComboBox.getSelectedItem();
            if (choice != null && !youtubeClientsModel.contains(choice)) {
                youtubeClientsModel.addElement(choice);
            }
        });

        JButton removeButton = new JButton(GuiLanguage.msg("gui.config.youtubeClientsRemove"));
        removeButton.setFont(Tokens.fontSmall());
        removeButton.addActionListener(e -> {
            int index = youtubeClientsJList.getSelectedIndex();
            if (index >= 0) {
                youtubeClientsModel.remove(index);
            }
        });

        JButton upButton = new JButton(GuiLanguage.msg("gui.config.youtubeClientsUp"));
        upButton.setFont(Tokens.fontSmall());
        upButton.addActionListener(e -> moveSelectedYoutubeClient(-1));

        JButton downButton = new JButton(GuiLanguage.msg("gui.config.youtubeClientsDown"));
        downButton.setFont(Tokens.fontSmall());
        downButton.addActionListener(e -> moveSelectedYoutubeClient(1));

        controls.add(youtubeClientsAddComboBox);
        controls.add(addButton);
        controls.add(removeButton);
        controls.add(upButton);
        controls.add(downButton);

        wrapper.add(controls, BorderLayout.EAST);
        return wrapper;
    }

    /** Moves the selected client up (-1) or down (+1) in the ordered list, if it can move. */
    private void moveSelectedYoutubeClient(int direction) {
        int index = youtubeClientsJList.getSelectedIndex();
        int target = index + direction;
        if (index < 0 || target < 0 || target >= youtubeClientsModel.size()) {
            return;
        }
        String value = youtubeClientsModel.remove(index);
        youtubeClientsModel.add(target, value);
        youtubeClientsJList.setSelectedIndex(target);
    }

    /**
     * Creates the Playback Advanced section: maxHistorySize and the audioSources checkboxes.
     */
    private Component createPlaybackAdvancedSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        int row = 0;

        addRow(panel, gbc, row++, GuiLanguage.msg("gui.config.maxHistorySize"), maxHistorySizeSpinner);

        JLabel audioSourcesLabel = new JLabel(GuiLanguage.msg("gui.config.audioSources"));
        audioSourcesLabel.setFont(Tokens.fontBody());
        audioSourcesLabel.setForeground(Tokens.textMuted());
        addSpanningRow(panel, gbc, row++, audioSourcesLabel);

        JPanel sourcesGrid = Widgets.transparent(new GridLayout(0, 2, Tokens.SPACE_SM, 0));
        for (JCheckBox checkBox : audioSourceCheckBoxes.values()) {
            sourcesGrid.add(checkBox);
        }
        addSpanningRow(panel, gbc, row, sourcesGrid);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.playbackAdvanced"), panel);
    }

    /**
     * Creates the Commands Advanced section: clearChannel limits.
     */
    private Component createCommandsAdvancedSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.clearChannelDeleteLimit"), clearChannelDeleteLimitSpinner);
        addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.clearChannelAgeDays"), clearChannelAgeDaysSpinner);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.commandsAdvanced"), panel);
    }

    /**
     * Creates the Updates section.
     */
    private Component createUpdatesSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.updateRepository"), updateRepositoryField);
        addSpanningRow(panel, gbc, 1, updateAutoUpdateCheckBox);
        addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.updateCheckIntervalHours"), updateCheckIntervalSpinner);
        addRow(panel, gbc, 3, GuiLanguage.msg("gui.config.updateGithubToken"), updateGithubTokenField);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.updates"), panel);
    }

    /**
     * Creates the Dangerous section: dangerous.evalEngine, dangerous.eval.
     *
     * <p>eval is not presented as an ordinary toggle. What it turns on is the bot owner running
     * arbitrary code in the bot's own process — anything the host machine can do, the eval
     * command can do — so the checkbox is paired with a warning in the danger color that says
     * so plainly, rather than relying on the setting's name to communicate the risk.
     */
    private Component createDangerousSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.evalEngine"), evalEngineField);
        addSpanningRow(panel, gbc, 1, evalCheckBox);
        addSpanningRow(panel, gbc, 2, warningLabel(GuiLanguage.msg("gui.config.evalWarning")));

        return Widgets.titledCard(GuiLanguage.msg("gui.config.dangerous"), panel);
    }

    /**
     * Creates the GUI & Web section: gui.enabled/fontSize/language, web.bindAddress/allowConfigEdit.
     *
     * <p>web.allowConfigEdit gets the same treatment as dangerous.eval above, for the same
     * reason: reference.conf itself calls out that turning it on lets anyone holding the web
     * panel's token change config.txt, so the checkbox here says so too rather than reading
     * like any other flag.
     */
    private Component createGuiWebSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addSpanningRow(panel, gbc, 0, guiEnabledCheckBox);
        addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.guiFontSize"), guiFontSizeSpinner);
        addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.guiLanguage"), guiLanguageComboBox);
        addRow(panel, gbc, 3, GuiLanguage.msg("gui.config.webBindAddress"), webBindAddressField);
        addSpanningRow(panel, gbc, 4, webAllowConfigEditCheckBox);
        addSpanningRow(panel, gbc, 5, warningLabel(GuiLanguage.msg("gui.config.webAllowConfigEditWarning")));

        return Widgets.titledCard(GuiLanguage.msg("gui.config.guiWeb"), panel);
    }

    /**
     * Creates the Performance section: performance.nasBufferMs, performance.frameBufferMs.
     */
    private Component createPerformanceSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.nasBufferMs"), nasBufferMsSpinner);
        addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.frameBufferMs"), frameBufferMsSpinner);

        return Widgets.titledCard(GuiLanguage.msg("gui.config.performance"), panel);
    }

    /**
     * Creates the read-only "Advanced (config.txt only)" section.
     *
     * <p>commands.aliases and playback.transforms are nested structures — serialising either of
     * them out of a single text field is exactly what {@code WebWrites.validate} refuses
     * STRING_LIST/CONFIG options for ("must be edited in config.txt directly"), because
     * guessing at how to flatten one back into HOCON is how a config file gets corrupted. Shown
     * here read-only, rendered as HOCON, rather than an editor that could silently mangle
     * either one.
     */
    private Component createAdvancedReadOnlySection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();

        addSpanningRow(panel, gbc, 0, noteLabel(GuiLanguage.msg("gui.config.aliasesReadOnlyNote")));
        addSpanningRow(panel, gbc, 1, new JScrollPane(aliasesTextArea));
        addSpanningRow(panel, gbc, 2, noteLabel(GuiLanguage.msg("gui.config.transformsReadOnlyNote")));
        addSpanningRow(panel, gbc, 3, new JScrollPane(transformsTextArea));

        return Widgets.titledCard(GuiLanguage.msg("gui.config.advancedReadOnly"), panel);
    }

    /** A short callout in the danger color, for a control whose plain label understates the risk. */
    private JLabel warningLabel(String text) {
        JLabel label = new JLabel("<html><body style='width: 340px'>" + escapeHtml(text) + "</body></html>");
        label.setFont(Tokens.fontSmall());
        label.setForeground(Tokens.danger());
        return label;
    }

    /** A short, muted explanatory line — for context that isn't a warning. */
    private JLabel noteLabel(String text) {
        JLabel label = new JLabel("<html><body style='width: 420px'>" + escapeHtml(text) + "</body></html>");
        label.setFont(Tokens.fontSmall());
        label.setForeground(Tokens.textMuted());
        return label;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
        // Fixed to the bottom of the whole panel rather than inside the scrollable content, so
        // it stays visible regardless of which section — old or newly added — is scrolled into
        // view; every option on this panel needs a restart, not just the original ones.
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

        // Discord — same rule as the proxy password above: setText only, never logged.
        discordTokenField.setText(config.getToken());
        discordOwnerSpinner.setValue(config.getOwnerId());

        // Localization
        botLanguageComboBox.setSelectedItem(config.getDefaultLanguage().name());

        // Now Playing
        npMinimalMessageCheckBox.setSelected(config.useMinimalNowPlayingMessage());
        npShowButtonsCheckBox.setSelected(config.showNowPlayingButtons());
        npShowProgressBarCheckBox.setSelected(config.showNpProgressBar());

        // YouTube Advanced
        youtubePoTokenField.setText(config.getYoutubePoToken());
        youtubeVisitorDataField.setText(config.getYoutubeVisitorData());
        youtubeClientsModel.clear();
        List<String> clients = config.getYoutubeClients();
        if (clients != null) {
            for (String client : clients) {
                youtubeClientsModel.addElement(client);
            }
        }

        // Playback Advanced
        maxHistorySizeSpinner.setValue(config.getMaxHistorySize());
        for (Map.Entry<AudioSource, JCheckBox> entry : audioSourceCheckBoxes.entrySet()) {
            entry.getValue().setSelected(config.isAudioSourceEnabled(entry.getKey()));
        }
        transformsTextArea.setText(renderConfig(config.getTransforms()));

        // Commands Advanced
        clearChannelDeleteLimitSpinner.setValue(config.getClearChannelDeleteLimit());
        clearChannelAgeDaysSpinner.setValue((int) config.getClearChannelAgeDays());
        aliasesTextArea.setText(renderConfig(config.getAliasesConfig()));

        // Updates
        updateRepositoryField.setText(config.getUpdateRepository());
        updateAutoUpdateCheckBox.setSelected(config.isAutoUpdate());
        updateCheckIntervalSpinner.setValue(config.getUpdateIntervalHours());
        // setText, not a log line — same rule as every other secret field on this panel.
        updateGithubTokenField.setText(config.getUpdateGithubToken());

        // Dangerous
        evalEngineField.setText(config.getEvalEngine());
        evalCheckBox.setSelected(config.useEval());

        // GUI & Web
        guiEnabledCheckBox.setSelected(config.getGuiEnabled());
        guiFontSizeSpinner.setValue(config.getGuiFontSize());
        // The raw value, not getGuiLanguage(): that getter resolves a blank config value against
        // ui.language, which would make "left blank" and "pinned to whatever ui.language
        // currently is" indistinguishable here — and silently pin it the next time this saves.
        guiLanguageComboBox.setSelectedItem(config.getGuiLanguageRaw());
        webBindAddressField.setText(config.getWebBindAddress());
        webAllowConfigEditCheckBox.setSelected(config.isWebConfigEditAllowed());

        // Performance
        nasBufferMsSpinner.setValue(config.getNasBufferMs());
        frameBufferMsSpinner.setValue(config.getFrameBufferMs());
    }

    /** Renders a nested config value as HOCON text, for the read-only advanced section. */
    private String renderConfig(Config value) {
        if (value == null) {
            return "";
        }
        return value.root().render(ConfigRenderOptions.defaults()
                .setOriginComments(false)
                .setComments(false)
                .setJson(false));
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
        updates.put("proxy.password", quoteString(readAndClearPassword(proxyPasswordField)));
        updates.put("proxy.lavaplayer", String.valueOf(proxyLavaplayerCheckBox.isSelected()));
        updates.put("proxy.jda", String.valueOf(proxyJdaCheckBox.isSelected()));
        updates.put("proxy.github", String.valueOf(proxyGithubCheckBox.isSelected()));

        // Discord
        updates.put("discord.token", quoteString(readAndClearPassword(discordTokenField)));
        updates.put("discord.owner", String.valueOf(discordOwnerSpinner.getValue()));

        // Localization
        updates.put("ui.language", quoteString((String) botLanguageComboBox.getSelectedItem()));

        // Now Playing
        updates.put("nowPlaying.minimalMessage", String.valueOf(npMinimalMessageCheckBox.isSelected()));
        updates.put("nowPlaying.showButtons", String.valueOf(npShowButtonsCheckBox.isSelected()));
        updates.put("nowPlaying.showProgressBar", String.valueOf(npShowProgressBarCheckBox.isSelected()));

        // YouTube Advanced
        updates.put("playback.youtube.poToken", quoteString(readAndClearPassword(youtubePoTokenField)));
        updates.put("playback.youtube.visitorData", quoteString(readAndClearPassword(youtubeVisitorDataField)));
        updates.put("playback.youtube.clients", formatStringList(currentYoutubeClients()));

        // Playback Advanced
        updates.put("playback.maxHistorySize", String.valueOf(maxHistorySizeSpinner.getValue()));
        for (Map.Entry<AudioSource, JCheckBox> entry : audioSourceCheckBoxes.entrySet()) {
            updates.put("playback.audioSources." + entry.getKey().getConfigName(),
                    String.valueOf(entry.getValue().isSelected()));
        }
        // playback.transforms is shown read-only above; there is deliberately no entry for it
        // here — see createAdvancedReadOnlySection for why.

        // Commands Advanced
        updates.put("commands.clearChannel.deleteLimit", String.valueOf(clearChannelDeleteLimitSpinner.getValue()));
        updates.put("commands.clearChannel.ageDays", String.valueOf(clearChannelAgeDaysSpinner.getValue()));
        // commands.aliases is shown read-only above; there is deliberately no entry for it
        // here — see createAdvancedReadOnlySection for why.

        // Updates
        updates.put("updates.repository", quoteString(updateRepositoryField.getText()));
        updates.put("updates.autoUpdate", String.valueOf(updateAutoUpdateCheckBox.isSelected()));
        updates.put("updates.checkIntervalHours", String.valueOf(updateCheckIntervalSpinner.getValue()));
        updates.put("updates.githubToken", quoteString(readAndClearPassword(updateGithubTokenField)));

        // Dangerous
        updates.put("dangerous.evalEngine", quoteString(evalEngineField.getText()));
        updates.put("dangerous.eval", String.valueOf(evalCheckBox.isSelected()));

        // GUI & Web
        updates.put("gui.enabled", String.valueOf(guiEnabledCheckBox.isSelected()));
        updates.put("gui.fontSize", String.valueOf(guiFontSizeSpinner.getValue()));
        updates.put("gui.language", quoteString((String) guiLanguageComboBox.getSelectedItem()));
        updates.put("web.bindAddress", quoteString(webBindAddressField.getText()));
        updates.put("web.allowConfigEdit", String.valueOf(webAllowConfigEditCheckBox.isSelected()));

        // Performance
        updates.put("performance.nasBufferMs", String.valueOf(nasBufferMsSpinner.getValue()));
        updates.put("performance.frameBufferMs", String.valueOf(frameBufferMsSpinner.getValue()));

        return updates;
    }

    /** The clients list currently shown in the editor, in display (= save) order. */
    private List<String> currentYoutubeClients() {
        List<String> clients = new ArrayList<>();
        for (int i = 0; i < youtubeClientsModel.size(); i++) {
            clients.add(youtubeClientsModel.get(i));
        }
        return clients;
    }

    /**
     * Renders a list of strings as a HOCON array of quoted strings.
     *
     * <p>The only STRING_LIST field this panel writes: playback.youtube.clients is built from
     * the ordered editor above, out of a known and bounded set of names, rather than parsed out
     * of free text — which is what makes writing it back out safe.
     */
    private String formatStringList(List<String> values) {
        StringBuilder sb = new StringBuilder("[ ");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(quoteString(values.get(i)));
        }
        sb.append(" ]");
        return sb.toString();
    }

    /**
     * Reads a password field and wipes the char array Swing handed back.
     *
     * <p>The resulting String still ends up in the updates map and, briefly, in the rewritten
     * config.txt content — Java offers no way around that once a String exists at all — but
     * the mutable buffer this came from does not have to sit in memory a moment longer than
     * it takes to copy it out. Shared by every secret field on this panel: the Discord token,
     * the update GitHub token, the YouTube poToken pair, and the proxy password.
     */
    private String readAndClearPassword(JPasswordField field) {
        char[] password = field.getPassword();
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
