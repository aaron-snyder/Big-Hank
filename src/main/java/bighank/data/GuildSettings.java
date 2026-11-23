package bighank.data;

import java.util.LinkedHashSet;
import java.util.Set;

/** Per-server state: Big Hank's channel and roles, who has joined this server's lake, and who is Fish God. */
public final class GuildSettings {
    private Long fishingChannelId;
    private Long anglerRoleId;
    private Long fishGodRoleId;
    private Long fishGodUserId; // who Big Hank last gave the Fish God role to
    private Set<Long> players = new LinkedHashSet<>();

    public Long fishingChannelId() {
        return fishingChannelId;
    }

    public void setFishingChannelId(long channelId) {
        this.fishingChannelId = channelId;
    }

    /** The role that lets members see the lake channel. */
    public Long anglerRoleId() {
        return anglerRoleId;
    }

    public void setAnglerRoleId(long roleId) {
        this.anglerRoleId = roleId;
    }

    public Long fishGodRoleId() {
        return fishGodRoleId;
    }

    public void setFishGodRoleId(long roleId) {
        this.fishGodRoleId = roleId;
    }

    public Long fishGodUserId() {
        return fishGodUserId;
    }

    public void setFishGodUserId(Long userId) {
        this.fishGodUserId = userId;
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
