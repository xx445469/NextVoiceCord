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
import com.jagrosh.jmusicbot.audio.lavalink.LavalinkNodeConfig;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.jagrosh.jmusicbot.config.update.ConfigUpdater;
import com.jagrosh.jmusicbot.gui.GuiLanguage;
import com.jagrosh.jmusicbot.gui.GuiPreferences;
import com.jagrosh.jmusicbot.gui.GuiWindowState;
import com.jagrosh.jmusicbot.gui.components.Widgets;
import com.jagrosh.jmusicbot.gui.theme.ThemeManager;
import com.jagrosh.jmusicbot.gui.theme.Tokens;
import com.jagrosh.jmusicbot.i18n.Language;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Configuration panel for viewing and editing bot configuration.
 * Allows editing of every option in {@link com.jagrosh.jmusicbot.config.model.ConfigOption}.
 *
 * <p>Two tiers, not two tabs: the settings someone would plausibly change in their first month
 * are shown as ordinary cards; the rest sit behind {@link Widgets.CollapsibleCard} headings that
 * start collapsed but stay in the same scroll, so nothing has moved to a separate screen someone
 * has to guess about. A section's collapsed/expanded state is remembered across restarts (see
 * {@link #loadExpandedAdvancedSections()} / {@link #onAdvancedSectionToggled}), and the filter
 * box at the top hides whichever rows — common or advanced — do not match what was typed, against
 * both the row's label and its config key.
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

    /** Reused across test-connection presses; a Jackson mapper is safe to share and reuse. */
    private static final ObjectMapper LAVALINK_TEST_MAPPER = new ObjectMapper();

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

    // Lavalink section (common) — playback.engine, lavalink.nodes. See createLavalinkSection
    // for why this is common rather than tucked behind an advanced card.
    private final JComboBox<String> lavalinkEngineComboBox;
    private final DefaultListModel<LavalinkNodeConfig> lavalinkNodesModel;
    private final JList<LavalinkNodeConfig> lavalinkNodesJList;
    private final JLabel lavalinkTestConnectionStatusLabel;

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

    // Proxy section (advanced)
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

    // YouTube Advanced section — playback.youtube.poToken/visitorData; the clients list itself
    // (playback.youtube.clients) lives in the common YouTube section below, not here — see
    // createYoutubeClientsSection.
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

    // Updates section — updates.autoUpdate is common; repository/checkIntervalHours/githubToken
    // are advanced (see createUpdatesAdvancedSection).
    private final JTextField updateRepositoryField;
    private final JCheckBox updateAutoUpdateCheckBox;
    private final JSpinner updateCheckIntervalSpinner;
    private final JPasswordField updateGithubTokenField;

    // Dangerous section (advanced) — dangerous.evalEngine, dangerous.eval
    private final JTextField evalEngineField;
    private final JCheckBox evalCheckBox;

    // Appearance (common) — gui.theme/fontSize/language.
    // GUI & Web Advanced — gui.enabled, web.bindAddress/allowConfigEdit.
    private final JCheckBox guiEnabledCheckBox;
    private final JSpinner guiFontSizeSpinner;
    private final JComboBox<String> guiLanguageComboBox;
    private final JComboBox<ThemeManager.Theme> guiThemeComboBox;
    private final JTextField webBindAddressField;
    private final JCheckBox webAllowConfigEditCheckBox;

    // Performance section (advanced) — performance.nasBufferMs/frameBufferMs
    private final JSpinner nasBufferMsSpinner;
    private final JSpinner frameBufferMsSpinner;

    // Search / tiering bookkeeping — see the class javadoc.
    private JTextField searchField;
    private final List<FilterSection> filterSections = new ArrayList<>();
    private final Map<String, Widgets.CollapsibleCard> advancedSections = new LinkedHashMap<>();
    private final Set<String> expandedAdvancedSections;

    /**
     * Creates the configuration panel.
     *
     * @param bot the bot instance
     */
    public ConfigPanel(Bot bot) {
        this.config = bot.getConfig();
        // Read before any section is built: createXAdvancedSection() below consults this to
        // decide whether that section starts open or collapsed.
        this.expandedAdvancedSections = new LinkedHashSet<>(loadExpandedAdvancedSections());

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

        // Lavalink — "fallback" is offered because config.txt accepts it, not because it works;
        // see createLavalinkSection for the note shown beside it.
        lavalinkEngineComboBox = new JComboBox<>(new String[]{"lavaplayer", "lavalink", "fallback"});
        lavalinkNodesModel = new DefaultListModel<>();
        lavalinkNodesJList = new JList<>(lavalinkNodesModel);
        lavalinkNodesJList.setVisibleRowCount(4);
        lavalinkNodesJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lavalinkNodesJList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean cellHasFocus) {
                // describe(), never the record itself: it never includes the password (see
                // LavalinkNodeConfig), which is exactly what a list rendered in this window
                // must never show.
                String text = value instanceof LavalinkNodeConfig node ? node.describe() : String.valueOf(value);
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        lavalinkTestConnectionStatusLabel = new JLabel(" ");
        lavalinkTestConnectionStatusLabel.setFont(Tokens.fontSmall());

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

        // Appearance / GUI & Web
        guiEnabledCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.guiEnabled"));
        guiFontSizeSpinner = new JSpinner(new SpinnerNumberModel(12, 8, 24, 1));
        guiThemeComboBox = new JComboBox<>(ThemeManager.Theme.values());
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
                guiThemeComboBox, youtubeClientsAddComboBox, lavalinkEngineComboBox);
        applyFieldStyle(songInStatusCheckBox, stayInChannelCheckBox, useYouTubeOAuthCheckBox,
                npImagesCheckBox, updateAlertsCheckBox, proxyLavaplayerCheckBox, proxyJdaCheckBox,
                proxyGithubCheckBox, npMinimalMessageCheckBox, npShowButtonsCheckBox,
                npShowProgressBarCheckBox, updateAutoUpdateCheckBox, evalCheckBox, guiEnabledCheckBox,
                webAllowConfigEditCheckBox);
        applyFieldStyle(aloneTimeSpinner, maxSecondsSpinner, maxYTPlaylistPagesSpinner, skipRatioSpinner,
                proxyPortSpinner, discordOwnerSpinner, maxHistorySizeSpinner, clearChannelDeleteLimitSpinner,
                clearChannelAgeDaysSpinner, updateCheckIntervalSpinner, guiFontSizeSpinner,
                nasBufferMsSpinner, frameBufferMsSpinner);
        applyFieldStyle(youtubeClientsJList, aliasesTextArea, transformsTextArea, lavalinkNodesJList,
                lavalinkTestConnectionStatusLabel);
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

        JPanel bottom = Widgets.transparent(null);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        JLabel subtitle = Widgets.muted(GuiLanguage.msg("gui.config.subtitle"));
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        bottom.add(subtitle);
        bottom.add(Box.createVerticalStrut(Tokens.SPACE_SM));
        bottom.add(buildSearchRow());
        header.add(bottom, BorderLayout.SOUTH);

        return header;
    }

    /**
     * The filter box: hides rows — common or advanced — that match neither their label nor
     * their config key as the reader types. With 57 options spread across nearly twenty cards,
     * this is what keeps "I know what it's called" from turning into a manual scroll.
     */
    private Component buildSearchRow() {
        JPanel row = Widgets.transparent(new BorderLayout(Tokens.SPACE_SM, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);

        JLabel label = new JLabel(GuiLanguage.msg("gui.config.searchLabel"));
        label.setFont(Tokens.fontBody());
        label.setForeground(Tokens.textMuted());
        row.add(label, BorderLayout.WEST);

        searchField = new JTextField();
        searchField.setFont(Tokens.fontBody());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        row.add(searchField, BorderLayout.CENTER);

        return row;
    }

    private Component buildScrollArea() {
        JPanel content = Widgets.transparent(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // Common settings — shown as ordinary cards, in the same order the panel always had.
        addSection(content, createCommandsSection());
        addSection(content, createPresenceSection());
        addSection(content, createVoiceSection());
        addSection(content, createPlaybackSection());
        addSection(content, createLavalinkSection());
        addSection(content, createEmojisSection());
        addSection(content, createOtherSection());
        addSection(content, createProxySection());
        addSection(content, createDiscordSection());
        addSection(content, createLocalizationSection());
        addSection(content, createNowPlayingSection());
        addSection(content, createYoutubeClientsSection());
        addSection(content, createYoutubeAdvancedSection());
        addSection(content, createPlaybackAdvancedSection());
        addSection(content, createCommandsAdvancedSection());
        addSection(content, createUpdatesSection());
        addSection(content, createUpdatesAdvancedSection());
        addSection(content, createDangerousSection());
        addSection(content, createAppearanceSection());
        addSection(content, createGuiWebAdvancedSection());
        addSection(content, createPerformanceSection());
        addSection(content, createAdvancedReadOnlySection());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scrollPane;
    }

    private void addSection(JPanel content, Component section) {
        if (content.getComponentCount() > 0) {
            content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        }
        content.add(section);
    }

    /** A form panel: label column left, control column right, growing horizontally. */
    private JPanel formPanel() {
        JPanel panel = Widgets.transparent(new GridBagLayout());
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private GridBagConstraints rowConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        // SPACE_SM rather than SPACE_XS: rows this close together read as one dense block
        // rather than a list of separate settings — the same reason every card already keeps
        // SPACE_MD from its neighbour (see addSection).
        gbc.insets = new Insets(Tokens.SPACE_SM, 0, Tokens.SPACE_SM, Tokens.SPACE_MD);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    /**
     * Adds a label/control row and returns it as a {@link FilterRow}, searchable on both the
     * label text and {@code configKey}.
     */
    private FilterRow addRow(JPanel panel, GridBagConstraints gbc, int row, String label,
                              JComponent control, String configKey) {
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

        return new FilterRow(label, configKey, l, control);
    }

    /**
     * Adds a single control spanning both columns (a checkbox, a warning, a composite editor)
     * and returns it as a {@link FilterRow}. {@code configKey} may be {@code null} for a row
     * that only ever exists merged into another one via {@link FilterRow#merge} — a warning or
     * note label that has no config key of its own.
     */
    private FilterRow addSpanningRow(JPanel panel, GridBagConstraints gbc, int row,
                                      JComponent control, String configKey) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(control, gbc);
        gbc.gridwidth = 1;

        String label = control instanceof JCheckBox ? ((JCheckBox) control).getText() : "";
        return new FilterRow(label, configKey, control);
    }

    /** Registers a section's rows with the search filter. {@code collapsible} is null for a common section. */
    private void registerSection(Component card, Widgets.CollapsibleCard collapsible, List<FilterRow> rows) {
        filterSections.add(new FilterSection(card, collapsible, rows));
    }

    /**
     * Wraps {@code body} in a {@link Widgets.CollapsibleCard}, starting expanded only if
     * {@code key} was left open last session, and wires its toggle back into
     * {@link #onAdvancedSectionToggled} so that choice is remembered for next time.
     */
    private Widgets.CollapsibleCard advancedCard(String key, String title, JPanel body) {
        Widgets.CollapsibleCard card = new Widgets.CollapsibleCard(
                title, body, expandedAdvancedSections.contains(key));
        card.onToggle(expanded -> onAdvancedSectionToggled(key, expanded));
        advancedSections.put(key, card);
        return card;
    }

    /**
     * Creates the Commands configuration section.
     */
    private Component createCommandsSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.prefix"), prefixField, "commands.prefix"));
        rows.add(addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.altPrefix"), altPrefixField, "commands.altPrefix"));
        rows.add(addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.helpWord"), helpWordField, "commands.help"));

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.commands"), panel);
        registerSection(card, null, rows);
        return card;
    }

    /**
     * Creates the Presence configuration section.
     */
    private Component createPresenceSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.gameStatus"), gameField, "presence.game"));
        rows.add(addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.onlineStatus"), statusComboBox, "presence.status"));
        rows.add(addSpanningRow(panel, gbc, 2, songInStatusCheckBox, "presence.songInStatus"));

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.presence"), panel);
        registerSection(card, null, rows);
        return card;
    }

    /**
     * Creates the Voice configuration section.
     */
    private Component createVoiceSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addSpanningRow(panel, gbc, 0, stayInChannelCheckBox, "voice.stayInChannel"));
        rows.add(addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.aloneTimeUntilStop"), aloneTimeSpinner,
                "voice.aloneTimeUntilStopSeconds"));

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.voice"), panel);
        registerSection(card, null, rows);
        return card;
    }

    /**
     * Creates the Playback configuration section.
     */
    private Component createPlaybackSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.maxTrackSeconds"), maxSecondsSpinner,
                "playback.maxTrackSeconds"));
        rows.add(addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.skipRatio"), skipRatioSpinner,
                "playback.skipRatio"));
        rows.add(addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.maxYouTubePlaylistPages"), maxYTPlaylistPagesSpinner,
                "playback.maxYouTubePlaylistPages"));
        rows.add(addSpanningRow(panel, gbc, 3, useYouTubeOAuthCheckBox, "playback.youtube.useOAuth"));

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.playback"), panel);
        registerSection(card, null, rows);
        return card;
    }

    /**
     * Creates the common Lavalink section: playback.engine and lavalink.nodes.
     *
     * <p>{@code playback.engine} is the switch that decides which playback path the whole bot
     * uses, so — unlike the Proxy section below, which someone already knows they need before
     * going looking for it — this stays common, never collapsed: someone who installed this
     * build to use Lavalink should not have to go hunting through advanced cards to find it.
     * {@code fallback} is offered as a real choice, because config.txt accepts it, but the note
     * merged into this row says plainly that it is not implemented yet (stage 3 — both engines
     * live, with a handover on node failure) and resolves to {@code lavaplayer} with a logged
     * warning. The window is not allowed to offer it as though it worked when the startup log
     * already says otherwise.
     *
     * <p>{@code lavalink.nodes} gets a real list editor, built the same way as the
     * {@code playback.youtube.clients} editor above — an ordered list plus add/remove/reorder
     * controls — except each entry here is a small object (name/host/port/password/secure)
     * rather than a single string, so add and edit open a small dialog instead of picking from a
     * combo box (see {@link #showLavalinkNodeDialog}). Stage 1 only ever reads the first entry —
     * the note merged into this row says so — so the editor lets someone build a longer list
     * without it silently going unused.
     *
     * <p>The Test Connection button (see {@link #testLavalinkConnection}) is the fastest way to
     * find out whether a node is actually reachable, rather than restarting the bot and reading
     * a stack trace: it hits the node's own {@code GET /v4/info} off the Swing event thread.
     *
     * <p>Leads with an intro line stating plainly that a Lavalink node is a separate audio
     * server, not an HTTP/SOCKS proxy — see {@link #createProxySection()}'s javadoc for the
     * incident (an owner's Lavalink node pasted into the proxy fields) this exists to prevent.
     */
    private Component createLavalinkSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addSpanningRow(panel, gbc, 0, noteLabel(GuiLanguage.msg("gui.config.lavalinkIntro")), null));

        FilterRow engineRow = addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.lavalinkEngine"),
                lavalinkEngineComboBox, "playback.engine");
        engineRow.merge(addSpanningRow(panel, gbc, 2,
                noteLabel(GuiLanguage.msg("gui.config.lavalinkEngineFallbackNote")), null));
        rows.add(engineRow);

        FilterRow nodesRow = addRow(panel, gbc, 3, GuiLanguage.msg("gui.config.lavalinkNodes"),
                buildLavalinkNodesEditor(), "lavalink.nodes");
        nodesRow.merge(addSpanningRow(panel, gbc, 4,
                noteLabel(GuiLanguage.msg("gui.config.lavalinkNodesNote")), null));
        nodesRow.merge(addSpanningRow(panel, gbc, 5, lavalinkTestConnectionStatusLabel, null));
        rows.add(nodesRow);

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.lavalink"), panel);
        registerSection(card, null, rows);
        return card;
    }

    /** The ordered list, add/edit/remove/reorder controls and test-connection button for lavalink.nodes. */
    private JPanel buildLavalinkNodesEditor() {
        JPanel wrapper = Widgets.transparent(new BorderLayout(Tokens.SPACE_SM, 0));

        JScrollPane listScroll = new JScrollPane(lavalinkNodesJList);
        listScroll.setPreferredSize(new Dimension(240, 90));
        wrapper.add(listScroll, BorderLayout.CENTER);

        JPanel controls = Widgets.transparent(new GridLayout(0, 1, 0, Tokens.SPACE_XS));

        JButton addButton = new JButton(GuiLanguage.msg("gui.config.lavalinkNodeAdd"));
        addButton.setFont(Tokens.fontSmall());
        addButton.addActionListener(e -> {
            LavalinkNodeConfig created = showLavalinkNodeDialog(null);
            if (created != null) {
                lavalinkNodesModel.addElement(withDefaultedName(created, lavalinkNodesModel.size()));
            }
        });

        JButton editButton = new JButton(GuiLanguage.msg("gui.config.lavalinkNodeEdit"));
        editButton.setFont(Tokens.fontSmall());
        editButton.addActionListener(e -> {
            int index = lavalinkNodesJList.getSelectedIndex();
            if (index < 0) {
                return;
            }
            LavalinkNodeConfig updated = showLavalinkNodeDialog(lavalinkNodesModel.get(index));
            if (updated != null) {
                lavalinkNodesModel.set(index, withDefaultedName(updated, index));
            }
        });

        JButton removeButton = new JButton(GuiLanguage.msg("gui.config.lavalinkNodeRemove"));
        removeButton.setFont(Tokens.fontSmall());
        removeButton.addActionListener(e -> {
            int index = lavalinkNodesJList.getSelectedIndex();
            if (index >= 0) {
                lavalinkNodesModel.remove(index);
            }
        });

        JButton upButton = new JButton(GuiLanguage.msg("gui.config.lavalinkNodeUp"));
        upButton.setFont(Tokens.fontSmall());
        upButton.addActionListener(e -> moveSelectedLavalinkNode(-1));

        JButton downButton = new JButton(GuiLanguage.msg("gui.config.lavalinkNodeDown"));
        downButton.setFont(Tokens.fontSmall());
        downButton.addActionListener(e -> moveSelectedLavalinkNode(1));

        JButton testButton = new JButton(GuiLanguage.msg("gui.config.lavalinkTestConnection"));
        testButton.setFont(Tokens.fontSmall());
        testButton.addActionListener(e -> testLavalinkConnection(testButton));

        controls.add(addButton);
        controls.add(editButton);
        controls.add(removeButton);
        controls.add(upButton);
        controls.add(downButton);
        controls.add(testButton);

        wrapper.add(controls, BorderLayout.EAST);
        return wrapper;
    }

    /** Moves the selected node up (-1) or down (+1) in the ordered list, if it can move. */
    private void moveSelectedLavalinkNode(int direction) {
        int index = lavalinkNodesJList.getSelectedIndex();
        int target = index + direction;
        if (index < 0 || target < 0 || target >= lavalinkNodesModel.size()) {
            return;
        }
        LavalinkNodeConfig value = lavalinkNodesModel.remove(index);
        lavalinkNodesModel.add(target, value);
        lavalinkNodesJList.setSelectedIndex(target);
    }

    /**
     * Defaults a blank name to {@code node-<index>}, the same fallback
     * {@link LavalinkNodeConfig#parseList} uses when config.txt is edited by hand without one.
     */
    private LavalinkNodeConfig withDefaultedName(LavalinkNodeConfig node, int index) {
        if (node.name() != null && !node.name().isBlank()) {
            return node;
        }
        return new LavalinkNodeConfig("node-" + index, node.host(), node.port(), node.password(), node.secure());
    }

    /**
     * Shows the add/edit dialog for one {@code lavalink.nodes} entry. Re-prompts rather than
     * accepting a blank host, since {@link LavalinkNodeConfig#parseList} would otherwise silently
     * skip whatever gets saved here. Returns {@code null} if the dialog is cancelled.
     */
    private LavalinkNodeConfig showLavalinkNodeDialog(LavalinkNodeConfig existing) {
        JTextField nameField = new JTextField(existing == null ? "" : existing.name(), 15);
        JTextField hostField = new JTextField(existing == null ? "" : existing.host(), 15);
        JSpinner portSpinner = new JSpinner(
                new SpinnerNumberModel(existing == null ? 2333 : existing.port(), 1, 65535, 1));
        // A password, not a JTextField: same reasoning as every other credential field on this
        // panel (see the class javadoc) — never shown or logged in plain text.
        JPasswordField passwordField = new JPasswordField(existing == null ? "" : existing.password(), 15);
        JCheckBox secureCheckBox = new JCheckBox(GuiLanguage.msg("gui.config.lavalinkNodeSecure"),
                existing != null && existing.secure());
        applyFieldStyle(nameField, hostField, portSpinner, passwordField, secureCheckBox);

        JPanel form = formPanel();
        GridBagConstraints gbc = rowConstraints();
        addRow(form, gbc, 0, GuiLanguage.msg("gui.config.lavalinkNodeName"), nameField, null);
        addRow(form, gbc, 1, GuiLanguage.msg("gui.config.lavalinkNodeHost"), hostField, null);
        addRow(form, gbc, 2, GuiLanguage.msg("gui.config.lavalinkNodePort"), portSpinner, null);
        addRow(form, gbc, 3, GuiLanguage.msg("gui.config.lavalinkNodePassword"), passwordField, null);
        addSpanningRow(form, gbc, 4, secureCheckBox, null);

        String title = existing == null
                ? GuiLanguage.msg("gui.config.lavalinkNodeDialogTitleAdd")
                : GuiLanguage.msg("gui.config.lavalinkNodeDialogTitleEdit");

        while (true) {
            int result = JOptionPane.showConfirmDialog(this, form, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return null;
            }
            String host = hostField.getText().trim();
            if (host.isEmpty()) {
                JOptionPane.showMessageDialog(this, GuiLanguage.msg("gui.config.lavalinkNodeDialogHostRequired"),
                        title, JOptionPane.ERROR_MESSAGE);
                continue;
            }
            return new LavalinkNodeConfig(nameField.getText().trim(), host, (Integer) portSpinner.getValue(),
                    readAndClearPassword(passwordField), secureCheckBox.isSelected());
        }
    }

    /** The node currently selected in the list, or the first one if nothing is selected. */
    private LavalinkNodeConfig selectedOrFirstLavalinkNode() {
        int index = lavalinkNodesJList.getSelectedIndex();
        if (index >= 0) {
            return lavalinkNodesModel.get(index);
        }
        return lavalinkNodesModel.isEmpty() ? null : lavalinkNodesModel.get(0);
    }

    /**
     * Probes a Lavalink node's {@code GET /v4/info} off the Swing event thread and reports one
     * of three distinct outcomes: reachable and authenticated, reachable but the password was
     * rejected, or unreachable — restarting the bot and reading a stack trace is not how anyone
     * should have to find out which of those applies. The button stays disabled for the duration
     * of the request, via {@link SwingWorker}, which runs {@code doInBackground} off the EDT and
     * hands the result back to {@code done} on it.
     */
    private void testLavalinkConnection(JButton button) {
        LavalinkNodeConfig node = selectedOrFirstLavalinkNode();
        if (node == null) {
            lavalinkTestConnectionStatusLabel.setForeground(Tokens.textMuted());
            lavalinkTestConnectionStatusLabel.setText(GuiLanguage.msg("gui.config.lavalinkTestConnectionNoNode"));
            return;
        }

        button.setEnabled(false);
        lavalinkTestConnectionStatusLabel.setForeground(Tokens.textMuted());
        lavalinkTestConnectionStatusLabel.setText(
                GuiLanguage.msg("gui.config.lavalinkTestConnectionRunning", node.describe()));

        SwingWorker<LavalinkProbeResult, Void> worker = new SwingWorker<>() {
            @Override
            protected LavalinkProbeResult doInBackground() {
                return probeLavalinkNode(node);
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                LavalinkProbeResult result;
                try {
                    result = get();
                } catch (Exception ex) {
                    result = LavalinkProbeResult.unreachable(GuiLanguage.msg(
                            "gui.config.lavalinkTestConnectionUnreachable", node.describe(), ex.getMessage()));
                }
                lavalinkTestConnectionStatusLabel.setForeground(switch (result.outcome()) {
                    case AUTHENTICATED -> Tokens.success();
                    case PASSWORD_REJECTED -> Tokens.warning();
                    case UNREACHABLE -> Tokens.danger();
                });
                lavalinkTestConnectionStatusLabel.setText(result.message());
            }
        };
        worker.execute();
    }

    /**
     * Hits {@code GET /v4/info} with the node's password in the {@code Authorization} header —
     * exactly the request documented and verified against a real node — and turns the response
     * into one of {@link LavalinkProbeResult}'s three outcomes. Runs entirely on the calling
     * thread; the caller ({@link #testLavalinkConnection}) is what keeps this off the EDT.
     */
    private LavalinkProbeResult probeLavalinkNode(LavalinkNodeConfig node) {
        java.net.http.HttpRequest request;
        try {
            request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(node.httpBaseUrl() + "/v4/info"))
                    .timeout(java.time.Duration.ofSeconds(8))
                    .header("Authorization", node.password())
                    .GET()
                    .build();
        } catch (RuntimeException ex) {
            return LavalinkProbeResult.unreachable(GuiLanguage.msg(
                    "gui.config.lavalinkTestConnectionUnreachable", node.describe(), ex.getMessage()));
        }

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        try {
            java.net.http.HttpResponse<String> response =
                    client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                return LavalinkProbeResult.authenticated(describeLavalinkInfo(response.body()));
            }
            if (status == 401 || status == 403) {
                return LavalinkProbeResult.passwordRejected(GuiLanguage.msg(
                        "gui.config.lavalinkTestConnectionAuthFailed", node.describe(), status));
            }
            return LavalinkProbeResult.unreachable(GuiLanguage.msg(
                    "gui.config.lavalinkTestConnectionUnreachable", node.describe(), "HTTP " + status));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return LavalinkProbeResult.unreachable(GuiLanguage.msg(
                    "gui.config.lavalinkTestConnectionUnreachable", node.describe(), ex.getMessage()));
        } catch (Exception ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return LavalinkProbeResult.unreachable(GuiLanguage.msg(
                    "gui.config.lavalinkTestConnectionUnreachable", node.describe(), message));
        }
    }

    /** Pulls version/sourceManagers/plugins out of a successful /v4/info body for the status line. */
    private String describeLavalinkInfo(String body) {
        try {
            JsonNode root = LAVALINK_TEST_MAPPER.readTree(body);
            String version = root.path("version").path("semver").asText("?");
            int sourceManagers = root.path("sourceManagers").size();
            int plugins = root.path("plugins").size();
            return GuiLanguage.msg("gui.config.lavalinkTestConnectionOk", version, sourceManagers, plugins);
        } catch (java.io.IOException | RuntimeException ex) {
            return GuiLanguage.msg("gui.config.lavalinkTestConnectionOk", "?", 0, 0);
        }
    }

    /** One of the three outcomes {@link #testLavalinkConnection} must report distinctly. */
    private record LavalinkProbeResult(Outcome outcome, String message) {
        private enum Outcome { AUTHENTICATED, PASSWORD_REJECTED, UNREACHABLE }

        static LavalinkProbeResult authenticated(String message) {
            return new LavalinkProbeResult(Outcome.AUTHENTICATED, message);
        }

        static LavalinkProbeResult passwordRejected(String message) {
            return new LavalinkProbeResult(Outcome.PASSWORD_REJECTED, message);
        }

        static LavalinkProbeResult unreachable(String message) {
            return new LavalinkProbeResult(Outcome.UNREACHABLE, message);
        }
    }

    /**
     * Creates the UI/Emojis configuration section.
     */
    private Component createEmojisSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.success"), successEmojiField, "ui.emojis.success"));
        rows.add(addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.warning"), warningEmojiField, "ui.emojis.warning"));
        rows.add(addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.error"), errorEmojiField, "ui.emojis.error"));
        rows.add(addRow(panel, gbc, 3, GuiLanguage.msg("gui.config.loading"), loadingEmojiField, "ui.emojis.loading"));
        rows.add(addRow(panel, gbc, 4, GuiLanguage.msg("gui.config.searching"), searchingEmojiField, "ui.emojis.searching"));

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.uiEmojis"), panel);
        registerSection(card, null, rows);
        return card;
    }

    /**
     * Creates the Other configuration section.
     */
    private Component createOtherSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addSpanningRow(panel, gbc, 0, npImagesCheckBox, "nowPlaying.images"));
        rows.add(addSpanningRow(panel, gbc, 1, updateAlertsCheckBox, "updates.alerts"));
        rows.add(addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.logLevel"), logLevelComboBox, "logging.level"));
        rows.add(addRow(panel, gbc, 3, GuiLanguage.msg("gui.config.playlistsFolder"), playlistsFolderField,
                "paths.playlistsFolder"));

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.other"), panel);
        registerSection(card, null, rows);
        return card;
    }

    /**
     * Creates the Proxy configuration section (advanced): an HTTP/SOCKS proxy for the bot's
     * own outbound network requests.
     *
     * <p>This is not where a Lavalink node goes. An owner's log once showed exactly that
     * mistake: a Lavalink node's host/port/password pasted into these fields, silently routing
     * YouTube traffic through it as though it were an HTTP proxy — because the two sections
     * used to look identical (host/port/username/password cards) and the only cue telling them
     * apart was a checkbox reading "...through proxy (Lavaplayer)", three letters away from
     * "Lavalink". The intro line below says so explicitly; the checkbox no longer names the
     * engine at all (see EN.json's {@code gui.config.proxyLavaplayer}); and
     * {@link #createLavalinkSection()} — common tier, laid out as an engine selector and a node
     * list rather than a fourth host/port card — is what a Lavalink node's address actually
     * belongs in.
     *
     * <p>The three checkboxes are labelled by what they route rather than by their raw config
     * keys — "lavaplayer", "jda" and "github" mean nothing to someone who has not read the
     * source, but "audio playback", "Discord connection" and "update checks" are the things
     * they actually chose to proxy or not.
     *
     * <p>Collapsed by default: a proxy is infrastructure someone already knows they need,
     * rather than something a first run prompts anyone to go looking for.
     */
    private Component createProxySection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addSpanningRow(panel, gbc, 0, noteLabel(GuiLanguage.msg("gui.config.proxyIntro")), null));

        rows.add(addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.proxyHost"), proxyHostField, "proxy.host"));
        rows.add(addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.proxyPort"), proxyPortSpinner, "proxy.port"));
        rows.add(addRow(panel, gbc, 3, GuiLanguage.msg("gui.config.proxyUsername"), proxyUsernameField, "proxy.username"));
        rows.add(addRow(panel, gbc, 4, GuiLanguage.msg("gui.config.proxyPassword"), proxyPasswordField, "proxy.password"));
        rows.add(addSpanningRow(panel, gbc, 5, proxyLavaplayerCheckBox, "proxy.lavaplayer"));
        rows.add(addSpanningRow(panel, gbc, 6, proxyJdaCheckBox, "proxy.jda"));
        rows.add(addSpanningRow(panel, gbc, 7, proxyGithubCheckBox, "proxy.github"));

        Widgets.CollapsibleCard card = advancedCard("proxy", GuiLanguage.msg("gui.config.proxy"), panel);
        registerSection(card, card, rows);
        return card;
    }

    /**
     * Creates the Discord configuration section: discord.token, discord.owner.
     *
     * <p>Both are required fields the bot cannot start without. The token in particular is
     * shown masked, exactly like the proxy password above, and carries an explicit warning:
     * unlike most settings on this panel, getting it wrong does not degrade a feature — it
     * stops the bot from starting at all. Common, never collapsed, and the warning is merged
     * into the token's own {@link FilterRow} so the two can never be shown or hidden apart.
     */
    private Component createDiscordSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        FilterRow tokenRow = addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.discordToken"), discordTokenField,
                "discord.token");
        tokenRow.merge(addSpanningRow(panel, gbc, 1, warningLabel(GuiLanguage.msg("gui.config.discordTokenWarning")), null));
        rows.add(tokenRow);
        rows.add(addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.discordOwner"), discordOwnerSpinner, "discord.owner"));

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.discord"), panel);
        registerSection(card, null, rows);
        return card;
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
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.botLanguage"), botLanguageComboBox, "ui.language"));

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.localization"), panel);
        registerSection(card, null, rows);
        return card;
    }

    /**
     * Creates the Now Playing configuration section.
     */
    private Component createNowPlayingSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addSpanningRow(panel, gbc, 0, npMinimalMessageCheckBox, "nowPlaying.minimalMessage"));
        rows.add(addSpanningRow(panel, gbc, 1, npShowButtonsCheckBox, "nowPlaying.showButtons"));
        rows.add(addSpanningRow(panel, gbc, 2, npShowProgressBarCheckBox, "nowPlaying.showProgressBar"));

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.nowPlaying"), panel);
        registerSection(card, null, rows);
        return card;
    }

    /**
     * Creates the common YouTube section: playback.youtube.clients only.
     *
     * <p>The clients list is the setting users most often need to change — YouTube breaks
     * individual InnerTube clients rather than all at once — so unlike poToken/visitorData
     * below it is common, not advanced, and gets a real ordered editor rather than a read-only
     * dump, built from {@link #KNOWN_YOUTUBE_CLIENTS}, a known and bounded set of names read
     * from {@code AudioSource.clientByName}.
     */
    private Component createYoutubeClientsSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.youtubeClients"), buildYoutubeClientsEditor(),
                "playback.youtube.clients"));

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.youtube"), panel);
        registerSection(card, null, rows);
        return card;
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
     * Creates the YouTube Advanced section (advanced): playback.youtube.poToken/visitorData.
     *
     * <p>Both are opaque tokens scraped from a browser session rather than something typed by
     * hand day to day, which is what keeps them out of the common tier even though the client
     * list two sections up, from the same {@code playback.youtube.*} family, is common.
     */
    private Component createYoutubeAdvancedSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.youtubePoToken"), youtubePoTokenField,
                "playback.youtube.poToken"));
        rows.add(addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.youtubeVisitorData"), youtubeVisitorDataField,
                "playback.youtube.visitorData"));

        Widgets.CollapsibleCard card = advancedCard("youtubeAdvanced", GuiLanguage.msg("gui.config.youtubeAdvanced"), panel);
        registerSection(card, card, rows);
        return card;
    }

    /**
     * Creates the Playback Advanced section (advanced): maxHistorySize and the audioSources
     * checkboxes.
     */
    private Component createPlaybackAdvancedSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();
        int row = 0;

        rows.add(addRow(panel, gbc, row++, GuiLanguage.msg("gui.config.maxHistorySize"), maxHistorySizeSpinner,
                "playback.maxHistorySize"));

        JLabel audioSourcesLabel = new JLabel(GuiLanguage.msg("gui.config.audioSources"));
        audioSourcesLabel.setFont(Tokens.fontBody());
        audioSourcesLabel.setForeground(Tokens.textMuted());
        FilterRow audioSourcesLabelRow = addSpanningRow(panel, gbc, row++, audioSourcesLabel, null);

        JPanel sourcesGrid = Widgets.transparent(new GridLayout(0, 2, Tokens.SPACE_SM, 0));
        for (JCheckBox checkBox : audioSourceCheckBoxes.values()) {
            sourcesGrid.add(checkBox);
        }
        FilterRow sourcesRow = addSpanningRow(panel, gbc, row, sourcesGrid, "playback.audioSources");
        rows.add(sourcesRow.merge(audioSourcesLabelRow));

        Widgets.CollapsibleCard card = advancedCard("playbackAdvanced", GuiLanguage.msg("gui.config.playbackAdvanced"), panel);
        registerSection(card, card, rows);
        return card;
    }

    /**
     * Creates the Commands Advanced section (advanced): clearChannel limits.
     */
    private Component createCommandsAdvancedSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.clearChannelDeleteLimit"), clearChannelDeleteLimitSpinner,
                "commands.clearChannel.deleteLimit"));
        rows.add(addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.clearChannelAgeDays"), clearChannelAgeDaysSpinner,
                "commands.clearChannel.ageDays"));

        Widgets.CollapsibleCard card = advancedCard("commandsAdvanced", GuiLanguage.msg("gui.config.commandsAdvanced"), panel);
        registerSection(card, card, rows);
        return card;
    }

    /**
     * Creates the common Updates section: updates.autoUpdate only. The repository, check
     * interval and GitHub token live in {@link #createUpdatesAdvancedSection()} instead.
     */
    private Component createUpdatesSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addSpanningRow(panel, gbc, 0, updateAutoUpdateCheckBox, "updates.autoUpdate"));

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.updates"), panel);
        registerSection(card, null, rows);
        return card;
    }

    /**
     * Creates the Updates Advanced section (advanced): repository, check interval, GitHub token.
     */
    private Component createUpdatesAdvancedSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.updateRepository"), updateRepositoryField,
                "updates.repository"));
        rows.add(addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.updateCheckIntervalHours"), updateCheckIntervalSpinner,
                "updates.checkIntervalHours"));
        rows.add(addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.updateGithubToken"), updateGithubTokenField,
                "updates.githubToken"));

        Widgets.CollapsibleCard card = advancedCard("updatesAdvanced", GuiLanguage.msg("gui.config.updatesAdvanced"), panel);
        registerSection(card, card, rows);
        return card;
    }

    /**
     * Creates the Dangerous section (advanced): dangerous.evalEngine, dangerous.eval.
     *
     * <p>eval is not presented as an ordinary toggle. What it turns on is the bot owner running
     * arbitrary code in the bot's own process — anything the host machine can do, the eval
     * command can do — so the checkbox is paired with a warning in the danger color that says
     * so plainly, rather than relying on the setting's name to communicate the risk. The warning
     * is merged into the checkbox's own {@link FilterRow}: opening this section, collapsed or
     * filtered or not, never shows the checkbox without it.
     */
    private Component createDangerousSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.evalEngine"), evalEngineField, "dangerous.evalEngine"));
        FilterRow evalRow = addSpanningRow(panel, gbc, 1, evalCheckBox, "dangerous.eval");
        evalRow.merge(addSpanningRow(panel, gbc, 2, warningLabel(GuiLanguage.msg("gui.config.evalWarning")), null));
        rows.add(evalRow);

        Widgets.CollapsibleCard card = advancedCard("dangerous", GuiLanguage.msg("gui.config.dangerous"), panel);
        registerSection(card, card, rows);
        return card;
    }

    /**
     * Creates the common Appearance section: gui.theme, gui.language, gui.fontSize.
     *
     * <p>These three govern only how this desktop window looks and speaks to the person running
     * it, never the bot itself — the same class of setting the Preferences page already applies
     * instantly and saves through {@link GuiPreferences}. They are kept here too, editable the
     * same way as every other option and covered by the same save/restore round trip, so this
     * panel's claim to show every option stays true without sending someone to a second screen
     * to find gui.theme.
     */
    private Component createAppearanceSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addRow(panel, gbc, 0, GuiLanguage.msg("gui.config.guiTheme"), guiThemeComboBox, "gui.theme"));
        rows.add(addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.guiLanguage"), guiLanguageComboBox, "gui.language"));
        rows.add(addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.guiFontSize"), guiFontSizeSpinner, "gui.fontSize"));

        Component card = Widgets.titledCard(GuiLanguage.msg("gui.config.appearance"), panel);
        registerSection(card, null, rows);
        return card;
    }

    /**
     * Creates the GUI & Web Advanced section (advanced): gui.enabled, web.bindAddress,
     * web.allowConfigEdit.
     *
     * <p>web.allowConfigEdit gets the same treatment as dangerous.eval above, for the same
     * reason: reference.conf itself calls out that turning it on lets anyone holding the web
     * panel's token change config.txt, so the checkbox here says so too rather than reading
     * like any other flag — merged into the same {@link FilterRow} so the warning can never be
     * shown or hidden apart from it.
     */
    private Component createGuiWebAdvancedSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addSpanningRow(panel, gbc, 0, guiEnabledCheckBox, "gui.enabled"));
        rows.add(addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.webBindAddress"), webBindAddressField, "web.bindAddress"));
        FilterRow allowEditRow = addSpanningRow(panel, gbc, 2, webAllowConfigEditCheckBox, "web.allowConfigEdit");
        allowEditRow.merge(addSpanningRow(panel, gbc, 3,
                warningLabel(GuiLanguage.msg("gui.config.webAllowConfigEditWarning")), null));
        rows.add(allowEditRow);

        Widgets.CollapsibleCard card = advancedCard("guiWebAdvanced", GuiLanguage.msg("gui.config.guiWeb"), panel);
        registerSection(card, card, rows);
        return card;
    }

    /**
     * Creates the Performance section (advanced): performance.nasBufferMs, performance.frameBufferMs.
     *
     * <p>Both buffers exist to smooth out the bot's own audio pipeline (see reference.conf),
     * which only runs at all when {@code playback.engine} is {@code lavaplayer} — a Lavalink
     * node does its own buffering on its side of the connection. The note below says so, per
     * the same "don't leave a setting silently inert" reasoning as
     * {@link #createLavalinkSection()}'s node-list note.
     */
    private Component createPerformanceSection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        rows.add(addSpanningRow(panel, gbc, 0,
                noteLabel(GuiLanguage.msg("gui.config.performanceLavaplayerOnlyNote")), null));
        rows.add(addRow(panel, gbc, 1, GuiLanguage.msg("gui.config.nasBufferMs"), nasBufferMsSpinner,
                "performance.nasBufferMs"));
        rows.add(addRow(panel, gbc, 2, GuiLanguage.msg("gui.config.frameBufferMs"), frameBufferMsSpinner,
                "performance.frameBufferMs"));

        Widgets.CollapsibleCard card = advancedCard("performance", GuiLanguage.msg("gui.config.performance"), panel);
        registerSection(card, card, rows);
        return card;
    }

    /**
     * Creates the read-only "Advanced (config.txt only)" section (advanced).
     *
     * <p>commands.aliases and playback.transforms are nested structures — serialising either of
     * them out of a single text field is exactly what {@code WebWrites.validate} refuses
     * STRING_LIST/CONFIG options for ("must be edited in config.txt directly"), because
     * guessing at how to flatten one back into HOCON is how a config file gets corrupted. Shown
     * here read-only, rendered as HOCON, rather than an editor that could silently mangle
     * either one. Each note is merged with its own text area into one {@link FilterRow}.
     */
    private Component createAdvancedReadOnlySection() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = rowConstraints();
        List<FilterRow> rows = new ArrayList<>();

        FilterRow aliasesRow = addSpanningRow(panel, gbc, 0, noteLabel(GuiLanguage.msg("gui.config.aliasesReadOnlyNote")),
                "commands.aliases");
        aliasesRow.merge(addSpanningRow(panel, gbc, 1, new JScrollPane(aliasesTextArea), null));
        rows.add(aliasesRow);

        FilterRow transformsRow = addSpanningRow(panel, gbc, 2,
                noteLabel(GuiLanguage.msg("gui.config.transformsReadOnlyNote")), "playback.transforms");
        transformsRow.merge(addSpanningRow(panel, gbc, 3, new JScrollPane(transformsTextArea), null));
        rows.add(transformsRow);

        Widgets.CollapsibleCard card = advancedCard("advancedReadOnly", GuiLanguage.msg("gui.config.advancedReadOnly"), panel);
        registerSection(card, card, rows);
        return card;
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

        // Lavalink — the raw engine value (see BotConfig.getPlaybackEngineRaw), not the resolved
        // one, so a config.txt with engine = "fallback" shows "fallback" here instead of quietly
        // reverting the displayed choice to lavaplayer.
        lavalinkEngineComboBox.setSelectedItem(config.getPlaybackEngineRaw());
        lavalinkNodesModel.clear();
        for (LavalinkNodeConfig node : config.getLavalinkNodes()) {
            lavalinkNodesModel.addElement(node);
        }
        lavalinkTestConnectionStatusLabel.setText(" ");

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

        // Appearance / GUI & Web
        guiEnabledCheckBox.setSelected(config.getGuiEnabled());
        guiThemeComboBox.setSelectedItem(ThemeManager.Theme.fromConfigKey(config.getGuiTheme()));
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

        // Lavalink
        updates.put("playback.engine", quoteString((String) lavalinkEngineComboBox.getSelectedItem()));
        updates.put("lavalink.nodes", formatLavalinkNodesList(currentLavalinkNodes()));

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

        // Appearance / GUI & Web
        updates.put("gui.enabled", String.valueOf(guiEnabledCheckBox.isSelected()));
        updates.put("gui.theme", quoteString(((ThemeManager.Theme) guiThemeComboBox.getSelectedItem()).getConfigKey()));
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

    /** The nodes currently shown in the editor, in display (= save) order. */
    private List<LavalinkNodeConfig> currentLavalinkNodes() {
        List<LavalinkNodeConfig> nodes = new ArrayList<>();
        for (int i = 0; i < lavalinkNodesModel.size(); i++) {
            nodes.add(lavalinkNodesModel.get(i));
        }
        return nodes;
    }

    /**
     * Renders lavalink.nodes as a single-line HOCON array of node objects — the same "single
     * line" choice {@link #formatStringList} makes for playback.youtube.clients, and for the
     * same reason: {@link #replaceLavalinkNodesValue} below replaces exactly the span between
     * the array's own brackets in the existing file, however many lines that span happens to be,
     * so the *replacement* does not need to match the original formatting — only stay valid
     * HOCON, which a single line trivially is.
     */
    private String formatLavalinkNodesList(List<LavalinkNodeConfig> nodes) {
        StringBuilder sb = new StringBuilder("[ ");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            LavalinkNodeConfig node = nodes.get(i);
            sb.append("{ name = ").append(quoteString(node.name()))
              .append(", host = ").append(quoteString(node.host()))
              .append(", port = ").append(node.port())
              .append(", password = ").append(quoteString(node.password()))
              .append(", secure = ").append(node.secure())
              .append(" }");
        }
        sb.append(" ]");
        return sb.toString();
    }

    /**
     * Renders a list of strings as a HOCON array of quoted strings.
     *
     * <p>The only STRING_LIST field this panel writes: playback.youtube.clients is built from
     * the ordered editor above, out of a known and bounded set of names, rather than parsed out
     * of free text — which is what makes writing it back out safe. Reused by
     * {@link #onAdvancedSectionToggled} for the same reason: the set of open advanced sections
     * is also a small, known, safely-quotable set of names.
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
     * the update GitHub token, the YouTube poToken pair, the proxy password, and a Lavalink
     * node's password in the add/edit dialog.
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

            // lavalink.nodes is a HOCON array that can legitimately span many lines in
            // config.txt (see reference.conf, where it does). The single-line regex replace
            // every other key on this panel uses below would only touch the array's opening
            // "[" and leave the rest of the old array sitting there as orphaned, unparseable
            // text — exactly the kind of corruption a structured editor is supposed to avoid.
            // This walks the actual bracket structure instead of guessing at a line pattern.
            if ("lavalink.nodes".equals(key)) {
                result = replaceLavalinkNodesValue(result, value);
                continue;
            }

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
     * Replaces {@code lavalink.nodes}'s value in {@code content} with {@code newValue}, without
     * assuming the existing value fits on one line.
     *
     * <p>This is the part of the whole feature most able to corrupt a config file: every other
     * value on this panel is a scalar the existing single-line regex can safely replace, but the
     * node list is a HOCON array that reference.conf ships spread across many lines, with a node
     * object nested inside it. Blindly regexing "the rest of this line" would leave everything
     * after the first line — the rest of the old array — behind as orphaned text. This instead
     * locates the array's own opening {@code [} and its matching {@code ]} (see
     * {@link #findMatchingBracket}) and replaces exactly that span, so the result is always
     * either "the old array, gone entirely" or nothing changed at all — never a corrupted mix
     * of both.
     *
     * <p>If no {@code lavalink.nodes} key can be found at all (config.txt predates this feature
     * and has not yet been regenerated against reference.conf), a new {@code lavalink} block is
     * appended rather than the edit being silently dropped.
     */
    private String replaceLavalinkNodesValue(String content, String newValue) {
        int[] range = findLavalinkNodesValueRange(content);
        if (range == null) {
            LOG.warn("Could not find lavalink.nodes in config.txt; appending a new lavalink section "
                    + "instead of guessing at a location that might corrupt the file.");
            String separator = content.endsWith("\n") ? "\n" : "\n\n";
            return content + separator + "lavalink {\n  nodes = " + newValue + "\n}\n";
        }
        return content.substring(0, range[0]) + newValue + content.substring(range[1] + 1);
    }

    /**
     * Finds the {@code [...]} span of {@code lavalink.nodes}'s current value, trying the nested
     * block form ({@code lavalink { nodes = [...] }}, what reference.conf ships) first and the
     * dotted top-level form ({@code lavalink.nodes = [...]}) second. Returns {@code null} if
     * neither is found.
     *
     * @return {@code {indexOf('['), indexOf(matching ']')}}, or {@code null}
     */
    private int[] findLavalinkNodesValueRange(String content) {
        java.util.regex.Matcher sectionMatcher =
                java.util.regex.Pattern.compile("(?m)^\\s*lavalink\\s*\\{").matcher(content);
        if (sectionMatcher.find()) {
            int openBraceIndex = content.indexOf('{', sectionMatcher.start());
            int closeBraceIndex = findMatchingBracket(content, openBraceIndex);
            if (closeBraceIndex > openBraceIndex) {
                int[] range = findBracketedValueRange(content, openBraceIndex + 1, closeBraceIndex, "nodes");
                if (range != null) {
                    return range;
                }
            }
        }
        return findBracketedValueRange(content, 0, content.length(), "lavalink.nodes");
    }

    /** Finds {@code keyName = [...]} inside {@code content[regionStart, regionEnd)}, if present. */
    private int[] findBracketedValueRange(String content, int regionStart, int regionEnd, String keyName) {
        java.util.regex.Matcher keyMatcher = java.util.regex.Pattern
                .compile("(?m)^\\s*" + java.util.regex.Pattern.quote(keyName) + "\\s*=\\s*")
                .matcher(content);
        keyMatcher.region(regionStart, regionEnd);
        if (!keyMatcher.find()) {
            return null;
        }
        int bracketIndex = keyMatcher.end();
        while (bracketIndex < regionEnd && Character.isWhitespace(content.charAt(bracketIndex))) {
            bracketIndex++;
        }
        if (bracketIndex >= regionEnd || content.charAt(bracketIndex) != '[') {
            return null;
        }
        int valueEnd = findMatchingBracket(content, bracketIndex);
        if (valueEnd < 0 || valueEnd > regionEnd) {
            return null;
        }
        return new int[]{bracketIndex, valueEnd};
    }

    /**
     * Finds the index of the character matching the bracket at {@code openIndex} (a {@code [} or
     * {@code {}), skipping over the contents of double-quoted strings — escaped quotes included —
     * so a bracket character inside a quoted value (a password containing {@code [}, say) is
     * never mistaken for structure. Returns -1 if no matching close bracket is found.
     */
    private static int findMatchingBracket(String content, int openIndex) {
        int depth = 0;
        boolean inString = false;
        for (int i = openIndex; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '[' || c == '{') {
                depth++;
            } else if (c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
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

    /**
     * Reads which advanced sections were left open last session.
     *
     * <p>Backed by {@link GuiWindowState}, not {@link BotConfig} or {@link GuiPreferences}:
     * this is a per-window Swing preference, not a bot setting, and never was one. An earlier
     * version wrote it into config.txt as {@code gui.configPanelAdvancedSections}, which is not
     * a real {@link com.jagrosh.jmusicbot.config.model.ConfigOption} — so
     * {@link com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics} correctly flagged it as
     * an unknown key on every subsequent load, which triggered a config repair (and another
     * {@code config.txt.bakN} backup) on every restart, since the panel rewrote the key right
     * back on the very next toggle. {@link GuiWindowState}'s javadoc has the full account.
     * Never throws; a read that fails comes back as "nothing was open," which is also this
     * panel's default.
     */
    private Set<String> loadExpandedAdvancedSections() {
        return GuiWindowState.loadExpandedAdvancedSections();
    }

    /**
     * Persists which advanced sections are open, through {@link GuiWindowState}. Runs on every
     * toggle rather than waiting for Save: opening a section is not something someone thinks of
     * as "a config edit" they need to remember to save, the same reasoning {@link GuiPreferences}
     * documents for its own, unrelated fields (theme/font/language, which — unlike this — really
     * are bot settings with defaults in reference.conf, and so belong in config.txt).
     */
    private void onAdvancedSectionToggled(String key, boolean expanded) {
        if (expanded) {
            expandedAdvancedSections.add(key);
        } else {
            expandedAdvancedSections.remove(key);
        }
        GuiWindowState.saveExpandedAdvancedSections(expandedAdvancedSections);
    }

    /**
     * Hides whichever registered rows do not match {@code searchField}'s text, against both
     * the row's label and its config key; a card with no matching row at all is hidden too. An
     * advanced section with a match is shown open for the duration of the search without
     * changing its stored preference — see {@link Widgets.CollapsibleCard#setFilterExpanded}.
     * Clearing the box puts everything back exactly where it was.
     */
    private void applyFilter() {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        boolean filtering = !query.isEmpty();

        for (FilterSection section : filterSections) {
            boolean anyMatch = false;
            for (FilterRow row : section.rows) {
                boolean matches = !filtering || row.matches(query);
                row.setVisible(matches);
                anyMatch |= matches;
            }
            section.card.setVisible(anyMatch);
            if (section.collapsible != null) {
                if (filtering) {
                    section.collapsible.setFilterExpanded(anyMatch);
                } else {
                    section.collapsible.clearFilterOverride();
                }
            }
        }

        revalidate();
        repaint();
    }

    /**
     * One filterable unit: usually a label and its control, sometimes a control alone (a
     * spanning checkbox), and occasionally several components a warning or note has been
     * {@link #merge}d into — everything that must show or hide together stays in one FilterRow
     * so the filter can never separate a field from the warning attached to it.
     */
    private static final class FilterRow {
        private final List<JComponent> parts = new ArrayList<>();
        private final String haystack;

        FilterRow(String label, String configKey, JComponent... initialParts) {
            String key = configKey == null ? "" : configKey;
            String text = label == null ? "" : label;
            this.haystack = (text + " " + key).toLowerCase(Locale.ROOT);
            parts.addAll(Arrays.asList(initialParts));
        }

        /** Folds another row's components into this one, so they always show or hide together. */
        FilterRow merge(FilterRow other) {
            parts.addAll(other.parts);
            return this;
        }

        boolean matches(String query) {
            return haystack.contains(query);
        }

        void setVisible(boolean visible) {
            for (JComponent part : parts) {
                part.setVisible(visible);
            }
        }
    }

    /** A card and the {@link FilterRow}s inside it, plus its {@link Widgets.CollapsibleCard} if it has one. */
    private static final class FilterSection {
        final Component card;
        final Widgets.CollapsibleCard collapsible;
        final List<FilterRow> rows;

        FilterSection(Component card, Widgets.CollapsibleCard collapsible, List<FilterRow> rows) {
            this.card = card;
            this.collapsible = collapsible;
            this.rows = rows;
        }
    }
}
