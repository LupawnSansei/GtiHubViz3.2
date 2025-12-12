package com.beginsecure;



import javax.swing.*;

/**
 * Launches the GitHubViz desktop application.
 * @author @NickGottwald
 * @author @Muska Said
 */
public final class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AppFrame().setVisible(true));
    }
}
