package bighank.game;

import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/** A caught fish. It sells for one CarpCoin per inch (rounded). */
public final class Fish {
    private Species species;
    private double size;
    private long caughtAt; // epoch millis; breaks ties on the leaderboard (first to catch it wins)

    private Fish() {} // for Gson

    public Fish(Species species, double size, long caughtAt) {
        this.species = species;
        this.size = Math.round(size * 10) / 10.0;
        this.caughtAt = caughtAt;
    }

    /** Rolls a random fish the rod can catch, sized up by the bait (if any). */
    static Fish roll(Rod rod, Bait bait, RandomGenerator rng, long nowMillis) {
        List<Species> pool = Species.catchableWith(rod);
        Species species = pool.get(rng.nextInt(pool.size()));
        double bonus = bait == null ? 0 : bait.randomBonus(rng);
        return new Fish(species, species.randomSize(rng) + bonus, nowMillis);
    }

    public Species species() {
        return species;
    }

    /** Length in inches, to one decimal place. */
    public double size() {
        return size;
    }

    public long caughtAt() {
        return caughtAt;
    }

    public long value() {
        return Math.round(size);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%.1f inch %s", size, species.displayName());
    }
}
