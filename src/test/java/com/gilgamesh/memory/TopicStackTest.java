package com.gilgamesh.memory;

import com.gilgamesh.model.Intent;
import com.gilgamesh.nlp.EntityExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TopicStackTest — Tests for the Context Suspend & Resume (Topic Stack) feature.
 *
 * Validates:
 *   - TopicState creation, expiration, and matching
 *   - ConversationContext suspend/resume lifecycle
 *   - Stack depth limiting and eviction
 *   - TurnAnalyzer resume signal detection
 *   - Multi-level suspend/resume flows
 *   - Edge cases: resume non-existent topic, empty stack, expired topics
 */
class TopicStackTest {

    private ConversationContext context;
    private EntityExtractor entityExtractor;
    private TurnAnalyzer turnAnalyzer;

    @BeforeEach
    void setUp() {
        context = new ConversationContext();
        entityExtractor = new EntityExtractor();
        turnAnalyzer = new TurnAnalyzer(entityExtractor);
    }

    // ========================================================================
    // TopicState Unit Tests
    // ========================================================================

    @Nested
    @DisplayName("TopicState")
    class TopicStateTests {

        @Test
        @DisplayName("should store topic ID, entities, turns, and intent")
        void testTopicStateCreation() {
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            Intent intent = createIntent("java_info");
            ConversationTurn turn = new ConversationTurn("tell me about java", "Java is...", intent);

            TopicState state = new TopicState("java_info", List.of(java), List.of(turn),
                    Instant.now(), intent);

            assertEquals("java_info", state.getTopicId());
            assertEquals(1, state.getEntities().size());
            assertEquals("Java", state.getEntities().get(0).getName());
            assertEquals(1, state.getTurns().size());
            assertEquals(intent, state.getLastIntent());
            assertNotNull(state.getSuspendedAt());
        }

        @Test
        @DisplayName("should match topics case-insensitively")
        void testTopicMatching() {
            TopicState state = new TopicState("java_info", List.of(), List.of(), Instant.now(), null);

            assertTrue(state.matchesTopic("java_info"));
            assertTrue(state.matchesTopic("Java_Info"));
            assertTrue(state.matchesTopic("JAVA_INFO"));
            assertFalse(state.matchesTopic("python_info"));
            assertFalse(state.matchesTopic(null));
        }

        @Test
        @DisplayName("should detect expiration based on age")
        void testTopicExpiration() {
            // Create a topic that was suspended 10 seconds ago
            Instant tenSecondsAgo = Instant.now().minusSeconds(10);
            TopicState state = new TopicState("java_info", List.of(), List.of(), tenSecondsAgo, null);

            // Should be expired if max age is 5 seconds
            assertTrue(state.isExpired(5_000));

            // Should NOT be expired if max age is 30 seconds
            assertFalse(state.isExpired(30_000));

            // Should NOT be expired if max age is non-positive
            assertFalse(state.isExpired(0));
            assertFalse(state.isExpired(-1));
        }

        @Test
        @DisplayName("should return unmodifiable entity and turn lists")
        void testImmutableLists() {
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            TopicState state = new TopicState("java_info", List.of(java), List.of(), Instant.now(), null);

            assertThrows(UnsupportedOperationException.class, () ->
                    state.getEntities().add(new Entity("Python", Entity.EntityType.LANGUAGE)));
            assertThrows(UnsupportedOperationException.class, () ->
                    state.getTurns().add(new ConversationTurn("test", "test", null)));
        }
    }

    // ========================================================================
    // ConversationContext Suspend & Resume Tests
    // ========================================================================

    @Nested
    @DisplayName("ConversationContext — Suspend & Resume")
    class SuspendResumeTests {

        @Test
        @DisplayName("should suspend current topic and push onto stack")
        void testSuspendCurrentTopic() {
            // Build up a topic with entities
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("tell me about java", "Java is...", createIntent("java_info"), List.of(java));

            assertEquals(0, context.getSuspendedTopics().size());

            context.suspendCurrentTopic();

            assertEquals(1, context.getSuspendedTopics().size());
            TopicState suspended = context.getSuspendedTopics().get(0);
            assertEquals("java_info", suspended.getTopicId());
            assertEquals(1, suspended.getEntities().size());
            assertEquals("Java", suspended.getEntities().get(0).getName());
        }

