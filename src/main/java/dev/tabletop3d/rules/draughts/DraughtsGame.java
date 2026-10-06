package dev.tabletop3d.rules.draughts;

import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.rules.RuleMessage;
import dev.tabletop3d.rules.RuleViolation;

import dev.tabletop3d.ui.GameSymbols;

import java.util.*;

/** English draughts / American checkers: short kings and compulsory forward captures by men. */
public final class DraughtsGame implements BoardGame {
    @Override
    public List<RuleMessage> messages() {
        return List.of(
                chain < 0
                        ? RuleMessage.of("board.draughts.choose")
                        : RuleMessage.of("board.draughts.chain", "coordinate", key(chain)));
    }

    private final int[] board = new int[64]; // -1 empty; 0/1 men; 2/3 kings
    private final Map<String, Integer> repetitions = new HashMap<>();
    private int turn, chain = -1, quiet;
    private String result = "ongoing";

    public DraughtsGame() {
        Arrays.fill(board, -1);
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                if ((x + y) % 2 == 1) {
                    if (y < 3) board[x + y * 8] = 1;
                    else if (y > 4) board[x + y * 8] = 0;
                }
        remember();
    }

    public String id() {
        return "draughts";
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

    private String key(int i) {
        return i % 8 + "," + i / 8;
    }

    public List<Cell> cells() {
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i < 64; i++)
            out.add(
                    new Cell(
                            key(i),
                            i % 8,
                            i / 8,
                            board[i] < 0 ? "" : board[i] >= 2 ? GameSymbols.KING : GameSymbols.PAWN,
                            board[i] < 0 ? -1 : board[i] % 2));
        return List.copyOf(out);
    }

    private List<String> from(int pos, boolean capture) {
        List<String> out = new ArrayList<>();
        int piece = board[pos];
        if (piece < 0 || piece % 2 != turn) return out;
        for (int dy : new int[] {-1, 1}) {
            if (piece < 2 && dy != (turn == 0 ? -1 : 1)) continue;
            for (int dx : new int[] {-1, 1}) {
                int step = capture ? 2 : 1, x = pos % 8 + dx * step, y = pos / 8 + dy * step;
                if (x < 0 || x >= 8 || y < 0 || y >= 8 || board[x + y * 8] >= 0) continue;
                if (capture) {
                    int mid = board[pos + dx + 8 * dy];
                    if (mid < 0 || mid % 2 == turn) continue;
                }
                out.add("move:" + key(pos) + ":" + key(x + y * 8));
            }
        }
        return out;
    }

    public List<String> legalActions(int seat) {
        if (seat != turn || finished()) return List.of();
        if (chain >= 0) return List.copyOf(from(chain, true));
        List<String> captures = new ArrayList<>(), steps = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            captures.addAll(from(i, true));
            steps.addAll(from(i, false));
        }
        return List.copyOf(captures.isEmpty() ? steps : captures);
    }

    private int pos(String key) {
        String[] xy = key.split(",");
        return Integer.parseInt(xy[0]) + 8 * Integer.parseInt(xy[1]);
    }

    public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action))
            throw new RuleViolation(
                    "error.captures-are-mandatory-continue-capture-chains-with-the-same-piece",
                    "Captures are mandatory; continue capture chains with the same piece");
        String[] a = action.split(":");
        int from = pos(a[1]), to = pos(a[2]), piece = board[from];
        boolean capture = Math.abs(from % 8 - to % 8) == 2;
        board[from] = -1;
        board[to] = piece;
        if (capture) board[(from + to) / 2] = -1;
        quiet = capture || piece < 2 ? 0 : quiet + 1;
        boolean crowned = piece < 2 && (to / 8 == 0 && seat == 0 || to / 8 == 7 && seat == 1);
        if (crowned) board[to] += 2;
        if (capture && !crowned && !from(to, true).isEmpty()) {
            chain = to;
            return;
        }
        chain = -1;
        turn = 1 - seat;
        if (legalActions(turn).isEmpty()) result = "winner:" + seat;
        else if (quiet >= 80) result = "draw:40-moves-without-capture-or-man-move";
        else if (remember() >= 3) result = "draw:threefold-repetition";
    }

    private int remember() {
        return repetitions.merge(turn + Arrays.toString(board), 1, Integer::sum);
    }

    public Map<String, String> publicInfo() {
        return Map.of(
                "rules",
                "8×8 English draughts: mandatory captures; men move and capture forward; short kings move both ways; continue capture chains; crowning ends the turn. Automatic draw on threefold repetition or forty moves each without a capture or man move",
                "rulesVariant",
                "english-draughts-auto-draw",
                "phase",
                chain < 0 ? "Choose a piece and destination" : "Continue capture chain: " + key(chain));
    }
}
