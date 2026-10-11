package dev.tabletop3d.room;

import com.google.gson.JsonPrimitive;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.TabletopTest;
import dev.tabletop3d.interaction.GameWorld;
import dev.tabletop3d.rules.GameFactory;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomLifecycleTest {
    @Test
    void legacyReturnCoordinatesSurviveAnUnloadedWorld(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        Tabletop3D plugin = mock(Tabletop3D.class);
        TabletopTest.set(plugin, "rooms", new LinkedHashMap<UUID, Room>());
        TabletopTest.set(plugin, "savedReturnPoints", new HashMap<>());
        TabletopTest.set(plugin, "loaded", true);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        doCallRealMethod().when(plugin).restore();
        doCallRealMethod().when(plugin).save();
        UUID player = UUID.randomUUID();
        var point = new RoomStore.ReturnPoint("unloaded-world", 12, 34, 56, 78, -9);
        var file = directory.resolve("rooms.json");
        RoomStore.write(file, List.of(), Map.of(player, point));

        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            plugin.restore();
            plugin.save();
        }

        assertEquals(Map.of(player, point), RoomStore.read(file).returns());
    }

    @Test
    void invalidPlayingHistoryStillRejectsRestoreAndPreservesItsFile(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        Tabletop3D plugin = mock(Tabletop3D.class);
        plugin.arena = mock(GameWorld.class);
        TabletopTest.set(plugin, "rooms", new LinkedHashMap<UUID, Room>());
        TabletopTest.set(plugin, "savedReturnPoints", new HashMap<>());
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        doCallRealMethod().when(plugin).restore();
        doCallRealMethod().when(plugin).start(any());
        when(plugin.prepareSeats(any())).thenReturn(true);
        Room room = new Room(UUID.randomUUID(), "gomoku", 2, 1, 0);
        room.fillBots();
        room.phase = Room.Phase.PLAYING;
        room.event(0, new JsonPrimitive("invalid-action"));
        var file = directory.resolve("rooms.json");
        RoomStore.write(file, List.of(room), Map.of());
        byte[] original = java.nio.file.Files.readAllBytes(file);

        assertThrows(IllegalStateException.class, plugin::restore);

        assertArrayEquals(original, java.nio.file.Files.readAllBytes(file));
        verify(plugin.arena, never()).render(any());
        verify(plugin, never()).save();
    }

    @Test
    void unsupportedRecordsFailBeforeInstallingAnyRooms(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        Tabletop3D plugin = mock(Tabletop3D.class, CALLS_REAL_METHODS);
        plugin.arena = mock(GameWorld.class);
        TabletopTest.set(plugin, "rooms", new LinkedHashMap<UUID, Room>());
        TabletopTest.set(plugin, "savedReturnPoints", new HashMap<>());
        doReturn(directory.toFile()).when(plugin).getDataFolder();
        doReturn(java.util.logging.Logger.getAnonymousLogger()).when(plugin).getLogger();
        UUID old = UUID.randomUUID(), other = UUID.randomUUID();
        String data =
                """
                {"returns":{},"rooms":[
                  {"id":"%s","kind":"color-eight","rulesVersion":1,"history":[{"seat":0,"action":"draw"}],"phase":"FINISHED"},
                  {"id":"%s","kind":"connectfour","capacity":2,"seed":1,"table":0,"phase":"LOBBY","revision":0,"seats":[],"history":[]} ]}
                """
                        .formatted(old, other);
        java.nio.file.Files.writeString(directory.resolve("rooms.json"), data);
        assertThrows(IllegalStateException.class, plugin::restore);
        assertTrue(plugin.rooms.isEmpty());
        assertEquals(data, java.nio.file.Files.readString(directory.resolve("rooms.json")));
        try (var files = java.nio.file.Files.list(directory)) {
            var backup =
                    files.filter(p -> p.getFileName().toString().startsWith("rooms.unreadable-"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(data, java.nio.file.Files.readString(backup));
        }
    }

    @Test
    void removedRoomsRejectAllPlayerMutationsBeforeAnySideEffect() throws Exception {
        Tabletop3D plugin = mock(Tabletop3D.class, CALLS_REAL_METHODS);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        doReturn(true).when(plugin).allowed(player);
        plugin.arena = mock(GameWorld.class);
        plugin.coordinator = mock(BoardOccupancy.class);
        TabletopTest.set(plugin, "rooms", new LinkedHashMap<UUID, Room>());
        Room room = new Room(UUID.randomUUID(), "chess", 2, 1, 0);
        room.join(id, "Player");
        room.board = GameFactory.create("chess", 2, 1);
        List<Runnable> actions =
                List.of(
                        () -> plugin.join(player, room),
                        () -> plugin.joinReserved(player, room),
                        () -> plugin.resume(player, room),
                        () -> plugin.enterArena(player, room),
                        () -> plugin.ready(player, room),
                        () -> plugin.startWithBots(player, room),
                        () ->
                                plugin.action(
                                        player,
                                        room,
                                        room.revision,
                                        new JsonPrimitive("move:e2:e4")),
                        () -> plugin.apply(room, 0, new JsonPrimitive("move:e2:e4"), null),
                        () -> plugin.rematch(player, room));
        for (Runnable action : actions) assertThrows(IllegalArgumentException.class, action::run);
        assertTrue(room.ready.isEmpty());
        assertTrue(room.history.isEmpty());
        assertEquals(1, room.seats.size());
        verifyNoInteractions(plugin.arena, plugin.coordinator);
    }

    @Test
    void permissionRevocationPreventsReadyBotsAndResume() throws Exception {
        Tabletop3D plugin = mock(Tabletop3D.class, CALLS_REAL_METHODS);
        Player player = mock(Player.class);
        doReturn(false).when(plugin).allowed(player);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Room room = new Room(UUID.randomUUID(), "chess", 2, 1, 0);
        room.join(player.getUniqueId(), "Player");
        TabletopTest.set(plugin, "rooms", new LinkedHashMap<>(Map.of(room.id, room)));
        plugin.arena = mock(GameWorld.class);
        plugin.ready(player, room);
        plugin.startWithBots(player, room);
        plugin.resume(player, room);
        assertTrue(room.ready.isEmpty());
        assertEquals(1, room.seats.size());
        verifyNoInteractions(plugin.arena);
    }
}
