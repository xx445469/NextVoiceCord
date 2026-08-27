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
package com.jagrosh.jmusicbot.gui.panels;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.gui.GuiLanguage;
import com.jagrosh.jmusicbot.gui.components.Widgets;
import com.jagrosh.jmusicbot.gui.theme.Tokens;
import com.jagrosh.jmusicbot.update.SelfUpdater;
import com.jagrosh.jmusicbot.utils.OtherUtil;

import javax.swing.*;
import java.awt.*;
import java.net.URI;

/**
 * Its own top-level page, not a card tucked inside {@link SettingsPanel}: checking is now
 * unconditional (see {@link SelfUpdater}'s class javadoc) and this page is also where the one
 * genuinely consequential action lives — installing a downloaded release restarts the bot and
 * drops every voice connection instantly, which deserves a page of its own rather than a corner
 * of Preferences.
 *
 * <p>Two independent things live here, matching {@link SelfUpdater}'s own split:
 * <ul>
 *   <li>"Check for updates" asks GitHub what the latest release is and, if it is newer than
 *       this one, downloads it — see {@link SelfUpdater#checkAndStage()}. It never installs
 *       anything, but a successful check does enable the button below: telling someone about an
 *       update they have no way to act on is worse than not telling them at all.</li>
 *   <li>"Install and restart" acts on whatever the check above (or {@link SelfUpdater}'s own
 *       hourly background check) has already staged. Pressing it never installs silently: {@link
 *       SelfUpdater#decideInstall(boolean)} is asked first, and if anything is currently
 *       playing, that is said plainly, with the choice left to whoever pressed the button —
 *       never refused outright, never done without warning.</li>
 * </ul>
 *
 * @author adan (xx445469)
 */
public class UpdatesPanel extends JPanel
{
    private final Bot bot;

    private final JLabel stagedLabel = new JLabel(" ");
    private final JButton installButton;
    private final JLabel installResultLabel = new JLabel(" ");

