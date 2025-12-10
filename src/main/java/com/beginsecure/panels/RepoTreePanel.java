package com.beginsecure.panels;

import com.beginsecure.Blackboard;
import com.beginsecure.Square;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Navigable tree that mirrors the repository structure and publishes selection changes.
 * @author @NickGottwald
 * @author @Muska Said
 */
public final class RepoTreePanel extends JPanel implements PropertyChangeListener {

    private final DefaultTreeModel model;
    private final JTree tree;

    public RepoTreePanel() {
        super(new BorderLayout());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("repo");
        model = new DefaultTreeModel(root);
        tree = new JTree(model);
        add(new JScrollPane(tree), BorderLayout.CENTER);

        // Publish selected folder (best-effort: works if Blackboard#setSelectedPrefix exists)
        tree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override public void valueChanged(TreeSelectionEvent e) {
                if (e.getPath() == null) return;
                Object[] parts = e.getPath().getPath();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    String p = String.valueOf(parts[i]);
                    if ("repo".equals(p)) continue;        // ignore virtual root
                    if (sb.length() > 0) sb.append('/');
                    sb.append(p);
                }
                String raw = sb.toString();
                String prefix = raw.endsWith(".java") && raw.contains("/") ? raw.substring(0, raw.lastIndexOf('/')) : raw;
                safeSetSelectedPrefix(prefix);
            }
        });

        // Rebuild tree when squares load/change (best-effort)
        try { Blackboard.getInstance().addPropertyChangeListener(this); } catch (Throwable ignored) {}
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String n = evt.getPropertyName();
        if ("squares".equals(n) || evt.getNewValue() != null) {
            refreshFromBlackboard();
        }
    }

    private void refreshFromBlackboard() {
        List<Square> squares;
        try {
            squares = Blackboard.getInstance().getSquares();
        } catch (Throwable t) {
            return;
        }
        if (squares == null || squares.isEmpty()) return;
        rebuildTree(squares);
    }

    private void rebuildTree(List<Square> squares) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("repo");

        for (Square s : squares) {
            String path = String.valueOf(s.getPath()).replace("\\", "/");
            if (!path.endsWith(".java")) continue;

            String[] parts = path.split("/");
            DefaultMutableTreeNode curr = root;
            for (String p : parts) {
                if (p.isEmpty()) continue;
                curr = getOrCreateChild(curr, p);
            }
        }

        model.setRoot(root);
        expandAllRows();
    }

    private DefaultMutableTreeNode getOrCreateChild(DefaultMutableTreeNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode c = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (name.equals(c.getUserObject())) return c;
        }
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(name);
        model.insertNodeInto(n, parent, parent.getChildCount());
        return n;
    }

    private void expandAllRows() {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private void safeSetSelectedPrefix(String prefix) {
        try {
            Object bb = Blackboard.getInstance();
            Method m = bb.getClass().getMethod("setSelectedPrefix", String.class);
            m.invoke(bb, prefix);
        } catch (Throwable ignored) {
            // If Blackboard doesn't have setSelectedPrefix yet, just ignore.
        }
    }
}


