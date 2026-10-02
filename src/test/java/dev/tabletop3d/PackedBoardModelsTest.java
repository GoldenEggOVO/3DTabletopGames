package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;

import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.rules.GameFactory;
import dev.tabletop3d.ui.GameSymbols;
import java.util.*;
import org.junit.jupiter.api.Test;

class PackedBoardModelsTest {
    @Test
    void catalogueBoardsAndEveryInitialPieceHaveRegisteredModels() {
        for (String kind : List.of("chess", "connectfour", "xiangqi", "gomoku", "go", "go9",
                "go13", "reversi", "draughts", "checkers", "ludo")) {
            assertTrue(PackedBoardModels.supported(kind), kind);
            assertTrue(TabletopPack.supported(kind), kind);
            assertTrue(CraftEngineModels.IDS.contains(PackedBoardModels.table(kind)), kind);
            var game = GameFactory.create(kind, kind.equals("checkers") ? 6 : 2, 0);
            for (Cell cell : game.cells()) if (cell.owner() >= 0)
                assertTrue(CraftEngineModels.IDS.contains(
                        PackedBoardModels.piece(kind, cell, game.publicInfo())), kind + " " + cell);
        }
    }

    @Test
    void crownsSidesAndDynamicSeatColorsAreDistinct() {
        assertNotEquals(PackedBoardModels.piece("draughts", new Cell("a",0,0,GameSymbols.PAWN,0), Map.of()),
                PackedBoardModels.piece("draughts", new Cell("a",0,0,GameSymbols.KING,0), Map.of()));
        assertNotEquals(PackedBoardModels.piece("chess", new Cell("a",0,0,GameSymbols.KING,0), Map.of()),
                PackedBoardModels.piece("chess", new Cell("a",0,0,GameSymbols.KING,1), Map.of()));
        for (String color : PackedBoardModels.COLORS)
            assertTrue(CraftEngineModels.IDS.contains("peg_" + color));
    }
}
