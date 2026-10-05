package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.tabletop3d.ui.MessageText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class WindowMenuTest {
    @Test
    void catalogKeepsFullWidthLinksAndFillsGamesAcrossEachRow() {
        UUID token = UUID.randomUUID();
        var menu =
                layout(
                        "catalog",
                        List.of(
                                button("xiangqi"),
                                button("resource-pack"),
                                button("resume"),
                                button("gomoku"),
                                button("chess"),
                                button("connectfour"),
                                button("close")),
                        token);
        assertEquals(2, menu.columns());
        assertEquals(
                List.of(
                        "resource-pack",
                        "resume",
                        "connectfour",
                        "xiangqi",
                        "gomoku",
                        "chess",
                        "close"),
                menu.buttons().stream().map(GameMenus.Button::id).toList());
        assertEquals(2, menu.headers().size());
        assertEquals(350, menu.headers().getFirst().width());
        assertEquals(
                ClickEvent.runCommand("/3dtabletop click 3dtabletop:" + token + " 0"),
                menu.headers().getFirst().caption().clickEvent());
        assertEquals(
                List.of("connectfour", "xiangqi", "gomoku", "chess"),
                menu.controls().stream()
                        .map(control -> MessageText.plain(control.caption()))
                        .toList());
        assertTrue(menu.controls().stream().allMatch(control -> control.width() == 174));
        assertEquals("3dtabletop:" + token + " 6", menu.exit().action());
    }

    @Test
    void roomBrowserUsesAnOwnedFullWidthLink() {
        UUID token = UUID.randomUUID();
        var menu =
                layout(
                        "catalog",
                        List.of(button("resource-pack"), button("rooms"), button("chess")),
                        token);
        assertEquals(
                List.of("resource-pack", "rooms"),
                menu.headers().stream()
                        .map(control -> MessageText.plain(control.caption()))
                        .toList());
        var browser = menu.headers().get(1);
        assertEquals(350, browser.width());
        assertEquals(
                ClickEvent.runCommand("/3dtabletop click 3dtabletop:" + token + " 1"),
                browser.caption().clickEvent());
        assertEquals(1, menu.controls().size());
    }

    @Test
    void roomAndSetupKeepTheirActionOrderAndWidths() {
        var room =
                layout(
                        "room",
                        List.of(
                                button("leave"),
                                button("entry"),
                                button("play"),
                                button("bots"),
                                button("ready"),
                                button("back")),
                        UUID.randomUUID());
        assertEquals(
                List.of("ready", "bots", "play", "entry", "leave", "back"),
                room.buttons().stream().map(GameMenus.Button::id).toList());
        assertEquals(1, room.columns());
        assertTrue(room.controls().stream().allMatch(control -> control.width() == 330));
        var setup =
                layout(
                        "setup",
                        List.of(
                                button("details"),
                                button("capacity"),
                                button("mode"),
                                button("start"),
                                button("rule-profile")),
                        UUID.randomUUID());
        assertEquals(
                List.of("start", "mode", "capacity", "rule-profile", "details"),
                setup.buttons().stream().map(GameMenus.Button::id).toList());
        assertEquals(1, setup.columns());
        assertEquals(330, setup.controls().getFirst().width());
    }

    @Test
    void handPaginationAndBackUseTheExistingTwoColumnLayout() {
        var menu =
                layout(
                        "hand",
                        List.of(
                                button("back"),
                                button("next"),
                                button("entry"),
                                button("hand-action"),
                                button("previous")),
                        UUID.randomUUID());
        assertEquals(
                List.of("hand-action", "entry", "previous", "next", "back"),
                menu.buttons().stream().map(GameMenus.Button::id).toList());
        assertEquals(2, menu.columns());
        assertEquals(390, menu.descriptionWidth());
        assertTrue(menu.controls().stream().allMatch(control -> control.width() == 185));
        assertEquals(NamedTextColor.GRAY, menu.controls().getLast().caption().color());
    }

    @Test
    void everyPageUsesTheSameDedicatedCloseControlAndLocalizedNavigation() {
        for (String page : List.of("dialog", "catalog", "room", "setup", "hand")) {
            UUID token = UUID.randomUUID();
            var menu =
                    layout(
                            page,
                            List.of(
                                    new GameMenus.Button("back", "Return", () -> {}),
                                    new GameMenus.Button("close", "Close Menu", () -> {})),
                            token);
            assertEquals("[ Close Menu ]", MessageText.plain(menu.exit().caption()), page);
            assertEquals(230, menu.exit().width(), page);
            assertEquals("3dtabletop:" + token + " 1", menu.exit().action(), page);
            assertEquals("Return", MessageText.plain(menu.controls().getFirst().caption()), page);
        }
    }

    @Test
    void titlesDescriptionsAndButtonParametersKeepTheirStylesAndLiteralNames() {
        String name = "<red>Player&c";
        var title = Component.text(name, NamedTextColor.RED);
        var description = Component.text("Room details", NamedTextColor.GOLD);
        var button =
                new GameMenus.Button(
                        "entry", MessageText.render("Next: {name}", "name", name), () -> {});
        var menu =
                BoardWindow.layout(
                        "dialog", title, description, List.of(button), UUID.randomUUID());
        assertEquals(name, MessageText.plain(menu.title()));
        assertEquals("Room details", MessageText.plain(menu.description()));
        assertEquals("Next: " + name, MessageText.plain(menu.controls().getFirst().caption()));
        assertTrue(
                LegacyComponentSerializer.legacySection()
                        .serialize(menu.title())
                        .contains("§c" + name));
    }

    @Test
    void closeInvalidatesTheSessionWithoutLeavingTheRoom() throws Exception {
        var fixture = new MenuFlowTest.Fixture();
        Room room = fixture.addRoom(0);
        room.join(fixture.player.getUniqueId(), "Owner");
        fixture.menus.room(fixture.player, room);
        fixture.buttons.stream()
                .filter(button -> button.id().equals("close"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        assertFalse(fixture.menus.active(fixture.player));
        assertSame(room, fixture.plugin.room(fixture.player));
        verify(fixture.plugin, never()).leave(fixture.player);
    }

    @Test
    void nativeMenuUsesRenderedCallbackOrderAndRejectsRepeatedClicks() throws Exception {
        var plugin = mock(Tabletop3D.class);
        when(plugin.allowed(any())).thenReturn(true);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        var view = mock(BoardWindow.class);
        when(view.render(anyString(), any(Component.class), any(Component.class), anyList(), any()))
                .thenAnswer(
                        call ->
                                BoardWindow.layout(
                                        call.getArgument(0),
                                        call.getArgument(1),
                                        call.getArgument(2),
                                        call.getArgument(3),
                                        call.getArgument(4)));
        when(view.open(eq(player), any())).thenReturn(true);
        var menus = new GameMenus(plugin, view);
        var clicked = new AtomicInteger();
        menus.show(
                player,
                "Test",
                "",
                List.of(
                        new GameMenus.Button("leave", "Leave", () -> clicked.set(1)),
                        new GameMenus.Button("play", "Play", () -> clicked.set(2))),
                null,
                "room");
        var rendered = ArgumentCaptor.forClass(BoardWindow.Menu.class);
        verify(view).open(eq(player), rendered.capture());
        String action = rendered.getValue().controls().getFirst().action();
        assertEquals(
                "Play", MessageText.plain(rendered.getValue().controls().getFirst().caption()));
        menus.handle(player, action);
        assertEquals(2, clicked.get());
        clicked.set(0);
        menus.handle(player, action);
        assertEquals(0, clicked.get());
        verify(player, never()).sendMessage(any(Component.class));
        menus.close();
    }

    private static BoardWindow.Menu layout(
            String page, List<GameMenus.Button> buttons, UUID token) {
        return BoardWindow.layout(page, Component.text("Title"), Component.empty(), buttons, token);
    }

    private static GameMenus.Button button(String id) {
        return new GameMenus.Button(id, id, () -> {});
    }
}
