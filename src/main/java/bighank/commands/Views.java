package bighank.commands;

import bighank.game.Bait;
import bighank.game.Fish;
import bighank.game.Inventory;
import bighank.game.Leaderboard;
import bighank.game.Rod;
import bighank.game.Species;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

/** Builds every message Big Hank sends: text, embeds, and buttons. No game logic lives here. */
final class Views {
    static final Color LAKE_BLUE = new Color(0x2E86AB);
    static final int LEADERBOARD_SIZE = 10;

    private Views() {}

    // ================================================================ fishing

    /** The public message for a catch. "Cast again" edits this same message, so a fishing trip is one message. */
    static MessageCreateData catchMessage(Inventory inv, long ownerId, Inventory.Catch c, long tripFish, long tripInches) {
        StringBuilder text = new StringBuilder("🎣 **").append(clean(inv.name())).append("** caught a **")
                .append(c.fish()).append("**!");
        if (c.personalBest() && inv.totalCaught() > 1) {
            text.append(" 🏆 New personal best!");
        }
        text.append("\n-# ").append(c.rod().displayName()).append(" · ");
        if (c.bait() == null) {
            text.append("no bait");
        } else {
            text.append(c.bait().displayName()).append(c.baitLeft() == 0 ? " (that was your last)" : " (" + c.baitLeft() + " left)");
        }
        text.append(String.format(" · this trip: %,d fish, %,d in", tripFish, tripInches));

        return new MessageCreateBuilder()
                .setContent(text.toString())
                .setComponents(fishingButtons(inv, ownerId, tripFish, tripInches))
                .build();
    }

    /** Replaces the catch message after the player sells from it. */
    static MessageCreateData saleMessage(Inventory inv, long ownerId, Inventory.Sale sale) {
        String text = String.format("💰 **%s** sold %,d fish for **%,d CarpCoins**!%n-# Wallet: %,d CarpCoins",
                clean(inv.name()), sale.fishSold(), sale.coinsEarned(), sale.newBalance());
        return new MessageCreateBuilder()
                .setContent(text)
                .setComponents(fishingButtons(inv, ownerId, 0, 0))
                .build();
    }

    private static ActionRow fishingButtons(Inventory inv, long ownerId, long tripFish, long tripInches) {
        return ActionRow.of(
                Button.primary(ButtonId.of(ButtonId.CAST, ownerId, tripFish, tripInches), "Cast again")
                        .withEmoji(Emoji.fromUnicode("🎣")),
                sellButton(inv, ButtonId.of(ButtonId.SELL_CATCH, ownerId)));
    }

    private static Button sellButton(Inventory inv, String id) {
        if (inv.fish().isEmpty()) {
            return Button.success(id, "Bucket's empty").withEmoji(Emoji.fromUnicode("💰")).asDisabled();
        }
        return Button.success(id, String.format("Sell %,d fish · %,d", inv.fish().size(), bucketValue(inv)))
                .withEmoji(Emoji.fromUnicode("💰"));
    }

    // ================================================================ store

