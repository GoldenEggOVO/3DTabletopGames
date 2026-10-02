package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class WindowMenuTest {
    @Test
    void roomBrowserUsesAnOwnedFullWidthTextLinkAboveTheGameGrid() throws Exception {
        var config = new YamlConfiguration();
        try (var input = WindowMenuTest.class.getResourceAsStream("/menus/catalog.yml")) {
            config.loadFromString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        UUID token = UUID.randomUUID();
        var rendered = GameMenuLayouts.render(config, "Games", "", List.of(
                new GameMenus.Button("resource-pack", "Enable pack", () -> {}),
                new GameMenus.Button("rooms", "Browse Rooms", () -> {}),
                new GameMenus.Button("chess", "Chess", () -> {})), token);
        int index = rendered.buttons().stream().map(GameMenus.Button::id).toList().indexOf("rooms");
        assertEquals(350, rendered.config().getInt("Body.rooms.width"));
        var link = (net.kyori.adventure.text.Component) rendered.config().get("Body.rooms.component");
        assertEquals(net.kyori.adventure.text.event.ClickEvent.runCommand("/3dtabletop click 3dtabletop:" + token + " " + index), link.clickEvent());
        assertFalse(rendered.config().contains("Bottom.buttons.slot" + index));
    }
    @Test
    void catalogKeepsFullWidthHeaderLinksAndFillsGamesAcrossEachRow() throws Exception {
        var config = new YamlConfiguration();
        try (var input = WindowMenuTest.class.getResourceAsStream("/menus/catalog.yml")) {
            config.loadFromString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        List<GameMenus.Button> buttons = new ArrayList<>();
        buttons.add(new GameMenus.Button("resource-pack", "Enable pack", () -> {}));
        buttons.add(new GameMenus.Button("resume", "Resume", () -> {}));
        for (String id : List.of("connectfour", "xiangqi", "gomoku", "chess", "ludo"))
            buttons.add(new GameMenus.Button(id, id, () -> {}));
        buttons.add(new GameMenus.Button("close", "Close", () -> {}));
        UUID token = UUID.randomUUID();
        var rendered = GameMenuLayouts.render(config, "Games", "", buttons, token);
        assertEquals(2, rendered.config().getInt("Bottom.columns"));
        assertEquals(350, rendered.config().getInt("Body.resource-pack.width"));
        var link = (net.kyori.adventure.text.Component) rendered.config().get("Body.resource-pack.component");
        assertEquals(net.kyori.adventure.text.event.ClickEvent.runCommand("/3dtabletop click 3dtabletop:" + token + " 0"), link.clickEvent());
        assertEquals("Resume", rendered.config().getString("Body.resume.text"));
        assertEquals(List.of("connectfour", "xiangqi", "gomoku", "chess", "ludo"),
                rendered.buttons().subList(2, 7).stream().map(GameMenus.Button::id).toList());
        assertEquals(174, rendered.config().getInt("Bottom.buttons.slot2.width"));
        assertFalse(rendered.config().contains("Bottom.buttons.slot0"));
        assertEquals(List.of("3dtabletop:" + token + " 7"), rendered.config().getStringList("Bottom.exit.actions"));
    }

    @Test
    void navigationAlwaysUsesCurrentLanguageInsteadOfLiteralTemplateCaptions() throws Exception {
        var config = new YamlConfiguration();
        var chinese = chineseCatalog();
        config.set("Bottom.buttons.back.text", "&e" + chinese.getString("menu.back"));
        config.set("Bottom.buttons.main.text", "&e" + chinese.getString("menu.main"));
        config.set("Bottom.buttons.back.width", 330);
        var rendered =
                GameMenuLayouts.render(
                        config,
                        "Title",
                        "",
                        List.of(
                                new GameMenus.Button("back", "Back", () -> {}),
                                new GameMenus.Button("main", "Main Menu", () -> {})),
                        UUID.randomUUID());
        assertEquals(
                "Back",
                dev.tabletop3d.ui.MessageText.plain(
                        (net.kyori.adventure.text.Component)
                                rendered.config().get("Bottom.buttons.slot0.component")));
        assertEquals(
                "Main Menu",
                dev.tabletop3d.ui.MessageText.plain(
                        (net.kyori.adventure.text.Component)
                                rendered.config().get("Bottom.buttons.slot1.component")));
        assertEquals(330, rendered.config().getInt("Bottom.buttons.slot0.width"));
    }

    @Test
    void everyMenuUsesTheSameDedicatedCloseButtonAndPreservesBackStyle() throws Exception {
        for (String page : GameMenuLayouts.PAGES) {
            var config = new YamlConfiguration();
            try (var input = WindowMenuTest.class.getResourceAsStream("/menus/" + page + ".yml")) {
                assertNotNull(input, page);
                config.loadFromString(
                        new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            config.set("Bottom.exit.tooltip", List.of("Old page-specific close hint"));
            String backStyle = config.getString("Bottom.buttons.back.text");
            UUID token = UUID.randomUUID();
            var rendered =
                    GameMenuLayouts.render(
                            config,
                            "Title",
                            "",
                            List.of(
                                    new GameMenus.Button("back", "Back", () -> {}),
                                    new GameMenus.Button("close", "Close Menu", () -> {})),
                            token);
            assertEquals(
                    "<dark_gray>[ <red>Close Menu <dark_gray>]",
                    rendered.config().getString("Bottom.exit.text"),
                    page);
            assertEquals(230, rendered.config().getInt("Bottom.exit.width"), page);
            assertFalse(rendered.config().contains("Bottom.exit.tooltip"), page);
            assertEquals(
                    List.of("3dtabletop:" + token + " 1"),
                    rendered.config().getStringList("Bottom.exit.actions"),
                    page);
            assertEquals(
                    backStyle.replace("@label@", "Back"),
                    rendered.config().getString("Bottom.buttons.slot0.text"),
                    page);
        }
    }

    @Test
    void closeInvalidatesTheMenuSessionWithoutLeavingTheRoom() throws Exception {
        var f = new MenuFlowTest.Fixture();
        Room room = f.addRoom(0);
        room.join(f.player.getUniqueId(), "Owner");
        f.menus.room(f.player, room);
        f.buttons.stream()
                .filter(button -> button.id().equals("close"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        var field = GameMenus.class.getDeclaredField("sessions");
        field.setAccessible(true);
        assertFalse(((Map<?, ?>) field.get(f.menus)).containsKey(f.player.getUniqueId()));
        assertSame(room, f.plugin.room(f.player));
        verify(f.plugin, never()).leave(f.player);
    }

    @Test
    void roomMenusDoNotTranslateOrParseHumanNames() {
        var plugin = mock(Tabletop3D.class);
        when(plugin.allowed(any())).thenReturn(true);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        var world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        var room = new Room(UUID.randomUUID(), "chess", 2, 0, 0);
        String name = "<red>Ready&cBot";
        room.join(id, name);
        // The mocked plugin has no initialized fields; provide the live room registry.
        try {
            var field = Tabletop3D.class.getDeclaredField("rooms");
            field.setAccessible(true);
            field.set(plugin, new LinkedHashMap<>(Map.of(room.id, room)));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
        var view = mock(BoardWindow.class);
        when(view.render(
                        anyString(),
                        any(net.kyori.adventure.text.Component.class),
                        any(net.kyori.adventure.text.Component.class),
                        anyList(),
                        any()))
                .thenAnswer(
                        invocation ->
                                GameMenuLayouts.render(
                                        new YamlConfiguration(),
                                        (net.kyori.adventure.text.Component)
                                                invocation.getArgument(1),
                                        (net.kyori.adventure.text.Component)
                                                invocation.getArgument(2),
                                        invocation.getArgument(3),
                                        invocation.getArgument(4)));
        when(view.open(eq(player), any(), anyString())).thenReturn(true);
        new GameMenus(plugin, view).room(player, room);
        var body = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(view).render(eq("room"), any(), body.capture(), anyList(), any());
        assertTrue(
                dev.tabletop3d.ui.MessageText.plain(body.getValue()).contains(name),
                dev.tabletop3d.ui.MessageText.plain(body.getValue()));
    }

    @Test
    void namedTitleAndBodyKeepTheirOwnLanguageAndStyles() throws Exception {
        var config = new YamlConfiguration();
        config.set("Title", "<white>@title@");
        config.set("Body.content.text", "<gray>@description@");
        var chinese = chineseCatalog();
        String titleText = chinese.getString("game.chess");
        String bodyText = chinese.getString("menu.leave.confirm");
        var title = dev.tabletop3d.ui.MessageText.render("<red>" + titleText + "</red>");
        var body = dev.tabletop3d.ui.MessageText.render("<gold>" + bodyText + "</gold>");
        var rendered = GameMenuLayouts.render(config, title, body, List.of(), UUID.randomUUID());
        var actualTitle =
                (net.kyori.adventure.text.Component) rendered.config().get("Title-component");
        var actualBody =
                (net.kyori.adventure.text.Component)
                        rendered.config().get("Body.content.component");
        assertEquals(titleText, dev.tabletop3d.ui.MessageText.plain(actualTitle));
        assertEquals(bodyText, dev.tabletop3d.ui.MessageText.plain(actualBody));
        assertTrue(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .serialize(actualTitle)
                        .contains("§c" + titleText));
    }

    @Test
    void namedButtonValuesStayLiteralThroughLegacyAndMiniMessageLayouts() {
        for (String style : List.of("&a@label@", "<green>@label@</green>")) {
            var config = new YamlConfiguration();
            config.set("Bottom.buttons.entry.text", style);
            String name = "<red>Player&c";
            var rendered =
                    GameMenuLayouts.render(
                            config,
                            "Title",
                            "",
                            List.of(
                                    new GameMenus.Button(
                                            "entry",
                                            dev.tabletop3d.ui.MessageText.render(
                                                    "Next: {name}", "name", name),
                                            () -> {})),
                            UUID.randomUUID());
            var component =
                    (net.kyori.adventure.text.Component)
                            rendered.config().get("Bottom.buttons.slot0.component");
            assertEquals("Next: " + name, dev.tabletop3d.ui.MessageText.plain(component));
        }
    }

    @Test
    void defaultDialogTextIsEnglish() {
        String title =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(Language.component("menu.title"));
        assertEquals("3D Tabletop Games", title);
    }

    @Test
    void confirmLeaveButtonUsesEnglishAfterLayoutRemovesSourceColor() {
        var config = new YamlConfiguration();
        config.set("Bottom.buttons.entry.text", "&f@label@");
        var rendered =
                GameMenuLayouts.render(
                        config,
                        "Leave Room",
                        "",
                        List.of(
                                new GameMenus.Button(
                                        Language.component("menu.leave.confirm"), () -> {})),
                        UUID.randomUUID());
        String label =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(
                                BoardWindow.text(
                                        rendered.config().getString("Bottom.buttons.slot0.text")));
        assertEquals("Confirm Leave", label);
    }

    @Test
    void mainCatalogUsesBrandedTitle() throws Exception {
        var plugin = mock(Tabletop3D.class);
        TabletopTest.set(plugin, "rooms", new java.util.LinkedHashMap<UUID, Room>());
        when(plugin.allowed(any())).thenReturn(true);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        var view = mock(BoardWindow.class);
        when(view.render(
                        anyString(),
                        any(net.kyori.adventure.text.Component.class),
                        any(net.kyori.adventure.text.Component.class),
                        anyList(),
                        any()))
                .thenAnswer(
                        invocation ->
                                GameMenuLayouts.render(
                                        new YamlConfiguration(),
                                        (net.kyori.adventure.text.Component)
                                                invocation.getArgument(1),
                                        net.kyori.adventure.text.Component.empty(),
                                        invocation.getArgument(3),
                                        invocation.getArgument(4)));
        when(view.open(eq(player), any(), eq("catalog"))).thenReturn(true);
        var menus = new GameMenus(plugin, view);
        menus.main(player);
        verify(view)
                .render(
                        eq("catalog"),
                        eq(net.kyori.adventure.text.Component.text("3D Tabletop Games")),
                        eq(net.kyori.adventure.text.Component.empty()),
                        anyList(),
                        any());
        menus.close();
    }

    @Test
    void layoutPreservesStyleButReplacesActionsWithOwnedCallbacks() {
        var config = new YamlConfiguration();
        config.set("Title", "@title@");
        config.set("Bottom.buttons.connectfour.width", 222);
        config.set("Bottom.buttons.connectfour.actions", List.of("command: op bad"));
        config.set("Bottom.buttons.mahjong.text", "Mahjong");
        config.set("Events.Open", List.of("command: old"));
        UUID token = UUID.randomUUID();
        var rendered =
                GameMenuLayouts.render(
                        config,
                        "Board Games",
                        "",
                        List.of(new GameMenus.Button("connectfour", "Connect Four", () -> {})),
                        token);
        assertEquals(222, rendered.config().getInt("Bottom.buttons.slot0.width"));
        assertEquals(
                List.of("3dtabletop:" + token + " 0"),
                rendered.config().getStringList("Bottom.buttons.slot0.actions"));
        assertFalse(rendered.config().contains("Bottom.buttons.mahjong"));
        assertFalse(rendered.config().contains("Events"));
    }

    @Test
    void windowUsesRenderedOrderAndDoesNotAlsoSendChatButtons() {
        var plugin = mock(Tabletop3D.class);
        when(plugin.allowed(any())).thenReturn(true);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        var view = mock(BoardWindow.class);
        var clicked = new AtomicInteger();
        when(view.render(
                        anyString(),
                        any(net.kyori.adventure.text.Component.class),
                        any(net.kyori.adventure.text.Component.class),
                        anyList(),
                        any()))
                .thenAnswer(
                        invocation -> {
                            var config = new YamlConfiguration();
                            config.set("Bottom.buttons.second.text", "@label@");
                            return GameMenuLayouts.render(
                                    config,
                                    "Test",
                                    "",
                                    invocation.getArgument(3),
                                    invocation.getArgument(4));
                        });
        when(view.open(eq(player), any(), anyString())).thenReturn(true);
        var menus = new GameMenus(plugin, view);
        menus.show(
                player,
                "Test",
                "",
                List.of(
                        new GameMenus.Button("first", "First", () -> clicked.set(1)),
                        new GameMenus.Button("second", "Second", () -> clicked.set(2))),
                null);
        var rendered = org.mockito.ArgumentCaptor.forClass(YamlConfiguration.class);
        verify(view).open(eq(player), rendered.capture(), eq("dialog"));
        String action =
                rendered.getValue().getStringList("Bottom.buttons.slot0.actions").getFirst();
        menus.handle(player, action);
        assertEquals(2, clicked.get());
        clicked.set(0);
        menus.handle(player, action);
        assertEquals(0, clicked.get());
        verify(player, never()).sendMessage(anyString());
        menus.close();
    }
    private static YamlConfiguration chineseCatalog() throws Exception {
        var catalog = new YamlConfiguration();
        catalog.options().pathSeparator('\u001f');
        try (var input = WindowMenuTest.class.getResourceAsStream("/languages/zh_CN.yml")) {
            assertNotNull(input);
            catalog.loadFromString(
                    new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return catalog;
    }
}
