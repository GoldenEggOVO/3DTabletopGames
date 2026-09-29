package dev.tabletop3d.rules;

import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LudoOptionsTest {
    @Test void threeStartingAttemptsAreBoundedAndStopAfterFirstDeployment()throws Exception{
        long seed=0;for(;;seed++){SplittableRandom random=new SplittableRandom(seed);if(random.nextInt(1,7)!=6&&random.nextInt(1,7)!=6&&random.nextInt(1,7)!=6)break;}
        LudoGame game=new LudoGame(2,seed,new LudoOptions(false,false,true,false,3));
        game.apply(0,"roll");assertEquals(0,game.currentPlayer());game.apply(0,"roll");assertEquals(0,game.currentPlayer());
        game.apply(0,"roll");assertEquals(1,game.currentPlayer());
        LudoGame deployed=new LudoGame(2,seed,new LudoOptions(false,false,true,false,3));
        set(deployed,"pendingRoll",6);deployed.apply(0,"move:0:sk0");positions(deployed)[0]=-1;
        deployed.apply(0,"roll");assertEquals(1,deployed.currentPlayer(),"A captured pawn does not regain the first-deployment attempts");
    }
    void set(LudoGame game,String name,Object value)throws Exception{Field f=LudoGame.class.getDeclaredField(name);f.setAccessible(true);f.set(game,value);}
    int[] positions(LudoGame game)throws Exception{Field f=LudoGame.class.getDeclaredField("progress");f.setAccessible(true);return (int[])f.get(game);}
    LudoGame blocking(){return new LudoGame(2,0,new LudoOptions(true,true,true,false));}
    void readyToFinish(LudoGame game,int seat,int roll)throws Exception{
        int[] p=positions(game);Arrays.fill(p,seat*4,seat*4+3,56);p[seat*4+3]=56-roll;set(game,"current",seat);set(game,"pendingRoll",roll);
    }

    @Test void sixRequiredOptionStartsEveryPawnInTheYard()throws Exception{
        LudoGame g=new LudoGame(2,0,new LudoOptions(false,false,true,false));
        assertEquals(8,g.cells().stream().filter(c->c.id().startsWith("ba")&&c.owner()>=0).count());
        set(g,"pendingRoll",3);assertTrue(g.legalActions(0).isEmpty());
        set(g,"pendingRoll",6);assertEquals(List.of("move:0:sk0","move:1:sk0","move:2:sk0","move:3:sk0"),g.legalActions(0));
        g.apply(0,"move:0:sk0");assertEquals(0,positions(g)[0]);assertEquals(0,g.currentPlayer());
    }
    @Test void capturedFirstPawnStillRequiresSixWithAutomaticFirstEnabled()throws Exception{
        LudoGame g=new LudoGame(2,0,LudoOptions.DEFAULT);int[] p=positions(g);p[4]=24;set(g,"current",1);set(g,"pendingRoll",2);
        g.apply(1,"move:4:sk0");assertEquals(-1,p[0]);assertEquals(0,g.currentPlayer());
        set(g,"pendingRoll",3);assertTrue(g.legalActions(0).isEmpty());
        set(g,"pendingRoll",6);assertTrue(g.legalActions(0).contains("move:0:sk0"));
    }
    @Test void anOwnPawnOnAnIntermediateCellBlocksTheMove()throws Exception{
        LudoGame g=blocking();int[] p=positions(g);p[0]=5;p[1]=7;set(g,"pendingRoll",3);
        assertFalse(g.legalActions(0).contains("move:0:sk8"));
        assertThrows(IllegalArgumentException.class,()->g.apply(0,"move:0:sk8"));assertEquals(5,p[0]);
    }
    @Test void oneOpponentOnAnIntermediateCellBlocksTheMove()throws Exception{
        LudoGame g=blocking();int[] p=positions(g);p[0]=24;p[4]=0;set(g,"pendingRoll",4);
        assertFalse(g.legalActions(0).contains("move:0:sk28"));
    }
    @Test void blockingFollowsTheRingAcrossItsNumberingBoundary()throws Exception{
        LudoGame g=blocking();int[] p=positions(g);p[4]=24;set(g,"current",1);set(g,"pendingRoll",4);
        assertFalse(g.legalActions(1).contains("move:4:sk2"));
    }
    @Test void blockingChecksThePrivateHomeLane()throws Exception{
        LudoGame g=blocking();int[] p=positions(g);p[0]=50;p[1]=52;set(g,"pendingRoll",4);
        assertFalse(g.legalActions(0).contains("move:0:ld0_3"));
    }
    @Test void occupiedSourceAndOwnDestinationNeverBlockTheMove()throws Exception{
        LudoGame g=blocking();int[] p=positions(g);p[0]=5;p[1]=5;p[2]=8;set(g,"pendingRoll",3);
        g.apply(0,"move:0:sk8");assertEquals(8,p[0]);assertEquals(5,p[1]);assertEquals(8,p[2]);
    }
    @Test void landingOnOpponentsStillCapturesEveryPawnWithBlockingEnabled()throws Exception{
        LudoGame g=blocking();int[] p=positions(g);p[0]=24;p[4]=0;p[5]=0;set(g,"pendingRoll",2);
        g.apply(0,"move:0:sk26");assertEquals(26,p[0]);assertEquals(-1,p[4]);assertEquals(-1,p[5]);
    }
    @Test void occupiedFinishRemainsALegalDestination()throws Exception{
        LudoGame g=blocking();readyToFinish(g,0,1);g.apply(0,"move:3:go0");assertEquals("winner:0",g.outcome());
    }
    @Test void defaultAllowsPassingOccupiedCells()throws Exception{
        LudoGame g=new LudoGame(2,0);int[] p=positions(g);p[0]=24;p[1]=25;p[4]=0;set(g,"pendingRoll",4);
        g.apply(0,"move:0:sk28");assertEquals(28,p[0]);assertEquals(25,p[1]);assertEquals(0,p[4]);
    }
    @Test void overOkClampsOvershootAtTheFinish()throws Exception{
        LudoGame g=new LudoGame(2,0,new LudoOptions(true,false,false,false));readyToFinish(g,0,1);set(g,"pendingRoll",6);
        assertEquals(List.of("move:3:go0"),g.legalActions(0));g.apply(0,"move:3:go0");
        assertEquals(56,positions(g)[3]);assertEquals("winner:0",g.outcome());
    }
    @Test void overOkStillHonorsIntermediateHomeLaneBlocking()throws Exception{
        LudoGame g=new LudoGame(2,0,new LudoOptions(true,true,false,false));int[] p=positions(g);p[0]=53;p[1]=55;set(g,"pendingRoll",6);
        assertFalse(g.legalActions(0).contains("move:0:go0"));
    }
    @Test void allPlacesSkipsFinishedSeatsAndAwardsLastPlace()throws Exception{
        LudoGame g=new LudoGame(4,0,new LudoOptions(true,false,true,true));readyToFinish(g,1,1);g.apply(1,"move:7:go1");
        assertFalse(g.finished());assertEquals("ongoing",g.outcome());assertEquals(List.of(1),g.placements());assertEquals(2,g.currentPlayer());
        assertTrue(g.legalActions(1).isEmpty());List<Integer> first=g.placements();assertThrows(UnsupportedOperationException.class,()->first.add(0));
        readyToFinish(g,3,1);g.apply(3,"move:15:go3");assertEquals(List.of(1,3),g.placements());assertEquals(0,g.currentPlayer());
        set(g,"pendingRoll",1);g.apply(0,"move:0:sk1");assertEquals(2,g.currentPlayer());assertEquals(List.of(1),first);
        readyToFinish(g,2,1);g.apply(2,"move:11:go2");
        assertTrue(g.finished());assertEquals(List.of(1,3,2,0),g.placements());assertEquals("winner:1",g.outcome());assertTrue(g.legalActions(2).isEmpty());
    }
    @Test void finishingOnSixAdvancesInsteadOfGrantingAFinishedSeatAnotherTurn()throws Exception{
        LudoGame g=new LudoGame(3,0,new LudoOptions(true,false,true,true));readyToFinish(g,0,6);g.apply(0,"move:3:go0");
        assertFalse(g.finished());assertEquals(List.of(0),g.placements());assertEquals(1,g.currentPlayer());assertEquals(List.of("roll"),g.legalActions(1));
    }
    @Test void noMoveRollAlsoSkipsFinishedSeats()throws Exception{
        long seed=0;while(new SplittableRandom(seed).nextInt(1,7)==6)seed++;
        LudoGame g=new LudoGame(3,seed,new LudoOptions(false,false,true,true));readyToFinish(g,1,1);g.apply(1,"move:7:go1");
        set(g,"current",0);g.apply(0,"roll");assertEquals(2,g.currentPlayer());assertEquals(List.of("roll"),g.legalActions(2));
        assertEquals(List.of(1),g.placements());assertFalse(g.finished());
    }
    @Test void allPlacesWithTwoPlayersFinishesAfterTheFirstWinner()throws Exception{
        LudoGame g=new LudoGame(2,0,new LudoOptions(true,false,true,true));readyToFinish(g,1,1);g.apply(1,"move:7:go2");
        assertTrue(g.finished());assertEquals(List.of(1,0),g.placements());assertEquals("winner:1",g.outcome());
    }
    @Test void firstWinnerModeStillStopsImmediately()throws Exception{
        LudoGame g=new LudoGame(4,0,LudoOptions.DEFAULT);readyToFinish(g,2,1);g.apply(2,"move:11:go2");
        assertTrue(g.finished());assertEquals("winner:2",g.outcome());assertEquals(List.of(2),g.placements());
    }
    @Test void explicitDefaultsReplayTheLegacyConstructorExactly(){
        for(int players=2;players<=4;players++){
            LudoGame legacy=new LudoGame(players,98),explicit=new LudoGame(players,98,LudoOptions.DEFAULT);
            assertEquals("ludo-auto-first-no-blocking-exact-v1",explicit.publicInfo().get("rulesVariant"));
            for(int i=0;i<2000&&!legacy.finished();i++){
                int seat=legacy.currentPlayer();List<String> moves=legacy.legalActions(seat);assertEquals(moves,explicit.legalActions(seat));
                String move=moves.get(i%moves.size());legacy.apply(seat,move);explicit.apply(seat,move);
                assertEquals(legacy.currentPlayer(),explicit.currentPlayer());assertEquals(legacy.outcome(),explicit.outcome());
                assertEquals(legacy.cells(),explicit.cells());assertEquals(legacy.publicInfo(),explicit.publicInfo());
            }
        }
    }
}
