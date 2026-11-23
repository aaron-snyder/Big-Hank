package bighank.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.junit.jupiter.api.Test;

class BotCommandsTest {
    @Test
    void commandDefinitionsAreValid() {
        // JDA validates names, descriptions, and choice limits while building, so this catches typos early.
        List<CommandData> commands = BotCommands.definitions();
        assertEquals(12, commands.size());
        for (CommandData command : commands) {
            assertTrue(command.toData().toString().contains("\"contexts\":[\"0\"]"), command.getName() + " is guild-only");
        }
    }
}
