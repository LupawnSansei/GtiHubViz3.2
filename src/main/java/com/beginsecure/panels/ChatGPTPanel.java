package com.beginsecure.panels;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Swing panel that provides an OpenAI-backed chat experience for asking
 * questions about the currently loaded repository.
 */
public final class ChatGPTPanel extends JPanel {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final int MAX_TOKENS = 600;
    private static final ChatMessage SYSTEM_PROMPT = new ChatMessage(
            "system",
            "You are a helpful repository assistant embedded inside a Java Swing application. "
                    + "Use the repository summaries provided to ground your responses, reference diagrams/metrics when helpful, "
                    + "and keep every answer to four sentences or fewer.");

    private final JTextArea transcript = new JTextArea();
    private final JTextArea inputArea = new JTextArea(3, 25);
    private final JButton sendButton = new JButton("Send");
    private final JLabel statusLabel = new JLabel("Enter a prompt to start chatting with OpenAI.");

    private final List<ChatMessage> history = new ArrayList<>();
    private final OpenAIChatClient client = new OpenAIChatClient(API_URL, DEFAULT_MODEL, MAX_TOKENS);
    private final RepositoryContextBuilder contextBuilder = new RepositoryContextBuilder();

    public ChatGPTPanel() {
        super(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(Color.WHITE);

        configureTranscript();
        configureInputZone();

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setOpaque(false);
        content.add(buildEnvNotice(), BorderLayout.NORTH);
        content.add(new JScrollPane(transcript), BorderLayout.CENTER);
        content.add(buildInputRow(), BorderLayout.SOUTH);

        add(content, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void configureTranscript() {
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        transcript.setText("AI Assistant ready. Ask about architecture, code quality, or anything else.\n\n");
    }

    private void configureInputZone() {
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insert-newline");
        inputArea.getActionMap().put("insert-newline", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputArea.append("\n");
            }
        });
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send-message");
        inputArea.getActionMap().put("send-message", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                triggerSend();
            }
        });
        sendButton.addActionListener(evt -> triggerSend());
    }

    private JComponent buildEnvNotice() {
        JLabel label = new JLabel("Using openaikey environment variable for authentication.");
        label.setFont(label.getFont().deriveFont(Font.ITALIC, 11f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        return label;
    }

    private JPanel buildInputRow() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.add(new JScrollPane(inputArea), BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);
        return panel;
    }

    private void triggerSend() {
        String prompt = inputArea.getText().trim();
        if (prompt.isEmpty()) {
            statusLabel.setText("Type a question before sending.");
            return;
        }
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            statusLabel.setText("Set openaikey in your environment before chatting.");
            return;
        }

        appendTranscript("You", prompt);
        history.add(new ChatMessage("user", prompt));
        inputArea.setText("");
        setSendingState(true);
        statusLabel.setText("Contacting OpenAI...");

        ChatMessage context = contextBuilder.buildContextMessage();
        new ChatWorker(apiKey, List.copyOf(history), context).execute();
    }

    private void appendTranscript(String speaker, String text) {
        transcript.append(speaker + ":\n");
        transcript.append(text);
        transcript.append("\n\n");
        transcript.setCaretPosition(transcript.getDocument().getLength());
    }

    private void setSendingState(boolean sending) {
        sendButton.setEnabled(!sending);
        inputArea.setEnabled(!sending);
    }

    private String resolveApiKey() {
        String envValue = System.getenv("openaikey");
        return envValue == null ? "" : envValue.trim();
    }

    private final class ChatWorker extends SwingWorker<String, Void> {
        private final String apiKey;
        private final List<ChatMessage> snapshot;
        private final ChatMessage context;

        ChatWorker(String apiKey, List<ChatMessage> snapshot, ChatMessage context) {
            this.apiKey = apiKey;
            this.snapshot = snapshot;
            this.context = context;
        }

        @Override
        protected String doInBackground() throws Exception {
            return client.complete(apiKey, snapshot, SYSTEM_PROMPT, context);
        }

        @Override
        protected void done() {
            setSendingState(false);
            try {
                String reply = get();
                history.add(new ChatMessage("assistant", reply));
                appendTranscript("OpenAI", reply);
                statusLabel.setText("Response received.");
            } catch (Exception ex) {
                statusLabel.setText("Unable to reach OpenAI: " + ex.getMessage());
            }
        }
    }
}
