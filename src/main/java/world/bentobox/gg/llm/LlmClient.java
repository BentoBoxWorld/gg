package world.bentobox.gg.llm;

/**
 * The only thing Gg needs from a language model: text in, text out. Implement this to add a provider whose
 * API is not OpenAI-compatible.
 */
public interface LlmClient {

    /**
     * Blocking call; always invoked off the main server thread.
     *
     * @param system instructions for the model
     * @param user   the content to judge
     * @return the model's text reply
     * @throws LlmException if the model could not be reached or did not give a usable reply
     */
    String complete(String system, String user) throws LlmException;
}
