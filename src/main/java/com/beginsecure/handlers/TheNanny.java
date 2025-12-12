package com.beginsecure.handlers;

import com.beginsecure.Blackboard;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.regex.Pattern;

/**
 * Action listener that initiates repository loading when the user submits a URL.
 * @author @NickGottwald
 * @author @Muska Said
 */
public class TheNanny implements ActionListener {

    private static final Pattern GITHUB_URL =
            Pattern.compile("^https?://github\\.com/[^/]+/[^/]+(/.*)?$", Pattern.CASE_INSENSITIVE);

    private final JTextField urlField;

    public TheNanny(JTextField field) {
        this.urlField = field;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            Blackboard.getInstance().reportError("Enter a GitHub repository URL before loading.");
            return;
        }
        if (!GITHUB_URL.matcher(url).matches()) {
            Blackboard.getInstance().reportError("Provide a valid GitHub repository URL (https://github.com/owner/repo).");
            return;
        }
        Blackboard.getInstance().setLoading(true);
        Blackboard.getInstance().setStatusMessage("Loading " + url + "...");
        Blackboard.getInstance().loadFromUrl(url);
    }
}
