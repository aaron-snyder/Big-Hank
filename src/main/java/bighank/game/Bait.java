package bighank.game;

import java.util.random.RandomGenerator;

/**
 * Bait makes fish bigger, and bigger fish sell for more. Each purchase is a pack of
 * {@link #USES_PER_PACK} casts, so on average every bait pays for itself.
 */
public enum Bait {
    WORM("Worm", 5, 1, 5),
    POWERBAIT("PowerBait", 10, 1, 10),
    GLITTER_POWERBAIT("Glitter PowerBait", 15, 1, 15),
    SPINNER_LURE("Spinner Lure", 20, 15, 20);

    public static final int USES_PER_PACK = 5;

    private final String displayName;
    private final long price;
    private final int minBonus; // inclusive
    private final int maxBonus; // exclusive

    Bait(String displayName, long price, int minBonus, int maxBonus) {
        this.displayName = displayName;
        this.price = price;
        this.minBonus = minBonus;
        this.maxBonus = maxBonus;
    }

    public String displayName() {
        return displayName;
    }

    /** Price of one pack. */
    public long price() {
        return price;
    }

    /** Extra inches this bait adds to a catch. */
    public int randomBonus(RandomGenerator rng) {
        return rng.nextInt(minBonus, maxBonus);
    }
}
