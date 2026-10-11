package dev.tabletop3d.command;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.menu.GameMenus;
import dev.tabletop3d.room.Room;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TabletopCommandTest {
    @Test
    void anUppercaseUniqueRoomPrefixFindsTheSameRoom() throws Exception {
        Tabletop3D plugin = mock(Tabletop3D.class);
        Player player = mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        Room room = new Room(UUID.fromString("abcdef00-0000-0000-0000-000000000001"), "chess", 2, 1, 0);
        var rooms = Tabletop3D.class.getField("rooms");
        rooms.setAccessible(true);
        rooms.set(plugin, new LinkedHashMap<>(Map.of(room.id, room)));
        new TabletopCommand(plugin).onCommand(player, null, "3dtabletop",
                new String[] {"join", "ABCDEF"});
        verify(plugin).join(player, room);
    }

    @Test
    void ambiguousRoomPrefixNeverJoinsAnArbitraryRoom() throws Exception {
        Tabletop3D plugin = mock(Tabletop3D.class);
        Player player = mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        Room first = new Room(UUID.fromString("abcdef00-0000-0000-0000-000000000001"), "chess", 2, 1, 0);
        Room second = new Room(UUID.fromString("abcdef00-0000-0000-0000-000000000002"), "chess", 2, 1, 1);
        var rooms = Tabletop3D.class.getField("rooms");
        rooms.setAccessible(true);
        rooms.set(plugin, new LinkedHashMap<>(Map.of(first.id, first, second.id, second)));
        new TabletopCommand(plugin).onCommand(player, null, "3dtabletop",
                new String[] {"join", "abcdef"});
        verify(plugin, never()).join(any(), any());
        verify(plugin).tell(eq(player), any());
    }

    @Test
    void mixedCaseGameUsesItsCanonicalNameAndDefaultCapacity() {
        var plugin = mock(Tabletop3D.class);
        var player = mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        var command = new TabletopCommand(plugin);

        assertTrue(command.onCommand(player, null, "3dtabletop", new String[] {"CREATE", "MAHJONG"}));

        verify(plugin).create(player, "mahjong", 4);
    }

    @Test
    void deniedPlayerCannotCreateOrOpenAMenu() {
        var plugin = mock(Tabletop3D.class);
        var player = mock(Player.class);
        plugin.menus = mock(GameMenus.class);
        var command = new TabletopCommand(plugin);

        command.onCommand(player, null, "3dtabletop", new String[] {"create", "chess"});
        command.onCommand(player, null, "3dtabletop", new String[0]);

        verify(plugin, never()).create(any(), anyString(), anyInt());
        verifyNoInteractions(plugin.menus);
        verify(plugin, times(2)).tell(eq(player), any());
    }

    @Test
    void privateCardSuggestionsAreVisibleOnlyToTheActingPlayer() throws Exception {
        var plugin = mock(Tabletop3D.class);
        var player = mock(Player.class);
        var room = new Room(UUID.randomUUID(), "color-eight", 2, 17, 0);
        room.join(UUID.randomUUID(), "First");
        room.join(UUID.randomUUID(), "Second");
        room.board = room.newBoard();
        room.phase = Room.Phase.PLAYING;
        var registry = Tabletop3D.class.getField("rooms");
        registry.setAccessible(true);
        registry.set(plugin, new LinkedHashMap<>(java.util.Map.of(room.id, room)));
        when(plugin.allowed(player)).thenReturn(true);
        when(plugin.room(player)).thenReturn(room);
        var command = new TabletopCommand(plugin);
        String[] arguments = {"move", "play:"};

        when(player.getUniqueId()).thenReturn(room.seats.get(1 - room.turn()).id());
        assertEquals(List.of(), command.onTabComplete(player, null, "3dtabletop", arguments));

        when(player.getUniqueId()).thenReturn(room.seats.get(room.turn()).id());
        assertFalse(command.onTabComplete(player, null, "3dtabletop", arguments).isEmpty());
    }
}
