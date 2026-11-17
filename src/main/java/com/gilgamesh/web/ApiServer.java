package com.gilgamesh.web;

import com.gilgamesh.BotReply;
import com.gilgamesh.GilgameshBot;
import com.gilgamesh.memory.ConversationContext;
import com.gilgamesh.memory.ConversationTurn;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JsonMapper;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ApiServer — Makes the bot reachable over HTTP, so a web page can talk to it.
 *
 * Endpoints (all JSON):
 *   POST   /api/chat            {"sessionId": "...", "message": "..."}  ->  {"sessionId", "reply"}
 *   GET    /api/sessions/{id}   the conversation so far
 *   DELETE /api/sessions/{id}   forget the conversation
 *   GET    /api/intents         example messages, grouped by category (for suggestions)
 *   GET    /api/health          is the server up
 *
 * Every error is answered with {"error": "..."} and a matching status code; internal
 * details such as stack traces are logged, never sent to the client.
 */
public class ApiServer {

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());

    /** Requests larger than this are rejected before they are read (a chat message is tiny). */
    private static final long MAX_REQUEST_BYTES = 16_384;

    /** Classpath folder holding the built web UI, if it has been packaged into the jar. */
    private static final String UI_FOLDER = "/public";

    private final GilgameshBot bot;
    private final SessionManager sessions;
    private final int maxMessageLength;
    private final Gson gson = new Gson();
    private Javalin app;

    /** What the client sends to /api/chat. */
    private static class ChatRequest {
        String sessionId;
        String message;
    }

    /** What /api/chat answers. */
    private record ChatResponse(String sessionId, BotReply reply) {}

    /** One exchange in a conversation's history. */
    private record HistoryEntry(String user, String bot, String intent, long timestamp) {}

    private record SessionView(String sessionId, String topic, String userName, List<HistoryEntry> history) {}

    private record Category(String name, List<String> examples) {}

    public ApiServer(GilgameshBot bot, SessionManager sessions, int maxMessageLength) {
        this.bot = bot;
        this.sessions = sessions;
        this.maxMessageLength = maxMessageLength;
    }

    /**
     * Starts listening.
     *
     * @param host address to bind to ("127.0.0.1" = this computer only)
     * @param port port to listen on (0 = any free port; see {@link #port()})
     */
    public ApiServer start(String host, int port) {
        boolean hasUi = ApiServer.class.getResource(UI_FOLDER + "/index.html") != null;

        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.http.maxRequestSize = MAX_REQUEST_BYTES;
            config.jsonMapper(new JsonMapper() {
                @Override
                public String toJsonString(Object obj, Type type) {
                    return gson.toJson(obj, type);
                }

                @Override
                public <T> T fromJsonString(String json, Type targetType) {
                    return gson.fromJson(json, targetType);
                }
            });
            if (hasUi) {
                config.staticFiles.add(UI_FOLDER, Location.CLASSPATH);
            }
        });

        app.post("/api/chat", this::chat);
        app.get("/api/sessions/{id}", this::getSession);
        app.delete("/api/sessions/{id}", this::deleteSession);
        app.get("/api/intents", this::intents);
        app.get("/api/health", ctx -> ctx.json(Map.of(
                "status", "ok",
                "intents", bot.getRepository().getIntents().size(),
                "sessions", sessions.liveCount())));

        // A bad session id anywhere is the client's mistake, not a server failure
        app.exception(IllegalArgumentException.class, (e, ctx) -> error(ctx, 400, e.getMessage()));
        app.exception(Exception.class, (e, ctx) -> {
            LOG.log(Level.WARNING, "Unexpected error handling " + ctx.method() + " " + ctx.path(), e);
            error(ctx, 500, "Something went wrong on the server.");
        });
        // Say explicitly that the JSON is UTF-8. Browsers assume it, but some clients fall back
        // to an older encoding and would show "°C" or emoji as garbage.
        app.after("/api/*", ctx -> {
            String contentType = ctx.res().getContentType();
            if (contentType != null && contentType.startsWith("application/json") && !contentType.contains("charset")) {
                ctx.contentType("application/json; charset=utf-8");
            }
        });

        // An unknown /api/ path would otherwise get the web server's plain-text "Not Found"
        app.error(404, ctx -> {
            if (ctx.path().startsWith("/api/") && ctx.attribute(ERROR_SENT) == null) {
                error(ctx, 404, "No such endpoint.");
            }
        });

        app.start(host, port);
        return this;
    }

    /** The port the server is actually listening on. */
    public int port() {
        return app.port();
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    private void chat(Context ctx) {
        ChatRequest request;
        try {
            request = gson.fromJson(ctx.body(), ChatRequest.class);
        } catch (JsonParseException e) {
            error(ctx, 400, "The request body must be JSON like {\"message\": \"hello\"}.");
            return;
        }
        if (request == null || request.message == null || request.message.isBlank()) {
            error(ctx, 400, "\"message\" is required.");
            return;
        }
        String message = request.message.trim();
        if (message.length() > maxMessageLength) {
            error(ctx, 400, "The message is too long (limit: " + maxMessageLength + " characters).");
            return;
        }

        SessionManager.Session session = sessions.open(request.sessionId); // invalid id -> 400 via handler above
        BotReply reply = session.conversation().reply(message);
        ctx.json(new ChatResponse(session.id(), reply));
    }

    private void getSession(Context ctx) {
        Optional<SessionManager.Session> session = sessions.find(ctx.pathParam("id"));
        if (session.isEmpty()) {
            error(ctx, 404, "No conversation with that id.");
            return;
        }
        ConversationContext context = session.get().conversation().getContext();
        List<HistoryEntry> history = new ArrayList<>();
        for (ConversationTurn turn : context.getHistory()) {
            history.add(new HistoryEntry(
                    turn.getUserMessage(),
                    turn.getBotResponse(),
                    turn.getDetectedIntent() != null ? turn.getDetectedIntent().getTag() : null,
                    turn.getTimestamp() != null ? turn.getTimestamp().toEpochMilli() : 0L));
        }
        ctx.json(new SessionView(session.get().id(), context.peekCurrentTopic(), context.getUserName(), history));
    }

    private void deleteSession(Context ctx) {
        if (sessions.delete(ctx.pathParam("id"))) {
            ctx.status(204);
        } else {
            error(ctx, 404, "No conversation with that id.");
        }
    }

    private void intents(Context ctx) {
        List<Category> categories = new ArrayList<>();
        bot.getRepository().examplesByCategory()
                .forEach((name, examples) -> categories.add(new Category(name, examples)));
        ctx.json(Map.of("categories", categories));
    }

    /** Request attribute marking that this class already wrote the error body. */
    private static final String ERROR_SENT = "gilgamesh.errorSent";

    private static void error(Context ctx, int status, String message) {
        ctx.attribute(ERROR_SENT, Boolean.TRUE);
        ctx.status(status).json(Map.of("error", message == null ? "Bad request." : message));
    }
}
