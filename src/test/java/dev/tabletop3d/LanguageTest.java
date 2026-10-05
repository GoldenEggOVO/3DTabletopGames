package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class LanguageTest {
    @TempDir(factory = WorkspaceTempFactory.class)
    Path temp;

    @Test
    void completeRuleMessagesHaveNamedParametersAndNeverTranslatePlayerNames() throws Exception {
        String player = chineseCatalog().getString("game.xiangqi");
        var message =
                dev.tabletop3d.rules.RuleMessage.of(
                        "board.move", "player", 1, "move", "a2a4");
        String rendered = plain(BoardMessages.render(message));
        assertTrue(rendered.contains("a2") && rendered.contains("a4"), rendered);
        assertFalse(rendered.contains("board.move"));
        assertEquals(
                player + "'s turn",
                plain(
                        Language.component(
                                "color-eight.turn",
                                "player",
                                net.kyori.adventure.text.Component.text(player))));
    }

    @Test
    void bundledLanguagesExposeEveryMessageIndividuallyWithMatchingPlaceholders() throws Exception {
        var catalogs = new java.util.ArrayList<YamlConfiguration>();
        for (String locale : java.util.List.of("en_US", "zh_CN")) {
            var yaml = new YamlConfiguration();
            yaml.options().pathSeparator('\u001f');
            try (var reader =
                    new java.io.InputStreamReader(
                            getClass().getResourceAsStream("/languages/" + locale + ".yml"),
                            java.nio.charset.StandardCharsets.UTF_8)) {
                yaml.load(reader);
            }
            assertFalse(yaml.contains("messages"));
            assertFalse(yaml.contains("translations"));
            catalogs.add(yaml);
        }
        assertEquals(catalogs.getFirst().getKeys(false), catalogs.getLast().getKeys(false));
        for (String key : catalogs.getFirst().getKeys(false)) {
            assertFalse(
                    key.matches(".*\\.[a-f0-9]{6}$"),
                    "Message keys must describe their meaning: " + key);
            String english = catalogs.getFirst().getString(key),
                    chinese = catalogs.getLast().getString(key);
            assertNotNull(english, key);
            assertNotNull(chinese, key);
            assertEquals(
                    dev.tabletop3d.ui.MessageText.placeholders(english),
                    dev.tabletop3d.ui.MessageText.placeholders(chinese),
                    key);
            dev.tabletop3d.ui.MessageText.validate(english);
            dev.tabletop3d.ui.MessageText.validate(chinese);
        }
        assertTrue(Language.reload(temp, "zh_CN", w -> fail(w)));
        assertEquals(catalogs.getLast().getString("menu.create"), plain(Language.component("menu.create")));
        Language.reload(temp, "en_US", w -> fail(w));
    }

    @Test
    void chineseCatalogTranslatesProseWhileKeepingCommandsAndCardNotation() throws Exception {
        var catalog = chineseCatalog();
        var allowed = java.util.Set.of("A", "J", "Q", "K", "D", "X", "Z", "URL", "CraftEngine");
        var untranslated = new java.util.ArrayList<String>();
        for (String key : catalog.getKeys(false)) {
            String text = catalog.getString(key)
                    .replaceAll("\\{[^}]+}|<[^>]+>|§[0-9a-fk-or]", "")
                    .replaceAll("/3dtabletop(?: [a-z-]+)?", "");
            var words = java.util.regex.Pattern.compile("[A-Za-z]+").matcher(text);
            while (words.find())
                if (!allowed.contains(words.group())) untranslated.add(key + ": " + words.group());
        }
        assertTrue(untranslated.isEmpty(),
                "Untranslated Chinese messages: " + untranslated.stream().limit(12).toList());
        try {
            assertTrue(Language.reload(temp, "zh_CN", w -> fail(w)));
            assertEquals(catalog.getString("color-eight.turn").replace("{player}", "TestPlayer"),
                    plain(Language.component("color-eight.turn", "player", "TestPlayer")));
            assertTrue(plain(Language.component("chat.seat-held")).contains("/3dtabletop resume"));
            assertEquals("A", plain(Language.component("playing-card.rank.ace")));
        } finally {
            Language.reload(temp, "en_US", w -> fail(w));
        }
    }

    @Test
    void casinoStyleLanguagesAllowFlatAndNestedKeysAndKeepLastGoodReload() throws Exception {
        var warnings = new java.util.ArrayList<String>();
        Language.load(temp, "en_US", warnings::add);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(Files.isRegularFile(temp.resolve("en_US.yml")));
        assertTrue(Files.isRegularFile(temp.resolve("zh_CN.yml")));
        Files.writeString(
                temp.resolve("custom.yml"),
                "menu:\n  create: 'Custom Create'\nchat.joined: '{player} arrived'\n");
        try {
            assertTrue(Language.reload(temp, "custom", warnings::add));
            assertEquals("Custom Create", plain(Language.component("menu.create")));
            assertEquals(
                    "<red>name arrived",
                    plain(Language.component("chat.joined", "player", "<red>name")));
            long generation = Language.generation();
            Files.writeString(
                    temp.resolve("custom.yml"),
                    "menu.create: 'BAD'\nchat.joined: '{unexpected}'\n");
            assertFalse(Language.reload(temp, "custom", warnings::add));
            assertEquals(generation, Language.generation());
            assertEquals("Custom Create", plain(Language.component("menu.create")));
            assertTrue(
                    warnings.stream()
                            .anyMatch(w -> w.contains("custom.yml") && w.contains("chat.joined")));
        } finally {
            Language.reload(temp, "en_US", w -> fail(w));
        }
    }

    @Test
    void menusOutcomesAndRosterUseNamedTranslationsWithoutChangingStoredNames() throws Exception {
        var plugin = mock(Tabletop3D.class);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("LanguageTest"));
        var config = new YamlConfiguration();
        config.set("language", "test");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());

        Files.createDirectories(temp.resolve("languages"));
        Files.writeString(
                temp.resolve("languages/test.yml"),
                "room.bot: 'Practice {number}'\n"
                    + "room.roster: '{players} | {empty}/{capacity}'\n"
                    + "result.winner: 'Winner: {player}'\n"
                    + "status.finished: 'Done'\n"
                    + "promotion.q: 'Regina'\n");
        String name = "<red>" + chineseCatalog().getString("game.xiangqi") + "&c99";
        var room = new Room(java.util.UUID.randomUUID(), "chess", 2, 0, 0);
        room.join(java.util.UUID.randomUUID(), name);
        room.fillBots();
        room.phase = Room.Phase.FINISHED;
        room.result = "winner:0";
        try {
            Language.load(plugin);
            assertEquals(
                    name + ", Practice 2 | 0/2", plain(RoomText.roster(room.seats, room.capacity)));
            assertEquals("Winner: " + name, plain(RoomText.outcome(room, room.result)));
            assertEquals("Done", plain(RoomText.phase(room)));
            assertEquals(
                    "a7 → a8 · Promote to Regina",
                    plain(GameMenus.actionLabel(room, "move:a7:a8:q")));
            assertEquals(name, room.seats.getFirst().name());
            assertEquals("winner:0", room.result);
        } finally {
            config.set("language", "en_US");
            Language.load(plugin);
        }
    }


    @Test
    void physicalCoordinateHintsFollowTheChosenLanguage() {
        Language.load(temp, "en_US", message -> fail(message));
        try {
            for (String locale : java.util.List.of("en_US", "zh_CN")) {
                var catalog = Language.load(temp, locale, message -> fail(message));
                assertTrue(Language.reload(temp, locale, message -> fail(message)));
                assertEquals(catalog.get("board.die").replace("{number}", "2"),
                        GameWorld.coordinate("yacht", new dev.tabletop3d.rules.Cell("die", 2, 0, "", -1)));
            }
        } finally {
            Language.reload(temp, "en_US", message -> fail(message));
        }
    }

    private static String plain(net.kyori.adventure.text.Component text) {
        return dev.tabletop3d.ui.MessageText.plain(text);
    }

    private static YamlConfiguration chineseCatalog() throws Exception {
        var catalog = new YamlConfiguration();
        catalog.options().pathSeparator('\u001f');
        try (var input = LanguageTest.class.getResourceAsStream("/languages/zh_CN.yml")) {
            assertNotNull(input);
            catalog.loadFromString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return catalog;
    }
}
