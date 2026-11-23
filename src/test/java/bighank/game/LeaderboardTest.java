package bighank.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LeaderboardTest {

    private static Inventory playerWhoCaught(int fish, Random rng) {
        Inventory inv = new Inventory("p" + fish);
        for (int i = 0; i < fish; i++) {
            inv.cast(Rod.STARTER, 100_000L + i * Inventory.CAST_COOLDOWN_MILLIS, rng);
        }
        return inv;
    }

    @Test
    void ranksByScoreSkipsZeroesAndSharesTies() {
        Random rng = new Random(1);
        Map<Long, Inventory> players = Map.of(
                1L, playerWhoCaught(3, rng),
                2L, playerWhoCaught(5, rng),
                3L, playerWhoCaught(3, rng),
                4L, new Inventory("newbie"));

        List<Leaderboard.Entry> ranked = Leaderboard.rank(players, Leaderboard.Category.CATCHES);

        assertEquals(3, ranked.size());
        assertEquals(2L, ranked.get(0).userId());
        assertEquals(1, ranked.get(0).rank());
        assertEquals(2, ranked.get(1).rank());
        assertEquals(2, ranked.get(2).rank());
    }
}
