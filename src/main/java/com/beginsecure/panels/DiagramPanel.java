package com.beginsecure.panels;

import com.beginsecure.Blackboard;
import com.beginsecure.Square;
import com.beginsecure.util.RelationshipExtractor;
import com.beginsecure.util.SourceUtils;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays a PlantUML dependency diagram for the files currently selected in the workspace tree.
 * The rendered diagram mirrors the example "resultFP" panel shared in the requirements.
 * @author @NickGottwald
 * @author @Muska Said
 */
public final class DiagramPanel extends JPanel implements PropertyChangeListener {

    private final DiagramCanvas canvas = new DiagramCanvas();
    private final JLabel statusLabel = new JLabel("Load a repository to visualize dependencies.");
    private volatile boolean loading;

    public DiagramPanel() {
        super(new BorderLayout());
        setBackground(new Color(246, 244, 240));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JScrollPane scroller = new JScrollPane(canvas);
        scroller.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        scroller.getViewport().setBackground(new Color(246, 244, 240));
        add(scroller, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        footer.add(statusLabel, BorderLayout.WEST);
        JButton refreshButton = new JButton("Refresh diagram");
        refreshButton.addActionListener(evt -> refreshDiagram());
        footer.add(refreshButton, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        try {
            Blackboard.getInstance().addPropertyChangeListener(this);
        } catch (Throwable ignored) {
            statusLabel.setText("Blackboard listener unavailable.");
        }

        refreshDiagram();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String name = evt.getPropertyName();
        if ("loading".equals(name) && evt.getNewValue() instanceof Boolean b) {
            loading = b;
            if (loading) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Loading repository data...");
                    canvas.setDiagram(null);
                });
            }
            return;
        }
        if ("squares".equals(name) || "selectedPrefix".equals(name)) {
            loading = false;
            SwingUtilities.invokeLater(this::refreshDiagram);
        }
    }

    private void refreshDiagram() {
        if (loading) return;

        List<Square> squares = snapshotSquares();
        if (squares.isEmpty()) {
            canvas.setDiagram(null);
            statusLabel.setText("Load a repository to visualize dependencies.");
            return;
        }

        String prefix = getSelectedPrefix();
        List<Square> filtered = filterByPrefix(squares, prefix);
        if (filtered.isEmpty()) {
            canvas.setDiagram(null);
            if (prefix == null || prefix.isBlank()) {
                statusLabel.setText("No Java sources available to render.");
            } else {
                statusLabel.setText("No Java sources inside \"" + prefix + "\".");
            }
            return;
        }

        DiagramModel model = buildModel(filtered);
        if (model == null) {
            canvas.setDiagram(null);
            statusLabel.setText("Could not build a diagram for the current selection.");
            return;
        }

        canvas.setDiagram(model.umlSource);
        String suffix = (prefix == null || prefix.isBlank()) ? "" : " | folder: " + prefix;
        statusLabel.setText(String.format("Diagram: %d files | %d links%s",
                model.nodeCount, model.edgeCount, suffix));
    }

    private static List<Square> snapshotSquares() {
        try {
            List<Square> list = Blackboard.getInstance().getSquares();
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            return new ArrayList<>(list);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private static String getSelectedPrefix() {
        try {
            String prefix = Blackboard.getInstance().getSelectedPrefix();
            return prefix == null ? "" : prefix;
        } catch (Throwable t) {
            return "";
        }
    }

    private static List<Square> filterByPrefix(List<Square> source, String prefix) {
        List<Square> filtered = new ArrayList<>();
        boolean hasPrefix = prefix != null && !prefix.isBlank();
        for (Square square : source) {
            String path = String.valueOf(square.getPath()).replace('\\', '/');
            if (!path.endsWith(".java")) continue;

            if (!hasPrefix || matchesPrefix(path, prefix)) {
                filtered.add(square);
            }
        }
        return filtered;
    }

    private static boolean matchesPrefix(String path, String prefix) {
        if (prefix == null || prefix.isBlank()) return true;
        String normalized = prefix.replace('\\', '/');
        return path.contains(normalized + "/") || path.endsWith("/" + normalized) || path.equals(normalized);
    }

    private static DiagramModel buildModel(List<Square> squares) {
        Map<String, Square> byName = new LinkedHashMap<>();
        for (Square s : squares) {
            String name = s.getSimpleName();
            if (name == null || name.isBlank()) continue;
            byName.put(name, s);
        }
        if (byName.isEmpty()) return null;

        Map<String, String> aliasByName = new LinkedHashMap<>();
        List<NodeDef> nodes = new ArrayList<>();

        int index = 1;
        for (Map.Entry<String, Square> entry : byName.entrySet()) {
            String simpleName = entry.getKey();
            String alias = "N" + index++;
            aliasByName.put(simpleName, alias);
            nodes.add(new NodeDef(simpleName, alias, classify(entry.getValue())));
        }

        List<RelationshipExtractor.Relationship> relationships = RelationshipExtractor.extract(squares);
        int externalIndex = 1;
        for (RelationshipExtractor.Relationship rel : relationships) {
            if (!aliasByName.containsKey(rel.getTo())) {
                String alias = "X" + externalIndex++;
                aliasByName.put(rel.getTo(), alias);
                nodes.add(new NodeDef(rel.getTo(), alias, NodeStyle.EXTERNAL));
            }
        }

        StringBuilder uml = new StringBuilder();
        uml.append("@startuml\n");
        uml.append("!pragma layout smetana\n");
        uml.append("skinparam backgroundColor #f6f4f0\n");
        uml.append("skinparam ArrowColor #444444\n");
        uml.append("skinparam ArrowThickness 1.2\n");
        uml.append("skinparam defaultFontName Arial\n");
        uml.append("skinparam Shadowing false\n");
        uml.append("skinparam linetype ortho\n");

        for (NodeDef def : nodes) {
            String label = def.style.symbol.isEmpty() ? def.name : def.style.symbol + " " + def.name;
            uml.append(String.format("rectangle \"%s\" as %s %s%n",
                    escape(label), def.alias, def.style.colorDirective));
        }

        int edges = 0;
        for (RelationshipExtractor.Relationship rel : relationships) {
            String fromAlias = aliasByName.get(rel.getFrom());
            String toAlias = aliasByName.get(rel.getTo());
            if (fromAlias == null || toAlias == null) continue;
            uml.append(rel.toPlantUml(fromAlias, toAlias)).append('\n');
            edges++;
        }

        uml.append("@enduml\n");
        return new DiagramModel(uml.toString(), nodes.size(), edges);
    }

    private static NodeStyle classify(Square square) {
        String name = square.getSimpleName();
        String code = square.getSource();

        if (SourceUtils.declaresInterface(name, code)) {
            return NodeStyle.INTERFACE;
        }
        if (SourceUtils.declaresAbstractClass(name, code)) {
            return NodeStyle.ABSTRACT_CLASS;
        }
        return NodeStyle.CLASS;
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("\"", "\\\"");
    }

    private static final class DiagramModel {
        final String umlSource;
        final int nodeCount;
        final int edgeCount;
        DiagramModel(String source, int nodes, int edges) {
            this.umlSource = source;
            this.nodeCount = nodes;
            this.edgeCount = edges;
        }
    }

    private static final class NodeDef {
        final String name;
        final String alias;
        final NodeStyle style;
        NodeDef(String name, String alias, NodeStyle style) {
            this.name = name;
            this.alias = alias;
            this.style = style;
        }
    }

    private enum NodeStyle {
        CLASS("C", "#dcead3"),
        ABSTRACT_CLASS("A", "#cfe7f7"),
        INTERFACE("I", "#dcd0f7"),
        EXTERNAL("ext", "#e6e6e6");

        final String symbol;
        final String colorDirective;

        NodeStyle(String symbol, String color) {
            this.symbol = symbol == null ? "" : symbol;
            this.colorDirective = color == null || color.isBlank() ? "" : "#" + color.replace("#", "");
        }
    }

    private static final class DiagramCanvas extends JPanel {
        private BufferedImage image;
        private String error;

        void setDiagram(String umlSource) {
            if (umlSource == null || umlSource.isBlank()) {
                image = null;
                error = null;
                repaint();
                return;
            }
            try {
                SourceStringReader reader = new SourceStringReader(umlSource);
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                reader.outputImage(os, 0, new FileFormatOption(FileFormat.PNG));
                byte[] data = os.toByteArray();
                os.close();
                image = ImageIO.read(new ByteArrayInputStream(data));
                error = null;
            } catch (IOException e) {
                image = null;
                error = e.getMessage();
            }
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                int x = Math.max(0, (getWidth() - image.getWidth()) / 2);
                int y = Math.max(0, (getHeight() - image.getHeight()) / 2);
                g.drawImage(image, x, y, this);
            } else {
                g.setColor(new Color(110, 110, 110));
                g.setFont(g.getFont().deriveFont(Font.PLAIN, 13f));
                String message = (error == null) ? "No diagram data yet." : "Unable to render diagram: " + error;
                FontMetrics fm = g.getFontMetrics();
                int textWidth = fm.stringWidth(message);
                int x = Math.max(10, (getWidth() - textWidth) / 2);
                int y = Math.max(20, getHeight() / 2);
                g.drawString(message, x, y);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            if (image != null) {
                return new Dimension(image.getWidth(), image.getHeight());
            }
            return new Dimension(640, 420);
        }
    }
}
