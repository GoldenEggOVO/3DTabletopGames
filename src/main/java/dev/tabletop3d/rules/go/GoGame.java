package dev.tabletop3d.rules.go;

import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.rules.RuleMessage;
import dev.tabletop3d.rules.RuleViolation;
import dev.tabletop3d.ui.GameSymbols;

import java.util.*;

/**
 * Area-scoring Go: no suicide, positional superko, and an explicit two-player dead-stone agreement.
 */
public final class GoGame implements BoardGame {
    private RuleMessage lastMessage = RuleMessage.of("board.ready");

    @Override
    public List<RuleMessage> messages() {
        List<RuleMessage> messages = new ArrayList<>();
        messages.add(lastMessage);
        messages.add(
                RuleMessage.of("board.go.captures", "black", prisoners[0], "white", prisoners[1]));
        if (scoring || finished()) {
            double[] points = score();
            messages.add(
                    RuleMessage.of(
                            "board.go.score",
                            "black",
                            points[0],
                            "white",
                            points[1],
                            "dead",
                            dead.size()));
        }
        if (scoring)
            messages.add(
                    RuleMessage.of(
                            "board.go.confirmation", "black", accepted[0], "white", accepted[1]));
        return List.copyOf(messages);
    }

    private final int size;
    private int[] board;
    private final Set<String> seen = new HashSet<>();
    private final Set<Integer> dead = new HashSet<>();
    private final boolean[] accepted = new boolean[2];
    private final int[] prisoners = new int[2];
    private int turn, passes, playTurnBeforeScoring;
    private boolean scoring;
    private String result = "ongoing", last = "Black moves first";
    private List<String> cachedMoves;

    public GoGame(int size) {
        if (!Set.of(9, 13, 19).contains(size))
            throw new RuleViolation("error.go-supports-9-9-13-13-and-19-19", "Go supports 9×9, 13×13, and 19×19");
        this.size = size;
        board = new int[size * size];
        Arrays.fill(board, -1);
        seen.add(position(board));
    }

    public String id() {
        return size == 19 ? "go" : "go" + size;
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

    public boolean scoring() {
        return scoring;
    }

    public int size() {
        return size;
    }

    private String key(int i) {
        return i % size + "," + i / size;
    }

    private String position(int[] state) {
        StringBuilder b = new StringBuilder(state.length);
        for (int p : state) b.append((char) ('0' + p + 1));
        return b.toString();
    }

    public List<Cell> cells() {
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i < board.length; i++)
            out.add(
                    new Cell(
                            key(i),
                            i % size,
                            i / size,
                            board[i] < 0
                                    ? ""
                                    : (board[i] == 0 ? GameSymbols.BLACK : GameSymbols.WHITE) + (dead.contains(i) ? "×" : ""),
                            board[i]));
        return List.copyOf(out);
    }

    private int[] neighbors(int p) {
        int[] out = new int[4];
        int n = 0;
        if (p % size > 0) out[n++] = p - 1;
        if (p % size < size - 1) out[n++] = p + 1;
        if (p >= size) out[n++] = p - size;
        if (p < board.length - size) out[n++] = p + size;
        return Arrays.copyOf(out, n);
    }

    private record Group(Set<Integer> stones, Set<Integer> liberties) {}

    private Group group(int[] state, int start) {
        Set<Integer> stones = new HashSet<>(), liberties = new HashSet<>();
        ArrayDeque<Integer> todo = new ArrayDeque<>();
        todo.add(start);
        stones.add(start);
        while (!todo.isEmpty()) {
            int p = todo.removeFirst();
            for (int q : neighbors(p)) {
                if (state[q] < 0) liberties.add(q);
                else if (state[q] == state[start] && stones.add(q)) todo.add(q);
            }
        }
        return new Group(stones, liberties);
    }

    private int[] placed(int pos, int side) {
        if (board[pos] >= 0) return null;
        int[] next = board.clone();
        next[pos] = side;
        for (int q : neighbors(pos))
            if (next[q] == 1 - side) {
                Group g = group(next, q);
                if (g.liberties.isEmpty()) g.stones.forEach(i -> next[i] = -1);
            }
        if (group(next, pos).liberties.isEmpty() || seen.contains(position(next))) return null;
        return next;
    }

