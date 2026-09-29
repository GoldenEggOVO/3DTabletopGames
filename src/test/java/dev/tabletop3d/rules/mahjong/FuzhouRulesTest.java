package dev.tabletop3d.rules.mahjong;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FuzhouRulesTest {
    private final FuzhouRules rules=new FuzhouRules(Map.of());
    @Test void ordinaryFiveMeldHandUsesFlowersGoldKongsAndDealerStreak() {
        var melds=List.of(meld("p1 p1 p1 p1",true),meld("s1 s1 s1 s1",false));
        var hand=tiles("m1 m2 m3 m4 m5 m6 p7 p8 p9 z1 z1");
        var context=new FuzhouRules.Context(33,27,false,false,2,tiles("f1 f2"));
        var score=rules.score(hand,melds,context).orElseThrow();
        assertEquals(12,score.flowers());assertEquals(1,score.multiplier());assertEquals(1200,score.payment());
        assertEquals(2400,rules.score(hand,melds,new FuzhouRules.Context(33,27,true,false,2,tiles("f1 f2"))).orElseThrow().payment());
    }
    @Test void sixteenTileFormatRejectsFourMeldAndSevenPairHands() {
        assertTrue(rules.score(tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z1 z1"),List.of(),context(27,false)).isEmpty());
        assertTrue(rules.score(tiles("m1 m1 m2 m2 p3 p3 p4 p4 s5 s5 s6 s6 z1 z1"),List.of(),context(27,false)).isEmpty());
    }
    @Test void threeGoldKnockdownAcceptsExactlyThreeGoldWithoutARegularShape() {
        var sixteen=tiles("m1 m4 m7 p1 p4 p7 s1 s4 s7 z1 z2 z3 z4 z7 z7 z7");
        var score=rules.score(sixteen,List.of(),context(-1,true)).orElseThrow();
        assertEquals(List.of("SAN_JIN_DAO"),score.patterns());assertEquals(24000,score.payment());
        assertTrue(rules.score(sixteen,List.of(),context(0,false)).isEmpty());
        var four=new ArrayList<>(sixteen);four.add(new Tiles.Tile("fourth-gold",33,false));
        assertTrue(rules.score(four,List.of(),context(33,true)).isEmpty());
        assertTrue(new FuzhouRules(Map.of("fuzhou.allow-san-jin-dao","false")).score(sixteen,List.of(),context(-1,true)).isEmpty());
    }
    @Test void goldenPairMustUseTwoGoldInACompleteFiveMeldShape() {
        var hand=tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z1 z1 z1 z7 z7");
        var score=rules.score(hand,List.of(),context(33,true)).orElseThrow();
        assertEquals(List.of("JIN_QUE"),score.patterns());assertEquals(30000,score.payment());
        var disabled=new FuzhouRules(Map.of("allow-jin-que","false"));
        assertEquals(2,disabled.score(hand,List.of(),context(33,true)).orElseThrow().multiplier());
    }
    @Test void robGoldRequiresGoldWinningTileClosedOpeningHandAndARealShape() {
        var hand=tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z1 z1 z1 z2 z7");
        var context=new FuzhouRules.Context(33,33,false,true,0,List.of());
        assertEquals(List.of("QIANG_JIN"),rules.score(hand,List.of(),context).orElseThrow().patterns());
        assertTrue(rules.score(hand,List.of(),new FuzhouRules.Context(33,28,false,true,0,List.of())).isEmpty());
        assertTrue(new FuzhouRules(Map.of("allow-qiang-jin","false")).score(hand,List.of(),context).isEmpty());
        assertTrue(rules.score(tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z1 z2 z3 z4 z7"),List.of(),context).isEmpty());
    }
    @Test void namespacedOptionsOverrideUnqualifiedOptions() {
        var configured=new FuzhouRules(Map.of("point-per-flower","50","fuzhou.point-per-flower","125","base-flowers","4"));
        var score=configured.score(tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z1 z1 z1 z2 z2"),List.of(),context(28,false)).orElseThrow();
        assertEquals(500,score.payment());
        assertThrows(IllegalArgumentException.class,()->new FuzhouRules(Map.of("allow-qiang-jin","yes")));
    }
    @Test void duplicatePhysicalTilesAndFiveCopiesCannotBeScored() {
        var hand=tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z1 z1 z1 z2 z2");
        var duplicate=new ArrayList<>(hand);duplicate.set(16,hand.get(15));
        assertTrue(rules.score(duplicate,List.of(),context(28,false)).isEmpty());
        assertTrue(rules.score(tiles("m1 m1 m1 m1 m1 m2 m3 p1 p2 p3 s7 s8 s9 z1 z1 z1 z2"),List.of(),context(28,false)).isEmpty());
    }
    private static FuzhouRules.Context context(int winning,boolean tsumo){return new FuzhouRules.Context(33,winning,tsumo,false,0,List.of());}
    private static Meld meld(String codes,boolean open){return new Meld(Meld.Kind.QUAD,tiles(codes),open,open?1:-1);}
    private static int sequence;
    static List<Tiles.Tile> tiles(String codes){List<Tiles.Tile> tiles=new ArrayList<>();for(String code:codes.split(" "))tiles.add(new Tiles.Tile("fz"+(sequence++),Tiles.type(code),false));return tiles;}
}
