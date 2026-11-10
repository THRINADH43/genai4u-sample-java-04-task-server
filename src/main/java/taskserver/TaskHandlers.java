package taskserver;

import java.util.Map;
import java.util.Optional;

/** Factory for the task handlers the server knows about. */
public final class TaskHandlers {
    private static final Map<String, TaskHandler> HANDLERS = Map.of(
            "uppercase", String::toUpperCase,
            "reverse", input -> new StringBuilder(input).reverse().toString(),
            "wordcount", input -> String.valueOf(input.isBlank() ? 0 : input.trim().split("\\s+").length),
            "sleep", input -> {
                long ms = Long.parseLong(input);
                if (ms < 0 || ms > 10_000) throw new IllegalArgumentException("sleep must be 0-10000 ms");
                Thread.sleep(ms);
                return "slept " + ms + " ms";
            });

    private TaskHandlers() {}

    public static Optional<TaskHandler> forType(String type) {
        return Optional.ofNullable(HANDLERS.get(type));
    }
}
