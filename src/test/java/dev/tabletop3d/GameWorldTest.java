package dev.tabletop3d;

import dev.tabletop3d.rules.ludo.LudoGame;

import dev.tabletop3d.rules.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GameWorldTest {
    @Test void chessSourceThenDestinationRetainsAllPromotionChoices() {
        List<String> promotions=List.of("move:a7:a8:q","move:a7:a8:r","move:a7:a8:b","move:a7:a8:n");
        GameWorld.Pick pick=new GameWorld.Pick(UUID.randomUUID(),7,"a7",promotions);
        assertEquals(promotions,GameWorld.destinationActions(pick,"a8"));
        assertTrue(GameWorld.destinationActions(pick,"b8").isEmpty());
        BoardGame chess=GameFactory.create("chess",2,0);
        assertEquals(List.of("move:e2:e3","move:e2:e4").stream().sorted().toList(),GameWorld.sourceActions(chess,0,"e2").stream().sorted().toList());
        assertTrue(GameWorld.sourceActions(chess,0,"e7").isEmpty());
    }
    @Test void ludoStackSelectionMatchesPawnIdentityNotCellId() throws Exception {
        long seed=0;while(new SplittableRandom(seed).nextInt(1,7)!=1)seed++;
        BoardGame game=GameFactory.create("ludo",2,seed);
        Field field=LudoGame.class.getDeclaredField("progress");field.setAccessible(true);
        int[] progress=(int[])field.get(game);
        progress[0]=5;progress[1]=5;progress[2]=4;
        game.apply(0,"roll");
        // Selecting a stack excludes a pawn whose destination is the selected cell.
        assertEquals(3,game.actionsForCell(0,"sk5").size());
        List<String> actions=GameWorld.sourceActions(game,0,"sk5");
        assertEquals(List.of("move:0:sk6","move:1:sk6"),actions);
        GameWorld.Pick pick=new GameWorld.Pick(UUID.randomUUID(),1,"sk5",actions);
        assertEquals(2,GameWorld.destinationActions(pick,"sk6").size());
        assertTrue(GameWorld.sourceActions(game,1,"sk5").isEmpty());
    }
    @Test void ludoLaunchWorksThroughWorldCellSelection() {
        long seed=0;while(new SplittableRandom(seed).nextInt(1,7)!=6)seed++;
        BoardGame game=GameFactory.create("ludo",2,seed);game.apply(0,"roll");
        List<String> actions=GameWorld.sourceActions(game,0,"ba0_1");
        assertEquals(List.of("move:1:sk0"),actions);
        assertEquals(actions,GameWorld.destinationActions(new GameWorld.Pick(UUID.randomUUID(),1,"ba0_1",actions),"sk0"));
    }
    @Test void checkersLayoutPreservesEqualLengthHexNeighbors() {
        BoardGame game=GameFactory.create("checkers",6,0);
        GameWorld.Layout layout=GameWorld.layout("checkers",game.cells());
        Cell origin=cell(game,"6,8"),east=cell(game,"7,8"),southEast=cell(game,"6,9");
        double first=Math.hypot(layout.x(east)-layout.x(origin),layout.z(east)-layout.z(origin));
        double second=Math.hypot(layout.x(southEast)-layout.x(origin),layout.z(southEast)-layout.z(origin));
        assertEquals(first,second,1e-6);
        for(Cell c:game.cells()){assertTrue(Math.abs(layout.x(c))<7);assertTrue(Math.abs(layout.z(c))<7);}
    }
    @Test void allBoardsFitTheirTableAndLudoColorsUseTheSeatMapping() {
        for(String kind:List.of("gomoku","xiangqi","chess","ludo","checkers")) {
            BoardGame game=GameFactory.create(kind,2,0);GameWorld.Layout layout=GameWorld.layout(kind,game.cells());
            for(Cell c:game.cells()){assertTrue(Math.abs(layout.x(c))<8);assertTrue(Math.abs(layout.z(c))<8);}
        }
        assertEquals(2,GameWorld.actualColor(Map.of("colors","[0, 2]"),1));
    }
    private static Cell cell(BoardGame game,String id){return game.cells().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow();}
}
