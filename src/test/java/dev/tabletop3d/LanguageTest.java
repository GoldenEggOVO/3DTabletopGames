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

    @Test void bundledLanguagesExposeEveryMessageIndividuallyWithMatchingPlaceholders() throws Exception {
        var catalogs=new java.util.ArrayList<YamlConfiguration>();
        for(String locale:java.util.List.of("en_US","zh_CN")) {
            var yaml=new YamlConfiguration();yaml.options().pathSeparator('\u001f');
            try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/languages/"+locale+".yml"),java.nio.charset.StandardCharsets.UTF_8)){yaml.load(reader);}
            assertFalse(yaml.contains("messages"));assertFalse(yaml.contains("translations"));catalogs.add(yaml);
        }
        assertEquals(catalogs.getFirst().getKeys(false),catalogs.getLast().getKeys(false));
        for(String key:catalogs.getFirst().getKeys(false)) {
            String english=catalogs.getFirst().getString(key),chinese=catalogs.getLast().getString(key);
            assertNotNull(english,key);assertNotNull(chinese,key);
            assertEquals(dev.tabletop3d.ui.MessageText.placeholders(english),dev.tabletop3d.ui.MessageText.placeholders(chinese),key);
            dev.tabletop3d.ui.MessageText.validate(english);dev.tabletop3d.ui.MessageText.validate(chinese);
        }
        assertTrue(Language.reload(temp,"zh_CN",w->fail(w)));
        assertEquals("创建房间",plain(Language.component("menu.create")));
        Language.reload(temp,"en_US",w->fail(w));
    }

    @Test void casinoStyleLanguagesAllowFlatAndNestedKeysAndKeepLastGoodReload() throws Exception {
        var warnings=new java.util.ArrayList<String>();
        Language.load(temp,"en_US",warnings::add);
        assertTrue(warnings.isEmpty(),warnings.toString());
        assertTrue(Files.isRegularFile(temp.resolve("en_US.yml")));
        assertTrue(Files.isRegularFile(temp.resolve("zh_CN.yml")));
        Files.writeString(temp.resolve("custom.yml"),"menu:\n  create: 'Custom Create'\nchat.joined: '{player} arrived'\n");
        try {
            assertTrue(Language.reload(temp,"custom",warnings::add));
            assertEquals("Custom Create",plain(Language.component("menu.create")));
            assertEquals("<red>name arrived",plain(Language.component("chat.joined","player","<red>name")));
            long generation=Language.generation();
            Files.writeString(temp.resolve("custom.yml"),"menu.create: 'BAD'\nchat.joined: '{unexpected}'\n");
            assertFalse(Language.reload(temp,"custom",warnings::add));
            assertEquals(generation,Language.generation());
            assertEquals("Custom Create",plain(Language.component("menu.create")));
            assertTrue(warnings.stream().anyMatch(w->w.contains("custom.yml")&&w.contains("chat.joined")));
        }finally{Language.reload(temp,"en_US",w->fail(w));}
    }

    @Test void migrationRetainsOriginalCustomFilesAndNeverOverwritesNewLanguages() throws Exception {
        Path data=temp.resolve("plugin");Files.createDirectories(data.resolve("lang"));
        Files.writeString(data.resolve("lang/en.yml"),"translations:\n  '创建房间': 'Old Custom Create'\n");
        Files.writeString(data.resolve("lang/My_Locale.yml"),"messages:\n  'menu.close': 'Old Close'\n");
        Language.migrate(data);
        assertTrue(Files.isRegularFile(data.resolve("lang/en.yml")));
        var migrated=new YamlConfiguration();migrated.options().pathSeparator('\u001f');migrated.load(data.resolve("languages/en_US.yml").toFile());
        assertEquals("Old Custom Create",migrated.getString("menu.create"));
        assertFalse(migrated.contains("messages"));assertFalse(migrated.contains("translations"));
        assertTrue(Language.reload(data.resolve("languages"),"en",w->fail(w)));
        assertEquals("Old Custom Create",plain(Language.component("menu.create")));
        Files.writeString(data.resolve("languages/en_US.yml"),"menu.create: 'New Edit'\n");
        Language.migrate(data);
        assertEquals("menu.create: 'New Edit'\n",Files.readString(data.resolve("languages/en_US.yml")));
        assertTrue(Files.isRegularFile(data.resolve("languages/My_Locale.yml")));
        Language.reload(temp,"en_US",w->fail(w));
    }

    @Test void legacyDynamicOverridesAreAppliedBeforeLiteralParametersAndNamedOverridesWin() throws Exception {
        var plugin=mock(Tabletop3D.class);when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("LanguageTest"));var config=new YamlConfiguration();config.set("language","test");
        when(plugin.getConfig()).thenReturn(config);when(plugin.getDataFolder()).thenReturn(temp.toFile());

        Files.createDirectories(temp.resolve("languages"));
        String english="translations:\n  '创建房间': 'Local Create'\n  '加入了房间': 'joined locally'\n";
        Files.writeString(temp.resolve("languages/en_US.yml"),english);
        Path selected=temp.resolve("languages/test.yml");
        Files.writeString(selected,"translations:\n  '加入了房间': 'joined custom'\n  '中国象棋': 'Custom Xiangqi'\n  '飞行棋': 'Custom Flight'\n  '§6[日暮棋牌] §f': '&6[Custom] &f'\n");
        String player="<red>加入了房间&c";
        try {
            Language.load(plugin);
            assertEquals(player+" joined custom",plain(Language.component("chat.joined","player",player)));
            assertEquals("[Custom] "+player,plain(Language.component("chat.prefix","message",net.kyori.adventure.text.Component.text(player))));
            assertEquals("Local Create",plain(Language.component("menu.create")));
            assertEquals("Custom Xiangqi",plain(RoomText.game("xiangqi")));
            assertEquals("Custom Flight · Legacy",plain(RoomText.game("aeroplane")));
            assertEquals("Connect Four",plain(RoomText.game("connectfour")));
            Files.writeString(selected,"translations:\n  '加入了房间': 'unused'\nmessages:\n  'chat.joined': '<green>{player} arrived</green>'\n");
            Language.load(plugin);
            assertEquals(player+" arrived",plain(Language.component("chat.joined","player",player)));
            assertEquals(english,Files.readString(temp.resolve("languages/en_US.yml")));
        }finally{Files.delete(temp.resolve("languages/en_US.yml"));config.set("language","en");Language.load(plugin);}
    }

    @Test void menusOutcomesAndRosterUseNamedTranslationsWithoutChangingStoredNames() throws Exception {
        var plugin=mock(Tabletop3D.class);when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("LanguageTest"));var config=new YamlConfiguration();config.set("language","test");
        when(plugin.getConfig()).thenReturn(config);when(plugin.getDataFolder()).thenReturn(temp.toFile());

        Files.createDirectories(temp.resolve("languages"));
        Files.writeString(temp.resolve("languages/test.yml"),"messages:\n  'room.bot': 'Practice {number}'\n  'room.roster': '{players} | {empty}/{capacity}'\n  'result.winner': 'Winner: {player}'\n  'status.finished': 'Done'\n  'promotion.q': 'Regina'\n");
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
        var plugin=mock(Tabletop3D.class);when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("LanguageTest"));var config=new YamlConfiguration();config.set("language","test");
        when(plugin.getConfig()).thenReturn(config);when(plugin.getDataFolder()).thenReturn(temp.toFile());

        Files.createDirectories(temp.resolve("languages"));
        Files.writeString(temp.resolve("languages/en_US.yml"),"messages:\n  'menu.create': 'LOCAL OVERRIDE'\n");
        Files.writeString(temp.resolve("languages/test.yml"),"translations:\n  '创建房间': 'Create Room'\n");
        try{Language.load(plugin);assertEquals("Create Room",plain(Language.component("menu.create")));}
        finally{Files.delete(temp.resolve("languages/en_US.yml"));config.set("language","en");Language.load(plugin);}
    }

    @Test void namedMessagesProtectDynamicNamesAndKeepLegacyOverrides() throws Exception {
        var plugin=mock(Tabletop3D.class);when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("LanguageTest"));var config=new YamlConfiguration();
        config.set("language","test");when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());

        Files.createDirectories(temp.resolve("languages"));
        Files.writeString(temp.resolve("languages/test.yml"),"translations:\n  '确认离开': 'Leave now'\nmessages:\n  'table.turn': '<gold>Next: {player}</gold>'\n");
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
                if (java.util.Set.of("HandArt.java","HandModels.java").contains(file.getFileName().toString()) || name.contains("upstream") || !(name.endsWith(".java") || name.contains("resources\\menus") && name.endsWith(".yml"))) continue;
                var match = literal.matcher(Files.readString(file));
                while (match.find()) {
                    String phrase = match.group(1);
                    if (phrase.equals("楚河      漢界")) continue; // Board markings intentionally remain Chinese.
                    if (file.getFileName().toString().equals("MahjongTableHud.java")
                            && java.util.Set.of("東", "南", "西", "北").contains(phrase)) continue; // Seat wind inscriptions.
                    if (han.matcher(phrase).find() && han.matcher(Language.text(phrase)).find())
                        missing.add(name + ": " + phrase);
                }
            }
        }
        assertTrue(missing.isEmpty(), String.join("\n", missing));
    }

    @Test void customLanguageOverridesEnglishAndMissingKeysFallBack() throws Exception {
        var plugin = mock(Tabletop3D.class);when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("LanguageTest"));
        var config = new YamlConfiguration();
        config.set("language", "test");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getResource("languages/en_US.yml")).thenAnswer(inv ->
            getClass().getClassLoader().getResourceAsStream("languages/en_US.yml"));
        Files.createDirectories(temp.resolve("languages"));
        Files.writeString(temp.resolve("languages/test.yml"), "translations:\n  '棋盘游戏': 'Custom Boards'\n");
        try {
            Language.load(plugin);
            assertEquals("Custom Boards", Language.text("棋盘游戏"));
            assertEquals("Create Room", Language.text("创建房间"));
            assertTrue(Files.isRegularFile(temp.resolve("languages/en_US.yml")));
        } finally {
            config.set("language", "en");
            Language.load(plugin);
        }
    }
    @Test void oldCardLanguageCannotRestoreTheRetiredNameOrPenaltyDisplay() throws Exception {
        var plugin=mock(Tabletop3D.class);when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("LanguageTest"));var config=new YamlConfiguration();config.set("language","en");
        when(plugin.getConfig()).thenReturn(config);when(plugin.getDataFolder()).thenReturn(temp.toFile());

        Path english=temp.resolve("languages/en_US.yml");Files.createDirectories(english.getParent());
        String previous="messages:\n  'game.lastcard': 'Last Card'\n  'hand.card-state': 'Draw Penalty: {penalty}'\n";
        Files.writeString(english,previous);
        try {
            Language.load(plugin);assertEquals("Color Eight",plain(RoomText.game("lastcard")));
            assertFalse(plain(HandText.status("lastcard",new dev.tabletop3d.rules.LastCardGame(2,10))).contains("Penalty"));
            assertEquals(previous,Files.readString(english));
        }finally{Files.delete(english);Language.load(plugin);}
    }
    @Test void previousEnglishFileKeepsEditsAndReceivesNewBundledKeys() throws Exception {
        var plugin = mock(Tabletop3D.class);when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("LanguageTest"));
        var config = new YamlConfiguration();
        config.set("language", "en");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getResource("languages/en_US.yml")).thenAnswer(inv ->
            getClass().getClassLoader().getResourceAsStream("languages/en_US.yml"));
        Path english = temp.resolve("languages/en_US.yml");
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
