package world.bentobox.gg;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import world.bentobox.bentobox.BentoBox;

public class ChatGPTService {
    private static final String URL = "https://api.openai.com/v1/chat/completions";
    private final String apiKey;
    private final GgAddon addon;

    public ChatGPTService(GgAddon addon, String apiKey) { 
        this.apiKey = apiKey; 
        this.addon = addon;
    }

    /**
     * Sends chat + challenge definitions to GPT,
     * expects JSON: { "<challengeId>": ["PlayerA","PlayerB"], ... }
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> evaluateChallenges(Map<String, Object> payload) {
        try {
            // Construct the JSON payload
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "gpt-4.1");

            JSONArray messages = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a Minecraft server assistant. JSON-only output.");
            messages.add(systemMessage);

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", new JSONObject(payload).toString());
            messages.add(userMessage);

            requestBody.put("messages", messages);

            // Sanitize the API key
            String sanitizedApiKey = apiKey != null ? apiKey.replace("“", "").replace("”", "").trim() : "";

            // Open connection
            @SuppressWarnings("deprecation")
            HttpURLConnection connection = (HttpURLConnection) new URL(URL).openConnection();
            connection.setRequestMethod("POST");

            // Use the sanitized API key
            connection.setRequestProperty("Authorization", "Bearer " + sanitizedApiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Send the request
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.toString().getBytes());
                os.flush();
            }

            // Check for HTTP response code
            int responseCode = connection.getResponseCode();
            BentoBox.getInstance().logDebug("HTTP Response Code: " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                addon.logError("Error: Unauthorized (401). Please check your API key.");
                return Collections.emptyMap();
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
                addon.logError("Error: Received HTTP response code " + responseCode);
                return Collections.emptyMap();
            }

            // Read the response
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            // Log the raw response for debugging
            BentoBox.getInstance().logDebug("Raw response from ChatGPT: " + response.toString());

            // Parse the response
            JSONParser parser = new JSONParser();
            JSONObject jsonResponse = (JSONObject) parser.parse(response.toString());
            JSONObject choice = (JSONObject) ((JSONArray) jsonResponse.get("choices")).get(0);
            JSONObject message = (JSONObject) choice.get("message");
            Object content = message.get("content");

            // Log the content for debugging
            BentoBox.getInstance().logDebug("Parsed content from ChatGPT: " + content);

            // Ensure content is a JSONObject
            if (!(content instanceof String)) {
                addon.logError("Unexpected content type: " + content.getClass().getName());
                return Collections.emptyMap();
            }

            // Parse the content into a Map
            JSONObject parsedContent = (JSONObject) parser.parse((String) content);
            Map<String, List<String>> result = new HashMap<>();
            for (Object key : parsedContent.keySet()) {
                String challengeId = (String) key;
                Object value = parsedContent.get(challengeId);

                // Log the value type for debugging
                BentoBox.getInstance().logDebug("Value for challenge ID " + challengeId + ": " + value + " (type: " + value.getClass().getName() + ")");

                // Skip unexpected keys like "challenges_count"
                if (!(value instanceof JSONArray)) {
                    addon.logError("Skipping unexpected key: " + challengeId + " with value type: " + value.getClass().getName());
                    continue;
                }

                // Convert JSONArray to List<String>
                JSONArray playersArray = (JSONArray) value;
                List<String> players = new ArrayList<>();
                for (Object player : playersArray) {
                    players.add((String) player);
                }
                result.put(challengeId, players);
            }

            return result;
        } catch (Exception e) {
            addon.logError("Exception occurred while parsing response: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }
}
