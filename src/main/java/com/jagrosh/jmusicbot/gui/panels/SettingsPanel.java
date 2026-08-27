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

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
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
        content.add(Box.createVerticalStrut(Tokens.SPACE_SM));
        content.add(createConfigSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_SM));
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
     * {@link Widgets#titledCard(String, Component)}, tightened for this page's own density
     * budget — the same reasoning, and the same values, as {@code ConfigPanel}'s own copy of
     * this helper.
     */
    private JPanel titledCard(String title, Component body) {
        return Widgets.titledCard(title, body, Tokens.SPACE_MD, Tokens.SPACE_SM);
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
        // Packs the theme and font-size rows two-to-a-line once the card is wide enough — see
        // Widgets.FormGrid — rather than always stacking every row, one per line.
        Widgets.FormGrid body = new Widgets.FormGrid();

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

        body.addField(preferenceRow(GuiLanguage.msg("gui.language.label"), buildLanguageBox()));
        // The distinction is worth stating outright: someone changing this expects it to
        // affect what the bot says in Discord, and it does not.
        body.addFull(Widgets.hint(GuiLanguage.msg("gui.language.hint")));
        body.addField(preferenceRow(GuiLanguage.msg("gui.appearance.theme"), themeControl));
        body.addField(preferenceRow(GuiLanguage.msg("gui.appearance.fontSize"), fontSpinner));
        body.addFull(Widgets.hint(GuiLanguage.msg("gui.appearance.savedNote")));

        String title = GuiLanguage.msg("gui.section.appearance");
        JPanel card = titledCard(title, body);
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
        JPanel card = titledCard(title, body);
        sections.add(new Section(title, card));
        return card;
    }

    /**
     * Creates the system info section.
     */
    private JPanel createInfoSection() {
        Widgets.FormGrid body = new Widgets.FormGrid();

        body.addField(preferenceRow(GuiLanguage.msg("gui.preferences.javaVersion"), mutedValue(System.getProperty("java.version"))));
        body.addField(preferenceRow(GuiLanguage.msg("gui.preferences.javaVendor"), mutedValue(System.getProperty("java.vendor"))));
        body.addField(preferenceRow(GuiLanguage.msg("gui.preferences.os"),
                mutedValue(System.getProperty("os.name") + " " + System.getProperty("os.version"))));
        body.addField(preferenceRow(GuiLanguage.msg("gui.preferences.currentTheme"), mutedValue(ThemeManager.getCurrentTheme().getDisplayName())));
        body.addField(preferenceRow(GuiLanguage.msg("gui.preferences.flatlaf"), mutedValue("3.7")));

        String title = GuiLanguage.msg("gui.preferences.systemInfo");
        JPanel card = titledCard(title, body);
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
