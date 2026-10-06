package dev.tabletop3d.rules;

import dev.tabletop3d.rules.ludo.LudoOptions;

import java.util.*;

public final class GameOptions {
    public record Option(String key, String defaultValue, List<String> values) {
        public Option {
            values = List.copyOf(values);
        }

        public String value(Map<String, String> settings) {
            return settings.getOrDefault(key, defaultValue);
        }

        public String next(Map<String, String> settings) {
            return values.get((values.indexOf(value(settings)) + 1) % values.size());
        }
    }

    private GameOptions() {}

    private static Option option(String key, String defaultValue, String... values) {
        return new Option(key, defaultValue, List.of(values));
    }

    public static List<Option> forGame(String kind) {
        return forGame(kind, Map.of());
    }

    public static List<Option> forGame(String kind, Map<String, String> settings) {
        return switch (kind) {
            case "mahjong" -> mahjong(settings.getOrDefault("profile", "riichi"));
            case "ludo" ->
                    List.of(
                            option("start", "automatic", "automatic", "three", "six"),
                            option("blocking", "off", "off", "on"),
                            option("goal", "exact", "exact", "over"),
                            option("finish", "first", "first", "all"));
            case "chess", "draughts", "reversi", "connectfour" ->
                    List.of(option("first", "host", "host", "opponent", "random"));
            case "gomoku" ->
                    List.of(
                            option("first", "host", "host", "opponent", "random"),
                            option("double-three", "allow", "allow", "forbid"),
                            option("double-four", "allow", "allow", "forbid"),
                            option("overline", "allow", "allow", "forbid"));
            case "checkers" ->
                    List.of(
                            option("jump-own", "allow", "allow", "forbid"),
                            option("other-camps", "allow", "allow", "forbid"),
                            option("finish", "first", "first", "all"));
            default -> List.of();
        };
    }

    public static Map<String, String> validate(String kind, Map<String, String> supplied) {
        Objects.requireNonNull(supplied, "Room options");
        Map<String, String> result = new LinkedHashMap<>();
        List<Option> available = forGame(kind, supplied);
        for (var entry : supplied.entrySet()) {
            Option option =
                    available.stream()
                            .filter(o -> o.key().equals(entry.getKey()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new RuleViolation(
                                                    "error.rule.unknown-option",
                                                    "Unknown rule option for "
                                                            + kind
                                                            + ": "
                                                            + entry.getKey(),
                                                    "game",
                                                    kind,
                                                    "option",
                                                    entry.getKey()));
            if (!option.values().contains(entry.getValue()))
                throw new RuleViolation(
                        "error.rule.invalid-option",
                        "Invalid rule option: " + entry.getKey() + "=" + entry.getValue(),
                        "option",
                        entry.getKey(),
                        "value",
                        entry.getValue());
            if (!option.defaultValue().equals(entry.getValue()))
                result.put(entry.getKey(), entry.getValue());
        }
        if (kind.equals("mahjong")) {
            String profile = supplied.getOrDefault("profile", "riichi");
            List<String> shapes =
                    profile.equals("guangdong")
                            ? List.of("standard-win", "seven-pairs", "thirteen-orphans")
                            : List.of();
            if (!shapes.isEmpty()
                    && shapes.stream()
                            .allMatch(key -> supplied.getOrDefault(key, "true").equals("false")))
                throw new RuleViolation(
                        "error.rule.winning-shape", "Keep at least one winning hand shape enabled");
        }
        return Collections.unmodifiableMap(result);
    }

    public static Map<String, String> change(
            String kind, Map<String, String> settings, Option option) {
        Map<String, String> changed = new LinkedHashMap<>(settings);
        if (kind.equals("mahjong") && option.key().equals("profile"))
            changed.keySet().retainAll(Set.of("rounds", "starting-points"));
        changed.put(option.key(), option.next(settings));
        return validate(kind, changed);
    }

    private static Option flag(String key, boolean defaultValue) {
        return option(key, Boolean.toString(defaultValue), "true", "false");
    }

    private static List<Option> mahjong(String profile) {
        List<Option> options =
                new ArrayList<>(
                        List.of(
                                option(
                                        "profile",
                                        "riichi",
                                        "riichi",
                                        "guangdong",
                                        "sichuan",
                                        "taiwan"),
                                option(
                                        "rounds",
                                        profile.equals("guangdong") ? "1" : "4",
                                        "1",
                                        "4",
                                        "8"),
                                option("starting-points", "25000", "25000", "30000", "35000")));
        options.addAll(
                switch (profile) {
                    case "riichi" ->
                            List.of(
                                    option("red-five-count", "3", "0", "3", "4"),
                                    flag("open-tanyao", true),
                                    flag("kiriage-mangan", false),
                                    flag("counted-yakuman", true),
                                    flag("double-yakuman", false));
                    case "guangdong" ->
                            List.of(
                                    flag("standard-win", true),
                                    flag("seven-pairs", true),
                                    flag("thirteen-orphans", true),
                                    option("ron-payment", "1000", "500", "1000", "2000"),
                                    option(
                                            "tsumo-payment-per-opponent",
                                            "500",
                                            "250",
                                            "500",
                                            "1000"));
                    case "sichuan" ->
                            List.of(
                                    option("base-points", "100", "100", "200", "500"),
                                    option("max-fan", "4", "3", "4", "5", "6"),
                                    option("self-draw-mode", "ADD_BASE", "ADD_BASE", "ADD_FAN"),
                                    option(
                                            "exchange-direction",
                                            "RANDOM",
                                            "RANDOM",
                                            "CLOCKWISE",
                                            "COUNTERCLOCKWISE",
                                            "ACROSS"),
                                    flag("over-water-ron", true),
                                    flag("forced-win-last-four", true),
                                    flag("call-transfer", true),
                                    flag("refund-kong-on-exhaustion", true));
                    case "taiwan" ->
                            List.of(
                                    option("base-points", "100", "100", "200", "500"),
                                    option("points-per-tai", "100", "50", "100", "200"),
                                    option("max-tai", "16", "8", "16", "32"),
                                    option("minimum-tai", "0", "0", "1", "2", "3"),
                                    option("dealer-tai", "1", "0", "1", "2"),
                                    option("dealer-streak-tai", "2", "0", "1", "2"),
                                    flag("allow-rob-added-kan", true),
                                    option("ron-mode", "NEAREST_ONLY", "NEAREST_ONLY", "MULTIPLE"));
                    default ->
                            throw new RuleViolation(
                                    "error.mahjong.profile",
                                    "Unknown mahjong profile: " + profile,
                                    "profile",
                                    profile);
                });
        return List.copyOf(options);
    }

    public static LudoOptions ludo(Map<String, String> settings) {
        return new LudoOptions(
                settings.getOrDefault("start", "automatic").equals("automatic"),
                settings.getOrDefault("blocking", "off").equals("on"),
                !settings.getOrDefault("goal", "exact").equals("over"),
                settings.getOrDefault("finish", "first").equals("all"),
                settings.getOrDefault("start", "automatic").equals("three") ? 3 : 1);
    }
}
