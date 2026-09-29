package dev.tabletop3d;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MahjongTableControlsTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void mahjongHoverHighlightsOnlyOneTileWithoutMovingAnyTile(){
        var f=new HandTableTest.Fixture("mahjong");int start=f.entities.size();f.table.show(f.owner);
        Map<Entity,Location> positions=new HashMap<>();
        for(Entity e:f.entities.subList(start,f.entities.size()))positions.put(e,e.getLocation());
        Entity first=f.entities.get(start);f.table.hover(f.owner,"a");
        for(int i=0;i<5;i++)f.table.tick();
        for(var e:positions.entrySet())assertEquals(e.getValue(),e.getKey().getLocation(),"Mahjong tiles stay in place");
        verify(first).setGlowing(true);
        f.table.hover(f.owner,null);verify(first,atLeastOnce()).setGlowing(false);
        f.table.close();
    }
    @Test void groupsKeepAllChoicesAndOnlyPermitRuleLegalSkipping(){
        var groups=MahjongControls.groups(List.of("chi:a,b","chi:a,c","kan-open:a,b,c","pon:a,b","pass"));
        assertEquals(List.of("kan","pon","chi","pass"),new ArrayList<>(groups.keySet()));
        assertEquals(List.of("chi:a,b","chi:a,c"),groups.get("chi"));
        assertEquals(List.of("ron"),new ArrayList<>(MahjongControls.groups(List.of("ron")).keySet()));
        assertEquals(List.of("kan","dismiss"),new ArrayList<>(MahjongControls.groups(List.of("kan-closed:a","discard:a")).keySet()));
        assertTrue(MahjongControls.groups(List.of("exchange:a,b,c","missing:m","discard:a")).isEmpty());
    }
    @Test void callButtonsAreOwnerOnlyReachableAndDisappearAfterResponse(){
        var f=new HandTableTest.Fixture("mahjong");
        when(f.game.legalActions(0)).thenReturn(List.of("chi:a,b","chi:a,c","kan-open:a,b,c","pass"));
        int start=f.entities.size();f.table.show(f.owner);
        for(Entity e:f.entities.subList(start,f.entities.size())){
            verify(e).setVisibleByDefault(false);verify(f.owner).showEntity(f.plugin,e);
            verify(f.spectator,never()).showEntity(f.plugin,e);
        }
        Location eye=f.origin.clone().add(0,.32,1.8);
        assertEquals("chi",f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        assertNull(f.table.callHit(f.spectator,eye,new Vector(0,0,-1)));
        assertNull(f.table.callHit(f.owner,eye.clone().add(0,0,6),new Vector(0,0,-1)));
        when(f.world.rayTraceBlocks(any(),any(),anyDouble(),any(),eq(true)))
            .thenReturn(new org.bukkit.util.RayTraceResult(eye.toVector()));
        assertNull(f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        when(f.world.rayTraceBlocks(any(),any(),anyDouble(),any(),eq(true))).thenReturn(null);
        List<Entity> calls=List.copyOf(f.entities.subList(f.entities.size()-6,f.entities.size()));
        when(f.game.legalActions(0)).thenReturn(List.of());f.room.revision++;
        assertNull(f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        calls.forEach(e->verify(e).remove());f.table.close();
    }
    @Test void skipOnOwnTurnOnlyDismissesThePromptAndItReturnsOnTheNextRevision(){
        var f=new HandTableTest.Fixture("mahjong");
        when(f.game.legalActions(0)).thenReturn(List.of("kan-closed:a","discard:b"));f.table.show(f.owner);
        Location eye=f.origin.clone().add(.125,.32,1.8);
        assertEquals("dismiss",f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        f.table.dismissCalls(f.owner);f.table.show(f.owner);
        assertNull(f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        verify(f.game,never()).apply(anyInt(),anyString());
        f.room.revision++;assertEquals("dismiss",f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        f.room.phase=Room.Phase.FINISHED;assertNull(f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        f.table.close();
    }
    @Test void cardTablesIgnoreMahjongButtonHitChecksWithoutRemovingTheHand(){
        var f=new HandTableTest.Fixture("lastcard");int start=f.entities.size();f.table.show(f.owner);
        assertNull(f.table.callHit(f.owner,f.origin.clone().add(0,.32,1.8),new Vector(0,0,-1)));
        for(Entity e:f.entities.subList(start,f.entities.size()))verify(e,never()).remove();f.table.close();
    }
    @Test void tableCallsExecuteSingleChoicesAndKeepMultipleCombinationsSelectable() throws Exception {
        var f=new HandTableTest.Fixture("mahjong");
        var arena=mock(GameWorld.class,CALLS_REAL_METHODS);
        doNothing().when(arena).render(f.room);
        TabletopTest.set(arena,"plugin",f.plugin);TabletopTest.set(arena,"selections",new HashMap<>());
        TabletopTest.set(arena,"views",new HashMap<>());f.plugin.menus=mock(GameMenus.class);
        var click=GameWorld.class.getDeclaredMethod("pickCell",org.bukkit.entity.Player.class,Room.class,int.class,String.class);click.setAccessible(true);
        when(f.game.legalActions(0)).thenReturn(List.of("kan-open:a,b,c","chi:a,b","chi:a,c","pass"));
        click.invoke(arena,f.owner,f.room,0,"@call:kan");
        verify(f.plugin).apply(eq(f.room),eq(0),eq(new com.google.gson.JsonPrimitive("kan-open:a,b,c")),isNull());
        click.invoke(arena,f.owner,f.room,0,"@call:chi");
        verify(f.plugin.menus).boardChoices(f.owner,f.room,List.of("chi:a,b","chi:a,c"),0);
        clearInvocations(f.plugin);when(f.game.legalActions(0)).thenReturn(List.of("discard:a"));
        click.invoke(arena,f.owner,f.room,0,"@call:kan");verify(f.plugin,never()).apply(any(),anyInt(),any(),any());
        f.table.close();
    }
    @Test void pendingUndoRemovesCallsWithoutRequiringARevisionChange(){
        var f=new HandTableTest.Fixture("mahjong");when(f.game.legalActions(0)).thenReturn(List.of("pon:a,b","pass"));
        Location eye=f.origin.clone().add(-.125,.32,1.8);
        assertEquals("pon",f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        f.room.undo=new RoundActions.Undo(f.owner.getUniqueId(),0,30_000,Set.of());
        assertNull(f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        f.room.undo=null;assertEquals("pon",f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        f.table.close();
    }
}