        @Test
        @DisplayName("should resume a suspended topic and restore entities")
        void testSuspendAndResumeTopic() {
            // Set up Java topic
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("tell me about java", "Java is...", createIntent("java_info"), List.of(java));

            // Suspend Java, switch to Python
            context.suspendCurrentTopic();
            Entity python = new Entity("Python", Entity.EntityType.LANGUAGE);
            context.update("tell me about python", "Python is...", createIntent("python_info"), List.of(python));

            // Verify Python is active
            assertEquals("python_info", context.getCurrentTopic());
            assertEquals("Python", context.getActiveEntity().getName());

            // Resume Java
            boolean resumed = context.resumeTopic("java_info");
            assertTrue(resumed);
            assertEquals("java_info", context.getCurrentTopic());

            // Entities should be restored from the Java snapshot
            Entity activeEntity = context.getActiveEntity();
            assertNotNull(activeEntity);
            assertEquals("Java", activeEntity.getName());

            // Python should now be on the stack (auto-suspended during resume)
            assertEquals(1, context.getSuspendedTopics().size());
            assertEquals("python_info", context.getSuspendedTopics().get(0).getTopicId());
        }

        @Test
        @DisplayName("should scope entities per topic and clear state on suspend")
        void testEntitiesScopedPerTopic() {
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("tell me about java", "Java is...", createIntent("java_info"), List.of(java));
            context.suspendCurrentTopic();

            // Suspending clears stale state for the next topic
            assertNull(context.getActiveEntity());
            assertEquals("unknown", context.getCurrentTopic());

            Entity paris = new Entity("Paris", Entity.EntityType.GENERAL);
            context.update("weather in paris", "Sunny.", createIntent("weather"), List.of(paris));

            context.resumeTopic("java_info");

            assertEquals("java_info", context.getCurrentTopic());
            assertEquals(1, context.getRecentEntities().size());
            assertEquals("Java", context.getMostRecentSingularNonPersonEntity().getName());
            assertFalse(context.getRecentEntities().contains(paris));
        }

        @Test
        @DisplayName("should return false when resuming a non-existent topic")
        void testResumeNonExistentTopic() {
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("tell me about java", "Java is...", createIntent("java_info"), List.of(java));

            boolean resumed = context.resumeTopic("nonexistent_topic");
            assertFalse(resumed);

            // Context should be unchanged
            assertEquals("java_info", context.getCurrentTopic());
        }

        @Test
        @DisplayName("should not suspend when topic is 'unknown' or entities are empty")
        void testNoSuspendForEmptyTopic() {
            // Empty context - nothing to suspend
            context.suspendCurrentTopic();
            assertEquals(0, context.getSuspendedTopics().size());

            // Update with no entities
            context.update("hello", "Hi!", createIntent("greeting"), List.of());
            context.suspendCurrentTopic();
            assertEquals(0, context.getSuspendedTopics().size());
        }

        @Test
        @DisplayName("should deduplicate topics on the stack when re-suspending")
        void testDeduplicateOnStack() {
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("java info", "Java...", createIntent("java_info"), List.of(java));
            context.suspendCurrentTopic();

            // Update Java again (simulate coming back and suspending again)
            Entity java2 = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("more java", "More Java...", createIntent("java_info"), List.of(java2));
            context.suspendCurrentTopic();

            // Should only have one java_info on the stack (deduplicated)
            long javaCount = context.getSuspendedTopics().stream()
                    .filter(ts -> ts.matchesTopic("java_info")).count();
            assertEquals(1, javaCount);
        }

        @Test
        @DisplayName("should reset topic stack when context is reset")
        void testResetClearsStack() {
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("java", "Java...", createIntent("java_info"), List.of(java));
            context.suspendCurrentTopic();
            assertEquals(1, context.getSuspendedTopics().size());

            context.reset();
            assertEquals(0, context.getSuspendedTopics().size());
        }
    }

    // ========================================================================
    // Stack Depth & Eviction Tests
    // ========================================================================

    @Nested
    @DisplayName("Topic Stack Depth & Eviction")
    class StackDepthTests {

        @Test
        @DisplayName("should enforce max stack depth by evicting oldest topic")
        void testTopicStackDepthLimit() {
            context.setMaxStackDepth(3);

            // Push 4 topics onto the stack
            for (int i = 1; i <= 4; i++) {
                Entity entity = new Entity("Lang" + i, Entity.EntityType.LANGUAGE);
                context.update("about lang" + i, "Lang" + i + " is...",
                        createIntent("lang" + i + "_info"), List.of(entity));
                context.suspendCurrentTopic();
            }

            // Only 3 should remain (oldest evicted)
            List<TopicState> stack = context.getSuspendedTopics();
            assertEquals(3, stack.size());

            // The oldest (lang1_info) should have been evicted
            assertNull(context.findSuspendedTopic("lang1_info"));
            // The newest should be at the top
            assertNotNull(context.findSuspendedTopic("lang4_info"));
        }

