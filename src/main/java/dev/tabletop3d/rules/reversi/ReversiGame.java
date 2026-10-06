package dev.tabletop3d.rules.reversi;

import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.rules.RuleMessage;
import dev.tabletop3d.rules.RuleViolation;
import dev.tabletop3d.ui.GameSymbols;

import java.util.*;

/** 8x8 Othello: mandatory flips, automatic forced pass, count only when neither side can move. */
public final class ReversiGame implements BoardGame {
    private RuleMessage lastMessage = RuleMessage.of("board.ready");

    @Override
    public List<RuleMessage> messages() {
        return List.of(
                lastMessage,
                RuleMessage.of("board.reversi.count", "black", count(0), "white", count(1)));
    }

    private final int[] board = new int[64];
    private int turn;
    private String result = "ongoing", last = "Black moves first";

    public ReversiGame() {
        Arrays.fill(board, -1);
        board[3 + 3 * 8] = board[4 + 4 * 8] = 1;
        board[4 + 3 * 8] = board[3 + 4 * 8] = 0;
    }

    public String id() {
        return "reversi";
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
        for (int i = 0; i < 64; i++)
            cells.add(
                    new Cell(
                            key(i),
                            i % 8,
                            i / 8,
                            board[i] < 0 ? "" : board[i] == 0 ? GameSymbols.BLACK : GameSymbols.WHITE,
                            board[i]));
        return List.copyOf(cells);
    }

    private String key(int i) {
        return i % 8 + "," + i / 8;
    }

    private List<Integer> flips(int pos, int seat) {
        List<Integer> all = new ArrayList<>();
        if (board[pos] >= 0) return all;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                List<Integer> line = new ArrayList<>();
                int x = pos % 8 + dx, y = pos / 8 + dy;
                while (x >= 0 && x < 8 && y >= 0 && y < 8 && board[x + 8 * y] == 1 - seat) {
                    line.add(x + 8 * y);
                    x += dx;
                    y += dy;
                }
                if (x >= 0 && x < 8 && y >= 0 && y < 8 && board[x + 8 * y] == seat)
                    all.addAll(line);
            }
        return all;
    }

    private List<String> moves(int seat) {
        List<String> moves = new ArrayList<>();
        for (int i = 0; i < 64; i++) if (!flips(i, seat).isEmpty()) moves.add("place:" + key(i));
        return moves;
    }

    public List<String> legalActions(int seat) {
        return !finished() && seat == turn ? List.copyOf(moves(seat)) : List.of();
    }

    public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action))
            throw new RuleViolation(
                    "error.you-must-bracket-and-flip-at-least-one-opponent-disc",
                    "You must bracket and flip at least one opponent disc");
        String[] xy = action.substring(6).split(",");
        int pos = Integer.parseInt(xy[0]) + 8 * Integer.parseInt(xy[1]);
        List<Integer> taken = flips(pos, seat);
        board[pos] = seat;
        taken.forEach(i -> board[i] = seat);
        last = (seat == 0 ? "Black" : "White") + " flipped " + taken.size() + " discs";
        lastMessage =
                RuleMessage.of("board.reversi.flipped", "player", seat + 1, "count", taken.size());
        turn = 1 - seat;
        if (moves(turn).isEmpty()) {
            if (moves(seat).isEmpty()) {
                int a = count(0), b = count(1);
                result = a == b ? "draw:equal-discs" : "winner:" + (a > b ? 0 : 1);
            } else {
                turn = seat;
                last += " · opponent has no legal move and passes automatically";
            }
        }
    }

    private int count(int side) {
        return (int) Arrays.stream(board).filter(x -> x == side).count();
    }

    public Map<String, String> publicInfo() {
        return Map.of(
                "rules",
                "8×8 Reversi, Black first; pass automatically when no discs can be flipped; count discs when neither player has a legal move",
                "rulesVariant",
                "othello-8",
                "count",
                "Black " + count(0) + " · White " + count(1),
                "lastAction",
                last);
    }
}
