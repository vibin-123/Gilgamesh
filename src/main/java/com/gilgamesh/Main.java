package com.gilgamesh;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.ConversationStore;
import com.gilgamesh.nlp.IntentClassifier;
import com.gilgamesh.nlp.ReferenceResolver;
import com.gilgamesh.nlp.TextPreprocessor;
import com.gilgamesh.response.DateTimeIntentHandler;
import com.gilgamesh.response.IntentHandler;
import com.gilgamesh.response.OpenMeteoWeatherClient;
import com.gilgamesh.response.ResponseEngine;
import com.gilgamesh.response.StaticIntentHandler;
import com.gilgamesh.response.WeatherIntentHandler;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main — Entry point for the Gilgamesh Context-Aware NLP Chatbot.
 */
public class Main {

    private static final Set<String> EXIT_COMMANDS = Set.of("quit", "exit", "bye", "q");
    private static final String RESET_COMMAND = "reset";

    public static void main(String[] args) {
        // Force UTF-8 so the banner and emoji render correctly on Windows consoles
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));

        configureLogging(Arrays.asList(args).contains("--debug"));
        printBanner();

        ConversationManager conversationManager;
        try {
            conversationManager = buildConversationManager(Arrays.asList(args).contains("--fresh"));
        } catch (Exception e) {
            System.err.println("Gilgamesh failed to start: " + e.getMessage());
            Logger.getLogger(Main.class.getName()).log(Level.FINE, "Startup failure", e);
            System.exit(1);
            return;
        }

        System.out.println("Type your message below. Type 'quit' to exit.\n");

        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

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

            if (RESET_COMMAND.equalsIgnoreCase(input)) {
                conversationManager.resetContext();
                System.out.println("Gilgamesh: Done. I've forgotten our earlier conversation.\n");
                continue;
            }

            // Process message through the full multi-turn pipeline; a failure in one
            // turn must never kill the session.
            String response;
            try {
                response = conversationManager.processMessage(input);
            } catch (Exception e) {
                Logger.getLogger(Main.class.getName()).log(Level.WARNING, "Error while processing message", e);
                response = "Sorry, something went wrong on my side. Please try again.";
            }

            System.out.println("Gilgamesh: " + response);
            System.out.println();
        }

        scanner.close();
    }

    /**
     * Routes java.util.logging output: WARNING and above by default,
     * everything (FINE) when --debug is passed.
     */
    private static void configureLogging(boolean debug) {
        Level level = debug ? Level.FINE : Level.WARNING;
        Logger root = Logger.getLogger("");
        root.setLevel(level);
        for (Handler handler : root.getHandlers()) {
            handler.setLevel(level);
        }
    }

    private static ConversationManager buildConversationManager(boolean fresh) {
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
                new WeatherIntentHandler(new OpenMeteoWeatherClient()),
                new StaticIntentHandler());
        ResponseEngine responseEngine = new ResponseEngine(handlers);

        // 6. Create short-term conversation memory context
        ConversationContext context = new ConversationContext();

        // 7. Create entity extractor & turn analyzer for multi-turn discourse
        com.gilgamesh.nlp.EntityExtractor entityExtractor = new com.gilgamesh.nlp.EntityExtractor();
        com.gilgamesh.memory.TurnAnalyzer turnAnalyzer = new com.gilgamesh.memory.TurnAnalyzer(entityExtractor);

        // 8. Create multi-turn conversation manager orchestrator
        ConversationManager manager = new ConversationManager(
                entityExtractor,
                turnAnalyzer,
                referenceResolver,
                classifier,
                responseEngine,
                context
        );

        // 9. Persist the conversation between runs (--fresh discards the saved session)
        ConversationStore store = new ConversationStore(ConversationStore.defaultPath());
        if (fresh) {
            store.delete();
        } else {
            store.load().ifPresent(snapshot -> {
                context.restore(snapshot, repository::findByTag);
                System.out.println("(Restored your previous conversation. Type 'reset' to start over.)\n");
            });
        }
        manager.setStore(store);

        return manager;
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
