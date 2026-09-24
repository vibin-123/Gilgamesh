package com.gilgamesh.memory;

/**
 * Entity — Represents a named entity extracted from conversation turns.
 *
 * NLP Concept: Named Entity Recognition (NER)
 * --------------------------------------------
 * NER identifies and classifies named entities in text into predefined categories
 * such as person names, programming languages, organizations, etc. This lightweight
 * entity model enables the reference resolver to bind pronouns and noun phrases
 * to the correct referent from conversation history.
 */
public class Entity {

    /**
     * Classification of recognized entity types.
     */
    public enum EntityType {
        LANGUAGE,   // Programming languages: Java, Python, etc.
        PERSON,     // People: James Gosling, Guido van Rossum, etc.
        BOT,        // Bot references: Gilgamesh
        CONCEPT,    // Technical concepts: NLP, tokenization, etc.
        GENERAL     // Unclassified entities
    }

    /**
     * Grammatical number for pronoun compatibility checks.
     */
    public enum GrammaticalNumber {
        SINGULAR,
        PLURAL
    }

    private final String name;
    private final EntityType type;
    private final GrammaticalNumber grammaticalNumber;

    public Entity(String name, EntityType type) {
        this(name, type, GrammaticalNumber.SINGULAR);
    }

    public Entity(String name, EntityType type, GrammaticalNumber grammaticalNumber) {
        this.name = name;
        this.type = type;
        this.grammaticalNumber = grammaticalNumber;
    }

    public String getName() {
        return name;
    }

    public EntityType getType() {
        return type;
    }

    public GrammaticalNumber getGrammaticalNumber() {
        return grammaticalNumber;
    }

    /**
     * Checks if this entity is compatible with a singular pronoun (it, this, that).
     */
    public boolean isSingularNonPerson() {
        return grammaticalNumber == GrammaticalNumber.SINGULAR
                && type != EntityType.PERSON;
    }

    /**
     * Checks if this entity is compatible with a person pronoun (he, she).
     */
    public boolean isPerson() {
        return type == EntityType.PERSON;
    }

    /**
     * Checks if this entity is compatible with a plural pronoun (they, them, these, those).
     */
    public boolean isPlural() {
        return grammaticalNumber == GrammaticalNumber.PLURAL;
    }

    /**
     * Checks if this entity matches a contextual noun phrase type.
     * For example, "the language" should match entities of type LANGUAGE.
     */
    public boolean matchesType(EntityType expectedType) {
        return this.type == expectedType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity) o;
        return name.equalsIgnoreCase(entity.name) && type == entity.type;
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode() * 31 + type.hashCode();
    }

    @Override
    public String toString() {
        return "Entity{name='" + name + "', type=" + type + ", number=" + grammaticalNumber + "}";
    }
}
