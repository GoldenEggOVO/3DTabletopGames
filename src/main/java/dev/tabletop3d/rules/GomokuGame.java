package dev.tabletop3d.rules;

import dev.tabletop3d.ui.GameSymbols;

import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * Pure-Java adaptation of NucleoidMC/Gomoku Board (MIT, see META-INF/licenses). The four
 * directional run scans are retained; render code is separated and a winning final placement is
 * checked before a full-board draw.
 */
public final class GomokuGame implements BoardGame {
    private RuleMessage lastMessage = RuleMessage.of("board.ready");

    @Override
    public List<RuleMessage> messages() {
        return List.of(lastMessage, RuleMessage.of("board.gomoku.remaining", "count", empty));
    }

    private static final int SIZE = 15, WIN = 5;
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private final int[][] board = new int[SIZE][SIZE];
    private final GomokuOptions options;
    private List<String> cachedActions;
    private int current, empty = SIZE * SIZE;
    private String result = "ongoing", lastAction = "No stones placed yet";

    public GomokuGame() {
        this(GomokuOptions.DEFAULT);
    }

    public GomokuGame(GomokuOptions options) {
        this.options = Objects.requireNonNull(options);
        for (int[] row : board) Arrays.fill(row, -1);
    }

    public GomokuOptions options() {
        return options;
    }

    @Override
    public String id() {
        return "gomoku";
    }

    @Override
    public int playerCount() {
        return 2;
    }

    @Override
    public int currentPlayer() {
        return current;
    }

    @Override
    public boolean finished() {
        return !result.equals("ongoing");
    }

    @Override
    public String outcome() {
        return result;
    }

