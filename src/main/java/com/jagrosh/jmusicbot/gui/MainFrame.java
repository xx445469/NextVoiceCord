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
package com.jagrosh.jmusicbot.gui;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.GCMonitor;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor;
import com.jagrosh.jmusicbot.gui.components.Sidebar;
import com.jagrosh.jmusicbot.gui.panels.DashboardPanel;
import com.jagrosh.jmusicbot.gui.theme.Tokens;
import java.awt.CardLayout;
import com.jagrosh.jmusicbot.gui.components.IconFactory;
import com.jagrosh.jmusicbot.gui.components.StatusBar;
import com.jagrosh.jmusicbot.gui.model.BotStatusData;
import com.jagrosh.jmusicbot.gui.panels.ConfigPanel;
import com.jagrosh.jmusicbot.gui.panels.PerformancePanel;
import com.jagrosh.jmusicbot.gui.panels.SettingsPanel;
import com.jagrosh.jmusicbot.gui.panels.SourceHealthPanel;
import com.jagrosh.jmusicbot.gui.panels.StatusPanel;
import com.jagrosh.jmusicbot.gui.panels.SystemHealthPanel;
import com.jagrosh.jmusicbot.gui.theme.ThemeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;

/**
 * Main application frame for JMusicBot with modern FlatLaf styling.
 * Provides a tabbed interface with console, status, and settings panels.
 *
 * @author Arif Banai (arif-banai)
 */
public class MainFrame extends JFrame {
    
    private static final Logger LOG = LoggerFactory.getLogger(MainFrame.class);
    private static final String TITLE = "NextVoiceCord";
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    
    private final Bot bot;
    private final Sidebar sidebar;
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel content = new JPanel(contentLayout);
    private final DashboardPanel dashboardPanel;
    /** Which view is showing, so only its metrics are refreshed. */
    private String currentView = "dashboard";
    private final ConsolePanel consolePanel;
    private final StatusPanel statusPanel;
    private final PerformancePanel performancePanel;
    private final SystemHealthPanel systemHealthPanel;
    private final SourceHealthPanel sourceHealthPanel;
    private final SettingsPanel settingsPanel;
    private final ConfigPanel configPanel;
    private final StatusBar statusBar;
    private final Instant startTime;
    private Timer statusUpdateTimer;
    
    /**
     * Creates the main application frame.
     *
     * @param bot the bot instance
     */
    public MainFrame(Bot bot) {
        super(TITLE);

        // Initialised before any panel is built, so the first labels are already in the
        // right language rather than being written in English and corrected a moment later.
        GuiLanguage.initialise(bot.getLanguages(), bot.getConfig().getGuiLanguage());

        // Without this the window shows the generic Java coffee cup in the taskbar,
        // the dock and the Alt-Tab switcher — the three places a user actually looks
        // for a running application.
        setIconImages(IconFactory.getWindowIcons());
        this.bot = bot;
        this.startTime = Instant.now();
        
        // Initialize panels
        this.consolePanel = new ConsolePanel();
        this.statusPanel = new StatusPanel(bot);
        this.performancePanel = new PerformancePanel(bot);
        this.systemHealthPanel = new SystemHealthPanel();
        this.sourceHealthPanel = new SourceHealthPanel(bot.getTrackLoadingMonitor());
        this.settingsPanel = new SettingsPanel();
        this.configPanel = new ConfigPanel(bot);
        this.statusBar = new StatusBar();
        this.dashboardPanel = new DashboardPanel(bot);
        this.sidebar = new Sidebar(this::showView);
        
        initializeFrame();
    }
    
