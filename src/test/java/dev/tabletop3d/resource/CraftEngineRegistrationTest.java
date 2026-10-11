package dev.tabletop3d.resource;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.support.WorkspaceTempFactory;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftEngineRegistrationTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path folder;

    private Tabletop3D plugin(boolean installed) {
        var server = mock(Server.class);
        var manager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(manager);
        if (installed) {
            var engine = mock(Plugin.class);
            when(engine.getDataFolder()).thenReturn(folder.resolve("engine-data").toFile());
            when(manager.getPlugin("CraftEngine")).thenReturn(engine);
        }
        var plugin = mock(Tabletop3D.class, CALLS_REAL_METHODS);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CraftEngineRegistrationTest"));
        return plugin;
    }

    @Test
    void firstLoadInstallsMatchingRegistrationsIntoDetectedEngineFolder() throws Exception {
        plugin(true).onLoad();
        Path target = folder.resolve("engine-data/resources/tabletop3d");
        for (String name : new String[] {"pack.yml", "configuration/items.yml"}) {
            assertTrue(Files.isRegularFile(target.resolve(name)), name);
            assertArrayEquals(Files.readAllBytes(Path.of("resource-pack/craftengine/resources/tabletop3d").resolve(name)),
                    Files.readAllBytes(target.resolve(name)), name);
        }
    }

    @Test
    void existingRegistrationRemainsUnchangedWhileMissingFilesAreInstalled() throws Exception {
        Path target = folder.resolve("engine-data/resources/tabletop3d");
        Files.createDirectories(target.resolve("configuration"));
        Path items = target.resolve("configuration/items.yml");
        Files.writeString(items, "items:\n  custom:item:\n    material: stone\n");
        byte[] custom = Files.readAllBytes(items);
        plugin(true).onLoad();
        assertArrayEquals(custom, Files.readAllBytes(items));
        assertTrue(Files.isRegularFile(target.resolve("pack.yml")));
    }

    @Test
    void absenceOfCraftEngineDoesNotCreateResourceFolders() throws Exception {
        plugin(false).onLoad();
        assertFalse(Files.exists(folder.resolve("engine-data")));
    }
}
