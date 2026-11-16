package com.gilgamesh.memory;

import com.gilgamesh.model.Intent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * SessionSnapshot — A plain-data, JSON-friendly copy of a {@link ConversationContext}.
 *
 * Intents are stored by tag and instants as epoch millis, so Gson needs no custom adapters
 * and the saved file stays easy to read (and to serve to a UI later).
 */
public class SessionSnapshot {

    /** Version 2: topics are subjects ("Java") instead of intent tags; adds pendingQuestion. */
    public static final int CURRENT_VERSION = 2;

    public int version = CURRENT_VERSION;
    public long savedAtMillis;
    public String currentTopic;
    public String lastUserMessage;
    public String lastBotResponse;
    public String lastIntentTag;
    /** The user's name, or null if not given. */
    public String userName;
    public List<EntityDto> recentEntities = new ArrayList<>();
    public List<TurnDto> history = new ArrayList<>();
    /** Suspended topics, most recent first. */
    public List<TopicDto> topicStack = new ArrayList<>();
    /** The question the bot was waiting on, or null. */
    public PendingDto pendingQuestion;

    public static class PendingDto {
        public String intentTag;
        public String slot;
        public String queryTemplate;
        public List<String> options = new ArrayList<>();
    }

    public static class EntityDto {
        public String name;
        public String type;
        public String number;

        static EntityDto from(Entity entity) {
            EntityDto dto = new EntityDto();
            dto.name = entity.getName();
            dto.type = entity.getType().name();
            dto.number = entity.getGrammaticalNumber().name();
            return dto;
        }

        /** @return the entity, or null if this record is malformed */
        Entity toEntity() {
            try {
                return new Entity(name, Entity.EntityType.valueOf(type),
                        number == null ? Entity.GrammaticalNumber.SINGULAR : Entity.GrammaticalNumber.valueOf(number));
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    public static class TurnDto {
        public String userMessage;
        public String botResponse;
        public String intentTag;
        public long timestampMillis;
        public List<EntityDto> entities = new ArrayList<>();
        public String topic;

        static TurnDto from(ConversationTurn turn) {
            TurnDto dto = new TurnDto();
            dto.userMessage = turn.getUserMessage();
            dto.botResponse = turn.getBotResponse();
            dto.intentTag = turn.getDetectedIntent() != null ? turn.getDetectedIntent().getTag() : null;
            dto.timestampMillis = turn.getTimestamp() != null ? turn.getTimestamp().toEpochMilli() : 0L;
            dto.entities = entitiesToDtos(turn.getEntities());
            dto.topic = turn.getTopic();
            return dto;
        }

        ConversationTurn toTurn(Function<String, Intent> intentLookup) {
            return new ConversationTurn(userMessage, botResponse, lookup(intentLookup, intentTag),
                    Instant.ofEpochMilli(timestampMillis), dtosToEntities(entities), topic);
        }
    }

    public static class TopicDto {
        public String topicId;
        public List<EntityDto> entities = new ArrayList<>();
        public List<TurnDto> turns = new ArrayList<>();
        public long suspendedAtMillis;
        public String lastIntentTag;

        static TopicDto from(TopicState state) {
            TopicDto dto = new TopicDto();
            dto.topicId = state.getTopicId();
            dto.entities = entitiesToDtos(state.getEntities());
            for (ConversationTurn turn : state.getTurns()) {
                dto.turns.add(TurnDto.from(turn));
            }
            dto.suspendedAtMillis = state.getSuspendedAt() != null ? state.getSuspendedAt().toEpochMilli() : 0L;
            dto.lastIntentTag = state.getLastIntent() != null ? state.getLastIntent().getTag() : null;
            return dto;
        }

        TopicState toTopicState(Function<String, Intent> intentLookup) {
            List<ConversationTurn> turnList = new ArrayList<>();
            for (TurnDto turn : turns) {
                turnList.add(turn.toTurn(intentLookup));
            }
            return new TopicState(topicId, dtosToEntities(entities), turnList,
                    Instant.ofEpochMilli(suspendedAtMillis), lookup(intentLookup, lastIntentTag));
        }
    }

    static List<EntityDto> entitiesToDtos(List<Entity> entities) {
        List<EntityDto> dtos = new ArrayList<>();
        if (entities != null) {
            for (Entity entity : entities) {
                dtos.add(EntityDto.from(entity));
            }
        }
        return dtos;
    }

    static List<Entity> dtosToEntities(List<EntityDto> dtos) {
        List<Entity> entities = new ArrayList<>();
        if (dtos != null) {
            for (EntityDto dto : dtos) {
                Entity entity = dto.toEntity();
                if (entity != null) {
                    entities.add(entity);
                }
            }
        }
        return entities;
    }

    static Intent lookup(Function<String, Intent> intentLookup, String tag) {
        return tag == null || intentLookup == null ? null : intentLookup.apply(tag);
    }
}
