package taskserver;

import java.util.Map;
import java.util.Optional;

/** Factory for the task handlers the server knows about. */
public final class TaskHandlers {
    private static final Map<String, TaskHandler> HANDLERS = Map.of(
            "count_words", text -> String.valueOf(text.isBlank() ? 0 : text.trim().split("\\s+").length),
            "sleep", seconds -> {
                Thread.sleep(Long.parseLong(seconds) * 1000);
                return "slept " + seconds + " s";
            });

    /** The request field each task type reads. */
    static final Map<String, String> INPUT_FIELD = Map.of("count_words", "text", "sleep", "seconds");

    private TaskHandlers() {}

    public static Optional<TaskHandler> forType(String type) {
        return Optional.ofNullable(HANDLERS.get(type));
    }

    /** Throws IllegalArgumentException with a message for the 400 answer. */
    static void validate(String type, String input) {
        if (input == null) {
            throw new IllegalArgumentException(type + " needs the field " + INPUT_FIELD.get(type));
        }
        if (type.equals("sleep")) {
            int s;
            try {
                s = Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("seconds must be a whole number");
            }
            if (s < 1 || s > 10) {
                throw new IllegalArgumentException("seconds must be from 1 to 10");
            }
        }
    }
}
