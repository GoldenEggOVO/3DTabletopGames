package dev.tabletop3d;

import dev.tabletop3d.ui.GameSymbols;

import dev.tabletop3d.rules.RuleMessage;
import dev.tabletop3d.rules.RuleViolation;
import dev.tabletop3d.ui.MessageText;

import net.kyori.adventure.text.Component;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Complete named templates, English fallback and atomic language reloads. */
final class Language {
    private static final Map<String, String> ENGLISH = bundled("en_US");
    private static volatile Map<String, String> current = ENGLISH;
    private static long generation;

    static Component component(String key, Object... parameters) {
        return MessageText.render(current.getOrDefault(key, key), parameters);
    }

    static Component message(RuleMessage message) {
        List<Object> parameters = new ArrayList<>();
        message.parameters()
                .forEach(
                        (name, value) -> {
                            parameters.add(name);
                            parameters.add(value);
                        });
        return component(message.key(), parameters.toArray());
    }

    static Component error(IllegalArgumentException exception) {
        return exception instanceof RuleViolation violation
                ? message(violation.message())
                : component("error.invalid");
    }

    static long generation() {
        return generation;
    }

    static String glyph(String input) {
        if (input == null) return "";
        StringBuilder translated = new StringBuilder();
        for (int index = 0; index < input.length(); index++) {
            String symbol = input.substring(index, index + 1), key = GLYPHS.get(symbol);
            translated.append(key == null ? symbol : MessageText.plain(component(key)));
        }
        return translated.toString();
    }

    private static final Map<String, String> GLYPHS =
            Map.ofEntries(
                    Map.entry(GameSymbols.BLACK_GENERAL, "piece.xiangqi.black-general"),
                    Map.entry(GameSymbols.RED_GENERAL, "piece.xiangqi.red-general"),
                    Map.entry(GameSymbols.BLACK_ADVISOR, "piece.xiangqi.advisor"),
                    Map.entry(GameSymbols.RED_ADVISOR, "piece.xiangqi.advisor"),
                    Map.entry(GameSymbols.ELEPHANT, "piece.xiangqi.elephant"),
                    Map.entry(GameSymbols.RED_ELEPHANT, "piece.xiangqi.elephant"),
                    Map.entry(GameSymbols.ROOK, "piece.xiangqi.rook"),
                    Map.entry(GameSymbols.TRADITIONAL_HORSE, "piece.xiangqi.horse"),
                    Map.entry(GameSymbols.HORSE, "piece.xiangqi.horse"),
                    Map.entry(GameSymbols.RED_CANNON, "piece.xiangqi.cannon"),
                    Map.entry(GameSymbols.BLACK_CANNON, "piece.xiangqi.cannon"),
                    Map.entry(GameSymbols.BLACK_PAWN, "piece.xiangqi.pawn"),
                    Map.entry(GameSymbols.PAWN, "piece.xiangqi.pawn"),
                    Map.entry(GameSymbols.KING, "piece.draughts.king"),
                    Map.entry(GameSymbols.RED, "piece.color.red"),
                    Map.entry(GameSymbols.YELLOW, "piece.color.yellow"),
                    Map.entry(GameSymbols.BLACK, "piece.color.black"),
                    Map.entry(GameSymbols.WHITE, "piece.color.white"));

    static void load(Tabletop3D plugin) {
        current =
                load(
                        plugin.getDataFolder().toPath().resolve("languages"),
                        plugin.getConfig().getString("language", "en_US"),
                        plugin.getLogger()::warning);
        generation++;
    }

    static Map<String, String> load(Path folder, String locale, Consumer<String> warning) {
        Map<String, String> entries = new LinkedHashMap<>(ENGLISH);
        try {
            Files.createDirectories(folder);
            for (String language : List.of("en_US", "zh_CN")) {
                Path file = folder.resolve(language + ".yml");
                if (!Files.exists(file))
                    try (InputStream input =
                            Language.class.getResourceAsStream("/languages/" + language + ".yml")) {
                        if (input == null)
                            throw new IOException("Missing bundled language: " + language);
                        Files.copy(input, file);
                    }
            }
        } catch (IOException exception) {
            warning.accept("Cannot initialize languages: " + exception.getMessage());
            return ENGLISH;
        }
        if (locale == null || !locale.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            warning.accept("Invalid language filename; using en_US");
            locale = "en_US";
        }
        read(folder.resolve("en_US.yml"), entries, warning);
        if (!locale.equals("en_US")) read(folder.resolve(locale + ".yml"), entries, warning);
        return Map.copyOf(entries);
    }

    static boolean reload(Path folder, String locale, Consumer<String> warning) {
        List<String> problems = new ArrayList<>();
        Map<String, String> candidate = load(folder, locale, problems::add);
        problems.forEach(warning);
        if (!problems.isEmpty()) return false;
        current = candidate;
        generation++;
        return true;
    }

    private static void read(Path file, Map<String, String> entries, Consumer<String> warning) {
        try {
            YamlConfiguration yaml = yaml();
            yaml.load(file.toFile());
            for (var entry : flatten(yaml, "").entrySet()) {
                String key = entry.getKey();
                try {
                    String baseline = ENGLISH.get(key);
                    if (baseline == null)
                        throw new IllegalArgumentException(
                                "Unknown message key; convert old catalogs with the standalone"
                                    + " upgrade tool");
                    if (!(entry.getValue() instanceof String value))
                        throw new IllegalArgumentException("Expected a string");
                    if (!MessageText.placeholders(baseline)
                            .containsAll(MessageText.placeholders(value)))
                        throw new IllegalArgumentException(
                                "Unknown placeholder; expected "
                                        + MessageText.placeholders(baseline));
                    MessageText.validate(value);
                    entries.put(key, value);
                } catch (IllegalArgumentException exception) {
                    warning.accept(
                            file.getFileName() + " [" + key + "]: " + exception.getMessage());
                }
            }
        } catch (Exception exception) {
            warning.accept("Cannot load " + file.getFileName() + ": " + exception.getMessage());
        }
    }

    private static Map<String, String> bundled(String locale) {
        try (InputStream input =
                Language.class.getResourceAsStream("/languages/" + locale + ".yml")) {
            if (input == null) throw new IOException("Missing bundled language");
            YamlConfiguration yaml = yaml();
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            Map<String, String> values = new LinkedHashMap<>();
            flatten(yaml, "").forEach((key, value) -> values.put(key, (String) value));
            return Map.copyOf(values);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read bundled English", exception);
        }
    }

    private static Map<String, Object> flatten(ConfigurationSection section, String prefix) {
        Map<String, Object> values = new LinkedHashMap<>();
        section.getValues(false)
                .forEach(
                        (key, value) -> {
                            if (value instanceof ConfigurationSection child)
                                values.putAll(flatten(child, prefix + key + "."));
                            else values.put(prefix + key, value);
                        });
        return values;
    }

    private static YamlConfiguration yaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator('\u001f');
        return yaml;
    }

    private Language() {}
}
