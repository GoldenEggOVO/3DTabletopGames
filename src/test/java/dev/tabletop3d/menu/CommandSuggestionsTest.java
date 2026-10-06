package dev.tabletop3d.menu;

import dev.tabletop3d.room.Room;
import dev.tabletop3d.rules.connectfour.ConnectFourGame;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommandSuggestionsTest {
    @Test void rootSuggestionsHideOnlyDuplicateNamespacedCommand() {
        var commands = new java.util.LinkedHashSet<>(List.of("3dtabletop", "3dtabletop:3dtabletop", "other:command"));
        CommandSuggestions.hideDuplicateRoot(commands);
        assertEquals(List.of("3dtabletop", "other:command"), List.copyOf(commands));
    }
    @Test void suggestsOnlyContextualCommandsAndPrefixes() {
        var room = new Room(UUID.randomUUID(), "connectfour", 2, 1L, 0);
        UUID player = UUID.randomUUID();
        room.join(player, "player");
        assertEquals(List.of("status"), CommandSuggestions.complete(new String[]{"s"}, List.of(room), null, true, true));
        assertEquals(List.of("create"), CommandSuggestions.complete(new String[]{"cr"}, List.of(room), null, false, true));
        assertTrue(CommandSuggestions.complete(new String[]{""}, List.of(room), room, false, false).isEmpty());
        assertEquals(List.of("connectfour"), CommandSuggestions.complete(new String[]{"create", "conn"}, List.of(room), null, false, true));
        assertEquals(List.of("2", "3", "4", "6"), CommandSuggestions.complete(new String[]{"create", "checkers", ""}, List.of(room), null, false, true));
        assertEquals(List.of(room.id.toString()), CommandSuggestions.complete(new String[]{"join", room.id.toString().substring(0, 4)}, List.of(room), null, false, true));
        room.phase = Room.Phase.PLAYING;
        assertFalse(CommandSuggestions.complete(new String[]{""}, List.of(room), room, false, true).contains("undo"));
        room.board = new ConnectFourGame();
        assertEquals(List.of("drop:3"), CommandSuggestions.complete(new String[]{"move", "drop:3"}, List.of(room), room, false, true));
        assertTrue(CommandSuggestions.complete(new String[]{"join", ""}, List.of(room), null, false, true).isEmpty());
    }
}
