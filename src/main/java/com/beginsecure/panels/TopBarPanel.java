package com.beginsecure.panels;

import com.beginsecure.handlers.TheNanny;

import javax.swing.*;
import java.awt.*;

/**
 * Toolbar hosting the repository URL field and trigger button for data loading.
 * @author @NickGottwald
 * @author @Muska Said
 */
public final class TopBarPanel extends JPanel {
    private final JTextField urlField = new JTextField();
    private final JButton okButton = new JButton("OK");

    public TopBarPanel() {
        super(new BorderLayout(8, 0));
        add(urlField, BorderLayout.CENTER);
        add(okButton, BorderLayout.EAST);

        // Reuse your existing controller
        okButton.addActionListener(new TheNanny(urlField));

        // (Optional) enter key submits
        urlField.addActionListener(e -> okButton.doClick());
    }
}

