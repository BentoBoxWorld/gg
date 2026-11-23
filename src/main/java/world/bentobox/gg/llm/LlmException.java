package world.bentobox.gg.llm;

/**
 * The LLM request failed. Gg treats this as "no rewards this round" and tries again on the next poll.
 */
public class LlmException extends Exception {

    private static final long serialVersionUID = 1L;

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
