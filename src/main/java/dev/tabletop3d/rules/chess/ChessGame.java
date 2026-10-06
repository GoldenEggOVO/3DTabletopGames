package dev.tabletop3d.rules.chess;

import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.rules.RuleMessage;
import dev.tabletop3d.rules.RuleViolation;

import dev.tabletop3d.ui.GameSymbols;

import dev.tabletop3d.rules.chess.engine.Board;
import dev.tabletop3d.rules.chess.engine.Piece;
import dev.tabletop3d.rules.chess.engine.PieceType;
import dev.tabletop3d.rules.chess.engine.Side;
import dev.tabletop3d.rules.chess.engine.Square;
import dev.tabletop3d.rules.chess.engine.move.Move;

import java.util.*;

/** Standard chess using the Apache-2.0 chesslib move generator, without Stockfish. */
public final class ChessGame implements BoardGame {
    private RuleMessage lastMessage = RuleMessage.of("board.ready");

    @Override
    public List<RuleMessage> messages() {
        return List.of(lastMessage);
    }

    private final Board board = new Board();
    private String result = "ongoing", lastAction = "White moves first", reason = "";
    private Map<String, Move> cached;

    public ChessGame() {}

    ChessGame(String fen) {
        board.loadFromFen(fen);
        updateResult();
    }

    @Override
    public String id() {
        return "chess";
    }

    @Override
    public int playerCount() {
        return 2;
    }

    @Override
    public int currentPlayer() {
        return board.getSideToMove() == Side.WHITE ? 0 : 1;
    }

    @Override
    public boolean finished() {
        return !result.equals("ongoing");
    }

    @Override
    public String outcome() {
        return result;
    }

    private static String id(Square square) {
        return square.name().toLowerCase(Locale.ROOT);
    }

    private static String action(Move move) {
        String action = "move:" + id(move.getFrom()) + ":" + id(move.getTo());
        return move.getPromotion() == Piece.NONE
                ? action
                : action + ":" + move.getPromotion().getFenSymbol().toLowerCase(Locale.ROOT);
    }

    @Override
    public List<Cell> cells() {
        List<Cell> out = new ArrayList<>(64);
        for (Square square : Square.values()) {
            if (square == Square.NONE) continue;
            Piece p = board.getPiece(square);
            out.add(
                    new Cell(
                            id(square),
                            square.getFile().ordinal(),
                            square.getRank().ordinal(),
                            p == Piece.NONE ? "" : symbol(p),
                            p == Piece.NONE ? -1 : p.getPieceSide() == Side.WHITE ? 0 : 1));
        }
        return List.copyOf(out);
    }

    private static String symbol(Piece piece) {
        return switch (piece.getPieceType()) {
            case KING -> GameSymbols.KING;
            case QUEEN -> GameSymbols.QUEEN;
            case ROOK -> GameSymbols.ROOK;
            case BISHOP -> GameSymbols.ELEPHANT;
            case KNIGHT -> GameSymbols.HORSE;
            case PAWN -> GameSymbols.PAWN;
            default -> "";
        };
    }

    private Map<String, Move> moves() {
        if (cached == null) {
            Map<String, Move> moves = new LinkedHashMap<>();
            for (Move move : board.legalMoves()) moves.put(action(move), move);
            cached = Collections.unmodifiableMap(moves);
        }
        return cached;
    }

    @Override
    public List<String> legalActions(int seat) {
        return finished() || seat != currentPlayer() ? List.of() : List.copyOf(moves().keySet());
    }

    @Override
    public List<String> actionsForCell(int seat, String cellId) {
        if (cellId == null) return List.of();
        return legalActions(seat).stream()
                .filter(
                        action -> {
                            String[] parts = action.split(":");
                            return parts[1].equals(cellId) || parts[2].equals(cellId);
                        })
                .toList();
    }

    @Override
    public void apply(int seat, String action) {
        if (finished() || seat != currentPlayer() || action == null || !moves().containsKey(action))
            throw new RuleViolation("error.invalid-chess-move", "Invalid chess move");
        Move move = moves().get(action);
        if (!board.doMove(move, true)) throw new IllegalStateException("Rules engine rejected a validated move");
        lastAction = (seat == 0 ? "White " : "Black ") + action.substring(5);
        lastMessage = RuleMessage.of("board.move", "player", seat + 1, "move", action.substring(5));
        if (move.getPromotion() != Piece.NONE) {
            lastAction += " promoted to " + symbol(move.getPromotion());
            lastMessage =
                    RuleMessage.of(
                            "board.move-promoted",
                            "player",
                            seat + 1,
                            "move",
                            action.substring(5),
                            "piece",
                            symbol(move.getPromotion()));
        }
        cached = null;
        updateResult();
    }

    private void updateResult() {
        // Checkmate takes precedence over a halfmove draw on the same final move.
        if (board.isMated()) {
            result = "winner:" + (1 - currentPlayer());
            reason = "Checkmate";
        } else if (board.isStaleMate()) {
            result = "draw:stalemate";
            reason = "Stalemate";
        } else if (deadMaterial()) {
            result = "draw:insufficient-material";
            reason = "Insufficient mating material";
        } else if (board.isRepetition()) {
            result = "draw:threefold-repetition";
            reason = "Threefold repetition";
        } else if (board.getHalfMoveCounter() >= 100) {
            result = "draw:50-move-rule";
            reason = "50 moves without a capture or pawn move";
        }
    }

    private boolean deadMaterial() {
        // Upstream marks several four-piece mixed-minor positions as dead. A
        // cooperative mate can exist there. Only automatically adjudicate the
        // unambiguous material cases; do not confuse inability to FORCE mate with
        // inability to reach ANY mating position (notably KNN v K and KN v KN).
        List<Square> minors = new ArrayList<>();
        for (Square square : Square.values()) {
            if (square == Square.NONE) continue;
            Piece p = board.getPiece(square);
            if (p == Piece.NONE || p.getPieceType() == PieceType.KING) continue;
            if (p.getPieceType() != PieceType.BISHOP && p.getPieceType() != PieceType.KNIGHT)
                return false;
            minors.add(square);
        }
        if (minors.size() <= 1) return true;
        if (minors.stream().anyMatch(s -> board.getPiece(s).getPieceType() != PieceType.BISHOP))
            return false;
        return minors.stream()
                .allMatch(s -> s.isLightSquare() == minors.getFirst().isLightSquare());
    }

    @Override
    public Map<String, String> publicInfo() {
        return Map.of(
                "rules",
                "Standard chess: castling, en passant and promotion; checkmate wins, stalemate or insufficient material draws",
                "rulesVariant",
                "standard-auto-claim-draws",
                "ruleLimit",
                "Casual mode automatically draws on threefold repetition or the 50-move rule",
                "phase",
                finished() ? "Game finished" : board.isKingAttacked() ? "In check; respond to the check" : "Waiting for a move",
                "turn",
                currentPlayer() == 0 ? "White" : "Black",
                "lastAction",
                lastAction,
                "resultReason",
                reason,
                "fen",
                board.getFen());
    }
}
