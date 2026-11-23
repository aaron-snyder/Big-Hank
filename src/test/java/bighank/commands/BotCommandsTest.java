package bighank.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bighank.game.Bait;
import bighank.game.GameException;
import java.util.List;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.junit.jupiter.api.Test;

class BotCommandsTest {
    @Test
    void sixGuildOnlyCommands() {
        // JDA validates names, descriptions, and option limits while building, so this catches typos early.
        List<CommandData> commands = BotCommands.definitions();
        assertEquals(List.of("start", "fish", "store", "profile", "leaderboard", "setup"),
                commands.stream().map(CommandData::getName).toList());
        for (CommandData command : commands) {
            assertTrue(command.toData().toString().contains("\"contexts\":[\"0\"]"), command.getName() + " is guild-only");
        }
    }

    @Test
    void parsesBaitFromAutocompleteOrTypedNames() {
        assertEquals(Bait.SPINNER_LURE, BotCommands.parseBait("SPINNER_LURE"));
        assertEquals(Bait.SPINNER_LURE, BotCommands.parseBait("  spinner lure "));
        assertEquals(Bait.GLITTER_POWERBAIT, BotCommands.parseBait("Glitter PowerBait"));
        assertNull(BotCommands.parseBait(BotCommands.NO_BAIT));
        assertNull(BotCommands.parseBait("no bait"));
        assertThrows(GameException.class, () -> BotCommands.parseBait("dynamite"));
    }
}