    /**
     * Initializes the frame layout and components.
     */
    private void initializeFrame() {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        // Raised from 600x400: at that size the tab strip wraps to two rows and the
        // performance tables have no usable width.
        setMinimumSize(new Dimension(820, 560));
        setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        
        // Create menu bar
        setJMenuBar(createMenuBar());
        
        // Setup tabbed pane with icons
        
        // Main layout
        setupSidebar();

        content.setOpaque(true);
        content.setBackground(Tokens.surface());

        JPanel mainPanel = new JPanel(new BorderLayout());
        // No outer margin: the sidebar runs to the window edge, which is what makes it read
        // as part of the frame rather than a floating panel.
        mainPanel.add(sidebar, BorderLayout.WEST);
        mainPanel.add(content, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);
        
        setContentPane(mainPanel);
        
        // Window close handler
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClosing();
            }
        });
    }
    
    /**
     * Initializes and shows the frame.
     */
    public void init() {
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        
        // Start status bar update timer
        startStatusBarUpdates();
        
        // Start GC monitoring for performance visualization
        GCMonitor.getInstance().start();
        
        // Start system health monitoring (CPU, memory, threads)
        SystemHealthMonitor.getInstance().start();
        
        LOG.info("NextVoiceCord GUI initialized");
    }
    
    /**
     * Starts a timer to periodically update status from bot's JDA.
     * Fetches data once and distributes to both StatusBar and StatusPanel.
     */
    private void startStatusBarUpdates() {
        statusUpdateTimer = new Timer(2000, e -> updateAllStatusDisplays());
        statusUpdateTimer.setInitialDelay(500);
        statusUpdateTimer.start();
    }
    
    /**
     * Fetches status data once and updates both StatusBar and StatusPanel.
     * Single source of truth for status information.
     */
    private void updateAllStatusDisplays() {
        // Fetch data once (includes uptime calculation)
        BotStatusData statusData = BotStatusData.fromBot(bot, startTime);
        
        // Distribute to components
        statusBar.updateStatus(statusData, statusData.uptimeString());
        statusPanel.updateStatus(statusData);
        
        // Only the visible view is refreshed. Polling metrics for panels nobody is looking
        // at is work done to be thrown away, and these are the expensive ones.
        switch (currentView) {
            case "performance" -> performancePanel.updateMetrics();
            case "system" -> systemHealthPanel.updateMetrics();
            case "sources" -> sourceHealthPanel.refreshMetrics();
            default -> { }
        }
    }
    
    /**
     * Creates the menu bar with File, View, and Help menus.
     */
    /**
     * Builds the navigation column.
     *
     * <p>Grouped rather than listed flat: the first group is what you watch, the second what
     * you configure. Seven tabs in a row could not express that difference; a column can.
     */
    private void setupSidebar() {
        register("dashboard", dashboardPanel);
        register("console", consolePanel);
        register("status", statusPanel);
        register("performance", performancePanel);
        register("system", systemHealthPanel);
        register("sources", sourceHealthPanel);
        register("settings", settingsPanel);
        register("config", configPanel);

        sidebar.addItem("dashboard", GuiLanguage.msg("gui.nav.overview"), IconFactory.getIcon(IconFactory.IconType.STATUS, 16));
        sidebar.addItem("console", GuiLanguage.msg("gui.nav.console"), IconFactory.getIcon(IconFactory.IconType.CONSOLE, 16));
        sidebar.addItem("status", GuiLanguage.msg("gui.nav.servers"), IconFactory.getIcon(IconFactory.IconType.CONNECTED, 16));

        sidebar.addSection(GuiLanguage.msg("gui.section.diagnostics"));
        sidebar.addItem("performance", GuiLanguage.msg("gui.nav.performance"), IconFactory.getIcon(IconFactory.IconType.PLAY, 16));
        sidebar.addItem("system", GuiLanguage.msg("gui.nav.system"), IconFactory.getIcon(IconFactory.IconType.REFRESH, 16));
        sidebar.addItem("sources", GuiLanguage.msg("gui.nav.sources"), IconFactory.getIcon(IconFactory.IconType.SEARCH, 16));

        // Offered only when --web is actually serving. A permanently visible button that
        // sometimes does nothing is worse than no button.
        if (bot.getWebPanel() != null && bot.getWebPanel().getUrl().isPresent())
        {
            sidebar.addSection(GuiLanguage.msg("gui.section.webPanel"));
            sidebar.addAction(GuiLanguage.msg("gui.action.openInBrowser"),
                              IconFactory.getIcon(IconFactory.IconType.SEARCH, 16),
                              this::openWebPanel);
        }

        sidebar.addSpacer();
        sidebar.addSection(GuiLanguage.msg("gui.section.configure"));
        sidebar.addItem("settings", GuiLanguage.msg("gui.nav.preferences"), IconFactory.getIcon(IconFactory.IconType.SETTINGS, 16));
        sidebar.addItem("config", GuiLanguage.msg("gui.nav.botConfig"), IconFactory.getIcon(IconFactory.IconType.COPY, 16));
    }

    /**
     * Opens the web panel in the default browser.
     *
     * <p>The URL carries the token, which is the point: the alternative is asking someone to
     * copy it out of a console that has long since scrolled past it.
     */
    private void openWebPanel() {
        bot.getWebPanel().getUrl().ifPresent(url -> {
            try {
                if (java.awt.Desktop.isDesktopSupported()
                        && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                    return;
                }
                // Headless-capable desktops and some Linux setups have no BROWSE action.
                // Putting the URL on the clipboard still saves the retyping.
                copyToClipboard(url);
                JOptionPane.showMessageDialog(this,
                        GuiLanguage.msg("gui.webPanel.cannotOpen"),
                        GuiLanguage.msg("gui.webPanel.title"), JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                LOG.warn("Could not open the web panel: {}", ex.toString());
                copyToClipboard(url);
            }
        });
    }

    private void copyToClipboard(String text) {
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
    }

    private void register(String key, java.awt.Component view) {
        content.add(view, key);
    }

    /**
     * Switches the visible view.
     *
     * <p>The dashboard polls once a second, so it is stopped whenever something else is
     * showing — a hidden view that keeps working is a cost paid for nothing.
     */
    private void showView(String key) {
        currentView = key;
        contentLayout.show(content, key);

        if ("dashboard".equals(key)) {
            dashboardPanel.start();
        } else {
            dashboardPanel.stop();
        }
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        // File menu
        JMenu fileMenu = new JMenu(GuiLanguage.msg("gui.main.file"));
        fileMenu.setMnemonic('F');

        JMenuItem exitItem = new JMenuItem(GuiLanguage.msg("gui.main.exit"));
        exitItem.setMnemonic('x');
        exitItem.addActionListener(e -> handleWindowClosing());
        fileMenu.add(exitItem);

        // View menu
        JMenu viewMenu = new JMenu(GuiLanguage.msg("gui.main.view"));
        viewMenu.setMnemonic('V');

        // Theme submenu
        JMenu themeMenu = new JMenu(GuiLanguage.msg("gui.main.theme"));
        ButtonGroup themeGroup = new ButtonGroup();

        for (ThemeManager.Theme theme : ThemeManager.getAvailableThemes()) {
            JRadioButtonMenuItem themeItem = new JRadioButtonMenuItem(theme.getDisplayName());
            themeItem.setSelected(theme == ThemeManager.getCurrentTheme());
            themeItem.addActionListener(e -> ThemeManager.setTheme(theme));
            themeGroup.add(themeItem);
            themeMenu.add(themeItem);
        }
        viewMenu.add(themeMenu);

        viewMenu.addSeparator();

        // Tab navigation
        JMenuItem consoleTab = new JMenuItem(GuiLanguage.msg("gui.main.console"));
        consoleTab.addActionListener(e -> sidebar.select("console"));
        viewMenu.add(consoleTab);

        JMenuItem statusTab = new JMenuItem(GuiLanguage.msg("gui.main.status"));
        statusTab.addActionListener(e -> sidebar.select("status"));
        viewMenu.add(statusTab);

        JMenuItem performanceTab = new JMenuItem(GuiLanguage.msg("gui.main.performance"));
        performanceTab.addActionListener(e -> sidebar.select("performance"));
        viewMenu.add(performanceTab);

        JMenuItem systemTab = new JMenuItem(GuiLanguage.msg("gui.main.systemHealth"));
        systemTab.addActionListener(e -> sidebar.select("system"));
        viewMenu.add(systemTab);

        JMenuItem sourcesTab = new JMenuItem(GuiLanguage.msg("gui.main.sources"));
        sourcesTab.addActionListener(e -> sidebar.select("sources"));
        viewMenu.add(sourcesTab);

        JMenuItem settingsTab = new JMenuItem(GuiLanguage.msg("gui.main.settings"));
        settingsTab.addActionListener(e -> sidebar.select("settings"));
        viewMenu.add(settingsTab);

        JMenuItem configTab = new JMenuItem(GuiLanguage.msg("gui.main.config"));
        configTab.addActionListener(e -> sidebar.select("config"));
        viewMenu.add(configTab);

        // Help menu
        JMenu helpMenu = new JMenu(GuiLanguage.msg("gui.main.help"));
        helpMenu.setMnemonic('H');

        JMenuItem aboutItem = new JMenuItem(GuiLanguage.msg("gui.main.about"));
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);
        
        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);
        
        return menuBar;
    }
    
    /**
     * Sets up the tabbed pane with all panels.
     */
    
    /**
     * Handles window closing - shuts down the bot gracefully.
     */
    private void handleWindowClosing() {
        int result = JOptionPane.showConfirmDialog(
            this,
            GuiLanguage.msg("gui.main.confirmExitMessage"),
            GuiLanguage.msg("gui.main.confirmExitTitle"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            LOG.info("User requested shutdown");
            try {
                bot.shutdown();
            } catch (Exception ex) {
                LOG.error("Error during shutdown", ex);
            }
            dispose();
            System.exit(0);
        }
    }
    
    /**
     * Shows the About dialog.
     */
    private void showAboutDialog() {
        String message = GuiLanguage.msg("gui.main.aboutMessage",
                ThemeManager.getCurrentTheme().getDisplayName(),
                System.getProperty("java.version"));

        JOptionPane.showMessageDialog(
            this,
            message,
            GuiLanguage.msg("gui.main.aboutTitle", TITLE),
            JOptionPane.INFORMATION_MESSAGE
        );
    }
    
    
    /**
     * Gets the status panel for external updates.
     */
    public StatusPanel getStatusPanel() {
        return statusPanel;
    }
    
    /**
     * Gets the console panel.
     */
    public ConsolePanel getConsolePanel() {
        return consolePanel;
    }
}
