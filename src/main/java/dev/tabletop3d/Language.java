package dev.tabletop3d;

import org.bukkit.configuration.file.YamlConfiguration;
import dev.tabletop3d.ui.MessageText;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Translates legacy display text without changing persisted rule state or menu actions. */
final class Language {
    private static final String MESSAGE = "message:";
    private static final Map<String,String> ALIASES = Map.ofEntries(
        Map.entry("menu.title", "3D Tabletop Games"), Map.entry("menu.back", "返回上一页"),
        Map.entry("menu.main", "返回主菜单"), Map.entry("menu.close", "关闭菜单"),
        Map.entry("menu.resume", "继续当前对局"), Map.entry("menu.create", "创建房间"),
        Map.entry("menu.leave.title", "离开房间"), Map.entry("menu.leave.confirm", "确认离开"),
        Map.entry("menu.leave.description", "对局中离开会结束整桌免费局；只关闭菜单则保留座位。"),
        Map.entry("table.waiting", "等候准备"), Map.entry("table.hint", "瞄准 · 点击落子"),
        Map.entry("table.join", "潜行右键：加入 / 房间菜单"));
    private static volatile Language current = bundledEnglish();
    private final Map<String, String> entries;
    private final Map<String, String> messages;
    private final Pattern pattern;

    private Language(Map<String, String> entries) {
        this.entries = Map.copyOf(entries);
        var named = new LinkedHashMap<String,String>();
        entries.forEach((key,value)->{if(key.startsWith(MESSAGE))named.put(key.substring(MESSAGE.length()),value);});
        messages = Map.copyOf(named);
        pattern = Pattern.compile(entries.keySet().stream()
            .filter(key -> key.length() > 1 && !key.startsWith(MESSAGE))
            .sorted((a, b) -> Integer.compare(b.length(), a.length()))
            .map(key -> key.length() == 2 ? "(?<!\\p{IsHan})" + Pattern.quote(key) : Pattern.quote(key))
            .reduce((a, b) -> a + "|" + b).orElse("(?!)"));
    }

    /** Named UI templates with literal/component parameters, independent of rule state. */
    static Component component(String key, Object... pairs) {
        return MessageText.render(current.messages.getOrDefault(key,key),pairs);
    }

    static String text(String input) {
        if (input == null || input.isEmpty()) return input;
        Language language = current;
        String whole = language.entries.get(input);
        if (whole != null) return whole;
        Matcher matcher = language.pattern.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(result,
            Matcher.quoteReplacement(language.entries.get(matcher.group())));
        matcher.appendTail(result);
        return result.toString();
    }

    static String glyph(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            String letter = input.substring(i, i + 1);
            result.append(current.entries.getOrDefault(letter, letter));
        }
        return result.toString();
    }

    static void load(Tabletop3D plugin) {
        String code = plugin.getConfig().getString("language", "en");
        if (code == null || !code.matches("[a-z][a-z0-9_-]*"))
            throw new IllegalArgumentException("Invalid language code: " + code);
        Path directory = plugin.getDataFolder().toPath().resolve("lang");
        try {
            Files.createDirectories(directory);
            Path english = directory.resolve("en.yml");
            if (!Files.exists(english)) copy(plugin, "lang/en.yml", english);
            Map<String, String> merged = new LinkedHashMap<>(bundledEnglish().entries);
            merged.putAll(read(english));
            if (!code.equals("en")) {
                Path selected = directory.resolve(code + ".yml");
                if (!Files.isRegularFile(selected))
                    throw new IllegalArgumentException("Language file missing: " + selected);
                merged.putAll(read(selected));
            }
            current = new Language(merged);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load language files", ex);
        }
    }

    private static void copy(Tabletop3D plugin, String resource, Path destination) throws IOException {
        try (InputStream input = plugin.getResource(resource)) {
            if (input == null) throw new IOException("Missing bundled resource: " + resource);
            Files.copy(input, destination);
        }
    }

    static Map<String, String> read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            YamlConfiguration config = yaml();
            try { config.load(reader); }
            catch (Exception ex) { throw new IOException("Invalid language YAML: " + file, ex); }
            return values(config);
        }
    }

    private static Map<String, String> values(YamlConfiguration config) {
        var section = config.getConfigurationSection("translations");
        if (section == null && !config.isConfigurationSection("messages"))
            throw new IllegalArgumentException("Language file needs translations or messages section");
        Map<String, String> values = new LinkedHashMap<>();
        for (var entry : section == null ? Map.<String,Object>of().entrySet() : section.getValues(false).entrySet()) {
            if (!(entry.getValue() instanceof String value))
                throw new IllegalArgumentException("Invalid translation: " + entry.getKey());
            values.put(entry.getKey(), value);
            if (entry.getKey().contains("\\n"))
                values.put(entry.getKey().replace("\\n", "\n"), value.replace("\\n", "\n"));
        }
        ALIASES.forEach((key,source)->{if(values.containsKey(source))values.put(MESSAGE+key,values.get(source));});
        var named=config.getConfigurationSection("messages");
        if(named!=null)for(var entry:named.getValues(false).entrySet()) {
            if(!(entry.getValue() instanceof String value))throw new IllegalArgumentException("Invalid message: "+entry.getKey());
            MessageText.validate(value);
            values.put(MESSAGE+entry.getKey(),value);
        }
        return values;
    }

    private static Language bundledEnglish() {
        try (InputStream input = Language.class.getClassLoader().getResourceAsStream("lang/en.yml")) {
            if (input == null) throw new IllegalStateException("Missing bundled English language file");
            YamlConfiguration config = yaml();
            config.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return new Language(values(config));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read bundled English language file", ex);
        }
    }

    private static YamlConfiguration yaml() {
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('\u001f');
        return config;
    }
}
