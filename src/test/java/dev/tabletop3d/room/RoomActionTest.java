package dev.tabletop3d.room;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.TabletopTest;
import dev.tabletop3d.interaction.GameWorld;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomActionTest {
    static Stream<JsonElement> malformedActions() {
        return Stream.of(null, JsonNull.INSTANCE, new JsonObject(), new JsonArray(),
                new JsonPrimitive(42), new JsonPrimitive(true));
    }

    @ParameterizedTest
    @MethodSource("malformedActions")
    void malformedActionsCannotFinishTheRoomOrChangeItsHistory(JsonElement action) throws Exception {
        var plugin = mock(Tabletop3D.class);
        plugin.arena = mock(GameWorld.class);
        var room = new Room(UUID.randomUUID(), "connectfour", 2, 42, 0);
        room.board = room.newBoard();
        room.phase = Room.Phase.PLAYING;
        var cells = room.board.cells();
        TabletopTest.set(plugin, "rooms", new LinkedHashMap<>(Map.of(room.id, room)));
        doCallRealMethod().when(plugin).apply(any(), anyInt(), any(), any());

        assertDoesNotThrow(() -> plugin.apply(room, 0, action, null));

        assertEquals(Room.Phase.PLAYING, room.phase);
        assertEquals(cells, room.board.cells());
        assertTrue(room.history.isEmpty());
        verify(plugin, never()).finish(any(), anyString());
        verify(plugin, never()).save();
        verify(plugin.arena, never()).render(any());
    }
}
