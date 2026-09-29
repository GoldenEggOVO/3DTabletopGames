package dev.tabletop3d.rules.mahjong;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QinhuangdaoRulesTest {
    private final QinhuangdaoRules rules=new QinhuangdaoRules(Map.of());
    @Test void defaultProfileAndWildcardWrapMatchConfiguredDeck() {
        assertEquals(124,rules.tileCount());assertEquals(QinhuangdaoRules.RonMode.NEAREST_ONLY,rules.ronMode());
        assertFalse(rules.allowChi());assertTrue(rules.allowPon());assertFalse(rules.wildcardDiscardable());assertFalse(rules.wildcardCallable());
        assertEquals(0,rules.wildcardType(8));assertEquals(27,rules.wildcardType(30));
        var full=new QinhuangdaoRules(Map.of("tile-set","136"));assertEquals(31,full.wildcardType(33));
        assertEquals(17,new QinhuangdaoRules(Map.of("tile-set","108","wildcard-mode","FIXED","fixed-wildcard","P9")).wildcardType(-1));
        assertThrows(IllegalArgumentException.class,()->new QinhuangdaoRules(Map.of("tile-set","108","wildcard-mode","FIXED","fixed-wildcard","Z1")));
    }
    @Test void closedNoWildcardStandardHandMultipliesWithoutInventingPatterns() {
        var score=rules.score(tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z1 z1"),List.of(),context(27,false)).orElseThrow();
        assertEquals(4,score.multiplier());assertEquals(400,score.payment());
        assertEquals(List.of("PING_HU","CLOSED_HAND","NO_WILDCARD"),score.patterns());
    }
    @Test void luxurySevenPairsCountsNaturalQuadsAndChoosesOnlyTheHighestTier() {
        var opts=new QinhuangdaoRules(Map.of("pattern-multipliers.CLOSED_HAND","1","pattern-multipliers.NO_WILDCARD","1","max-multiplier","128"));
        String[] hands={"m1 m1 m2 m2 p3 p3 p4 p4 s5 s5 s6 s6 z1 z1","m1 m1 m1 m1 p3 p3 p4 p4 s5 s5 s6 s6 z1 z1",
            "m1 m1 m1 m1 p3 p3 p3 p3 s5 s5 s6 s6 z1 z1","m1 m1 m1 m1 p3 p3 p3 p3 s5 s5 s5 s5 z1 z1"};
        String[] patterns={"SEVEN_PAIRS","LUXURY_SEVEN_PAIRS","DOUBLE_LUXURY_SEVEN_PAIRS","TRIPLE_LUXURY_SEVEN_PAIRS"};
        for(int i=0;i<hands.length;i++){var score=opts.score(tiles(hands[i]),List.of(),context(27,false)).orElseThrow();assertEquals(4<<i,score.multiplier());assertEquals(patterns[i],score.patterns().getFirst());}
        var wild=opts.score(tiles("m1 m1 m1 z4 p3 p3 p4 p4 s5 s5 s6 s6 z1 z1"),List.of(),context(27,false)).orElseThrow();
        assertEquals("SEVEN_PAIRS",wild.patterns().getFirst());
    }
    @Test void ambiguousSevenPairsAndStandardShapeUsesMaximumPayment() {
        var hand=tiles("m1 m1 m2 m2 m3 m3 m4 m4 m5 m5 m6 m6 m7 m7");
        var configured=new QinhuangdaoRules(Map.of("pattern-multipliers.PING_HU","20","max-multiplier","100"));
        assertEquals(80,configured.score(hand,List.of(),context(6,false)).orElseThrow().multiplier());
        assertEquals("PING_HU",configured.score(hand,List.of(),context(6,false)).orElseThrow().patterns().getFirst());
    }
    @Test void wildcardPairScoresOnlyWhenTheWinningTileCompletesThePreexistingGoldSingleton() {
        var singleton=tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z4 z1");
        var yes=rules.score(singleton,List.of(),context(27,true)).orElseThrow();assertTrue(yes.patterns().contains("WILDCARD_SINGLE_WAIT"));
        var no=rules.score(singleton,List.of(),context(30,true)).orElseThrow();assertFalse(no.patterns().contains("WILDCARD_SINGLE_WAIT"));assertFalse(no.patterns().contains("WILDCARD_WINNING_TILE"));
        var twoGold=tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z4 z4");
        var both=rules.score(twoGold,List.of(),context(30,true)).orElseThrow();assertTrue(both.patterns().containsAll(List.of("WILDCARD_SINGLE_WAIT","WILDCARD_WINNING_TILE")));
    }
    @Test void catchFiveRequiresNaturalFourFiveSixManContainingTheWinningFive() {
        var caught=rules.score(tiles("m4 m5 m6 p1 p2 p3 p4 p5 p6 s7 s8 s9 z1 z1"),List.of(),context(4,true)).orElseThrow();
        assertTrue(caught.patterns().contains("CATCH_FIVE"));
        assertFalse(rules.score(tiles("m5 m6 m7 p1 p2 p3 p4 p5 p6 s7 s8 s9 z1 z1"),List.of(),context(4,true)).orElseThrow().patterns().contains("CATCH_FIVE"));
        assertFalse(rules.score(tiles("p4 p5 p6 m1 m2 m3 m7 m8 m9 s7 s8 s9 z1 z1"),List.of(),context(13,true)).orElseThrow().patterns().contains("CATCH_FIVE"));
    }
    @Test void onePhysicalWinningFiveCannotCompleteBothThePairAndCatchFiveSequence() {
        var configured=new QinhuangdaoRules(Map.of("pattern-multipliers.CATCH_FIVE","3"));
        var score=configured.score(tiles("m4 m5 m6 m5 z4 p1 p2 p3 p7 p8 p9 s7 s8 s9"),List.of(),context(4,true)).orElseThrow();
        assertEquals(6,score.multiplier());assertTrue(score.patterns().contains("CATCH_FIVE"));
        assertFalse(score.patterns().contains("WILDCARD_SINGLE_WAIT"));
    }
    @Test void pureSelfDrawAndDisabledRobKongCannotBeOverriddenByContext() {
        var hand=tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z1 z1");
        assertTrue(new QinhuangdaoRules(Map.of("ron-mode","TSUMO_ONLY")).score(hand,List.of(),context(27,false)).isEmpty());
        assertTrue(new QinhuangdaoRules(Map.of("allow-rob-added-kan","false")).score(hand,List.of(),new QinhuangdaoRules.Context(30,27,false,false,true,false,false,false,false)).isEmpty());
    }
    @Test void allTripletsAndFullyExposedRequireRealMeldShapes() {
        var melds=List.of(meld("m1 m1 m1",true),meld("p3 p3 p3",true),meld("s5 s5 s5",true),meld("z1 z1 z1",true));
        var score=rules.score(tiles("z2 z2"),melds,context(28,false)).orElseThrow();
        assertEquals(16,score.multiplier());assertTrue(score.patterns().containsAll(List.of("ALL_MELDS_EXPOSED","ALL_TRIPLETS","NO_WILDCARD")));
        var hidden=new ArrayList<>(melds);hidden.set(0,meld("m1 m1 m1 m1",false));
        assertFalse(rules.score(tiles("z2 z2"),hidden,context(28,false)).orElseThrow().patterns().contains("ALL_MELDS_EXPOSED"));
    }
    @Test void heavenlyAndEarthlyUseSpecialBaseAndLuxuryTierWithoutStackingOrdinaryBonuses() {
        var heavenly=new QinhuangdaoRules.Context(30,27,true,false,false,false,true,false,false);
        assertEquals(10,rules.score(tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z1 z1"),List.of(),heavenly).orElseThrow().multiplier());
        assertEquals(20,rules.score(tiles("m1 m1 m1 m1 p3 p3 p4 p4 s5 s5 s6 s6 z1 z1"),List.of(),heavenly).orElseThrow().multiplier());
    }
    @Test void fourGoldKnockdownIsSelfDrawOnlyAndDoesNotNeedARegularShape() {
        var hand=tiles("m1 m4 m7 p1 p4 p7 s1 s4 s7 z1 z4 z4 z4 z4");
        var gold=new QinhuangdaoRules.Context(30,30,true,false,false,false,false,false,true);
        assertEquals(List.of("WILDCARD_KONG"),rules.score(hand,List.of(),gold).orElseThrow().patterns());
        assertEquals(1000,rules.score(hand,List.of(),gold).orElseThrow().payment());
        assertTrue(rules.score(hand,List.of(),new QinhuangdaoRules.Context(30,30,false,false,false,false,false,false,true)).isEmpty());
    }
    @Test void rulesRespectDisabledShapesScoreCapAndPhysicalTileLimits() {
        var hand=tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z1 z1");
        assertTrue(new QinhuangdaoRules(Map.of("standard-win","false")).score(hand,List.of(),context(27,false)).isEmpty());
        assertEquals(3,new QinhuangdaoRules(Map.of("max-multiplier","3")).score(hand,List.of(),context(27,false)).orElseThrow().multiplier());
        var duplicate=new ArrayList<>(hand);duplicate.set(13,hand.get(12));assertTrue(rules.score(duplicate,List.of(),context(27,false)).isEmpty());
        assertTrue(rules.score(tiles("m1 m2 m3 m4 m5 m6 p1 p2 p3 s7 s8 s9 z5 z5"),List.of(),context(31,false)).isEmpty());
    }
    private static QinhuangdaoRules.Context context(int winning,boolean tsumo){return new QinhuangdaoRules.Context(30,winning,tsumo,false,false,false,false,false,false);}
    private static Meld meld(String codes,boolean open){var tiles=tiles(codes);return new Meld(tiles.size()==4?Meld.Kind.QUAD:Meld.Kind.TRIPLET,tiles,open,open?1:-1);}
    private static int sequence;
    private static List<Tiles.Tile> tiles(String codes){List<Tiles.Tile> tiles=new ArrayList<>();for(String code:codes.split(" "))tiles.add(new Tiles.Tile("qh"+(sequence++),Tiles.type(code),false));return tiles;}
}
