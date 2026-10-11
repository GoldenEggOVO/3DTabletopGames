package dev.tabletop3d.menu;

import com.google.gson.JsonPrimitive;
import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.TabletopTest;
import dev.tabletop3d.interaction.GameWorld;
import dev.tabletop3d.resource.TabletopPack;
import dev.tabletop3d.room.Room;
import dev.tabletop3d.rules.texasholdem.TexasHoldemGame;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PokerRaiseWindowTest {
    private Tabletop3D plugin;
    private Player player;
    private Room room;
    private int seat;

    @BeforeEach
    void setup() throws Exception {
        plugin = mock(Tabletop3D.class);
        plugin.arena = mock(GameWorld.class);
        plugin.pack = mock(TabletopPack.class);
        player = mock(Player.class);
        room = new Room(UUID.randomUUID(), "texas-holdem", 3, 1, 0);
        room.board = new TexasHoldemGame(3, 1);
        seat = room.turn();
        room.fillBots();
        UUID id = UUID.randomUUID();
        room.seats.set(seat, new Room.Seat(id, "Player", false));
        when(player.getUniqueId()).thenReturn(id);
        when(plugin.arena.atTableWorld(player, room)).thenReturn(true);
        room.phase = Room.Phase.PLAYING;
        TabletopTest.set(plugin, "rooms", new LinkedHashMap<>(Map.of(room.id, room)));
    }

    @Test
    void movingToAnotherWorldInvalidatesAnOpenRaiseDialog() {
        when(plugin.arena.atTableWorld(player, room)).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> PokerRaiseWindow.submit(plugin, player, room, 0, seat, "20"));
        verify(plugin, never()).apply(any(), anyInt(), any(), any());
    }

    @Test
    void validRaiseRechecksThePackAndUsesTheCurrentRoom() {
        PokerRaiseWindow.submit(plugin, player, room, 0, seat, "20");
        verify(plugin.pack).require(player, room.kind);
        verify(plugin).apply(room, seat, new JsonPrimitive("raise:20"), null);
    }

    @Test
    void staleRoomRevisionCannotSubmitARaise() {
        room.revision++;
        assertThrows(IllegalArgumentException.class,
                () -> PokerRaiseWindow.submit(plugin, player, room, 0, seat, "20"));
        verify(plugin, never()).apply(any(), anyInt(), any(), any());
    }

    @Test
    void closedRoomCannotSubmitARaise() {
        plugin.rooms.clear();
        assertThrows(IllegalArgumentException.class,
                () -> PokerRaiseWindow.submit(plugin, player, room, 0, seat, "20"));
        verify(plugin, never()).apply(any(), anyInt(), any(), any());
    }

    @Test
    void invalidRaiseLeavesTheRoomUntouched() {
        assertThrows(IllegalArgumentException.class,
                () -> PokerRaiseWindow.submit(plugin, player, room, 0, seat, "5"));
        verify(plugin, never()).apply(any(), anyInt(), any(), any());
        assertEquals(0, room.revision);
        assertTrue(room.history.isEmpty());
    }
}
