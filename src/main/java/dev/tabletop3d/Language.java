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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Named display messages with a compatibility adapter for rule text and old language files. */
final class Language {
    private static final String MESSAGE = "message:";
    private static final Map<String,String> ALIASES = aliases();
    private static volatile Language current = bundledEnglish();
    private static long generation;
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

    static long generation() { return generation; }

    /** Rule output and stored reasons keep their existing strings; translate only at the UI boundary. */
    static Component legacy(String input) {
        if(input==null)return Component.empty();
        for(var alias:ALIASES.entrySet())if(alias.getValue().equals(input))return component(alias.getKey());
        return MessageText.render(text(input));
    }

    static String text(String input) {
        if (input == null || input.isEmpty()) return input;
        return current.translate(input);
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
            merged.putAll(read(english,merged,false));
            if (!code.equals("en")) {
                Path selected = directory.resolve(code + ".yml");
                if (!Files.isRegularFile(selected))
                    throw new IllegalArgumentException("Language file missing: " + selected);
                merged.putAll(read(selected,merged,true));
            }
            current = new Language(merged);
            generation++;
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
        return read(file,bundledEnglish().entries,false);
    }

    private static Map<String, String> read(Path file,Map<String,String> baseline,boolean selected) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            YamlConfiguration config = yaml();
            try { config.load(reader); }
            catch (Exception ex) { throw new IOException("Invalid language YAML: " + file, ex); }
            return values(config,baseline,selected);
        }
    }

    private static Map<String, String> values(YamlConfiguration config,Map<String,String> baseline,boolean selected) {
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
        // Translate old templates once when loading, before inserting any player names or values.
        var translations=new LinkedHashMap<>(baseline);translations.putAll(values);
        var translator=new Language(translations);
        ALIASES.forEach((key,source)->{
            boolean changed=values.entrySet().stream().anyMatch(e->(selected||!Objects.equals(baseline.get(e.getKey()),e.getValue()))
                &&(source.equals(e.getKey())||e.getKey().length()>1&&source.contains(e.getKey())));
            if(changed)values.put(MESSAGE+key,translator.translate(source));
        });
        var named=config.getConfigurationSection("messages");
        if(named!=null)for(var entry:named.getValues(false).entrySet()) {
            if(!(entry.getValue() instanceof String value))throw new IllegalArgumentException("Invalid message: "+entry.getKey());
            value=value.replace("\\n","\n");MessageText.validate(value);
            values.put(MESSAGE+entry.getKey(),value);
        }
        return values;
    }

    private static Language bundledEnglish() {
        try (InputStream input = Language.class.getClassLoader().getResourceAsStream("lang/en.yml")) {
            if (input == null) throw new IllegalStateException("Missing bundled English language file");
            YamlConfiguration config = yaml();
            config.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return new Language(values(config,Map.of(),false));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read bundled English language file", ex);
        }
    }

    private String translate(String input) {
        String whole=entries.get(input);if(whole!=null)return whole;
        Matcher matcher=pattern.matcher(input);StringBuilder result=new StringBuilder();
        while(matcher.find())matcher.appendReplacement(result,Matcher.quoteReplacement(entries.get(matcher.group())));
        matcher.appendTail(result);return result.toString();
    }

    private static Map<String,String> aliases() {
        try(InputStream input=Language.class.getClassLoader().getResourceAsStream("lang/legacy.yml")) {
            if(input==null)throw new IOException("Missing language compatibility mappings");
            var config=yaml();config.load(new InputStreamReader(input,StandardCharsets.UTF_8));
            var result=new LinkedHashMap<String,String>();
            config.getConfigurationSection("messages").getValues(false).forEach((key,value)->result.put(key,((String)value).replace("\\n","\n")));
            return Map.copyOf(result);
        }catch(Exception ex){throw new IllegalStateException("Cannot load language compatibility mappings",ex);}
    }

    private static YamlConfiguration yaml() {
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('\u001f');
        return config;
    }
}
