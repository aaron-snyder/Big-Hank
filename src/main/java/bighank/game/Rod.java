package bighank.game;

/** Fishing rods, from starter to endgame. Prices are in CarpCoins. */
public enum Rod {
    // Tuned so the Tuna Tamer takes roughly 1,000 casts with smart bait use (about 35 minutes of fishing),
    // or about 2,200 without bait.
    GUPPY_GETTER("Guppy Getter", 1, 0),
    TROUT_TICKLER("Trout Tickler", 2, 100),
    CATFISH_CLOBBERER("Catfish Clobberer", 3, 1_000),
    SALMON_SLAPPER("Salmon Slapper", 4, 5_000),
    TUNA_TAMER("Tuna Tamer", 5, 20_000);

    /** Every new player starts with this one. */
    public static final Rod STARTER = GUPPY_GETTER;

    private final String displayName;
    private final int tier;
    private final long price;

    Rod(String displayName, int tier, long price) {
        this.displayName = displayName;
        this.tier = tier;
        this.price = price;
    }

    public String displayName() {
        return displayName;
    }

    public int tier() {
        return tier;
    }

    public long price() {
        return price;
    }
}
