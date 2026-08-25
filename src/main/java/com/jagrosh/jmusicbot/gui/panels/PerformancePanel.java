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
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.GCMonitor;
import com.jagrosh.jmusicbot.audio.PerformanceMetrics;
import com.jagrosh.jmusicbot.audio.PerformanceMetrics.*;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor.HealthSample;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor.HealthSnapshot;
import com.jagrosh.jmusicbot.gui.components.Widgets;
import com.jagrosh.jmusicbot.gui.theme.Tokens;
import net.dv8tion.jda.api.entities.Guild;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * Panel for visualizing audio processing performance.
 *
 * <p>Rebuilt on the shared card design system ({@link Widgets}, {@link Tokens}) so it reads as
 * part of the same window as the Overview tab rather than a separate diagnostics utility.
 *
 * @author Arif Banai (arif-banai)
 */
public class PerformancePanel extends JPanel {

    private final Bot bot;
    private final JComboBox<GuildItem> guildSelector;
    private final JComboBox<WindowOption> windowSelector;

    // Visualization panels
    private final EnhancedTimelinePanel enhancedTimeline;
    private final LatencyGraphPanel latencyGraph;
    private final SchedulerDriftPanel driftChart;

    // Guild overview table
    private final JTable guildOverviewTable;
    private final DefaultTableModel guildOverviewModel;

    // Health indicator
    private final Widgets.Badge healthBadge = new Widgets.Badge("NO DATA", Tokens.textMuted());

    // Stat tiles
    private final Widgets.StatTile qualityTile = new Widgets.StatTile("quality score");
    private final Widgets.StatTile durationTile = new Widgets.StatTile("duration");
    private final Widgets.StatTile framesTile = new Widgets.StatTile("frames");
    private final Widgets.StatTile missedTile = new Widgets.StatTile("missed frames");
    private final Widgets.StatTile missRateTile = new Widgets.StatTile("miss rate");
    private final Widgets.StatTile missRateWindowTile = new Widgets.StatTile("miss rate 10s / 60s");
    private final Widgets.StatTile latencyTile = new Widgets.StatTile("latency avg (p95)");
    private final Widgets.StatTile fpsTile = new Widgets.StatTile("frame rate");
    private final Widgets.StatTile stutterTile = new Widgets.StatTile("stutters");
    private final Widgets.StatTile stuckTile = new Widgets.StatTile("stuck events");
    private final Widgets.StatTile gcTile = new Widgets.StatTile("gc events");
    private final Widgets.StatTile ttffTile = new Widgets.StatTile("time to first frame");

    // Event table
    private final JTable eventTable;
    private final DefaultTableModel eventTableModel;

    private MetricsSnapshot currentSnapshot;
    private HealthSnapshot healthSnapshot;
    private int selectedWindowSeconds = 30;

    private enum WindowOption {
        SECONDS_10(10, "10 seconds"),
        SECONDS_30(30, "30 seconds"),
        MINUTE_1(60, "1 minute"),
        MINUTE_2(120, "2 minutes");

        private final int seconds;
        private final String display;

        WindowOption(int seconds, String display) {
            this.seconds = seconds;
            this.display = display;
        }

        public int getSeconds() { return seconds; }

        @Override
        public String toString() { return display; }
    }

