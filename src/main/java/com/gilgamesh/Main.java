package com.gilgamesh;

import com.gilgamesh.data.IntentRepository;
import com.gilgamesh.model.Intent;
import com.gilgamesh.nlp.IntentClassifier;
import com.gilgamesh.nlp.TextPreprocessor;
import com.gilgamesh.response.ResponseEngine;
import com.gilgamesh.response.IntentHandler;
import com.gilgamesh.response.DateTimeIntentHandler;
import com.gilgamesh.response.WeatherIntentHandler;
import com.gilgamesh.response.StaticIntentHandler;
import com.gilgamesh.memory.ConversationContext;

import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Main — Entry point for the Gilgamesh NLP Chatbot.
 *
 * This class wires together all components of the NLP pipeline and runs
 * an interactive REPL (Read-Eval-Print Loop) for chatting with the user.
 *
 * NLP Pipeline Overview:
 * ----------------------
 * The chatbot processes each message through a series of NLP steps,
 * mirroring how real-world NLP systems work:
 *
 * User Input (raw text)
 * │
 * ▼
 * TextPreprocessor ─── Tokenization (OpenNLP SimpleTokenizer)
 * │ Lowercasing (case normalization)
 * │ Stopword Removal (noise reduction)
 * ▼
 * IntentClassifier ─── Jaccard Similarity Scoring
 * │ (bag-of-words comparison against patterns)
 * ▼
 * ResponseEngine ───── Template-based Response Selection
 * │ (random pick from matched intent's responses)
 * ▼
 * Bot Response (displayed to user)
 *
 * Each component is independent and could be swapped out. For example,
 * you could replace IntentClassifier's Jaccard scoring with a trained
 * ML model without changing any other class.
 */
public class Main {

    /** Words that signal the user wants to exit the conversation. */
    private static final Set<String> EXIT_COMMANDS = Set.of("quit", "exit", "bye", "q");

    public static void main(String[] args) {
        printBanner();

        // --- Component Initialization ---
        // Each component is created independently, following separation of concerns.
        // Dependencies are injected via constructors (poor man's dependency injection).

        // 1. Load intent definitions from intents.json
        IntentRepository repository = new IntentRepository();

        // 2. Create the text preprocessor (tokenization + stopwords)
        TextPreprocessor preprocessor = new TextPreprocessor();

        // 3. Create the classifier (needs repository for intents and preprocessor for
        // text cleaning)
        IntentClassifier classifier = new IntentClassifier(repository, preprocessor);

        // 4. Create the response engine with a chain of handlers
        List<IntentHandler> handlers = List.of(
                new DateTimeIntentHandler(),
                new WeatherIntentHandler(),
                new StaticIntentHandler());
        ResponseEngine responseEngine = new ResponseEngine(handlers);

        // 5. Create the short-term conversation memory context
        ConversationContext context = new ConversationContext();

        System.out.println("Type your message below. Type 'quit' to exit.\n");

        // --- Chat Loop (REPL) ---
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("You: ");

            // Read user input
            if (!scanner.hasNextLine()) {
                break; // Handle EOF (e.g., piped input)
            }
            String input = scanner.nextLine().trim();

            // Check for empty input
            if (input.isEmpty()) {
                continue;
            }

            // Check for exit commands
            if (EXIT_COMMANDS.contains(input.toLowerCase())) {
                System.out.println("Gilgamesh: Goodbye! It was great chatting with you. 👋");
                break;
            }

            // --- NLP Pipeline Execution ---
            // Step 1 & 2: Preprocessing + Classification
            // (The classifier internally calls the preprocessor)
            Intent matchedIntent = classifier.classify(input);

            // Step 3: Response generation
            String response = responseEngine.generateResponse(matchedIntent);

            // Step 4: Update Conversation Context
            context.update(input, response, matchedIntent);

            // Display the response
            System.out.println("Gilgamesh: " + response);
            System.out.println();
        }

        scanner.close();
    }

    /**
     * Prints a welcome banner with project information.
     */
    private static void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║           ⚔  GILGAMESH NLP CHATBOT  ⚔           ║");
        System.out.println("║                                                  ║");
        System.out.println("║  A Java chatbot demonstrating core NLP concepts  ║");
        System.out.println("║                                                  ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
    }
}
