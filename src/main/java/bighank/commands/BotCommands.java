package bighank.commands;

import bighank.data.GameStore;
import bighank.data.GuildSettings;
import bighank.game.Bait;
import bighank.game.GameException;
import bighank.game.Inventory;
import bighank.game.Leaderboard;
import bighank.game.Rod;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Big Hank's six slash commands, plus the buttons on his messages.
 *
 * <ul>
 *   <li>{@code /start} works anywhere. It gives the player the Lake Angler role, which unlocks #big-hanks-lake.</li>
 *   <li>{@code /fish}, {@code /store}, {@code /profile}, and {@code /leaderboard} only work in the lake, which
 *       only anglers can see. Everything except catches is private (ephemeral), so the lake stays readable.</li>
 *   <li>{@code /setup} is for server managers, to rebuild the lake if it gets deleted.</li>
 * </ul>
 */
public class BotCommands extends ListenerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(BotCommands.class);
    static final String NO_BAIT = "NONE";

    private final GameStore store;
    private final LakeKeeper lakeKeeper;
    private final Random rng = new Random();

    public BotCommands(GameStore store, LakeKeeper lakeKeeper) {
        this.store = store;
        this.lakeKeeper = lakeKeeper;
    }

    // ================================================================ command definitions

    public static List<CommandData> definitions() {
        List<SlashCommandData> commands = List.of(
                Commands.slash("start", "Join Big Hank's Lake and unlock the #" + LakeKeeper.CHANNEL_NAME + " channel"),
                Commands.slash("fish", "Cast a line in the lake")
                        .addOptions(new OptionData(OptionType.STRING, "bait",
                                "Bait to use. Hank remembers it until it runs out", false, true)),
                Commands.slash("store", "Buy rods and bait, and sell your fish"),
                Commands.slash("profile", "Your stats, gear, and bucket (or another angler's)")
                        .addOptions(new OptionData(OptionType.USER, "angler", "Whose profile to look at")),
                Commands.slash("leaderboard", "The top anglers in this server and around the world"),
                Commands.slash("setup", "Rebuild Big Hank's lake channel and role (server managers only)")
                        // Hidden from everyone without Manage Server; also re-checked when it runs.
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)));

        List<CommandData> result = new ArrayList<>();
        for (SlashCommandData command : commands) {
            result.add(command.setContexts(InteractionContextType.GUILD));
        }
        return result;
    }

    // ================================================================ slash commands

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            replyPrivately(event, "Big Hank only fishes in servers.");
            return;
        }
        guarded(event, "/" + event.getName(), () -> {
            GuildSettings settings = store.guild(event.getGuild().getIdLong());
            switch (event.getName()) {
                case "start" -> start(event);
                case "setup" -> setup(event);
                default -> {
                    requireLake(event, settings);
                    Inventory inv = requirePlayer(event.getMember(), event.getUser(), settings);
                    long userId = event.getUser().getIdLong();
                    switch (event.getName()) {
                        case "fish" -> fish(event, inv);
                        case "store" -> event.reply(Views.store(inv, userId, null)).setEphemeral(true).queue();
                        case "profile" -> profile(event, inv);
                        case "leaderboard" -> event.reply(Views.leaderboard(
                                        Leaderboard.rank(store.playersIn(event.getGuild().getIdLong())), userId, false))
                                .setEphemeral(true).queue();
                        default -> replyPrivately(event, "Big Hank doesn't know that one.");
                    }
                }
            }
        });
    }

    private void start(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        User user = event.getUser();
        event.deferReply(true).queue();
        lakeKeeper.addAngler(guild, user.getIdLong(), result -> {
            String message;
            synchronized (store) {
                message = switch (result.status()) {
                    case READY -> {
                        GuildSettings settings = store.guild(guild.getIdLong());
                        boolean alreadyJoined = settings.hasJoined(user.getIdLong());
                        Inventory inv = store.player(user.getIdLong());
                        boolean newPlayer = inv == null;
                        if (newPlayer) {
                            inv = store.createPlayer(user.getIdLong(), user.getEffectiveName());
                        }
                        inv.setName(user.getEffectiveName());
                        settings.join(user.getIdLong());
                        store.save();
                        lakeKeeper.refreshFishGod(guild); // a returning angler may hold the biggest catch here
                        yield Views.welcome(inv.name(), result.channelId(), newPlayer, alreadyJoined);
                    }
                    case MISSING_PERMISSIONS -> "Big Hank can't open his lake yet. He's missing the "
                            + permissionNames(result.missing()) + " permission. Ask a server manager to fix that.";
                    case CHANNEL_DELETED -> "Big Hank's lake was drained (the channel was deleted). "
                            + "A server manager can run `/setup` to dig a new one.";
                    case ROLE_TOO_HIGH -> "Big Hank can't hand out the **" + LakeKeeper.ROLE_NAME + "** role. A server "
                            + "manager needs to drag Big Hank's role above it in Server Settings → Roles.";
                    case FAILED -> "Big Hank couldn't open the lake just now. Try again in a bit.";
                };
            }
            event.getHook().sendMessage(message).queue();
        });
    }

    private void setup(SlashCommandInteractionEvent event) {
        // Discord already hides /setup from non-managers, but server owners can change that, so check anyway.
        if (event.getMember() == null
                || !event.getMember().hasPermission(event.getGuildChannel(), Permission.MANAGE_SERVER)) {
            throw new GameException("Only folks with **Manage Server** can set up Big Hank.");
        }
        event.deferReply(true).queue();
        lakeKeeper.ensure(event.getGuild(), LakeKeeper.Mode.REBUILD, result -> {
            String message = switch (result.status()) {
                case READY -> (result.channelCreated() ? "🎣 Dug a fresh lake: <#" : "✅ <#")
                        + result.channelId() + (result.channelCreated() ? ">!" : "> is all set.")
                        + " Only members with the **" + LakeKeeper.ROLE_NAME + "** role can see it, and anyone can "
                        + "get that role by running `/start` from any channel.";
                case MISSING_PERMISSIONS -> "Big Hank is missing the " + permissionNames(result.missing())
                        + " permission. Add it to his role in Server Settings → Roles (or re-invite him with the "
                        + "link from his README), then run `/setup` again.";
                case ROLE_TOO_HIGH -> "Drag Big Hank's role above **" + LakeKeeper.ROLE_NAME + "** in Server Settings → "
                        + "Roles so he can hand it out, then run `/setup` again.";
                case CHANNEL_DELETED, FAILED -> "Discord wouldn't let Big Hank set up the lake. Check his "
                        + "permissions and try again.";
            };
            event.getHook().sendMessage(message).queue();
        });
    }

    private void fish(SlashCommandInteractionEvent event, Inventory inv) {
        String baitChoice = event.getOption("bait", OptionMapping::getAsString);
        if (baitChoice != null) {
            inv.equipBait(parseBait(baitChoice));
        }
        event.reply(castAndShow(event.getGuild(), inv, event.getUser().getIdLong(), 0, 0)).queue();
    }

    /**
     * Casts with the player's best rod and builds the catch message. If the catch is a personal best, it may change
     * who is Fish God in any server the player has joined, so those roles get refreshed too.
     */
    private MessageCreateData castAndShow(Guild guild, Inventory inv, long userId, long tripFish, long tripTenths) {
        long guildId = guild.getIdLong();
        boolean wasFishGod = Objects.equals(fishGodOf(guildId), userId);
        Inventory.Catch c = inv.cast(inv.bestRod(), System.currentTimeMillis(), rng);
        store.save();
        boolean isFishGod = Objects.equals(fishGodOf(guildId), userId);
        if (c.personalBest()) {
            for (long joinedGuildId : store.guildsJoinedBy(userId)) {
                Guild joined = guild.getJDA().getGuildById(joinedGuildId);
                if (joined != null) {
                    lakeKeeper.refreshFishGod(joined);
                }
            }
        }
        return Views.catchMessage(inv, userId, c, tripFish + 1, tripTenths + Math.round(c.fish().size() * 10),
                isFishGod, isFishGod && !wasFishGod);
    }

    /** This server's #1 angler by biggest catch, or null if nobody has caught anything. */
    private Long fishGodOf(long guildId) {
        return Leaderboard.leader(store.playersIn(guildId));
    }

    private void profile(SlashCommandInteractionEvent event, Inventory self) {
        long viewerId = event.getUser().getIdLong();
        User target = event.getOption("angler", OptionMapping::getAsUser);
        Long fishGod = fishGodOf(event.getGuild().getIdLong());
        if (target == null || target.getIdLong() == viewerId) {
            event.reply(Views.profile(self, viewerId, viewerId, Objects.equals(fishGod, viewerId)))
                    .setEphemeral(true).queue();
            return;
        }
        Inventory other = store.player(target.getIdLong());
        if (other == null) {
            throw new GameException(Views.clean(target.getEffectiveName()) + " hasn't joined Big Hank's Lake yet.");
        }
        event.reply(Views.profile(other, target.getIdLong(), viewerId, Objects.equals(fishGod, target.getIdLong())))
                .setEphemeral(true).queue();
    }

    // ================================================================ autocomplete (bait for /fish)

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("fish") || !event.getFocusedOption().getName().equals("bait")) {
            return;
        }
        String typed = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        List<Command.Choice> choices = new ArrayList<>();
        synchronized (store) {
            Inventory inv = store.player(event.getUser().getIdLong());
            if (inv != null) {
                for (Bait bait : Bait.values()) {
                    int count = inv.baitCount(bait);
                    if (count > 0) {
                        String label = bait.displayName() + " (" + count + " left)"
                                + (bait == inv.equippedBait() ? " · on your hook" : "");
                        choices.add(new Command.Choice(label, bait.name()));
                    }
                }
            }
        }
        choices.add(new Command.Choice("No bait", NO_BAIT));
        choices.removeIf(c -> !c.getName().toLowerCase(Locale.ROOT).contains(typed));
        event.replyChoices(choices).queue();
    }

    /** Turns the bait option into a bait, or null for "no bait". Accepts autocomplete values or typed names. */
    static Bait parseBait(String text) {
        String t = text.strip();
        if (t.equalsIgnoreCase(NO_BAIT) || t.equalsIgnoreCase("no bait")) {
            return null;
        }
        for (Bait bait : Bait.values()) {
            if (bait.name().equalsIgnoreCase(t) || bait.displayName().equalsIgnoreCase(t)) {
                return bait;
            }
        }
        String shown = t.length() > 40 ? t.substring(0, 40) + "…" : t;
        throw new GameException("Hank's never heard of \"" + Views.clean(shown) + "\". Pick a bait from the list.");
    }

    // ================================================================ buttons

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        ButtonId id = ButtonId.parse(event.getComponentId());
        if (id == null || !event.isFromGuild()) {
            return;
        }
        if (id.ownerId() != event.getUser().getIdLong()) {
            replyPrivately(event, "That's not your line, partner. Use `/fish` to cast your own.");
            return;
        }
        guarded(event, "button " + id.action(), () -> {
            GuildSettings settings = store.guild(event.getGuild().getIdLong());
            Inventory inv = requirePlayer(event.getMember(), event.getUser(), settings);
            long owner = id.ownerId();
            switch (id.action()) {
                case ButtonId.CAST -> edit(event, castAndShow(event.getGuild(), inv, owner, id.longArg(0), id.longArg(1)));
                case ButtonId.SELL_CATCH -> {
                    Inventory.Sale sale = inv.sellAll();
                    store.save();
                    edit(event, Views.saleMessage(inv, owner, sale));
                }
                case ButtonId.BUY_ROD -> storeAction(event, inv, () -> {
                    Rod rod = Rod.valueOf(id.arg(0));
                    inv.buyRod(rod);
                    return "✅ Yeehaw! You bought the **" + rod.displayName() + "**. Your next cast uses it.";
                });
                case ButtonId.BUY_BAIT -> storeAction(event, inv, () -> {
                    Bait bait = Bait.valueOf(id.arg(0));
                    int nowHave = inv.buyBait(bait, 1);
                    String hook = "";
                    if (inv.equippedBait() == null) {
                        inv.equipBait(bait);
                        hook = " It's on your hook for your next cast.";
                    }
                    return "✅ Bought " + Bait.USES_PER_PACK + " " + bait.displayName() + " (you have " + nowHave + ")."
                            + hook;
                });
                case ButtonId.STORE_SELL -> storeAction(event, inv, () -> {
                    Inventory.Sale sale = inv.sellAll();
                    return String.format("💰 Sold %,d fish for **%,d CarpCoins**!", sale.fishSold(), sale.coinsEarned());
                });
                case ButtonId.LEADERBOARD -> {
                    boolean global = "global".equals(id.arg(0));
                    edit(event, Views.leaderboard(Leaderboard.rank(
                            global ? store.allPlayers() : store.playersIn(event.getGuild().getIdLong())), owner, global));
                }
                case ButtonId.LEAVE -> leave(event, settings);
                default -> replyPrivately(event, "That button doesn't do anything anymore.");
            }
        });
    }

    /** Runs a store purchase/sale and redraws the store with the result (or the reason it failed) on top. */
    private void storeAction(ButtonInteractionEvent event, Inventory inv, Supplier<String> action) {
        String status;
        try {
            status = action.get();
            store.save();
        } catch (GameException e) {
            status = "❌ " + e.getMessage();
        }
        edit(event, Views.store(inv, event.getUser().getIdLong(), status));
    }

    private void leave(ButtonInteractionEvent event, GuildSettings settings) {
        long userId = event.getUser().getIdLong();
        settings.leave(userId);
        store.save();
        event.deferEdit().queue();
        lakeKeeper.refreshFishGod(event.getGuild()); // if they were Fish God, the title passes on
        lakeKeeper.removeAngler(event.getGuild(), userId, result -> event.getHook().editOriginal(
                new MessageEditBuilder()
                        .setContent("🚪 You've packed up and left the lake, so it's hidden from you and you're off this "
                                + "server's leaderboard. Your gear and coins are safe. `/start` brings you back anytime.")
                        .setReplace(true)
                        .build()).queue());
    }

    // ================================================================ checks

    private static void requireLake(SlashCommandInteractionEvent event, GuildSettings settings) {
        Long channelId = settings.fishingChannelId();
        if (channelId == null) {
            throw new GameException("Big Hank's lake isn't set up here yet. A server manager can run `/setup`.");
        }
        if (event.getGuild().getTextChannelById(channelId) == null) {
            throw new GameException("Big Hank's lake was drained (the channel was deleted). "
                    + "A server manager can run `/setup` to dig a new one.");
        }
        if (event.getChannelIdLong() != channelId) {
            throw new GameException(settings.hasJoined(event.getUser().getIdLong())
                    ? "Big Hank only fishes in <#" + channelId + ">. Head on over there!"
                    : "Use `/start` to join Big Hank's Lake first!");
        }
    }

    private Inventory requirePlayer(Member member, User user, GuildSettings settings) {
        long userId = user.getIdLong();
        Inventory inv = store.player(userId);
        boolean joined = settings.hasJoined(userId);
        if (!joined && hasAnglerRole(member, settings)) {
            // Someone gave them the role another way (a server manager, or Discord's onboarding). Welcome aboard.
            if (inv == null) {
                inv = store.createPlayer(userId, user.getEffectiveName());
            }
            settings.join(userId);
            store.save();
            joined = true;
        }
        if (inv == null || !joined) {
            throw new GameException("You haven't joined Big Hank's Lake yet! Use `/start` to grab your fishin' license.");
        }
        inv.setName(user.getEffectiveName()); // keep leaderboard names current
        return inv;
    }

    private static boolean hasAnglerRole(Member member, GuildSettings settings) {
        Long roleId = settings.anglerRoleId();
        return member != null && roleId != null && member.getRoles().stream().anyMatch(r -> r.getIdLong() == roleId);
    }

    // ================================================================ helpers

    /** Runs a handler under the store lock, turning rule violations into private replies. */
    private void guarded(IReplyCallback event, String what, Runnable handler) {
        try {
            synchronized (store) {
                handler.run();
            }
        } catch (GameException e) {
            replyPrivately(event, e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("{} failed", what, e);
            if (!event.isAcknowledged()) {
                replyPrivately(event, "Big Hank fell in the lake. Try that again in a bit.");
            }
        }
    }

    private static void edit(ButtonInteractionEvent event, MessageCreateData view) {
        event.editMessage(MessageEditData.fromCreateData(view)).queue();
    }

    private static void replyPrivately(IReplyCallback event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    private static String permissionNames(Set<Permission> permissions) {
        return permissions.stream().map(p -> "**" + p.getName() + "**").collect(Collectors.joining(", "));
    }
}
