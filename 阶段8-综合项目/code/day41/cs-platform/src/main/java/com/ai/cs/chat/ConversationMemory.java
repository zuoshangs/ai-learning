package com.ai.cs.chat;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding window conversation memory manager.
 */
@Service
public class ConversationMemory {

    private static final int DEFAULT_MAX_HISTORY = 10;

    private final ConcurrentHashMap<String, List<Map<String, Object>>> sessions;
    private final int maxHistory;

    /**
     * Creates a ConversationMemory with the default max history of 10.
     */
    public ConversationMemory() {
        this(DEFAULT_MAX_HISTORY);
    }

    /**
     * Creates a ConversationMemory with a configurable max history.
     *
     * @param maxHistory the maximum number of messages to keep per session
     */
    public ConversationMemory(int maxHistory) {
        this.sessions = new ConcurrentHashMap<>();
        this.maxHistory = maxHistory;
    }

    /**
     * Adds a message to the specified session. If the session does not exist, it is created.
     * After adding, the history is trimmed to the configured maxHistory.
     *
     * @param sessionId the session identifier; if null, a UUID-based one is auto-generated
     * @param role      the role of the message sender ("user" or "assistant")
     * @param content   the content of the message
     * @return the actual sessionId used (auto-generated if null was passed)
     */
    public String addMessage(String sessionId, String role, String content) {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }

        List<Map<String, Object>> history = sessions.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()));

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        message.put("timestamp", System.currentTimeMillis());

        history.add(message);

        // Trim to maxHistory (remove oldest messages)
        trimHistory(history);

        return sessionId;
    }

    /**
     * Returns the message history for the specified session.
     * Each message map contains "role" and "content" keys only (no timestamp).
     *
     * @param sessionId the session identifier
     * @return list of message maps, or an empty list if the session does not exist
     */
    public List<Map<String, Object>> getHistory(String sessionId) {
        List<Map<String, Object>> history = sessions.get(sessionId);
        if (history == null) {
            return Collections.emptyList();
        }

        synchronized (history) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> msg : history) {
                Map<String, Object> filtered = new LinkedHashMap<>();
                filtered.put("role", msg.get("role"));
                filtered.put("content", msg.get("content"));
                result.add(filtered);
            }
            return result;
        }
    }

    /**
     * Removes the specified session and its history.
     *
     * @param sessionId the session identifier to remove
     */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * Returns a set of all active session IDs.
     *
     * @return a set of session IDs (may be empty)
     */
    public Set<String> getAllSessionIds() {
        return sessions.keySet();
    }

    /**
     * Trims the history list to the configured maxHistory by removing the oldest messages.
     *
     * @param history the list to trim
     */
    private void trimHistory(List<Map<String, Object>> history) {
        synchronized (history) {
            while (history.size() > maxHistory) {
                history.remove(0);
            }
        }
    }
}
