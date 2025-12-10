package com.beginsecure.panels;

/**
 * Minimal chat message descriptor compatible with OpenAI chat APIs.
 */
record ChatMessage(String role, String content) {}
