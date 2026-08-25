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

import com.jagrosh.jmusicbot.audio.GCMonitor;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor.HealthSample;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor.HealthSnapshot;
import com.jagrosh.jmusicbot.gui.GuiLanguage;
import com.jagrosh.jmusicbot.gui.components.Widgets;
import com.jagrosh.jmusicbot.gui.theme.Tokens;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Panel for displaying system health metrics including CPU, memory, GC events.
 *
 * <p>Rebuilt on the shared card design system ({@link Widgets}, {@link Tokens}) to match the
 * Overview tab rather than looking like a separate diagnostics utility bolted onto the window.
 *
 * @author Arif Banai (arif-banai)
 */
public class SystemHealthPanel extends JPanel {

    private final JComboBox<WindowOption> windowSelector;

    // Charts
    private final CpuChartPanel cpuChart;
    private final HeapChartPanel heapChart;
    private final GcChartPanel gcChart;

    // Health indicator
    private final Widgets.Badge statusBadge = new Widgets.Badge(GuiLanguage.msg("gui.system.noData"), Tokens.textMuted());

    // Meters
    private final Widgets.Meter cpuMeter = new Widgets.Meter();
    private final Widgets.Meter heapMeter = new Widgets.Meter();

    // Stat tiles
    private final Widgets.StatTile cpuTile = new Widgets.StatTile(GuiLanguage.msg("gui.system.cpuUsage"));
    private final Widgets.StatTile heapTile = new Widgets.StatTile(GuiLanguage.msg("gui.system.heapMemory"));
    private final Widgets.StatTile gcCountTile = new Widgets.StatTile(GuiLanguage.msg("gui.system.gcCount"));
    private final Widgets.StatTile gcTimeTile = new Widgets.StatTile(GuiLanguage.msg("gui.system.gcTime"));
    private final Widgets.StatTile threadsTile = new Widgets.StatTile(GuiLanguage.msg("gui.system.threads"));
    private final Widgets.StatTile allocRateTile = new Widgets.StatTile(GuiLanguage.msg("gui.system.allocRate"));

    private HealthSnapshot currentSnapshot;
    private int selectedWindowSeconds = 60;

    private enum WindowOption {
        SECONDS_30(30, "gui.system.window30Seconds"),
        MINUTE_1(60, "gui.system.window1Minute"),
        MINUTE_2(120, "gui.system.window2Minutes"),
        MINUTE_5(300, "gui.system.window5Minutes");

        private final int seconds;
        private final String displayKey;

        WindowOption(int seconds, String displayKey) {
            this.seconds = seconds;
            this.displayKey = displayKey;
        }

        public int getSeconds() { return seconds; }

        @Override
        public String toString() { return GuiLanguage.msg(displayKey); }
    }

    public SystemHealthPanel() {
        setLayout(new BorderLayout(0, Tokens.SPACE_MD));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(
                Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG, Tokens.SPACE_LG));

        windowSelector = new JComboBox<>(WindowOption.values());
        windowSelector.setFont(Tokens.fontBody());
        windowSelector.setSelectedItem(WindowOption.MINUTE_1);
        windowSelector.addActionListener(e -> {
            WindowOption sel = (WindowOption) windowSelector.getSelectedItem();
            if (sel != null) {
                selectedWindowSeconds = sel.getSeconds();
                refreshMetrics();
            }
        });

