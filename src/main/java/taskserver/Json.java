package taskserver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tiny helpers so the project needs no JSON library. Only flat string and number fields. */
final class Json {
    private Json() {}

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(name)
                + "\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(-?\\d+(?:\\.\\d+)?))").matcher(json);
        if (!m.find()) return null;
        if (m.group(2) != null) return m.group(2);
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
