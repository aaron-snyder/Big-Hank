package bighank.data;

import java.util.LinkedHashSet;
import java.util.Set;

/** Per-server state: which channel Big Hank plays in, and who has joined this server's lake. */
public final class GuildSettings {
    private Long fishingChannelId;
    private Set<Long> players = new LinkedHashSet<>();

    public Long fishingChannelId() {
        return fishingChannelId;
    }

    public void setFishingChannelId(long channelId) {
        this.fishingChannelId = channelId;
    }

    public boolean hasJoined(long userId) {
        return players.contains(userId);
    }

    public boolean join(long userId) {
        return players.add(userId);
    }

    public boolean leave(long userId) {
        return players.remove(userId);
    }

    public Set<Long> players() {
        return players;
    }
}
