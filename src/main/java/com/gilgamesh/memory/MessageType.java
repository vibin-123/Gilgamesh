package com.gilgamesh.memory;

/**
 * MessageType — Classifies the nature of a user message relative to conversation history.
 *
 * NLP Concept: Discourse Analysis / Turn Classification
 * -----------------------------------------------------
 * Understanding whether a message continues an existing topic or introduces a new one
 * is fundamental to multi-turn dialog. This classification drives how context is applied
 * during reference resolution and intent classification.
 */
public enum MessageType {

    /**
     * Self-contained utterance with no dependency on prior context.
     * Example: "What is the weather today?"
     */
    STANDALONE,

    /**
     * Direct continuation of the previous turn, often containing pronouns or
     * elliptical phrases that depend on prior context.
     * Example: "Who created it?" (after discussing Java)
     */
    FOLLOW_UP,

    /**
     * Incomplete or elliptical statement that requires active context to be meaningful.
     * Example: "and its advantages?" (continuing a discussion)
     */
    CONTEXT_DEPENDENT,

    /**
     * Introduction of a new entity or topic distinct from the current conversation flow.
     * Example: "Tell me about Python" (after discussing Java)
     */
    TOPIC_CHANGE
}
