package bighank.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LeaderboardTest {

    /** A player whose biggest catch is a fish of the given size, caught at the given time. */
    private static Inventory caught(double size, long at) {
        Inventory inv = new Inventory("p" + size + "@" + at);
        // Reach in through a real catch, then overwrite its biggest fish deterministically.
        inv.cast(Rod.STARTER, at, new java.util.Random(1));
        try {
            var field = Inventory.class.getDeclaredField("biggestCatch");
            field.setAccessible(true);
            field.set(inv, new Fish(Species.TUNA, size, at));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return inv;
    }

    @Test
    void ranksByBiggestCatchAndFirstCatchWinsTies() {
        Map<Long, Inventory> players = new HashMap<>();
        players.put(1L, caught(60.5, 3_000));
        players.put(2L, caught(82.4, 5_000));
        players.put(3L, caught(82.4, 4_000)); // same size as player 2, but caught earlier
        players.put(4L, new Inventory("never fished"));

        List<Leaderboard.Entry> ranked = Leaderboard.rank(players);

        assertEquals(List.of(3L, 2L, 1L), ranked.stream().map(Leaderboard.Entry::userId).toList());
        assertEquals(List.of(1, 2, 3), ranked.stream().map(Leaderboard.Entry::rank).toList(), "no shared ranks");
        assertEquals(3L, Leaderboard.leader(players), "the Fish God");
    }

    @Test
    void nobodyLeadsAnEmptyLake() {
        assertNull(Leaderboard.leader(Map.of()));
        assertNull(Leaderboard.leader(Map.of(1L, new Inventory("never fished"))));
    }
}
