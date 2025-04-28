package world.bentobox.gg;
import java.util.List;

public class Challenge {
    private final String id;
    private final String prompt;
    private final List<String> commands;
    public Challenge(String id, String prompt, List<String> commands) {
        this.id = id; this.prompt = prompt; this.commands = commands;
    }
    public String getId()       { return id; }
    public String getPrompt()   { return prompt; }
    public List<String> getCommands() { return commands; }
}
