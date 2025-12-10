package com.beginsecure.panels;

import com.beginsecure.Blackboard;
import com.beginsecure.Square;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Visual heat-map grid displaying repository files based on line counts and load status.
 * @author @NickGottwald
 * @author @Muska Said
 */
public class GridPanel extends JPanel implements PropertyChangeListener {

    private boolean loading = false;
    private boolean ready = false;

    public GridPanel() {
        setBackground(Color.WHITE);
        Blackboard.getInstance().addPropertyChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String name = evt.getPropertyName();
        if ("loading".equals(name) && evt.getNewValue() instanceof Boolean b) {
            loading = b;
            if (loading) ready = false;
            repaint();
            return;
        }
        if ("squares".equals(name)) {
            ready = true;
            loading = false;
            repaint();
        }
    }

    private void drawLoading(Graphics g) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.drawString("Loading...", getWidth() / 2 - 30, getHeight() / 2);
    }

    private Color calculateColor(int lines) {
        if (lines < 50) return new Color(180, 240, 180);
        else if (lines < 200) return new Color(255, 245, 150);
        else return new Color(240, 140, 140);
    }

    private void drawSquares(Graphics g) {
        List<Square> squares = Blackboard.getInstance().getSquares();

        if (squares == null || squares.isEmpty()) {
            return;
        }

        int cols = (int) Math.ceil(Math.sqrt(squares.size()));
        int rows = (int) Math.ceil((double) squares.size() / cols);

        int squareWidth = getWidth() / cols;
        int squareHeight = getHeight() / rows;

        for (int i = 0; i < squares.size(); i++) {
            Square square = squares.get(i);

            int row = i / cols;
            int col = i % cols;

            int x = col * squareWidth;
            int y = row * squareHeight;

            Color color = calculateColor(square.getLinesOfCode());
            g.setColor(color);
            g.fillRect(x, y, squareWidth - 2, squareHeight - 2);

            g.setColor(Color.BLACK);
            g.drawRect(x, y, squareWidth - 2, squareHeight - 2);

            g.setFont(new Font("Arial", Font.PLAIN, 6));
            String text = square.getName() + " (" + square.getLinesOfCode() + ")";
            g.drawString(text, x + 5, y + 15);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (loading) {
            drawLoading(g);
        } else if (ready) {
            drawSquares(g);
        }
    }
}
