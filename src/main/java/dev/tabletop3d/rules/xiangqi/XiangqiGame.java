package dev.tabletop3d.rules.xiangqi;

import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.rules.RuleMessage;
import dev.tabletop3d.rules.RuleViolation;

import dev.tabletop3d.ui.GameSymbols;

import dev.tabletop3d.rules.xiangqi.engine.model.*;

import java.util.*;

/** Room-safe adapter around James Wang's MIT Xiangqi model. */
public final class XiangqiGame implements BoardGame {
    private RuleMessage lastMessage = RuleMessage.of("board.ready");

    @Override
    public List<RuleMessage> messages() {
        return List.of(lastMessage);
    }

    private final Board board;
    private String result = "ongoing", lastAction = "Red moves first", reason = "";
    private int noCapturePly;
    private final Map<String, List<Integer>> positions = new HashMap<>();
    private final List<CheckRecord> checks = new ArrayList<>();
    private Map<String, Move> cached;

    private record CheckRecord(int seat, boolean check) {}

    public XiangqiGame() {
        this(Board.initialPosition());
    }

    // Only package tests can inject positions; production always starts with a normal board.
    XiangqiGame(Board position) {
        board = position.copy();
        positions.put(positionKey(), new ArrayList<>(List.of(0)));
        if (moves().isEmpty()) {
            result = "winner:" + (1 - currentPlayer());
            reason = "No legal move loses";
        }
    }

    @Override
    public String id() {
        return "xiangqi";
    }

    @Override
    public int playerCount() {
        return 2;
    }

    @Override
    public int currentPlayer() {
        return board.turn() % 2;
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
        List<Cell> cells = new ArrayList<>(90);
        for (int row = 0; row < 10; row++)
            for (int col = 0; col < 9; col++) {
                Piece p = board.pieceAt(row, col);
                cells.add(
                        new Cell(
                                col + "," + row,
                                col,
                                row,
                                p == null ? "" : symbol(p),
                                p == null ? -1 : p.getColor()));
            }
        return List.copyOf(cells);
    }

    private static String symbol(Piece piece) {
        return switch (Math.abs(piece.getCode())) {
            case 1 -> piece.getColor() == 0 ? GameSymbols.RED_GENERAL : GameSymbols.BLACK_GENERAL;
            case 2 -> piece.getColor() == 0 ? GameSymbols.RED_ADVISOR : GameSymbols.BLACK_ADVISOR;
            case 3 -> piece.getColor() == 0 ? GameSymbols.RED_ELEPHANT : GameSymbols.ELEPHANT;
            case 4 -> GameSymbols.HORSE;
            case 5 -> GameSymbols.ROOK;
            case 6 -> piece.getColor() == 0 ? GameSymbols.RED_CANNON : GameSymbols.BLACK_CANNON;
            case 7 -> piece.getColor() == 0 ? GameSymbols.PAWN : GameSymbols.BLACK_PAWN;
            default -> throw new IllegalStateException("Unknown piece");
        };
    }

    private Map<String, Move> moves() {
        if (cached == null) {
            Map<String, Move> map = new LinkedHashMap<>();
            for (Move move : board.legalMoves()) map.put(action(move), move);
            cached = Collections.unmodifiableMap(map);
        }
        return cached;
    }

    private static String action(Move move) {
        return "move:" + move.start.y + "," + move.start.x + ":" + move.end.y + "," + move.end.x;
    }

    @Override
    public List<String> legalActions(int seat) {
        return finished() || seat != currentPlayer() ? List.of() : List.copyOf(moves().keySet());
    }

    @Override
    public void apply(int seat, String action) {
        if (finished() || seat != currentPlayer() || action == null || !moves().containsKey(action))
            throw new RuleViolation("error.invalid-move", "Invalid move");
        Move move = moves().get(action);
        noCapturePly = move.target == null ? noCapturePly + 1 : 0;
        lastAction = (seat == 0 ? "Red " : "Black ") + symbol(move.piece) + " " + action.substring(5);
        lastMessage = RuleMessage.of("board.move", "player", seat + 1, "move", action.substring(5));
        board.move(move);
        cached = null;
        checks.add(new CheckRecord(seat, inCheck(currentPlayer())));
        if (moves().isEmpty()) {
            result = "winner:" + seat;
            reason = "Checkmate or stalemate";
            return;
        }
        List<Integer> occurrences =
                positions.computeIfAbsent(positionKey(), ignored -> new ArrayList<>());
        occurrences.add(checks.size());
        if (occurrences.size() >= 3) adjudicateRepetition(occurrences.get(occurrences.size() - 3));
        if (!finished() && noCapturePly >= 120) {
            result = "draw:120-ply-no-capture";
            reason = "Draw after sixty rounds without a capture";
        }
    }

    private void adjudicateRepetition(int fromPly) {
        boolean[] allChecking = {true, true};
        int[] count = {0, 0};
        for (int i = fromPly; i < checks.size(); i++) {
            CheckRecord check = checks.get(i);
            count[check.seat()]++;
            allChecking[check.seat()] &= check.check();
        }
        for (int i = 0; i < 2; i++) allChecking[i] &= count[i] >= 2;
        if (allChecking[0] != allChecking[1]) {
            int checkingSeat = allChecking[0] ? 0 : 1;
            result = "winner:" + (1 - checkingSeat);
            reason = (checkingSeat == 0 ? "Red" : "Black") + " loses for perpetual check";
        } else {
            result = "draw:threefold-repetition";
            reason = "Draw by threefold repetition";
        }
    }

    private boolean inCheck(int seat) {
        Board attack = board.copy();
        attack.setTurn(1 - seat);
        int kingCode = seat == 0 ? 1 : -1;
        return attack.candidateMoves().stream()
                .anyMatch(m -> m.target != null && m.target.getCode() == kingCode);
    }

    private String positionKey() {
        StringBuilder key = new StringBuilder().append(currentPlayer()).append(':');
        for (int row = 0; row < 10; row++)
            for (int col = 0; col < 9; col++) {
                Piece p = board.pieceAt(row, col);
                key.append((char) ('h' + (p == null ? 0 : p.getCode())));
            }
        return key.toString();
    }

    @Override
    public Map<String, String> publicInfo() {
        return Map.of(
                "rules",
                "Xiangqi; stalemate loses; threefold repetition draws, perpetual check loses; sixty rounds without a capture draws",
                "rulesVariant",
                "casual-threefold-perpetual-check",
                "ruleLimit",
                "Casual rules: perpetual chase uses threefold repetition; tournament chase adjudication is not implemented",
                "phase",
                finished() ? "Game finished" : inCheck(currentPlayer()) ? "In check; respond to the check" : "Waiting for a move",
                "turn",
                currentPlayer() == 0 ? "Red" : "Black",
                "lastAction",
                lastAction,
                "resultReason",
                reason);
    }
}
