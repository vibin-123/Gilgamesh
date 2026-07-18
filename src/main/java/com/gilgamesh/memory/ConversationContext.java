package com.gilgamesh.memory;

import com.gilgamesh.model.Intent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ConversationContext — Stores and manages short-term conversation memory.
 *
 * It retains:
 *   - The last detected intent (Intent object or null if fallback)
 *   - The last user message (String)
 *   - The last bot response (String)
 *   - The current conversation topic (derived from the intent, or preserved if not determinable)
 *   - A configurable sliding history window of recent turns
 *   - The timestamp of the last interaction
 *   - Active turn count since the last reset or expiration
 *
 * This context is configured to automatically expire after either:
 *   - A maximum number of turns (maxSessionTurns)
 *   - A duration of inactivity (inactivityTimeoutMs)
 *
 * The implementation is thread-safe.
 */
public class ConversationContext {

    // Default Configuration
    public static final int DEFAULT_MAX_HISTORY_SIZE = 10;
    public static final int DEFAULT_MAX_SESSION_TURNS = 20;
    public static final long DEFAULT_INACTIVITY_TIMEOUT_MS = 300_000; // 5 minutes

    private int maxHistorySize;
    private int maxSessionTurns;
    private long inactivityTimeoutMs;

    // State Variables
    private Intent lastDetectedIntent;
    private String lastUserMessage;
    private String lastBotResponse;
    private String currentTopic;
    private final List<ConversationTurn> history;
    private long lastInteractionTimestamp;
    private int turnCount;

    /**
     * Initializes a new ConversationContext with default configuration.
     */
    public ConversationContext() {
        this(DEFAULT_MAX_HISTORY_SIZE, DEFAULT_MAX_SESSION_TURNS, DEFAULT_INACTIVITY_TIMEOUT_MS);
    }

    /**
     * Initializes a new ConversationContext with a custom configuration.
     *
     * @param maxHistorySize      Maximum number of turns to keep in history
     * @param maxSessionTurns     Maximum turns before the context expires
     * @param inactivityTimeoutMs Timeout in milliseconds before context expires
     */
    public ConversationContext(int maxHistorySize, int maxSessionTurns, long inactivityTimeoutMs) {
        this.maxHistorySize = maxHistorySize;
        this.maxSessionTurns = maxSessionTurns;
        this.inactivityTimeoutMs = inactivityTimeoutMs;
        this.history = new ArrayList<>();
        resetState();
    }

    /**
     * Updates the context with the latest interaction.
     * Checks and applies expiration rules before performing the update.
     *
     * @param userMessage    The message sent by the user
     * @param botResponse    The response returned by the bot
     * @param detectedIntent The intent matched by the classifier (can be null)
     */
    public synchronized void update(String userMessage, String botResponse, Intent detectedIntent) {
        // Clear expired context first using the timestamp before this update is applied
        clearExpiredContext();

        this.lastUserMessage = userMessage;
        this.lastBotResponse = botResponse;
        this.lastDetectedIntent = detectedIntent;

        // Current topic logic:
        // If an intent is matched, set the topic to the intent tag.
        // Otherwise, preserve the current topic.
        if (detectedIntent != null) {
            this.currentTopic = detectedIntent.getTag();
        }

        ConversationTurn turn = new ConversationTurn(userMessage, botResponse, detectedIntent);
        this.history.add(turn);

        // Keep history size bounded (sliding window)
        while (this.history.size() > maxHistorySize) {
            this.history.remove(0);
        }

        this.lastInteractionTimestamp = System.currentTimeMillis();
        this.turnCount++;

        System.out.println("[ConversationContext] Context updated. Turn: " + turnCount + "/" 
                + (maxSessionTurns > 0 ? maxSessionTurns : "∞") 
                + ", Topic: " + currentTopic + ", History Size: " + history.size());
    }

    /**
     * Retrieves the current conversation context.
     * Internally checks for expiration.
     *
     * @return This context instance
     */
    public synchronized ConversationContext getCurrentContext() {
        clearExpiredContext();
        return this;
    }

    /**
     * Clears the context if it has expired due to turn limits or inactivity.
     *
     * @return true if the context was expired and cleared, false otherwise
     */
    public synchronized boolean clearExpiredContext() {
        if (isExpired()) {
            System.out.println("[ConversationContext] Context expired due to " 
                    + (isTurnLimitExpired() ? "turn limit." : "inactivity."));
            reset();
            return true;
        }
        return false;
    }

    /**
     * Resets the conversation context to its initial empty state.
     */
    public synchronized void reset() {
        resetState();
        System.out.println("[ConversationContext] Memory cleared.");
    }

    private void resetState() {
        this.lastDetectedIntent = null;
        this.lastUserMessage = null;
        this.lastBotResponse = null;
        this.currentTopic = "unknown";
        this.history.clear();
        this.lastInteractionTimestamp = System.currentTimeMillis();
        this.turnCount = 0;
    }

    /**
     * Checks if the context is expired.
     */
    private boolean isExpired() {
        if (turnCount == 0) {
            return false;
        }
        return isTurnLimitExpired() || isInactivityExpired();
    }

    private boolean isTurnLimitExpired() {
        return maxSessionTurns > 0 && turnCount >= maxSessionTurns;
    }

    private boolean isInactivityExpired() {
        if (inactivityTimeoutMs > 0) {
            long idleTime = System.currentTimeMillis() - lastInteractionTimestamp;
            return idleTime > inactivityTimeoutMs;
        }
        return false;
    }

    // --- Getters & Setters ---

    public synchronized Intent getLastDetectedIntent() {
        clearExpiredContext();
        return lastDetectedIntent;
    }

    public synchronized String getLastUserMessage() {
        clearExpiredContext();
        return lastUserMessage;
    }

    public synchronized String getLastBotResponse() {
        clearExpiredContext();
        return lastBotResponse;
    }

    public synchronized String getCurrentTopic() {
        clearExpiredContext();
        return currentTopic;
    }

    public synchronized List<ConversationTurn> getHistory() {
        clearExpiredContext();
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized long getLastInteractionTimestamp() {
        return lastInteractionTimestamp;
    }

    public synchronized int getTurnCount() {
        clearExpiredContext();
        return turnCount;
    }

    public int getMaxHistorySize() {
        return maxHistorySize;
    }

    public synchronized void setMaxHistorySize(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    public int getMaxSessionTurns() {
        return maxSessionTurns;
    }

    public synchronized void setMaxSessionTurns(int maxSessionTurns) {
        this.maxSessionTurns = maxSessionTurns;
    }

    public long getInactivityTimeoutMs() {
        return inactivityTimeoutMs;
    }

    public synchronized void setInactivityTimeoutMs(long inactivityTimeoutMs) {
        this.inactivityTimeoutMs = inactivityTimeoutMs;
    }
}
