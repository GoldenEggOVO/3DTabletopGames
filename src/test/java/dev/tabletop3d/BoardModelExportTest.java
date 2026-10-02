package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.tabletop3d.rules.GameFactory;
import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.ui.GameSymbols;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Export the same board coordinates/art used by native rendering for the pack builder. */
class BoardModelExportTest {
    @Test
    void exportOriginalBoardsAndPieceMeshesAtTheirNativeCoordinates() throws Exception {
        Path output = Path.of("target/board-model-source");
        Files.createDirectories(output);
        Map<String,Object> meshes = new TreeMap<>();
        for (String kind : List.of("chess", "xiangqi", "gomoku", "go", "go9", "go13",
                "reversi", "draughts", "checkers", "ludo")) {
            var game = GameFactory.create(kind, kind.equals("checkers") ? 6 : 2, 0);
            var geometry = new TableGeometry(kind, game.cells());
            var art = TableArt.draw(geometry);
            assertEquals(256, art.getWidth());
            assertEquals(256, art.getHeight());
            ImageIO.write(art,"png",output.resolve(kind + ".png").toFile());
            for (Cell cell : game.cells()) if (cell.owner() >= 0) {
                String id = PackedBoardModels.piece(kind, cell, game.publicInfo());
                List<TableModels.Part> parts = kind.equals("reversi")
                        ? TableModels.reversi() : TableModels.piece(kind, cell, game.publicInfo());
                assertFalse(parts.isEmpty(), id);
                assertTrue(parts.stream().allMatch(p -> p.w()>0 && p.h()>0 && p.d()>0), id);
                meshes.put(id,parts);
            }
        }
        for (int side=0;side<2;side++) {
            Cell stone = new Cell("a",0,0,"",side);
            meshes.put(PackedBoardModels.piece("go",stone,Map.of()),TableModels.piece("go",stone,Map.of()));
            Cell dead = new Cell("a",0,0,"×",side);
            meshes.put(PackedBoardModels.piece("go",dead,Map.of()),TableModels.piece("go",dead,Map.of()));
            for (String glyph : List.of(GameSymbols.PAWN,GameSymbols.HORSE,GameSymbols.ELEPHANT,
                    GameSymbols.ROOK,GameSymbols.QUEEN,GameSymbols.KING)) {
                Cell cell = new Cell("a",0,0,glyph,side);
                meshes.put(PackedBoardModels.piece("chess",cell,Map.of()),TableModels.piece("chess",cell,Map.of()));
            }
            Cell king = new Cell("a",0,0,GameSymbols.KING,side);
            meshes.put(PackedBoardModels.piece("draughts",king,Map.of()),TableModels.piece("draughts",king,Map.of()));
            meshes.put("connectfour_" + (side==0 ? "red" : "yellow"),TableModels.connectFour(side));
        }
        for (int color=0;color<6;color++)
            meshes.put("peg_"+PackedBoardModels.COLORS.get(color),TableModels.piece("checkers",new Cell("a",0,0,"1",color),Map.of()));
        for (int color=0;color<6;color++)
            meshes.put("ludo_"+PackedBoardModels.COLORS.get(color),TableModels.piece("ludo",new Cell("a",0,0,"1",color),Map.of()));
        Files.writeString(output.resolve("meshes.json"),new Gson().toJson(meshes),StandardCharsets.UTF_8);
        Files.writeString(output.resolve("model-ids.json"),new Gson().toJson(CraftEngineModels.IDS),StandardCharsets.UTF_8);
    }
}