    public PerformancePanel(Bot bot) {
        this.bot = bot;

        setLayout(new BorderLayout(0, Tokens.SPACE_MD));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG));

        guildSelector = new JComboBox<>();
        guildSelector.setFont(Tokens.fontBody());
        guildSelector.setPreferredSize(new Dimension(160, 26));
        guildSelector.addActionListener(e -> refreshMetrics());

        windowSelector = new JComboBox<>(WindowOption.values());
        windowSelector.setFont(Tokens.fontBody());
        windowSelector.setSelectedItem(WindowOption.SECONDS_30);
        windowSelector.addActionListener(e -> {
            WindowOption sel = (WindowOption) windowSelector.getSelectedItem();
            if (sel != null) {
                selectedWindowSeconds = sel.getSeconds();
                refreshMetrics();
            }
        });

        // === Guild overview table ===
        String[] overviewCols = {"Guild", "Status", "Quality", "Miss Rate", "Stutters", "Stuck", "TTFF"};
        guildOverviewModel = new DefaultTableModel(overviewCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        guildOverviewTable = new JTable(guildOverviewModel);
        styleTable(guildOverviewTable);
        guildOverviewTable.setFillsViewportHeight(true);
        guildOverviewTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        guildOverviewTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        guildOverviewTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        guildOverviewTable.getColumnModel().getColumn(3).setPreferredWidth(70);
        guildOverviewTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        guildOverviewTable.getColumnModel().getColumn(5).setPreferredWidth(50);
        guildOverviewTable.getColumnModel().getColumn(6).setPreferredWidth(55);

        // Select guild when row is clicked
        guildOverviewTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = guildOverviewTable.getSelectedRow();
                if (row >= 0 && row < guildSelector.getItemCount()) {
                    guildSelector.setSelectedIndex(row);
                }
            }
        });

        // === Graphs ===
        enhancedTimeline = new EnhancedTimelinePanel();
        latencyGraph = new LatencyGraphPanel();
        driftChart = new SchedulerDriftPanel();

        // === Events table ===
        String[] cols = {"Time", "Type", "Details", "Sev"};
        eventTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        eventTable = new JTable(eventTableModel);
        styleTable(eventTable);
        eventTable.setFillsViewportHeight(true);
        eventTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        eventTable.getColumnModel().getColumn(1).setPreferredWidth(50);
        eventTable.getColumnModel().getColumn(2).setPreferredWidth(220);
        eventTable.getColumnModel().getColumn(3).setPreferredWidth(45);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
    }

    private Component buildHeader() {
        JPanel header = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_XS));
        header.add(Widgets.pageTitle("Performance"), BorderLayout.NORTH);
        header.add(Widgets.muted("Per-guild audio pipeline diagnostics — frame timing, latency and stability"),
                BorderLayout.SOUTH);
        return header;
    }

    private Component buildBody() {
        JPanel content = Widgets.transparent(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(buildControlsCard());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(buildOverviewCard());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(buildStatsSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(buildTimelineCard());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));

        Widgets.Card latencyCard = Widgets.titledCard("Latency", latencyGraph);
        latencyCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        latencyCard.setPreferredSize(new Dimension(600, 220));
        content.add(latencyCard);
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));

        Widgets.Card driftCard = Widgets.titledCard("Scheduler Drift", driftChart);
        driftCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        driftCard.setPreferredSize(new Dimension(600, 200));
        content.add(driftCard);
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));

        content.add(buildEventsCard());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private Component buildControlsCard() {
        Widgets.Card card = new Widgets.Card();
        card.setLayout(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_SM, Tokens.SPACE_XS));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        card.add(Widgets.muted("Guild"));
        card.add(guildSelector);
        card.add(Box.createHorizontalStrut(Tokens.SPACE_SM));
        card.add(Widgets.muted("Window"));
        card.add(windowSelector);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setFont(Tokens.fontBody());
        refreshBtn.addActionListener(e -> refreshGuildList());
        card.add(refreshBtn);

        card.add(Box.createHorizontalStrut(Tokens.SPACE_MD));
        card.add(healthBadge);

        card.add(Box.createHorizontalStrut(Tokens.SPACE_MD));
        JButton exportJsonBtn = new JButton("Export JSON");
        exportJsonBtn.setFont(Tokens.fontBody());
        exportJsonBtn.setToolTipText("Export diagnostics to JSON file");
        exportJsonBtn.addActionListener(e -> exportToJson());
        card.add(exportJsonBtn);

        JButton copySummaryBtn = new JButton("Copy Summary");
        copySummaryBtn.setFont(Tokens.fontBody());
        copySummaryBtn.setToolTipText("Copy summary to clipboard for Discord");
        copySummaryBtn.addActionListener(e -> copyToClipboard());
        card.add(copySummaryBtn);

        return card;
    }

    private Component buildOverviewCard() {
        JScrollPane overviewScroll = new JScrollPane(guildOverviewTable);
        overviewScroll.setBorder(BorderFactory.createEmptyBorder());
        overviewScroll.setOpaque(false);
        overviewScroll.getViewport().setOpaque(false);
        overviewScroll.getVerticalScrollBar().setUnitIncrement(16);
        overviewScroll.setPreferredSize(new Dimension(600, 140));

        Widgets.Card card = Widgets.titledCard("Guild Overview (click a row to select)", overviewScroll);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        return card;
    }

    private Component buildStatsSection() {
        JPanel section = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_SM));

        JLabel heading = new JLabel("Statistics");
        heading.setFont(Tokens.fontHeading());
        heading.setForeground(Tokens.text());
        section.add(heading, BorderLayout.NORTH);

        JPanel grid = Widgets.transparent(new GridLayout(3, 4, Tokens.SPACE_SM, Tokens.SPACE_SM));
        grid.add(qualityTile);
        grid.add(durationTile);
        grid.add(framesTile);
        grid.add(missedTile);
        grid.add(missRateTile);
        grid.add(missRateWindowTile);
        grid.add(latencyTile);
        grid.add(fpsTile);
        grid.add(stutterTile);
        grid.add(stuckTile);
        grid.add(gcTile);
        grid.add(ttffTile);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));

        section.add(grid, BorderLayout.CENTER);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        return section;
    }

    private Component buildTimelineCard() {
        Widgets.Card card = new Widgets.Card();
        card.setLayout(new BorderLayout());
        card.add(enhancedTimeline, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        card.setPreferredSize(new Dimension(600, 220));
        return card;
    }

    private Component buildEventsCard() {
        JScrollPane tableScroll = new JScrollPane(eventTable);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableScroll.setOpaque(false);
        tableScroll.getViewport().setOpaque(false);
        tableScroll.getVerticalScrollBar().setUnitIncrement(16);
        tableScroll.setPreferredSize(new Dimension(600, 180));

        Widgets.Card card = Widgets.titledCard("Events", tableScroll);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
        return card;
    }

    private void styleTable(JTable table) {
        table.setShowGrid(false);
        table.setRowHeight(Tokens.SPACE_LG + 4);
        table.setBackground(Tokens.surfaceRaised());
        table.setForeground(Tokens.text());
        table.setFont(Tokens.fontBody());
        table.setSelectionBackground(Tokens.accent());
        table.setSelectionForeground(Color.WHITE);
        table.setBorder(BorderFactory.createEmptyBorder());
        table.setIntercellSpacing(new Dimension(0, 0));

        JTableHeader header = table.getTableHeader();
        header.setFont(Tokens.fontSmall());
        header.setBackground(Tokens.surfaceRaised());
        header.setForeground(Tokens.textMuted());
        header.setBorder(BorderFactory.createEmptyBorder());
    }

    public void refreshGuildList() {
        guildSelector.removeAllItems();
        guildOverviewModel.setRowCount(0);

        if (bot == null || bot.getJDA() == null) return;

        List<Guild> guilds = bot.getJDA().getGuilds();
        for (Guild g : guilds) {
            if (g.getAudioManager().isConnected()) {
                guildSelector.addItem(new GuildItem(g));

                // Add to overview table
                var handler = g.getAudioManager().getSendingHandler();
                if (handler instanceof AudioHandler ah) {
                    MetricsSnapshot snap = ah.getPerformanceMetrics().getSnapshot(selectedWindowSeconds);
                    String status = ah.getPlayer().getPlayingTrack() != null
                        ? (ah.getPlayer().isPaused() ? "Paused" : "Playing")
                        : "Idle";

                    guildOverviewModel.addRow(new Object[]{
                        g.getName(),
                        status,
                        snap.qualityScore() + "%",
                        String.format("%.2f%%", snap.missRatePercent()),
                        snap.stutterCount(),
                        snap.stuckCount(),
                        snap.timeToFirstFrameMs() > 0 ? snap.timeToFirstFrameMs() + "ms" : "-"
                    });
                }
            }
        }
        if (guildSelector.getItemCount() == 0) {
            guildSelector.addItem(new GuildItem(null));
        }
    }

    private void refreshMetrics() {
        // Always get health snapshot (system-wide, not guild-specific)
        healthSnapshot = SystemHealthMonitor.getInstance().getSnapshot(selectedWindowSeconds);

        GuildItem sel = (GuildItem) guildSelector.getSelectedItem();
        if (sel == null || sel.guild() == null) {
            currentSnapshot = null;
            enhancedTimeline.updateSnapshot(null, 0, selectedWindowSeconds);
            updateDisplay();
            return;
        }

        var handler = sel.guild().getAudioManager().getSendingHandler();
        if (!(handler instanceof AudioHandler ah)) {
            currentSnapshot = null;
            enhancedTimeline.updateSnapshot(null, 0, selectedWindowSeconds);
            updateDisplay();
            return;
        }

        currentSnapshot = ah.getPerformanceMetrics().getSnapshot(selectedWindowSeconds);
        enhancedTimeline.updateSnapshot(currentSnapshot, sel.guild().getIdLong(), selectedWindowSeconds);
        updateDisplay();
    }

    private void updateDisplay() {
        if (currentSnapshot == null) {
            healthBadge.set("NO DATA", Tokens.textMuted());
            qualityTile.setValue("—");
            qualityTile.setValueColor(Tokens.text());
            durationTile.setValue("—");
            framesTile.setValue("—");
            missedTile.setValue("—");
            missedTile.setValueColor(Tokens.text());
            missRateTile.setValue("—");
            missRateWindowTile.setValue("—");
            latencyTile.setValue("—");
            fpsTile.setValue("—");
            stutterTile.setValue("—");
            stutterTile.setValueColor(Tokens.text());
            stuckTile.setValue("—");
            stuckTile.setValueColor(Tokens.text());
            gcTile.setValue("—");
            ttffTile.setValue("—");
            ttffTile.setValueColor(Tokens.text());
            eventTableModel.setRowCount(0);
            repaintGraphs();
            return;
        }

        HealthStatus health = currentSnapshot.healthStatus();
        healthBadge.set(health.name(), colorFor(health));

        int score = currentSnapshot.qualityScore();
        qualityTile.setValue(score + "%");
        qualityTile.setValueColor(score >= 90 ? Tokens.success() : score >= 70 ? Tokens.warning() : Tokens.danger());

        durationTile.setValue(currentSnapshot.formattedDuration());
        framesTile.setValue(formatNum(currentSnapshot.totalFramesProvided()));
        missedTile.setValue(String.valueOf(currentSnapshot.totalFramesMissed()));
        missedTile.setValueColor(currentSnapshot.totalFramesMissed() > 0 ? Tokens.danger() : Tokens.text());
        missRateTile.setValue(String.format("%.2f%%", currentSnapshot.missRatePercent()));

        // Show avg latency with p95 - use microseconds for sub-millisecond values
        double avgLat = currentSnapshot.avgLatencyMs();
        double p95Lat = currentSnapshot.p95LatencyMs();
        boolean useMicroseconds = avgLat < 0.5 && p95Lat < 0.5;

        if (useMicroseconds) {
            double avgUs = avgLat * 1000;
            double p95Us = p95Lat * 1000;
            latencyTile.setValue(p95Lat > 0
                    ? String.format("%.0f (%.0f) μs", avgUs, p95Us)
                    : String.format("%.0f μs", avgUs));
        } else {
            latencyTile.setValue(p95Lat > 0
                    ? String.format("%.2f (%.2f) ms", avgLat, p95Lat)
                    : String.format("%.2f ms", avgLat));
        }

        fpsTile.setValue(String.format("%.1f fps", currentSnapshot.framesPerSecond()));
        stutterTile.setValue(String.valueOf(currentSnapshot.stutterCount()));
        stutterTile.setValueColor(currentSnapshot.stutterCount() > 0 ? Tokens.warning() : Tokens.text());
        stuckTile.setValue(String.valueOf(currentSnapshot.stuckCount()));
        stuckTile.setValueColor(currentSnapshot.stuckCount() > 0 ? Tokens.danger() : Tokens.text());
        gcTile.setValue(String.valueOf(currentSnapshot.gcEvents().length));

        // TTFF (Time to First Frame)
        long ttff = currentSnapshot.timeToFirstFrameMs();
        if (ttff > 0) {
            ttffTile.setValue(ttff + "ms");
            ttffTile.setValueColor(ttff > 1000 ? Tokens.danger() : ttff > 500 ? Tokens.warning() : Tokens.text());
        } else {
            ttffTile.setValue("—");
            ttffTile.setValueColor(Tokens.text());
        }

        // Miss rate windows (10s / 60s)
        missRateWindowTile.setValue(String.format("%.2f%% / %.2f%%",
            currentSnapshot.missRate10s(), currentSnapshot.missRate60s()));

        updateEventTable();
        repaintGraphs();
    }

    private Color colorFor(HealthStatus health) {
        return switch (health) {
            case GOOD -> Tokens.success();
            case WARNING -> Tokens.warning();
            case CRITICAL -> Tokens.danger();
        };
    }

    private void updateEventTable() {
        eventTableModel.setRowCount(0);
        if (currentSnapshot == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

        for (StutterEvent e : currentSnapshot.stutterEvents()) {
            eventTableModel.addRow(new Object[]{
                sdf.format(new Date(e.timestamp())),
                "Stut",
                e.missedFrames() + "f (" + e.durationMs() + "ms)",
                e.severity().name().substring(0, 3)
            });
        }

        for (PerformanceMetrics.StuckEvent e : currentSnapshot.stuckEvents()) {
            String details = e.trackTitle() != null
                ? truncate(e.trackTitle(), 20) + " (" + e.thresholdMs() + "ms)"
                : "Track stuck (" + e.thresholdMs() + "ms)";
            eventTableModel.addRow(new Object[]{
                sdf.format(new Date(e.timestamp())),
                "Stck",
                details,
                e.severity().name().substring(0, 3)
            });
        }

        for (GCMonitor.GCEvent e : currentSnapshot.gcEvents()) {
            eventTableModel.addRow(new Object[]{
                sdf.format(new Date(e.timestamp())),
                "GC",
                e.collectorName().replace(" Generation", "").replace("Copy", "YG") + " (" + e.durationMs() + "ms)",
                e.severity().name().substring(0, 3)
            });
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 2) + ".." : s;
    }

    private void repaintGraphs() {
        enhancedTimeline.repaint();
        latencyGraph.repaint();
        driftChart.repaint();
    }

    private String formatNum(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    public void updateMetrics() {
        refreshMetrics();
    }

    /**
     * Exports current metrics to a JSON file.
     */
    private void exportToJson() {
        if (currentSnapshot == null) {
            JOptionPane.showMessageDialog(this,
                "No metrics data available. Select a guild with active voice first.",
                "Export Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Diagnostics");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));

        // Generate default filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        chooser.setSelectedFile(new File("jmusicbot_diagnostics_" + timestamp + ".json"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".json")) {
                file = new File(file.getAbsolutePath() + ".json");
            }

            try {
                String json = currentSnapshot.toJson();
                Files.writeString(file.toPath(), json);
                JOptionPane.showMessageDialog(this,
                    "Diagnostics exported to:\n" + file.getAbsolutePath(),
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to export: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Copies a summary to the clipboard for sharing on Discord.
     */
    private void copyToClipboard() {
        if (currentSnapshot == null) {
            JOptionPane.showMessageDialog(this,
                "No metrics data available. Select a guild with active voice first.",
                "Copy Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String summary = currentSnapshot.toDiscordSummary();
        StringSelection selection = new StringSelection(summary);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

        JOptionPane.showMessageDialog(this,
            "Summary copied to clipboard!",
            "Copied", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Alpha-blended variant of a token colour, for shaded chart fills. */
    private static Color withAlpha(Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    // ===== Graph Panels =====

    private class LatencyGraphPanel extends JPanel {

        LatencyGraphPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            if (w < 40 || h < 20) { g2.dispose(); return; }

            int margin = 35; // Increased for microsecond labels
            int graphW = w - margin - 15;
            int graphH = h - 20; // More bottom margin for time labels
            int graphTop = 5;

            g2.setColor(Tokens.surfaceSunken());
            g2.fillRect(margin, graphTop, graphW, graphH);

            if (currentSnapshot == null || currentSnapshot.latencyBuckets().length == 0) {
                drawCentered(g2, "No data", w, h);
                g2.dispose();
                return;
            }

            LatencyBucket[] buckets = currentSnapshot.latencyBuckets();
            double p95Latency = currentSnapshot.p95LatencyMs();

            // Find max data latency
            double maxDataLat = 0;
            for (LatencyBucket b : buckets) {
                maxDataLat = Math.max(maxDataLat, b.max());
            }

            // Determine if we should use microsecond scale
            boolean useMicroseconds = maxDataLat < 0.5 && p95Latency < 0.5;

            double scaleFactor = useMicroseconds ? 1000.0 : 1.0;
            String unit = useMicroseconds ? "μs" : "ms";

            double maxDataDisplay = maxDataLat * scaleFactor;
            double p95Display = p95Latency * scaleFactor;

            double scaleMax;
            if (useMicroseconds) {
                if (maxDataDisplay <= 10) scaleMax = 10;
                else if (maxDataDisplay <= 25) scaleMax = 25;
                else if (maxDataDisplay <= 50) scaleMax = 50;
                else if (maxDataDisplay <= 100) scaleMax = 100;
                else if (maxDataDisplay <= 250) scaleMax = 250;
                else scaleMax = 500;
            } else {
                if (maxDataDisplay <= 1) scaleMax = 1;
                else if (maxDataDisplay <= 2) scaleMax = 2;
                else if (maxDataDisplay <= 5) scaleMax = 5;
                else if (maxDataDisplay <= 10) scaleMax = 10;
                else if (maxDataDisplay <= 20) scaleMax = 20;
                else scaleMax = Math.ceil(maxDataDisplay * 1.2);
            }

            g2.setFont(Tokens.fontSmall());

            // Draw horizontal grid lines at appropriate intervals
            double gridStep;
            if (useMicroseconds) {
                gridStep = scaleMax <= 25 ? 5 : scaleMax <= 50 ? 10 : scaleMax <= 100 ? 25 : scaleMax <= 250 ? 50 : 100;
            } else {
                gridStep = scaleMax <= 2 ? 0.5 : scaleMax <= 5 ? 1.0 : scaleMax <= 10 ? 2.0 : 5.0;
            }

            for (double val = gridStep; val < scaleMax; val += gridStep) {
                int y = graphTop + graphH - (int) (val / scaleMax * graphH);
                g2.setColor(Tokens.border());
                g2.drawLine(margin, y, margin + graphW, y);
                g2.setColor(Tokens.textMuted());
                String label = val == (int) val ? String.format("%.0f", val) : String.format("%.1f", val);
                g2.drawString(label, 2, y + 4);
            }

            // Y-axis labels with unit
            g2.setColor(Tokens.textMuted());
            g2.drawString(String.format("%.0f%s", scaleMax, unit), 2, graphTop + 10);
            g2.drawString("0", 2, graphTop + graphH - 2);

            // Draw min-max range as shaded area
            g2.setColor(withAlpha(Tokens.accent(), 40));
            for (int i = 0; i < buckets.length - 1; i++) {
                int x1 = margin + i * graphW / buckets.length;
                int x2 = margin + (i + 1) * graphW / buckets.length;
                double min1 = buckets[i].min() * scaleFactor;
                double max1 = buckets[i].max() * scaleFactor;
                double min2 = buckets[i + 1].min() * scaleFactor;
                double max2 = buckets[i + 1].max() * scaleFactor;
                int y1min = graphTop + graphH - (int) (Math.min(min1, scaleMax) / scaleMax * graphH);
                int y1max = graphTop + graphH - (int) (Math.min(max1, scaleMax) / scaleMax * graphH);
                int y2min = graphTop + graphH - (int) (Math.min(min2, scaleMax) / scaleMax * graphH);
                int y2max = graphTop + graphH - (int) (Math.min(max2, scaleMax) / scaleMax * graphH);
                g2.fillPolygon(new int[]{x1, x2, x2, x1}, new int[]{y1max, y2max, y2min, y1min}, 4);
            }

            // Draw p95 horizontal reference line (dashed)
            if (p95Display > 0 && p95Display <= scaleMax) {
                int p95Y = graphTop + graphH - (int) (p95Display / scaleMax * graphH);
                g2.setColor(Tokens.warning());
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10, new float[]{4, 4}, 0));
                g2.drawLine(margin, p95Y, margin + graphW, p95Y);
                g2.setStroke(new BasicStroke(1));
                g2.drawString("p95", margin + graphW + 2, p95Y + 4);
            }

            // Draw average latency line
            g2.setColor(Tokens.accent());
            g2.setStroke(new BasicStroke(2f));
            for (int i = 0; i < buckets.length - 1; i++) {
                int x1 = margin + i * graphW / buckets.length;
                int x2 = margin + (i + 1) * graphW / buckets.length;
                double avg1 = buckets[i].avg() * scaleFactor;
                double avg2 = buckets[i + 1].avg() * scaleFactor;
                int y1 = graphTop + graphH - (int) (Math.min(avg1, scaleMax) / scaleMax * graphH);
                int y2 = graphTop + graphH - (int) (Math.min(avg2, scaleMax) / scaleMax * graphH);
                g2.drawLine(x1, y1, x2, y2);
            }

            // Draw time axis labels
            g2.setStroke(new BasicStroke(1));
            g2.setFont(Tokens.fontSmall());
            g2.setColor(Tokens.textMuted());
            int timeInterval = selectedWindowSeconds <= 30 ? 10 : selectedWindowSeconds <= 60 ? 15 : 30;
            for (int sec = 0; sec <= selectedWindowSeconds; sec += timeInterval) {
                int x = margin + (int) ((double) sec / selectedWindowSeconds * graphW);
                g2.setColor(Tokens.border());
                g2.drawLine(x, graphTop, x, graphTop + graphH);
                g2.setColor(Tokens.textMuted());
                g2.drawString("-" + (selectedWindowSeconds - sec) + "s", x - 8, h - 3);
            }

            // Border
            g2.setColor(Tokens.border());
            g2.drawRect(margin, graphTop, graphW, graphH);
            g2.dispose();
        }
    }

    private class SchedulerDriftPanel extends JPanel {

        SchedulerDriftPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            if (w < 40 || h < 20) { g2.dispose(); return; }

            int margin = 35;
            int graphW = w - margin - 10;
            int graphH = h - 20;
            int graphTop = 5;

            g2.setColor(Tokens.surfaceSunken());
            g2.fillRect(margin, graphTop, graphW, graphH);

            if (healthSnapshot == null || healthSnapshot.isEmpty()) {
                drawCentered(g2, "No data", w, h);
                g2.dispose();
                return;
            }

            HealthSample[] samples = healthSnapshot.samples();
            if (samples.length < 2) {
                drawCentered(g2, "Collecting...", w, h);
                g2.dispose();
                return;
            }

            // Find max drift for scaling - use adaptive scale
            long maxDrift = 10;
            for (HealthSample s : samples) {
                maxDrift = Math.max(maxDrift, s.schedulerDriftMs());
            }

            // Adaptive scaling: 25ms, 50ms, 100ms, 200ms, 500ms
            long scaleMax;
            if (maxDrift <= 25) scaleMax = 25;
            else if (maxDrift <= 50) scaleMax = 50;
            else if (maxDrift <= 100) scaleMax = 100;
            else if (maxDrift <= 200) scaleMax = 200;
            else if (maxDrift <= 500) scaleMax = 500;
            else scaleMax = ((maxDrift / 100) + 1) * 100;

            g2.setFont(Tokens.fontSmall());

            // Draw horizontal grid lines
            long gridStep = scaleMax <= 50 ? 10 : scaleMax <= 100 ? 25 : scaleMax <= 200 ? 50 : 100;
            for (long ms = gridStep; ms < scaleMax; ms += gridStep) {
                int y = graphTop + graphH - (int) ((double) ms / scaleMax * graphH);
                g2.setColor(Tokens.border());
                g2.drawLine(margin, y, margin + graphW, y);
                g2.setColor(Tokens.textMuted());
                g2.drawString(ms + "", 2, y + 4);
            }

            // Y-axis labels
            g2.setColor(Tokens.textMuted());
            g2.drawString(scaleMax + "ms", 2, graphTop + 10);
            g2.drawString("0", 2, graphTop + graphH - 2);

            // Draw drift bars
            int barWidth = Math.max(2, graphW / samples.length - 1);
            for (int i = 0; i < samples.length; i++) {
                long drift = samples[i].schedulerDriftMs();
                if (drift <= 0) continue;

                int x = margin + (i * graphW / samples.length);
                int barHeight = (int) ((double) drift / scaleMax * graphH);
                barHeight = Math.min(barHeight, graphH); // Clamp to graph height

                // Color based on severity
                if (drift > 100) {
                    g2.setColor(Tokens.danger());
                } else if (drift > 50) {
                    g2.setColor(Tokens.warning());
                } else {
                    g2.setColor(Tokens.success());
                }

                g2.fillRect(x, graphTop + graphH - barHeight, barWidth, barHeight);
            }

            // Draw time axis labels
            g2.setFont(Tokens.fontSmall());
            int timeInterval = selectedWindowSeconds <= 30 ? 10 : selectedWindowSeconds <= 60 ? 15 : 30;
            for (int sec = 0; sec <= selectedWindowSeconds; sec += timeInterval) {
                int x = margin + (int) ((double) sec / selectedWindowSeconds * graphW);
                g2.setColor(Tokens.border());
                g2.drawLine(x, graphTop, x, graphTop + graphH);
                g2.setColor(Tokens.textMuted());
                g2.drawString("-" + (selectedWindowSeconds - sec) + "s", x - 8, h - 3);
            }

            // Border
            g2.setColor(Tokens.border());
            g2.drawRect(margin, graphTop, graphW, graphH);
            g2.dispose();
        }
    }

    private void drawCentered(Graphics2D g2, String text, int w, int h) {
        g2.setColor(Tokens.textMuted());
        int tw = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, w / 2 - tw / 2, h / 2 + 4);
    }

    private record GuildItem(Guild guild) {
        @Override
        public String toString() {
            return guild != null ? guild.getName() : "(No active voice)";
        }
    }
}
