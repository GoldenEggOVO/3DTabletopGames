package dev.tabletop3d.rules;

import java.util.*;

/** Locale-independent description; the UI renders a complete template. */
public record RuleMessage(String key, Map<String, Object> parameters) {
    public RuleMessage {
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    public static RuleMessage of(String key, Object... parameters) {
        if (parameters.length % 2 != 0)
            throw new IllegalArgumentException("Message parameters must be name/value pairs");
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < parameters.length; index += 2)
            values.put((String) parameters[index], parameters[index + 1]);
        return new RuleMessage(key, values);
    }
}
