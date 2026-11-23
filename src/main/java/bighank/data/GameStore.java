package bighank.data;

import bighank.game.Inventory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds every player and server setting, and saves them to a JSON file after each change.
 *
 * <p>Not thread-safe on its own: callers synchronize on the store while they read or change it.
 */
public final class GameStore {
    private static final Logger LOG = LoggerFactory.getLogger(GameStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The on-disk shape of the save file. */
    private static final class SaveData {
        Map<Long, Inventory> players = new LinkedHashMap<>();
        Map<Long, GuildSettings> guilds = new LinkedHashMap<>();
    }

    private final Path file;
    private final SaveData data;

    private GameStore(Path file, SaveData data) {
        this.file = file;
        this.data = data;
    }

    /**
     * Loads the save file, or starts fresh if it doesn't exist yet. A corrupt file stops startup
     * instead of being silently overwritten, so nobody loses progress.
     */
    public static GameStore load(Path file) throws IOException {
        if (!Files.exists(file)) {
            LOG.info("No save file at {}, starting a fresh lake", file.toAbsolutePath());
            return new GameStore(file, new SaveData());
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            SaveData data = GSON.fromJson(reader, SaveData.class);
            if (data == null) {
                data = new SaveData();
            }
            if (data.players == null) {
                data.players = new LinkedHashMap<>();
            }
            if (data.guilds == null) {
                data.guilds = new LinkedHashMap<>();
            }
            LOG.info("Loaded {} players and {} servers from {}", data.players.size(), data.guilds.size(), file);
            return new GameStore(file, data);
        } catch (JsonParseException e) {
            throw new IOException("Save file " + file + " is corrupt; fix or move it before starting", e);
        }
    }

    /** Writes everything to disk. Writes to a temp file first so a crash mid-save can't corrupt the data. */
    public synchronized void save() {
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "inventories", ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.error("Failed to save game data to {}", file, e);
        }
    }

    // ---------------------------------------------------------------- players

    /** The player's inventory, or null if they've never run /start. */
    public Inventory player(long userId) {
        return data.players.get(userId);
    }

    public Inventory createPlayer(long userId, String name) {
        Inventory inv = new Inventory(name);
        data.players.put(userId, inv);
        return inv;
    }

    public Map<Long, Inventory> allPlayers() {
        return data.players;
    }

    /** Players who have joined the given server's lake. */
    public Map<Long, Inventory> playersIn(long guildId) {
        Map<Long, Inventory> result = new HashMap<>();
        GuildSettings guild = data.guilds.get(guildId);
        if (guild != null) {
            for (long userId : guild.players()) {
                Inventory inv = data.players.get(userId);
                if (inv != null) {
                    result.put(userId, inv);
                }
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- servers

    /** Settings for a server, creating empty ones the first time. */
    public GuildSettings guild(long guildId) {
        return data.guilds.computeIfAbsent(guildId, id -> new GuildSettings());
    }

    /** Forgets a server entirely (Big Hank was removed from it). Players keep their global progress. */
    public void removeGuild(long guildId) {
        data.guilds.remove(guildId);
    }
}
