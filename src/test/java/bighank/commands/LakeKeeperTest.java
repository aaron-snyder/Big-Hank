package bighank.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bighank.commands.LakeKeeper.Result;
import bighank.commands.LakeKeeper.Status;
import bighank.data.GameStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LakeKeeperTest {
    private static final long GUILD = 1L;
    private static final long NEW_CHANNEL = 500L;

    @TempDir
    Path dir;

    private GameStore store;
    private LakeKeeper keeper;
    private Guild guild;
    private SelfMember self;
    private ChannelAction<TextChannel> createAction;
    private final List<Result> results = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws IOException {
        store = GameStore.load(dir.resolve("inventories.json"));
        keeper = new LakeKeeper(store);

        guild = mock(Guild.class);
        self = mock(SelfMember.class);
        when(guild.getIdLong()).thenReturn(GUILD);
        when(guild.getSelfMember()).thenReturn(self);
        when(guild.getTextChannelsByName(anyString(), eq(true))).thenReturn(List.of());

        // createTextChannel(...).setTopic(...).reason(...).queue(success, failure) "succeeds" with a new channel
        TextChannel created = channel(NEW_CHANNEL);
        createAction = mock(ChannelAction.class);
        when(guild.createTextChannel(LakeKeeper.CHANNEL_NAME)).thenReturn(createAction);
        when(createAction.setTopic(anyString())).thenReturn(createAction);
        when(createAction.reason(anyString())).thenReturn(createAction);
        doAnswer(inv -> {
            ((Consumer<TextChannel>) inv.getArgument(0)).accept(created);
            return null;
        }).when(createAction).queue(any(), any());
    }

    private static TextChannel channel(long id) {
        TextChannel channel = mock(TextChannel.class);
        when(channel.getIdLong()).thenReturn(id);
        return channel;
    }

    private Result ensure() {
        keeper.ensureLake(guild, results::add);
        assertEquals(1, results.size(), "callback runs exactly once");
        return results.remove(0);
    }

    @Test
    void createsTheChannelWhenMissing() {
        when(self.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true);

        Result result = ensure();

        assertEquals(Status.CREATED, result.status());
        assertEquals(NEW_CHANNEL, result.channelId());
        assertEquals(NEW_CHANNEL, store.guild(GUILD).fishingChannelId());
        verify(createAction).setTopic(LakeKeeper.CHANNEL_TOPIC);
    }

    @Test
    void reusesItsExistingChannel() {
        store.guild(GUILD).setFishingChannelId(42L);
        TextChannel existing = channel(42L);
        when(guild.getTextChannelById(42L)).thenReturn(existing);

        Result result = ensure();

        assertEquals(Status.ALREADY_EXISTS, result.status());
        verify(guild, never()).createTextChannel(anyString());
    }

    @Test
    void adoptsAChannelThatAlreadyHasTheName() {
        TextChannel named = channel(77L);
        when(guild.getTextChannelsByName(LakeKeeper.CHANNEL_NAME, true)).thenReturn(List.of(named));

        Result result = ensure();

        assertEquals(Status.ADOPTED, result.status());
        assertEquals(77L, store.guild(GUILD).fishingChannelId());
        verify(guild, never()).createTextChannel(anyString());
    }

    @Test
    void recreatesADeletedChannelWhenAsked() {
        store.guild(GUILD).setFishingChannelId(42L); // channel 42 no longer exists
        when(guild.getTextChannelById(anyLong())).thenReturn(null);
        when(self.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true);

        Result result = ensure();

        assertEquals(Status.CREATED, result.status());
        assertEquals(NEW_CHANNEL, store.guild(GUILD).fishingChannelId());
    }

    @Test
    void reportsMissingPermissionWithoutTrying() {
        when(self.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(false);

        Result result = ensure();

        assertEquals(Status.MISSING_PERMISSION, result.status());
        assertNull(store.guild(GUILD).fishingChannelId());
        verify(guild, never()).createTextChannel(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsFailureAndCanRetry() {
        when(self.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true);
        doAnswer(inv -> {
            ((Consumer<Throwable>) inv.getArgument(1)).accept(new RuntimeException("discord said no"));
            return null;
        }).when(createAction).queue(any(), any());

        assertEquals(Status.FAILED, ensure().status());
        assertEquals(Status.FAILED, ensure().status(), "a failure doesn't leave the server stuck 'in progress'");
    }

    @Test
    void doesNotCreateTwiceWhileARequestIsInFlight() {
        when(self.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true);
        doAnswer(inv -> null).when(createAction).queue(any(), any()); // never completes

        keeper.ensureLake(guild, results::add); // starts creating, no result yet
        assertEquals(0, results.size());
        assertEquals(Status.IN_PROGRESS, ensure().status());
    }
}
