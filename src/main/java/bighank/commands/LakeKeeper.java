package bighank.commands;

import bighank.data.GameStore;
import bighank.data.GuildSettings;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gives every server Big Hank's own channel, {@value #CHANNEL_NAME}.
 *
 * <ul>
 *   <li>When Big Hank joins a server (or starts up in one he never set up), he creates the channel, or adopts an
 *       existing channel with that name.</li>
 *   <li>If a server manager later deletes the channel, Big Hank respects that and does <em>not</em> recreate it
 *       on his own. {@code /setup} brings it back.</li>
 *   <li>When Big Hank is removed from a server, that server's settings are forgotten.</li>
 * </ul>
 *
 * <p>The only permission this needs is Manage Channels. The channel topic explains how to join, so Big Hank never
 * needs to send regular messages.
 */
public class LakeKeeper extends ListenerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(LakeKeeper.class);

    public static final String CHANNEL_NAME = "big-hanks-lake";
    static final String CHANNEL_TOPIC = "🎣 Big Hank's fishing lake! Type /start to join, then /fish to cast a line. "
            + "/commands lists everything. Not playing? Mute this channel and you'll never hear from Hank.";

    /** What happened when we tried to make sure a server has its lake. */
    public enum Status { ALREADY_EXISTS, ADOPTED, CREATED, MISSING_PERMISSION, IN_PROGRESS, FAILED }

    public record Result(Status status, Long channelId) {}

    private final GameStore store;
    private final Set<Long> creating = ConcurrentHashMap.newKeySet();

    public LakeKeeper(GameStore store) {
        this.store = store;
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        ensureLake(event.getGuild(), result -> logResult(event.getGuild(), result));
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        // Covers servers that added Big Hank while he was offline, or where creation failed last time.
        // A server that *had* a lake which was later deleted is left alone.
        Guild guild = event.getGuild();
        boolean neverSetUp;
        synchronized (store) {
            neverSetUp = store.guild(guild.getIdLong()).fishingChannelId() == null;
        }
        if (neverSetUp) {
            ensureLake(guild, result -> logResult(guild, result));
        }
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        synchronized (store) {
            store.removeGuild(event.getGuild().getIdLong());
            store.save();
        }
        LOG.info("Removed from server {}, forgot its settings", event.getGuild().getId());
    }

    /**
     * Makes sure the server has a working lake channel, creating it if needed. {@code onDone} is called exactly
     * once, possibly on another thread.
     */
    public void ensureLake(Guild guild, Consumer<Result> onDone) {
        long guildId = guild.getIdLong();
        synchronized (store) {
            GuildSettings settings = store.guild(guildId);
            Long current = settings.fishingChannelId();
            if (current != null && guild.getTextChannelById(current) != null) {
                onDone.accept(new Result(Status.ALREADY_EXISTS, current));
                return;
            }

            List<TextChannel> named = guild.getTextChannelsByName(CHANNEL_NAME, true);
            if (!named.isEmpty()) {
                long adopted = named.get(0).getIdLong();
                settings.setFishingChannelId(adopted);
                store.save();
                onDone.accept(new Result(Status.ADOPTED, adopted));
                return;
            }

            if (!guild.getSelfMember().hasPermission(Permission.MANAGE_CHANNEL)) {
                onDone.accept(new Result(Status.MISSING_PERMISSION, null));
                return;
            }

            if (!creating.add(guildId)) {
                onDone.accept(new Result(Status.IN_PROGRESS, null));
                return;
            }
        }

        try {
            guild.createTextChannel(CHANNEL_NAME)
                    .setTopic(CHANNEL_TOPIC)
                    .reason("Big Hank's fishing channel")
                    .queue(channel -> {
                        synchronized (store) {
                            store.guild(guildId).setFishingChannelId(channel.getIdLong());
                            store.save();
                        }
                        creating.remove(guildId);
                        onDone.accept(new Result(Status.CREATED, channel.getIdLong()));
                    }, error -> {
                        creating.remove(guildId);
                        LOG.warn("Couldn't create #{} in server {}", CHANNEL_NAME, guildId, error);
                        onDone.accept(new Result(Status.FAILED, null));
                    });
        } catch (RuntimeException e) { // e.g. permissions changed between the check and the request
            creating.remove(guildId);
            LOG.warn("Couldn't create #{} in server {}", CHANNEL_NAME, guildId, e);
            onDone.accept(new Result(Status.FAILED, null));
        }
    }

    private static void logResult(Guild guild, Result result) {
        switch (result.status()) {
            case CREATED -> LOG.info("Created #{} in server {}", CHANNEL_NAME, guild.getId());
            case ADOPTED -> LOG.info("Using existing #{} in server {}", CHANNEL_NAME, guild.getId());
            case MISSING_PERMISSION -> LOG.warn("No Manage Channels permission in server {}; "
                    + "a server manager can grant it and run /setup", guild.getId());
            default -> { }
        }
    }
}
