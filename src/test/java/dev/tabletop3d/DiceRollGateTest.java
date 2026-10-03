package dev.tabletop3d;

import com.google.gson.JsonPrimitive;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiceRollGateTest {
    @Test void yachtRejectsKeepAndScoreThroughTheSharedPathWhileDiceAreRolling() throws Exception {
        Tabletop3D plugin = mock(Tabletop3D.class);
        TabletopTest.set(plugin, "rooms", new LinkedHashMap<UUID,Room>());
        plugin.arena = mock(GameWorld.class);
        Room room = new Room(UUID.randomUUID(), "yacht", 2, 0, 0);
        room.board = spy(room.newBoard()); room.phase = Room.Phase.PLAYING;
        plugin.rooms.put(room.id, room);
        when(plugin.arena.rolling(room)).thenReturn(true);
        doCallRealMethod().when(plugin).apply(any(), anyInt(), any(), any());
        for (String action : List.of("roll", "hold:die0", "score:ones"))
            plugin.apply(room, 0, new JsonPrimitive(action), null);
        assertTrue(room.history.isEmpty());
        verify(room.board, never()).apply(anyInt(), anyString());
        verify(plugin.arena, never()).render(any());
    }
    @Test void animationBlocksTheSharedActionPathForBotsCommandsAndMenus() throws Exception {
        Tabletop3D plugin=mock(Tabletop3D.class);TabletopTest.set(plugin,"rooms",new LinkedHashMap<UUID,Room>());
        plugin.arena=mock(GameWorld.class);Room room=new Room(UUID.randomUUID(),"ludo",2,0,0);room.board=spy(room.newBoard());room.phase=Room.Phase.PLAYING;
        plugin.rooms.put(room.id,room);when(plugin.arena.rolling(room)).thenReturn(true);
        doCallRealMethod().when(plugin).apply(any(),anyInt(),any(),any());plugin.apply(room,0,new JsonPrimitive("roll"),null);
        assertTrue(room.history.isEmpty());verify(room.board,never()).apply(anyInt(),anyString());verify(plugin.arena,never()).render(any());
    }
}
