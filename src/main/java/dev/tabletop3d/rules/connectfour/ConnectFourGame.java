package dev.tabletop3d.rules.connectfour;

import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.rules.RuleViolation;
import dev.tabletop3d.ui.GameSymbols;

import java.util.*;

/** Standard 7-column, 6-row gravity Connect Four. Row zero is the bottom. */
public final class ConnectFourGame implements BoardGame {
    private final int[][] board = new int[6][7];
    private final int[] heights = new int[7];
    private int turn, moves;
    private String result = "ongoing";

    public ConnectFourGame() {
        for (int[] row : board) Arrays.fill(row, -1);
    }

    public String id() {
        return "connectfour";
    }

    public int playerCount() {
        return 2;
    }

    public int currentPlayer() {
        return turn;
    }

    public boolean finished() {
        return !result.equals("ongoing");
    }

    public String outcome() {
        return result;
    }

    public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y < 6; y++)
            for (int x = 0; x < 7; x++)
                cells.add(
                        new Cell(
                                x + "," + y,
                                x,
                                y,
                                board[y][x] < 0 ? "" : board[y][x] == 0 ? GameSymbols.RED : GameSymbols.YELLOW,
                                board[y][x]));
        return List.copyOf(cells);
    }

    public List<String> legalActions(int seat) {
        if (finished() || seat != turn) return List.of();
        List<String> actions = new ArrayList<>();
        for (int x = 0; x < 7; x++) if (heights[x] < 6) actions.add("drop:" + x);
        return List.copyOf(actions);
    }

    public List<String> actionsForCell(int seat, String cell) {
        if (cell == null || !cell.matches("[0-6],[0-5]")) return List.of();
        String action = "drop:" + cell.charAt(0);
        return legalActions(seat).contains(action) ? List.of(action) : List.of();
    }

    public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action))
            throw new RuleViolation(
                    "error.invalid-drop-choose-a-column-that-is-not-full", "Invalid drop: choose a column that is not full");
        int x = action.charAt(5) - '0', y = heights[x]++;
        board[y][x] = seat;
        moves++;
        for (int[] d : new int[][] {{1, 0}, {0, 1}, {1, 1}, {1, -1}})
            if (1 + count(x, y, d[0], d[1], seat) + count(x, y, -d[0], -d[1], seat) >= 4) {
                result = "winner:" + seat;
                return;
            }
        if (moves == 42) result = "draw:board-full";
        else turn = 1 - turn;
    }

    private int count(int x, int y, int dx, int dy, int seat) {
        int n = 0;
        for (x += dx, y += dy;
                x >= 0 && x < 7 && y >= 0 && y < 6 && board[y][x] == seat;
                x += dx, y += dy) n++;
        return n;
    }

    public Map<String, String> publicInfo() {
        return Map.of("rules", "Vertical 7×6 board; click a column to drop a disc; four in a horizontal, vertical or diagonal line wins", "turn", turn == 0 ? "Red" : "Yellow");
    }
}
