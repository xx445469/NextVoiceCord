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
import com.jagrosh.jmusicbot.gui.GuiLanguage;
import com.jagrosh.jmusicbot.gui.GuiPreferences;
import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.gui.components.Widgets;
import com.jagrosh.jmusicbot.gui.theme.ThemeManager;
import com.jagrosh.jmusicbot.gui.theme.Tokens;
import com.jagrosh.jmusicbot.update.UpdateChecker;
import com.jagrosh.jmusicbot.utils.OtherUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings panel for configuring GUI appearance and viewing configuration.
 *
 * @author Arif Banai (arif-banai)
 */
public class SettingsPanel extends JPanel implements SectionedPanel {

    private final Bot bot;

    // The panel's own cards, in the order they appear — see getSections(). Every
    // create*Section() below appends itself here, right at the point where its title and its
    // card come together, so this list can never name a card that isn't actually on the page.
    private final List<Section> sections = new ArrayList<>();

    public SettingsPanel(Bot bot) {
        this.bot = bot;

        setLayout(new BorderLayout(0, Tokens.SPACE_MD));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildScrollArea(), BorderLayout.CENTER);
    }

    /**
     * This panel's cards, in the order they appear on the page. See {@link SectionedPanel} for
     * why this — not a list kept in the sidebar — is the only place that names them.
     */
    @Override
    public List<Section> getSections() {
        return List.copyOf(sections);
    }

    private Component buildHeader() {
        JPanel header = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_XS));
        header.add(Widgets.pageTitle(GuiLanguage.msg("gui.nav.preferences")), BorderLayout.NORTH);
        header.add(Widgets.muted(GuiLanguage.msg("gui.preferences.subtitle")), BorderLayout.SOUTH);
        return header;
    }

    private Component buildScrollArea() {
        JPanel content = Widgets.transparent(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(createAppearanceSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createConfigSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createUpdatesSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createInfoSection());

        return Widgets.scrollable(content);
    }

    /** One preference: a label on the left, its control on the right. */
    private JPanel preferenceRow(String label, JComponent control) {
        JPanel row = Widgets.transparent(new BorderLayout(Tokens.SPACE_MD, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel l = new JLabel(label);
        l.setFont(Tokens.fontLabel());
        l.setForeground(Tokens.text());
        row.add(l, BorderLayout.WEST);

        JPanel controlWrap = Widgets.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlWrap.add(control);
        row.add(controlWrap, BorderLayout.EAST);
        return row;
    }

    /**
     * Creates the appearance settings section.
     */
    /**
     * The window's own language.
     *
     * <p>Rendered from the language's name in itself — 日本語 rather than Japanese — because
     * someone changing away from a language they cannot read has to recognise their own to
     * find it.
     */
    private JComponent buildLanguageBox() {
        JComboBox<Language> box = new JComboBox<>(GuiLanguage.available().toArray(new Language[0]));
        box.setSelectedItem(GuiLanguage.get());
        box.setFont(Tokens.fontBody());
        box.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,
                    Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof Language language) {
                    setText(language.getNativeName() + "  ·  " + language.getEnglishName());
                }
                return this;
            }
        });

        box.addActionListener(e -> {
            Language chosen = (Language) box.getSelectedItem();
            if (chosen == null || chosen == GuiLanguage.get()) {
                return;
            }
            GuiLanguage.set(chosen);
            GuiPreferences.saveLanguage(chosen.name());
            // Labels created before the change keep the old text, and rebuilding the whole
            // window mid-session risks more than it fixes. Saying so is more honest than a
            // half-translated window with no explanation.
            JOptionPane.showMessageDialog(this,
                    GuiLanguage.msg("gui.language.restartNotice"),
                    GuiLanguage.msg("gui.language.label"),
                    JOptionPane.INFORMATION_MESSAGE);
        });
        return box;
    }

    private JPanel createAppearanceSection() {
        JPanel body = Widgets.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        // A segmented control rather than a combo box: four themes are few enough that every
        // choice can stay on screen at once, so comparing them does not require opening a menu.
        List<ThemeManager.Theme> themes = List.of(ThemeManager.Theme.values());
        List<String> themeLabels = themes.stream().map(ThemeManager.Theme::getDisplayName).toList();
        int initialTheme = Math.max(0, themes.indexOf(ThemeManager.getCurrentTheme()));
        Widgets.Segmented themeControl = new Widgets.Segmented(themeLabels, initialTheme);
        themeControl.onChange(index -> {
            ThemeManager.Theme selected = themes.get(index);
            ThemeManager.setTheme(selected);
            GuiPreferences.saveTheme(selected.getConfigKey());
        });

        SpinnerNumberModel fontModel = new SpinnerNumberModel(
            ThemeManager.getBaseFontSize(), 8, 24, 1
        );
        JSpinner fontSpinner = new JSpinner(fontModel);
        fontSpinner.setFont(Tokens.fontBody());

        // Held back briefly before writing. The spinner fires on every arrow click, and
        // someone stepping 12 → 16 would otherwise rewrite config.txt four times on the way.
        Timer fontSaveDelay = new Timer(500, e ->
                GuiPreferences.saveFontSize((Integer) fontSpinner.getValue()));
        fontSaveDelay.setRepeats(false);

        fontSpinner.addChangeListener(e -> {
            int size = (Integer) fontSpinner.getValue();
            ThemeManager.setBaseFontSize(size);
            fontSaveDelay.restart();
        });

        body.add(preferenceRow(GuiLanguage.msg("gui.language.label"), buildLanguageBox()));
        // The distinction is worth stating outright: someone changing this expects it to
        // affect what the bot says in Discord, and it does not.
        body.add(Widgets.hint(GuiLanguage.msg("gui.language.hint")));
        body.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        body.add(preferenceRow(GuiLanguage.msg("gui.appearance.theme"), themeControl));
        body.add(Box.createVerticalStrut(Tokens.SPACE_SM));
        body.add(preferenceRow(GuiLanguage.msg("gui.appearance.fontSize"), fontSpinner));
        body.add(Widgets.hint(GuiLanguage.msg("gui.appearance.savedNote")));

        String title = GuiLanguage.msg("gui.section.appearance");
        JPanel card = Widgets.titledCard(title, body);
        sections.add(new Section(title, card));
        return card;
    }

    /**
     * Creates the configuration info section.
     */
    private JPanel createConfigSection() {
        JPanel body = Widgets.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel buttonPanel = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_SM, 0));
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);

        JButton openFolderButton = Widgets.secondaryButton(GuiLanguage.msg("gui.preferences.openFolder"));
        openFolderButton.addActionListener(e -> openConfigFolder());
        buttonPanel.add(openFolderButton);

        JButton openFileButton = Widgets.secondaryButton(GuiLanguage.msg("gui.preferences.openFile"));
        openFileButton.addActionListener(e -> openConfigFile());
        buttonPanel.add(openFileButton);

        body.add(buttonPanel);
        body.add(Widgets.hint(GuiLanguage.msg("gui.preferences.location", getConfigPath())));

        String title = GuiLanguage.msg("gui.preferences.configuration");
        JPanel card = Widgets.titledCard(title, body);
        sections.add(new Section(title, card));
        return card;
    }

    /**
     * Creates the on-demand update-check section.
     *
     * <p>{@code updates.autoUpdate} governs the timer in {@link com.jagrosh.jmusicbot.update.SelfUpdater};
     * this button is deliberately separate from it and never downloads or installs anything —
     * it only asks GitHub what the latest release is and says what it found. Someone with
     * auto-update off still has a way to look right now, without that button quietly turning
     * auto-update on for one run.
     */
    private JPanel createUpdatesSection() {
        JPanel body = Widgets.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(preferenceRow(GuiLanguage.msg("gui.preferences.currentVersion"),
                mutedValue(OtherUtil.getCurrentVersion())));
        body.add(Box.createVerticalStrut(Tokens.SPACE_SM));

        // Primary: this is the action the card exists for, unlike the two open-a-file-manager
        // buttons in the Configuration card above.
        JButton checkButton = Widgets.primaryButton(GuiLanguage.msg("gui.preferences.checkForUpdates"));

        // Hidden until there is somewhere for it to go, rather than disabled, so its
        // appearance itself signals "an update was found" without needing to read the label.
        JButton releasesButton = Widgets.secondaryButton(GuiLanguage.msg("gui.action.openInBrowser"));
        releasesButton.setVisible(false);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(Tokens.fontBody());
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);

        checkButton.addActionListener(e -> checkForUpdates(checkButton, releasesButton, statusLabel));

        JPanel buttonPanel = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_SM, 0));
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);
        buttonPanel.add(checkButton);
        buttonPanel.add(releasesButton);
        body.add(buttonPanel);
        body.add(Box.createVerticalStrut(Tokens.SPACE_SM));
        body.add(statusLabel);

        String title = GuiLanguage.msg("gui.preferences.updates");
        JPanel card = Widgets.titledCard(title, body);
        sections.add(new Section(title, card));
        return card;
    }

    /**
     * Runs the check off the event thread.
     *
     * <p>{@link UpdateChecker#checkForUpdate} makes an HTTP call to the GitHub API; doing that
     * on the EDT would freeze the whole window for however long the request takes to answer —
     * or to time out, on a bad connection.
     */
    private void checkForUpdates(JButton checkButton, JButton releasesButton, JLabel statusLabel) {
        checkButton.setEnabled(false);
        releasesButton.setVisible(false);
        statusLabel.setForeground(Tokens.textMuted());
        statusLabel.setText(GuiLanguage.msg("gui.preferences.checkingForUpdates"));

        String current = OtherUtil.getCurrentVersion();

        new SwingWorker<UpdateChecker.CheckOutcome, Void>() {
            @Override
            protected UpdateChecker.CheckOutcome doInBackground() {
                return new UpdateChecker().checkForUpdate(current);
            }

            @Override
            protected void done() {
                // Re-enabled unconditionally: a check that is queued up twice is exactly what
                // this guards against, and that has to hold whichever outcome came back.
                checkButton.setEnabled(true);
                try {
                    presentOutcome(get(), releasesButton, statusLabel);
                } catch (Exception ex) {
                    // doInBackground() cannot throw — checkForUpdate() catches everything — so
                    // this only fires if that ever changes, and it must still say something
                    // rather than leave the button looking like it did nothing.
                    statusLabel.setForeground(Tokens.danger());
                    statusLabel.setText(GuiLanguage.msg("gui.preferences.updateCheckFailed", ex.getMessage()));
                }
            }
        }.execute();
    }

    /** Renders whichever of the three outcomes the check produced. */
    private void presentOutcome(UpdateChecker.CheckOutcome outcome, JButton releasesButton, JLabel statusLabel) {
        switch (outcome) {
            case UpdateChecker.CheckOutcome.UpToDate upToDate -> {
                statusLabel.setForeground(Tokens.success());
                statusLabel.setText(GuiLanguage.msg("gui.preferences.updateUpToDate", upToDate.currentVersion()));
            }
            case UpdateChecker.CheckOutcome.UpdateAvailable available -> {
                statusLabel.setForeground(Tokens.accent());
                statusLabel.setText(GuiLanguage.msg("gui.preferences.updateAvailable", available.latestVersion()));

                // A fresh listener each time: an operator can click "check" repeatedly, and a
                // stale listener from an earlier check would open last time's URL instead of
                // this one's — or, worse, both, once for every check that ever found an update.
                for (var listener : releasesButton.getActionListeners()) {
                    releasesButton.removeActionListener(listener);
                }
                releasesButton.addActionListener(e -> openReleasesPage(available.releasesUrl()));
                releasesButton.setVisible(true);
            }
            case UpdateChecker.CheckOutcome.Failed failed -> {
                statusLabel.setForeground(Tokens.danger());
                statusLabel.setText(GuiLanguage.msg("gui.preferences.updateCheckFailed", failed.detail()));
            }
        }
    }

    /**
     * Opens the release in the system browser, falling back to the clipboard.
     *
     * <p>Mirrors {@code MainFrame.openWebPanel}: some headless-capable desktops and Linux
     * setups have no BROWSE action at all, and a URL on the clipboard is still strictly more
     * useful than a dead button.
     */
    private void openReleasesPage(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
            copyToClipboard(url);
            JOptionPane.showMessageDialog(this,
                    GuiLanguage.msg("gui.preferences.cannotOpenBrowser"),
                    GuiLanguage.msg("gui.preferences.updates"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            copyToClipboard(url);
        }
    }

    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
    }

    /**
     * Creates the system info section.
     */
    private JPanel createInfoSection() {
        JPanel body = Widgets.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(preferenceRow(GuiLanguage.msg("gui.preferences.javaVersion"), mutedValue(System.getProperty("java.version"))));
        body.add(Box.createVerticalStrut(Tokens.SPACE_XS));
        body.add(preferenceRow(GuiLanguage.msg("gui.preferences.javaVendor"), mutedValue(System.getProperty("java.vendor"))));
        body.add(Box.createVerticalStrut(Tokens.SPACE_XS));
        body.add(preferenceRow(GuiLanguage.msg("gui.preferences.os"),
                mutedValue(System.getProperty("os.name") + " " + System.getProperty("os.version"))));
        body.add(Box.createVerticalStrut(Tokens.SPACE_XS));
        body.add(preferenceRow(GuiLanguage.msg("gui.preferences.currentTheme"), mutedValue(ThemeManager.getCurrentTheme().getDisplayName())));
        body.add(Box.createVerticalStrut(Tokens.SPACE_XS));
        body.add(preferenceRow(GuiLanguage.msg("gui.preferences.flatlaf"), mutedValue("3.7")));

        String title = GuiLanguage.msg("gui.preferences.systemInfo");
        JPanel card = Widgets.titledCard(title, body);
        sections.add(new Section(title, card));
        return card;
    }

    private JLabel mutedValue(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Tokens.fontBody());
        label.setForeground(Tokens.textMuted());
        return label;
    }

    /**
     * Gets the config file path.
     */
    private String getConfigPath() {
        File configFile = new File("config.txt");
        try {
            return configFile.getCanonicalPath();
        } catch (IOException e) {
            return configFile.getAbsolutePath();
        }
    }

    /**
     * Opens the config folder in the system file manager.
     */
    private void openConfigFolder() {
        try {
            File configFile = new File("config.txt");
            File folder = configFile.getParentFile();
            if (folder == null) {
                folder = new File(".");
            }
            Desktop.getDesktop().open(folder.getCanonicalFile());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this,
                GuiLanguage.msg("gui.preferences.cannotOpenFolder", e.getMessage()),
                GuiLanguage.msg("gui.dialog.error"),
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Opens the config file in the default text editor.
     */
    private void openConfigFile() {
        try {
            File configFile = new File("config.txt");
            if (configFile.exists()) {
                Desktop.getDesktop().edit(configFile);
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    GuiLanguage.msg("gui.preferences.configNotFound", configFile.getAbsolutePath()),
                    GuiLanguage.msg("gui.dialog.notFound"),
                    JOptionPane.WARNING_MESSAGE
                );
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this,
                GuiLanguage.msg("gui.preferences.cannotOpenFile", e.getMessage()),
                GuiLanguage.msg("gui.dialog.error"),
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
