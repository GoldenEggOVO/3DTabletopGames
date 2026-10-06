package dev.tabletop3d.rules;

public final class RuleViolation extends IllegalArgumentException {
    private final RuleMessage message;

    public RuleViolation(String key, String diagnostic, Object... parameters) {
        super(diagnostic);
        message = RuleMessage.of(key, parameters);
    }

    public RuleMessage message() {
        return message;
    }
}
