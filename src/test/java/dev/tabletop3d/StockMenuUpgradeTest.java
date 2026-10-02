package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class StockMenuUpgradeTest {
    @TempDir Path data;
    private static final String OLD_SETUP =
            """
            # Game setup; rules are immutable after room creation. Existing custom pages stay intact.
            Title: '<white>@title@'
            Body:
              content:
                type: message
                text: '<white>@description@'
                width: 370
            Bottom:
              type: multi
              columns: 2
              buttons:
                mode:
                  text: '<aqua>@label@'
                  width: 180
                capacity:
                  text: '<aqua>@label@'
                  width: 180
                entry:
                  text: '<white>@label@'
                  width: 180
              exit:
                text: '<gray>@label@'
                width: 180
            """;

    @Test
    void templatesUseConfiguredOrderWithoutRuntimeStockReplacement() throws Exception {
        var layouts = layouts(OLD_SETUP.replace("\n", "\r\n"));
        assertEquals(
                List.of("mode", "start"),
                render(layouts).buttons().stream().map(GameMenus.Button::id).toList());
        assertEquals(
                OLD_SETUP.replace("\n", "\r\n"), Files.readString(data.resolve("menus/setup.yml")));
    }

    @Test
    void customizedStockKeepsItsOrderAndFile() throws Exception {
        String custom = OLD_SETUP.replace("columns: 2", "columns: 3");
        var result = render(layouts(custom));
        assertEquals(
                List.of("mode", "start"),
                result.buttons().stream().map(GameMenus.Button::id).toList());
        assertEquals(3, result.config().getInt("Bottom.columns"));
        assertEquals(custom, Files.readString(data.resolve("menus/setup.yml")));
    }

    @Test
    void oldCustomButtonsCannotRestoreRemovedEntriesAndTheFileStaysIntact() throws Exception {
        String custom =
                """
                Title: '@title@'
                Bottom:
                  columns: 3
                  buttons:
                    rules:
                      text: 'Rules & Help'
                      actions: ['command: 3dtabletop rules']
                    details:
                      text: 'Room Details'
                    public-table:
                      text: 'Public Table Details'
                    leave:
                      text: 'My Exit'
                      width: 222
                """;
        var layouts = layouts(OLD_SETUP);
        Path file = data.resolve("menus/dialog.yml");
        Files.writeString(file, custom);
        var f = new MenuFlowTest.Fixture();
        Room room = f.addRoom(0);
        room.join(f.player.getUniqueId(), "Owner");
        room.phase = Room.Phase.PLAYING;
        f.menus.roomOptions(f.player, room);
        var result =
                layouts.load(
                        "dialog",
                        Component.text("Options"),
                        Component.empty(),
                        f.buttons,
                        UUID.randomUUID());
        assertEquals(
                List.of("leave", "back", "close"),
                result.buttons().stream().map(GameMenus.Button::id).toList());
        assertEquals("My Exit", result.config().getString("Bottom.buttons.slot0.text"));
        assertEquals(222, result.config().getInt("Bottom.buttons.slot0.width"));
        assertEquals(3, result.config().getInt("Bottom.columns"));
        assertEquals(custom, Files.readString(file));
    }

    private GameMenuLayouts layouts(String template) throws Exception {
        Tabletop3D plugin = mock(Tabletop3D.class);
        when(plugin.getDataFolder()).thenReturn(data.toFile());
        when(plugin.getResource(anyString()))
                .thenAnswer(i -> getClass().getClassLoader().getResourceAsStream(i.getArgument(0)));
        var layouts = new GameMenuLayouts(plugin);
        Files.writeString(data.resolve("menus/setup.yml"), template);
        return layouts;
    }

    private GameMenuLayouts.Rendered render(GameMenuLayouts layouts) {
        return layouts.load(
                "setup",
                Component.text("Setup"),
                Component.empty(),
                List.of(
                        new GameMenus.Button("mode", "Mode", () -> {}),
                        new GameMenus.Button("start", "Start", () -> {})),
                UUID.randomUUID());
    }
}
