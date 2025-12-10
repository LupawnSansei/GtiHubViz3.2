package com.beginsecure.panels;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides a simple Groq-backed chat client so users can ask questions about their repositories.
 * Authenticates exclusively via the groqkey environment variable.
 */
public final class GroqChatPanel extends JPanel {

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "llama3-70b-versatile";
    private static final int MAX_TOKENS = 600;
    private static final ChatMessage SYSTEM_PROMPT = new ChatMessage(
            "system",
            "You are a helpful repository assistant embedded inside a Java Swing application. "
                    + "Provide concise, actionable answers and include code snippets when needed.");

    private static final Pattern CONTENT_PATTERN =
            Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final JTextArea transcript = new JTextArea();
    private final JTextArea inputArea = new JTextArea(3, 25);
    private final JButton sendButton = new JButton("Send");
    private final JLabel statusLabel = new JLabel("Enter a prompt to start chatting with Groq.");

    private final List<ChatMessage> history = new ArrayList<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public GroqChatPanel() {
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
        JLabel label = new JLabel("Using groqkey environment variable for authentication.");
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
            statusLabel.setText("Set groqkey in your environment before chatting.");
            return;
        }

        appendTranscript("You", prompt);
        history.add(new ChatMessage("user", prompt));
        inputArea.setText("");
        setSendingState(true);
        statusLabel.setText("Contacting Groq...");

        new ChatWorker(apiKey, List.copyOf(history)).execute();
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
        String envValue = System.getenv("groqkey");
        return envValue == null ? "" : envValue.trim();
    }

    private final class ChatWorker extends SwingWorker<String, Void> {
        private final String apiKey;
        private final List<ChatMessage> snapshot;

        ChatWorker(String apiKey, List<ChatMessage> snapshot) {
            this.apiKey = apiKey;
            this.snapshot = snapshot;
        }

        @Override
        protected String doInBackground() throws Exception {
            return fetchCompletion(apiKey, snapshot);
        }

        @Override
        protected void done() {
            setSendingState(false);
            try {
                String reply = get();
                history.add(new ChatMessage("assistant", reply));
                appendTranscript("Groq", reply);
                statusLabel.setText("Response received.");
            } catch (Exception ex) {
                statusLabel.setText("Unable to reach Groq: " + ex.getMessage());
            }
        }
    }

    private String fetchCompletion(String apiKey, List<ChatMessage> snapshot) throws Exception {
        String payload = buildPayload(snapshot);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            String body = response.body();
            throw new IllegalStateException("Groq API error (" + response.statusCode() + "): " + summarize(body));
        }
        String content = extractContent(response.body());
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Groq returned an empty response.");
        }
        return content;
    }

    private static String buildPayload(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"")
                .append(DEFAULT_MODEL)
                .append("\",\"temperature\":0.2,\"max_tokens\":")
                .append(MAX_TOKENS)
                .append(",\"messages\":[");
        appendMessageJson(sb, SYSTEM_PROMPT);
        for (ChatMessage message : messages) {
            sb.append(',');
            appendMessageJson(sb, message);
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void appendMessageJson(StringBuilder sb, ChatMessage message) {
        sb.append("{\"role\":\"")
                .append(message.role())
                .append("\",\"content\":\"")
                .append(escapeJson(message.content()))
                .append("\"}");
    }

    private static String escapeJson(String text) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String extractContent(String json) {
        Matcher matcher = CONTENT_PATTERN.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return null;
    }

    private static String summarize(String body) {
        if (body == null) return "";
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
    }

    private static String unescapeJson(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                char next = input.charAt(++i);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 < input.length()) {
                            String hex = input.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException ignore) {
                                sb.append("\\u").append(hex);
                                i += 4;
                            }
                        }
                    }
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private record ChatMessage(String role, String content) {}
}