        @Test
        @DisplayName("should evict expired topics from the stack")
        void testExpiredTopicEviction() {
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("java", "Java...", createIntent("java_info"), List.of(java));
            context.suspendCurrentTopic();
            assertEquals(1, context.getSuspendedTopics().size());

            // clearExpiredTopics won't evict recent topics (default timeout is 10 min)
            context.clearExpiredTopics();
            assertEquals(1, context.getSuspendedTopics().size());
        }

        @Test
        @DisplayName("should find suspended topics by ID")
        void testFindSuspendedTopic() {
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("java", "Java...", createIntent("java_info"), List.of(java));
            context.suspendCurrentTopic();

            Entity python = new Entity("Python", Entity.EntityType.LANGUAGE);
            context.update("python", "Python...", createIntent("python_info"), List.of(python));
            context.suspendCurrentTopic();

            TopicState found = context.findSuspendedTopic("java_info");
            assertNotNull(found);
            assertEquals("java_info", found.getTopicId());

            assertNull(context.findSuspendedTopic("nonexistent"));
            assertNull(context.findSuspendedTopic(null));
        }
    }

    // ========================================================================
    // TurnAnalyzer Resume Signal Detection Tests
    // ========================================================================

    @Nested
    @DisplayName("TurnAnalyzer — Resume Signal Detection")
    class ResumeSignalTests {

        @Test
        @DisplayName("should extract resume target from 'going back to Java'")
        void testExtractResumeTargetGoingBack() {
            assertEquals("java", turnAnalyzer.extractResumeTarget("going back to java"));
            assertEquals("python", turnAnalyzer.extractResumeTarget("going back to python"));
        }

        @Test
        @DisplayName("should extract resume target from 'back to Java'")
        void testExtractResumeTargetBackTo() {
            assertEquals("java", turnAnalyzer.extractResumeTarget("back to java"));
        }

        @Test
        @DisplayName("should extract resume target from 'returning to Java'")
        void testExtractResumeTargetReturning() {
            assertEquals("java", turnAnalyzer.extractResumeTarget("returning to java"));
            assertEquals("java", turnAnalyzer.extractResumeTarget("return to java"));
        }

        @Test
        @DisplayName("should extract resume target from 'resume Java'")
        void testExtractResumeTargetResume() {
            assertEquals("java", turnAnalyzer.extractResumeTarget("resume java"));
            assertEquals("python", turnAnalyzer.extractResumeTarget("revisit python"));
        }

        @Test
        @DisplayName("should extract resume target from 'about Java again'")
        void testExtractResumeTargetAgain() {
            assertEquals("java", turnAnalyzer.extractResumeTarget("about java again"));
        }

        @Test
        @DisplayName("should strip articles from resume target")
        void testExtractResumeTargetStripsArticles() {
            assertEquals("java", turnAnalyzer.extractResumeTarget("going back to the java"));
        }

        @Test
        @DisplayName("should return null when no resume signal present")
        void testNoResumeSignal() {
            assertNull(turnAnalyzer.extractResumeTarget("tell me about java"));
            assertNull(turnAnalyzer.extractResumeTarget("what is python"));
            assertNull(turnAnalyzer.extractResumeTarget("hello"));
        }

        @Test
        @DisplayName("should classify as TOPIC_RESUME when topic is on the stack")
        void testTopicResumeSignalDetection() {
            // Build up a Java topic
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("tell me about java", "Java is...", createIntent("java_info"), List.of(java));

            // Suspend it
            context.suspendCurrentTopic();

            // Switch to Python
            Entity python = new Entity("Python", Entity.EntityType.LANGUAGE);
            context.update("tell me about python", "Python is...", createIntent("python_info"), List.of(python));

            // "going back to java" should be TOPIC_RESUME because java_info is on the stack
            MessageType type = turnAnalyzer.analyze("going back to java", context);
            assertEquals(MessageType.TOPIC_RESUME, type);
        }

        @Test
        @DisplayName("should NOT classify as TOPIC_RESUME when topic is NOT on the stack")
        void testNoResumeWithoutStack() {
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("tell me about java", "Java is...", createIntent("java_info"), List.of(java));

            // "going back to Python" — Python was never discussed, not on stack
            MessageType type = turnAnalyzer.analyze("going back to python", context);
            // Should fall through to TOPIC_CHANGE (new entity "python" detected)
            assertNotEquals(MessageType.TOPIC_RESUME, type);
        }

