package bighank;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads settings from environment variables, falling back to a {@code .env} file in the working directory.
 *
 * <p>Secrets like the bot token live only in the environment or in {@code .env}, which is git-ignored, so the
 * source code can be shared publicly.
 */
final class Config {
    private final Map<String, String> dotEnv;

    private Config(Map<String, String> dotEnv) {
        this.dotEnv = dotEnv;
    }

    static Config load() throws IOException {
        Path path = Path.of(".env");
        return new Config(Files.exists(path) ? parse(Files.readAllLines(path, StandardCharsets.UTF_8)) : Map.of());
    }

    /** The value of {@code key}, or {@code fallback} if it's not set anywhere. Real env vars win over .env. */
    String get(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = dotEnv.get(key);
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** Parses simple {@code KEY=value} lines. Blank lines and {@code #} comments are skipped. */
    static Map<String, String> parse(List<String> lines) {
        Map<String, String> values = new HashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }
}
