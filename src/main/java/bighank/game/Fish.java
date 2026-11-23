package bighank.game;

import java.util.List;
import java.util.random.RandomGenerator;

/** A caught fish. It sells for one CarpCoin per inch. */
public final class Fish {
    private Species species;
    private int size;

    private Fish() {} // for Gson

    public Fish(Species species, int size) {
        this.species = species;
        this.size = size;
    }

    /** Rolls a random fish the rod can catch, sized up by the bait (if any). */
    static Fish roll(Rod rod, Bait bait, RandomGenerator rng) {
        List<Species> pool = Species.catchableWith(rod);
        Species species = pool.get(rng.nextInt(pool.size()));
        int bonus = bait == null ? 0 : bait.randomBonus(rng);
        return new Fish(species, species.randomSize(rng) + bonus);
    }

    public Species species() {
        return species;
    }

    public int size() {
        return size;
    }

    public long value() {
        return size;
    }

    @Override
    public String toString() {
        return size + " inch " + species.displayName();
    }
}