    @Override
    public List<Cell> cells() {
        List<Cell> out = new ArrayList<>(SIZE * SIZE);
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) {
                int p = board[y][x];
                out.add(new Cell(x + "," + y, x, y, p == 0 ? GameSymbols.BLACK : p == 1 ? GameSymbols.WHITE : "", p));
            }
        return List.copyOf(out);
    }

    @Override
    public List<String> legalActions(int seat) {
        if (finished() || seat != current) return List.of();
        if (cachedActions != null) return cachedActions;
        List<String> out = new ArrayList<>(empty);
        Map<String, Boolean> forbiddenCache = new HashMap<>();
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++)
                if (board[y][x] == -1) {
                    boolean forbidden = false;
                    if (seat == 0 && !options.equals(GomokuOptions.DEFAULT)) {
                        board[y][x] = 0;
                        forbidden = forbidden(x, y, forbiddenCache);
                        board[y][x] = -1;
                    }
                    if (!forbidden) out.add("place:" + x + "," + y);
                }
        cachedActions = List.copyOf(out);
        return cachedActions;
    }

    @Override
    public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action))
            throw new RuleViolation("error.invalid-placement", "Invalid placement");
        String[] xy = action.substring(6).split(",");
        int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]);
        board[y][x] = seat;
        empty--;
        cachedActions = null;
        lastAction = (seat == 0 ? "Black" : "White") + " placed at " + (x + 1) + "," + (y + 1);
        lastMessage =
                RuleMessage.of(
                        "board.placed", "player", seat + 1, "coordinate", (x + 1) + "," + (y + 1));
        if (checkWin(seat, d -> x + d, d -> y)
                || checkWin(seat, d -> x, d -> y + d)
                || checkWin(seat, d -> x + d, d -> y + d)
                || checkWin(seat, d -> x - d, d -> y + d)) result = "winner:" + seat;
        else if (empty == 0) result = "draw:board-full";
        else current = 1 - current;
    }

    private boolean inside(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    private boolean vacant(int x, int y) {
        return inside(x, y) && board[y][x] == -1;
    }

    private int run(int x, int y, int dx, int dy) {
        int count = 1;
        for (int sign : new int[] {-1, 1}) {
            int px = x + sign * dx, py = y + sign * dy;
            while (inside(px, py) && board[py][px] == 0) {
                count++;
                px += sign * dx;
                py += sign * dy;
            }
        }
        return count;
    }

    private boolean five(int x, int y) {
        for (int[] d : DIRECTIONS) {
            int length = run(x, y, d[0], d[1]);
            if (length == WIN || !options.forbidOverline() && length > WIN) return true;
        }
        return false;
    }

    /** RIF 9.2/9.3: a three must extend to a legal straight four; fake threes do not count. */
    private boolean forbidden(int x, int y, Map<String, Boolean> memo) {
        if (five(x, y)) return false;
        if (options.forbidOverline())
            for (int[] d : DIRECTIONS) if (run(x, y, d[0], d[1]) > WIN) return true;
        if (options.forbidDoubleFour() && fours(x, y) > 1) return true;
        if (!options.forbidDoubleThree()) return false;
        String key = x + "," + y + Arrays.deepToString(board);
        Boolean known = memo.get(key);
        if (known != null) return known;
        boolean result = threes(x, y, memo) > 1;
        memo.put(key, result);
        return result;
    }

    private int fours(int x, int y) {
        Set<List<Integer>> threats = new HashSet<>();
        for (int[] d : DIRECTIONS)
            for (int start = -4; start <= 0; start++) {
                List<Integer> stones = new ArrayList<>(4);
                int gapX = -1, gapY = -1;
                boolean possible = true;
                for (int offset = start; offset < start + 5; offset++) {
                    int px = x + offset * d[0], py = y + offset * d[1];
                    if (!inside(px, py) || board[py][px] == 1) {
                        possible = false;
                        break;
                    }
                    if (board[py][px] == 0) stones.add(px + py * SIZE);
                    else if (gapX >= 0) {
                        possible = false;
                        break;
                    } else {
                        gapX = px;
                        gapY = py;
                    }
                }
                if (!possible || stones.size() != 4 || gapX < 0) continue;
                board[gapY][gapX] = 0;
                boolean completesFive = run(gapX, gapY, d[0], d[1]) == WIN;
                board[gapY][gapX] = -1;
                // A straight four has two winning endpoints but is only one four.
                if (completesFive) threats.add(stones);
            }
        return threats.size();
    }

    private int threes(int x, int y, Map<String, Boolean> memo) {
        Set<List<Integer>> threats = new HashSet<>();
        for (int[] d : DIRECTIONS)
            for (int start = -3; start <= 0; start++) {
                int beforeX = x + (start - 1) * d[0], beforeY = y + (start - 1) * d[1];
                int afterX = x + (start + 4) * d[0], afterY = y + (start + 4) * d[1];
                if (!vacant(beforeX, beforeY) || !vacant(afterX, afterY)) continue;
                List<Integer> stones = new ArrayList<>(3);
                int gapX = -1, gapY = -1;
                boolean possible = true;
                for (int offset = start; offset < start + 4; offset++) {
                    int px = x + offset * d[0], py = y + offset * d[1];
                    if (board[py][px] == 1) {
                        possible = false;
                        break;
                    }
                    if (board[py][px] == 0) stones.add(px + py * SIZE);
                    else if (gapX >= 0) {
                        possible = false;
                        break;
                    } else {
                        gapX = px;
                        gapY = py;
                    }
                }
                if (!possible || stones.size() != 3 || gapX < 0 || threats.contains(stones))
                    continue;
                board[gapY][gapX] = 0;
                board[beforeY][beforeX] = 0;
                boolean beforeWins = run(beforeX, beforeY, d[0], d[1]) == WIN;
                board[beforeY][beforeX] = -1;
                board[afterY][afterX] = 0;
                boolean afterWins = run(afterX, afterY, d[0], d[1]) == WIN;
                board[afterY][afterX] = -1;
                boolean legalExtension =
                        beforeWins
                                && afterWins
                                && !five(gapX, gapY)
                                && !forbidden(gapX, gapY, memo);
                board[gapY][gapX] = -1;
                if (legalExtension) threats.add(stones);
                if (threats.size() > 1) return threats.size();
            }
        return threats.size();
    }

    private boolean checkWin(int piece, IntUnaryOperator x, IntUnaryOperator y) {
        int max = 0, run = 0;
        for (int d = -WIN; d <= WIN; d++) {
            int cx = x.applyAsInt(d), cy = y.applyAsInt(d);
            if (cx < 0 || cy < 0 || cx >= SIZE || cy >= SIZE || board[cy][cx] != piece) run = 0;
            else max = Math.max(max, ++run);
        }
        return max >= WIN;
    }

    @Override
    public Map<String, String> publicInfo() {
        String rules =
                options.equals(GomokuOptions.DEFAULT)
                        ? "15×15 freestyle Gomoku; five or more in a row wins; no forbidden moves"
                        : "15x15 Gomoku; White wins with five or more; Black wins with "
                                + (options.forbidOverline() ? "exactly five" : "five or more")
                                + (options.forbidDoubleThree() ? "; no double threes" : "")
                                + (options.forbidDoubleFour() ? "; no double fours" : "")
                                + (options.forbidOverline() ? "; no overlines" : "");
        return Map.of(
                "rules",
                rules,
                "rulesVariant",
                options.equals(GomokuOptions.DEFAULT) ? "freestyle-15" : "gomoku-15-custom",
                "phase",
                finished() ? "Game finished" : "Waiting for a placement",
                "turn",
                current == 0 ? "Black" : "White",
                "lastAction",
                lastAction,
                "remaining",
                String.valueOf(empty));
    }
}
