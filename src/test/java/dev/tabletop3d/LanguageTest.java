package dev.tabletop3d;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LanguageTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @Test void englishCatalogCoversAllBundledChineseDisplayLiterals() throws Exception {
        Pattern literal = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
        Pattern han = Pattern.compile("[\\p{IsHan}]");
        Path source = Path.of(System.getProperty("basedir"), "src/main");
        var missing = new java.util.ArrayList<String>();
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.toString();
                if (name.contains("upstream") || !(name.endsWith(".java") || name.contains("resources\\menus") && name.endsWith(".yml"))) continue;
                var match = literal.matcher(Files.readString(file));
                while (match.find()) {
                    String phrase = match.group(1);
                    if (han.matcher(phrase).find() && han.matcher(Language.text(phrase)).find())
                        missing.add(name + ": " + phrase);
                }
            }
        }
        assertTrue(missing.isEmpty(), String.join("\n", missing));
    }

    @Test void customLanguageOverridesEnglishAndMissingKeysFallBack() throws Exception {
        var plugin = mock(Tabletop3D.class);
        var config = new YamlConfiguration();
        config.set("language", "test");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getResource("lang/en.yml")).thenAnswer(inv ->
            getClass().getClassLoader().getResourceAsStream("lang/en.yml"));
        Files.createDirectories(temp.resolve("lang"));
        Files.writeString(temp.resolve("lang/test.yml"), "translations:\n  '棋盘游戏': 'Custom Boards'\n");
        try {
            Language.load(plugin);
            assertEquals("Custom Boards", Language.text("棋盘游戏"));
            assertEquals("Create Room", Language.text("创建房间"));
            assertTrue(Files.isRegularFile(temp.resolve("lang/en.yml")));
        } finally {
            config.set("language", "en");
            Language.load(plugin);
        }
    }
    @Test void previousEnglishFileKeepsEditsAndReceivesNewBundledKeys() throws Exception {
        var plugin = mock(Tabletop3D.class);
        var config = new YamlConfiguration();
        config.set("language", "en");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getResource("lang/en.yml")).thenAnswer(inv ->
            getClass().getClassLoader().getResourceAsStream("lang/en.yml"));
        Path english = temp.resolve("lang/en.yml");
        Files.createDirectories(english.getParent());
        String previous = "translations:\n  '棋牌游戏': 'Board Games'\n";
        Files.writeString(english, previous);
        try {
            Language.load(plugin);
            assertEquals("Confirm Leave", Language.text("确认离开"));
            assertEquals("3D Tabletop Games", Language.text("3D Tabletop Games"));
            assertEquals(previous, Files.readString(english));
        } finally {
            Files.delete(english);
            Language.load(plugin);
        }
    }
}
