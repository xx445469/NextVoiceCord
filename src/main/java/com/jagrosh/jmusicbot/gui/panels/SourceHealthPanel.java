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

import com.jagrosh.jmusicbot.audio.TrackLoadingMonitor;
import com.jagrosh.jmusicbot.audio.TrackLoadingMonitor.*;
import com.jagrosh.jmusicbot.gui.GuiLanguage;
import com.jagrosh.jmusicbot.gui.components.Widgets;
import com.jagrosh.jmusicbot.gui.theme.Tokens;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Panel for visualizing track loading health and performance by source.
 * Shows load times, success rates, and recent load history.
 *
 * @author Arif Banai (arif-banai)
 */
public class SourceHealthPanel extends JPanel {

    private final JComboBox<WindowOption> windowSelector;

    // Stats
    private final Widgets.StatTile totalTile = new Widgets.StatTile(GuiLanguage.msg("gui.sources.loads"));
    private final Widgets.StatTile successTile = new Widgets.StatTile(GuiLanguage.msg("gui.sources.successRate"));
    private final Widgets.StatTile avgTile = new Widgets.StatTile(GuiLanguage.msg("gui.sources.avgLoadTime"));
    private final Widgets.StatTile p95Tile = new Widgets.StatTile(GuiLanguage.msg("gui.sources.p95LoadTime"));
    private final Widgets.StatTile failedTile = new Widgets.StatTile(GuiLanguage.msg("gui.sources.failed"));

    // Source table
    private final JTable sourceTable;
    private final DefaultTableModel sourceTableModel;

    // Recent loads table
    private final JTable recentLoadsTable;
    private final DefaultTableModel recentLoadsModel;

    // Visualization
    private final LoadTimeChartPanel loadTimeChart;
    private final SuccessPiePanel successPie;

    private final TrackLoadingMonitor monitor;
    private LoadingSnapshot currentSnapshot;
    private int selectedWindowSeconds = 60;

    private enum WindowOption {
        SECONDS_30(30, GuiLanguage.msg("gui.sources.window30Seconds")),
        MINUTE_1(60, GuiLanguage.msg("gui.sources.window1Minute")),
        MINUTE_5(300, GuiLanguage.msg("gui.sources.window5Minutes")),
        MINUTE_10(600, GuiLanguage.msg("gui.sources.window10Minutes"));

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

    /**
     * Creates a new SourceHealthPanel.
     *
     * @param monitor the TrackLoadingMonitor instance to display data from
     */
    public SourceHealthPanel(TrackLoadingMonitor monitor) {
        this.monitor = monitor;

        setLayout(new BorderLayout(0, Tokens.SPACE_MD));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG));

        windowSelector = new JComboBox<>(WindowOption.values());
        windowSelector.setSelectedItem(WindowOption.MINUTE_1);
        windowSelector.setFont(Tokens.fontBody());
        windowSelector.addActionListener(e -> {
            WindowOption sel = (WindowOption) windowSelector.getSelectedItem();
            if (sel != null) {
                selectedWindowSeconds = sel.getSeconds();
                refreshMetrics();
            }
        });