    public UpdatesPanel(Bot bot)
    {
        this.bot = bot;

        setLayout(new BorderLayout(0, Tokens.SPACE_MD));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG));

        installButton = Widgets.primaryButton(GuiLanguage.msg("gui.updates.installButton"));
        installButton.addActionListener(e -> onInstallPressed());
        installResultLabel.setFont(Tokens.fontBody());
        installResultLabel.setAlignmentX(LEFT_ALIGNMENT);
        stagedLabel.setFont(Tokens.fontBody());
        stagedLabel.setForeground(Tokens.textMuted());
        stagedLabel.setAlignmentX(LEFT_ALIGNMENT);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildScrollArea(), BorderLayout.CENTER);

        refreshStagedStatus();
    }

    private Component buildHeader()
    {
        JPanel header = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_XS));
        header.add(Widgets.pageTitle(GuiLanguage.msg("gui.nav.updates")), BorderLayout.NORTH);
        header.add(Widgets.muted(GuiLanguage.msg("gui.updates.subtitle")), BorderLayout.SOUTH);
        return header;
    }

    private Component buildScrollArea()
    {
        JPanel content = Widgets.transparent(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(createUpdatesSection());
        return Widgets.scrollable(content);
    }

    private JPanel preferenceRow(String label, JComponent control)
    {
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

    private JLabel mutedValue(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(Tokens.fontBody());
        label.setForeground(Tokens.textMuted());
        return label;
    }

    // ==================== Card ====================

    /**
     * One card holding both halves described in the class javadoc: an on-demand check that
     * never installs anything, and the install action that only ever acts on what the
     * background check already staged.
     */
    private JPanel createUpdatesSection()
    {
        JPanel body = Widgets.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(preferenceRow(GuiLanguage.msg("gui.preferences.currentVersion"),
                mutedValue(OtherUtil.getCurrentVersion())));
        body.add(Box.createVerticalStrut(Tokens.SPACE_SM));

        JButton checkButton = Widgets.primaryButton(GuiLanguage.msg("gui.preferences.checkForUpdates"));

        // Hidden until there is somewhere for it to go, rather than disabled, so its
        // appearance itself signals "an update was found" without needing to read the label.
        JButton releasesButton = Widgets.secondaryButton(GuiLanguage.msg("gui.action.openInBrowser"));
        releasesButton.setVisible(false);

        JLabel checkStatusLabel = new JLabel(" ");
        checkStatusLabel.setFont(Tokens.fontBody());
        checkStatusLabel.setAlignmentX(LEFT_ALIGNMENT);

        checkButton.addActionListener(e -> checkForUpdates(checkButton, releasesButton, checkStatusLabel));

        JPanel checkButtonPanel = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_SM, 0));
        checkButtonPanel.setAlignmentX(LEFT_ALIGNMENT);
        checkButtonPanel.add(checkButton);
        checkButtonPanel.add(releasesButton);
        body.add(checkButtonPanel);
        body.add(Box.createVerticalStrut(Tokens.SPACE_SM));
        body.add(checkStatusLabel);

        body.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        body.add(stagedLabel);
        body.add(Box.createVerticalStrut(Tokens.SPACE_SM));

        JPanel installButtonPanel = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_SM, 0));
        installButtonPanel.setAlignmentX(LEFT_ALIGNMENT);
        installButtonPanel.add(installButton);
        body.add(installButtonPanel);
        body.add(Box.createVerticalStrut(Tokens.SPACE_SM));
        body.add(installResultLabel);

        // No card heading: this page holds exactly one card and the page title above it already
        // reads "Updates". Repeating it put the same word twice within a few pixels, which reads
        // as a rendering mistake rather than as structure.
        Widgets.Card card = new Widgets.Card();
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_MD, Tokens.SPACE_MD, Tokens.SPACE_MD, Tokens.SPACE_MD));
        card.add(body, BorderLayout.CENTER);

        // This page has only ever the one card, unlike SettingsPanel or ConfigPanel further
        // down the sidebar. With nothing else in the scroll area to take up a tall window's
        // spare height, a Widgets.Card's unbounded default maximum size lets BoxLayout stretch
        // it — and, through the FlowLayout button rows inside it, its content — to fill that
        // space, opening a dead gap between the check-for-updates status and the staged-version
        // line below it. Capping the card at its own preferred height keeps it the size its
        // content actually needs regardless of how tall the window is.
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    // ==================== On-demand check (never installs) ====================

    /**
     * Runs the check — and, when one is found, the download — off the event thread.
     *
     * <p>{@link SelfUpdater#checkAndStage()} makes an HTTP call to the GitHub API and, if a
     * newer release exists, follows it with a ~68 MB download; doing either on the EDT would
     * freeze the whole window for however long that takes, and the download alone is not
     * instant. A {@link Timer} polls {@link SelfUpdater#isDownloading()} while the
     * {@link SwingWorker} below runs, so the status line can say "downloading" once the transfer
     * genuinely starts rather than leaving "checking" on screen — and looking stuck — for
     * however long a ~68 MB transfer takes.
     */
    private void checkForUpdates(JButton checkButton, JButton releasesButton, JLabel statusLabel)
    {
        SelfUpdater upd = updater();
        if (upd == null)
        {
            // Before Discord has connected there is no SelfUpdater to check with yet — see
            // Bot.setJDA(). Said plainly rather than silently doing nothing, so a press this
            // early does not look like the button is broken.
            statusLabel.setForeground(Tokens.danger());
            statusLabel.setText(GuiLanguage.msg("gui.updates.notConnected"));
            return;
        }

        checkButton.setEnabled(false);
        releasesButton.setVisible(false);
        statusLabel.setForeground(Tokens.textMuted());
        statusLabel.setText(GuiLanguage.msg("gui.preferences.checkingForUpdates"));

        Timer downloadPoll = new Timer(200, e -> {
            if (upd.isDownloading())
            {
                statusLabel.setText(GuiLanguage.msg("gui.updates.downloading"));
            }
        });
        downloadPoll.start();

        new SwingWorker<SelfUpdater.CheckAndStageOutcome, Void>()
        {
            @Override
            protected SelfUpdater.CheckAndStageOutcome doInBackground()
            {
                return upd.checkAndStage();
            }

            @Override
            protected void done()
            {
                downloadPoll.stop();
                checkButton.setEnabled(true);
                try
                {
                    presentOutcome(get(), releasesButton, statusLabel);
                }
                catch (Exception ex)
                {
                    statusLabel.setForeground(Tokens.danger());
                    statusLabel.setText(GuiLanguage.msg("gui.preferences.updateCheckFailed", ex.getMessage()));
                }
                // Whatever just happened (staged, failed, already up to date), the install
                // button's own state is derived from SelfUpdater fresh rather than assumed.
                refreshStagedStatus();
            }
        }.execute();
    }

    /** Renders whichever of the four outcomes the check-and-stage run produced. */
    private void presentOutcome(SelfUpdater.CheckAndStageOutcome outcome, JButton releasesButton, JLabel statusLabel)
    {
        switch (outcome)
        {
            case SelfUpdater.CheckAndStageOutcome.UpToDate upToDate ->
            {
                statusLabel.setForeground(Tokens.success());
                statusLabel.setText(GuiLanguage.msg("gui.preferences.updateUpToDate", upToDate.currentVersion()));
            }
            case SelfUpdater.CheckAndStageOutcome.Staged staged ->
            {
                statusLabel.setForeground(Tokens.success());
                statusLabel.setText(GuiLanguage.msg("gui.updates.stagedVersion", staged.version()));
                showReleasesLink(releasesButton, staged.releasesUrl());
            }
            case SelfUpdater.CheckAndStageOutcome.DownloadFailed downloadFailed ->
            {
                statusLabel.setForeground(Tokens.danger());
                statusLabel.setText(GuiLanguage.msg("gui.updates.downloadFailed", downloadFailed.version()));
                showReleasesLink(releasesButton, downloadFailed.releasesUrl());
            }
            case SelfUpdater.CheckAndStageOutcome.CheckFailed failed ->
            {
                statusLabel.setForeground(Tokens.danger());
                statusLabel.setText(GuiLanguage.msg("gui.preferences.updateCheckFailed", failed.detail()));
            }
        }
    }

    /** Shows the "Open in browser" button pointed at {@code url}, if there is one to show. */
    private void showReleasesLink(JButton releasesButton, String url)
    {
        if (url == null || url.isBlank())
        {
            return;
        }
        for (var listener : releasesButton.getActionListeners())
        {
            releasesButton.removeActionListener(listener);
        }
        releasesButton.addActionListener(e -> openReleasesPage(url));
        releasesButton.setVisible(true);
    }

    /**
     * Opens the release in the system browser, falling back to the clipboard.
     *
     * <p>Mirrors {@code MainFrame.openWebPanel}: some headless-capable desktops and Linux
     * setups have no BROWSE action at all, and a URL on the clipboard is still strictly more
     * useful than a dead button.
     */
    private void openReleasesPage(String url)
    {
        try
        {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
            copyToClipboard(url);
            JOptionPane.showMessageDialog(this,
                    GuiLanguage.msg("gui.preferences.cannotOpenBrowser"),
                    GuiLanguage.msg("gui.preferences.updates"), JOptionPane.INFORMATION_MESSAGE);
        }
        catch (Exception ex)
        {
            copyToClipboard(url);
        }
    }

    private void copyToClipboard(String text)
    {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
    }

    // ==================== Staged status / install ====================

    /**
     * Refreshes what {@link #stagedLabel} says and whether {@link #installButton} is enabled,
     * from {@link SelfUpdater}'s own state. Cheap enough to call on the same timer {@link
     * com.jagrosh.jmusicbot.gui.MainFrame} already polls the rest of the window with — this
     * only reads a couple of volatile fields, never touches the network.
     */
    public void refreshStagedStatus()
    {
        SelfUpdater updater = updater();
        if (updater == null || updater.getStagedVersion().isEmpty())
        {
            stagedLabel.setText(GuiLanguage.msg("gui.updates.noStagedUpdate"));
            installButton.setEnabled(false);
            return;
        }

        stagedLabel.setText(GuiLanguage.msg("gui.updates.stagedVersion", updater.getStagedVersion().get()));
        installButton.setEnabled(true);
    }

    /**
     * Asks {@link SelfUpdater} what pressing the button should do, and acts on the answer — see
     * the class javadoc for why this never installs on the first call alone when something is
     * playing.
     */
    private void onInstallPressed()
    {
        SelfUpdater updater = updater();
        if (updater == null)
        {
            return;
        }
        actOnDecision(updater, updater.decideInstall(false));
    }

    private void actOnDecision(SelfUpdater updater, SelfUpdater.InstallDecision decision)
    {
        switch (decision)
        {
            case SelfUpdater.InstallDecision.NotStaged notStaged ->
            {
                installResultLabel.setForeground(Tokens.textMuted());
                installResultLabel.setText(GuiLanguage.msg("gui.updates.notStaged"));
                refreshStagedStatus();
            }
            case SelfUpdater.InstallDecision.Blocked blocked ->
            {
                String playing = String.join(", ", blocked.playingGuilds());
                int choice = JOptionPane.showConfirmDialog(this,
                        GuiLanguage.msg("gui.updates.confirmPlayingMessage", playing),
                        GuiLanguage.msg("gui.updates.confirmTitle"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.YES_OPTION)
                {
                    // Re-asked with force=true rather than installing straight from this
                    // branch: something could have started or stopped playing in the moment
                    // this dialog was open, and the fresh check is what keeps that honest.
                    actOnDecision(updater, updater.decideInstall(true));
                }
            }
            case SelfUpdater.InstallDecision.Ready ready ->
            {
                int choice = JOptionPane.showConfirmDialog(this,
                        GuiLanguage.msg("gui.updates.confirmIdleMessage", ready.version()),
                        GuiLanguage.msg("gui.updates.confirmTitle"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.YES_OPTION)
                {
                    runInstall(updater);
                }
            }
        }
    }

    /**
     * Runs the actual swap-and-restart off the event thread. On success this call never
     * returns — the process that would return is the one it replaces — so {@code done()} below
     * only ever fires on the failure path {@link SelfUpdater#installNow()} already logs.
     */
    private void runInstall(SelfUpdater updater)
    {
        installButton.setEnabled(false);
        installResultLabel.setForeground(Tokens.textMuted());
        installResultLabel.setText(GuiLanguage.msg("gui.updates.installing"));

        new SwingWorker<Void, Void>()
        {
            @Override
            protected Void doInBackground()
            {
                updater.installNow();
                return null;
            }

            @Override
            protected void done()
            {
                installResultLabel.setForeground(Tokens.danger());
                installResultLabel.setText(GuiLanguage.msg("gui.updates.installFailed"));
                refreshStagedStatus();
            }
        }.execute();
    }

    private SelfUpdater updater()
    {
        return bot == null ? null : bot.getUpdater();
    }
}
