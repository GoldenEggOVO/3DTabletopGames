package dev.tabletop3d;

import com.google.gson.*;
import dev.tabletop3d.rules.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.bukkit.*;
import org.bukkit.entity.Player;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomOptionsTest {
    @Test void startingSideMovesThePlayerAndPreservesHostAuthority() throws Exception {
        Room room=new Room(UUID.randomUUID(),"chess",2,25,0,Map.of("first","opponent"));
        Player host=mock(Player.class);when(host.getUniqueId()).thenReturn(UUID.randomUUID());room.join(host.getUniqueId(),"Host");room.fillBots();
        Tabletop3D plugin=mock(Tabletop3D.class);plugin.arena=mock(GameWorld.class);doCallRealMethod().when(plugin).prepareSeats(room);
        when(plugin.allowed(host)).thenReturn(true);when(plugin.arena.atTableWorld(host,room)).thenReturn(true);
        World world=mock(World.class);Location previous=new Location(world,0,80,1.7),target=new Location(world,0,80,-1.7);
        when(host.getLocation()).thenReturn(previous);when(plugin.arena.seatLocation(room,1)).thenReturn(target);when(host.teleport(target)).thenReturn(true);
        try(var bukkit=mockStatic(Bukkit.class)){
            bukkit.when(()->Bukkit.getPlayer(host.getUniqueId())).thenReturn(host);
            assertTrue(plugin.prepareSeats(room));verify(host).teleport(target);assertEquals(1,room.seat(host.getUniqueId()));assertTrue(room.host(host.getUniqueId()));
            assertTrue(plugin.prepareSeats(room));verify(host,times(1)).teleport(target);
        }
    }
    @Test void cancelledSideChangeDoesNotStartTheGameOrReassignItsHost() throws Exception {
        Room room=new Room(UUID.randomUUID(),"chess",2,25,0,Map.of("first","opponent"));
        Player host=mock(Player.class);when(host.getUniqueId()).thenReturn(UUID.randomUUID());room.join(host.getUniqueId(),"Host");room.fillBots();room.ready.add(host.getUniqueId());
        List<Room.Seat> before=List.copyOf(room.seats);Tabletop3D plugin=mock(Tabletop3D.class);plugin.arena=mock(GameWorld.class);
        doCallRealMethod().when(plugin).start(room);doCallRealMethod().when(plugin).prepareSeats(room);when(plugin.allowed(host)).thenReturn(true);
        when(plugin.arena.atTableWorld(host,room)).thenReturn(true);when(host.getLocation()).thenReturn(new Location(mock(World.class),0,80,0));
        when(plugin.arena.seatLocation(room,1)).thenReturn(new Location(mock(World.class),0,80,1));
        try(var bukkit=mockStatic(Bukkit.class)){
            bukkit.when(()->Bukkit.getPlayer(host.getUniqueId())).thenReturn(host);plugin.start(room);
            assertEquals(before,room.seats);assertEquals(Room.Phase.LOBBY,room.phase);assertNotNull(room.board);assertTrue(room.ready.isEmpty());assertTrue(room.host(host.getUniqueId()));
            verify(plugin.arena).render(room);
        }
    }
    @Test void choosingSecondChangesSeatsWithoutChangingChessColorsOrTheOpeningRule(){
        Room room=new Room(UUID.randomUUID(),"chess",2,25,0,Map.of("first","opponent"));
        UUID host=UUID.randomUUID();room.join(host,"Host");room.fillBots();room.prepareSeats();
        assertEquals(1,room.seat(host));assertEquals(0,room.newBoard().currentPlayer());
        assertTrue(room.newBoard().legalActions(0).contains("move:e2:e4"));
        room.prepareSeats();assertEquals(1,room.seat(host),"Empty-history restart must not swap twice");
    }
    @Test void missingOptionsRetainLegacyLudoDeployment(){
        assertEquals(Map.of(),Room.readOptions(new JsonObject()));
        Room room=new Room(UUID.randomUUID(),"ludo",2,12,0);
        assertEquals(new LudoGame(2,12).cells(),room.newBoard().cells());
    }
    @Test void configuredRulesAreImmutableAndSurviveJsonAndFreshRounds(){
        Map<String,String> draft=new HashMap<>(Map.of("start","six","blocking","on","goal","over","finish","all"));
        Room room=new Room(UUID.randomUUID(),"ludo",2,12,0,draft);draft.clear();
        assertThrows(UnsupportedOperationException.class,()->room.options.put("start","automatic"));
        JsonObject record=new JsonObject();record.add("options",new Gson().toJsonTree(room.options));
        Room restored=new Room(room.id,room.kind,2,12,0,Room.readOptions(record));
        assertEquals(room.options,restored.options);assertTrue(restored.newBoard().cells().stream().filter(c->c.owner()>=0).allMatch(c->c.id().startsWith("ba")));
        room.phase=Room.Phase.FINISHED;RoundActions.fresh(room,13);
        assertEquals(restored.options,room.options);assertEquals(restored.newBoard().cells(),room.newBoard().cells());
    }
    @Test void malformedOrUnknownOptionsAreRejectedInsteadOfChangingSavedRules(){
        for(String json:List.of("{\"options\":null}","{\"options\":[]}","{\"options\":{\"blocking\":true}}"))
            assertThrows(RuntimeException.class,()->Room.readOptions(JsonParser.parseString(json).getAsJsonObject()));
        assertThrows(IllegalArgumentException.class,()->new Room(UUID.randomUUID(),"ludo",2,0,0,Map.of("blocking","double")));
        assertThrows(IllegalArgumentException.class,()->new Room(UUID.randomUUID(),"chess",2,0,0,Map.of("blocking","on")));
    }
}
