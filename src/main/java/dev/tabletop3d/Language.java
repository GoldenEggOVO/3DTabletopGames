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
    private static final Map<String,String> SOURCES = compatibility("sources");
    private static final Language ENGLISH = bundledEnglish();
    private static volatile Language current = ENGLISH;
    private static long generation;
    private final Map<String, String> entries;
    private final Map<String, String> messages;
    private final Pattern pattern;

    private Language(Map<String, String> entries) {
        var adapted = new LinkedHashMap<>(entries);
        SOURCES.forEach((key,source)->{String value=entries.get(MESSAGE+key);if(value!=null)adapted.put(source,value);});
        this.entries = Map.copyOf(adapted);
        var named = new LinkedHashMap<String,String>();
        entries.forEach((key,value)->{if(key.startsWith(MESSAGE))named.put(key.substring(MESSAGE.length()),value);});
        messages = Map.copyOf(named);
        pattern = Pattern.compile(this.entries.keySet().stream()
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
        Path data=plugin.getDataFolder().toPath();
        try { migrate(data); }
        catch(IOException ex){plugin.getLogger().warning("Cannot migrate legacy languages: "+ex.getMessage());}
        current=load(data.resolve("languages"),plugin.getConfig().getString("language","en_US"),plugin.getLogger()::warning);
        generation++;
    }

    /** Copy legacy files once; preserve originals and prefer any existing destination. */
    static void migrate(Path data) throws IOException {
        Path old=data.resolve("lang"),folder=data.resolve("languages");
        if(!Files.isDirectory(old))return;
        Files.createDirectories(folder);
        try(var files=Files.list(old)) {
            for(Path file:files.filter(Files::isRegularFile).toList()) {
                String name=file.getFileName().toString();
                if(!name.endsWith(".yml")||name.equals("legacy.yml"))continue;
                Path target=folder.resolve(name.equals("en.yml")?"en_US.yml":name);
                if(!Files.exists(target)){
                    var merged=new LinkedHashMap<>(ENGLISH.entries);
                    var warnings=new java.util.ArrayList<String>();
                    readFile(file,merged,warnings::add,!name.equals("en.yml"));
                    if(!warnings.isEmpty()){Files.copy(file,target);continue;} // Preserve malformed/custom files for diagnosis.
                    var converted=yaml();
                    merged.forEach((key,value)->{
                        if(key.startsWith(MESSAGE))converted.set(key.substring(MESSAGE.length()),value);
                        else if(!SOURCES.containsValue(key))converted.set("translations"+'\u001f'+key,value);
                    });
                    Files.writeString(target,converted.saveToString(),StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
                }
            }
        }
    }

    static Language load(Path folder,String locale,java.util.function.Consumer<String> warning) {
        Language english=ENGLISH;
        try {
            Files.createDirectories(folder);
            for(String builtIn:java.util.List.of("en_US","zh_CN")) {
                Path file=folder.resolve(builtIn+".yml");
                if(!Files.exists(file))try(InputStream input=Language.class.getResourceAsStream("/languages/"+builtIn+".yml")) {
                    if(input==null)throw new IOException("Missing bundled language: "+builtIn);
                    Files.copy(input,file);
                }
            }
        }catch(IOException ex){warning.accept("Cannot initialize languages at "+folder+": "+ex.getMessage());return english;}
        if(locale==null||!locale.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            warning.accept("Invalid language filename; using en_US.");locale="en_US";
        }
        if(locale.equals("en"))locale="en_US";
        Map<String,String> merged=new LinkedHashMap<>(english.entries);
        readFile(folder.resolve("en_US.yml"),merged,warning,false);
        if(!locale.equals("en_US"))readFile(folder.resolve(locale+".yml"),merged,warning,true);
        return new Language(merged);
    }

    static boolean reload(Path folder,String locale,java.util.function.Consumer<String> warning) {
        var problems=new java.util.ArrayList<String>();Language candidate=load(folder,locale,problems::add);
        problems.forEach(warning);if(!problems.isEmpty())return false;
        current=candidate;generation++;return true;
    }

    private static void readFile(Path file,Map<String,String> merged,java.util.function.Consumer<String> warning,boolean selected) {
        try {
            YamlConfiguration config=yaml();config.loadFromString(Files.readString(file,StandardCharsets.UTF_8));
            if(config.isConfigurationSection("translations")||config.isConfigurationSection("messages")) {
                // Old installations keep their phrase overrides, including template adaptation.
                Map<String,String> legacy=values(config,merged,selected);
                legacy.forEach((key,value)->{if(!key.startsWith(MESSAGE))merged.put(key,value);});
                legacy.forEach((key,value)->{if(key.startsWith(MESSAGE))accept(file,key.substring(MESSAGE.length()),value,merged,warning);});
                SOURCES.forEach((key,source)->{if(legacy.containsKey(source))merged.put(MESSAGE+key,legacy.get(source));});
            }
            flatValues(config,"").forEach((key,value)->{
                if(!key.startsWith("translations.")&&!key.startsWith("messages."))accept(file,key,value,merged,warning);
            });
        }catch(Exception ex){warning.accept("Cannot load language "+file.getFileName()+"; using English fallback: "+ex.getMessage());}
    }

    private static void accept(Path file,String key,Object value,Map<String,String> merged,java.util.function.Consumer<String> warning) {
        try {
            String baseline=ENGLISH.messages.get(key);
            if(baseline==null)throw new IllegalArgumentException("Unknown message key");
            if(!(value instanceof String text))throw new IllegalArgumentException("Expected a string");
            if(!MessageText.placeholders(baseline).containsAll(MessageText.placeholders(text)))
                throw new IllegalArgumentException("Unknown placeholder; expected "+MessageText.placeholders(baseline));
            MessageText.validate(text);merged.put(MESSAGE+key,text);
        }catch(IllegalArgumentException ex){warning.accept(file.getFileName()+" ["+key+"]: "+ex.getMessage());}
    }

    static Map<String, String> read(Path file) throws IOException {
        return read(file,ENGLISH.entries,false);
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
        SOURCES.forEach((key,source)->{if(values.containsKey(source))values.put(MESSAGE+key,values.get(source));});
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
        try(InputStream input=Language.class.getResourceAsStream("/languages/en_US.yml")) {
            if(input==null)throw new IOException("Missing bundled English language file");
            var config=yaml();config.load(new InputStreamReader(input,StandardCharsets.UTF_8));
            var values=new LinkedHashMap<String,String>();
            flatValues(config,"").forEach((key,value)->{if(value instanceof String text)values.put(MESSAGE+key,text);});
            return new Language(values);
        }catch(Exception ex){throw new IllegalStateException("Cannot read bundled English language file",ex);}
    }

    private String translate(String input) {
        String whole=entries.get(input);if(whole!=null)return whole;
        Matcher matcher=pattern.matcher(input);StringBuilder result=new StringBuilder();
        while(matcher.find())matcher.appendReplacement(result,Matcher.quoteReplacement(entries.get(matcher.group())));
        matcher.appendTail(result);return result.toString();
    }

    private static Map<String,String> aliases() { return compatibility("messages"); }

    private static Map<String,String> compatibility(String section) {
        try(InputStream input=Language.class.getResourceAsStream("/language-compatibility.yml")) {
            if(input==null)throw new IOException("Missing language compatibility mappings");
            var config=yaml();config.load(new InputStreamReader(input,StandardCharsets.UTF_8));
            var result=new LinkedHashMap<String,String>();
            config.getConfigurationSection(section).getValues(false).forEach((key,value)->result.put(key,(String)value));
            return Map.copyOf(result);
        }catch(Exception ex){throw new IllegalStateException("Cannot load language compatibility mappings",ex);}
    }

    private static YamlConfiguration yaml() {
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('\u001f');
        return config;
    }

    private static Map<String,Object> flatValues(org.bukkit.configuration.ConfigurationSection section,String prefix) {
        var result=new LinkedHashMap<String,Object>();
        section.getValues(false).forEach((key,value)->{
            if(value instanceof org.bukkit.configuration.ConfigurationSection child)result.putAll(flatValues(child,prefix+key+"."));
            else result.put(prefix+key,value);
        });
        return result;
    }
}
