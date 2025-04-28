package world.bentobox.gg;
import com.fasterxml.jackson.databind.*;
import okhttp3.*;

import java.io.IOException;
import java.util.*;

public class ChatGPTService {
    private static final String URL = "https://api.openai.com/v1/chat/completions";
    private final OkHttpClient client = new OkHttpClient();
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatGPTService(String apiKey) { this.apiKey = apiKey; }

    /**
     * Sends chat + challenge definitions to GPT,
     * expects JSON: { "<challengeId>": ["PlayerA","PlayerB"], ... }
     */
    @SuppressWarnings("unchecked")
    public Map<String,List<String>> evaluateChallenges(Map<String,Object> payload) {
        try {
            String json = mapper.writeValueAsString(Map.of(
                "model", "gpt-4",
                "messages", List.of(
                  Map.of("role","system","content",
                         "You are a Minecraft server assistant. JSON-only output."),
                  Map.of("role","user","content", mapper.writeValueAsString(payload))
                )
            ));
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request req = new Request.Builder()
                .url(URL)
                .header("Authorization", "Bearer "+apiKey)
                .post(body)
                .build();

            try (Response resp = client.newCall(req).execute()) {
                JsonNode root = mapper.readTree(resp.body().string());
                String content = root.at("/choices/0/message/content").asText();
                return mapper.readValue(content, Map.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }
}
