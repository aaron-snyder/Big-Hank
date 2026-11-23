package bighank.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * One player's progress. A player has a single inventory that follows them to every server.
 *
 * <p>All rule checks live here so the Discord layer only has to translate commands and print results.
 * Rule violations throw {@link GameException} with a message meant for the player.
 */
public final class Inventory {
    public static final long STARTING_COINS = 100;
    public static final long CAST_COOLDOWN_MILLIS = 5_000;

    private String name;
    private long coins = STARTING_COINS;
    private Set<Rod> rods = new LinkedHashSet<>(List.of(Rod.STARTER));
    private Map<Bait, Integer> baits = new EnumMap<>(Bait.class);
    private Bait equippedBait;
    private List<Fish> fish = new ArrayList<>();
    private long lastCastMillis;

    // Lifetime stats, used by the leaderboards
    private long totalCaught;
    private long lifetimeEarnings;
    private Fish biggestCatch;

    private Inventory() {} // for Gson

    public Inventory(String name) {
        this.name = name;
    }

    /** What happened on a successful cast. {@code bait} is null if none was used. */
    public record Catch(Fish fish, Rod rod, Bait bait, int baitLeft, boolean personalBest) {}

    /** What happened on a successful sale. */
    public record Sale(int fishSold, long coinsEarned, long newBalance) {}

    // ---------------------------------------------------------------- fishing

    public Catch cast(Rod rod, long nowMillis, RandomGenerator rng) {
        if (!rods.contains(rod)) {
            throw new GameException("Yain't got the " + rod.displayName() + "! Check the `/store`.");
        }
        long waitMillis = cooldownRemainingMillis(nowMillis);
        if (waitMillis > 0) {
            long seconds = (waitMillis + 999) / 1000;
            throw new GameException("Whoa there! Wait " + seconds + " more second" + (seconds == 1 ? "" : "s")
                    + " before fishin' again.");
        }

        Bait usedBait = equippedBait;
        Fish caught = Fish.roll(rod, usedBait, rng);
        int baitLeft = 0;
        if (usedBait != null) {
            baitLeft = baitCount(usedBait) - 1;
            setBaitCount(usedBait, baitLeft);
            if (baitLeft == 0) {
                equippedBait = null;
            }
        }

        boolean personalBest = biggestCatch == null || caught.size() > biggestCatch.size();
        if (personalBest) {
            biggestCatch = caught;
        }
        fish.add(caught);
        totalCaught++;
        lastCastMillis = nowMillis;
        return new Catch(caught, rod, usedBait, baitLeft, personalBest);
    }

    public long cooldownRemainingMillis(long nowMillis) {
        return Math.max(0, lastCastMillis + CAST_COOLDOWN_MILLIS - nowMillis);
    }

    /** The highest-tier rod the player owns. */
    public Rod bestRod() {
        Rod best = Rod.STARTER;
        for (Rod rod : rods) {
            if (rod.tier() > best.tier()) {
                best = rod;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- store

    public void buyRod(Rod rod) {
        if (rods.contains(rod)) {
            throw new GameException("You already own the " + rod.displayName() + ", partner.");
        }
        spend(rod.price(), "the " + rod.displayName());
        rods.add(rod);
    }

    /** Buys {@code packs} packs of bait and returns how many casts' worth the player now has. */
    public int buyBait(Bait bait, int packs) {
        if (packs < 1) {
            throw new GameException("You gotta buy at least one pack.");
        }
        spend(bait.price() * packs, packs + " pack" + (packs == 1 ? "" : "s") + " of " + bait.displayName());
        int newCount = baitCount(bait) + packs * Bait.USES_PER_PACK;
        setBaitCount(bait, newCount);
        return newCount;
    }

    public Sale sellAll() {
        if (fish.isEmpty()) {
            throw new GameException("Yain't got any fish to sell! Go `/fish` first.");
        }
        long earned = 0;
        for (Fish f : fish) {
            earned += f.value();
        }
        int sold = fish.size();
        fish.clear();
        coins += earned;
        lifetimeEarnings += earned;
        return new Sale(sold, earned, coins);
    }

    private void spend(long price, String what) {
        if (coins < price) {
            throw new GameException(String.format(
                    "Yain't got enough CarpCoins! %s costs %,d and you've only got %,d.",
                    capitalize(what), price, coins));
        }
        coins -= price;
    }

    // ---------------------------------------------------------------- bait

    /** Equips a bait for upcoming casts, or unequips with {@code null}. Bait is used up one per cast. */
    public void equipBait(Bait bait) {
        if (bait != null && baitCount(bait) == 0) {
            throw new GameException("Yain't got any " + bait.displayName() + "! Buy some at the `/store`.");
        }
        equippedBait = bait;
    }

    public int baitCount(Bait bait) {
        return baits.getOrDefault(bait, 0);
    }

    private void setBaitCount(Bait bait, int count) {
        if (count <= 0) {
            baits.remove(bait);
        } else {
            baits.put(bait, count);
        }
    }

    // ---------------------------------------------------------------- getters

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long coins() {
        return coins;
    }

    public boolean ownsRod(Rod rod) {
        return rods.contains(rod);
    }

    public Bait equippedBait() {
        return equippedBait;
    }

    public List<Fish> fish() {
        return Collections.unmodifiableList(fish);
    }

    public long totalCaught() {
        return totalCaught;
    }

    public long lifetimeEarnings() {
        return lifetimeEarnings;
    }

    public Fish biggestCatch() {
        return biggestCatch;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
