package world.bentobox.gg.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Talks to any server that implements the OpenAI-style {@code POST /chat/completions} endpoint. That shape
 * is the closest thing to a standard: most hosted providers, aggregators and local model runners accept
 * it, so one client plus a configurable URL, model and key covers nearly everything.
 * <p>
 * Only the lowest common denominator is sent (model and messages) because optional parameters are where
 * "compatible" servers disagree.
 */
public class OpenAiCompatibleClient implements LlmClient {

    private static final int MAX_ERROR_BODY = 300;

    private final HttpClient http;
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final Duration timeout;
    private final boolean jsonMode;

    /**
     * @param baseUrl  e.g. {@code https://api.example.com/v1}; {@code /chat/completions} is appended
     *                 unless already present
     * @param model    model name as the provider knows it
     * @param apiKey   bearer token, or blank for servers that need none (local models)
     * @param timeout  how long to wait for a reply
     * @param jsonMode ask the server to guarantee a JSON reply. Not every server supports this.
     */
    public OpenAiCompatibleClient(String baseUrl, String model, String apiKey, Duration timeout,
            boolean jsonMode) {
        this.endpoint = endpoint(baseUrl);
        this.model = model;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.timeout = timeout;
        this.jsonMode = jsonMode;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    static URI endpoint(String baseUrl) {
        String url = baseUrl.strip();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return URI.create(url.endsWith("/chat/completions") ? url : url + "/chat/completions");
    }

    @Override
    public String complete(String system, String user) throws LlmException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        messages.add(message("user", user));
        body.add("messages", messages);
        if (jsonMode) {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_object");
            body.add("response_format", format);
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (!apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new LlmException("Could not reach " + endpoint + ": " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting for " + endpoint, e);
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new LlmException("The LLM provider rejected the API key (HTTP " + response.statusCode() + ")");
        }
        if (response.statusCode() / 100 != 2) {
            throw new LlmException("HTTP " + response.statusCode() + " from " + endpoint + ": "
                    + abbreviate(response.body()));
        }
        try {
            return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("choices").get(0)
                    .getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
        } catch (RuntimeException e) {
            // Any shape other than choices[0].message.content being a string.
            throw new LlmException("Unexpected response from " + endpoint + ": " + abbreviate(response.body()), e);
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    private static String abbreviate(String s) {
        return s.length() > MAX_ERROR_BODY ? s.substring(0, MAX_ERROR_BODY) + "..." : s;
    }
}
