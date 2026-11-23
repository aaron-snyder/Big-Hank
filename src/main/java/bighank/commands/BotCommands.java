package bighank.commands;

import bighank.data.GameStore;
import bighank.data.GuildSettings;
import bighank.game.Bait;
import bighank.game.Fish;
import bighank.game.GameException;
import bighank.game.Inventory;
import bighank.game.Leaderboard;
import bighank.game.Rod;
import bighank.game.Species;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every slash command Big Hank understands.
 *
 * <p>Big Hank only plays in one channel per server, picked by a server manager with {@code /setup}. Anywhere
 * else he answers privately (ephemeral replies only the player can see), so people who aren't playing never get
 * spammed. Players opt in to each server's lake with {@code /start}.
 */
public class BotCommands extends ListenerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(BotCommands.class);
    private static final Color LAKE_BLUE = new Color(0x2E86AB);
    private static final int LEADERBOARD_SIZE = 10;

    private final GameStore store;
    private final Random rng = new Random();

    public BotCommands(GameStore store) {
        this.store = store;
    }

    // ================================================================ command definitions

    public static List<CommandData> definitions() {
        OptionData rodOption = new OptionData(OptionType.STRING, "rod", "Which rod to use (defaults to your best one)");
        for (Rod rod : Rod.values()) {
            rodOption.addChoice(rod.displayName(), rod.name());
        }

        OptionData baitOption = new OptionData(OptionType.STRING, "bait", "Bait to put on your hook", true);
        for (Bait bait : Bait.values()) {
            baitOption.addChoice(bait.displayName(), bait.name());
        }
        baitOption.addChoice("None (take it off)", "NONE");

        OptionData itemOption = new OptionData(OptionType.STRING, "item", "What to buy", true);
        for (Rod rod : Rod.values()) {
            if (rod != Rod.STARTER) {
                itemOption.addChoice(String.format("%s (%,d CarpCoins)", rod.displayName(), rod.price()),
                        "rod:" + rod.name());
            }
        }
        for (Bait bait : Bait.values()) {
            itemOption.addChoice(String.format("%s, %d casts (%,d CarpCoins)",
                    bait.displayName(), Bait.USES_PER_PACK, bait.price()), "bait:" + bait.name());
        }

        OptionData scopeOption = new OptionData(OptionType.STRING, "scope", "This server or everyone (default: this server)")
                .addChoice("This server", "server")
                .addChoice("Global", "global");
        OptionData categoryOption = new OptionData(OptionType.STRING, "category", "What to rank by (default: CarpCoins earned)");
        for (Leaderboard.Category category : Leaderboard.Category.values()) {
            categoryOption.addChoice(category.displayName(), category.name());
        }

        List<SlashCommandData> commands = List.of(
                Commands.slash("start", "Get your fishin' license and join this server's lake"),
                Commands.slash("fish", "Cast your line and catch a fish").addOptions(rodOption),
                Commands.slash("inventory", "Check your CarpCoins, rods, bait, and bucket"),
                Commands.slash("getfish", "Show every fish in your bucket"),
                Commands.slash("setbait", "Choose which bait to use on your next casts").addOptions(baitOption),
                Commands.slash("store", "See what's for sale"),
                Commands.slash("buy", "Buy a rod or bait").addOptions(itemOption,
                        new OptionData(OptionType.INTEGER, "packs", "How many bait packs (default 1)")
                                .setRequiredRange(1, 100)),
                Commands.slash("sell", "Sell every fish in your bucket (1 CarpCoin per inch)"),
                Commands.slash("leaderboard", "See the top anglers").addOptions(scopeOption, categoryOption),
                Commands.slash("leave", "Leave this server's lake and its leaderboard (you keep your gear)"),
                Commands.slash("commands", "List everything Big Hank can do"),
                Commands.slash("setup", "Pick the channel Big Hank fishes in (server managers only)")
                        .addOptions(new OptionData(OptionType.CHANNEL, "channel", "Defaults to this channel")
                                .setChannelTypes(ChannelType.TEXT))
                        // Hidden from everyone without Manage Server; also re-checked when it runs.
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)));

        List<CommandData> result = new ArrayList<>();
        for (SlashCommandData command : commands) {
            result.add(command.setContexts(InteractionContextType.GUILD));
        }
        return result;
    }

    // ================================================================ dispatch

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            replyPrivately(event, "Big Hank only fishes in servers.");
            return;
        }
        try {
            synchronized (store) {
                handle(event);
            }
        } catch (GameException e) {
            replyPrivately(event, e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Command /{} failed", event.getName(), e);
            if (!event.isAcknowledged()) {
                replyPrivately(event, "Big Hank fell in the lake. Try that again in a bit.");
            }
        }
    }

    private void handle(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        GuildSettings guild = store.guild(guildId);

        // Commands that work in any channel. They only ever reply privately.
        switch (event.getName()) {
            case "commands" -> {
                commands(event, guild);
                return;
            }
            case "setup" -> {
                setup(event, guild);
                return;
            }
            case "leave" -> {
                leave(event, guild);
                return;
            }
            default -> requireFishingChannel(event, guild);
        }

        switch (event.getName()) {
            case "start" -> {
                start(event, guild);
                return;
            }
            case "leaderboard" -> { // anyone in the fishing channel can look, even before joining
                leaderboard(event, guildId);
                return;
            }
            default -> {}
        }

        Inventory inv = requirePlayer(event, guild);
        switch (event.getName()) {
            case "fish" -> fish(event, inv);
            case "inventory" -> inventory(event, inv);
            case "getfish" -> getFish(event, inv);
            case "setbait" -> setBait(event, inv);
            case "store" -> store(event, inv);
            case "buy" -> buy(event, inv);
            case "sell" -> sell(event, inv);
            default -> replyPrivately(event, "Big Hank doesn't know that one.");
        }
    }

    private static void requireFishingChannel(SlashCommandInteractionEvent event, GuildSettings guild) {
        Long channelId = guild.fishingChannelId();
        if (channelId == null) {
            throw new GameException("This server hasn't picked a fishin' hole yet. Someone with **Manage Server** "
                    + "needs to run `/setup` in the channel Big Hank should use.");
        }
        if (event.getChannelIdLong() != channelId) {
            throw new GameException("Big Hank only fishes in <#" + channelId + ">. Head on over there!");
        }
    }

    private Inventory requirePlayer(SlashCommandInteractionEvent event, GuildSettings guild) {
        long userId = event.getUser().getIdLong();
        Inventory inv = store.player(userId);
        if (inv == null || !guild.hasJoined(userId)) {
            throw new GameException("You haven't joined this lake yet! Use `/start` to grab your fishin' license.");
        }
        inv.setName(event.getUser().getEffectiveName()); // keep leaderboard names current
        return inv;
    }

    // ================================================================ commands

    private void setup(SlashCommandInteractionEvent event, GuildSettings guild) {
        // Discord already hides /setup from non-managers, but server owners can change that, so check anyway.
        if (event.getMember() == null
                || !event.getMember().hasPermission(event.getGuildChannel(), Permission.MANAGE_SERVER)) {
            throw new GameException("Only folks with **Manage Server** can set up Big Hank.");
        }

        // Discord only lets people pick text channels here (see setChannelTypes), so the raw id is all we need.
        long channelId;
        Long picked = event.getOption("channel", OptionMapping::getAsLong);
        if (picked != null) {
            channelId = picked;
        } else if (event.getChannelType() == ChannelType.TEXT) {
            channelId = event.getChannelIdLong();
        } else {
            throw new GameException("Run `/setup` in a regular text channel, or pick one with the `channel` option.");
        }

        guild.setFishingChannelId(channelId);
        store.save();
        replyPrivately(event, "🎣 Big Hank will fish in <#" + channelId + ">. Everywhere else he'll stay quiet.\n"
                + "Let your server know: head to <#" + channelId + "> and type `/start` to join!\n"
                + "-# Tip: make that channel opt-in (e.g. a role or Channels & Roles) so non-players never see it.");
    }

    private void start(SlashCommandInteractionEvent event, GuildSettings guild) {
        long userId = event.getUser().getIdLong();
        String name = event.getUser().getEffectiveName();
        Inventory inv = store.player(userId);

        if (inv == null) {
            inv = store.createPlayer(userId, name);
            guild.join(userId);
            store.save();
            event.reply(String.format("🤠 Welcome to the lake, **%s**! Here's a %s and %,d CarpCoins to get you started.\n"
                            + "Try `/fish`, then `/sell` your catch. `/commands` lists everything.",
                    clean(name), Rod.STARTER.displayName(), inv.coins())).queue();
        } else if (guild.join(userId)) {
            inv.setName(name);
            store.save();
            event.reply("🤠 Welcome to this lake, **" + clean(name) + "**! Your gear came along with you.").queue();
        } else {
            replyPrivately(event, "You're already fishin' here, partner. Go `/fish`!");
        }
    }

    private void leave(SlashCommandInteractionEvent event, GuildSettings guild) {
        if (!guild.leave(event.getUser().getIdLong())) {
            replyPrivately(event, "You weren't fishin' in this server anyway.");
            return;
        }
        store.save();
        replyPrivately(event, "You've packed up and left this server's lake, so you're off its leaderboard. "
                + "Your gear and coins are safe. `/start` brings you back anytime.");
    }

    private void fish(SlashCommandInteractionEvent event, Inventory inv) {
        String rodChoice = event.getOption("rod", OptionMapping::getAsString);
        Rod rod = rodChoice == null ? inv.bestRod() : Rod.valueOf(rodChoice);

        Inventory.Catch c = inv.cast(rod, System.currentTimeMillis(), rng);
        store.save();

        StringBuilder msg = new StringBuilder()
                .append("🎣 Yeehaw! **").append(clean(inv.name())).append("** caught a **").append(c.fish())
                .append("** with the ").append(c.rod().displayName()).append('!');
        if (c.bait() != null) {
            msg.append("\n-# Used a ").append(c.bait().displayName()).append(", ")
                    .append(c.baitLeft() == 0 ? "that was your last one." : c.baitLeft() + " left.");
        }
        if (c.personalBest() && inv.totalCaught() > 1) {
            msg.append("\n🏆 New personal best!");
        }
        event.reply(msg.toString()).queue();
    }

    private void inventory(SlashCommandInteractionEvent event, Inventory inv) {
        StringBuilder rods = new StringBuilder();
        for (Rod rod : Rod.values()) {
            if (inv.ownsRod(rod)) {
                rods.append("• ").append(rod.displayName()).append('\n');
            }
        }

        StringBuilder baits = new StringBuilder();
        for (Bait bait : Bait.values()) {
            int count = inv.baitCount(bait);
            if (count > 0) {
                baits.append("• ").append(bait.displayName()).append(" ×").append(count)
                        .append(bait == inv.equippedBait() ? " *(on the hook)*" : "").append('\n');
            }
        }

        long bucketValue = inv.fish().stream().mapToLong(Fish::value).sum();
        long cooldown = inv.cooldownRemainingMillis(System.currentTimeMillis());

        MessageEmbed embed = new EmbedBuilder()
                .setColor(LAKE_BLUE)
                .setTitle(clean(inv.name()) + "'s Tackle Box")
                .addField("CarpCoins", String.format("%,d", inv.coins()), true)
                .addField("Bucket", String.format("%,d fish (worth %,d)", inv.fish().size(), bucketValue), true)
                .addField("Ready to cast?", cooldown == 0 ? "Yep!" : "In " + ((cooldown + 999) / 1000) + "s", true)
                .addField("Rods", rods.toString(), true)
                .addField("Bait", baits.isEmpty() ? "None" : baits.toString(), true)
                .build();
        event.replyEmbeds(embed).setEphemeral(true).queue();
    }

    private void getFish(SlashCommandInteractionEvent event, Inventory inv) {
        if (inv.fish().isEmpty()) {
            replyPrivately(event, "Your bucket's empty. Go `/fish`!");
            return;
        }

        // Group by species so a big bucket still fits in one message.
        Map<Species, long[]> bySpecies = new EnumMap<>(Species.class); // {count, biggest, total value}
        for (Fish f : inv.fish()) {
            long[] s = bySpecies.computeIfAbsent(f.species(), k -> new long[3]);
            s[0]++;
            s[1] = Math.max(s[1], f.size());
            s[2] += f.value();
        }

        StringBuilder lines = new StringBuilder();
        long total = 0;
        for (Map.Entry<Species, long[]> e : bySpecies.entrySet()) {
            long[] s = e.getValue();
            lines.append(String.format("**%s** ×%d (biggest %d in, worth %,d)%n",
                    e.getKey().displayName(), s[0], s[1], s[2]));
            total += s[2];
        }

        MessageEmbed embed = new EmbedBuilder()
                .setColor(LAKE_BLUE)
                .setTitle(clean(inv.name()) + "'s Bucket")
                .setDescription(lines)
                .setFooter(String.format("%,d fish worth %,d CarpCoins. /sell to cash in.", inv.fish().size(), total))
                .build();
        event.replyEmbeds(embed).setEphemeral(true).queue();
    }

    private void setBait(SlashCommandInteractionEvent event, Inventory inv) {
        String choice = event.getOption("bait", OptionMapping::getAsString);
        if ("NONE".equals(choice)) {
            inv.equipBait(null);
            store.save();
            replyPrivately(event, "Took the bait off your hook.");
            return;
        }
        Bait bait = Bait.valueOf(choice);
        inv.equipBait(bait);
        store.save();
        replyPrivately(event, "Bait set! Your next casts use " + bait.displayName()
                + " (" + inv.baitCount(bait) + " left).");
    }

    private void store(SlashCommandInteractionEvent event, Inventory inv) {
        StringBuilder rods = new StringBuilder();
        for (Rod rod : Rod.values()) {
            if (rod == Rod.STARTER) {
                continue;
            }
            rods.append(inv.ownsRod(rod) ? "✅ " : "• ")
                    .append(String.format("**%s**: %,d CarpCoins%n", rod.displayName(), rod.price()));
        }
        StringBuilder baits = new StringBuilder();
        for (Bait bait : Bait.values()) {
            baits.append(String.format("• **%s**: %,d CarpCoins%n", bait.displayName(), bait.price()));
        }

        MessageEmbed embed = new EmbedBuilder()
                .setColor(LAKE_BLUE)
                .setTitle("🏪 Hank's Bait & Tackle")
                .addField("Rods (better rods catch bigger fish)", rods.toString(), false)
                .addField("Bait (packs of " + Bait.USES_PER_PACK + " casts, makes fish bigger)", baits.toString(), false)
                .setFooter(String.format("You've got %,d CarpCoins. Use /buy to purchase.", inv.coins()))
                .build();
        event.replyEmbeds(embed).setEphemeral(true).queue();
    }

    private void buy(SlashCommandInteractionEvent event, Inventory inv) {
        String item = event.getOption("item", OptionMapping::getAsString);
        String who = "**" + clean(inv.name()) + "**";

        if (item.startsWith("rod:")) {
            Rod rod = Rod.valueOf(item.substring("rod:".length()));
            inv.buyRod(rod);
            store.save();
            event.reply(String.format("🛒 Yeehaw! %s bought the **%s**! (%,d CarpCoins left)",
                    who, rod.displayName(), inv.coins())).queue();
        } else if (item.startsWith("bait:")) {
            Bait bait = Bait.valueOf(item.substring("bait:".length()));
            int packs = event.getOption("packs", 1, OptionMapping::getAsInt);
            int nowHave = inv.buyBait(bait, packs);
            String hookNote = "";
            if (inv.equippedBait() == null) {
                inv.equipBait(bait);
                hookNote = " It's on the hook and ready to go.";
            }
            store.save();
            event.reply(String.format("🛒 Yeehaw! %s bought %d pack%s of **%s** (now has %d).%s",
                    who, packs, packs == 1 ? "" : "s", bait.displayName(), nowHave, hookNote)).queue();
        } else {
            throw new GameException("Hank doesn't sell that.");
        }
    }

    private void sell(SlashCommandInteractionEvent event, Inventory inv) {
        Inventory.Sale sale = inv.sellAll();
        store.save();
        event.reply(String.format("💰 **%s** sold %,d fish for **%,d CarpCoins**! (Balance: %,d)",
                clean(inv.name()), sale.fishSold(), sale.coinsEarned(), sale.newBalance())).queue();
    }

    private void leaderboard(SlashCommandInteractionEvent event, long guildId) {
        boolean global = "global".equals(event.getOption("scope", "server", OptionMapping::getAsString));
        Leaderboard.Category category = Leaderboard.Category.valueOf(
                event.getOption("category", Leaderboard.Category.EARNINGS.name(), OptionMapping::getAsString));

        List<Leaderboard.Entry> ranking = Leaderboard.rank(
                global ? store.allPlayers() : store.playersIn(guildId), category);

        long me = event.getUser().getIdLong();
        StringBuilder board = new StringBuilder();
        for (Leaderboard.Entry e : ranking.subList(0, Math.min(LEADERBOARD_SIZE, ranking.size()))) {
            board.append(formatEntry(e, category, me)).append('\n');
        }
        // Show the caller where they stand, even if they're not in the top 10.
        ranking.stream()
                .skip(LEADERBOARD_SIZE)
                .filter(e -> e.userId() == me)
                .findFirst()
                .ifPresent(e -> board.append("…\n").append(formatEntry(e, category, me)));

        MessageEmbed embed = new EmbedBuilder()
                .setColor(LAKE_BLUE)
                .setTitle("🏆 " + (global ? "Global" : "Server") + " Leaderboard: " + category.displayName())
                .setDescription(board.isEmpty() ? "Nobody's on the board yet. Go catch something!" : board)
                .setFooter(global ? "Anglers from every server" : "Anglers who joined this server's lake")
                .build();
        event.replyEmbeds(embed).queue();
    }

    private static String formatEntry(Leaderboard.Entry e, Leaderboard.Category category, long me) {
        String medal = switch (e.rank()) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "**" + e.rank() + ".**";
        };
        String name = clean(e.inventory().name());
        if (e.userId() == me) {
            name = "__" + name + "__";
        }
        return medal + " " + name + ": " + category.describe(e.inventory());
    }

    private void commands(SlashCommandInteractionEvent event, GuildSettings guild) {
        String where = guild.fishingChannelId() == null
                ? "This server hasn't picked a fishin' hole yet. A server manager can run `/setup`."
                : "Big Hank fishes in <#" + guild.fishingChannelId() + ">.";
        replyPrivately(event, where + "\n\n"
                + "`/start`: Get your fishin' license and join this server's lake\n"
                + "`/fish [rod]`: Cast a line (uses your best rod unless you pick one)\n"
                + "`/inventory`: Your CarpCoins, rods, bait, and bucket\n"
                + "`/getfish`: Every fish in your bucket\n"
                + "`/setbait <bait>`: Put bait on your hook (one is used per cast)\n"
                + "`/store`: What Hank's selling\n"
                + "`/buy <item> [packs]`: Buy a rod or bait\n"
                + "`/sell`: Sell all your fish, 1 CarpCoin per inch\n"
                + "`/leaderboard [scope] [category]`: Top anglers here or everywhere\n"
                + "`/leave`: Leave this server's lake (you keep your gear)\n"
                + "`/setup [channel]`: Pick Big Hank's channel (server managers)");
    }

    // ================================================================ helpers

    private static void replyPrivately(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    /** Escapes Discord markdown so a name like "*bob*" can't mess up formatting. */
    private static String clean(String name) {
        return MarkdownSanitizer.escape(name == null ? "Someone" : name);
    }
}
