package dev.tabletop3d.rules.gomoku;

import dev.tabletop3d.rules.BoardGame;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GomokuGameTest {
    private static GomokuGame configured(boolean three, boolean four, boolean overline) throws Exception {
        return new GomokuGame(new GomokuOptions(three, four, overline));
    }
    private static void position(GomokuGame game, int seat, int[][] black, int[][] white) throws Exception {
        var field = GomokuGame.class.getDeclaredField("board"); field.setAccessible(true);
        int[][] board = (int[][]) field.get(game);
        for (int[] row : board) Arrays.fill(row, -1);
        for (int[] p : black) board[p[1]][p[0]] = 0;
        for (int[] p : white) board[p[1]][p[0]] = 1;
        field = GomokuGame.class.getDeclaredField("current"); field.setAccessible(true); field.setInt(game, seat);
    }
    @Test void doubleThreeIsForbiddenOnlyWhenEnabledForBlack() throws Exception {
        int[][] cross = {{6,7},{8,7},{7,6},{7,8}};
        GomokuGame forbidden = configured(true, false, false);
        position(forbidden, 0, cross, new int[0][]);
        assertFalse(forbidden.legalActions(0).contains("place:7,7"));
        assertThrows(IllegalArgumentException.class, () -> forbidden.apply(0, "place:7,7"));
        GomokuGame allowed = configured(false, true, true);
        position(allowed, 0, cross, new int[0][]);
        assertTrue(allowed.legalActions(0).contains("place:7,7"));
        GomokuGame white = configured(true, true, true);
        position(white, 1, new int[0][], cross);
        assertTrue(white.legalActions(1).contains("place:7,7"));
    }
    @Test void edgeAndBlockedThreesDoNotProduceFalseDoubleThree() throws Exception {
        GomokuGame edge = configured(true, true, true);
        position(edge, 0, new int[][]{{0,7},{2,7},{1,6},{1,8}}, new int[0][]);
        assertTrue(edge.legalActions(0).contains("place:1,7"));
        GomokuGame blocked = configured(true, true, true);
        position(blocked, 0, new int[][]{{6,7},{8,7},{7,6},{7,8}}, new int[][]{{4,7},{10,7}});
        // A white stone at each outer extension still leaves a straight open four impossible.
        assertTrue(blocked.legalActions(0).contains("place:7,7"));
    }
    @Test void brokenOpenThreeCountsButAnOpenFourIsOneFour() throws Exception {
        GomokuGame broken = configured(true, false, false);
        position(broken, 0, new int[][]{{5,7},{8,7},{7,6},{7,8}}, new int[0][]);
        assertFalse(broken.legalActions(0).contains("place:7,7"));
        GomokuGame oneFour = configured(false, true, false);
        position(oneFour, 0, new int[][]{{5,7},{6,7},{8,7}}, new int[0][]);
        assertTrue(oneFour.legalActions(0).contains("place:7,7"));
    }
    @Test void twoFoursCanExistInOneDirection() throws Exception {
        GomokuGame game = configured(false, true, false);
        position(game, 0, new int[][]{{3,7},{5,7},{6,7},{9,7}}, new int[0][]);
        assertFalse(game.legalActions(0).contains("place:7,7"));
    }
    @Test void aThreeWhoseExtensionsAreOverlinesDoesNotCount() throws Exception {
        List<int[]> black = new ArrayList<>(List.of(new int[]{6,7},new int[]{8,7},new int[]{7,6},new int[]{7,8}));
        for (int x : new int[]{5,9}) for (int y : new int[]{4,5,6,8,9}) black.add(new int[]{x,y});
        GomokuGame game = configured(true, true, true);
        position(game, 0, black.toArray(int[][]::new), new int[0][]);
        assertTrue(game.legalActions(0).contains("place:7,7"));
    }
    @Test void aThreeWhoseExtensionsAreDoubleFoursDoesNotCount() throws Exception {
        List<int[]> black = new ArrayList<>(List.of(new int[]{6,7},new int[]{8,7},new int[]{7,6},new int[]{7,8}));
        for (int x : new int[]{5,9}) for (int y : new int[]{5,6,8}) black.add(new int[]{x,y});
        GomokuGame game = configured(true, true, true);
        position(game, 0, black.toArray(int[][]::new), new int[0][]);
        assertTrue(game.legalActions(0).contains("place:7,7"));
        GomokuGame noFourRestriction = configured(true, false, true);
        position(noFourRestriction, 0, black.toArray(int[][]::new), new int[0][]);
        assertFalse(noFourRestriction.legalActions(0).contains("place:7,7"));
    }
    @Test void aThreeWhoseExtensionsAreRecursiveDoubleThreesDoesNotCount() throws Exception {
        int[][] black = {{6,7},{8,7},{7,6},{7,8},{5,6},{5,8},{3,5},{4,6},{9,6},{9,8},{11,5},{10,6}};
        GomokuGame game = configured(true, false, false);
        position(game, 0, black, new int[0][]);
        assertTrue(game.legalActions(0).contains("place:7,7"));
        int[][] withCenter = Arrays.copyOf(black, black.length + 1);
        withCenter[black.length] = new int[]{7,7};
        GomokuGame extensions = configured(true, false, false);
        position(extensions, 0, withCenter, new int[0][]);
        assertFalse(extensions.legalActions(0).contains("place:5,7"));
        assertFalse(extensions.legalActions(0).contains("place:9,7"));
    }
    @Test void overlineOptionAndExactFivePriorityAreApplied() throws Exception {
        int[][] fiveWithGap = {{3,7},{4,7},{5,7},{6,7},{8,7}};
        GomokuGame forbidden = configured(false, false, true);
        position(forbidden, 0, fiveWithGap, new int[0][]);
        assertFalse(forbidden.legalActions(0).contains("place:7,7"));
        GomokuGame allowed = configured(false, false, false);
        position(allowed, 0, fiveWithGap, new int[0][]);
        allowed.apply(0, "place:7,7"); assertEquals("winner:0", allowed.outcome());
        GomokuGame win = configured(true, true, true);
        position(win, 0, new int[][]{{3,7},{4,7},{5,7},{6,7},{7,5},{7,6},{7,8},{5,5},{6,6},{8,8}}, new int[0][]);
        win.apply(0, "place:7,7"); assertEquals("winner:0", win.outcome());
    }
    @Test void fourDirectionsAndBoardEdgesWin() {
        int[][] directions = {{1,0},{0,1},{1,1},{-1,1}};
        for (int[] d : directions) {
            BoardGame game = new GomokuGame();
            for (int k = 0; k < 5; k++) {
                int x = d[0] < 0 ? 14 - k : k * d[0], y = k * d[1];
                game.apply(0, "place:" + x + "," + y);
                if (k < 4) game.apply(1, "place:" + (8 + k) + ",13");
            }
            assertEquals("winner:0", game.outcome());
            assertTrue(game.legalActions(0).isEmpty());
        }
    }
    @Test void occupiedCellAndExtraMoveAfterWinRejected() {
        BoardGame game = new GomokuGame();
        game.apply(0, "place:0,0");
        assertThrows(IllegalArgumentException.class, () -> game.apply(1, "place:0,0"));
        assertEquals(List.of("place:1,0"), game.actionsForCell(1, "1,0"));
    }
    @Test void winning225thPlacementBeatsFullBoardDraw() {
        // Balanced no-win fixture: every prefix is legal; last black move bridges an overline.
        String[] rows = {"001100110011001","110011001100110","001100110011001","110011101100110",
                "001100110011001","110011001100110","001100110011001","110010000.00110",
                "101100110011001","110011001100110","001100110011001","110011001100110",
                "001100110011001","110011001100110","001101110011001"};
        List<String> black = new ArrayList<>(), white = new ArrayList<>();
        for (int y = 0; y < 15; y++) for (int x = 0; x < 15; x++) {
            if (rows[y].charAt(x) == '0') black.add("place:" + x + "," + y);
            if (rows[y].charAt(x) == '1') white.add("place:" + x + "," + y);
        }
        assertEquals(112, black.size()); assertEquals(112, white.size());
        BoardGame game = new GomokuGame();
        for (int i = 0; i < 112; i++) {
            game.apply(0, black.get(i)); game.apply(1, white.get(i));
            assertFalse(game.finished(), "fixture must not have an earlier win");
        }
        game.apply(0, "place:9,7");
        assertEquals("winner:0", game.outcome());
        assertEquals("0", game.publicInfo().get("remaining"));
    }
}
