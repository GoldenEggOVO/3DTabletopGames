package dev.tabletop3d.rules.mahjong;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaiwanRulesTest {
    @Test void sixteenTileHandsRequireFiveMeldsAndClosedSelfDrawStacksThreeTai() {
        var rules=new TaiwanRules(Map.of());
        var tiles=GuangdongRulesTest.tiles("m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 p6 p7 p8 z1 z1");
        var score=rules.score(tiles,List.of(),context(27,true)).orElseThrow();
        assertEquals(4,score.tai());assertTrue(score.patterns().containsAll(List.of("TSUMO","CLOSED_HAND","FULLY_CONCEALED","SINGLE_WAIT")));
        assertEquals(500,rules.payment(score,false,0));assertEquals(1000,rules.payment(score,true,2));
        assertTrue(rules.score(tiles.subList(0,14),List.of(),context(27,true)).isEmpty());
    }
    @Test void openPingHuNeedsRonTwoSidedWaitAndNoFlowersOrHonors() {
        var rules=new TaiwanRules(Map.of());
        var tiles=GuangdongRulesTest.tiles("m2 m3 m4 p2 p3 p4 s6 s7 s8 p6 p7 p8 m6 m6");
        var exposed=List.of(new Meld(Meld.Kind.SEQUENCE,GuangdongRulesTest.tiles("m7 m8 m9"),true,3));
        assertEquals(2,rules.score(tiles,exposed,context(1,false)).orElseThrow().tai());
        assertEquals(List.of("TSUMO"),rules.score(tiles,exposed,context(1,true)).orElseThrow().patterns());
        var flowerContext=new TaiwanRules.Context(1,27,false,false,false,false,false,false,List.of(new Tiles.Tile("flower",35,false)));
        assertFalse(rules.score(tiles,exposed,flowerContext).orElseThrow().patterns().contains("PING_HU"));
    }
    @Test void honorLimitsAreCappedAndMinimumTaiCannotBeBypassedByDealerBonus() {
        var tiles=GuangdongRulesTest.tiles("z1 z1 z1 z2 z2 z2 z3 z3 z3 z4 z4 z4 z5 z5 z5 z6 z6");
        var score=new TaiwanRules(Map.of()).score(tiles,List.of(),context(32,false)).orElseThrow();
        assertEquals(16,score.tai());assertTrue(score.patterns().contains("BIG_FOUR_WINDS"));assertTrue(score.patterns().contains("ALL_HONORS"));
        var plain=GuangdongRulesTest.tiles("m2 m3 m4 p2 p3 p4 s6 s7 s8 p6 p7 p8 m6 m6");
        var exposed=List.of(new Meld(Meld.Kind.SEQUENCE,GuangdongRulesTest.tiles("m7 m8 m9"),true,3));
        assertTrue(new TaiwanRules(Map.of("minimum-tai","3")).score(plain,exposed,context(1,false)).isEmpty());
    }
    private static TaiwanRules.Context context(int type,boolean tsumo){return new TaiwanRules.Context(type,27,tsumo,false,false,false,false,false,List.of());}
}
