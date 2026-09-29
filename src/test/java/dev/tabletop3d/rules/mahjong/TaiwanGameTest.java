package dev.tabletop3d.rules.mahjong;

import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.MahjongGame;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaiwanGameTest {
    @Test void flowersAreExposedAndReplacedWithoutChangingTheSixteenTileHandOrInventory() {
        for(int seed=0;seed<12;seed++) {
            var game=new MahjongGame(4,seed,Map.of("profile","taiwan","rounds","1"));
            assertEquals(17,game.handSize(0));for(int seat=1;seat<4;seat++)assertEquals(16,game.handSize(seat));
            for(int step=0;step<600&&!game.finished();step++) {
                Set<String> seen=new HashSet<>();int count=game.deckSize();
                for(int seat=0;seat<4;seat++) {
                    assertTrue(game.hand(seat).stream().noneMatch(t->t.face().startsWith("f")));
                    for(var group:List.of(game.hand(seat),game.discards(seat),game.exposed(seat)))for(HandGame.Piece tile:group){assertTrue(seen.add(tile.id()));count++;}
                }
                assertEquals(144,count);var actions=game.legalActions(game.currentPlayer());assertFalse(actions.isEmpty());game.apply(game.currentPlayer(),actions.getFirst());
            }
            assertTrue(game.finished());
        }
    }
    @Test void ponIsOfferedBeforeTheNextPlayersChiAndChiDoesNotDraw()throws Exception {
        var game=new MahjongGame(4,0,Map.of("profile","taiwan","rounds","1"));
        setHand(game,0,"m3 m5 m7 m9 p1 p2 p4 p5 p7 p8 s1 s3 s5 s7 z1 z2 z3");
        setHand(game,1,"m1 m2 m5 m7 p1 p2 p4 p5 s1 s2 s4 s5 z1 z2 z3 z4");
        setHand(game,2,"m3 m3 m5 m7 p1 p2 p4 p5 s1 s2 s4 s5 z1 z2 z3 z4");setHand(game,3,"");
        game.apply(0,"discard:s0_0");assertEquals(2,game.currentPlayer());game.apply(2,"pass");assertEquals(1,game.currentPlayer());
        int wall=game.deckSize();String chi=game.legalActions(1).stream().filter(a->a.startsWith("chi:")).findFirst().orElseThrow();
        game.apply(1,chi);assertEquals(wall,game.deckSize());assertEquals(14,game.handSize(1));assertFalse(game.legalActions(1).contains("tsumo"));
    }
    @Test void dealerWinsContinueTheSameSeatAndNonDealerWinsAdvanceTheMatch()throws Exception {
        var game=new MahjongGame(4,12,Map.of("profile","taiwan","rounds","4"));
        winningHand(game,0);game.apply(0,"tsumo");assertFalse(game.finished());assertEquals("ROUND_END",game.publicInfo().get("phase"));
        assertEquals("0",game.publicInfo().get("dealer"));assertEquals("1",game.publicInfo().get("dealerStreak"));assertEquals("1",game.publicInfo().get("round"));
        game.apply(0,"next-hand");assertEquals(17,game.handSize(0));
        for(int next=1;next<=4;next++) {
            int winner=next%4;winningHand(game,winner);set(game,"current",winner);game.apply(winner,"tsumo");
            if(next<4){assertEquals(Integer.toString(winner),game.publicInfo().get("dealer"));assertEquals("0",game.publicInfo().get("dealerStreak"));game.apply(winner,"next-hand");}
        }
        assertTrue(game.finished());
    }
    private static void winningHand(MahjongGame game,int seat)throws Exception {
        setHand(game,seat,"m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 p6 p7 p8 z1 z1");
        List<List<Tiles.Tile>> hands=get(game,"hands");set(game,"drawnTile",hands.get(seat).getLast());
        List<List<Meld>> melds=get(game,"melds");melds.get(seat).clear();
    }
    private static void setHand(MahjongGame game,int seat,String codes)throws Exception {
        List<List<Tiles.Tile>> hands=get(game,"hands");hands.get(seat).clear();int index=0;
        for(String code:codes.split(" "))if(!code.isEmpty())hands.get(seat).add(new Tiles.Tile("s"+seat+"_"+index++,Tiles.type(code),false));
    }
    @SuppressWarnings("unchecked") private static <T>T get(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return (T)f.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
}
