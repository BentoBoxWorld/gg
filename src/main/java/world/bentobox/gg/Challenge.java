package world.bentobox.gg;

import java.util.List;

/**
 * Something players can be rewarded for saying.
 *
 * @param id         unique id, the key in config.yml
 * @param prompt     plain-language description of what qualifies. This is the only part the LLM sees.
 * @param commands   console commands to run for the winner; {@code %player%} is replaced by their name.
 *                   Never sent to the LLM.
 * @param dailyLimit how many times one player can earn this challenge per day
 */
public record Challenge(String id, String prompt, List<String> commands, int dailyLimit) {

    public Challenge {
        commands = List.copyOf(commands);
    }
}
