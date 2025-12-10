package com.beginsecure;

import com.beginsecure.panels.*;

import javax.swing.*;
import java.awt.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Top-level window that wires together the repository view, metrics, and status controls.
 * @author @NickGottwald
 * @author @Muska Said
 */
public final class AppFrame extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(AppFrame.class.getName());
    static {
        try {
            LOGGER.setUseParentHandlers(false);
            ConsoleHandler handler = new ConsoleHandler();
            handler.setFormatter(new WhiteFormatter());
            handler.setLevel(java.util.logging.Level.INFO);
            LOGGER.addHandler(handler);
        } catch (SecurityException ignored) { }
    }
    public AppFrame() {
        super("GitHubViz - Code Repository Visualizer");

        LOGGER.info("Initializing top bar panel");
        var topBar   = new TopBarPanel();
        LOGGER.info("Initializing repository tree panel");
        var repoTree = new RepoTreePanel();
        LOGGER.info("Initializing metrics panel");
        var metricsPanel = new MetricsPanel();
        LOGGER.info("Initializing diagram panel");
        var diagramPanel = new DiagramPanel();
        LOGGER.info("Initializing chat panel");
        var chatPanel = new ChatGPTPanel();
        LOGGER.info("Initializing grid panel");
        var gridPanel = new GridPanel();
        var tabs     = new TabsPanel(gridPanel, metricsPanel, diagramPanel, chatPanel);

        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, repoTree, tabs);
        split.setResizeWeight(0.28);

        LOGGER.info("Initializing status bar panel");
        var status   = new StatusBarPanel();                            // listens to Blackboard

        var icons = loadAppIcons();
        if (!icons.isEmpty()) {
            setIconImages(icons);
            applyTaskbarIcon(icons.get(icons.size() - 1));
        }

        setLayout(new BorderLayout());
        add(topBar, BorderLayout.NORTH);
        add(split,  BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
    }

    private static java.util.List<java.awt.Image> loadAppIcons() {
        var url = AppFrame.class.getResource("/com/beginsecure/github-icon-27.jpg");
        if (url == null) return java.util.Collections.emptyList();
        try {
            var base = javax.imageio.ImageIO.read(url);
            java.util.List<java.awt.Image> icons = new java.util.ArrayList<>();
            icons.add(scale(base, 16));
            icons.add(scale(base, 24));
            icons.add(scale(base, 32));
            icons.add(scale(base, 48));
            icons.add(scale(base, 64));
            icons.add(base);
            return icons;
        } catch (java.io.IOException e) {
            return java.util.Collections.emptyList();
        }
    }

    private static void applyTaskbarIcon(java.awt.Image icon) {
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar.getTaskbar().setIconImage(icon);
            }
        } catch (UnsupportedOperationException | SecurityException ignored) {
        }
    }

    private static java.awt.Image scale(java.awt.Image src, int size) {
        if (src.getWidth(null) == size && src.getHeight(null) == size) return src;
        var img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g2 = img.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, size, size, null);
        g2.dispose();
        return img;
    }
    private static final class WhiteFormatter extends Formatter {
        private static final String WHITE = "\u001B[37m";
        private static final String RESET = "\u001B[0m";

        @Override
        public String format(LogRecord record) {
            return WHITE + formatMessage(record) + RESET + System.lineSeparator();
        }
    }
}
