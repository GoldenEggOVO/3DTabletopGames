package dev.tabletop3d.rules.mahjong;

import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.MahjongGame;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SichuanGameTest {
    @Test void exchangeMovesExactlyThreeSameSuitTilesTogetherBeforeHiddenMissingSuitChoices() {
        var game=new MahjongGame(4,9,Map.of("profile","sichuan","rounds","1","exchange-direction","CLOCKWISE"));
        List<List<String>> selected=new ArrayList<>();for(int seat=0;seat<4;seat++)selected.add(new ArrayList<>());
        while(game.publicInfo().get("phase").equals("EXCHANGE")) {
            int seat=game.currentPlayer();String action=game.legalActions(seat).getFirst();
            if(action.startsWith("exchange-add:"))selected.get(seat).add(action.substring(13));game.apply(seat,action);
        }
        for(int seat=0;seat<4;seat++) {
            assertEquals(3,selected.get(seat).size());List<String> received=game.hand((seat+1)%4).stream().map(HandGame.Piece::id).toList();
            assertTrue(received.containsAll(selected.get(seat)));assertEquals(seat==0?14:13,game.handSize(seat));
        }
        game.apply(0,"missing:m");assertFalse(game.publicInfo().containsKey("missing.0"));
        for(int seat=1;seat<4;seat++)game.apply(seat,"missing:p");assertEquals("TURN",game.publicInfo().get("phase"));
        if(game.hand(0).stream().anyMatch(t->t.face().startsWith("m")))for(String action:game.legalActions(0)) {
            assertTrue(action.startsWith("discard:"));assertTrue(game.hand(0).stream().anyMatch(t->t.id().equals(action.substring(8))&&t.face().startsWith("m")));
        }
    }
    @Test void finiteBloodBattleConservesTilesAndAllInternalPointsAcrossSeeds() {
        for(int seed=0;seed<25;seed++) {
            var game=new MahjongGame(4,seed,Map.of("profile","sichuan","rounds","1"));
            for(int step=0;step<650&&!game.finished();step++) {
                Set<String> ids=new HashSet<>();int count=game.deckSize();
                for(int seat=0;seat<4;seat++)for(var group:List.of(game.hand(seat),game.discards(seat),game.exposed(seat)))for(var tile:group){count++;assertTrue(ids.add(tile.id()));}
                assertEquals(108,count);var legal=game.legalActions(game.currentPlayer());assertFalse(legal.isEmpty(),game.publicInfo().toString());game.apply(game.currentPlayer(),legal.getFirst());
            }
            assertTrue(game.finished());int total=0;for(int seat=0;seat<4;seat++)total+=Integer.parseInt(game.publicInfo().get("score."+seat));assertEquals(100000,total);
        }
    }
    @Test void multipleWinnersLeaveTheHandAndNeverPayLaterSelfDraws()throws Exception {
        var game=prepared();setHand(game,0,"m3 m5 m7 m9 p1 p2 p4 p5 p7 p8 m1 m4 p6 p9");
        for(int seat:new int[]{1,2})setHand(game,seat,"m1 m2 m4 m5 m6 p2 p3 p4 p7 p8 p9 m8 m8");setHand(game,3,"");
        game.apply(0,"discard:s0_0");game.apply(1,"ron");game.apply(2,"ron");assertFalse(game.finished());assertEquals(3,game.currentPlayer());
        int first=Integer.parseInt(game.publicInfo().get("score.1")),second=Integer.parseInt(game.publicInfo().get("score.2"));
        setHand(game,3,"m1 m2 m3 m4 m5 m6 p2 p3 p4 p7 p8 p9 m8 m8");set(game,"drawnTile",hands(game).get(3).getLast());game.apply(3,"tsumo");
        assertTrue(game.finished());assertEquals("1,2,3",game.publicInfo().get("winners"));
        assertEquals(first,Integer.parseInt(game.publicInfo().get("score.1")));assertEquals(second,Integer.parseInt(game.publicInfo().get("score.2")));
    }
    @Test void unreadyKongIncomeIsRefundedOnExhaustion()throws Exception {
        var game=prepared();setHand(game,0,"m1 m1 m1 m1 m2 m4 p1 p3 p5 p7 p9 m6 m8 p2");
        for(int seat=1;seat<4;seat++)setHand(game,seat,"m1 m2 m4 m5 m7 m8 p1 p2 p4 p5 p7 p8 m9");
        game.apply(0,"kan-closed:s0_0");assertEquals("25600",game.publicInfo().get("score.0"));
        setHand(game,0,"m2 m4 m6 m8 p1 p3 p5 p7 p9 m9 p2");exhaust(game);game.apply(0,"discard:s0_10");
        assertTrue(game.finished());for(int seat=0;seat<4;seat++)assertEquals("25000",game.publicInfo().get("score."+seat));
    }
    @Test void kongDiscardTransfersTheActualKongIncomeOnce()throws Exception {
        var game=prepared();setHand(game,0,"m1 m1 m1 m1 m2 m3 p1 p3 p5 p7 p9 m6 m8 p2");
        setHand(game,1,"m1 m2 m4 m5 m6 p2 p3 p4 p7 p8 p9 m8 m8");setHand(game,2,"");setHand(game,3,"");
        game.apply(0,"kan-closed:s0_0");setHand(game,0,"m3 m4 m6 m8 p1 p3 p5 p7 p9 m9 p2");game.apply(0,"discard:s0_0");game.apply(1,"ron");
        assertEquals("24800",game.publicInfo().get("score.0"));assertEquals("25600",game.publicInfo().get("score.1"));
    }
    private static MahjongGame prepared()throws Exception {
        var game=new MahjongGame(4,7,Map.of("profile","sichuan","rounds","1"));
        while(!game.publicInfo().get("phase").equals("TURN"))game.apply(game.currentPlayer(),game.legalActions(game.currentPlayer()).getFirst());
        int[] missing=get(game,"missingSuit");Arrays.fill(missing,2);set(game,"discardCount",5);return game;
    }
    private static List<List<Tiles.Tile>> hands(MahjongGame game)throws Exception{return get(game,"hands");}
    private static void setHand(MahjongGame game,int seat,String codes)throws Exception {
        var hand=hands(game).get(seat);hand.clear();int index=0;for(String code:codes.split(" "))if(!code.isEmpty())hand.add(new Tiles.Tile("s"+seat+"_"+index++,Tiles.type(code),false));
    }
    private static void exhaust(MahjongGame game)throws Exception{Wall wall=get(game,"wall");set(wall,"front",get(wall,"back"));}
    @SuppressWarnings("unchecked") private static <T>T get(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return (T)f.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
}