        cpuChart = new CpuChartPanel();
        heapChart = new HeapChartPanel();
        gcChart = new GcChartPanel();

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
    }

    private Component buildHeader() {
        JPanel header = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_XS));
        header.add(Widgets.pageTitle(GuiLanguage.msg("gui.system.title")), BorderLayout.NORTH);
        header.add(Widgets.muted(GuiLanguage.msg("gui.system.subtitle")),
                BorderLayout.SOUTH);
        return header;
    }

    private Component buildBody() {
        JPanel content = Widgets.transparent(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(buildControlsCard());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));
        content.add(buildStatsSection());
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));

        JPanel chartsRow = Widgets.transparent(new GridLayout(1, 2, Tokens.SPACE_MD, 0));
        chartsRow.add(chartCard(GuiLanguage.msg("gui.system.cpuUsageChart"), cpuChart));
        chartsRow.add(chartCard(GuiLanguage.msg("gui.system.heapMemoryChart"), heapChart));
        chartsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        chartsRow.setPreferredSize(new Dimension(600, 220));
        content.add(chartsRow);
        content.add(Box.createVerticalStrut(Tokens.SPACE_MD));

        Widgets.Card gcCard = chartCard(GuiLanguage.msg("gui.system.gcEvents"), gcChart);
        gcCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        gcCard.setPreferredSize(new Dimension(600, 220));
        content.add(gcCard);

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

        card.add(Widgets.muted(GuiLanguage.msg("gui.system.window")));
        card.add(windowSelector);

        JButton refreshBtn = new JButton(GuiLanguage.msg("gui.system.refresh"));
        refreshBtn.setFont(Tokens.fontBody());
        refreshBtn.addActionListener(e -> refreshMetrics());
        card.add(refreshBtn);

        card.add(Box.createHorizontalStrut(Tokens.SPACE_MD));
        card.add(statusBadge);

        return card;
    }

    private Component buildStatsSection() {
        JPanel section = Widgets.transparent(new BorderLayout(0, Tokens.SPACE_SM));

        JLabel heading = new JLabel(GuiLanguage.msg("gui.system.currentStats"));
        heading.setFont(Tokens.fontHeading());
        heading.setForeground(Tokens.text());
        section.add(heading, BorderLayout.NORTH);

        JPanel body = Widgets.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        // CPU and heap get meters beneath their figures, since both are proportions.
        JPanel meterRow = Widgets.transparent(new GridLayout(1, 2, Tokens.SPACE_SM, 0));
        meterRow.add(wrapWithMeter(cpuTile, cpuMeter));
        meterRow.add(wrapWithMeter(heapTile, heapMeter));
        meterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        body.add(meterRow);
        body.add(Box.createVerticalStrut(Tokens.SPACE_SM));

        JPanel grid = Widgets.transparent(new GridLayout(1, 4, Tokens.SPACE_SM, Tokens.SPACE_SM));
        grid.add(gcCountTile);
        grid.add(gcTimeTile);
        grid.add(threadsTile);
        grid.add(allocRateTile);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        body.add(grid);

        section.add(body, BorderLayout.CENTER);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        return section;
    }

    /** A stat tile with a thin proportion meter tucked beneath its figure. */
    private Component wrapWithMeter(Widgets.StatTile tile, Widgets.Meter meter) {
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        // StatTile already lays out value/caption vertically; append the meter beneath it.
        tile.add(Box.createVerticalStrut(Tokens.SPACE_SM));
        meter.setAlignmentX(Component.LEFT_ALIGNMENT);
        tile.add(meter);
        return tile;
    }

    private Widgets.Card chartCard(String title, JPanel chart) {
        return Widgets.titledCard(title, chart);
    }

    public void refreshMetrics() {
        currentSnapshot = SystemHealthMonitor.getInstance().getSnapshot(selectedWindowSeconds);
        updateDisplay();
    }

    private void updateDisplay() {
        if (currentSnapshot == null || currentSnapshot.isEmpty()) {
            statusBadge.set(GuiLanguage.msg("gui.system.noData"), Tokens.textMuted());
            cpuTile.setValue("—");
            cpuTile.setValueColor(Tokens.text());
            cpuMeter.setFraction(0);
            cpuMeter.setFill(Tokens.accent());
            heapTile.setValue("—");
            heapTile.setValueColor(Tokens.text());
            heapMeter.setFraction(0);
            heapMeter.setFill(Tokens.accent());
            gcCountTile.setValue("—");
            gcTimeTile.setValue("—");
            gcTimeTile.setValueColor(Tokens.text());
            threadsTile.setValue("—");
            allocRateTile.setValue("—");
            repaintCharts();
            return;
        }

        // Update status indicator
        statusBadge.set(currentSnapshot.status().name(), colorFor(currentSnapshot.status()));

        // CPU
        if (currentSnapshot.currentProcessCpu() >= 0) {
            double cpu = currentSnapshot.currentProcessCpu();
            cpuTile.setValue(String.format("%.1f%%", cpu));
            cpuTile.setValueColor(cpu > 80 ? Tokens.danger() : cpu > 50 ? Tokens.warning() : Tokens.text());
            cpuMeter.setFraction(cpu / 100.0);
            cpuMeter.setFill(cpu > 80 ? Tokens.danger() : cpu > 50 ? Tokens.warning() : Tokens.accent());
        } else {
            cpuTile.setValue(GuiLanguage.msg("gui.system.notAvailable"));
            cpuTile.setValueColor(Tokens.text());
            cpuMeter.setFraction(0);
            cpuMeter.setFill(Tokens.accent());
        }

        // Heap
        double heapPct = currentSnapshot.currentHeapPercent();
        heapTile.setValue(currentSnapshot.formattedHeap());
        heapTile.setValueColor(heapPct > 80 ? Tokens.danger() : heapPct > 60 ? Tokens.warning() : Tokens.text());
        heapMeter.setFraction(heapPct / 100.0);
        heapMeter.setFill(heapPct > 80 ? Tokens.danger() : heapPct > 60 ? Tokens.warning() : Tokens.accent());

        GCMonitor.GCEvent[] gcEvents = GCMonitor.getInstance().getRecentEvents(selectedWindowSeconds);
        gcCountTile.setValue(String.valueOf(gcEvents.length));

        long totalGcTime = 0;
        for (GCMonitor.GCEvent e : gcEvents) {
            totalGcTime += e.durationMs();
        }
        gcTimeTile.setValue(totalGcTime + " ms");
        gcTimeTile.setValueColor(totalGcTime > 500 ? Tokens.danger() : totalGcTime > 100 ? Tokens.warning() : Tokens.text());

        threadsTile.setValue(String.valueOf(currentSnapshot.currentThreadCount()));

        if (currentSnapshot.avgAllocationRate() > 0) {
            double mbPerSec = currentSnapshot.avgAllocationRate() / (1024 * 1024);
            allocRateTile.setValue(String.format("%.1f MB/s", mbPerSec));
        } else {
            allocRateTile.setValue("—");
        }

        repaintCharts();
    }

    private Color colorFor(SystemHealthMonitor.HealthStatus status) {
        return switch (status) {
            case GOOD -> Tokens.success();
            case WARNING -> Tokens.warning();
            case CRITICAL -> Tokens.danger();
        };
    }

    private void repaintCharts() {
        cpuChart.repaint();
        heapChart.repaint();
        gcChart.repaint();
    }

    public void updateMetrics() {
        refreshMetrics();
    }

    /** Alpha-blended variant of a token colour, for shaded chart fills. */
    private static Color withAlpha(Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    // ===== Chart Panels =====

    private class CpuChartPanel extends JPanel {

        CpuChartPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int margin = 30;
            int graphW = w - margin - 5;
            int graphH = h - 20;
            int graphTop = 5;

            g2.setColor(Tokens.surfaceSunken());
            g2.fillRect(margin, graphTop, graphW, graphH);

            // Draw horizontal grid lines at 25%, 50%, 75%
            g2.setFont(Tokens.fontSmall());
            for (int pct = 25; pct < 100; pct += 25) {
                int y = graphTop + graphH - (int) (pct / 100.0 * graphH);
                g2.setColor(Tokens.border());
                g2.drawLine(margin, y, margin + graphW, y);
                g2.setColor(Tokens.textMuted());
                g2.drawString(pct + "", 2, y + 4);
            }

            // Y-axis labels
            g2.setColor(Tokens.textMuted());
            g2.drawString("100%", 2, graphTop + 10);
            g2.drawString("0", 2, graphTop + graphH - 2);

            if (currentSnapshot == null || currentSnapshot.isEmpty()) {
                drawCentered(g2, GuiLanguage.msg("gui.system.chartNoData"), w, h);
                g2.dispose();
                return;
            }

            HealthSample[] samples = currentSnapshot.samples();
            if (samples.length < 2) {
                drawCentered(g2, GuiLanguage.msg("gui.system.collecting"), w, h);
                g2.dispose();
                return;
            }

            // Draw CPU area fill
            g2.setColor(withAlpha(Tokens.accent(), 40));
            int[] xPoints = new int[samples.length + 2];
            int[] yPoints = new int[samples.length + 2];
            for (int i = 0; i < samples.length; i++) {
                double cpu = Math.max(0, samples[i].processCpuPercent());
                xPoints[i] = margin + (i * graphW / samples.length);
                yPoints[i] = graphTop + graphH - (int) (cpu / 100.0 * graphH);
            }
            xPoints[samples.length] = margin + graphW;
            yPoints[samples.length] = graphTop + graphH;
            xPoints[samples.length + 1] = margin;
            yPoints[samples.length + 1] = graphTop + graphH;
            g2.fillPolygon(xPoints, yPoints, samples.length + 2);

            // Draw CPU line
            g2.setColor(Tokens.accent());
            g2.setStroke(new BasicStroke(2f));

            for (int i = 0; i < samples.length - 1; i++) {
                double cpu1 = Math.max(0, samples[i].processCpuPercent());
                double cpu2 = Math.max(0, samples[i + 1].processCpuPercent());

                int x1 = margin + (i * graphW / samples.length);
                int x2 = margin + ((i + 1) * graphW / samples.length);
                int y1 = graphTop + graphH - (int) (cpu1 / 100.0 * graphH);
                int y2 = graphTop + graphH - (int) (cpu2 / 100.0 * graphH);

                g2.drawLine(x1, y1, x2, y2);
            }

            g2.setStroke(new BasicStroke(1));
            g2.setColor(Tokens.border());
            g2.drawRect(margin, graphTop, graphW, graphH);
            g2.dispose();
        }
    }

    private class HeapChartPanel extends JPanel {

        HeapChartPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int margin = 30;
            int graphW = w - margin - 5;
            int graphH = h - 20;
            int graphTop = 5;

            g2.setColor(Tokens.surfaceSunken());
            g2.fillRect(margin, graphTop, graphW, graphH);

            // Draw horizontal grid lines at 25%, 50%, 75%
            g2.setFont(Tokens.fontSmall());
            for (int pct = 25; pct < 100; pct += 25) {
                int y = graphTop + graphH - (int) (pct / 100.0 * graphH);
                g2.setColor(Tokens.border());
                g2.drawLine(margin, y, margin + graphW, y);
                g2.setColor(Tokens.textMuted());
                g2.drawString(pct + "", 2, y + 4);
            }

            // Y-axis labels
            g2.setColor(Tokens.textMuted());
            g2.drawString("100%", 2, graphTop + 10);
            g2.drawString("0", 2, graphTop + graphH - 2);

            if (currentSnapshot == null || currentSnapshot.isEmpty()) {
                drawCentered(g2, GuiLanguage.msg("gui.system.chartNoData"), w, h);
                g2.dispose();
                return;
            }

            HealthSample[] samples = currentSnapshot.samples();
            if (samples.length < 2) {
                drawCentered(g2, GuiLanguage.msg("gui.system.collecting"), w, h);
                g2.dispose();
                return;
            }

            // Draw heap fill
            g2.setColor(withAlpha(Tokens.accent(), 50));
            int[] xPoints = new int[samples.length + 2];
            int[] yPoints = new int[samples.length + 2];

            for (int i = 0; i < samples.length; i++) {
                xPoints[i] = margin + (i * graphW / samples.length);
                yPoints[i] = graphTop + graphH - (int) (samples[i].heapUsedPercent() / 100.0 * graphH);
            }
            xPoints[samples.length] = margin + graphW;
            yPoints[samples.length] = graphTop + graphH;
            xPoints[samples.length + 1] = margin;
            yPoints[samples.length + 1] = graphTop + graphH;

            g2.fillPolygon(xPoints, yPoints, samples.length + 2);

            // Draw heap line
            g2.setColor(Tokens.accent());
            g2.setStroke(new BasicStroke(2f));

            for (int i = 0; i < samples.length - 1; i++) {
                int x1 = margin + (i * graphW / samples.length);
                int x2 = margin + ((i + 1) * graphW / samples.length);
                int y1 = graphTop + graphH - (int) (samples[i].heapUsedPercent() / 100.0 * graphH);
                int y2 = graphTop + graphH - (int) (samples[i + 1].heapUsedPercent() / 100.0 * graphH);

                g2.drawLine(x1, y1, x2, y2);
            }

            g2.setStroke(new BasicStroke(1));
            g2.setColor(Tokens.border());
            g2.drawRect(margin, graphTop, graphW, graphH);
            g2.dispose();
        }
    }

    private class GcChartPanel extends JPanel {

        GcChartPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int margin = 35;
            int graphW = w - margin - 10;
            int graphH = h - 25;
            int graphTop = 5;

            g2.setColor(Tokens.surfaceSunken());
            g2.fillRect(margin, graphTop, graphW, graphH);

            GCMonitor.GCEvent[] gcEvents = GCMonitor.getInstance().getRecentEvents(selectedWindowSeconds);

            long windowMs = selectedWindowSeconds * 1000L;
            long now = System.currentTimeMillis();
            long startTime = now - windowMs;

            // Find max duration for scaling
            long maxDuration = 10;
            for (GCMonitor.GCEvent e : gcEvents) {
                maxDuration = Math.max(maxDuration, e.durationMs());
            }
            // Use adaptive scale
            long scaleMax = maxDuration <= 10 ? 10 : maxDuration <= 25 ? 25 :
                           maxDuration <= 50 ? 50 : maxDuration <= 100 ? 100 :
                           ((maxDuration / 50) + 1) * 50;

            // Draw horizontal grid lines
            g2.setFont(Tokens.fontSmall());
            long gridStep = scaleMax <= 25 ? 5 : scaleMax <= 50 ? 10 : scaleMax <= 100 ? 25 : 50;
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

            // Draw time axis labels
            int timeInterval = selectedWindowSeconds <= 30 ? 10 : selectedWindowSeconds <= 60 ? 15 :
                              selectedWindowSeconds <= 120 ? 30 : 60;
            for (int sec = 0; sec <= selectedWindowSeconds; sec += timeInterval) {
                int x = margin + (int) ((double) sec / selectedWindowSeconds * graphW);
                g2.setColor(Tokens.border());
                g2.drawLine(x, graphTop, x, graphTop + graphH);
                g2.setColor(Tokens.textMuted());
                g2.drawString("-" + (selectedWindowSeconds - sec) + "s", x - 8, h - 3);
            }

            if (gcEvents.length == 0) {
                g2.setColor(Tokens.textMuted());
                g2.drawString(GuiLanguage.msg("gui.system.noGcEvents"), w / 2 - 30, graphTop + graphH / 2);
            } else {
                // Draw GC bars
                for (GCMonitor.GCEvent e : gcEvents) {
                    if (e.timestamp() < startTime) continue;

                    int x = margin + (int) ((e.timestamp() - startTime) / (double) windowMs * graphW);
                    int barHeight = (int) ((double) e.durationMs() / scaleMax * graphH);
                    barHeight = Math.min(barHeight, graphH);

                    g2.setColor(e.severity() == GCMonitor.GCEvent.Severity.SEVERE ? Tokens.danger() :
                               e.severity() == GCMonitor.GCEvent.Severity.MODERATE ? Tokens.warning() : Tokens.accent());
                    g2.fillRect(x - 3, graphTop + graphH - barHeight, 6, barHeight);
                }
            }

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
}
