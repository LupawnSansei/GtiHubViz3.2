package com.beginsecure.panels;



import javax.swing.*;

/**
 * Hosts the primary visualization panels inside a single tabbed container.
 * @author @NickGottwald
 * @author @Muska Said
 */
public final class TabsPanel extends JTabbedPane {
    public TabsPanel(GridPanel grid, MetricsPanel metrics, DiagramPanel diagram, ChatGPTPanel chat) {
        addTab("Grid", grid);
        addTab("Metrics", metrics);
        addTab("Diagram", diagram);
        addTab("AI Chat", chat);
    }
}