    /** The private store: what you have, what you can buy, and buttons to buy or sell. */
    static MessageCreateData store(Inventory inv, long ownerId, String status) {
        StringBuilder rods = new StringBuilder();
        for (Rod rod : Rod.values()) {
            if (inv.ownsRod(rod)) {
                rods.append("✅ **").append(rod.displayName()).append("**: owned")
                        .append(rod == inv.bestRod() ? " *(fishing with this)*" : "").append('\n');
            } else if (inv.coins() >= rod.price()) {
                rods.append(String.format("🛒 **%s**: %,d%n", rod.displayName(), rod.price()));
            } else {
                rods.append(String.format("🔒 **%s**: %,d *(need %,d more)*%n",
                        rod.displayName(), rod.price(), rod.price() - inv.coins()));
            }
        }

        StringBuilder baits = new StringBuilder();
        for (Bait bait : Bait.values()) {
            baits.append(String.format("**%s**: %,d a pack · you have %d", bait.displayName(), bait.price(),
                    inv.baitCount(bait)));
            if (bait == inv.equippedBait()) {
                baits.append(" *(on your hook)*");
            }
            baits.append('\n');
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(LAKE_BLUE)
                .setTitle("🏪 Hank's Bait & Tackle")
                .setDescription(String.format("**Wallet:** %,d CarpCoins%n**Bucket:** %,d fish, worth %,d CarpCoins",
                        inv.coins(), inv.fish().size(), bucketValue(inv)))
                .addField("🎣 Rods (better rods reach bigger fish)", rods.toString(), false)
                .addField("🪱 Bait (" + Bait.USES_PER_PACK + " casts a pack, makes fish bigger)", baits.toString(), false)
                .setFooter("Pick bait when you cast: /fish bait:<name>. Hank remembers it until it runs out.");

        List<Button> rodButtons = new ArrayList<>();
        for (Rod rod : Rod.values()) {
            if (rod == Rod.STARTER) {
                continue;
            }
            String id = ButtonId.of(ButtonId.BUY_ROD, ownerId, rod.name());
            String label = String.format("%s · %,d", rod.displayName(), rod.price());
            if (inv.ownsRod(rod)) {
                rodButtons.add(Button.secondary(id, rod.displayName()).withEmoji(Emoji.fromUnicode("✅")).asDisabled());
            } else if (inv.coins() >= rod.price()) {
                rodButtons.add(Button.primary(id, label));
            } else {
                rodButtons.add(Button.secondary(id, label).withEmoji(Emoji.fromUnicode("🔒")).asDisabled());
            }
        }

        List<Button> baitButtons = new ArrayList<>();
        for (Bait bait : Bait.values()) {
            String id = ButtonId.of(ButtonId.BUY_BAIT, ownerId, bait.name());
            String label = String.format("%s ×%d · %,d", bait.displayName(), Bait.USES_PER_PACK, bait.price());
            baitButtons.add(inv.coins() >= bait.price()
                    ? Button.primary(id, label)
                    : Button.secondary(id, label).asDisabled());
        }

        return new MessageCreateBuilder()
                .setContent(status == null ? "" : status)
                .setEmbeds(embed.build())
                .setComponents(
                        ActionRow.of(rodButtons),
                        ActionRow.of(baitButtons),
                        ActionRow.of(sellButton(inv, ButtonId.of(ButtonId.STORE_SELL, ownerId))))
                .build();
    }

    // ================================================================ profile

    /** A player's stats, gear, and bucket. Viewing your own adds a Leave button. */
    static MessageCreateData profile(Inventory inv, long ownerId, long viewerId) {
        StringBuilder baits = new StringBuilder();
        for (Bait bait : Bait.values()) {
            int count = inv.baitCount(bait);
            if (count > 0) {
                baits.append(bait.displayName()).append(" ×").append(count).append('\n');
            }
        }

        StringBuilder rods = new StringBuilder();
        for (Rod rod : Rod.values()) {
            if (inv.ownsRod(rod)) {
                rods.append(rod.displayName()).append('\n');
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(LAKE_BLUE)
                .setTitle("🎣 " + clean(inv.name()) + "'s Profile")
                .addField("CarpCoins", String.format("%,d", inv.coins()), true)
                .addField("Fish caught", String.format("%,d", inv.totalCaught()), true)
                .addField("Earned all-time", String.format("%,d", inv.lifetimeEarnings()), true)
                .addField("Biggest catch", inv.biggestCatch() == null ? "Nothing yet" : inv.biggestCatch().toString(), true)
                .addField("Fishing with", inv.bestRod().displayName(), true)
                .addField("Bait on hook", inv.equippedBait() == null ? "None" : inv.equippedBait().displayName(), true)
                .addField("Rods", rods.toString(), true)
                .addField("Bait", baits.isEmpty() ? "None" : baits.toString(), true)
                .addField(String.format("Bucket (%,d fish, worth %,d)", inv.fish().size(), bucketValue(inv)),
                        bucketSummary(inv), false);

        MessageCreateBuilder message = new MessageCreateBuilder();
        if (ownerId == viewerId) {
            embed.setFooter("Leaving hides the lake channel from you. Your gear and coins are kept; /start brings you back.");
            message.setComponents(ActionRow.of(
                    Button.danger(ButtonId.of(ButtonId.LEAVE, ownerId), "Leave the lake").withEmoji(Emoji.fromUnicode("🚪"))));
        }
        return message.setEmbeds(embed.build()).build();
    }

    private static String bucketSummary(Inventory inv) {
        if (inv.fish().isEmpty()) {
            return "Empty. Go `/fish`!";
        }
        Map<Species, long[]> bySpecies = new EnumMap<>(Species.class); // {count, biggest}
        for (Fish f : inv.fish()) {
            long[] s = bySpecies.computeIfAbsent(f.species(), k -> new long[2]);
            s[0]++;
            s[1] = Math.max(s[1], f.size());
        }
        StringBuilder lines = new StringBuilder();
        bySpecies.forEach((species, s) -> lines.append(String.format("%s ×%d *(biggest %d in)*%n",
                species.displayName(), s[0], s[1])));
        return lines.toString();
    }

    // ================================================================ leaderboard

    static MessageCreateData leaderboard(List<Leaderboard.Entry> ranking, long viewerId, boolean global,
                                         Leaderboard.Category category) {
        StringBuilder board = new StringBuilder();
        for (Leaderboard.Entry e : ranking.subList(0, Math.min(LEADERBOARD_SIZE, ranking.size()))) {
            board.append(formatEntry(e, category, viewerId)).append('\n');
        }
        // Show the viewer where they stand, even outside the top 10.
        ranking.stream()
                .skip(LEADERBOARD_SIZE)
                .filter(e -> e.userId() == viewerId)
                .findFirst()
                .ifPresent(e -> board.append("…\n").append(formatEntry(e, category, viewerId)));

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(LAKE_BLUE)
                .setTitle("🏆 " + (global ? "Global" : "Server") + " Leaderboard: " + category.displayName())
                .setDescription(board.isEmpty() ? "Nobody's on the board yet. Go catch something!" : board)
                .setFooter(global ? "Anglers from every server" : "Anglers who joined this server's lake");

        // Every button id in a message must be unique, so scope and category tabs get different tags
        // (otherwise "Global" and the current category's tab would point at the same view).
        List<Button> scopes = List.of(
                tab(ButtonId.of(ButtonId.LEADERBOARD, viewerId, "server", category.name(), "scope"), "This server", "🏠", !global),
                tab(ButtonId.of(ButtonId.LEADERBOARD, viewerId, "global", category.name(), "scope"), "Global", "🌎", global));
        List<Button> categories = new ArrayList<>();
        for (Leaderboard.Category c : Leaderboard.Category.values()) {
            categories.add(tab(ButtonId.of(ButtonId.LEADERBOARD, viewerId, global ? "global" : "server", c.name(), "category"),
                    c.displayName(), null, c == category));
        }

        return new MessageCreateBuilder()
                .setEmbeds(embed.build())
                .setComponents(ActionRow.of(scopes), ActionRow.of(categories))
                .build();
    }

    /** A button that acts like a tab: the selected one is highlighted and can't be pressed again. */
    private static Button tab(String id, String label, String emoji, boolean selected) {
        Button button = selected ? Button.primary(id, label).asDisabled() : Button.secondary(id, label);
        return emoji == null ? button : button.withEmoji(Emoji.fromUnicode(emoji));
    }

    private static String formatEntry(Leaderboard.Entry e, Leaderboard.Category category, long viewerId) {
        String medal = switch (e.rank()) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "**" + e.rank() + ".**";
        };
        String name = clean(e.inventory().name());
        if (e.userId() == viewerId) {
            name = "__" + name + "__";
        }
        return medal + " " + name + ": " + category.describe(e.inventory());
    }

    // ================================================================ joining

    /** The private welcome after /start. Doubles as the "how to play" guide. */
    static String welcome(String name, long channelId, boolean newPlayer, boolean alreadyJoined) {
        String greeting;
        if (alreadyJoined) {
            greeting = "You're already part of Big Hank's Lake, **" + clean(name) + "**!";
        } else if (newPlayer) {
            greeting = String.format("🤠 Welcome to Big Hank's Lake, **%s**! Here's a %s and %,d CarpCoins to get "
                    + "you started.", clean(name), Rod.STARTER.displayName(), Inventory.STARTING_COINS);
        } else {
            greeting = "🤠 Welcome to this server's lake, **" + clean(name) + "**! Your gear came along with you.";
        }
        return greeting + "\n\nHead over to <#" + channelId + ">. Only anglers can see it. Then:\n"
                + "• `/fish`: cast a line. Add `bait:` to use bait; Hank remembers it until it runs out.\n"
                + "• `/store`: buy rods and bait, and sell your fish.\n"
                + "• `/profile`: your stats, gear, and bucket.\n"
                + "• `/leaderboard`: the top anglers here and everywhere.\n"
                + "-# Want out? `/profile` has a Leave button.";
    }

    // ================================================================ helpers

    static long bucketValue(Inventory inv) {
        return inv.fish().stream().mapToLong(Fish::value).sum();
    }

    /** Escapes Discord markdown so a name like "*bob*" can't mess up formatting. */
    static String clean(String name) {
        return MarkdownSanitizer.escape(name == null ? "Someone" : name);
    }
}
