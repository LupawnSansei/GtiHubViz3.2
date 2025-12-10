package com.beginsecure;

import com.beginsecure.handlers.Delegate;

import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

/**
 * Application-wide message bus and shared repository of computed squares/metrics.
 * @author @NickGottwald
 * @author @Muska Said
 */
public class Blackboard extends PropertyChangeSupport {

    private static Blackboard instance;
    private Vector<Square> squares;
    private boolean ready = false;
    private boolean loading = false;
    private String selectedPrefix = "";
    private String lastRepositoryUrl = "";

    private Blackboard() {
        super(new Object());
        squares = new Vector<>();
    }

    public static synchronized Blackboard getInstance() {
        if (instance == null) {
            instance = new Blackboard();
        }
        return instance;
    }

    public synchronized void updateSquares(List<Square> newSquares) {
        List<Square> old = new ArrayList<>(squares);
        squares.clear();
        if (newSquares != null) {
            squares.addAll(newSquares);
        }
        ready = !squares.isEmpty();
        firePropertyChange("squares", old, Collections.unmodifiableList(new ArrayList<>(squares)));
    }

    public void setReady() {
        ready = true;
    }

    public synchronized void setLoading(boolean loading) {
        boolean old = this.loading;
        this.loading = loading;
        firePropertyChange("loading", old, this.loading);
    }

    public List<Square> getSquares() {
        return Collections.unmodifiableList(new ArrayList<>(squares));
    }

    public void clear() {
        updateSquares(Collections.emptyList());
        ready = false;
        if (loading) {
            setLoading(false);
        }
    }

    public void loadFromUrl(String url) {
        try {
            this.lastRepositoryUrl = (url == null) ? "" : url;
            Delegate delegate = new Delegate(url);
            Thread t = new Thread(delegate);
            t.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    public String getSelectedPrefix() {
        return selectedPrefix;
    }
    public void setSelectedPrefix(String prefix) {
        String old = this.selectedPrefix;
        this.selectedPrefix = (prefix == null) ? "" : prefix;
        firePropertyChange("selectedPrefix", old, this.selectedPrefix);
    }

    public String getLastRepositoryUrl() {
        return lastRepositoryUrl;
    }

    public void setStatusMessage(String message) {
        firePropertyChange("statusMessage", null, message);
    }

    public void reportError(String message) {
        firePropertyChange("error", null, message);
    }
}
