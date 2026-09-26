package dev.tabletop3d;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DataMigrationTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path directory;

    @Test void copiesLegacyRoomsMenusAndConfigWithoutChangingOriginal() throws Exception {
        Path old = directory.resolve("ServerBoards");
        Files.createDirectories(old.resolve("menus"));
        Files.writeString(old.resolve("rooms.json"), "{\"schema\":1}");
        Files.writeString(old.resolve("config.yml"), "max-rooms: 7\n");
        Files.writeString(old.resolve("menus/catalog.yml"), "Title: '&f旧菜单'\n");
        Path current = directory.resolve("3dtabletop");

        DataMigration.copyLegacy(old, current);

        assertEquals("{\"schema\":1}", Files.readString(current.resolve("rooms.json")));
        assertEquals("max-rooms: 7\n", Files.readString(current.resolve("config.yml")));
        assertEquals("Title: '&f旧菜单'\n", Files.readString(current.resolve("menus/catalog.yml")));
        assertEquals("{\"schema\":1}", Files.readString(old.resolve("rooms.json")));
        assertTrue(Files.isRegularFile(current.resolve("migration-from-serverboards.txt")));
        DataMigration.copyLegacy(old, current);
    }

    @Test void refusesToOverwriteNewData() throws Exception {
        Path old = directory.resolve("ServerBoards");
        Path current = directory.resolve("3dtabletop");
        Files.createDirectories(old);
        Files.createDirectories(current);
        Files.writeString(old.resolve("rooms.json"), "old");
        Files.writeString(current.resolve("rooms.json"), "new");
        assertThrows(IllegalStateException.class, () -> DataMigration.copyLegacy(old, current));
        assertEquals("new", Files.readString(current.resolve("rooms.json")));
    }
}
