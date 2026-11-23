package world.bentobox.gg.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

/**
 * Runs the client against a real HTTP server on localhost.
 */
class OpenAiCompatibleClientTest {

    private HttpServer server;
    private String baseUrl;
    private int status = 200;
    private String responseBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"awards\\\":[]}\"}}]}";
    private String requestPath;
    private String requestAuth;
    private JsonObject requestBody;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath = exchange.getRequestURI().getPath();
            requestAuth = exchange.getRequestHeaders().getFirst("Authorization");
            requestBody = JsonParser
                    .parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private OpenAiCompatibleClient client(String key, boolean jsonMode) {
        return new OpenAiCompatibleClient(baseUrl, "test-model", key, Duration.ofSeconds(5), jsonMode);
    }

    @Test
    void sendsAChatCompletionAndReturnsTheContent() throws Exception {
        assertEquals("{\"awards\":[]}", client("secret", false).complete("be fair", "héllo"));

        assertEquals("/v1/chat/completions", requestPath);
        assertEquals("Bearer secret", requestAuth);
        assertEquals("test-model", requestBody.get("model").getAsString());
        JsonObject system = requestBody.getAsJsonArray("messages").get(0).getAsJsonObject();
        JsonObject user = requestBody.getAsJsonArray("messages").get(1).getAsJsonObject();
        assertEquals("system", system.get("role").getAsString());
        assertEquals("be fair", system.get("content").getAsString());
        assertEquals("user", user.get("role").getAsString());
        assertEquals("héllo", user.get("content").getAsString());
        assertFalse(requestBody.has("response_format"));
    }

    @Test
    void sendsNoAuthorizationHeaderWithoutAKey() throws Exception {
        client("", false).complete("s", "u");
        assertNull(requestAuth);
    }

    @Test
    void jsonModeAsksForAJsonObject() throws Exception {
        client("k", true).complete("s", "u");
        assertEquals("json_object", requestBody.getAsJsonObject("response_format").get("type").getAsString());
    }

    @Test
    void aRejectedKeyIsReportedPlainly() {
        status = 401;
        LlmException e = assertThrows(LlmException.class, () -> client("bad", false).complete("s", "u"));
        assertTrue(e.getMessage().contains("API key"));
    }

    @Test
    void otherHttpErrorsIncludeTheStatusAndBody() {
        status = 429;
        responseBody = "{\"error\":\"slow down\"}";
        LlmException e = assertThrows(LlmException.class, () -> client("k", false).complete("s", "u"));
        assertTrue(e.getMessage().contains("429"));
        assertTrue(e.getMessage().contains("slow down"));
    }

    @Test
    void anUnexpectedBodyIsAnLlmException() {
        responseBody = "{\"choices\":[]}";
        assertThrows(LlmException.class, () -> client("k", false).complete("s", "u"));
        responseBody = "<html>gateway</html>";
        assertThrows(LlmException.class, () -> client("k", false).complete("s", "u"));
    }

    @Test
    void anUnreachableServerIsAnLlmException() {
        server.stop(0);
        assertThrows(LlmException.class, () -> client("k", false).complete("s", "u"));
    }

    @Test
    void acceptsBaseUrlsWithOrWithoutThePath() {
        URI expected = URI.create("https://example.com/v1/chat/completions");
        assertEquals(expected, OpenAiCompatibleClient.endpoint("https://example.com/v1"));
        assertEquals(expected, OpenAiCompatibleClient.endpoint(" https://example.com/v1/ "));
        assertEquals(expected, OpenAiCompatibleClient.endpoint("https://example.com/v1/chat/completions"));
    }
}
