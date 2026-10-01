package dev.tabletop3d;

import com.google.gson.JsonPrimitive;
import dev.tabletop3d.rules.HandGame;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HandInteractionTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void hoverRaisesOnlySelectedCardAndKeepsStableHit(){
        var f=new HandTableTest.Fixture("lastcard");int start=f.entities.size();f.table.show(f.owner);
        Entity first=f.entities.get(start);Location before=first.getLocation();
        f.table.hover(f.owner,"a");for(int i=0;i<5;i++)f.table.tick();
        assertEquals(.085,first.getLocation().getY()-before.getY(),1e-6);
        var pose=HandTable.handPose(0,2,0,2,false);
        Location eye=f.origin.clone().add(pose.x(),.12,pose.z()+1);
        assertEquals("a",f.table.hit(f.owner,eye,new Vector(0,0,-1)));
        f.table.hover(f.owner,null);for(int i=0;i<5;i++)f.table.tick();
        assertEquals(before.getY(),first.getLocation().getY(),1e-6);
    }
    @Test void newCardTravelsFromDeckButInitialAndRestoredHandsDoNot(){
        var f=new HandTableTest.Fixture("lastcard");f.table.show(f.owner);int start=f.entities.size();
        when(f.game.hand(0)).thenReturn(List.of(new HandGame.Piece("a","r1"),new HandGame.Piece("b","p1"),new HandGame.Piece("c","b1")));
        when(f.game.handSize(0)).thenReturn(3);f.room.event(0,new JsonPrimitive("draw"));f.room.revision++;f.table.show(f.owner);
        Entity card=f.entities.get(start);assertTrue(card.getLocation().distance(f.origin.clone().add(-.22,.07,0))<.12);
        for(int i=0;i<20;i++)f.table.tick();
        var pose=HandTable.handPose(0,2,2,3,false);
        assertEquals(pose.x(),card.getLocation().getX()-f.origin.getX(),1e-6);
        assertEquals(pose.z(),card.getLocation().getZ()-f.origin.getZ(),1e-6);
        f.table.clear(f.owner);start=f.entities.size();f.table.show(f.owner);
        assertTrue(f.entities.get(start).getLocation().distance(f.origin)>.6);
    }
    @Test void deckHitIsReachLimitedAndRespectsOcclusion(){
        var f=new HandTableTest.Fixture("lastcard");Location eye=f.origin.clone().add(-.22,1,0);
        assertTrue(f.table.deckHit(eye,new Vector(0,-1,0)));
        assertFalse(f.table.deckHit(eye.clone().add(0,6,0),new Vector(0,-1,0)));
        when(f.world.rayTraceBlocks(any(),any(),anyDouble(),any(),eq(true)))
            .thenReturn(new org.bukkit.util.RayTraceResult(eye.clone().add(0,-.1,0).toVector()));
        assertFalse(f.table.deckHit(eye,new Vector(0,-1,0)));
    }
    @Test void visibleDeckIsNotBlockedByUnusedHoverSpaceAboveStartingHand(){
        var f=new HandTableTest.Fixture("lastcard");
        when(f.game.hand(0)).thenReturn(List.of(new HandGame.Piece("a","r1"),new HandGame.Piece("b","r2"),
            new HandGame.Piece("c","r3"),new HandGame.Piece("d","r4"),new HandGame.Piece("e","r5")));
        when(f.game.handSize(0)).thenReturn(5);f.room.revision++;f.table.show(f.owner);
        Location eye=f.origin.clone().add(0,1.62-TableGeometry.SURFACE,2.25);
        Vector direction=f.origin.clone().add(-.22,.064,0).toVector().subtract(eye.toVector()).normalize();
        assertNull(f.table.hit(f.owner,eye,direction),"A visible deck must not be intercepted by empty space above a resting card");
        assertTrue(f.table.deckHit(eye,direction),"The default seat must be able to aim at the deck surface");
    }
    @Test void hoveringAnArrivingCardKeepsTheLiftAfterItsDrawAnimation(){
        var f=new HandTableTest.Fixture("lastcard");f.table.show(f.owner);int start=f.entities.size();
        when(f.game.hand(0)).thenReturn(List.of(new HandGame.Piece("a","r1"),new HandGame.Piece("b","p1"),new HandGame.Piece("c","b1")));
        when(f.game.handSize(0)).thenReturn(3);f.room.event(0,new JsonPrimitive("draw"));f.room.revision++;f.table.show(f.owner);
        Entity card=f.entities.get(start);f.table.hover(f.owner,"c");
        for(int i=0;i<20;i++)f.table.tick();
        assertEquals(f.origin.getY()+.017+HandTable.CARD_LIFT,card.getLocation().getY(),1e-6,"Hover during a draw must not be lost when the card arrives");
        f.table.hover(f.owner,"c");for(int i=0;i<4;i++)f.table.tick();
        assertEquals(f.origin.getY()+.017+HandTable.CARD_LIFT,card.getLocation().getY(),1e-6);
    }
}