    public List<String> legalActions(int seat) {
        if (finished() || seat < 0 || seat > 1) return List.of();
        if (scoring) {
            List<String> out = new ArrayList<>();
            if (!accepted[seat]) out.add("accept");
            out.add("resume");
            for (int i = 0; i < board.length; i++) if (board[i] >= 0) out.add("dead:" + key(i));
            return List.copyOf(out);
        }
        if (seat != turn) return List.of();
        if (cachedMoves == null) {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < board.length; i++)
                if (placed(i, seat) != null) out.add("place:" + key(i));
            out.add("pass");
            cachedMoves = List.copyOf(out);
        }
        return cachedMoves;
    }

    private int parse(String value) {
        String[] xy = value.split(",");
        return Integer.parseInt(xy[0]) + size * Integer.parseInt(xy[1]);
    }

    public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action))
            throw new RuleViolation(
                    "error.invalid-move-check-turn-liberties-suicide-or-repeated-position",
                    "Invalid move: check turn, liberties, suicide, or repeated position");
        cachedMoves = null;
        if (scoring) {
            if (action.equals("resume")) {
                scoring = false;
                turn = playTurnBeforeScoring;
                dead.clear();
                Arrays.fill(accepted, false);
                passes = 0;
                last = "Play resumed; resolve disputed groups by continuing play";
                lastMessage = RuleMessage.of("board.go.resumed");
                return;
            }
            if (action.startsWith("dead:")) {
                Set<Integer> stones = group(board, parse(action.substring(5))).stones;
                if (dead.containsAll(stones)) dead.removeAll(stones);
                else dead.addAll(stones);
                Arrays.fill(accepted, false);
                last = "Dead group markings changed; both players must confirm again";
                lastMessage = RuleMessage.of("board.go.dead-adjusted");
                return;
            }
            accepted[seat] = true;
            turn = 1 - seat;
            if (accepted[0] && accepted[1]) {
                double[] score = score();
                result = score[0] > score[1] ? "winner:0" : "winner:1";
                last = "Both players confirmed the final score";
                lastMessage = RuleMessage.of("board.go.agreed");
            }
            return;
        }
        if (action.equals("pass")) {
            passes++;
            turn = 1 - turn;
            last = (seat == 0 ? "Black" : "White") + " passed";
            lastMessage = RuleMessage.of("board.passed", "player", seat + 1);
            if (passes >= 2) {
                scoring = true;
                playTurnBeforeScoring = turn;
                Arrays.fill(accepted, false);
                last = "Both players passed: mark dead groups and confirm the score; resume play to resolve disputes";
                lastMessage = RuleMessage.of("board.go.scoring");
            }
            return;
        }
        int pos = parse(action.substring(6));
        int[] next = placed(pos, seat);
        if (next == null)
            throw new RuleViolation("error.repeated-position-or-no-liberties", "Repeated position or no liberties");
        for (int i = 0; i < board.length; i++)
            if (board[i] == 1 - seat && next[i] < 0) prisoners[seat]++;
        board = next;
        seen.add(position(board));
        passes = 0;
        turn = 1 - seat;
        last = (seat == 0 ? "Black" : "White") + " placed at " + key(pos);
        lastMessage = RuleMessage.of("board.placed", "player", seat + 1, "coordinate", key(pos));
    }

    /** Both colours count stones plus surrounded empty points; mixed boundaries remain neutral. */
    public double[] score() {
        int[] state = board.clone();
        dead.forEach(i -> state[i] = -1);
        double[] score = {0, 7.5};
        boolean[] checked = new boolean[state.length];
        for (int i = 0; i < state.length; i++) {
            if (state[i] >= 0) {
                score[state[i]]++;
                continue;
            }
            if (checked[i]) continue;
            ArrayDeque<Integer> todo = new ArrayDeque<>();
            todo.add(i);
            checked[i] = true;
            int points = 0, border = 0;
            while (!todo.isEmpty()) {
                int p = todo.removeFirst();
                points++;
                for (int q : neighbors(p)) {
                    if (state[q] >= 0) border |= 1 << state[q];
                    else if (!checked[q]) {
                        checked[q] = true;
                        todo.add(q);
                    }
                }
            }
            if (border == 1) score[0] += points;
            else if (border == 2) score[1] += points;
        }
        return score;
    }

    public Map<String, String> publicInfo() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("rules", size + "-line board with area scoring and 7.5 komi for White; no suicide and positional superko; after two passes, both players mark dead groups and confirm; resume play to resolve disputes");
        out.put("rulesVariant", "area-psk-komi7.5-agreement");
        out.put(
                "phase",
                finished()
                        ? "Finished"
                        : scoring
                                ? "Scoring agreement · Black "
                                        + (accepted[0] ? "confirmed" : "pending")
                                        + " / White "
                                        + (accepted[1] ? "confirmed" : "pending")
                                : "Playing");
        out.put("lastAction", last);
        out.put("captures", "Black captures " + prisoners[0] + " · White captures " + prisoners[1]);
        if (scoring || finished()) {
            double[] s = score();
            out.put("score", "Area: Black " + s[0] + " · White " + s[1] + " (including komi) · marked dead stones " + dead.size());
        }
        return out;
    }
}
