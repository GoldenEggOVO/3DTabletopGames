package dev.tabletop3d.rules.mahjong;

import dev.tabletop3d.rules.HandGame;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MahjongGameTest {
    @Test void onlyFourSeatsAreAllowedAndPublicViewsDoNotRevealConcealedFaces() {
        assertThrows(IllegalArgumentException.class,()->new MahjongGame(3,0,Map.of("profile","guangdong")));
        MahjongGame game=game(17);assertEquals(14,game.handSize(0));assertEquals(13,game.handSize(1));assertEquals(83,game.deckSize());
        assertTrue(game.cells().isEmpty());assertTrue(game.legalActions(1).isEmpty());
        assertThrows(UnsupportedOperationException.class,()->game.hand(0).clear());
        for(var tile:game.hand(1))assertFalse(game.publicInfo().containsValue(tile.id()));
    }
    @Test void seededActionsReplayWithoutDuplicateOrLostPhysicalTiles() {
        for(long seed=0;seed<25;seed++) {
            MahjongGame a=game(seed),b=game(seed);
            for(int step=0;!a.finished()&&step<600;step++) {
                int seat=a.currentPlayer();List<String> legal=a.legalActions(seat);assertFalse(legal.isEmpty(),a.publicInfo().toString());
                String action=legal.getFirst();a.apply(seat,action);b.apply(seat,action);
                assertEquals(a.publicInfo(),b.publicInfo());assertEquals(a.cells(),b.cells());
                Set<String> ids=new HashSet<>();int visible=0;
                for(int player=0;player<4;player++)for(List<HandGame.Piece> group:List.of(a.hand(player),a.discards(player),a.exposed(player)))
                    for(var tile:group){visible++;assertTrue(ids.add(tile.id()),"A physical tile has two owners");}
                assertEquals(136,visible+a.deckSize());
            }
            assertTrue(a.finished(),"A finite wall must finish the round");
        }
    }
    @Test void invalidActionsLeaveTheEntireObservableStateUnchanged() {
        MahjongGame game=game(9);Map<String,String> before=game.publicInfo();List<HandGame.Piece> hand=game.hand(0);
        assertThrows(IllegalArgumentException.class,()->game.apply(1,"discard:"+hand.getFirst().id()));
        assertThrows(IllegalArgumentException.class,()->game.apply(0,"discard:missing"));
        assertEquals(before,game.publicInfo());assertEquals(hand,game.hand(0));
    }
    @Test void ronResponsesFinishBeforeAnyPonAndPayEveryWinner()throws Exception {
        MahjongGame game=game(0);setHands(game,
            "z1 m1 m2 m3 m4 m5 m6 p1 p2 p3 s4 s5 s6 z2",
            "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1",
            "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1",
            "z1 z1 p1 p2 p4 p5 s1 s2 s4 s5 z2 z3 z4");
        game.apply(0,"discard:s0_0");assertEquals(1,game.currentPlayer());assertEquals(List.of("ron","pass"),game.legalActions(1));
        game.apply(1,"ron");assertFalse(game.finished());assertEquals(2,game.currentPlayer());
        game.apply(2,"ron");assertTrue(game.finished());assertEquals("1,2",game.publicInfo().get("winners"));
        assertEquals("23000",game.publicInfo().get("score.0"));assertEquals("26000",game.publicInfo().get("score.1"));
        assertEquals("26000",game.publicInfo().get("score.2"));assertTrue(game.exposed(3).isEmpty());
    }
    @Test void declinedRonFallsThroughToPonWithoutDrawingAnotherTile()throws Exception {
        MahjongGame game=game(0);setHands(game,
            "z1 m1 m2 m3 m4 m5 m6 p1 p2 p3 s4 s5 s6 z2",
            "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1",
            "m1 m3 m5 m7 m9 p1 p3 p5 p7 s1 s3 s5 z2",
            "z1 z1 p1 p2 p4 p5 s1 s2 s4 s5 z2 z3 z4");
        int remaining=game.deckSize();game.apply(0,"discard:s0_0");game.apply(1,"pass");
        assertEquals(3,game.currentPlayer());String pon=game.legalActions(3).stream().filter(a->a.startsWith("pon:")).findFirst().orElseThrow();
        game.apply(3,pon);assertEquals(3,game.currentPlayer());assertEquals(3,game.exposed(3).size());
        assertEquals(11,game.handSize(3));assertTrue(game.discards(0).isEmpty());assertEquals(remaining,game.deckSize());
        assertTrue(game.legalActions(3).stream().allMatch(a->a.startsWith("discard:")));
    }
    @Test void closedKongDrawsOneReplacementAndSelfDrawUsesFlatThreeWayPayment()throws Exception {
        MahjongGame game=game(0);setHands(game,"m1 m1 m1 m1 m2 m3 p4 p5 p6 s7 s8 s9 z1 z1","","","");
        int wall=game.deckSize();game.apply(0,"kan-closed:s0_0");
        assertEquals(11,game.handSize(0));assertEquals(4,game.exposed(0).size());assertEquals(wall-1,game.deckSize());
        game=game(0);setHands(game,"m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z1","","","");
        game.apply(0,"tsumo");assertEquals("26500",game.publicInfo().get("score.0"));
        for(int seat=1;seat<4;seat++)assertEquals("24500",game.publicInfo().get("score."+seat));
    }
    @Test void addedKongWaitsForRobbersAndDoesNotConsumeAReplacementWhenRobbed()throws Exception {
        MahjongGame game=game(0);setHands(game,
            "z1 m1 m2 m3 p1 p2 p3 s1 s2 s3 z2",
            "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1","","");
        List<List<Meld>> melds=field(game,"melds");
        melds.get(0).add(new Meld(Meld.Kind.TRIPLET,List.of(new Tiles.Tile("meld0",27,false),new Tiles.Tile("meld1",27,false),new Tiles.Tile("meld2",27,false)),true,2));
        int wall=game.deckSize();game.apply(0,"kan-added:s0_0");assertEquals(1,game.currentPlayer());
        game.apply(1,"ron");assertTrue(game.finished());assertEquals(wall,game.deckSize());
        assertEquals(3,game.exposed(0).size());assertEquals("z1",game.discards(0).getFirst().face());
    }
    private static MahjongGame game(long seed){return new MahjongGame(4,seed,Map.of("profile","guangdong"));}
    private static void setHands(MahjongGame game,String... codes)throws Exception {
        List<List<Tiles.Tile>> hands=field(game,"hands");
        for(int seat=0;seat<4;seat++){hands.get(seat).clear();int index=0;for(String code:codes[seat].split(" "))if(!code.isEmpty())hands.get(seat).add(new Tiles.Tile("s"+seat+"_"+index++,Tiles.type(code),false));}
    }
    @SuppressWarnings("unchecked") private static <T>T field(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return (T)field.get(object);}
}
