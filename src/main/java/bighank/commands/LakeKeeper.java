package bighank.commands;

import bighank.data.GameStore;
import bighank.data.GuildSettings;
import bighank.game.Leaderboard;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds and maintains Big Hank's private lake in every server:
 *
 * <ul>
 *   <li>a <b>Lake Angler</b> role that grants nothing except access to the lake, and</li>
 *   <li>a <b>#big-hanks-lake</b> channel that only anglers (and Big Hank) can see.</li>
 * </ul>
 *
 * <p>It also hands the gold <b>Fish God</b> role to whoever holds the server's biggest catch, and moves it when
 * someone beats them.
 *
 * <p>Players get the role with {@code /start}, so nobody who hasn't joined ever sees the channel. If a server
 * manager deletes the channel, Big Hank respects that and only rebuilds it when someone runs {@code /setup}. The
 * role is internal plumbing, so it's quietly recreated whenever it's missing.
 *
 * <p>All Discord changes run one at a time on a background thread, so two requests can never race to create
 * duplicate channels or roles.
 */
public class LakeKeeper extends ListenerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(LakeKeeper.class);

    public static final String CHANNEL_NAME = "big-hanks-lake";
    public static final String ROLE_NAME = "Lake Angler";
    public static final String FISH_GOD_ROLE_NAME = "Fish God";
    private static final int FISH_GOD_COLOR = 0xF1C40F; // gold
    static final String CHANNEL_TOPIC = "🎣 Big Hank's fishing lake! /fish to cast a line, /store to buy gear "
            + "and sell your catch, /leaderboard for the top anglers. Only people who joined with /start can see this.";

    /** Everything Big Hank needs: create the channel, create/assign the role, and set who can see the channel. */
    public static final Set<Permission> REQUIRED_PERMISSIONS =
            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES);

    public enum Mode {
        /** Startup, joining a server, or a player joining: never recreates a channel a manager deleted. */
        AUTOMATIC,
        /** {@code /setup}: rebuilds the channel even if it was deleted. */
        REBUILD
    }

    public enum Status { READY, MISSING_PERMISSIONS, CHANNEL_DELETED, ROLE_TOO_HIGH, FAILED }

    /**
     * Outcome of making sure a server's lake is in order.
     *
     * @param missing permissions Big Hank lacks (only for {@link Status#MISSING_PERMISSIONS})
     * @param channelCreated whether a brand-new channel was made
     */
    public record Result(Status status, Long channelId, Long roleId, Set<Permission> missing,
                         boolean channelCreated) {
        static Result of(Status status) {
            return new Result(status, null, null, Set.of(), false);
        }
    }

    private final GameStore store;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lake-keeper");
        t.setDaemon(true);
        return t;
    });

    public LakeKeeper(GameStore store) {
        this.store = store;
    }

    // ================================================================ events

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        ensure(event.getGuild(), Mode.AUTOMATIC, result -> logResult(event.getGuild(), result));
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        // Catches servers that added Big Hank while he was offline, and upgrades older lakes to private ones.
        ensure(event.getGuild(), Mode.AUTOMATIC, result -> logResult(event.getGuild(), result));
        refreshFishGod(event.getGuild());
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        synchronized (store) {
            store.removeGuild(event.getGuild().getIdLong());
            store.save();
        }
        LOG.info("Removed from server {}, forgot its settings", event.getGuild().getId());
    }

    // ================================================================ public API (all async)

    /** Makes sure the lake exists and is private. {@code onDone} runs once, on the background thread. */
    public void ensure(Guild guild, Mode mode, Consumer<Result> onDone) {
        run(onDone, () -> ensureNow(guild, mode));
    }

    /** Lets a player into the lake by giving them the angler role. */
    public void addAngler(Guild guild, long userId, Consumer<Result> onDone) {
        run(onDone, () -> {
            Result result = ensureNow(guild, Mode.AUTOMATIC);
            if (result.status() == Status.READY) {
                guild.addRoleToMember(UserSnowflake.fromId(userId), guild.getRoleById(result.roleId()))
                        .reason("Joined Big Hank's lake")
                        .complete();
            }
            return result;
        });
    }

    /** Takes the angler role away, hiding the lake from the player again. */
    public void removeAngler(Guild guild, long userId, Consumer<Result> onDone) {
        run(onDone, () -> {
            Long roleId;
            synchronized (store) {
                roleId = store.guild(guild.getIdLong()).anglerRoleId();
            }
            Role role = roleId == null ? null : guild.getRoleById(roleId);
            if (role != null && guild.getSelfMember().canInteract(role)
                    && guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                guild.removeRoleFromMember(UserSnowflake.fromId(userId), role).reason("Left Big Hank's lake").complete();
            }
            return Result.of(Status.READY);
        });
    }

    /**
     * Makes sure the Fish God role belongs to this server's current #1 and nobody else Big Hank gave it to.
     * Cheap to call often: it only touches Discord when the #1 actually changes.
     */
    public void refreshFishGod(Guild guild) {
        worker.execute(() -> {
            try {
                refreshFishGodNow(guild);
            } catch (RuntimeException e) {
                LOG.warn("Couldn't update the Fish God in server {}", guild.getId(), e);
            }
        });
    }

    private void run(Consumer<Result> onDone, java.util.function.Supplier<Result> task) {
        worker.execute(() -> {
            Result result;
            try {
                result = task.get();
            } catch (RuntimeException e) {
                LOG.warn("Lake maintenance failed", e);
                result = Result.of(Status.FAILED);
            }
            onDone.accept(result);
        });
    }

    // ================================================================ the actual work (blocking)

    /** Does the work synchronously. Only call from the worker thread (or tests). */
    Result ensureNow(Guild guild, Mode mode) {
        long guildId = guild.getIdLong();
        Long channelId;
        Long roleId;
        List<Long> existingPlayers;
        synchronized (store) {
            GuildSettings settings = store.guild(guildId);
            channelId = settings.fishingChannelId();
            roleId = settings.anglerRoleId();
            existingPlayers = List.copyOf(settings.players());
        }

        TextChannel channel = channelId == null ? null : guild.getTextChannelById(channelId);
        if (channel == null && channelId != null && mode == Mode.AUTOMATIC) {
            return new Result(Status.CHANNEL_DELETED, null, roleId, Set.of(), false);
        }

        Member self = guild.getSelfMember();
        EnumSet<Permission> missing = EnumSet.noneOf(Permission.class);
        for (Permission p : REQUIRED_PERMISSIONS) {
            if (!self.hasPermission(p)) {
                missing.add(p);
            }
        }
        if (!missing.isEmpty()) {
            return new Result(Status.MISSING_PERMISSIONS, channelId, roleId, missing, false);
        }

        // 1. The role. Created with zero permissions; it only unlocks the lake channel.
        Role role = roleId == null ? null : guild.getRoleById(roleId);
        boolean newRole = false;
        if (role == null) {
            role = guild.createRole()
                    .setName(ROLE_NAME)
                    .setPermissions(0L)
                    .setMentionable(false)
                    .setHoisted(false)
                    .reason("Lets members see Big Hank's lake")
                    .complete();
            newRole = true;
            synchronized (store) {
                store.guild(guildId).setAnglerRoleId(role.getIdLong());
                store.save();
            }
        }
        if (!self.canInteract(role)) {
            return new Result(Status.ROLE_TOO_HIGH, channelId, role.getIdLong(), Set.of(), false);
        }

        // 2. The channel: reuse ours, adopt one with the right name, or create it already private.
        if (channel == null && channelId == null) {
            List<TextChannel> named = guild.getTextChannelsByName(CHANNEL_NAME, true);
            channel = named.isEmpty() ? null : named.get(0);
        }
        boolean created = false;
        if (channel == null) {
            long viewBit = Permission.VIEW_CHANNEL.getRawValue();
            channel = guild.createTextChannel(CHANNEL_NAME)
                    .setTopic(CHANNEL_TOPIC)
                    .addMemberPermissionOverride(self.getIdLong(), viewBit, 0)
                    .addRolePermissionOverride(role.getIdLong(), viewBit, 0)
                    .addRolePermissionOverride(guild.getPublicRole().getIdLong(), 0, viewBit)
                    .reason("Big Hank's fishing channel")
                    .complete();
            created = true;
        } else {
            makePrivate(guild, channel, role);
        }
        synchronized (store) {
            store.guild(guildId).setFishingChannelId(channel.getIdLong());
            store.save();
        }

        // 3. If the role is new (first upgrade to a private lake, or someone deleted it), give it back to
        //    everyone who had already joined so nobody gets locked out.
        if (newRole) {
            for (long userId : existingPlayers) {
                guild.addRoleToMember(UserSnowflake.fromId(userId), role).reason("Already a Big Hank angler")
                        .queue(null, error -> LOG.debug("Couldn't restore angler role for {}", userId, error));
            }
        }
        return new Result(Status.READY, channel.getIdLong(), role.getIdLong(), Set.of(), created);
    }

    /** Blocking version of {@link #refreshFishGod}. Only call from the worker thread (or tests). */
    void refreshFishGodNow(Guild guild) {
        long guildId = guild.getIdLong();
        Long leader;
        Long holder;
        Long roleId;
        synchronized (store) {
            GuildSettings settings = store.guild(guildId);
            leader = Leaderboard.leader(store.playersIn(guildId));
            holder = settings.fishGodUserId();
            roleId = settings.fishGodRoleId();
        }
        Member self = guild.getSelfMember();
        if (!self.hasPermission(Permission.MANAGE_ROLES)) {
            return; // the lake setup already reports missing permissions
        }

        Role role = roleId == null ? null : guild.getRoleById(roleId);
        if (role == null) {
            if (leader == null) {
                return; // nobody to crown yet
            }
            role = guild.createRole()
                    .setName(FISH_GOD_ROLE_NAME)
                    .setColor(FISH_GOD_COLOR)
                    .setPermissions(0L)
                    .setMentionable(false)
                    .setHoisted(false)
                    .reason("Title for the biggest catch in Big Hank's lake")
                    .complete();
            holder = null; // a brand-new role isn't held by anyone
            synchronized (store) {
                GuildSettings settings = store.guild(guildId);
                settings.setFishGodRoleId(role.getIdLong());
                settings.setFishGodUserId(null);
                store.save();
            }
        }
        if (Objects.equals(leader, holder)) {
            return;
        }
        if (!self.canInteract(role)) {
            LOG.warn("In server {}, the {} role is above Big Hank's role", guild.getId(), FISH_GOD_ROLE_NAME);
            return;
        }

        if (holder != null) {
            long dethroned = holder;
            guild.removeRoleFromMember(UserSnowflake.fromId(dethroned), role).reason("Someone caught a bigger fish")
                    .queue(null, error -> LOG.debug("Couldn't remove Fish God from {}", dethroned, error));
        }
        if (leader != null) {
            try {
                guild.addRoleToMember(UserSnowflake.fromId(leader), role).reason("Biggest catch in the lake").complete();
            } catch (RuntimeException e) {
                LOG.debug("Couldn't give Fish God to {} (they may have left the server)", leader, e);
            }
        }
        synchronized (store) {
            store.guild(guildId).setFishGodUserId(leader);
            store.save();
        }
    }

    /** Hides an existing channel from everyone except anglers and Big Hank. Skips anything already right. */
    private static void makePrivate(Guild guild, TextChannel channel, Role role) {
        // Let Big Hank and anglers in *before* locking @everyone out, so Big Hank never loses access mid-way.
        allowView(channel, guild.getSelfMember());
        allowView(channel, role);
        PermissionOverride everyone = channel.getPermissionOverride(guild.getPublicRole());
        if (everyone == null || !everyone.getDenied().contains(Permission.VIEW_CHANNEL)) {
            channel.upsertPermissionOverride(guild.getPublicRole()).deny(Permission.VIEW_CHANNEL)
                    .reason("Only Big Hank's anglers can see the lake").complete();
        }
        String topic = channel.getTopic();
        if (topic == null || topic.isBlank()) {
            channel.getManager().setTopic(CHANNEL_TOPIC).complete();
        }
    }

    private static void allowView(TextChannel channel, IPermissionHolder holder) {
        PermissionOverride override = channel.getPermissionOverride(holder);
        if (override == null || !override.getAllowed().contains(Permission.VIEW_CHANNEL)) {
            channel.upsertPermissionOverride(holder).grant(Permission.VIEW_CHANNEL)
                    .reason("Big Hank's lake access").complete();
        }
    }

    private static void logResult(Guild guild, Result result) {
        switch (result.status()) {
            case READY -> {
                if (result.channelCreated()) {
                    LOG.info("Created #{} in server {}", CHANNEL_NAME, guild.getId());
                }
            }
            case MISSING_PERMISSIONS -> LOG.warn("Server {} is missing permissions {}; a server manager needs to "
                    + "grant them (or re-invite Big Hank) and run /setup", guild.getId(), result.missing());
            case ROLE_TOO_HIGH -> LOG.warn("In server {}, the {} role is above Big Hank's role", guild.getId(), ROLE_NAME);
            case CHANNEL_DELETED -> LOG.info("#{} was deleted in server {}; waiting for /setup",
                    CHANNEL_NAME, guild.getId());
            case FAILED -> LOG.warn("Couldn't set up the lake in server {}", guild.getId());
        }
    }
}
