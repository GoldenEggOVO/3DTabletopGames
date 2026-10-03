package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class RoomStoreTest {
    @TempDir Path directory;

    @Test
    void currentRoomsRoundTripIdentityOptionsSeatsAndReplay() throws Exception {
        List<Room> originals = new ArrayList<>();
        for (String kind : List.of("mahjong", "color-eight", "chess", "ludo", "doudizhu", "liars-bar", "texas-holdem")) {
            int capacity = Tabletop3D.defaultCapacity(kind);
            Room room = new Room(UUID.randomUUID(), kind, capacity, 42, originals.size());
            room.join(UUID.randomUUID(), "<red>literal name");
            room.fillBots();
            room.anchorWorld = UUID.randomUUID();
            room.anchorX = 12.5;
            room.anchorY = 70;
            room.anchorZ = -4;
            room.sideTray = true;
            room.board = room.newBoard();
            room.phase = Room.Phase.PLAYING;
            for (int index = 0; index < 16 && !room.board.finished(); index++) {
                int seat = room.board.currentPlayer();
                String action = BoardBots.choose(room.board, seat, new Random(index));
                if (action == null) break;
                room.board.apply(seat, action);
                room.event(seat, new com.google.gson.JsonPrimitive(action));
                room.revision++;
            }
            originals.add(room);
        }
        Path file = directory.resolve("rooms.json");
        var returnPoint = new RoomStore.ReturnPoint("world", 1, 2, 3, 45, -10);
        UUID player = UUID.randomUUID();
        RoomStore.write(file, originals, Map.of(player, returnPoint));
        var restored = RoomStore.read(file);
        assertEquals(Map.of(player, returnPoint), restored.returns());
        for (int index = 0; index < originals.size(); index++) {
            Room before = originals.get(index), after = restored.rooms().get(index);
            assertEquals(before.id, after.id);
            assertEquals(before.seats, after.seats);
            assertEquals(before.owner, after.owner);
            assertEquals(before.history, after.history);
            assertEquals(before.anchorWorld, after.anchorWorld);
            assertEquals(before.anchorX, after.anchorX);
            assertEquals(before.revision, after.revision);
            assertTrue(after.sideTray);
            var replay = after.newBoard();
            for (var element : after.history) {
                var move = element.getAsJsonObject();
                replay.applyRecorded(move.get("seat").getAsInt(), move.get("action").getAsString());
            }
            assertEquals(before.board.cells(), replay.cells());
            assertEquals(before.board.publicInfo(), replay.publicInfo());
            if (before.board instanceof dev.tabletop3d.rules.HandGame original) {
                var restoredHands = (dev.tabletop3d.rules.HandGame) replay;
                for (int seat = 0; seat < before.capacity; seat++)
                    assertEquals(original.hand(seat), restoredHands.hand(seat));
            }
            assertEquals(
                    before.board.legalActions(before.turn()),
                    replay.legalActions(after.turn() < 0 ? before.turn() : after.turn()));
        }
    }

    @Test
    void futureSchemaFailsBeforeReturningPartialRooms() throws Exception {
        Path file = directory.resolve("rooms.json");
        String data = "{\"schema\":999,\"rooms\":[],\"returns\":{}}";
        Files.writeString(file, data);
        assertThrows(IllegalArgumentException.class, () -> RoomStore.read(file));
        assertEquals(data, Files.readString(file));
    }
}
