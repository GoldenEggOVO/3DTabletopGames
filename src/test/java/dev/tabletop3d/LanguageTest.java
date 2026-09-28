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

    @Test void legacyDynamicOverridesAreAppliedBeforeLiteralParametersAndNamedOverridesWin() throws Exception {
        var plugin=mock(Tabletop3D.class);var config=new YamlConfiguration();config.set("language","test");
        when(plugin.getConfig()).thenReturn(config);when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getResource("lang/en.yml")).thenAnswer(i->getClass().getClassLoader().getResourceAsStream("lang/en.yml"));
        Files.createDirectories(temp.resolve("lang"));
        String english="translations:\n  '创建房间': 'Local Create'\n  '加入了房间': 'joined locally'\n";
        Files.writeString(temp.resolve("lang/en.yml"),english);
        Path selected=temp.resolve("lang/test.yml");
        Files.writeString(selected,"translations:\n  '加入了房间': 'joined custom'\n  '中国象棋': 'Custom Xiangqi'\n  '§6[日暮棋牌] §f': '&6[Custom] &f'\n");
        String player="<red>加入了房间&c";
        try {
            Language.load(plugin);
            assertEquals(player+" joined custom",plain(Language.component("chat.joined","player",player)));
            assertEquals("[Custom] "+player,plain(Language.component("chat.prefix","message",net.kyori.adventure.text.Component.text(player))));
            assertEquals("Local Create",plain(Language.component("menu.create")));
            assertEquals("Custom Xiangqi",plain(RoomText.game("xiangqi")));
            assertEquals("Connect Four",plain(RoomText.game("connectfour")));
            Files.writeString(selected,"translations:\n  '加入了房间': 'unused'\nmessages:\n  'chat.joined': '<green>{player} arrived</green>'\n");
            Language.load(plugin);
            assertEquals(player+" arrived",plain(Language.component("chat.joined","player",player)));
            assertEquals(english,Files.readString(temp.resolve("lang/en.yml")));
        }finally{Files.delete(temp.resolve("lang/en.yml"));config.set("language","en");Language.load(plugin);}
    }

    @Test void menusOutcomesAndRosterUseNamedTranslationsWithoutChangingStoredNames() throws Exception {
        var plugin=mock(Tabletop3D.class);var config=new YamlConfiguration();config.set("language","test");
        when(plugin.getConfig()).thenReturn(config);when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getResource("lang/en.yml")).thenAnswer(i->getClass().getClassLoader().getResourceAsStream("lang/en.yml"));
        Files.createDirectories(temp.resolve("lang"));
        Files.writeString(temp.resolve("lang/test.yml"),"messages:\n  'room.bot': 'Practice {number}'\n  'room.roster': '{players} | {empty}/{capacity}'\n  'result.winner': 'Winner: {player}'\n  'status.finished': 'Done'\n  'promotion.q': 'Regina'\n");
        String name="<red>准备&c陪练99";var room=new Room(java.util.UUID.randomUUID(),"chess",2,0,0);
        room.join(java.util.UUID.randomUUID(),name);room.fillBots();room.phase=Room.Phase.FINISHED;room.result="winner:0";
        try {
            Language.load(plugin);
            assertEquals(name+", Practice 2 | 0/2",plain(RoomText.roster(room.seats,room.capacity)));
            assertEquals("Winner: "+name,plain(RoomText.outcome(room,room.result)));
            assertEquals("Done",plain(RoomText.phase(room)));
            assertEquals("a7 → a8 · Promote to Regina",plain(GameMenus.actionLabel(room,"move:a7:a8:q")));
            assertEquals(name,room.seats.getFirst().name());assertEquals("winner:0",room.result);
        }finally{config.set("language","en");Language.load(plugin);}
    }

    private static String plain(net.kyori.adventure.text.Component text){return dev.tabletop3d.ui.MessageText.plain(text);}

    @Test void selectedLegacyValueOverridesALocalNamedValueEvenWhenItEqualsBundledEnglish() throws Exception {
        var plugin=mock(Tabletop3D.class);var config=new YamlConfiguration();config.set("language","test");
        when(plugin.getConfig()).thenReturn(config);when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getResource("lang/en.yml")).thenAnswer(i->getClass().getClassLoader().getResourceAsStream("lang/en.yml"));
        Files.createDirectories(temp.resolve("lang"));
        Files.writeString(temp.resolve("lang/en.yml"),"messages:\n  'menu.create': 'LOCAL OVERRIDE'\n");
        Files.writeString(temp.resolve("lang/test.yml"),"translations:\n  '创建房间': 'Create Room'\n");
        try{Language.load(plugin);assertEquals("Create Room",plain(Language.component("menu.create")));}
        finally{Files.delete(temp.resolve("lang/en.yml"));config.set("language","en");Language.load(plugin);}
    }

    @Test void namedMessagesProtectDynamicNamesAndKeepLegacyOverrides() throws Exception {
        var plugin=mock(Tabletop3D.class);var config=new YamlConfiguration();
        config.set("language","test");when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getResource("lang/en.yml")).thenAnswer(i->getClass().getClassLoader().getResourceAsStream("lang/en.yml"));
        Files.createDirectories(temp.resolve("lang"));
        Files.writeString(temp.resolve("lang/test.yml"),"translations:\n  '确认离开': 'Leave now'\nmessages:\n  'table.turn': '<gold>Next: {player}</gold>'\n");
        try {
            Language.load(plugin);
            assertEquals("Leave now",dev.tabletop3d.ui.MessageText.plain(Language.component("menu.leave.confirm")));
            assertEquals("Next: <red>玩家&c",dev.tabletop3d.ui.MessageText.plain(Language.component("table.turn","player","<red>玩家&c")));
            assertEquals("3D Tabletop Games",dev.tabletop3d.ui.MessageText.plain(Language.component("menu.title")));
        } finally {config.set("language","en");Language.load(plugin);}
    }

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
