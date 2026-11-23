package bighank.game;

import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Every kind of fish in the lake.
 *
 * <p>Each species has a {@code tier}: a rod can catch every species whose tier is at or below its own,
 * so better rods keep catching the small stuff too (just like the original switch fall-through).
 */
public enum Species {
    GOLDFISH("Goldfish", 1, 1, 5),
    GUPPY("Guppy", 1, 1, 3),
    PUFFERFISH("Pufferfish", 1, 1, 3),
    RAINBOW_TROUT("Rainbow Trout", 2, 10, 18),
    SMALLMOUTH_BASS("Smallmouth Bass", 2, 5, 20),
    LARGEMOUTH_BASS("Largemouth Bass", 3, 6, 25),
    CATFISH("Catfish", 3, 10, 25),
    SALMON("Salmon", 4, 20, 40),
    HALIBUT("Halibut", 4, 20, 45),
    TUNA("Tuna", 5, 43, 83);

    private final String displayName;
    private final int tier;
    private final int minInches; // inclusive
    private final int maxInches; // exclusive

    Species(String displayName, int tier, int minInches, int maxInches) {
        this.displayName = displayName;
        this.tier = tier;
        this.minInches = minInches;
        this.maxInches = maxInches;
    }

    public String displayName() {
        return displayName;
    }

    public int tier() {
        return tier;
    }

    public int randomSize(RandomGenerator rng) {
        return rng.nextInt(minInches, maxInches);
    }

    /** All species a given rod can reel in. */
    public static List<Species> catchableWith(Rod rod) {
        return Arrays.stream(values()).filter(s -> s.tier <= rod.tier()).toList();
    }
}
