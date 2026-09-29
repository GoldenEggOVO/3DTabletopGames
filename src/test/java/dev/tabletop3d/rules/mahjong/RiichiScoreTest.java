package dev.tabletop3d.rules.mahjong;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiichiScoreTest {
    private int serial;
    List<Tiles.Tile> tiles(String faces){List<Tiles.Tile> out=new ArrayList<>();for(String face:faces.split(" "))if(!face.isEmpty())out.add(new Tiles.Tile("score"+serial++,Tiles.type(face),face.endsWith("0")));return out;}
    Meld meld(Meld.Kind kind,boolean open,String faces){return new Meld(kind,tiles(faces),open,open?1:-1);}
    RiichiScore.Flags flags(String... names){Set<String> f=Set.of(names);return new RiichiScore.Flags(f.contains("tsumo"),f.contains("riichi"),f.contains("double"),f.contains("ippatsu"),f.contains("rinshan"),f.contains("chankan"),f.contains("haitei"),f.contains("houtei"),f.contains("tenhou"),f.contains("chiihou"));}
    Optional<RiichiScore.Score> score(String faces,String win,List<Meld> melds,RiichiScore.Flags flags,RiichiScore.Options options,String dora,String ura,int seat,int round){
        List<Tiles.Tile> hand=tiles(faces);Tiles.Tile winning=hand.stream().filter(t->t.type()==Tiles.type(win)).findFirst().orElseThrow();
        return RiichiScore.score(hand,melds,new RiichiScore.Context(winning,seat,round,flags,tiles(dora),tiles(ura),options));
    }
    RiichiScore.Score score(String faces,String win,String... flags){return score(faces,win,List.of(),flags(flags),RiichiScore.Options.DEFAULT,"","",28,27).orElseThrow();}
    RiichiScore.Score opened(String faces,String win,Meld... melds){return score(faces,win,List.of(melds),flags(),RiichiScore.Options.DEFAULT,"","",28,27).orElseThrow();}
    int han(RiichiScore.Score score,String name){return score.yaku().stream().filter(y->y.name().equals(name)).mapToInt(RiichiScore.Yaku::han).sum();}
    private static final String PINFU="m1 m2 m3 m4 m5 m6 p2 p3 p4 s6 s7 s8 p7 p7";

    @Test void closedPinfuRonAndTsumoHaveDifferentFuAndPayments(){
        assertTrue(score(PINFU,"m1",List.of(),flags(),RiichiScore.Options.DEFAULT,"","",28,27).isPresent());
        var ron=score(PINFU,"m1");assertEquals(List.of("PINFU"),ron.yakuNames());assertEquals(1,ron.han());assertEquals(30,ron.fu());
        assertEquals(240,ron.basePoints());assertEquals(1000,ron.ronPayment(false));assertEquals(1500,ron.ronPayment(true));
        var tsumo=score(PINFU,"m1","tsumo");assertEquals(2,tsumo.han());assertEquals(20,tsumo.fu());assertEquals(700,tsumo.tsumoDealerPayment());assertEquals(400,tsumo.tsumoChildPayment(false));assertEquals(700,tsumo.tsumoChildPayment(true));
    }
    @Test void edgeClosedAndPairWaitsDoNotScorePinfu(){
        for(String winning:new String[]{"m3","p3","p7"}){
            var value=score(PINFU,winning,"riichi");assertFalse(value.yakuNames().contains("PINFU"),winning);assertEquals(40,value.fu(),winning);
        }
        assertEquals(30,score(PINFU,"m1","riichi").fu());assertEquals(30,score(PINFU,"m6","riichi").fu());
        String highEdge=PINFU.replace("s6 s7 s8","s7 s8 s9");
        assertFalse(score(highEdge,"s7","riichi").yakuNames().contains("PINFU"));assertEquals(40,score(highEdge,"s7","riichi").fu());
        assertTrue(score(highEdge,"s9","riichi").yakuNames().contains("PINFU"));assertEquals(30,score(highEdge,"s9","riichi").fu());
    }
    @Test void recognizesOrdinaryStructuralYaku(){
        String[][] cases={
            {"TANYAO","m2 m3 m4 p3 p4 p5 s6 s7 s8 m6 m6 m6 p2 p2","m4"},
            {"IIPEIKOU","m1 m2 m3 m1 m2 m3 p4 p5 p6 s7 s8 s9 z2 z2","p4"},
            {"RYANPEIKOU","m1 m2 m3 m1 m2 m3 p4 p5 p6 p4 p5 p6 z2 z2","p4"},
            {"SANSHOKU_DOUJUN","m2 m3 m4 p2 p3 p4 s2 s3 s4 m6 m7 m8 z2 z2","m8"},
            {"ITTSUU","m1 m2 m3 m4 m5 m6 m7 m8 m9 p2 p3 p4 z2 z2","p2"},
            {"CHANTA","m1 m2 m3 p7 p8 p9 s1 s1 s1 z5 z5 z5 z2 z2","m1"},
            {"JUNCHAN","m1 m2 m3 p7 p8 p9 s1 s1 s1 m9 m9 m9 p1 p1","m1"},
            {"HONROUTOU","m1 m1 m9 m9 p1 p1 p9 p9 s1 s1 z1 z1 z2 z2","z2"},
            {"CHIITOITSU","m1 m1 m3 m3 p4 p4 p7 p7 s2 s2 s8 s8 z1 z1","z1"},
            {"TOITOI","m2 m2 m2 p3 p3 p3 s4 s4 s4 z1 z1 z1 p5 p5","p3"},
            {"SANANKOU","m2 m2 m2 p3 p3 p3 s4 s4 s4 z1 z1 z1 p5 p5","p3"},
            {"SANSHOKU_DOUKOU","m2 m2 m2 p2 p2 p2 s2 s2 s2 m6 m7 m8 z2 z2","m8"},
            {"SHOUSANGEN","z5 z5 z5 z6 z6 z6 m1 m2 m3 p4 p5 p6 z7 z7","m1"},
            {"HONITSU","m1 m2 m3 m4 m5 m6 m7 m7 m7 z1 z1 z1 z2 z2","m1"},
            {"CHINITSU","m1 m2 m3 m3 m4 m5 m6 m7 m8 m9 m9 m9 m2 m2","m1"}
        };
        for(String[] fixture:cases){
            assertTrue(score(fixture[1],fixture[2]).yakuNames().contains(fixture[0]),fixture[0]);
        }
    }
    @Test void threeColoredSequencesAndTripletsAreDistinct(){
        var sequences=score("m2 m3 m4 p2 p3 p4 s2 s3 s4 m6 m7 m8 z2 z2","m8");
        assertTrue(sequences.yakuNames().contains("SANSHOKU_DOUJUN"));assertFalse(sequences.yakuNames().contains("SANSHOKU_DOUKOU"));
        var triplets=score("m2 m2 m2 p2 p2 p2 s2 s2 s2 m6 m7 m8 z2 z2","m8");
        assertTrue(triplets.yakuNames().contains("SANSHOKU_DOUKOU"));assertFalse(triplets.yakuNames().contains("SANSHOKU_DOUJUN"));
    }
    @Test void openHandsLoseClosedOnlyYakuAndReduceSelectedPatterns(){
        var open=opened("p2 p3 p4 s2 s3 s4 m6 m7 m8 z2 z2","m8",meld(Meld.Kind.SEQUENCE,true,"m2 m3 m4"));
        assertEquals(1,han(open,"SANSHOKU_DOUJUN"));assertFalse(open.yakuNames().contains("PINFU"));
        var straight=opened("m4 m5 m6 m7 m8 m9 p2 p3 p4 z2 z2","p2",meld(Meld.Kind.SEQUENCE,true,"m1 m2 m3"));assertEquals(1,han(straight,"ITTSUU"));
        var chanta=opened("p7 p8 p9 s1 s1 s1 z5 z5 z5 z2 z2","p7",meld(Meld.Kind.SEQUENCE,true,"m1 m2 m3"));assertEquals(1,han(chanta,"CHANTA"));
        var junchan=opened("p7 p8 p9 s1 s1 s1 m9 m9 m9 p1 p1","p7",meld(Meld.Kind.SEQUENCE,true,"m1 m2 m3"));assertEquals(2,han(junchan,"JUNCHAN"));
        var honitsu=opened("m4 m5 m6 m7 m7 m7 z1 z1 z1 z2 z2","m4",meld(Meld.Kind.SEQUENCE,true,"m1 m2 m3"));assertEquals(2,han(honitsu,"HONITSU"));
        var chinitsu=opened("m3 m4 m5 m6 m7 m8 m9 m9 m9 m2 m2","m8",meld(Meld.Kind.SEQUENCE,true,"m1 m2 m3"));assertEquals(5,han(chinitsu,"CHINITSU"));
    }
    @Test void openTanyaoIsAnExplicitOption(){
        var meld=meld(Meld.Kind.SEQUENCE,true,"m2 m3 m4");String hand="p3 p4 p5 s6 s7 s8 m6 m6 m6 p2 p2";
        assertTrue(score(hand,"p3",List.of(meld),flags(),RiichiScore.Options.DEFAULT,"","",28,27).isPresent());
        assertTrue(score(hand,"p3",List.of(meld),flags(),new RiichiScore.Options(false,false,true,false),"","",28,27).isEmpty());
    }
    @Test void onlyTheTripletCompletedByRonBecomesOpenForFuAndConcealedTripletCount(){
        var closed=score("m2 m2 m2 p3 p3 p3 s4 s4 s4 z1 z1 z1 p5 p5","p3");
        assertEquals(50,closed.fu());assertEquals(2,han(closed,"SANANKOU"));assertEquals(0,closed.yakuman());
        Meld exposed=meld(Meld.Kind.TRIPLET,true,"z1 z1 z1");String hand="m2 m2 m2 p3 p3 p3 s4 s4 s4 p5 p5";
        var ron=score(hand,"p3",List.of(exposed),flags(),RiichiScore.Options.DEFAULT,"","",28,27).orElseThrow();
        var tsumo=score(hand,"p3",List.of(exposed),flags("tsumo"),RiichiScore.Options.DEFAULT,"","",28,27).orElseThrow();
        assertFalse(ron.yakuNames().contains("SANANKOU"));assertEquals(40,ron.fu());assertEquals(2,han(tsumo,"SANANKOU"));
    }
    @Test void threeQuadsScoreFuAccordingToTheirActualOpenStatus(){
        for(boolean open:new boolean[]{false,true}){
            List<Meld> melds=List.of(meld(Meld.Kind.QUAD,open,"m5 m5 m5 m5"),meld(Meld.Kind.QUAD,open,"p5 p5 p5 p5"),meld(Meld.Kind.QUAD,open,"s5 s5 s5 s5"));
            var value=score("m1 m2 m3 z2 z2","m1",melds,flags(),RiichiScore.Options.DEFAULT,"","",28,27).orElseThrow();
            assertEquals(2,han(value,"SANKANTSU"));assertEquals(open?50:80,value.fu());
        }
    }
    @Test void outsidePatternsDoNotOverlapEachOtherOrAllTerminalsAndHonors(){
        var junchan=score("m1 m2 m3 p7 p8 p9 s1 s1 s1 m9 m9 m9 p1 p1","m1");assertFalse(junchan.yakuNames().contains("CHANTA"));
        var honroutou=score("m1 m1 m9 m9 p1 p1 p9 p9 s1 s1 z1 z1 z2 z2","z2");assertFalse(honroutou.yakuNames().contains("CHANTA"));assertFalse(honroutou.yakuNames().contains("JUNCHAN"));
    }
    @Test void sevenPairsHasExactly25FuAndIdenticalQuadsAreNotTwoPairs(){
        assertEquals(25,score("m1 m1 m3 m3 p4 p4 p7 p7 s2 s2 s8 s8 z1 z1","z1","tsumo").fu());
        assertTrue(score("m1 m1 m1 m1 m3 m3 p4 p4 p7 p7 s2 s2 z1 z1","z1",List.of(),flags("riichi"),RiichiScore.Options.DEFAULT,"","",28,27).isEmpty());
    }
    @Test void alternativeDecompositionsChooseTheMostValuableLegalInterpretation(){
        var value=score("m1 m1 m2 m2 m3 m3 m4 m4 m5 m5 m6 m6 m7 m7","m7");
        assertTrue(value.yakuNames().contains("RYANPEIKOU"));assertFalse(value.yakuNames().contains("IIPEIKOU"));assertFalse(value.yakuNames().contains("CHIITOITSU"));assertTrue(value.han()>=9);
    }
    @Test void situationalYakuUseTheirWinMethodAndDoubleRiichiReplacesRiichi(){
        assertEquals(1,han(score(PINFU,"m1","riichi"),"RIICHI"));var twice=score(PINFU,"m1","riichi","double","ippatsu");
        assertEquals(2,han(twice,"DOUBLE_RIICHI"));assertEquals(0,han(twice,"RIICHI"));assertEquals(1,han(twice,"IPPATSU"));
        assertFalse(score(PINFU,"m1","ippatsu").yakuNames().contains("IPPATSU"));
        assertEquals(1,han(score(PINFU,"m1","tsumo","haitei"),"HAITEI_RAOYUE"));assertEquals(1,han(score(PINFU,"m1","houtei"),"HOUTEI_RAOYU"));assertEquals(1,han(score(PINFU,"m1","chankan"),"CHANKAN"));
        var quad=meld(Meld.Kind.QUAD,false,"m5 m5 m5 m5");var rinshan=score("p1 p2 p3 p4 p5 p6 s7 s8 s9 z2 z2","p1",List.of(quad),flags("tsumo","rinshan","haitei"),RiichiScore.Options.DEFAULT,"","",28,27).orElseThrow();
        assertEquals(1,han(rinshan,"RINSHAN_KAIHOU"));assertFalse(rinshan.yakuNames().contains("HAITEI_RAOYUE"));
    }
    @Test void dragonAndDoubleWindTripletsAddHanButDoubleWindPairIsOnlyTwoFu(){
        var dragons=score("z5 z5 z5 z6 z6 z6 m1 m2 m3 p4 p5 p6 z7 z7","m1");assertEquals(2,han(dragons,"YAKUHAI"));
        var wind=score("m1 m2 m3 p4 p5 p6 s7 s8 s9 z2 z2","m1",List.of(meld(Meld.Kind.TRIPLET,true,"z1 z1 z1")),flags(),RiichiScore.Options.DEFAULT,"","",27,27).orElseThrow();assertEquals(2,han(wind,"YAKUHAI"));
        var pair=score("m2 m2 m2 p3 p3 p3 s4 s5 s6 m6 m7 m8 z1 z1","m8",List.of(),flags("riichi"),RiichiScore.Options.DEFAULT,"","",27,27).orElseThrow();assertEquals(40,pair.fu());
    }
    @Test void doraUraAndRedBonusesCountPhysicalTilesAndCannotCreateAYaku(){
        String hand=PINFU.replace("m5","m0");
        var riichi=score(hand,"m1",List.of(),flags("riichi"),RiichiScore.Options.DEFAULT,"m4","p6",28,27).orElseThrow();
        assertEquals(1,han(riichi,"DORA"));assertEquals(2,han(riichi,"URA_DORA"));assertEquals(1,han(riichi,"AKA_DORA"));assertEquals(6,riichi.han());
        var plain=score(hand,"m1",List.of(),flags(),RiichiScore.Options.DEFAULT,"m4","p6",28,27).orElseThrow();assertEquals(0,han(plain,"URA_DORA"));assertEquals(3,plain.han());
        assertTrue(score("m4 m5 m6 p2 p3 p4 s6 s7 s8 p7 p7","p2",List.of(meld(Meld.Kind.SEQUENCE,true,"m1 m2 m3")),flags(),RiichiScore.Options.DEFAULT,"p6","",28,27).isEmpty());
    }
    @Test void kiriageAndCountedYakumanRespectRoomOptions(){
        for(boolean kiriage:new boolean[]{false,true}){
            var options=new RiichiScore.Options(true,kiriage,true,false);
            var four=score(PINFU,"m1",List.of(),flags("riichi","ippatsu"),options,"p1","",28,27).orElseThrow();assertEquals(4,four.han());assertEquals(30,four.fu());assertEquals(kiriage?2000:1920,four.basePoints());
            var three=score("z5 z5 z5 p1 p1 p1 s9 s9 s9 m2 m3 m4 z2 z2","m2",List.of(),flags(),options,"","",28,27).orElseThrow();assertEquals(3,three.han());assertEquals(60,three.fu());assertEquals(kiriage?2000:1920,three.basePoints());
        }
        for(boolean counted:new boolean[]{false,true}){
            var value=score(PINFU,"m1",List.of(),flags("riichi","ippatsu"),new RiichiScore.Options(true,false,counted,false),"p6 p6 p6 m2 m2","p6",28,27).orElseThrow();
            assertEquals(13,value.han());assertEquals(0,value.yakuman());assertEquals(counted?8000:6000,value.basePoints());
        }
    }
    @Test void recognizesEachNaturalYakumanAndSpecialWaitForm(){
        String[][] cases={
            {"KOKUSHI_MUSOU","m1 m9 p1 p9 s1 s9 z1 z2 z3 z4 z5 z6 z7 z7","m1"},
            {"KOKUSHI_MUSOU_13_WAIT","m1 m9 p1 p9 s1 s9 z1 z2 z3 z4 z5 z6 z7 z7","z7"},
            {"SUUANKOU_TANKI","m2 m2 m2 p3 p3 p3 s4 s4 s4 z1 z1 z1 p5 p5","p5"},
            {"DAISANGEN","z5 z5 z5 z6 z6 z6 z7 z7 z7 m1 m2 m3 p5 p5","m1"},
            {"SHOUSUUSHII","z1 z1 z1 z2 z2 z2 z3 z3 z3 m1 m2 m3 z4 z4","m1"},
            {"DAISUUSHII","z1 z1 z1 z2 z2 z2 z3 z3 z3 z4 z4 z4 m2 m2","z1"},
            {"TSUUIISOU","z1 z1 z2 z2 z3 z3 z4 z4 z5 z5 z6 z6 z7 z7","z7"},
            {"CHINROUTOU","m1 m1 m1 m9 m9 m9 p1 p1 p1 p9 p9 p9 s1 s1","m1"},
            {"RYUUIISOU","s2 s3 s4 s2 s3 s4 s6 s6 s6 s8 s8 s8 z6 z6","s2"},
            {"CHUUREN_POUTOU","m1 m1 m1 m2 m3 m4 m5 m6 m7 m8 m9 m9 m9 m5","m1"},
            {"JUNSEI_CHUUREN_POUTOU","m1 m1 m1 m2 m3 m4 m5 m6 m7 m8 m9 m9 m9 m5","m5"}
        };
        for(String[] fixture:cases){var value=score(fixture[1],fixture[2]);assertTrue(value.yakuNames().contains(fixture[0]),fixture[0]);assertEquals(1,value.yakuman(),fixture[0]);assertEquals(8000,value.basePoints(),fixture[0]);}
        assertTrue(score("m2 m2 m2 p3 p3 p3 s4 s4 s4 z1 z1 z1 p5 p5","p3","tsumo").yakuNames().contains("SUUANKOU"));
        var quads=List.of(meld(Meld.Kind.QUAD,true,"m2 m2 m2 m2"),meld(Meld.Kind.QUAD,true,"p3 p3 p3 p3"),meld(Meld.Kind.QUAD,true,"s4 s4 s4 s4"),meld(Meld.Kind.QUAD,true,"z1 z1 z1 z1"));
        assertTrue(score("p5 p5","p5",quads,flags(),RiichiScore.Options.DEFAULT,"","",28,27).orElseThrow().yakuNames().contains("SUUKANTSU"));
    }
    @Test void heavenlyAndEarthlyHandsRequireCorrectSeatAndAnUninterruptedClosedHand(){
        assertTrue(score(PINFU,"m1",List.of(),flags("tsumo","tenhou"),RiichiScore.Options.DEFAULT,"","",27,27).orElseThrow().yakuNames().contains("TENHOU"));
        assertTrue(score(PINFU,"m1","tsumo","chiihou").yakuNames().contains("CHIIHOU"));
        assertFalse(score(PINFU,"m1","tsumo","tenhou").yakuNames().contains("TENHOU"));assertFalse(score(PINFU,"m1","chiihou").yakuNames().contains("CHIIHOU"));
    }
    @Test void fourSpecialShapesCanDoubleButDifferentYakumanDoNotStack(){
        String[][] cases={
            {"m1 m9 p1 p9 s1 s9 z1 z2 z3 z4 z5 z6 z7 z7","z7"},
            {"m2 m2 m2 p3 p3 p3 s4 s4 s4 z1 z1 z1 p5 p5","p5"},
            {"z1 z1 z1 z2 z2 z2 z3 z3 z3 z4 z4 z4 z5 z5","z1"},
            {"m1 m1 m1 m2 m3 m4 m5 m6 m7 m8 m9 m9 m9 m5","m5"}
        };
        for(String[] fixture:cases){var value=score(fixture[0],fixture[1],List.of(),flags(),new RiichiScore.Options(true,false,true,true),"","",28,27).orElseThrow();assertEquals(2,value.yakuman());assertEquals(64000,value.ronPayment(false));}
        var combined=score("z1 z1 z1 z2 z2 z2 z3 z3 z3 z4 z4 z4 z5 z5","z1");assertTrue(combined.yakuNames().containsAll(List.of("DAISUUSHII","TSUUIISOU")));assertEquals(1,combined.yakuman());assertEquals(32000,combined.ronPayment(false));
    }
    @Test void naturalYakumanWinsATieAgainstAnOrdinaryCountedYakumanDecomposition(){
        var value=score("m2 m2 m2 m3 m3 m3 m4 m4 m4 m5 m5 m5 m6 m6","m6",List.of(),flags("tsumo","riichi","ippatsu"),RiichiScore.Options.DEFAULT,"m1","",28,27).orElseThrow();
        assertEquals(1,value.yakuman());assertTrue(value.yakuNames().contains("SUUANKOU_TANKI"));assertEquals(8000,value.basePoints());
    }
    @Test void malformedHandsAndMissingWinningTilesCannotScore(){
        assertTrue(score("m1 m2 m3 p4 p5 p6 s7 s8 s9 z1 z1 z1 z2 z3","z3",List.of(),flags("riichi"),RiichiScore.Options.DEFAULT,"","",28,27).isEmpty());
        List<Tiles.Tile> hand=tiles(PINFU);var context=new RiichiScore.Context(new Tiles.Tile("missing",0,false),28,27,flags("riichi"),List.of(),List.of(),RiichiScore.Options.DEFAULT);
        assertTrue(RiichiScore.score(hand,List.of(),context).isEmpty());
        var real=new RiichiScore.Context(hand.getFirst(),28,27,flags("riichi"),List.of(),List.of(),RiichiScore.Options.DEFAULT);hand.set(1,hand.getFirst());assertTrue(RiichiScore.score(hand,List.of(),real).isEmpty());
    }
}
