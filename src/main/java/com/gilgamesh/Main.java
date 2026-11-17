package com.gilgamesh;

import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.ConversationStore;
import com.gilgamesh.web.ApiServer;
import com.gilgamesh.web.SessionManager;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main — Entry point for the Gilgamesh Context-Aware NLP Chatbot.
 *
 * Two ways to run it:
 *   (no option)   chat in the terminal
 *   --serve       start the HTTP API (and the web UI, if it was built into the jar)
 *
 * Other options: --config FILE, --port N (with --serve), --fresh, --debug.
 */
public class Main {

    private static final Set<String> EXIT_COMMANDS = Set.of("quit", "exit", "bye", "q");
    private static final String RESET_COMMAND = "reset";

    public static void main(String[] args) {
        // Force UTF-8 so the banner and emoji render correctly on Windows consoles
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));

        List<String> options = Arrays.asList(args);
        configureLogging(options.contains("--debug"));
        printBanner();

        GilgameshConfig config;
        GilgameshBot bot;
        Integer port;
        try {
            config = GilgameshConfig.load(configFileFrom(args));
            port = portFrom(args);
            bot = new GilgameshBot(config);
        } catch (Exception e) {
            System.err.println("Gilgamesh failed to start: " + e.getMessage());
            Logger.getLogger(Main.class.getName()).log(Level.FINE, "Startup failure", e);
            System.exit(1);
            return;
        }

        if (options.contains("--serve")) {
            serve(bot, config, port != null ? port : config.serverPort());
        } else {
            chatInTerminal(bot, config, options.contains("--fresh"));
        }
    }

    // --- Web server mode ---

    private static void serve(GilgameshBot bot, GilgameshConfig config, int port) {
        SessionManager sessions = new SessionManager(bot, config.serverSessionDirectory(),
                config.serverMaxSessions(), config.sessionMaxAgeMs());
        ApiServer server = new ApiServer(bot, sessions, config.serverMaxMessageLength());
        try {
            server.start(config.serverHost(), port);
        } catch (Exception e) {
            System.err.println("Gilgamesh could not start the web server on " + config.serverHost() + ":" + port
                    + " (" + e.getMessage() + "). Is the port already in use? Try --port with another number.");
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        String address = "http://" + ("0.0.0.0".equals(config.serverHost()) ? "localhost" : config.serverHost())
                + ":" + server.port();
        System.out.println("Gilgamesh is listening on " + address);
        System.out.println("Try: POST " + address + "/api/chat  with  {\"message\": \"hello\"}");
        System.out.println("Press Ctrl+C to stop.");
    }

    // --- Terminal mode ---

    private static void chatInTerminal(GilgameshBot bot, GilgameshConfig config, boolean fresh) {
        ConversationManager conversationManager = restoreConversation(bot, config, fresh);

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

    /** Starts the terminal conversation, continuing the saved one unless --fresh was given. */
    private static ConversationManager restoreConversation(GilgameshBot bot, GilgameshConfig config, boolean fresh) {
        ConversationContext context = config.newContext();
        ConversationStore store = config.newStore();
        if (fresh) {
            store.delete();
        } else {
            store.load().ifPresent(snapshot -> {
                context.restore(snapshot, bot.getRepository()::findByTag);
                System.out.println("(Restored your previous conversation. Type 'reset' to start over.)\n");
            });
        }
        ConversationManager manager = bot.newConversation(context);
        manager.setStore(store);
        return manager;
    }

    // --- Command line ---

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

    /**
     * Reads the value of an option given as "--name value" or "--name=value".
     *
     * @return the value, or null if the option wasn't used
     * @throws IllegalArgumentException if the option is there but has no value
     */
    private static String optionValue(String[] args, String name) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith(name + "=")) {
                return args[i].substring(name.length() + 1);
            }
            if (args[i].equals(name)) {
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    throw new IllegalArgumentException(name + " needs a value");
                }
                return args[i + 1];
            }
        }
        return null;
    }

    /**
     * Reads the path given with "--config <file>" or "--config=<file>".
     *
     * @return the path, or null if the option wasn't used
     */
    static Path configFileFrom(String[] args) {
        String value = optionValue(args, "--config");
        return value == null ? null : Paths.get(value);
    }

    /**
     * Reads the port given with "--port <n>" or "--port=<n>".
     *
     * @return the port, or null if the option wasn't used
     * @throws IllegalArgumentException if the value is not a port number
     */
    static Integer portFrom(String[] args) {
        String value = optionValue(args, "--port");
        if (value == null) {
            return null;
        }
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 0 || port > 65_535) {
                throw new NumberFormatException();
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--port must be a number between 0 and 65535, not '" + value + "'");
        }
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
