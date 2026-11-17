package com.gilgamesh.web;

import com.gilgamesh.GilgameshBot;
import com.gilgamesh.GilgameshConfig;
import com.gilgamesh.response.PlaceFinder;
import com.gilgamesh.response.WeatherClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talks to a real server on a free local port, with fake weather and place lookups
 * so the tests need no internet.
 */
class ApiServerTest {

    @TempDir
    Path tempDir;

    private final HttpClient http = HttpClient.newHttpClient();
    private ApiServer server;
    private String base;

    @BeforeEach
    void startServer() {
        GilgameshConfig config = GilgameshConfig.of(Map.of(
                GilgameshConfig.SESSION_PATH, tempDir.resolve("session.json").toString().replace('\\', '/'),
                GilgameshConfig.SERVER_MAX_MESSAGE_LENGTH, "200"));
        WeatherClient weather = city -> new WeatherClient.Report(city, "Testland", 21.5, 9.0, 0);
        PlaceFinder places = name -> Optional.of(new PlaceFinder.Place(name, "Testland", 0, 0, "UTC"));
        GilgameshBot bot = new GilgameshBot(config, weather, places);

        SessionManager sessions = new SessionManager(bot, config.serverSessionDirectory(),
                config.serverMaxSessions(), config.sessionMaxAgeMs());
        server = new ApiServer(bot, sessions, config.serverMaxMessageLength()).start("127.0.0.1", 0);
        base = "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // --- helpers ---

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json");
        request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    /** Sends a chat message and returns the whole response body. */
    private JsonObject chat(String sessionId, String message) throws Exception {
        JsonObject body = new JsonObject();
        if (sessionId != null) body.addProperty("sessionId", sessionId);
        body.addProperty("message", message);
        HttpResponse<String> response = send("POST", "/api/chat", body.toString());
        assertEquals(200, response.statusCode(), response.body());
        return json(response);
    }

    private static JsonObject reply(JsonObject chatResponse) {
        return chatResponse.getAsJsonObject("reply");
    }

    // --- chatting ---

    @Test
    void firstMessageCreatesASessionAndExplainsHowItWasUnderstood() throws Exception {
        JsonObject response = chat(null, "tell me about Rust");
        String sessionId = response.get("sessionId").getAsString();
        assertTrue(SessionManager.isValidId(sessionId), sessionId);

        JsonObject reply = reply(response);
        assertTrue(reply.get("text").getAsString().contains("Rust"));
        assertEquals("ANSWER", reply.get("kind").getAsString());
        assertEquals("entity_about", reply.get("intent").getAsString());
        assertTrue(reply.get("confidence").getAsDouble() > 0.4);
        assertEquals("Rust", reply.get("topic").getAsString());
        assertEquals("Rust", reply.getAsJsonArray("entities").get(0).getAsJsonObject().get("name").getAsString());

        // The same session remembers the subject: "it" is resolved to Rust
        JsonObject followUp = reply(chat(sessionId, "who created it?"));
        assertTrue(followUp.get("text").getAsString().contains("Graydon Hoare"));
        assertEquals("FOLLOW_UP", followUp.get("messageType").getAsString());
        assertTrue(followUp.get("resolvedInput").getAsString().contains("Rust"));
        assertEquals("entity_creator", followUp.get("intent").getAsString());
    }

    @Test
    void sessionsDoNotShareNameTopicOrFallbackCount() throws Exception {
        String alice = chat(null, "my name is Alice").get("sessionId").getAsString();
        String bob = chat(null, "hello").get("sessionId").getAsString();
        assertNotEquals(alice, bob);

        chat(alice, "tell me about Rust");
        JsonObject bobAsksName = reply(chat(bob, "what is my name?"));
        assertFalse(bobAsksName.get("text").getAsString().contains("Alice"), "Bob must not see Alice's name");
        assertEquals("QUESTION", bobAsksName.get("kind").getAsString());
        assertNotEquals("Rust", bobAsksName.get("topic").getAsString());

        assertEquals("Your name is Alice.", reply(chat(alice, "what is my name?")).get("text").getAsString());

        // Alice confuses the bot three times; Bob's first confusion still gets the gentlest reply
        for (int i = 0; i < 3; i++) chat(alice, "zxqv blorp wibble");
        JsonObject aliceThird = reply(chat(alice, "zxqv blorp wibble"));
        assertEquals("FALLBACK", aliceThird.get("kind").getAsString());
        assertTrue(aliceThird.get("text").getAsString().contains("help"), aliceThird.toString());

        chat(bob, "Bob");
        String bobFirst = reply(chat(bob, "zxqv blorp wibble")).get("text").getAsString();
        assertFalse(bobFirst.contains("'help'"), "Bob's first fallback should not be the escalated one: " + bobFirst);
    }

    @Test
    void weatherRepliesCarryStructuredData() throws Exception {
        JsonObject reply = reply(chat(null, "what's the weather in Paris?"));

        assertEquals("weather", reply.get("dataType").getAsString());
        JsonObject data = reply.getAsJsonObject("data");
        assertEquals("Paris", data.get("city").getAsString());
        assertEquals(21.5, data.get("temperatureC").getAsDouble());

        JsonObject ask = reply(chat(null, "what is the weather like"));
        assertEquals("QUESTION", ask.get("kind").getAsString());
        assertEquals("location", ask.getAsJsonObject("pendingQuestion").get("slot").getAsString());
        assertTrue(ask.get("data") == null || ask.get("data").isJsonNull());
    }

    @Test
    void responsesDeclareUtf8SoSymbolsSurvive() throws Exception {
        HttpResponse<byte[]> response = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/chat"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"message\": \"weather in Paris\"}")).build(),
                HttpResponse.BodyHandlers.ofByteArray());

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.toLowerCase().contains("charset=utf-8"), contentType);
        String body = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("21.5°C"), body);
    }

    // --- bad requests ---

    @Test
    void invalidRequestsAreRejectedWithAClearError() throws Exception {
        assertError(400, send("POST", "/api/chat", "{\"message\": \"   \"}"));
        assertError(400, send("POST", "/api/chat", "{}"));
        assertError(400, send("POST", "/api/chat", "this is not json"));
        assertError(400, send("POST", "/api/chat", "{\"message\": \"" + "a".repeat(201) + "\"}"));
        assertError(404, send("GET", "/api/nope", null));
    }

    @Test
    void sessionIdsThatAreNotUuidsNeverReachTheDisk() throws Exception {
        for (String evil : new String[]{"../../secrets", "..\\\\..\\\\x", "abc", "session.json",
                "00000000-0000-0000-0000-00000000000g"}) {
            HttpResponse<String> response = send("POST", "/api/chat",
                    "{\"sessionId\": \"" + evil + "\", \"message\": \"hello\"}");
            assertError(400, response);
        }
        assertError(400, send("GET", "/api/sessions/not-a-uuid", null));
        assertError(400, send("DELETE", "/api/sessions/not-a-uuid", null));

        Path sessionsDir = tempDir.resolve("sessions");
        if (Files.isDirectory(sessionsDir)) {
            try (var files = Files.list(sessionsDir)) {
                assertEquals(0, files.count(), "no file may be written for a rejected id");
            }
        }
        try (var files = Files.list(tempDir)) {
            assertTrue(files.allMatch(p -> p.getFileName().toString().equals("sessions")),
                    "nothing may be written outside the sessions folder");
        }
    }

    private static void assertError(int expectedStatus, HttpResponse<String> response) {
        assertEquals(expectedStatus, response.statusCode(), response.body());
        JsonObject body = json(response);
        assertTrue(body.has("error") && !body.get("error").getAsString().isBlank(), response.body());
        assertFalse(response.body().contains("Exception"), "no internals in error bodies: " + response.body());
        assertFalse(response.body().contains("\tat "), "no stack traces in error bodies");
    }

    // --- sessions ---

    @Test
    void historyCanBeReadAndTheSessionForgotten() throws Exception {
        String sessionId = chat(null, "my name is Sam").get("sessionId").getAsString();
        chat(sessionId, "tell me about Kotlin");

        JsonObject session = json(send("GET", "/api/sessions/" + sessionId, null));
        assertEquals("Sam", session.get("userName").getAsString());
        assertEquals("Kotlin", session.get("topic").getAsString());
        JsonArray history = session.getAsJsonArray("history");
        assertEquals(2, history.size());
        assertEquals("tell me about Kotlin", history.get(1).getAsJsonObject().get("user").getAsString());
        assertEquals("entity_about", history.get(1).getAsJsonObject().get("intent").getAsString());

        assertEquals(204, send("DELETE", "/api/sessions/" + sessionId, null).statusCode());
        assertError(404, send("GET", "/api/sessions/" + sessionId, null));
        assertError(404, send("DELETE", "/api/sessions/" + sessionId, null));
        assertError(404, send("GET", "/api/sessions/11111111-2222-3333-4444-555555555555", null));
    }

    // --- discovery ---

    @Test
    void intentsEndpointOffersExamplesAndHealthReportsOk() throws Exception {
        JsonArray categories = json(send("GET", "/api/intents", null)).getAsJsonArray("categories");
        assertTrue(categories.size() >= 4);
        String all = categories.toString();
        assertTrue(all.contains("weather in Paris") && all.contains("tell me about Rust"), all);

        JsonObject health = json(send("GET", "/api/health", null));
        assertEquals("ok", health.get("status").getAsString());
        assertTrue(health.get("intents").getAsInt() > 40);
    }
}
