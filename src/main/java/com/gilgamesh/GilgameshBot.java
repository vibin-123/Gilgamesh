package com.gilgamesh;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.data.KnowledgeBase;
import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.TurnAnalyzer;
import com.gilgamesh.nlp.EntityExtractor;
import com.gilgamesh.nlp.IntentClassifier;
import com.gilgamesh.nlp.ReferenceResolver;
import com.gilgamesh.nlp.TextPreprocessor;
import com.gilgamesh.response.DateTimeIntentHandler;
import com.gilgamesh.response.HelpIntentHandler;
import com.gilgamesh.response.IntentHandler;
import com.gilgamesh.response.KnowledgeIntentHandler;
import com.gilgamesh.response.OpenMeteoWeatherClient;
import com.gilgamesh.response.PlaceFinder;
import com.gilgamesh.response.RepeatIntentHandler;
import com.gilgamesh.response.ResponseEngine;
import com.gilgamesh.response.StaticIntentHandler;
import com.gilgamesh.response.UserNameIntentHandler;
import com.gilgamesh.response.WeatherClient;
import com.gilgamesh.response.WeatherIntentHandler;

import java.util.List;

/**
 * GilgameshBot — The parts of the bot that are the same for every conversation.
 *
 * Loading the intents, training the classifier and wiring the handlers happens once here.
 * Each conversation then gets its own {@link ConversationManager} and
 * {@link ConversationContext} from {@link #newConversation()}; the terminal uses one,
 * the web server one per user. Everything held here is read-only after construction,
 * so it can be shared between conversations running at the same time.
 */
public class GilgameshBot {

    private final GilgameshConfig config;
    private final IntentRepository repository;
    private final IntentClassifier classifier;
    private final ResponseEngine responseEngine;
    private final EntityExtractor entityExtractor;
    private final TurnAnalyzer turnAnalyzer;
    private final ReferenceResolver referenceResolver;

    /** Builds the bot with live weather and time-zone lookups (Open-Meteo). */
    public GilgameshBot(GilgameshConfig config) {
        this(config, new OpenMeteoWeatherClient());
    }

    private GilgameshBot(GilgameshConfig config, OpenMeteoWeatherClient openMeteo) {
        this(config, openMeteo, openMeteo);
    }

    /**
     * @param weatherClient source of weather data (a fake one in tests)
     * @param placeFinder   resolves a city to its time zone (a fake one in tests)
     * @throws RuntimeException if intents.json or knowledge.json cannot be loaded
     */
    public GilgameshBot(GilgameshConfig config, WeatherClient weatherClient, PlaceFinder placeFinder) {
        this.config = config;

        // 1. Load intent definitions from intents.json
        this.repository = new IntentRepository();

        // 2. Hybrid context-aware classifier, tuned from gilgamesh.properties.
        //    The preprocessor does tokenization, spell correction, stopwords, synonyms and stemming.
        this.classifier = new IntentClassifier(repository, new TextPreprocessor());
        config.applyTo(classifier);

        // 3. Response engine: the first handler that accepts an intent answers it
        List<IntentHandler> handlers = List.of(
                new DateTimeIntentHandler(placeFinder),
                new WeatherIntentHandler(weatherClient),
                new RepeatIntentHandler(),
                new HelpIntentHandler(repository),
                new UserNameIntentHandler(),
                new KnowledgeIntentHandler(KnowledgeBase.loadDefault()),
                new StaticIntentHandler());
        this.responseEngine = new ResponseEngine(handlers);

        // 4. Entity extraction, turn analysis and reference resolution ("it" -> "Java")
        this.entityExtractor = new EntityExtractor();
        this.turnAnalyzer = new TurnAnalyzer(entityExtractor);
        this.referenceResolver = new ReferenceResolver();
    }

    /** Starts a new, empty conversation. */
    public ConversationManager newConversation() {
        return newConversation(config.newContext());
    }

    /** Creates a conversation around an existing context (for example one restored from disk). */
    public ConversationManager newConversation(ConversationContext context) {
        ConversationManager manager = new ConversationManager(
                entityExtractor, turnAnalyzer, referenceResolver, classifier, responseEngine, context);
        manager.setAmbiguityMargin(config.ambiguityMargin());
        return manager;
    }

    public GilgameshConfig getConfig() {
        return config;
    }

    public IntentRepository getRepository() {
        return repository;
    }
}