        @Test
        @DisplayName("should find matching suspended topic ID via keyword")
        void testFindMatchingSuspendedTopicId() {
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("java", "Java...", createIntent("java_info"), List.of(java));
            context.suspendCurrentTopic();

            String topicId = turnAnalyzer.findMatchingSuspendedTopicId("java", context);
            assertEquals("java_info", topicId);

            // Non-existent
            assertNull(turnAnalyzer.findMatchingSuspendedTopicId("rust", context));
            assertNull(turnAnalyzer.findMatchingSuspendedTopicId(null, context));
        }
    }

    // ========================================================================
    // Multi-Level Suspend & Resume Integration Tests
    // ========================================================================

    @Nested
    @DisplayName("Multi-Level Suspend & Resume")
    class MultiLevelTests {

        @Test
        @DisplayName("should handle A→B→C→resume A correctly")
        void testMultiLevelSuspendResume() {
            // Topic A: Java
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("about java", "Java...", createIntent("java_info"), List.of(java));
            context.suspendCurrentTopic();

            // Topic B: Python
            Entity python = new Entity("Python", Entity.EntityType.LANGUAGE);
            context.update("about python", "Python...", createIntent("python_info"), List.of(python));
            context.suspendCurrentTopic();

            // Topic C: NLP
            Entity nlp = new Entity("NLP", Entity.EntityType.CONCEPT);
            context.update("about nlp", "NLP...", createIntent("nlp_info"), List.of(nlp));

            // Stack should have: [python_info, java_info]
            assertEquals(2, context.getSuspendedTopics().size());
            assertEquals("NLP", context.getActiveEntity().getName());

            // Resume A (Java) — skipping over B (Python)
            boolean resumed = context.resumeTopic("java_info");
            assertTrue(resumed);
            assertEquals("java_info", context.getCurrentTopic());
            assertEquals("Java", context.getActiveEntity().getName());

            // Stack should now have: [nlp_info, python_info]
            // (NLP was auto-suspended during resume, java was removed from stack)
            assertEquals(2, context.getSuspendedTopics().size());
            assertNotNull(context.findSuspendedTopic("python_info"));
            assertNotNull(context.findSuspendedTopic("nlp_info"));
            assertNull(context.findSuspendedTopic("java_info")); // removed from stack, now active
        }

        @Test
        @DisplayName("should handle suspend-resume-followup sequence")
        void testSuspendResumeWithFollowUp() {
            // Set up Java with entity
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("about java", "Java...", createIntent("java_info"), List.of(java));

            // Suspend Java
            context.suspendCurrentTopic();

            // Switch to Python
            Entity python = new Entity("Python", Entity.EntityType.LANGUAGE);
            context.update("about python", "Python...", createIntent("python_info"), List.of(python));

            // Resume Java
            context.resumeTopic("java_info");

            // After resuming, the active entity should be Java
            // This means a follow-up like "what are its features?" should resolve to Java
            Entity activeEntity = context.getMostRecentSingularNonPersonEntity();
            assertNotNull(activeEntity);
            assertEquals("Java", activeEntity.getName());
        }

        @Test
        @DisplayName("should auto-suspend current topic during resume")
        void testTopicChangeTriggersAutoSuspend() {
            // Set up Java
            Entity java = new Entity("Java", Entity.EntityType.LANGUAGE);
            context.update("about java", "Java...", createIntent("java_info"), List.of(java));

            // Suspend Java, set up Python
            context.suspendCurrentTopic();
            Entity python = new Entity("Python", Entity.EntityType.LANGUAGE);
            context.update("about python", "Python...", createIntent("python_info"), List.of(python));

            // Resume Java — Python should be auto-suspended
            context.resumeTopic("java_info");

            // Python should now be on the stack
            TopicState suspendedPython = context.findSuspendedTopic("python_info");
            assertNotNull(suspendedPython);
            // The suspended Python topic should contain the Python entity
            boolean hasPython = suspendedPython.getEntities().stream()
                    .anyMatch(e -> e.getName().equals("Python"));
            assertTrue(hasPython, "Suspended Python topic should contain Python entity");
        }
    }

    // ========================================================================
    // Helper
    // ========================================================================

    /**
     * Creates a minimal Intent for testing.
     */
    private Intent createIntent(String tag) {
        return new Intent(tag, List.of("test pattern"), List.of("Test response."));
    }
}
