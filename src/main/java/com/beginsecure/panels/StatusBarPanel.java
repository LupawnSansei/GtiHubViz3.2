package com.beginsecure.panels;

import com.beginsecure.Blackboard;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;

/**
 * Status strip that listens to blackboard events to report progress and errors.
 * @author @NickGottwald
 * @author @Muska Said
 */
public final class StatusBarPanel extends JPanel implements PropertyChangeListener {
    private final JLabel label = new JLabel("status bar : problems, what is going on...");

    public StatusBarPanel() {
        super(new BorderLayout());
        add(label, BorderLayout.CENTER);
        try {
            Blackboard.getInstance().addPropertyChangeListener(this);
        } catch (Throwable ignored) { }
    }

    @Override public void propertyChange(PropertyChangeEvent evt) {
        var name = evt.getPropertyName();
        var val  = evt.getNewValue();

        if (val instanceof String s && ("statusMessage".equals(name) || "error".equals(name))) {
            label.setText(s);
            return;
        }
        if (val instanceof Boolean b && "loading".equals(name)) {
            label.setText(b ? "Loading..." : "Ready");
            return;
        }
        if ("squares".equals(name) && val instanceof Collection<?> c) {
            label.setText("Loaded " + c.size() + " files");
        }
    }
}