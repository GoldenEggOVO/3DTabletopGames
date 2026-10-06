package dev.tabletop3d.rules.ludo;

import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.GameFactory;

import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LudoGameTest {
    BoardGame game(int players,long seed){return GameFactory.create("ludo",players,seed);}
    void set(BoardGame game,String name,Object value)throws Exception{Field f=game.getClass().getDeclaredField(name);f.setAccessible(true);f.set(game,value);}
    int[] positions(BoardGame game)throws Exception{Field f=game.getClass().getDeclaredField("progress");f.setAccessible(true);return (int[])f.get(game);}
    @Test void firstPawnStartsAutomaticallyAndTwoPlayersUseOppositeColors(){
        BoardGame g=game(2,0);assertEquals("[0, 2]",g.publicInfo().get("colors"));
        assertEquals("●1",g.cells().stream().filter(c->c.id().equals("sk0")).findFirst().orElseThrow().piece());
        assertEquals(1,g.cells().stream().filter(c->c.id().equals("sk26")).findFirst().orElseThrow().owner());
        assertEquals(6,g.cells().stream().filter(c->c.id().startsWith("ba")&&c.owner()>=0).count());
        assertEquals(List.of("roll"),g.legalActions(0));assertTrue(g.legalActions(1).isEmpty());
    }
    @Test void sixOffersDeploymentAndKeepsTheTurn()throws Exception{
        BoardGame g=game(2,0);set(g,"pendingRoll",6);
        assertTrue(g.legalActions(0).contains("move:1:sk0"));g.apply(0,"move:1:sk0");
        assertEquals(0,g.currentPlayer());assertEquals(List.of("roll"),g.legalActions(0));
    }
    @Test void captureReturnsEveryOpposingPawnToItsOwnYard()throws Exception{
        BoardGame g=game(2,0);int[] p=positions(g);p[0]=24;p[4]=0;p[5]=0;set(g,"pendingRoll",2);
        g.apply(0,"move:0:sk26");assertEquals(-1,p[4]);assertEquals(-1,p[5]);assertEquals(1,g.currentPlayer());
    }
    @Test void finishRequiresExactRollWithoutReboundAndAllFourMustFinish()throws Exception{
        BoardGame g=game(2,0);int[] p=positions(g);Arrays.fill(p,0,3,56);p[3]=55;set(g,"pendingRoll",2);
        assertTrue(g.legalActions(0).isEmpty());set(g,"pendingRoll",1);
        g.apply(0,"move:3:go0");assertEquals("winner:0",g.outcome());assertTrue(g.legalActions(0).isEmpty());
    }
    @Test void everyHomeLaneIsPrivateAndItsFinishIsReachable()throws Exception{
        BoardGame g=game(4,0);int[] p=positions(g);
        for(int seat=0;seat<4;seat++){set(g,"current",seat);p[seat*4]=50;set(g,"pendingRoll",1);g.apply(seat,"move:"+(seat*4)+":ld"+seat+"_0");
            set(g,"current",seat);set(g,"pendingRoll",5);assertTrue(g.legalActions(seat).contains("move:"+(seat*4)+":go"+seat));}
    }
    @Test void noMoveRollAdvancesWithoutAnEmptyActionDeadlock()throws Exception{
        long seed=0;while(new SplittableRandom(seed).nextInt(1,7)==6)seed++;
        BoardGame g=game(2,seed);Arrays.fill(positions(g),0,4,-1);g.apply(0,"roll");
        assertEquals(1,g.currentPlayer());assertEquals(List.of("roll"),g.legalActions(1));
    }
    @Test void identicalSeedsReplayIdenticallyAndWrongTurnsNeverMutate(){
        for(int players=2;players<=4;players++){
            BoardGame a=game(players,98),b=game(players,98);
            assertThrows(IllegalArgumentException.class,()->a.apply(1,"roll"));
            for(int i=0;i<2000&&!a.finished();i++){
                int seat=a.currentPlayer();List<String> moves=a.legalActions(seat);assertFalse(moves.isEmpty());
                String move=moves.get(i%moves.size());a.apply(seat,move);b.apply(seat,move);
                assertEquals(a.cells(),b.cells());assertEquals(a.publicInfo(),b.publicInfo());
            }
        }
    }
    @Test void unsupportedPlayerCountsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->game(1,0));assertThrows(IllegalArgumentException.class,()->game(5,0));
    }
}
