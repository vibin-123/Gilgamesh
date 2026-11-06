package com.gilgamesh.memory;

import com.gilgamesh.model.Intent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TopicState — An immutable snapshot of a conversation topic's state at the moment
 * it was suspended.
 *
 * NLP Concept: Topic Tracking / Dialogue State Management
 * -------------------------------------------------------
 * In multi-turn dialogue systems, users frequently switch between topics and later
 * return to previous ones. TopicState captures everything the system knew about a
 * topic — its entities, conversational turns, and last matched intent — so the
 * conversation can be seamlessly resumed without losing context.
 *
 * This is the core data structure behind the "suspend & resume" mechanism in
 * ConversationContext's topic stack.
 */
public class TopicState {

    private final String topicId;
    private final List<Entity> entities;
    private final List<ConversationTurn> turns;
    private final Instant suspendedAt;
    private final Intent lastIntent;

    /**
     * Creates a new TopicState snapshot.
     *
     * @param topicId     The topic identifier (typically the intent tag, e.g. "java_info")
     * @param entities    The entities that were active when this topic was suspended
     * @param turns       The conversation turns that belonged to this topic
     * @param suspendedAt The timestamp when this topic was suspended
     * @param lastIntent  The last intent matched while this topic was active
     */
    public TopicState(String topicId, List<Entity> entities, List<ConversationTurn> turns,
                      Instant suspendedAt, Intent lastIntent) {
        this.topicId = topicId;
        this.entities = entities != null ? new ArrayList<>(entities) : new ArrayList<>();
        this.turns = turns != null ? new ArrayList<>(turns) : new ArrayList<>();
        this.suspendedAt = suspendedAt;
        this.lastIntent = lastIntent;
    }

    /**
     * Returns the topic identifier.
     *
     * @return The topic ID string (e.g. "java_info", "python_info")
     */
    public String getTopicId() {
        return topicId;
    }

    /**
     * Returns the entities that were active when this topic was suspended.
     *
     * @return An unmodifiable list of entities
     */
    public List<Entity> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    /**
     * Returns the conversation turns that belonged to this topic.
     *
     * @return An unmodifiable list of conversation turns
     */
    public List<ConversationTurn> getTurns() {
        return Collections.unmodifiableList(turns);
    }

    /**
     * Returns the timestamp when this topic was suspended.
     *
     * @return The suspension instant
     */
    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    /**
     * Returns the last intent matched while this topic was active.
     *
     * @return The last intent, or null if none was matched
     */
    public Intent getLastIntent() {
        return lastIntent;
    }

    /**
     * Checks whether this suspended topic has expired based on a maximum age.
     * Expired topics should be evicted from the stack to prevent stale context
     * from being restored.
     *
     * @param maxAgeMs Maximum age in milliseconds before a suspended topic expires
     * @return true if the topic was suspended longer ago than maxAgeMs
     */
    public boolean isExpired(long maxAgeMs) {
        if (suspendedAt == null || maxAgeMs <= 0) {
            return false;
        }
        long elapsedMs = Instant.now().toEpochMilli() - suspendedAt.toEpochMilli();
        return elapsedMs > maxAgeMs;
    }

    /**
     * Checks if this topic state matches a given topic identifier.
     * Comparison is case-insensitive.
     *
     * @param otherTopicId The topic ID to match against
     * @return true if the topic IDs match (case-insensitive)
     */
    public boolean matchesTopic(String otherTopicId) {
        if (topicId == null || otherTopicId == null) {
            return false;
        }
        return topicId.equalsIgnoreCase(otherTopicId);
    }

    @Override
    public String toString() {
        return "TopicState{" +
                "topicId='" + topicId + '\'' +
                ", entities=" + entities.size() +
                ", turns=" + turns.size() +
                ", suspendedAt=" + suspendedAt +
                ", lastIntent=" + (lastIntent != null ? lastIntent.getTag() : "null") +
                '}';
    }
}
