package dev.tabletop3d;

import com.google.gson.JsonPrimitive;
import dev.tabletop3d.rules.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoundActionsTest {
    Room room(String kind,boolean human){Room r=new Room(UUID.randomUUID(),kind,2,41,3);r.join(UUID.randomUUID(),"first");if(human)r.join(UUID.randomUUID(),"second");else r.fillBots();r.board=GameFactory.create(kind,2,r.seed);r.phase=Room.Phase.PLAYING;return r;}
    void move(Room r,String action){int seat=r.board.currentPlayer();r.board.apply(seat,action);r.event(seat,new JsonPrimitive(action));r.revision++;}
    @Test void rematchNeedsAllHumansPreservesTableAndGetsNewSeed(){var r=room("reversi",true);r.phase=Room.Phase.FINISHED;r.completed=true;r.result="winner:0";r.history.add(new JsonPrimitive("old"));long revision=r.revision;assertFalse(RoundActions.rematchReady(r,r.seats.getFirst().id()));assertThrows(IllegalArgumentException.class,()->RoundActions.fresh(r,90));assertTrue(RoundActions.rematchReady(r,r.seats.getLast().id()));RoundActions.fresh(r,90);assertEquals(3,r.table);assertEquals(2,r.seats.size());assertEquals(90,r.seed);assertTrue(r.history.isEmpty());assertNull(r.board);assertFalse(r.completed);assertEquals(Room.Phase.LOBBY,r.phase);assertTrue(r.revision>revision);}
    @Test void diceBotsFinishEveryRoundWithoutHoldLoops(){var g=new YachtGame(4,6);Random random=new Random(12);int steps=0;while(!g.finished()&&steps++<1500){int seat=g.currentPlayer();String action=BoardBots.choose(g,seat,random);assertTrue(g.legalActions(seat).contains(action));g.apply(seat,action);}assertTrue(g.finished(),"steps="+steps);}
}
