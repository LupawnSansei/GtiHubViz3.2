package com.beginsecure.panels;

import com.beginsecure.Blackboard;
import com.beginsecure.Square;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scatter-plot workspace that charts abstractness versus instability for selected files.
 * @author @NickGottwald
 * @author @Muska Said
 */
public final class MetricsPanel extends JPanel implements PropertyChangeListener {

    private final PlotPanel plot = new PlotPanel();

    public MetricsPanel() {
        super(new BorderLayout());
        add(plot, BorderLayout.CENTER);
        try { Blackboard.getInstance().addPropertyChangeListener(this); } catch (Throwable ignored) {}
        SwingUtilities.invokeLater(this::refresh);
    }

    @Override public void propertyChange(PropertyChangeEvent evt) {
        String n = evt.getPropertyName();
        if ("squares".equals(n) || "selectedPrefix".equals(n)) SwingUtilities.invokeLater(this::refresh);
    }

    private void refresh() {
        List<Square> squares = getSquaresSafe();
        String prefix = getSelectedPrefixSafe();

        List<PointData> pts = new ArrayList<>();
        for (Square square : squares) {
            if (square == null) continue;
            String path = String.valueOf(square.getPath()).replace('\\', '/');
            if (!path.endsWith(".java")) continue;
            if (!inSelectedFolder(path, prefix)) continue;

            double instability = clampMetric(square.getInstability());
            double abstractness = clampMetric(square.getAbstractness());
            pts.add(new PointData(square.getSimpleName(), instability, abstractness));
        }

        plot.setData(pts);
    }

    private static boolean inSelectedFolder(String path, String prefix) {
        if (prefix == null || prefix.isEmpty()) return true;
        String p = path.replace('\\','/');
        return p.contains(prefix + "/") || p.endsWith("/" + prefix) || p.equals(prefix);
    }

    private List<Square> getSquaresSafe() {
        try {
            List<Square> list = Blackboard.getInstance().getSquares();
            return (list != null) ? list : Collections.emptyList();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private String getSelectedPrefixSafe() {
        try {
            String prefix = Blackboard.getInstance().getSelectedPrefix();
            return prefix == null ? "" : prefix;
        } catch (Throwable t) {
            return "";
        }
    }

    private static double clampMetric(Double value) {
        if (value == null) return 0.0;
        if (value < 0) return 0.0;
        return value > 1 ? 1.0 : value;
    }

    // ---------- data + plot ----------

    private static final class PointData {
        final String name;
        final double instability;  // X in [0,1]
        final double abstractness; // Y in [0,1]
        PointData(String n, double i, double a) { name = n; instability = i; abstractness = a; }
    }

    private static final class PlotPanel extends JPanel {
        private List<PointData> data = Collections.emptyList();
        private PointData hover;

        PlotPanel() {
            setBackground(Color.WHITE);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    hover = hitTest(e.getX(), e.getY());
                    setToolTipText(hover == null ? null :
                            hover.name + " | I: " + fmt(hover.instability) + ", A: " + fmt(hover.abstractness));
                    repaint();
                }
            });
            setToolTipText("");
        }

        void setData(List<PointData> pts) {
            data = (pts == null) ? Collections.emptyList() : pts;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();

            int left = 40, right = 16, top = 22, bottom = 36;
            int pw = Math.max(1, w - left - right);
            int ph = Math.max(1, h - top - bottom);

            g2.setColor(new Color(240, 246, 246));
            g2.fillRect(left, top, pw, ph);
            g2.setColor(Color.WHITE);
            g2.fillOval(left - pw/3, top + ph - pw/3, pw/2, pw/2);
            g2.fillOval(left + pw - pw/3, top - pw/3, pw/2, pw/2);

            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f,6f}, 0f));
            g2.setColor(new Color(210, 210, 210));
            g2.drawLine(left, top, left + pw, top + ph);

            g2.setColor(new Color(90, 90, 90));
            g2.drawString("Painful", left + 8, top + ph - 12);
            g2.drawString("Useless", left + pw - 50, top + 16);
            g2.drawString("instability (I)", left + pw/2 - 34, h - 10);
            g2.rotate(-Math.PI/2);
            g2.drawString("abstractness (A)", -(top + ph/2 + 28), 14);
            g2.rotate(Math.PI/2);

            FontMetrics fm = g2.getFontMetrics();
            for (PointData p : data) {
                int x = left + (int) Math.round(p.instability * pw);
                int y = top  + ph - (int) Math.round(p.abstractness * ph);

                int hash = p.name.hashCode();
                x += (hash % 7) - 3;
                y += ((hash / 7) % 7) - 3;

                x = Math.max(left, Math.min(left + pw, x));
                y = Math.max(top,  Math.min(top + ph, y));

                int r = 10;
                boolean isHover = (hover == p);
                g2.setColor(isHover ? new Color(30, 30, 30) : new Color(40, 40, 40));
                g2.fillOval(x - r/2, y - r/2, r, r);

                String label = p.name;
                int tw = fm.stringWidth(label);
                g2.setColor(new Color(60, 60, 60));
                g2.drawString(label, x - tw/2, y - r - 4);
            }
            g2.dispose();
        }

        private PointData hitTest(int mx, int my) {
            int left = 40, right = 16, top = 22, bottom = 36;
            int pw = Math.max(1, getWidth() - left - right);
            int ph = Math.max(1, getHeight() - top - bottom);
            for (PointData p : data) {
                int x = left + (int) Math.round(p.instability * pw) + ((p.name.hashCode()%7)-3);
                int y = top  + ph - (int) Math.round(p.abstractness * ph) + (((p.name.hashCode()/7)%7)-3);
                int r = 10;
                Rectangle hit = new Rectangle(x - r/2 - 2, y - r/2 - 2, r + 4, r + 4);
                if (hit.contains(mx, my)) return p;
            }
            return null;
        }

        private static String fmt(double v) { return String.format("%.2f", v); }
    }
}