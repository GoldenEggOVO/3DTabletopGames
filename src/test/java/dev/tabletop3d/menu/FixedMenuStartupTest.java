package dev.tabletop3d.menu;

import dev.tabletop3d.Tabletop3D;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FixedMenuStartupTest {
    @TempDir Path data;

    @Test
    void openingMenusDoesNotCreateTemplatesOnDisk() {
        var window = new BoardWindow(plugin());
        window.render(
                "catalog",
                Component.text("Games"),
                Component.empty(),
                List.of(),
                UUID.randomUUID());
        assertFalse(Files.exists(data.resolve("menus")));
    }

    @Test
    void obsoleteBrokenTemplateCannotPreventOpeningTheMenu() throws Exception {
        Files.createDirectory(data.resolve("menus"));
        Files.writeString(data.resolve("menus/room.yml"), "Title: [broken\n");
        var window = new BoardWindow(plugin());
        assertDoesNotThrow(
                () ->
                        window.render(
                                "room",
                                Component.text("Room"),
                                Component.empty(),
                                List.of(new GameMenus.Button("leave", "Leave Room", () -> {})),
                                UUID.randomUUID()));
    }

    private Tabletop3D plugin() {
        var plugin = mock(Tabletop3D.class);
        when(plugin.getDataFolder()).thenReturn(data.toFile());
        when(plugin.getResource(anyString()))
                .thenAnswer(
                        call ->
                                getClass()
                                        .getClassLoader()
                                        .getResourceAsStream(call.getArgument(0)));
        return plugin;
    }
}
