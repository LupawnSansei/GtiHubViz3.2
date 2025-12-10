package com.beginsecure.panels;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Handles OpenAI chat completion HTTP requests and JSON parsing.
 */
final class OpenAIChatClient {
    private final String apiUrl;
    private final String model;
    private final int maxTokens;

    OpenAIChatClient(String apiUrl, String model, int maxTokens) {
        this.apiUrl = apiUrl;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    String complete(String apiKey, List<ChatMessage> history,
                    ChatMessage systemPrompt, ChatMessage context) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setDoOutput(true);

        JSONObject payload = buildPayload(history, systemPrompt, context);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        String body;
        try (InputStream stream = getResponseStream(connection, responseCode)) {
            body = readStream(stream);
        } finally {
            connection.disconnect();
        }

        if (responseCode < 200 || responseCode >= 300) {
            throw new IllegalStateException("OpenAI error (" + responseCode + "): " + summarize(body));
        }
        String content = extractContent(body);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("OpenAI returned an empty response.");
        }
        return content;
    }

    private JSONObject buildPayload(List<ChatMessage> history, ChatMessage systemPrompt, ChatMessage context) {
        JSONObject data = new JSONObject();
        data.put("model", model);
        data.put("temperature", 0.2);
        data.put("max_tokens", maxTokens);

        JSONArray messages = new JSONArray();
        messages.put(toJson(systemPrompt));
        if (context != null) {
            messages.put(toJson(context));
        }
        for (ChatMessage message : history) {
            messages.put(toJson(message));
        }
        data.put("messages", messages);
        return data;
    }

    private static JSONObject toJson(ChatMessage message) {
        return new JSONObject()
                .put("role", message.role())
                .put("content", message.content());
    }

    private static InputStream getResponseStream(HttpURLConnection connection, int code) throws IOException {
        InputStream stream = (code >= 200 && code < 300) ? connection.getInputStream() : connection.getErrorStream();
        return stream == null ? InputStream.nullInputStream() : stream;
    }

    private static String readStream(InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static String extractContent(String json) {
        JSONObject responseJson = new JSONObject(json);
        JSONArray choices = responseJson.getJSONArray("choices");
        if (choices.isEmpty()) {
            return null;
        }
        JSONObject first = choices.getJSONObject(0);
        JSONObject message = first.optJSONObject("message");
        if (message == null) {
            return first.optString("text", "").trim();
        }
        return message.optString("content", "").trim();
    }

    private static String summarize(String body) {
        if (body == null) return "";
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
    }
}