        String[] sourceCols = {
                GuiLanguage.msg("gui.sources.colSource"),
                GuiLanguage.msg("gui.sources.colLoads"),
                GuiLanguage.msg("gui.sources.colSuccessPercent"),
                GuiLanguage.msg("gui.sources.colAvgMs"),
                GuiLanguage.msg("gui.sources.colP95Ms")
        };
        sourceTableModel = new DefaultTableModel(sourceCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        sourceTable = new JTable(sourceTableModel);
        styleTable(sourceTable);
        sourceTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        sourceTable.getColumnModel().getColumn(1).setPreferredWidth(50);
        sourceTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        sourceTable.getColumnModel().getColumn(3).setPreferredWidth(70);
        sourceTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        sourceTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof String s) {
                    try {
                        double rate = Double.parseDouble(s.replace("%", ""));
                        if (rate >= 95) c.setForeground(Tokens.success());
                        else if (rate >= 80) c.setForeground(Tokens.warning());
                        else c.setForeground(Tokens.danger());
                    } catch (NumberFormatException ignored) {}
                }
                return c;
            }
        });

        String[] recentCols = {
                GuiLanguage.msg("gui.sources.colTime"),
                GuiLanguage.msg("gui.sources.colSource"),
                GuiLanguage.msg("gui.sources.colResult"),
                GuiLanguage.msg("gui.sources.colDuration"),
                GuiLanguage.msg("gui.sources.colTrackError")
        };
        recentLoadsModel = new DefaultTableModel(recentCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        recentLoadsTable = new JTable(recentLoadsModel);
        styleTable(recentLoadsTable);
        recentLoadsTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        recentLoadsTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        recentLoadsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        recentLoadsTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        recentLoadsTable.getColumnModel().getColumn(4).setPreferredWidth(150);
        recentLoadsTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof String s) {
                    if (s.contains("LOADED")) c.setForeground(Tokens.success());
                    else if (s.equals("NO_MATCHES")) c.setForeground(Tokens.warning());
                    else if (s.equals("LOAD_FAILED")) c.setForeground(Tokens.danger());
                }
                return c;
            }
        });

        loadTimeChart = new LoadTimeChartPanel();
        loadTimeChart.setOpaque(false);
        loadTimeChart.setPreferredSize(new Dimension(0, 220));

        successPie = new SuccessPiePanel();
        successPie.setOpaque(false);
        successPie.setPreferredSize(new Dimension(0, 220));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
    }

    private void styleTable(JTable table) {
        table.setShowGrid(false);
        table.setRowHeight(Tokens.SPACE_LG + 4);
        table.setBackground(Tokens.surfaceRaised());
        table.setForeground(Tokens.text());
        table.setFont(Tokens.fontBody());
        table.setBorder(BorderFactory.createEmptyBorder());
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionBackground(Tokens.accent());
        table.setSelectionForeground(Color.WHITE);

        JTableHeader header = table.getTableHeader();
        header.setFont(Tokens.fontSmall());
        header.setForeground(Tokens.textMuted());
        header.setBackground(Tokens.surfaceRaised());
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, Tokens.SPACE_XS, 0));
        header.setReorderingAllowed(false);
    }

    private Component buildHeader() {
        JPanel header = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_XS));
        header.add(Widgets.pageTitle(GuiLanguage.msg("gui.sources.title")), BorderLayout.NORTH);
        header.add(Widgets.muted(GuiLanguage.msg("gui.sources.subtitle")), BorderLayout.SOUTH);
        return header;
    }

    private Component buildBody() {
        JPanel body = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_MD));
        body.add(buildControlsAndStats(), BorderLayout.NORTH);
        body.add(buildScrollArea(), BorderLayout.CENTER);
        return body;
    }

    private Component buildControlsAndStats() {
        JPanel top = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_MD));

        JPanel controls = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_SM, 0));
        JLabel windowLabel = Widgets.muted(GuiLanguage.msg("gui.sources.windowLabel"));
        controls.add(windowLabel);
        controls.add(windowSelector);

        JButton refreshBtn = new JButton(GuiLanguage.msg("gui.sources.refresh"));
        refreshBtn.setFont(Tokens.fontBody());
        refreshBtn.addActionListener(e -> refreshMetrics());
        controls.add(refreshBtn);

        top.add(controls, BorderLayout.NORTH);

        JPanel stats = Widgets.transparent(new GridLayout(1, 5, Tokens.SPACE_SM, 0));
        stats.add(totalTile);
        stats.add(successTile);
        stats.add(avgTile);
        stats.add(p95Tile);
        stats.add(failedTile);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        top.add(stats, BorderLayout.CENTER);

        return top;
    }

    private Component buildScrollArea() {
        JPanel content = Widgets.transparent(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel charts = Widgets.transparent(new GridLayout(1, 2, Tokens.SPACE_MD, 0));
        charts.add(Widgets.titledCard(GuiLanguage.msg("gui.sources.loadTimeBySource"), loadTimeChart));
        charts.add(Widgets.titledCard(GuiLanguage.msg("gui.sources.successRateBySource"), successPie));
        charts.setAlignmentX(LEFT_ALIGNMENT);
        charts.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        content.add(charts);
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));

        JScrollPane sourceScroll = new JScrollPane(sourceTable);
        styleScroll(sourceScroll);
        sourceScroll.setPreferredSize(new Dimension(0, 260));

        JScrollPane recentScroll = new JScrollPane(recentLoadsTable);
        styleScroll(recentScroll);
        recentScroll.setPreferredSize(new Dimension(0, 260));

        JPanel tables = Widgets.transparent(new GridLayout(1, 2, Tokens.SPACE_MD, 0));
        tables.add(Widgets.titledCard(GuiLanguage.msg("gui.sources.sourcesCard"), sourceScroll));
        tables.add(Widgets.titledCard(GuiLanguage.msg("gui.sources.recentLoads"), recentScroll));
        tables.setAlignmentX(LEFT_ALIGNMENT);
        tables.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
        content.add(tables);

        return Widgets.scrollable(content);
    }

    private void styleScroll(JScrollPane scroll) {
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
    }

    /**
     * Refreshes metrics from TrackLoadingMonitor.
     */
    public void refreshMetrics() {
        if (monitor == null) {
            currentSnapshot = null;
        } else {
            currentSnapshot = monitor.getSnapshot(selectedWindowSeconds);
        }
        updateDisplay();
    }

    private void updateDisplay() {
        if (currentSnapshot == null) {
            totalTile.setValue("—");
            successTile.setValue("—");
            avgTile.setValue("—");
            p95Tile.setValue("—");
            failedTile.setValue("—");
            failedTile.setValueColor(Tokens.text());
            sourceTableModel.setRowCount(0);
            recentLoadsModel.setRowCount(0);
            repaintCharts();
            return;
        }

        // Update summary stats
        totalTile.setValue(String.valueOf(currentSnapshot.loadsInWindow()));

        double successRate = currentSnapshot.successRatePercent();
        successTile.setValue(String.format("%.1f%%", successRate));
        successTile.setValueColor(successRate >= 95 ? Tokens.success() :
                                   successRate >= 80 ? Tokens.warning() : Tokens.danger());

        avgTile.setValue(String.format("%.0fms", currentSnapshot.avgLoadDurationMs()));
        p95Tile.setValue(String.format("%.0fms", currentSnapshot.p95LoadDurationMs()));

        failedTile.setValue(String.valueOf(currentSnapshot.failedInWindow()));
        failedTile.setValueColor(currentSnapshot.failedInWindow() > 0 ? Tokens.danger() : Tokens.text());

        // Update source table
        updateSourceTable();

        // Update recent loads table
        updateRecentLoadsTable();

        repaintCharts();
    }

    private void updateSourceTable() {
        sourceTableModel.setRowCount(0);

        for (String source : currentSnapshot.trackedSources()) {
            SourceStats stats = monitor.getSourceStats(source);
            if (stats == null) continue;

            sourceTableModel.addRow(new Object[]{
                source,
                stats.getTotalLoads(),
                String.format("%.1f%%", stats.getSuccessRate()),
                String.format("%.0f", stats.getAverageDurationMs()),
                String.format("%.0f", stats.getP95DurationMs())
            });
        }
    }

    private void updateRecentLoadsTable() {
        recentLoadsModel.setRowCount(0);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        LoadEvent[] events = currentSnapshot.recentEvents();

        // Show most recent 20 events, reversed so newest is at top
        int start = Math.max(0, events.length - 20);
        for (int i = events.length - 1; i >= start; i--) {
            LoadEvent e = events[i];

            String details = e.trackOrPlaylistName() != null
                ? truncate(e.trackOrPlaylistName(), 40)
                : e.errorMessage() != null ? truncate(e.errorMessage(), 40) : "-";

            recentLoadsModel.addRow(new Object[]{
                sdf.format(new Date(e.timestamp())),
                e.source(),
                e.result().name(),
                e.loadDurationMs() + "ms",
                details
            });
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 2) + ".." : s;
    }

    private void repaintCharts() {
        loadTimeChart.repaint();
        successPie.repaint();
    }

    // ===== Chart Panels =====

    private class LoadTimeChartPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int margin = 40;
            int graphW = w - margin - 10;
            int graphH = h - 30;

            g2.setColor(Tokens.surfaceSunken());
            g2.fillRoundRect(margin, 10, graphW, graphH, Tokens.RADIUS_SM, Tokens.RADIUS_SM);

            if (currentSnapshot == null || currentSnapshot.trackedSources().length == 0) {
                g2.setColor(Tokens.textMuted());
                g2.setFont(Tokens.fontSmall());
                g2.drawString(GuiLanguage.msg("gui.sources.noData"), w / 2 - 20, h / 2);
                g2.dispose();
                return;
            }

            String[] sources = currentSnapshot.trackedSources();
            int barCount = sources.length;
            int barWidth = Math.max(20, (graphW - 10) / barCount - 10);

            // Find max duration for scaling
            double maxDuration = 100;
            for (String source : sources) {
                SourceStats stats = monitor.getSourceStats(source);
                if (stats != null) {
                    maxDuration = Math.max(maxDuration, stats.getP95DurationMs());
                }
            }
            maxDuration = Math.ceil(maxDuration / 100) * 100; // Round up to nearest 100

            // Draw Y axis labels
            g2.setFont(Tokens.fontSmall());
            g2.setColor(Tokens.textMuted());
            g2.drawString(String.format("%.0f", maxDuration), 5, 15);
            g2.drawString("0", 5, 10 + graphH);

            // Draw bars
            for (int i = 0; i < sources.length; i++) {
                String source = sources[i];
                SourceStats stats = monitor.getSourceStats(source);
                if (stats == null) continue;

                int x = margin + 5 + i * (barWidth + 10);

                // Avg bar
                double avgHeight = (stats.getAverageDurationMs() / maxDuration) * graphH;
                g2.setColor(Tokens.accent());
                g2.fillRect(x, 10 + graphH - (int) avgHeight, barWidth / 2 - 2, (int) avgHeight);

                // P95 bar
                double p95Height = (stats.getP95DurationMs() / maxDuration) * graphH;
                g2.setColor(Tokens.warning());
                g2.fillRect(x + barWidth / 2, 10 + graphH - (int) p95Height, barWidth / 2 - 2, (int) p95Height);

                // Source label
                g2.setColor(Tokens.textMuted());
                String label = truncate(source, 8);
                g2.drawString(label, x, h - 5);
            }

            // Legend
            g2.setFont(Tokens.fontSmall());
            g2.setColor(Tokens.accent());
            g2.fillRect(w - 70, 15, 10, 10);
            g2.setColor(Tokens.textMuted());
            g2.drawString(GuiLanguage.msg("gui.sources.legendAvg"), w - 55, 24);

            g2.setColor(Tokens.warning());
            g2.fillRect(w - 70, 30, 10, 10);
            g2.setColor(Tokens.textMuted());
            g2.drawString(GuiLanguage.msg("gui.sources.legendP95"), w - 55, 39);

            g2.dispose();
        }
    }

    private class SuccessPiePanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();

            g2.setColor(Tokens.surfaceSunken());
            g2.fillRoundRect(10, 10, w - 20, h - 20, Tokens.RADIUS_SM, Tokens.RADIUS_SM);

            if (currentSnapshot == null || currentSnapshot.loadsInWindow() == 0) {
                g2.setColor(Tokens.textMuted());
                g2.setFont(Tokens.fontSmall());
                g2.drawString(GuiLanguage.msg("gui.sources.noData"), w / 2 - 20, h / 2);
                g2.dispose();
                return;
            }

            int success = currentSnapshot.successInWindow();
            int failed = currentSnapshot.failedInWindow();
            int noMatch = currentSnapshot.noMatchInWindow();
            int total = success + failed + noMatch;

            int diameter = Math.min(w, h) - 60;
            int x = (w - diameter) / 2;
            int y = 20;

            // Draw pie slices
            int startAngle = 0;

            if (success > 0) {
                int arc = (int) ((success * 360.0) / total);
                g2.setColor(Tokens.success());
                g2.fillArc(x, y, diameter, diameter, startAngle, arc);
                startAngle += arc;
            }

            if (noMatch > 0) {
                int arc = (int) ((noMatch * 360.0) / total);
                g2.setColor(Tokens.warning());
                g2.fillArc(x, y, diameter, diameter, startAngle, arc);
                startAngle += arc;
            }

            if (failed > 0) {
                int arc = 360 - startAngle; // Remainder
                g2.setColor(Tokens.danger());
                g2.fillArc(x, y, diameter, diameter, startAngle, arc);
            }

            // Legend
            int legendY = y + diameter + 15;
            g2.setFont(Tokens.fontSmall());

            g2.setColor(Tokens.success());
            g2.fillRect(10, legendY, 10, 10);
            g2.setColor(Tokens.textMuted());
            g2.drawString(GuiLanguage.msg("gui.sources.legendSuccess", success), 25, legendY + 9);

            g2.setColor(Tokens.warning());
            g2.fillRect(w / 3, legendY, 10, 10);
            g2.setColor(Tokens.textMuted());
            g2.drawString(GuiLanguage.msg("gui.sources.legendNoMatch", noMatch), w / 3 + 15, legendY + 9);

            g2.setColor(Tokens.danger());
            g2.fillRect(2 * w / 3, legendY, 10, 10);
            g2.setColor(Tokens.textMuted());
            g2.drawString(GuiLanguage.msg("gui.sources.legendFailed", failed), 2 * w / 3 + 15, legendY + 9);

            g2.dispose();
        }
    }
}
