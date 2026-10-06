package dev.tabletop3d.room;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.interaction.GameWorld;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfflineRestoreTest {
    @TempDir Path directory;

    @Test
    void persistedOfflineSeatsUseOwnerAuthenticatedRestoreInsteadOfAdmission() throws Exception {
        Tabletop3D plugin = mock(Tabletop3D.class);
        plugin.coordinator = mock(BoardOccupancy.class);
        plugin.arena = mock(GameWorld.class);
        set(plugin, "rooms", new LinkedHashMap<UUID, Room>());
        set(plugin, "returns", new HashMap<>());
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        doCallRealMethod().when(plugin).restore();
        UUID player = UUID.randomUUID();
        String source =
                "{\"returns\":{},\"rooms\":[{\"id\":\""
                        + UUID.randomUUID()
                        + "\",\"kind\":\"chess\",\"capacity\":2,\"seed\":1,\"table\":0,\"phase\":\"LOBBY\",\"revision\":1,\"history\":[],\"seats\":[{\"id\":\""
                        + player
                        + "\",\"name\":\"offline\",\"bot\":false}]}]}";
        Files.writeString(directory.resolve("rooms.json"), source);
        when(plugin.coordinator.restoreReservation(player, "chess")).thenReturn(true);
        assertDoesNotThrow(plugin::restore);
        verify(plugin.coordinator).restoreReservation(player, "chess");
        verify(plugin.coordinator, never()).reserve(any(), any());
        assertEquals(1, plugin.rooms.size());
        assertTrue(plugin.rooms.values().iterator().next().offline.containsKey(player));
        assertEquals(source, Files.readString(directory.resolve("rooms.json")));
    }

    @Test
    void removedMahjongProfilesRejectRestoreWithoutOverwritingOriginalRecords() throws Exception {
        for (String profile : List.of("fuzhou", "qinhuangdao")) {
            Path data = Files.createDirectory(directory.resolve(profile));
            Tabletop3D plugin = mock(Tabletop3D.class);
            set(plugin, "rooms", new LinkedHashMap<UUID, Room>());
            set(plugin, "returns", new HashMap<>());
            when(plugin.getDataFolder()).thenReturn(data.toFile());
            doCallRealMethod().when(plugin).restore();
            doCallRealMethod().when(plugin).save();
            String source =
                    "{\"returns\":{},\"rooms\":[{\"id\":\""
                            + UUID.randomUUID()
                            + "\",\"kind\":\"mahjong\",\"capacity\":4,\"seed\":1,\"table\":0,\"rulesVersion\":1,\"options\":{\"profile\":\""
                            + profile
                            + "\"}}]}";
            Path file = data.resolve("rooms.json");
            Files.writeString(file, source);
            var failure = assertThrows(IllegalStateException.class, plugin::restore);
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            assertEquals("Unknown mahjong profile: " + profile, failure.getCause().getMessage());
            assertTrue(plugin.rooms.isEmpty());
            plugin.save();
            assertEquals(source, Files.readString(file));
            try (var files = Files.list(data)) {
                var copies =
                        files.filter(
                                        p ->
                                                p.getFileName()
                                                        .toString()
                                                        .startsWith("rooms.unreadable-"))
                                .toList();
                assertEquals(1, copies.size());
                assertEquals(source, Files.readString(copies.getFirst()));
            }
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = Tabletop3D.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
