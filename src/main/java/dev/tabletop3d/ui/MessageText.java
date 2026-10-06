package dev.tabletop3d.ui;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class MessageText {
    public static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_-]*)}");
    private static final TagResolver STYLES = TagResolver.resolver(
            StandardTags.color(), StandardTags.decorations(), StandardTags.reset(),
            StandardTags.newline(), StandardTags.gradient(), StandardTags.rainbow());
    private static final MiniMessage FORMAT = MiniMessage.builder().tags(STYLES).build();
    private static final Pattern TAG = Pattern.compile("<([^<>]*)>");
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)<(?:click|hover|font|insertion|selector|score|nbt|translatable|translate|lang|keybind)(?=[:>])");
    private static final String[] COLORS = {"black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white"};

    private MessageText() {}

    public static Component render(String template, Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Expected named placeholder pairs");
        var values = new LinkedHashMap<String, Component>();
        for (int i = 0; i < pairs.length; i += 2)
            values.put(String.valueOf(pairs[i]), pairs[i + 1] instanceof Component c
                    ? c : Component.text(String.valueOf(pairs[i + 1])));
        var tags = TagResolver.builder();
        var matcher = PLACEHOLDER.matcher(legacy(template));
        var result = new StringBuilder();
        int index = 0;
        while (matcher.find()) {
            Component value = values.get(matcher.group(1));
            if (value == null) continue;
            String tag = "tabletop_arg_" + index++;
            tags.resolver(TagResolver.resolver(tag, Tag.inserting(value)));
            matcher.appendReplacement(result, "<" + tag + ">");
        }
        matcher.appendTail(result);
        return FORMAT.deserialize(result.toString(), tags.build());
    }

    public static String plain(Component value) {
        return PlainTextComponentSerializer.plainText().serialize(value);
    }

    public static Set<String> placeholders(String template) {
        return PLACEHOLDER.matcher(template).results().map(m -> m.group(1))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static void validate(String template) {
        if (FORBIDDEN.matcher(template).find())
            throw new IllegalArgumentException("Only display styles are allowed in language templates");
        var matcher = TAG.matcher(legacy(template));
        var opened = new java.util.ArrayList<String>();
        while (matcher.find()) {
            String token = matcher.group(1);
            boolean closing = token.startsWith("/");
            if (closing) token = token.substring(1);
            String name = token.split(":", 2)[0].toLowerCase(java.util.Locale.ROOT);
            if (name.startsWith("#") && !name.matches("#[0-9a-f]{6}"))
                throw new IllegalArgumentException("Invalid hexadecimal color: " + token);
            if (closing && (STYLES.has(name) || name.startsWith("#"))) {
                int at = opened.lastIndexOf(name);
                if (token.contains(":") || at < 0)
                    throw new IllegalArgumentException("Unmatched closing style: " + token);
                opened.subList(at, opened.size()).clear();
                continue;
            }
            if (name.equals("reset")) opened.clear();
            else if (!closing && STYLES.has(name) && !java.util.Set.of("newline", "br").contains(name)) opened.add(name);
            if (STYLES.has(name) && !java.util.Set.of("newline", "br").contains(name)
                    && !plain(render(matcher.group() + "X")).equals("X"))
                throw new IllegalArgumentException("Invalid style: " + token);
        }
        int open = template.lastIndexOf('<');
        if (open > template.lastIndexOf('>')) {
            String name = template.substring(open + 1).split(":", 2)[0].toLowerCase(java.util.Locale.ROOT);
            if (STYLES.has(name) || name.startsWith("#")) throw new IllegalArgumentException("Unfinished style tag");
        }
        render(template);
    }

    static String legacy(String value) {
        var result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char prefix = value.charAt(i);
            if ((prefix == '&' || prefix == '§') && i + 1 < value.length()) {
                char code = Character.toLowerCase(value.charAt(i + 1));
                if (code == '#' && i + 7 < value.length()
                        && value.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    result.append("<reset><#").append(value, i + 2, i + 8).append('>');
                    i += 7;
                    continue;
                }
                if (code == 'x' && i + 13 < value.length()) {
                    String expanded = value.substring(i + 2, i + 14);
                    if (expanded.matches("(?i)([&§][0-9a-f]){6}")) {
                        result.append("<reset><#").append(expanded.replaceAll("[&§]", "")).append('>');
                        i += 13;
                        continue;
                    }
                }
                int color = "0123456789abcdef".indexOf(code);
                String tag = color >= 0 ? "reset><" + COLORS[color] : switch (code) {
                    case 'l' -> "bold";
                    case 'o' -> "italic";
                    case 'n' -> "underlined";
                    case 'm' -> "strikethrough";
                    case 'k' -> "obfuscated";
                    case 'r' -> "reset";
                    default -> null;
                };
                if (tag != null) {
                    result.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            result.append(prefix);
        }
        return result.toString();
    }
}
