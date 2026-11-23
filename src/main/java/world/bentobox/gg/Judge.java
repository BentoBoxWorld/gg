package world.bentobox.gg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.JsonParseException;

import world.bentobox.gg.llm.LlmClient;
import world.bentobox.gg.llm.LlmException;
import world.bentobox.gg.VerdictParser.Verdict;

/**
 * One judging round: snapshot the transcript, ask the LLM, and turn its reply into {@link Award}s that
 * have been checked against what was actually said. Runs on the async polling thread, one round at a time.
 */
public class Judge {

    private final Transcript transcript;
    private final Map<String, Challenge> challenges;
    private final List<Challenge> challengeList;
    private final LlmClient client;
    private final Consumer<String> warn;

    /** Lines with a seq at or below this have already been judged. */
    private long judgedUpTo;

    public Judge(Transcript transcript, List<Challenge> challenges, LlmClient client, Consumer<String> warn) {
        this.transcript = transcript;
        this.challengeList = List.copyOf(challenges);
        this.challenges = challenges.stream().collect(Collectors.toMap(Challenge::id, Function.identity()));
        this.client = client;
        this.warn = warn;
    }

    /**
     * @return awards for lines not judged before. Empty, without calling the LLM, if nobody has spoken
     *         since the last round.
     * @throws LlmException if the LLM could not be asked. Nothing is marked as judged, so the same lines
     *                      are retried next round.
     */
    public List<Award> run() throws LlmException {
        List<ChatLine> lines = transcript.snapshot();
        long previous = judgedUpTo;
        if (lines.stream().noneMatch(l -> l.isChat() && l.seq() > previous)) {
            return List.of();
        }

        String reply = client.complete(PromptBuilder.SYSTEM, PromptBuilder.user(challengeList, lines, previous));
        judgedUpTo = lines.getLast().seq();

        List<Verdict> verdicts;
        try {
            verdicts = VerdictParser.parse(reply);
        } catch (JsonParseException e) {
            warn.accept("Could not understand the LLM's reply, so nothing was awarded this round: " + reply);
            return List.of();
        }

        // The reply is a claim, not a fact. Keep only verdicts that point at a real, new, player-written
        // line and a challenge that exists, and only once each.
        List<Award> awards = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Verdict v : verdicts) {
            if (v.line() < 1 || v.line() > lines.size()) {
                continue;
            }
            ChatLine line = lines.get(v.line() - 1);
            Challenge challenge = challenges.get(v.challengeId());
            if (challenge == null || !line.isChat() || line.seq() <= previous
                    || !seen.add(line.seq() + ":" + challenge.id())) {
                continue;
            }
            awards.add(new Award(line, challenge));
        }
        return awards;
    }
}
