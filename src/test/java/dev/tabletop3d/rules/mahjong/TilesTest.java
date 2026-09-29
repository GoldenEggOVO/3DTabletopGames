package dev.tabletop3d.rules.mahjong;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TilesTest {
    @Test void everyWallContainsUniquePhysicalTilesAndFourCopiesOfEachPlayingFace() {
        for(int count:new int[]{108,124,136,144}) {
            List<Tiles.Tile> tiles=Tiles.set(count,3);
            assertEquals(count,tiles.size());assertEquals(count,tiles.stream().map(Tiles.Tile::id).distinct().count());
            int[] copies=new int[42];tiles.forEach(tile->copies[tile.type()]++);
            for(int type=0;type<count/4&&type<34;type++)assertEquals(4,copies[type]);
            for(int type=34;type<42;type++)assertEquals(count==144?1:0,copies[type]);
            assertEquals(3,tiles.stream().filter(Tiles.Tile::red).count());
        }
    }
    @Test void indicatorsWrapWithinTheirOwnSuitWindOrDragonFamily() {
        assertEquals(0,Tiles.next(8));assertEquals(9,Tiles.next(17));assertEquals(18,Tiles.next(26));
        assertEquals(27,Tiles.next(30));assertEquals(31,Tiles.next(33));assertEquals(32,Tiles.next(31));
        assertEquals(4,Tiles.type("m0"));assertEquals(31,Tiles.type("z5"));assertEquals(41,Tiles.type("f8"));
    }
    @Test void seededWallsReplayAndDeadWallReplacementsPreserveThePhysicalInventory() {
        Wall a=new Wall(Tiles.set(136,3),91,true),b=new Wall(Tiles.set(136,3),91,true);
        assertEquals(122,a.remaining());assertEquals(a.indicators(),b.indicators());
        Set<String> seen=new HashSet<>();
        for(int i=0;i<4;i++) {
            Tiles.Tile drawn=a.replacement();assertEquals(drawn,b.replacement());assertTrue(seen.add(drawn.id()));
            assertEquals(121-i,a.remaining());
        }
        assertNull(a.replacement());assertEquals(5,a.indicators().size());
        while(a.remaining()>0){Tiles.Tile tile=a.draw();assertEquals(tile,b.draw());assertTrue(seen.add(tile.id()));}
        assertNull(a.draw());assertEquals(122,seen.size());
        assertTrue(a.indicators().stream().noneMatch(tile->seen.contains(tile.id())));
        assertTrue(a.uraIndicators().stream().noneMatch(tile->seen.contains(tile.id())));
    }
}
