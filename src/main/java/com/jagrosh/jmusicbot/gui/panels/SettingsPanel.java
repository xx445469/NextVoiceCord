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

import com.jagrosh.jmusicbot.gui.components.Widgets;
import com.jagrosh.jmusicbot.gui.theme.ThemeManager;
import com.jagrosh.jmusicbot.gui.theme.Tokens;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * Settings panel for configuring GUI appearance and viewing configuration.
 *
 * @author Arif Banai (arif-banai)
 */
public class SettingsPanel extends JPanel {

    public SettingsPanel() {
        setLayout(new BorderLayout(0, Tokens.SPACE_MD));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildScrollArea(), BorderLayout.CENTER);
    }

    private Component buildHeader() {
        JPanel header = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_XS));
        header.add(Widgets.pageTitle("Preferences"), BorderLayout.NORTH);
        header.add(Widgets.muted("How this window looks — the bot itself is unaffected"), BorderLayout.SOUTH);
        return header;
    }

    private Component buildScrollArea() {
        JPanel content = Widgets.transparent(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(createAppearanceSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createConfigSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(createInfoSection());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scrollPane;
    }

    /** One preference: a label on the left, its control on the right. */
    private JPanel preferenceRow(String label, JComponent control) {
        JPanel row = Widgets.transparent(new BorderLayout(Tokens.SPACE_MD, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel l = new JLabel(label);
        l.setFont(Tokens.fontBody());
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
    private JPanel createAppearanceSection() {
        JPanel body = Widgets.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JComboBox<ThemeManager.Theme> themeBox = new JComboBox<>(ThemeManager.Theme.values());
        themeBox.setFont(Tokens.fontBody());
        themeBox.setSelectedItem(ThemeManager.getCurrentTheme());
        themeBox.addActionListener(e -> {
            ThemeManager.Theme selected = (ThemeManager.Theme) themeBox.getSelectedItem();
            if (selected != null) {
                ThemeManager.setTheme(selected);
            }
        });

        SpinnerNumberModel fontModel = new SpinnerNumberModel(
            ThemeManager.getBaseFontSize(), 8, 24, 1
        );
        JSpinner fontSpinner = new JSpinner(fontModel);
        fontSpinner.setFont(Tokens.fontBody());
        fontSpinner.addChangeListener(e -> {
            int size = (Integer) fontSpinner.getValue();
            ThemeManager.setBaseFontSize(size);
        });

        body.add(preferenceRow("Theme", themeBox));
        body.add(Box.createVerticalStrut(Tokens.SPACE_SM));
        body.add(preferenceRow("Font size", fontSpinner));
        body.add(Box.createVerticalStrut(Tokens.SPACE_SM));

        JLabel note = Widgets.muted("Applied immediately. Not saved to config.");
        note.setAlignmentX(LEFT_ALIGNMENT);
        body.add(note);

        return Widgets.titledCard("Appearance", body);
    }

    /**
     * Creates the configuration info section.
     */
    private JPanel createConfigSection() {
        JPanel body = Widgets.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel buttonPanel = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_SM, 0));
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);

        JButton openFolderButton = new JButton("Open config folder");
        openFolderButton.setFont(Tokens.fontBody());
        openFolderButton.addActionListener(e -> openConfigFolder());
        buttonPanel.add(openFolderButton);

        JButton openFileButton = new JButton("Open config.txt");
        openFileButton.setFont(Tokens.fontBody());
        openFileButton.addActionListener(e -> openConfigFile());
        buttonPanel.add(openFileButton);

        body.add(buttonPanel);
        body.add(Box.createVerticalStrut(Tokens.SPACE_SM));

        JLabel pathLabel = Widgets.muted("Location: " + getConfigPath());
        pathLabel.setAlignmentX(LEFT_ALIGNMENT);
        body.add(pathLabel);

        return Widgets.titledCard("Configuration", body);
    }

    /**
     * Creates the system info section.
     */
    private JPanel createInfoSection() {
        JPanel body = Widgets.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(preferenceRow("Java version", mutedValue(System.getProperty("java.version"))));
        body.add(Box.createVerticalStrut(Tokens.SPACE_XS));
        body.add(preferenceRow("Java vendor", mutedValue(System.getProperty("java.vendor"))));
        body.add(Box.createVerticalStrut(Tokens.SPACE_XS));
        body.add(preferenceRow("Operating system",
                mutedValue(System.getProperty("os.name") + " " + System.getProperty("os.version"))));
        body.add(Box.createVerticalStrut(Tokens.SPACE_XS));
        body.add(preferenceRow("Current theme", mutedValue(ThemeManager.getCurrentTheme().getDisplayName())));
        body.add(Box.createVerticalStrut(Tokens.SPACE_XS));
        body.add(preferenceRow("FlatLaf", mutedValue("3.7")));

        return Widgets.titledCard("System information", body);
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
                "Could not open config folder: " + e.getMessage(),
                "Error",
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
                    "Config file not found: " + configFile.getAbsolutePath(),
                    "File Not Found",
                    JOptionPane.WARNING_MESSAGE
                );
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this,
                "Could not open config file: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
