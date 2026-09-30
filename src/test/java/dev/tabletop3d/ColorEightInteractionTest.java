package dev.tabletop3d;

import dev.tabletop3d.rules.*;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ColorEightInteractionTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void everyCardInA54CardHandCanBeAimedAtAndOnlyThatCardRises(){
        var f=new HandTableTest.Fixture("lastcard");List<HandGame.Piece> hand=new ArrayList<>();
        for(int i=0;i<54;i++)hand.add(new HandGame.Piece("c"+i,"r1"));
        when(f.game.hand(0)).thenReturn(hand);when(f.game.handSize(0)).thenReturn(54);int start=f.entities.size();f.table.show(f.owner);
        for(int i=0;i<54;i++){
            var pose=HandTable.handPose(0,2,i,54,false);
            Location eye=f.origin.clone().add(pose.x()-.065,.13,2.9);
            assertEquals("c"+i,f.table.hit(f.owner,eye,new Vector(0,0,-1)),"Dense hand index "+i);
        }
        List<Entity> cards=f.entities.subList(start,f.entities.size()).stream().filter(e->Math.abs(e.getLocation().getY()-f.origin.getY()-.017-HandTable.handPose(0,2,27,54,false).lift())<.000001).toList();
        f.table.hover(f.owner,"c27");for(int i=0;i<5;i++)f.table.tick();
        assertTrue(cards.stream().anyMatch(e->e.getLocation().getY()>f.origin.getY()+.27));
        var first=HandTable.handPose(0,2,0,54,false);assertEquals(f.origin.getY()+.017+first.lift(),f.entities.get(start).getLocation().getY(),1e-6);
    }
    @Test void denseHandsRemainAimableAtEverySeatOfTheFivePlayerTable(){
        for(int seat=0;seat<5;seat++){
            var f=new HandTableTest.Fixture("lastcard");when(f.game.playerCount()).thenReturn(5);
            f.room.seats.clear();for(int i=0;i<5;i++)f.room.seats.add(new Room.Seat(i==seat?f.owner.getUniqueId():UUID.randomUUID(),"Seat "+i,i!=seat));
            List<HandGame.Piece> hand=new ArrayList<>();for(int i=0;i<54;i++)hand.add(new HandGame.Piece("c"+i,"r1"));
            when(f.game.hand(seat)).thenReturn(hand);when(f.game.handSize(seat)).thenReturn(54);f.table.show(f.owner);
            double angle=2*Math.PI*seat/5,sin=Math.sin(angle),cos=Math.cos(angle);
            for(int i=0;i<54;i++){
                var pose=HandTable.handPose(seat,5,i,54,false);
                Location eye=f.origin.clone().add(pose.x()-.075*cos+2*sin,.13,pose.z()+.075*sin+2*cos);
                assertEquals("c"+i,f.table.hit(f.owner,eye,new Vector(-sin,0,-cos)),"Seat "+seat+" card "+i);
            }
            f.table.close();
        }
    }
    @Test void wildSelectionHasFourOwnerOnlyButtonsAndPlaysTheChosenColoredEight()throws Exception{
        var f=new HandTableTest.Fixture("lastcard");var game=new LastCardGame(2,10);
        TabletopTest.set(game,"hands",new ArrayList<>(List.of(new ArrayList<>(List.of(48,24,25)),new ArrayList<>(List.of(12,13)))));
        f.room.board=game;f.room.revision++;f.table.sync();f.table.show(f.owner);
        assertEquals("choose:48",f.table.cardAction(f.owner,"48"));game.apply(0,"choose:48");f.room.revision++;
        int start=f.entities.size();f.table.show(f.owner);var pose=HandTable.handPose(0,2,0,3,false);
        for(int i=0;i<4;i++){
            double tangent=(i-1.5)*.10,angle=-Math.toRadians(pose.yaw());
            Location eye=f.origin.clone().add(pose.x()+tangent*Math.cos(angle),.017+pose.lift()+HandTable.CARD_LIFT+.32,pose.z()-tangent*Math.sin(angle)+.6);
            assertEquals("card:play:48:"+"rbyp".charAt(i),f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        }
        List<Entity> buttons=List.copyOf(f.entities.subList(start,f.entities.size()));assertEquals(8,buttons.size());
        for(Entity button:buttons){verify(f.owner).showEntity(f.plugin,button);verify(f.spectator,never()).showEntity(f.plugin,button);}
        game.apply(0,"play:48:p");f.room.revision++;f.table.sync();f.table.show(f.owner);
        assertEquals("p8",game.cells().get(1).piece());buttons.forEach(b->assertFalse(b.isValid()));
    }
    @Test void humanTurnsStayThirtySecondsOnlineOrOffline(){
        Tabletop3D plugin=mock(Tabletop3D.class,CALLS_REAL_METHODS);
        Room room=new Room(UUID.randomUUID(),"lastcard",2,10,0);UUID human=UUID.randomUUID();
        room.join(human,"Human");room.fillBots();room.board=new LastCardGame(2,10);
        assertEquals(30000,plugin.turnWaitMillis(room));room.offline.put(human,0L);
        assertEquals(30000,plugin.turnWaitMillis(room));
    }
    @Test void circularFurnitureFitsTheRadiusAndLeavesTheSquareCornersEmpty(){
        for(var part:RoundCardTable.parts()){
            assertTrue(part.w()>0&&part.h()>0&&part.d()>0);
            assertTrue(Math.hypot(Math.abs(part.x())+part.w()/2,Math.abs(part.z())+part.d()/2)<=RoundCardTable.RADIUS+1e-7);
            assertFalse(Math.abs(part.x()-1.4)<part.w()/2&&Math.abs(part.z()-1.4)<part.d()/2);
        }
        assertTrue(Tabletop3D.capacityValid("lastcard",5));assertFalse(Tabletop3D.capacityValid("lastcard",6));
    }
}
