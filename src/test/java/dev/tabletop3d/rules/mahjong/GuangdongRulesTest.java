package dev.tabletop3d.rules.mahjong;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuangdongRulesTest {
    @Test void threeAllowedShapesAreEvaluatedSeparatelyAndConfigCanDisableEach() {
        var rules=new GuangdongRules(Map.of());
        assertEquals("STANDARD",rules.win(tiles("m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z1"),List.of()));
        var pairs=tiles("m1 m1 m1 m1 m2 m2 p3 p3 p4 p4 s5 s5 z1 z1");
        assertEquals("SEVEN_PAIRS",rules.win(pairs,List.of()));
        assertNull(new GuangdongRules(Map.of("seven-pairs","false")).win(pairs,List.of()));
        assertEquals("THIRTEEN_ORPHANS",rules.win(tiles("m1 m9 p1 p9 s1 s9 z1 z2 z3 z4 z5 z6 z7 z7"),List.of()));
        assertNull(new GuangdongRules(Map.of("standard-win","false")).win(tiles("m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z1"),List.of()));
    }
    @Test void exposedMeldsReduceRequiredHandSizeButCannotSupplySevenPairs() {
        var rules=new GuangdongRules(Map.of());
        var meld=new Meld(Meld.Kind.TRIPLET,tiles("z1 z1 z1"),true,2);
        assertEquals("STANDARD",rules.win(tiles("m1 m2 m3 p4 p5 p6 s7 s8 s9 z2 z2"),List.of(meld)));
        assertNull(rules.win(tiles("m1 m1 m2 m2 p3 p3 p4 p4 s5 s5 z1"),List.of(meld)));
        assertNull(rules.win(tiles("m1 m2 m3 p4 p5 p6 s7 s8 s9 z1 z1"),List.of(meld)),"Five physical copies cannot form a legal hand");
    }
    static List<Tiles.Tile> tiles(String codes) {
        List<Tiles.Tile> result=new ArrayList<>();for(String code:codes.split(" "))if(!code.isEmpty())result.add(new Tiles.Tile("fixture"+result.size(),Tiles.type(code),false));return result;
    }
}
