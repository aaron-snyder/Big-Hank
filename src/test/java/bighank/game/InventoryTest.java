package bighank.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InventoryTest {
    private final Random rng = new Random(42);

    @Test
    void newPlayerStartsWithStarterRodAndCoins() {
        Inventory inv = new Inventory("hank");
        assertEquals(Inventory.STARTING_COINS, inv.coins());
        assertTrue(inv.ownsRod(Rod.STARTER));
        assertEquals(Rod.STARTER, inv.bestRod());
    }

    @Test
    void buyingDeductsCoins() { // original bug: purchases were free
        Inventory inv = new Inventory("hank");
        inv.buyRod(Rod.TROUT_TICKLER);
        assertEquals(0, inv.coins());
        assertTrue(inv.ownsRod(Rod.TROUT_TICKLER));
        assertEquals(Rod.TROUT_TICKLER, inv.bestRod());
    }

    @Test
    void cannotAffordOrDoubleBuy() {
        Inventory inv = new Inventory("hank");
        assertThrows(GameException.class, () -> inv.buyRod(Rod.CATFISH_CLOBBERER));
        assertEquals(Inventory.STARTING_COINS, inv.coins());
        assertThrows(GameException.class, () -> inv.buyRod(Rod.STARTER));
    }

    @Test
    void cannotFishWithUnownedRod() { // original bug: "/fish tuna" worked without owning it
        Inventory inv = new Inventory("hank");
        assertThrows(GameException.class, () -> inv.cast(Rod.TUNA_TAMER, 100_000, rng));
    }

    @Test
    void cooldownBlocksRapidCasts() {
        Inventory inv = new Inventory("hank");
        inv.cast(Rod.STARTER, 100_000, rng);
        assertThrows(GameException.class, () -> inv.cast(Rod.STARTER, 101_000, rng));
        inv.cast(Rod.STARTER, 100_000 + Inventory.CAST_COOLDOWN_MILLIS, rng);
        assertEquals(2, inv.fish().size());
    }

    @Test
    void sellSellsEveryFish() { // original bug: the loop only sold half the bucket
        Inventory inv = new Inventory("hank");
        long expected = 0;
        for (int i = 0; i < 7; i++) {
            expected += inv.cast(Rod.STARTER, 100_000L + i * Inventory.CAST_COOLDOWN_MILLIS, rng).fish().value();
        }
        Inventory.Sale sale = inv.sellAll();
        assertEquals(7, sale.fishSold());
        assertEquals(expected, sale.coinsEarned());
        assertTrue(inv.fish().isEmpty());
        assertEquals(Inventory.STARTING_COINS + expected, inv.coins());
        assertEquals(expected, inv.lifetimeEarnings());
        assertThrows(GameException.class, inv::sellAll);
    }

    @Test
    void baitIsUsedUpOnePerCast() { // original bug: bait was never consumed, and some baits could never be set
        Inventory inv = new Inventory("hank");
        assertThrows(GameException.class, () -> inv.equipBait(Bait.SPINNER_LURE));

        inv.buyBait(Bait.SPINNER_LURE, 1);
        assertEquals(Inventory.STARTING_COINS - Bait.SPINNER_LURE.price(), inv.coins());
        inv.equipBait(Bait.SPINNER_LURE);

        for (int i = 0; i < Bait.USES_PER_PACK; i++) {
            Inventory.Catch c = inv.cast(Rod.STARTER, 100_000L + i * Inventory.CAST_COOLDOWN_MILLIS, rng);
            assertEquals(Bait.SPINNER_LURE, c.bait());
            assertEquals(Bait.USES_PER_PACK - 1 - i, c.baitLeft());
            assertTrue(c.fish().size() >= 16, "spinner adds at least 15 inches");
        }
        assertEquals(0, inv.baitCount(Bait.SPINNER_LURE));
        assertNull(inv.equippedBait());
        assertNull(inv.cast(Rod.STARTER, 1_000_000, rng).bait());
    }

    @Test
    void betterRodsCatchEverythingWorseRodsCan() {
        assertEquals(Set.of(Species.GOLDFISH, Species.GUPPY, Species.PUFFERFISH),
                new HashSet<>(Species.catchableWith(Rod.GUPPY_GETTER)));
        assertEquals(Species.values().length, Species.catchableWith(Rod.TUNA_TAMER).size());
        for (int i = 0; i < 500; i++) {
            Fish f = Fish.roll(Rod.TROUT_TICKLER, null, rng);
            assertTrue(f.species().tier() <= Rod.TROUT_TICKLER.tier());
            assertTrue(f.size() >= 1);
        }
    }

    @Test
    void tracksPersonalBest() {
        Inventory inv = new Inventory("hank");
        Inventory.Catch first = inv.cast(Rod.STARTER, 100_000, rng);
        assertTrue(first.personalBest());
        assertEquals(first.fish(), inv.biggestCatch());
        assertEquals(1, inv.totalCaught());
    }
}
