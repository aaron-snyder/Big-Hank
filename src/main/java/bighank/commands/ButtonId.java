package bighank.commands;

import java.util.Arrays;
import java.util.List;

/**
 * Button IDs look like {@code bh:<action>:<ownerId>:<args...>}. Everything a button needs is in its ID, so Big Hank
 * doesn't have to remember anything about old messages, and buttons keep working after a restart.
 *
 * @param ownerId the player the button belongs to; nobody else can press it
 */
record ButtonId(String action, long ownerId, List<String> args) {
    private static final String PREFIX = "bh";

    static final String CAST = "cast";
    static final String SELL_CATCH = "sellcatch";
    static final String BUY_ROD = "buyrod";
    static final String BUY_BAIT = "buybait";
    static final String STORE_SELL = "storesell";
    static final String LEADERBOARD = "lb";
    static final String LEAVE = "leave";

    static String of(String action, long ownerId, Object... args) {
        StringBuilder id = new StringBuilder(PREFIX).append(':').append(action).append(':').append(ownerId);
        for (Object arg : args) {
            id.append(':').append(arg);
        }
        return id.toString();
    }

    /** Parses a button ID, or returns null if it isn't one of Big Hank's. */
    static ButtonId parse(String id) {
        String[] parts = id.split(":");
        if (parts.length < 3 || !parts[0].equals(PREFIX)) {
            return null;
        }
        try {
            return new ButtonId(parts[1], Long.parseLong(parts[2]), List.of(Arrays.copyOfRange(parts, 3, parts.length)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    String arg(int index) {
        return args.get(index);
    }

    long longArg(int index) {
        return Long.parseLong(args.get(index));
    }
}
