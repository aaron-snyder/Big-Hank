package bighank.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bighank.commands.LakeKeeper.Mode;
import bighank.commands.LakeKeeper.Result;
import bighank.commands.LakeKeeper.Status;
import bighank.data.GameStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import net.dv8tion.jda.api.requests.restaction.RoleAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

/** Exercises LakeKeeper against a mocked Discord server. */
class LakeKeeperTest {
    private static final long GUILD = 1L;
    private static final long SELF = 900L;
    private static final long EVERYONE = GUILD; // @everyone's role id is the server id
    private static final long NEW_ROLE = 300L;
    private static final long NEW_CHANNEL = 500L;
    private static final long VIEW = Permission.VIEW_CHANNEL.getRawValue();

    @TempDir
    Path dir;

    private GameStore store;
    private LakeKeeper keeper;
    private Guild guild;
    private SelfMember self;
    private Role everyone;
    private Role newRole;
    private RoleAction roleAction;
    private ChannelAction<TextChannel> channelAction;
    private AuditableRestAction<Void> addRoleAction;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws IOException {
        store = GameStore.load(dir.resolve("inventories.json"));
        keeper = new LakeKeeper(store);

        guild = mock(Guild.class);
        when(guild.getIdLong()).thenReturn(GUILD);
        when(guild.getTextChannelsByName(anyString(), eq(true))).thenReturn(List.of());

        self = mock(SelfMember.class);
        when(self.getIdLong()).thenReturn(SELF);
        when(self.hasPermission(any(Permission.class))).thenReturn(true);
        when(self.canInteract(any(Role.class))).thenReturn(true);
        when(guild.getSelfMember()).thenReturn(self);

        everyone = role(EVERYONE);
        when(guild.getPublicRole()).thenReturn(everyone);

        newRole = role(NEW_ROLE);
        roleAction = mock(RoleAction.class, RETURNS_SELF);
        when(roleAction.complete()).thenReturn(newRole);
        when(guild.createRole()).thenReturn(roleAction);

        TextChannel created = channel(NEW_CHANNEL);
        channelAction = mock(ChannelAction.class, RETURNS_SELF);
        when(channelAction.complete()).thenReturn(created);
        when(guild.createTextChannel(LakeKeeper.CHANNEL_NAME)).thenReturn(channelAction);

        addRoleAction = mock(AuditableRestAction.class, RETURNS_SELF);
        when(guild.addRoleToMember(any(UserSnowflake.class), any(Role.class))).thenReturn(addRoleAction);
    }

    private static Role role(long id) {
        Role role = mock(Role.class);
        when(role.getIdLong()).thenReturn(id);
        return role;
    }

    private static TextChannel channel(long id) {
        TextChannel channel = mock(TextChannel.class);
        when(channel.getIdLong()).thenReturn(id);
        return channel;
    }

    /** A pre-existing public channel; returns the override action so tests can verify what changed. */
    private PermissionOverrideAction stubEditableChannel(TextChannel channel) {
        PermissionOverrideAction overrideAction = mock(PermissionOverrideAction.class, RETURNS_SELF);
        when(channel.upsertPermissionOverride(any(IPermissionHolder.class))).thenReturn(overrideAction);
        when(channel.getManager()).thenReturn(mock(TextChannelManager.class, RETURNS_SELF));
        return overrideAction;
    }

    private static PermissionOverride override(Set<Permission> allowed, Set<Permission> denied) {
        PermissionOverride o = mock(PermissionOverride.class);
        when(o.getAllowed()).thenReturn(allowed.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(allowed));
        when(o.getDenied()).thenReturn(denied.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(denied));
        return o;
    }

    private static UserSnowflake user(long id) {
        return argThat(u -> u != null && u.getIdLong() == id);
    }

    @Test
    void freshServerGetsAPrivateChannelAndAPowerlessRole() {
        Result result = keeper.ensureNow(guild, Mode.AUTOMATIC);

        assertEquals(Status.READY, result.status());
        assertTrue(result.channelCreated());
        assertEquals(NEW_CHANNEL, store.guild(GUILD).fishingChannelId());
        assertEquals(NEW_ROLE, store.guild(GUILD).anglerRoleId());

        verify(roleAction).setName(LakeKeeper.ROLE_NAME);
        verify(roleAction).setPermissions(0L); // the role grants nothing server-wide
        verify(channelAction).addRolePermissionOverride(EVERYONE, 0, VIEW); // hidden from everyone...
        verify(channelAction).addRolePermissionOverride(NEW_ROLE, VIEW, 0); // ...except anglers...
        verify(channelAction).addMemberPermissionOverride(SELF, VIEW, 0); // ...and Big Hank
    }

