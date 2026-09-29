package dev.tabletop3d.rules.mahjong;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SichuanRulesTest {
    @Test void missingSuitAndHonorTilesCannotWinEvenWhenTheShapeIsComplete() {
        var rules=new SichuanRules(Map.of());
        var hand=GuangdongRulesTest.tiles("m1 m2 m3 m4 m5 m6 p2 p3 p4 p7 p8 p9 m8 m8");
        assertEquals(100,rules.score(hand,List.of(),context(2,false)).orElseThrow().payment());
        assertTrue(rules.score(hand,List.of(),context(0,false)).isEmpty());
        assertTrue(rules.score(GuangdongRulesTest.tiles("m1 m2 m3 m4 m5 m6 p2 p3 p4 p7 p8 p9 z1 z1"),List.of(),context(2,false)).isEmpty());
    }
    @Test void sevenPairsRootsAndOneSuitCombineBeforeTheFanCap() {
        var rules=new SichuanRules(Map.of());
        var mixed=GuangdongRulesTest.tiles("m1 m1 m1 m1 m2 m2 m3 m3 p4 p4 p5 p5 p6 p6");
        var score=rules.score(mixed,List.of(),context(2,false)).orElseThrow();assertEquals(3,score.fan());assertEquals(800,score.payment());
        var pure=GuangdongRulesTest.tiles("m1 m1 m1 m1 m2 m2 m3 m3 m4 m4 m5 m5 m6 m6");
        assertEquals(1600,rules.score(pure,List.of(),context(2,false)).orElseThrow().payment());
        assertEquals(1700,rules.score(pure,List.of(),context(2,true)).orElseThrow().payment());
        assertEquals(1600,new SichuanRules(Map.of("self-draw-mode","ADD_FAN")).score(pure,List.of(),context(2,true)).orElseThrow().payment());
    }
    @Test void exhaustiveReadyValueUsesTheLargestLegalWaitAndNoEventBonuses() {
        var rules=new SichuanRules(Map.of());
        var hand=GuangdongRulesTest.tiles("m1 m1 m1 m1 m2 m2 m3 m3 p4 p4 p5 p5 p6");
        assertEquals(800,rules.readyValue(hand,List.of(),2));assertEquals(0,rules.readyValue(hand,List.of(),0));
    }
    private static SichuanRules.Context context(int missing,boolean tsumo){return new SichuanRules.Context(missing,tsumo,false,false,false,false,false,false);}
}
