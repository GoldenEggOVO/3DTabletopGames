package dev.tabletop3d;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TurnRingTest {
    private static final NamespacedKey TAG=new NamespacedKey("serverboards","board-cell");

    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}

    @Test void nativeSegmentsLeaveSpaceForBothCentralPiles(){
        Fixture f=new Fixture();
        assertTrue(f.parts.size()==24);
        for(Entity entity:f.parts){
            assertInstanceOf(BlockDisplay.class,entity);
            Location at=entity.getLocation();
            double radius=Math.hypot(at.getX()-f.base.origin.getX(),at.getZ()-f.base.origin.getZ());
            assertTrue(radius>.34&&radius<.46,"The ring stays outside the draw and discard piles");
            verify(entity).setPersistent(false);
            verify(entity.getPersistentDataContainer()).set(TAG,PersistentDataType.STRING,f.base.room.id+"|@board");
            verify((Display)entity).setBrightness(new Display.Brightness(15,15));
            verify((Display)entity).setTeleportDuration(2);
            verify((Display)entity).setInterpolationDuration(2);
        }
    }

    @Test void followsSouthEastNorthWestAndReversesWithoutReplacingEntities(){
        Fixture f=new Fixture();
        List<Location> before=positions(f.parts);
        for(int i=0;i<20;i++)f.ring.tick();
        List<Location> clockwise=positions(f.parts);
        assertMovesInSeatOrder(before,clockwise,f.base.origin,1);
        f.ring.direction(-1);
        before=positions(f.parts);
        assertNotEquals(clockwise,before,"The arrowheads turn around with the direction");
        for(int i=0;i<20;i++)f.ring.tick();
        assertMovesInSeatOrder(before,positions(f.parts),f.base.origin,-1);
        assertEquals(f.entityCount,f.base.entities.size(),"Direction changes reuse every segment");
        for(Entity entity:f.parts)verify(entity,never()).remove();
    }

    @Test void pausesOutsidePlayingAndWhileUndoIsPending(){
        Fixture f=new Fixture();
        for(Room.Phase phase:Room.Phase.values()){
            if(phase==Room.Phase.PLAYING)continue;
            f.base.room.phase=phase;
            List<Location> before=positions(f.parts);
            f.ring.tick();
            assertEquals(before,positions(f.parts),"The ring must stay still in "+phase);
        }
        f.base.room.phase=Room.Phase.PLAYING;
        f.base.room.undo=new RoundActions.Undo(UUID.randomUUID(),0,30_000,Set.of());
        List<Location> before=positions(f.parts);
        f.ring.tick();
        assertEquals(before,positions(f.parts));
        f.base.room.undo=null;
        f.ring.tick();
        assertMovesInSeatOrder(before,positions(f.parts),f.base.origin,1);
    }

    @Test void closeRemovesAllSegmentsAndStopsFutureUpdates(){
        Fixture f=new Fixture();
        f.ring.tick();
        List<Location> before=positions(f.parts);
        f.ring.close();
        f.ring.close();
        f.ring.direction(-1);
        f.ring.tick();
        assertEquals(before,positions(f.parts));
        assertEquals(f.entityCount,f.base.entities.size());
        for(Entity entity:f.parts){
            assertFalse(entity.isValid());
            verify(entity,times(1)).remove();
        }
    }

    private static List<Location> positions(List<Entity> parts){
        return parts.stream().map(Entity::getLocation).toList();
    }
    private static void assertMovesInSeatOrder(List<Location> before,List<Location> after,Location origin,int direction){
        for(int i=0;i<before.size();i++){
            double x=before.get(i).getX()-origin.getX(),z=before.get(i).getZ()-origin.getZ();
            double nextX=after.get(i).getX()-origin.getX(),nextZ=after.get(i).getZ()-origin.getZ();
            assertTrue(direction*(z*nextX-x*nextZ)>0,"Positive direction must follow south -> east -> north -> west");
            assertEquals(x*x+z*z,nextX*nextX+nextZ*nextZ,1e-8,"Rotation keeps the same radius");
        }
    }
    private static final class Fixture {
        final HandTableTest.Fixture base=new HandTableTest.Fixture("lastcard");
        final TurnRing ring;
        final List<Entity> parts;
        final int entityCount;
        Fixture(){
            int start=base.entities.size();
            ring=new TurnRing(base.plugin,base.room,base.origin,TAG);
            parts=List.copyOf(base.entities.subList(start,base.entities.size()));
            entityCount=base.entities.size();
        }
    }
}