    @Test
    void existingPublicChannelIsMadePrivateWithoutLockingBigHankOut() {
        TextChannel existing = channel(42L);
        when(guild.getTextChannelsByName(LakeKeeper.CHANNEL_NAME, true)).thenReturn(List.of(existing));
        PermissionOverrideAction overrideAction = stubEditableChannel(existing);

        Result result = keeper.ensureNow(guild, Mode.AUTOMATIC);

        assertEquals(Status.READY, result.status());
        assertEquals(42L, store.guild(GUILD).fishingChannelId());
        verify(guild, never()).createTextChannel(anyString());

        InOrder order = inOrder(existing);
        order.verify(existing).upsertPermissionOverride(self);
        order.verify(existing).upsertPermissionOverride(newRole);
        order.verify(existing).upsertPermissionOverride(everyone); // @everyone is locked out last
        verify(overrideAction).deny(Permission.VIEW_CHANNEL);
    }

    @Test
    void playersWhoJoinedBeforeThePrivateLakeKeepAccess() {
        store.guild(GUILD).join(11L);
        store.guild(GUILD).join(22L);

        keeper.ensureNow(guild, Mode.AUTOMATIC);

        verify(guild).addRoleToMember(user(11L), eq(newRole));
        verify(guild).addRoleToMember(user(22L), eq(newRole));
    }

    @Test
    void alreadyPrivateLakeNeedsNoChanges() {
        TextChannel lake = channel(42L);
        Role angler = role(77L);
        store.guild(GUILD).setFishingChannelId(42L);
        store.guild(GUILD).setAnglerRoleId(77L);
        when(guild.getTextChannelById(42L)).thenReturn(lake);
        when(guild.getRoleById(77L)).thenReturn(angler);
        when(lake.getTopic()).thenReturn("already has a topic");
        PermissionOverride allowView = override(Set.of(Permission.VIEW_CHANNEL), Set.of());
        PermissionOverride denyView = override(Set.of(), Set.of(Permission.VIEW_CHANNEL));
        when(lake.getPermissionOverride(self)).thenReturn(allowView);
        when(lake.getPermissionOverride(angler)).thenReturn(allowView);
        when(lake.getPermissionOverride(everyone)).thenReturn(denyView);

        Result result = keeper.ensureNow(guild, Mode.AUTOMATIC);

        assertEquals(Status.READY, result.status());
        verify(guild, never()).createRole();
        verify(guild, never()).createTextChannel(anyString());
        verify(lake, never()).upsertPermissionOverride(any(IPermissionHolder.class));
    }

    @Test
    void deletedChannelStaysDeletedUntilSetup() {
        store.guild(GUILD).setFishingChannelId(42L); // no longer exists
        when(guild.getTextChannelById(anyLong())).thenReturn(null);

        assertEquals(Status.CHANNEL_DELETED, keeper.ensureNow(guild, Mode.AUTOMATIC).status());
        verify(guild, never()).createTextChannel(anyString());

        Result rebuilt = keeper.ensureNow(guild, Mode.REBUILD);
        assertEquals(Status.READY, rebuilt.status());
        assertTrue(rebuilt.channelCreated());
        assertEquals(NEW_CHANNEL, store.guild(GUILD).fishingChannelId());
    }

    @Test
    void missingPermissionsAreReportedBeforeChangingAnything() {
        when(self.hasPermission(Permission.MANAGE_ROLES)).thenReturn(false);

        Result result = keeper.ensureNow(guild, Mode.AUTOMATIC);

        assertEquals(Status.MISSING_PERMISSIONS, result.status());
        assertEquals(Set.of(Permission.MANAGE_ROLES), result.missing());
        verify(guild, never()).createRole();
        verify(guild, never()).createTextChannel(anyString());
    }

    @Test
    void roleAboveBigHankIsReported() {
        when(self.canInteract(any(Role.class))).thenReturn(false);

        assertEquals(Status.ROLE_TOO_HIGH, keeper.ensureNow(guild, Mode.AUTOMATIC).status());
        verify(guild, never()).createTextChannel(anyString());
    }

    @Test
    void addAnglerGivesThePlayerTheRole() throws Exception {
        when(guild.getRoleById(NEW_ROLE)).thenReturn(newRole);
        CompletableFuture<Result> done = new CompletableFuture<>();

        keeper.addAngler(guild, 55L, done::complete);

        assertEquals(Status.READY, done.get(5, TimeUnit.SECONDS).status());
        verify(guild).addRoleToMember(user(55L), eq(newRole));
        verify(addRoleAction).complete();
    }

    @Test
    void failuresAreReportedInsteadOfThrown() throws Exception {
        when(roleAction.complete()).thenThrow(new RuntimeException("discord said no"));
        CompletableFuture<Result> done = new CompletableFuture<>();

        keeper.ensure(guild, Mode.AUTOMATIC, done::complete);

        assertEquals(Status.FAILED, done.get(5, TimeUnit.SECONDS).status());
    }
}
