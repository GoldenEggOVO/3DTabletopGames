package dev.tabletop3d;

import dev.tabletop3d.rules.GameFactory;
import com.google.gson.JsonPrimitive;
import java.util.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LudoPresentationTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void close(){MockBukkit.unmock();}
    @Test void catalogReplacesNewFlightWithLudoButLegacyNamesRemainReadable(){
        assertTrue(Tabletop3D.NAMES.containsKey("ludo"));assertFalse(Tabletop3D.NAMES.containsKey("aeroplane"));
        assertEquals(4,Tabletop3D.defaultCapacity("ludo"));
        assertTrue(dev.tabletop3d.ui.MessageText.plain(RoomText.game("aeroplane")).contains("Legacy"));
    }
    @Test void ludoCoordinatesHaveUniqueClickTargetsAndAnExternalDie(){
        var board=GameFactory.create("ludo",4,0);var geometry=new TableGeometry("ludo",board.cells());
        for(var cell:board.cells())assertEquals(cell.id(),geometry.hit(geometry.x(cell),geometry.z(cell)));
        assertEquals("@roll",geometry.hit(1.30,0));assertEquals(256,TableArt.draw(geometry).getWidth());
    }
    @Test void hoveringALudoPawnShowsPrivateDestinationsAndKeepsPieceEntities()throws Exception{
        var f=new TableViewTest.Fixture("ludo");Map<?,?> before=new HashMap<>((Map<?,?>)TableViewTest.field(f.view,"tokens"));
        f.move("roll");for(int i=0;i<12;i++)f.view.tick();int count=f.entities.size();
        String source=f.room.board.cells().stream().filter(c->!GameWorld.sourceActions(f.room.board,0,c.id()).isEmpty()).findFirst().orElseThrow().id();
        int history=f.room.history.size();f.view.cursor(f.player,null,source);assertTrue(f.entities.size()>count);
        List<Entity> hints=List.copyOf(f.entities.subList(count,f.entities.size()));
        for(Entity hint:hints){verify((Display)hint).setVisibleByDefault(false);verify(f.player).showEntity(f.plugin,hint);}
        f.view.cursor(f.player,null,source);assertEquals(count+hints.size(),f.entities.size());assertEquals(history,f.room.history.size());
        f.move(GameWorld.sourceActions(f.room.board,0,source).getFirst());for(int i=0;i<8;i++)f.view.tick();
        var after=(Map<?,?>)TableViewTest.field(f.view,"tokens");for(var entry:before.entrySet())assertSame(entry.getValue(),after.get(entry.getKey()));
        for(Entity hint:hints)verify(hint,atLeastOnce()).remove();f.view.close();
    }
    @Test void clickingAPawnCommitsItsDiceMoveWithoutAnExtraDestinationClick()throws Exception{
        var f=new TableViewTest.Fixture("ludo");f.move("roll");
        var arena=mock(GameWorld.class,CALLS_REAL_METHODS);TabletopTest.set(arena,"plugin",f.plugin);TabletopTest.set(arena,"selections",new HashMap<>());
        when(f.plugin.allowed(f.player)).thenReturn(true);
        String source=f.room.board.cells().stream().filter(c->!GameWorld.sourceActions(f.room.board,0,c.id()).isEmpty()).findFirst().orElseThrow().id();
        String action=GameWorld.sourceActions(f.room.board,0,source).getFirst();
        var click=GameWorld.class.getDeclaredMethod("pickCell",Player.class,Room.class,int.class,String.class);click.setAccessible(true);click.invoke(arena,f.player,f.room,0,source);
        verify(f.plugin).apply(eq(f.room),eq(0),eq(new JsonPrimitive(action)),isNull());
    }
    @Test void stackedPawnsExplainTheChoiceAndKeepAllOptions()throws Exception{
        var f=new TableViewTest.Fixture("ludo");
        var field=f.room.board.getClass().getDeclaredField("progress");field.setAccessible(true);((int[])field.get(f.room.board))[1]=0;
        f.move("roll");for(int i=0;i<12;i++)f.view.tick();f.view.cursor(f.player,null,"sk0");
        var message=org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);verify(f.player).sendActionBar(message.capture());
        assertTrue(dev.tabletop3d.ui.MessageText.plain(message.getValue()).contains("choose a pawn"));
        var arena=mock(GameWorld.class,CALLS_REAL_METHODS);TabletopTest.set(arena,"plugin",f.plugin);TabletopTest.set(arena,"selections",new HashMap<>());
        TabletopTest.set(arena,"views",new HashMap<>(Map.of(f.room.id,f.view)));
        f.plugin.menus=mock(GameMenus.class);var choices=GameWorld.sourceActions(f.room.board,0,"sk0");assertEquals(2,choices.size());
        var click=GameWorld.class.getDeclaredMethod("pickCell",Player.class,Room.class,int.class,String.class);click.setAccessible(true);click.invoke(arena,f.player,f.room,0,"sk0");
        verify(f.plugin.menus).boardChoices(f.player,f.room,choices,0);verify(f.plugin,never()).apply(any(),anyInt(),any(),any());
    }
}
