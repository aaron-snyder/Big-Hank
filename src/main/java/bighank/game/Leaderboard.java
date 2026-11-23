package bighank.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/** Ranks players by one of a few lifetime stats. */
public final class Leaderboard {

    public enum Category {
        EARNINGS("CarpCoins earned", Inventory::lifetimeEarnings),
        BIGGEST("Biggest catch", inv -> inv.biggestCatch() == null ? 0 : inv.biggestCatch().size()),
        CATCHES("Fish caught", Inventory::totalCaught);

        private final String displayName;
        private final ToLongFunction<Inventory> score;

        Category(String displayName, ToLongFunction<Inventory> score) {
            this.displayName = displayName;
            this.score = score;
        }

        public String displayName() {
            return displayName;
        }

        public long score(Inventory inv) {
            return score.applyAsLong(inv);
        }

        /** How a player's score reads on the board. */
        public String describe(Inventory inv) {
            return switch (this) {
                case EARNINGS -> String.format("%,d CarpCoins", inv.lifetimeEarnings());
                case BIGGEST -> String.valueOf(inv.biggestCatch());
                case CATCHES -> String.format("%,d fish", inv.totalCaught());
            };
        }
    }

    public record Entry(int rank, long userId, Inventory inventory) {}

    private Leaderboard() {}

    /**
     * Ranks every player with a non-zero score, best first. Ties share a rank.
     *
     * @param players user id to inventory, already filtered to the scope being ranked
     */
    public static List<Entry> rank(Map<Long, Inventory> players, Category category) {
        List<Map.Entry<Long, Inventory>> sorted = players.entrySet().stream()
                .filter(e -> category.score(e.getValue()) > 0)
                .sorted(Comparator.comparingLong((Map.Entry<Long, Inventory> e) -> category.score(e.getValue()))
                        .reversed()
                        .thenComparing(e -> e.getKey()))
                .toList();

        List<Entry> ranked = new ArrayList<>(sorted.size());
        long previousScore = Long.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<Long, Inventory> e = sorted.get(i);
            long score = category.score(e.getValue());
            if (score != previousScore) {
                rank = i + 1;
                previousScore = score;
            }
            ranked.add(new Entry(rank, e.getKey(), e.getValue()));
        }
        return ranked;
    }
}
