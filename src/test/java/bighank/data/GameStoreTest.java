package bighank.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bighank.game.Bait;
import bighank.game.Inventory;
import bighank.game.Rod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameStoreTest {
    @TempDir
    Path dir;

    @Test
    void saveAndLoadRoundTrip() throws IOException { // original bug: /save always failed (Fish wasn't Serializable)
        Path file = dir.resolve("data/inventories.json");
        GameStore store = GameStore.load(file);

        Inventory inv = store.createPlayer(123456789012345678L, "hank");
        inv.buyBait(Bait.WORM, 2);
        inv.equipBait(Bait.WORM);
        inv.cast(Rod.STARTER, 100_000, new Random(7));
        store.guild(42L).setFishingChannelId(99L);
        store.guild(42L).join(123456789012345678L);
        store.save();

        GameStore reloaded = GameStore.load(file);
        Inventory back = reloaded.player(123456789012345678L);
        assertEquals("hank", back.name());
        assertEquals(inv.coins(), back.coins());
        assertEquals(9, back.baitCount(Bait.WORM));
        assertEquals(Bait.WORM, back.equippedBait());
        assertEquals(1, back.fish().size());
        assertEquals(inv.fish().get(0).toString(), back.fish().get(0).toString());
        assertTrue(back.ownsRod(Rod.STARTER));
        assertEquals(99L, reloaded.guild(42L).fishingChannelId());
        assertEquals(1, reloaded.playersIn(42L).size());
        assertTrue(reloaded.playersIn(7L).isEmpty());

        // The reloaded inventory still works (the original's Timer was null after loading).
        back.cast(Rod.STARTER, 200_000, new Random(8));
        assertEquals(2, back.fish().size());
    }

    @Test
    void corruptSaveFileStopsStartupInsteadOfWipingData() throws IOException {
        Path file = dir.resolve("inventories.json");
        Files.writeString(file, "{ this is not json");
        assertThrows(IOException.class, () -> GameStore.load(file));
    }

    @Test
    void loadsSaveFilesFromTheOlderVersion() throws IOException {
        // Whole-inch sizes, the old cooldown field, no catch times, no roles: what's on Railway right now.
        Path file = dir.resolve("old.json");
        Files.writeString(file, """
                {
                  "players": {
                    "123": {
                      "name": "aaron", "coins": 250, "rods": ["GUPPY_GETTER", "TROUT_TICKLER"],
                      "baits": {"WORM": 3}, "equippedBait": "WORM",
                      "fish": [{"species": "CATFISH", "size": 21}],
                      "lastCastMillis": 1763900000000, "totalCaught": 40, "lifetimeEarnings": 600,
                      "biggestCatch": {"species": "SALMON", "size": 33}
                    }
                  },
                  "guilds": {"773784535199776769": {"fishingChannelId": 42, "players": [123]}}
                }
                """);

        GameStore store = GameStore.load(file);
        Inventory inv = store.player(123L);

        assertEquals(250, inv.coins());
        assertEquals("21.0 inch Catfish", inv.fish().get(0).toString());
        assertEquals(33.0, inv.biggestCatch().size());
        assertEquals(0L, inv.biggestCatch().caughtAt());
        assertEquals(1, store.playersIn(773784535199776769L).size());
        assertEquals(null, store.guild(773784535199776769L).fishGodRoleId());
        inv.cast(Rod.TROUT_TICKLER, 1, new Random(1)); // and it still plays
        inv.cast(Rod.TROUT_TICKLER, 1, new Random(2)); // with no cooldown
    }
}
