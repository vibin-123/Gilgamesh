package com.gilgamesh;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.model.Intent;
import com.gilgamesh.nlp.IntentClassifier;
import com.gilgamesh.nlp.ReferenceResolver;
import com.gilgamesh.nlp.TextPreprocessor;
import com.gilgamesh.response.DateTimeIntentHandler;
import com.gilgamesh.response.IntentHandler;
import com.gilgamesh.response.ResponseEngine;
import com.gilgamesh.response.StaticIntentHandler;
import com.gilgamesh.response.WeatherIntentHandler;

import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Main — Entry point for the Gilgamesh Context-Aware NLP Chatbot.
 */
public class Main {

    private static final Set<String> EXIT_COMMANDS = Set.of("quit", "exit", "bye", "q");

    public static void main(String[] args) {
        printBanner();

        // 1. Load intent definitions from intents.json
        IntentRepository repository = new IntentRepository();

        // 2. Create text preprocessor (Tokenization + Stopwords + SpellCheck + Synonyms + Stemming)
        TextPreprocessor preprocessor = new TextPreprocessor();

        // 3. Create Reference Resolver for anaphora resolution ("it", "that", "he", "she")
        ReferenceResolver referenceResolver = new ReferenceResolver();

        // 4. Create hybrid context-aware classifier
        IntentClassifier classifier = new IntentClassifier(repository, preprocessor);

        // 5. Create response engine with non-repeating variations & progressive fallbacks
        List<IntentHandler> handlers = List.of(
                new DateTimeIntentHandler(),
                new WeatherIntentHandler(),
                new StaticIntentHandler());
        ResponseEngine responseEngine = new ResponseEngine(handlers);

        // 6. Create short-term conversation memory context
        ConversationContext context = new ConversationContext();

        // 7. Create entity extractor & turn analyzer for multi-turn discourse
        com.gilgamesh.nlp.EntityExtractor entityExtractor = new com.gilgamesh.nlp.EntityExtractor();
        com.gilgamesh.memory.TurnAnalyzer turnAnalyzer = new com.gilgamesh.memory.TurnAnalyzer(entityExtractor);

        // 8. Create multi-turn conversation manager orchestrator
        ConversationManager conversationManager = new ConversationManager(
                entityExtractor,
                turnAnalyzer,
                referenceResolver,
                classifier,
                responseEngine,
                context
        );

        System.out.println("Type your message below. Type 'quit' to exit.\n");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("You: ");

            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            if (EXIT_COMMANDS.contains(input.toLowerCase())) {
                System.out.println("Gilgamesh: Goodbye! It was great chatting with you. 👋");
                break;
            }

            // Process message through full multi-turn conversation pipeline
            String response = conversationManager.processMessage(input);

            // Display response
            System.out.println("Gilgamesh: " + response);
            System.out.println();
        }

        scanner.close();
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║           ⚔  GILGAMESH NLP CHATBOT  ⚔           ║");
        System.out.println("║                                                  ║");
        System.out.println("║  A Java chatbot demonstrating core NLP concepts  ║");
        System.out.println("║  Featuring TF-IDF, Stemming, Synonyms & Anaphora  ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
    }
}
