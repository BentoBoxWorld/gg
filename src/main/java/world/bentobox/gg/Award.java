package world.bentobox.gg;

/**
 * A decision that the player who wrote {@code line} satisfied {@code challenge}. Whether they are actually
 * paid is up to the {@link RewardManager}.
 */
public record Award(ChatLine line, Challenge challenge) {
}
