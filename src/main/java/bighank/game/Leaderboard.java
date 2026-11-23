package bighank.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ranks anglers by the biggest fish they've ever caught. Ties go to whoever caught it first, so there's always
 * exactly one #1. In a server, that's the Fish God.
 */
public final class Leaderboard {

    public record Entry(int rank, long userId, Inventory inventory) {}

    /** Bigger fish first; for an exact tie, the earlier catch; then user id so the order never flickers. */
    private static final Comparator<Map.Entry<Long, Inventory>> ORDER =
            Comparator.<Map.Entry<Long, Inventory>>comparingDouble(e -> -e.getValue().biggestCatch().size())
                    .thenComparingLong(e -> e.getValue().biggestCatch().caughtAt())
                    .thenComparingLong(Map.Entry::getKey);

    private Leaderboard() {}

    /**
     * Everyone who has caught at least one fish, best first.
     *
     * @param players user id to inventory, already filtered to the scope being ranked
     */
    public static List<Entry> rank(Map<Long, Inventory> players) {
        List<Map.Entry<Long, Inventory>> sorted = players.entrySet().stream()
                .filter(e -> e.getValue().biggestCatch() != null)
                .sorted(ORDER)
                .toList();
        List<Entry> ranked = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            ranked.add(new Entry(i + 1, sorted.get(i).getKey(), sorted.get(i).getValue()));
        }
        return ranked;
    }

    /** The #1 angler's user id, or null if nobody has caught anything yet. */
    public static Long leader(Map<Long, Inventory> players) {
        return players.entrySet().stream()
                .filter(e -> e.getValue().biggestCatch() != null)
                .min(ORDER)
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
