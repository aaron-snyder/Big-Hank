package bighank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigTest {
    @Test
    void parsesDotEnvLines() {
        Map<String, String> values = Config.parse(List.of(
                "# comment",
                "",
                "DISCORD_TOKEN=abc.def.ghi",
                "export DATA_FILE = \"save/data.json\"",
                "QUOTED='x=y'",
                "garbage line"));
        assertEquals("abc.def.ghi", values.get("DISCORD_TOKEN"));
        assertEquals("save/data.json", values.get("DATA_FILE"));
        assertEquals("x=y", values.get("QUOTED"));
        assertFalse(values.containsKey("garbage line"));
    }
}
