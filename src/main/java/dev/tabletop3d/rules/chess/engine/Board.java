
/*
 * Copyright 2017 Ben-Hur Carlos Vieira Langoni Junior
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.tabletop3d.rules.chess.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static dev.tabletop3d.rules.chess.engine.Bitboard.extractLsb;
import dev.tabletop3d.rules.chess.engine.game.GameContext;
import dev.tabletop3d.rules.chess.engine.game.VariationType;
import dev.tabletop3d.rules.chess.engine.move.Move;
import dev.tabletop3d.rules.chess.engine.move.MoveGenerator;
import dev.tabletop3d.rules.chess.engine.util.XorShiftRandom;

public class Board implements Cloneable {

    private static final List<Long> keys = new ArrayList<>();
    private static final long RANDOM_SEED = 49109794719L;
    private static final int ZOBRIST_TABLE_SIZE = 2000;

    static {
        final XorShiftRandom random = new XorShiftRandom(RANDOM_SEED);
        for (int i = 0; i < ZOBRIST_TABLE_SIZE; i++) {
            long key = random.nextLong();
            keys.add(key);
        }
    }

    private final long[] bitboard;
    private final long[] bbSide;
    private final Piece[] occupation;
    private final EnumMap<Side, CastleRight> castleRight;
    private final LinkedList<Long> history = new LinkedList<>();
    private Side sideToMove;
    private Square enPassantTarget;
    private Square enPassant;
    private Integer moveCounter;
    private Integer halfMoveCounter;
    private GameContext context;
    private final boolean updateHistory;
    private long incrementalHashKey;

    public Board() {
        this(new GameContext(), true);
    }

    public Board(GameContext gameContext, boolean updateHistory) {

        bitboard = new long[Piece.allPieces.length];
        bbSide = new long[Side.allSides.length];
        occupation = new Piece[Square.values().length];
        castleRight = new EnumMap<>(Side.class);
        context = gameContext;
        this.updateHistory = updateHistory;
        setSideToMove(Side.WHITE);
        setEnPassantTarget(Square.NONE);
        setEnPassant(Square.NONE);
        setMoveCounter(1);
        setHalfMoveCounter(0);
        loadFromFen(gameContext.getStartFEN());
    }

    private static boolean isPromoRank(Side side, Move move) {
        if (side.equals(Side.WHITE) &&
                move.getTo().getRank().equals(Rank.RANK_8)) {
            return true;
        } else return side.equals(Side.BLACK) &&
                move.getTo().getRank().equals(Rank.RANK_1);

    }

    private static Square findEnPassantTarget(Square sq, Side side) {
        Square ep = Square.NONE;
        if (!Square.NONE.equals(sq)) {
            ep = Side.WHITE.equals(side) ?
                    Square.encode(Rank.RANK_5, sq.getFile()) :
                    Square.encode(Rank.RANK_4, sq.getFile());
        }
        return ep;
    }

    private static Square findEnPassant(Square sq, Side side) {
        Square ep = Square.NONE;
        if (!Square.NONE.equals(sq)) {
            ep = Side.WHITE.equals(side) ?
                    Square.encode(Rank.RANK_3, sq.getFile()) :
                    Square.encode(Rank.RANK_6, sq.getFile());
        }
        return ep;
    }

    private static IntStream zeroToSeven() {
        return IntStream.iterate(0, i -> i + 1).limit(8);
    }

    private static IntStream sevenToZero() {
        return IntStream.iterate(7, i -> i - 1).limit(8);
    }

    public boolean doMove(final Move move) {
        return doMove(move, false);
    }

    public boolean doMove(final Move move, boolean fullValidation) {

        if (!isMoveLegal(move, fullValidation)) {
            return false;
        }

        Piece movingPiece = getPiece(move.getFrom());
        Side side = getSideToMove();

        final boolean isCastle;
        if (PieceType.KING.equals(movingPiece.getPieceType())
                && getCastleRight(side) != CastleRight.NONE
                && context.isCastleMove(move)) {
            if (context.getVariationType() == VariationType.CHESS960) {
                isCastle = isChess960Castle(move, side);
            } else {
                isCastle = true;
            }
        } else {
            isCastle = false;
        }

        incrementalHashKey ^= getSideKey(getSideToMove());

        if (getEnPassantTarget() != Square.NONE) {
            incrementalHashKey ^= getEnPassantKey(getEnPassantTarget());
        }

        if (PieceType.KING.equals(movingPiece.getPieceType())) {
            if (isCastle) {
                CastleRight c = context.isKingSideCastle(move) ? CastleRight.KING_SIDE :
                        CastleRight.QUEEN_SIDE;
                Move rookMove = context.getRookCastleMove(side, c);
                if (context.getVariationType() == VariationType.CHESS960) {

                    Square kingDest;
                    if (side == Side.WHITE) {
                        kingDest = c == CastleRight.KING_SIDE ? Square.G1 : Square.C1;
                    } else {
                        kingDest = c == CastleRight.KING_SIDE ? Square.G8 : Square.C8;
                    }
                    Piece king = getPiece(move.getFrom());
                    Piece rook = getPiece(rookMove.getFrom());
                    unsetPiece(king, move.getFrom());
                    if (!rookMove.getFrom().equals(move.getFrom())) {
                        unsetPiece(rook, rookMove.getFrom());
                    }
                    setPiece(rook, rookMove.getTo());
                    setPiece(king, kingDest);
                } else {
                    movePiece(rookMove);
                }
            }
            if (getCastleRight(side) != CastleRight.NONE) {
                incrementalHashKey ^= getCastleRightKey(side);
                getCastleRight().put(side, CastleRight.NONE);
            }
        } else if (PieceType.ROOK == movingPiece.getPieceType()
                && CastleRight.NONE != getCastleRight(side)) {
            final Move oo = context.getRookoo(side);
            final Move ooo = context.getRookooo(side);

            if (move.getFrom() == oo.getFrom()) {
                if (CastleRight.KING_AND_QUEEN_SIDE == getCastleRight(side)) {
                    incrementalHashKey ^= getCastleRightKey(side);
                    getCastleRight().put(side, CastleRight.QUEEN_SIDE);
                    incrementalHashKey ^= getCastleRightKey(side);
                } else if (CastleRight.KING_SIDE == getCastleRight(side)) {
                    incrementalHashKey ^= getCastleRightKey(side);
                    getCastleRight().put(side, CastleRight.NONE);
                }
            } else if (move.getFrom() == ooo.getFrom()) {
                if (CastleRight.KING_AND_QUEEN_SIDE == getCastleRight(side)) {
                    incrementalHashKey ^= getCastleRightKey(side);
                    getCastleRight().put(side, CastleRight.KING_SIDE);
                    incrementalHashKey ^= getCastleRightKey(side);
                } else if (CastleRight.QUEEN_SIDE == getCastleRight(side)) {
                    incrementalHashKey ^= getCastleRightKey(side);
                    getCastleRight().put(side, CastleRight.NONE);
                }
            }
        }

        Piece capturedPiece;
        if (isCastle && context.getVariationType() == VariationType.CHESS960) {

            capturedPiece = Piece.NONE;
        } else {
            capturedPiece = movePiece(move);
        }

        if (PieceType.ROOK == capturedPiece.getPieceType()) {
            final Move oo = context.getRookoo(side.flip());
            final Move ooo = context.getRookooo(side.flip());
            if (move.getTo() == oo.getFrom()) {
                if (CastleRight.KING_AND_QUEEN_SIDE == getCastleRight(side.flip())) {
                    incrementalHashKey ^= getCastleRightKey(side.flip());
                    getCastleRight().put(side.flip(), CastleRight.QUEEN_SIDE);
                    incrementalHashKey ^= getCastleRightKey(side.flip());
                } else if (CastleRight.KING_SIDE == getCastleRight(side.flip())) {
                    incrementalHashKey ^= getCastleRightKey(side.flip());
                    getCastleRight().put(side.flip(), CastleRight.NONE);
                }
            } else if (move.getTo() == ooo.getFrom()) {
                if (CastleRight.KING_AND_QUEEN_SIDE == getCastleRight(side.flip())) {
                    incrementalHashKey ^= getCastleRightKey(side.flip());
                    getCastleRight().put(side.flip(), CastleRight.KING_SIDE);
                    incrementalHashKey ^= getCastleRightKey(side.flip());
                } else if (CastleRight.QUEEN_SIDE == getCastleRight(side.flip())) {
                    incrementalHashKey ^= getCastleRightKey(side.flip());
                    getCastleRight().put(side.flip(), CastleRight.NONE);
                }
            }
        }

        if (Piece.NONE == capturedPiece) {
            setHalfMoveCounter(getHalfMoveCounter() + 1);
        } else {
            setHalfMoveCounter(0);
        }

        setEnPassantTarget(Square.NONE);
        setEnPassant(Square.NONE);

        if (PieceType.PAWN == movingPiece.getPieceType()) {
            if (Math.abs(move.getTo().getRank().ordinal() -
                    move.getFrom().getRank().ordinal()) == 2) {
                Piece otherPawn = Piece.make(side.flip(), PieceType.PAWN);
                setEnPassant(findEnPassant(move.getTo(), side));
                if (hasPiece(otherPawn, move.getTo().getSideSquares()) &&
                        verifyNotPinnedPiece(side, getEnPassant(), move.getTo())) {
                    setEnPassantTarget(move.getTo());
                    incrementalHashKey ^= getEnPassantKey(getEnPassantTarget());
                }
            }
            setHalfMoveCounter(0);
        }

        if (side == Side.BLACK) {
            setMoveCounter(getMoveCounter() + 1);
        }

        setSideToMove(side.flip());
        incrementalHashKey ^= getSideKey(getSideToMove());

        if (updateHistory) {
            getHistory().addLast(getIncrementalHashKey());
        }

        return true;
    }

    private Piece movePiece(Move move) {
        Square from = move.getFrom();
        Square to = move.getTo();
        Piece promotion = move.getPromotion();
        Piece movingPiece = getPiece(from);
        Piece capturedPiece = getPiece(to);

        unsetPiece(movingPiece, from);
        if (capturedPiece != Piece.NONE) unsetPiece(capturedPiece, to);
        setPiece(promotion == Piece.NONE ? movingPiece : promotion, to);

        if (movingPiece.getPieceType() == PieceType.PAWN
                && getEnPassantTarget() != Square.NONE
                && to.getFile() != from.getFile()
                && capturedPiece == Piece.NONE) {
            capturedPiece = getPiece(getEnPassantTarget());
            if (capturedPiece != Piece.NONE) unsetPiece(capturedPiece, getEnPassantTarget());
        }
        return capturedPiece;
    }

    public boolean hasPiece(Piece piece, Square[] location) {
        for (Square sq : location) {
            if ((getBitboard(piece) & sq.getBitboard()) != 0L) {
                return true;
            }
        }
        return false;
    }

    public Piece getPiece(Square sq) {

        return occupation[sq.ordinal()];
    }

    public long getBitboard() {
        return bbSide[0] | bbSide[1];
    }

    public long getBitboard(Piece piece) {
        return bitboard[piece.ordinal()];
    }

    public long getBitboard(Side side) {
        return bbSide[side.ordinal()];
    }

    public long[] getBbSide() {
        return bbSide;
    }

    public List<Square> getPieceLocation(Piece piece) {
        if (getBitboard(piece) != 0L) {
            return Bitboard.bbToSquareList(getBitboard(piece));
        }
        return Collections.emptyList();
    }

    public Square getFistPieceLocation(Piece piece) {
        if (getBitboard(piece) != 0L) {
            return Square.squareAt(Bitboard.bitScanForward(getBitboard(piece)));
        }
        return Square.NONE;
    }

    public Side getSideToMove() {
        return sideToMove;
    }

    public void setSideToMove(Side sideToMove) {
        this.sideToMove = sideToMove;
    }

    public Square getEnPassantTarget() {
        return enPassantTarget;
    }

    public void setEnPassantTarget(Square enPassant) {
        this.enPassantTarget = enPassant;
    }

    public Square getEnPassant() {
        return enPassant;
    }

    public void setEnPassant(Square enPassant) {
        this.enPassant = enPassant;
    }

    public Integer getMoveCounter() {
        return moveCounter;
    }

    public void setMoveCounter(Integer moveCounter) {
        this.moveCounter = moveCounter;
    }

    public Integer getHalfMoveCounter() {
        return halfMoveCounter;
    }

    public void setHalfMoveCounter(Integer halfMoveCounter) {
        this.halfMoveCounter = halfMoveCounter;
    }

    public CastleRight getCastleRight(Side side) {
        return castleRight.get(side);
    }

    public EnumMap<Side, CastleRight> getCastleRight() {
        return castleRight;
    }

    public GameContext getContext() {
        return context;
    }

    public void setContext(GameContext context) {
        this.context = context;
    }

    public void clear() {
        setSideToMove(Side.WHITE);
        setEnPassantTarget(Square.NONE);
        setEnPassant(Square.NONE);
        setMoveCounter(0);
        setHalfMoveCounter(0);
        getHistory().clear();

        Arrays.fill(bitboard, 0L);
        Arrays.fill(bbSide, 0L);
        Arrays.fill(occupation, Piece.NONE);
        incrementalHashKey = 0;
    }

    public void setPiece(Piece piece, Square sq) {
        bitboard[piece.ordinal()] |= sq.getBitboard();
        bbSide[piece.getPieceSide().ordinal()] |= sq.getBitboard();
        occupation[sq.ordinal()] = piece;
        if (piece != Piece.NONE && sq != Square.NONE) {
            incrementalHashKey ^= getPieceSquareKey(piece, sq);
        }
    }

    public void unsetPiece(Piece piece, Square sq) {
        bitboard[piece.ordinal()] ^= sq.getBitboard();
        bbSide[piece.getPieceSide().ordinal()] ^= sq.getBitboard();
        occupation[sq.ordinal()] = Piece.NONE;
        if (piece != Piece.NONE && sq != Square.NONE) {
            incrementalHashKey ^= getPieceSquareKey(piece, sq);
        }
    }

    public void loadFromFen(String fen) {
        loadFromFen(fen, false);
    }

    public void loadFromFen(String fen, boolean chess960) {
        clear();
        String squares = fen.substring(0, fen.indexOf(' '));
        String state = fen.substring(fen.indexOf(' ') + 1);

        String[] ranks = squares.split("/");
        int file;
        int rank = 7;
        for (String r : ranks) {
            file = 0;
            for (int i = 0; i < r.length(); i++) {
                char c = r.charAt(i);
                if (Character.isDigit(c)) {
                    file += Character.digit(c, 10);
                } else {
                    Square sq = Square.encode(Rank.allRanks[rank], File.allFiles[file]);
                    setPiece(Piece.fromFenSymbol(String.valueOf(c)), sq);
                    file++;
                }
            }
            rank--;
        }

        sideToMove = state.toLowerCase().charAt(0) == 'w' ? Side.WHITE : Side.BLACK;

        String[] flags = state.split(" ");
        String castlingField = flags.length >= 2 ? flags[1] : "-";

        boolean isShredderFen = false;
        boolean isChess960 = false;
        Square whiteRookOO = null, whiteRookOOO = null;
        Square blackRookOO = null, blackRookOOO = null;

        if (!castlingField.equals("-")) {
            for (int ci = 0; ci < castlingField.length(); ci++) {
                char ch = castlingField.charAt(ci);
                if (ch >= 'A' && ch <= 'H') {
                    isShredderFen = true;
                    break;
                }
                if (ch >= 'a' && ch <= 'h') {
                    isShredderFen = true;
                    break;
                }
            }
        }

        if (isShredderFen) {

            isChess960 = true;
            Square wKing = getKingSquare(Side.WHITE);
            Square bKing = getKingSquare(Side.BLACK);
            int wKingFile = wKing != Square.NONE ? wKing.getFile().ordinal() : -1;
            int bKingFile = bKing != Square.NONE ? bKing.getFile().ordinal() : -1;

            for (int ci = 0; ci < castlingField.length(); ci++) {
                char ch = castlingField.charAt(ci);
                if (ch >= 'A' && ch <= 'H') {
                    int rookFile = ch - 'A';
                    if (wKingFile >= 0 && rookFile > wKingFile) {
                        whiteRookOO = Square.encode(Rank.RANK_1, File.allFiles[rookFile]);
                    } else {
                        whiteRookOOO = Square.encode(Rank.RANK_1, File.allFiles[rookFile]);
                    }
                } else if (ch >= 'a' && ch <= 'h') {
                    int rookFile = ch - 'a';
                    if (bKingFile >= 0 && rookFile > bKingFile) {
                        blackRookOO = Square.encode(Rank.RANK_8, File.allFiles[rookFile]);
                    } else {
                        blackRookOOO = Square.encode(Rank.RANK_8, File.allFiles[rookFile]);
                    }
                }
            }

            if (whiteRookOO != null && whiteRookOOO != null) {
                castleRight.put(Side.WHITE, CastleRight.KING_AND_QUEEN_SIDE);
            } else if (whiteRookOO != null) {
                castleRight.put(Side.WHITE, CastleRight.KING_SIDE);
            } else if (whiteRookOOO != null) {
                castleRight.put(Side.WHITE, CastleRight.QUEEN_SIDE);
            } else {
                castleRight.put(Side.WHITE, CastleRight.NONE);
            }

            if (blackRookOO != null && blackRookOOO != null) {
                castleRight.put(Side.BLACK, CastleRight.KING_AND_QUEEN_SIDE);
            } else if (blackRookOO != null) {
                castleRight.put(Side.BLACK, CastleRight.KING_SIDE);
            } else if (blackRookOOO != null) {
                castleRight.put(Side.BLACK, CastleRight.QUEEN_SIDE);
            } else {
                castleRight.put(Side.BLACK, CastleRight.NONE);
            }
        } else {

            if (castlingField.contains("K") && castlingField.contains("Q")) {
                castleRight.put(Side.WHITE, CastleRight.KING_AND_QUEEN_SIDE);
            } else if (castlingField.contains("K")) {
                castleRight.put(Side.WHITE, CastleRight.KING_SIDE);
            } else if (castlingField.contains("Q")) {
                castleRight.put(Side.WHITE, CastleRight.QUEEN_SIDE);
            } else {
                castleRight.put(Side.WHITE, CastleRight.NONE);
            }

            if (castlingField.contains("k") && castlingField.contains("q")) {
                castleRight.put(Side.BLACK, CastleRight.KING_AND_QUEEN_SIDE);
            } else if (castlingField.contains("k")) {
                castleRight.put(Side.BLACK, CastleRight.KING_SIDE);
            } else if (castlingField.contains("q")) {
                castleRight.put(Side.BLACK, CastleRight.QUEEN_SIDE);
            } else {
                castleRight.put(Side.BLACK, CastleRight.NONE);
            }

            if (!castlingField.equals("-")) {
                Square wKing = getKingSquare(Side.WHITE);
                Square bKing = getKingSquare(Side.BLACK);
                boolean whiteNonStandard = wKing != Square.NONE && wKing != Square.E1
                        && castleRight.get(Side.WHITE) != CastleRight.NONE;
                boolean blackNonStandard = bKing != Square.NONE && bKing != Square.E8
                        && castleRight.get(Side.BLACK) != CastleRight.NONE;

                if (chess960 || whiteNonStandard || blackNonStandard) {
                    isChess960 = true;

                    if (castleRight.get(Side.WHITE) != CastleRight.NONE && wKing != Square.NONE) {
                        int wkf = wKing.getFile().ordinal();
                        if (castleRight.get(Side.WHITE) == CastleRight.KING_SIDE
                                || castleRight.get(Side.WHITE) == CastleRight.KING_AND_QUEEN_SIDE) {

                            for (int f = wkf + 1; f <= 7; f++) {
                                Square sq = Square.encode(Rank.RANK_1, File.allFiles[f]);
                                if (getPiece(sq) == Piece.WHITE_ROOK) {
                                    whiteRookOO = sq;
                                    break;
                                }
                            }
                        }
                        if (castleRight.get(Side.WHITE) == CastleRight.QUEEN_SIDE
                                || castleRight.get(Side.WHITE) == CastleRight.KING_AND_QUEEN_SIDE) {

                            for (int f = wkf - 1; f >= 0; f--) {
                                Square sq = Square.encode(Rank.RANK_1, File.allFiles[f]);
                                if (getPiece(sq) == Piece.WHITE_ROOK) {
                                    whiteRookOOO = sq;
                                    break;
                                }
                            }
                        }
                    }
                    if (castleRight.get(Side.BLACK) != CastleRight.NONE && bKing != Square.NONE) {
                        int bkf = bKing.getFile().ordinal();
                        if (castleRight.get(Side.BLACK) == CastleRight.KING_SIDE
                                || castleRight.get(Side.BLACK) == CastleRight.KING_AND_QUEEN_SIDE) {
                            for (int f = bkf + 1; f <= 7; f++) {
                                Square sq = Square.encode(Rank.RANK_8, File.allFiles[f]);
                                if (getPiece(sq) == Piece.BLACK_ROOK) {
                                    blackRookOO = sq;
                                    break;
                                }
                            }
                        }
                        if (castleRight.get(Side.BLACK) == CastleRight.QUEEN_SIDE
                                || castleRight.get(Side.BLACK) == CastleRight.KING_AND_QUEEN_SIDE) {
                            for (int f = bkf - 1; f >= 0; f--) {
                                Square sq = Square.encode(Rank.RANK_8, File.allFiles[f]);
                                if (getPiece(sq) == Piece.BLACK_ROOK) {
                                    blackRookOOO = sq;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isChess960) {
            Square wKing = getKingSquare(Side.WHITE);
            Square bKing = getKingSquare(Side.BLACK);
            context.loadChess960(
                    wKing != Square.NONE ? wKing : Square.E1,
                    whiteRookOO, whiteRookOOO,
                    bKing != Square.NONE ? bKing : Square.E8,
                    blackRookOO, blackRookOOO
            );
        } else if (context.getVariationType() == VariationType.CHESS960) {

            context = new GameContext();
        }

        if (flags.length >= 3) {
            String s = flags[2].toUpperCase().trim();
            if (!s.equals("-")) {
                Square ep = Square.valueOf(s);
                setEnPassant(ep);
                setEnPassantTarget(findEnPassantTarget(ep, sideToMove));
                if (!pawnCanBeCapturedEnPassant()) {
                    setEnPassantTarget(Square.NONE);
                }
            } else {
                setEnPassant(Square.NONE);
                setEnPassantTarget(Square.NONE);
            }
            if (flags.length >= 4) {
                halfMoveCounter = Integer.parseInt(flags[3]);
                if (flags.length >= 5) {
                    moveCounter = Integer.parseInt(flags[4]);
                }
            }
        }

        incrementalHashKey = getZobristKey();
        if (updateHistory) {
            getHistory().addLast(this.getZobristKey());
        }
    }

    public String getFen() {
        return getFen(true);
    }

    public String getFen(boolean includeCounters) {
        return getFen(includeCounters, false);
    }

    public String getFen(boolean includeCounters, boolean onlyOutputEnPassantIfCapturable) {

        StringBuilder fen = new StringBuilder();
        int emptySquares = 0;
        for (int i = 7; i >= 0; i--) {
            Rank r = Rank.allRanks[i];
            if (r == Rank.NONE) {
                continue;
            }
            for (File f : File.allFiles) {
                if (f == File.NONE) {
                    continue;
                }
                Square sq = Square.encode(r, f);
                Piece piece = getPiece(sq);
                if (Piece.NONE.equals(piece)) {
                    emptySquares++;
                } else {
                    if (emptySquares > 0) {
                        fen.append(emptySquares);
                        emptySquares = 0;
                    }
                    fen.append(piece.getFenSymbol());
                }
                if (f != File.FILE_H) {
                    continue;
                }
                if (emptySquares > 0) {
                    fen.append(emptySquares);
                    emptySquares = 0;
                }
                if (r != Rank.RANK_1) {
                    fen.append("/");
                }
            }
        }

        if (Side.WHITE.equals(sideToMove)) {
            fen.append(" w");
        } else {
            fen.append(" b");
        }

        String rights = "";
        if (context.getVariationType() == VariationType.CHESS960) {

            if (CastleRight.KING_AND_QUEEN_SIDE.equals(castleRight.get(Side.WHITE))
                    || CastleRight.KING_SIDE.equals(castleRight.get(Side.WHITE))) {
                if (context.getWhiteRookooFile() != null) {
                    rights += context.getWhiteRookooFile().getNotation().toUpperCase();
                }
            }
            if (CastleRight.KING_AND_QUEEN_SIDE.equals(castleRight.get(Side.WHITE))
                    || CastleRight.QUEEN_SIDE.equals(castleRight.get(Side.WHITE))) {
                if (context.getWhiteRookoooFile() != null) {
                    rights += context.getWhiteRookoooFile().getNotation().toUpperCase();
                }
            }
            if (CastleRight.KING_AND_QUEEN_SIDE.equals(castleRight.get(Side.BLACK))
                    || CastleRight.KING_SIDE.equals(castleRight.get(Side.BLACK))) {
                if (context.getBlackRookooFile() != null) {
                    rights += context.getBlackRookooFile().getNotation().toLowerCase();
                }
            }
            if (CastleRight.KING_AND_QUEEN_SIDE.equals(castleRight.get(Side.BLACK))
                    || CastleRight.QUEEN_SIDE.equals(castleRight.get(Side.BLACK))) {
                if (context.getBlackRookoooFile() != null) {
                    rights += context.getBlackRookoooFile().getNotation().toLowerCase();
                }
            }
        } else {
            if (CastleRight.KING_AND_QUEEN_SIDE.
                    equals(castleRight.get(Side.WHITE))) {
                rights += "KQ";
            } else if (CastleRight.KING_SIDE.
                    equals(castleRight.get(Side.WHITE))) {
                rights += "K";
            } else if (CastleRight.QUEEN_SIDE.
                    equals(castleRight.get(Side.WHITE))) {
                rights += "Q";
            }

            if (CastleRight.KING_AND_QUEEN_SIDE.
                    equals(castleRight.get(Side.BLACK))) {
                rights += "kq";
            } else if (CastleRight.KING_SIDE.
                    equals(castleRight.get(Side.BLACK))) {
                rights += "k";
            } else if (CastleRight.QUEEN_SIDE.
                    equals(castleRight.get(Side.BLACK))) {
                rights += "q";
            }
        }

        if (rights.isEmpty()) {
            fen.append(" -");
        } else {
            fen.append(" " + rights);
        }

        if (Square.NONE.equals(getEnPassant())
                || (onlyOutputEnPassantIfCapturable
                && !pawnCanBeCapturedEnPassant())) {
            fen.append(" -");
        } else {
            fen.append(" ");
            fen.append(getEnPassant().toString().toLowerCase());
        }

        if (includeCounters) {
            fen.append(" ");
            fen.append(getHalfMoveCounter());
            fen.append(" ");
            fen.append(getMoveCounter());
        }

        return fen.toString();
    }

    public Piece[] boardToArray() {

        final Piece[] pieces = new Piece[65];
        pieces[64] = Piece.NONE;

        for (Square square : Square.values()) {
            if (!Square.NONE.equals(square)) {
                pieces[square.ordinal()] = getPiece(square);
            }
        }

        return pieces;
    }

    public long squareAttackedBy(Square square, Side side) {
        return squareAttackedBy(square, side, getBitboard());
    }

    public long squareAttackedBy(Square square, Side side, long occ) {
        long result;
        result = Bitboard.getPawnAttacks(side.flip(), square) &
                getBitboard(Piece.make(side, PieceType.PAWN)) & occ;
        result |= Bitboard.getKnightAttacks(square, occ) &
                getBitboard(Piece.make(side, PieceType.KNIGHT));
        result |= Bitboard.getBishopAttacks(occ, square) &
                ((getBitboard(Piece.make(side, PieceType.BISHOP)) |
                        getBitboard(Piece.make(side, PieceType.QUEEN))));
        result |= Bitboard.getRookAttacks(occ, square) &
                ((getBitboard(Piece.make(side, PieceType.ROOK)) |
                        getBitboard(Piece.make(side, PieceType.QUEEN))));
        result |= Bitboard.getKingAttacks(square, occ) &
                getBitboard(Piece.make(side, PieceType.KING));
        return result;
    }

    public long squareAttackedByPieceType(Square square, Side side, PieceType type) {
        long result = 0L;
        long occ = getBitboard();
        switch (type) {
            case PAWN:
                result = Bitboard.getPawnAttacks(side.flip(), square) &
                        getBitboard(Piece.make(side, PieceType.PAWN));
                break;
            case KNIGHT:
                result = Bitboard.getKnightAttacks(square, occ) &
                        getBitboard(Piece.make(side, PieceType.KNIGHT));
                break;
            case BISHOP:
                result = Bitboard.getBishopAttacks(occ, square) &
                        getBitboard(Piece.make(side, PieceType.BISHOP));
                break;
            case ROOK:
                result = Bitboard.getRookAttacks(occ, square) &
                        getBitboard(Piece.make(side, PieceType.ROOK));
                break;
            case QUEEN:
                result = Bitboard.getQueenAttacks(occ, square) &
                        getBitboard(Piece.make(side, PieceType.QUEEN));
                break;
            case KING:
                result |= Bitboard.getKingAttacks(square, occ) &
                        getBitboard(Piece.make(side, PieceType.KING));
                break;
            default:
                break;
        }
        return result;
    }

    public Square getKingSquare(Side side) {
        Square result = Square.NONE;
        long piece = getBitboard(Piece.make(side, PieceType.KING));
        if (piece != 0L) {
            int sq = Bitboard.bitScanForward(piece);
            return Square.squareAt(sq);
        }
        return result;
    }

    public boolean isKingAttacked() {
        return squareAttackedBy(getKingSquare(getSideToMove()), getSideToMove().flip()) != 0;
    }

    public boolean isSquareAttackedBy(List<Square> squares, Side side) {
        for (Square sq : squares) {
            if (squareAttackedBy(sq, side) != 0L) {
                return true;
            }
        }
        return false;
    }

    public boolean isMoveLegal(Move move, boolean fullValidation) {

        final Piece fromPiece = getPiece(move.getFrom());
        final Side side = getSideToMove();
        final PieceType fromType = fromPiece.getPieceType();
        final Piece capturedPiece = getPiece(move.getTo());

        if (fullValidation) {
            if (Piece.NONE.equals(fromPiece)) {
                return false;
            }

            if (fromPiece.getPieceSide().equals(capturedPiece.getPieceSide())) {
                boolean allowCapture = false;
                if (fromType.equals(PieceType.KING)
                        && context.getVariationType() == VariationType.CHESS960) {
                    allowCapture = isChess960Castle(move, side);
                }
                if (!allowCapture) {
                    return false;
                }
            }

            if (!side.equals(fromPiece.getPieceSide())) {
                return false;
            }

            boolean pawnPromoting = fromPiece.getPieceType().equals(PieceType.PAWN) &&
                    isPromoRank(side, move);
            boolean hasPromoPiece = !move.getPromotion().equals(Piece.NONE);

            if (hasPromoPiece != pawnPromoting) {
                return false;
            }
            if (fromType.equals(PieceType.KING)) {

                boolean rookOnSquareOO = true;
                boolean rookOnSquareOOO = true;
                if (context.getVariationType() == VariationType.CHESS960) {
                    Piece destPiece = getPiece(move.getTo());
                    boolean destHasEnemy = destPiece != Piece.NONE && !destPiece.getPieceSide().equals(side);
                    if (destHasEnemy) {
                        rookOnSquareOO = false;
                        rookOnSquareOOO = false;
                    } else if (move.getFrom() != move.getTo()) {
                        Move rookOO = context.getRookoo(side);
                        Move rookOOO = context.getRookooo(side);
                        Piece expectedRook = Piece.make(side, PieceType.ROOK);
                        rookOnSquareOO = rookOO != null && getPiece(rookOO.getFrom()) == expectedRook;
                        rookOnSquareOOO = rookOOO != null && getPiece(rookOOO.getFrom()) == expectedRook;
                    }
                }

                if (getContext().isKingSideCastle(move) && rookOnSquareOO &&
                        (getCastleRight(side).equals(CastleRight.KING_AND_QUEEN_SIDE) ||
                         getCastleRight(side).equals(CastleRight.KING_SIDE))) {
                    long occ = getBitboard();
                    if (context.getVariationType() == VariationType.CHESS960) {
                        occ &= ~move.getFrom().getBitboard();
                        Move rookMove = context.getRookoo(side);
                        if (rookMove != null) {
                            occ &= ~rookMove.getFrom().getBitboard();
                        }
                    }
                    if ((occ & getContext().getooAllSquaresBb(side)) == 0L) {
                        return !isSquareAttackedBy(getContext().getooSquares(side), side.flip());
                    }
                    return false;
                }
                if (getContext().isQueenSideCastle(move) && rookOnSquareOOO &&
                        (getCastleRight(side).equals(CastleRight.KING_AND_QUEEN_SIDE) ||
                         getCastleRight(side).equals(CastleRight.QUEEN_SIDE))) {
                    long occ = getBitboard();
                    if (context.getVariationType() == VariationType.CHESS960) {
                        occ &= ~move.getFrom().getBitboard();
                        Move rookMove = context.getRookooo(side);
                        if (rookMove != null) {
                            occ &= ~rookMove.getFrom().getBitboard();
                        }
                    }
                    if ((occ & getContext().getoooAllSquaresBb(side)) == 0L) {
                        return !isSquareAttackedBy(getContext().getoooSquares(side), side.flip());
                    }
                    return false;
                }
            }
        }
        if (fromType.equals(PieceType.KING)) {

            if (context.getVariationType() != VariationType.CHESS960 || !isChess960Castle(move, side)) {
                if (squareAttackedBy(move.getTo(), side.flip()) != 0L) {
                    return false;
                }
            }
        }

        if (context.getVariationType() == VariationType.CHESS960 && isChess960Castle(move, side)) {
            return true;
        }
        Square kingSq = (fromType.equals(PieceType.KING) ?
                move.getTo() : getKingSquare(side));
        Side other = side.flip();
        long moveTo = move.getTo().getBitboard();
        long moveFrom = move.getFrom().getBitboard();
        long ep = getEnPassantTarget() != Square.NONE && move.getTo() == getEnPassant() &&
                (fromType.equals(PieceType.PAWN)) ? getEnPassantTarget().getBitboard() : 0;
        long allPieces = (getBitboard() ^ moveFrom ^ ep) | moveTo;

        long bishopAndQueens = ((getBitboard(Piece.make(other, PieceType.BISHOP)) |
                getBitboard(Piece.make(other, PieceType.QUEEN)))) & ~moveTo;

        if (bishopAndQueens != 0L &&
                (Bitboard.getBishopAttacks(allPieces, kingSq) & bishopAndQueens) != 0L) {
            return false;
        }

        long rookAndQueens = ((getBitboard(Piece.make(other, PieceType.ROOK)) |
                getBitboard(Piece.make(other, PieceType.QUEEN)))) & ~moveTo;

        if (rookAndQueens != 0L &&
                (Bitboard.getRookAttacks(allPieces, kingSq) & rookAndQueens) != 0L) {
            return false;
        }

        long knights = (getBitboard(Piece.make(other, PieceType.KNIGHT))) & ~moveTo;

        if (knights != 0L &&
                (Bitboard.getKnightAttacks(kingSq, allPieces) & knights) != 0L) {
            return false;
        }

        long pawns = (getBitboard(Piece.make(other, PieceType.PAWN))) & ~moveTo & ~ep;

        return pawns == 0L ||
                (Bitboard.getPawnAttacks(side, kingSq) & pawns) == 0L;
    }

    public boolean isAttackedBy(Move move) {

        PieceType pieceType = getPiece(move.getFrom()).getPieceType();
        assert (!PieceType.NONE.equals(pieceType));
        Side side = getSideToMove();
        long attacks = 0L;
        switch (pieceType) {
            case PAWN:
                if (!move.getFrom().getFile().equals(move.getTo().getFile())) {
                    attacks = Bitboard.getPawnCaptures(side, move.getFrom(),
                            getBitboard(), getEnPassantTarget());
                } else {
                    attacks = Bitboard.getPawnMoves(side, move.getFrom(), getBitboard());
                }
                break;
            case KNIGHT:
                attacks = Bitboard.getKnightAttacks(move.getFrom(), ~getBitboard(side));
                break;
            case BISHOP:
                attacks = Bitboard.getBishopAttacks(getBitboard(), move.getFrom());
                break;
            case ROOK:
                attacks = Bitboard.getRookAttacks(getBitboard(), move.getFrom());
                break;
            case QUEEN:
                attacks = Bitboard.getQueenAttacks(getBitboard(), move.getFrom());
                break;
            case KING:
                attacks = Bitboard.getKingAttacks(move.getFrom(), ~getBitboard(side));
                break;
            default:
                break;
        }
        return (attacks & move.getTo().getBitboard()) != 0L;
    }

    public LinkedList<Long> getHistory() {
        return history;
    }

    public boolean isMated() {
        try {
            if (isKingAttacked()) {
                final List<Move> l = MoveGenerator.generateLegalMoves(this);
                if (l.size() == 0) {
                    return true;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public boolean isDraw() {
        if (isRepetition()) {
            return true;
        }
        if (isInsufficientMaterial()) {
            return true;
        }
        if (getHalfMoveCounter() >= 100) {
            return true;
        }
        return isStaleMate();

    }

    public boolean isRepetition(int n) {

        final int i = Math.min(getHistory().size() - 1, getHalfMoveCounter());
        if (getHistory().size() >= 4) {
            long lastKey = getHistory().get(getHistory().size() - 1);
            int rep = 0;
            for (int x = 4; x <= i; x += 2) {
                final long k = getHistory().get(getHistory().size() - x - 1);
                if (k == lastKey && ++rep >= n - 1) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isRepetition() {

        return isRepetition(3);
    }

    public boolean isInsufficientMaterial() {

        if ((getBitboard(Piece.WHITE_QUEEN) +
                getBitboard(Piece.BLACK_QUEEN) +
                getBitboard(Piece.WHITE_ROOK) +
                getBitboard(Piece.BLACK_ROOK)) != 0L) {
            return false;
        }

        final long pawns = getBitboard(Piece.WHITE_PAWN) | getBitboard(Piece.BLACK_PAWN);
        if (pawns == 0L) {
            long count = Long.bitCount(getBitboard());
            int whiteCount = Long.bitCount(getBitboard(Side.WHITE));
            int blackCount = Long.bitCount(getBitboard(Side.BLACK));
            if (count == 4) {
                int whiteBishopCount = Long.bitCount(getBitboard(Piece.WHITE_BISHOP));
                int blackBishopCount = Long.bitCount(getBitboard(Piece.BLACK_BISHOP));
                if (whiteCount > 1 && blackCount > 1) {
                    return !((whiteBishopCount == 1 && blackBishopCount == 1) &&
                            getFistPieceLocation(Piece.WHITE_BISHOP).isLightSquare() !=
                                    getFistPieceLocation(Piece.BLACK_BISHOP).isLightSquare());
                }
                if (whiteCount == 3 || blackCount == 3) {
                    if (whiteBishopCount == 2 &&
                            ((Bitboard.lightSquares & getBitboard(Piece.WHITE_BISHOP)) == 0L ||
                                    (Bitboard.darkSquares & getBitboard(Piece.WHITE_BISHOP)) == 0L)) {
                        return true;
                    } else return blackBishopCount == 2 &&
                            ((Bitboard.lightSquares & getBitboard(Piece.BLACK_BISHOP)) == 0L ||
                                    (Bitboard.darkSquares & getBitboard(Piece.BLACK_BISHOP)) == 0L);
                } else {
                    return Long.bitCount(getBitboard(Piece.WHITE_KNIGHT)) == 2 ||
                            Long.bitCount(getBitboard(Piece.BLACK_KNIGHT)) == 2;
                }
            } else {
                if ((getBitboard(Piece.WHITE_KING) | getBitboard(Piece.WHITE_BISHOP)) == getBitboard(Side.WHITE) &&
                        ((getBitboard(Piece.BLACK_KING) | getBitboard(Piece.BLACK_BISHOP)) == getBitboard(Side.BLACK))) {
                    return (((Bitboard.lightSquares & getBitboard(Piece.WHITE_BISHOP)) == 0L) &&
                            ((Bitboard.lightSquares & getBitboard(Piece.BLACK_BISHOP)) == 0L)) ||
                            ((Bitboard.darkSquares & getBitboard(Piece.WHITE_BISHOP)) == 0L) &&
                                    ((Bitboard.darkSquares & getBitboard(Piece.BLACK_BISHOP)) == 0L);
                }
                return count < 4;
            }
        }

        return false;
    }

    public boolean isStaleMate() {
        try {
            if (!isKingAttacked()) {
                List<Move> l = MoveGenerator.generateLegalMoves(this);
                if (l.size() == 0) {
                    return true;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public String getPositionId() {
        String[] parts = this.getFen(false).split(" ");
        return parts[0] + " " + parts[1] + " " + parts[2] +
                " " + (this.getEnPassantTarget() != Square.NONE ? parts[3] : "-");
    }

    public List<Move> legalMoves() {

        return MoveGenerator.generateLegalMoves(this);
    }

    public String toUci(Move move) {
        if (context.getVariationType() == VariationType.CHESS960 && context.isCastleMove(move)) {
            Side side = getSideToMove();
            Move rookMove = context.isKingSideCastle(move)
                    ? context.getRookoo(side)
                    : context.getRookooo(side);
            if (rookMove != null) {
                return move.getFrom().toString().toLowerCase() + rookMove.getFrom().toString().toLowerCase();
            }
        }
        String uci = move.getFrom().toString().toLowerCase() + move.getTo().toString().toLowerCase();
        if (move.getPromotion() != Piece.NONE) {
            uci += move.getPromotion().getFenSymbol().toLowerCase();
        }
        return uci;
    }

    public Move fromUci(String uci) {
        if (uci.length() >= 4 && context.getVariationType() == VariationType.CHESS960) {
            Square from = Square.valueOf(uci.substring(0, 2).toUpperCase());
            Square to = Square.valueOf(uci.substring(2, 4).toUpperCase());
            Piece fromPiece = getPiece(from);
            Piece toPiece = getPiece(to);

            if (fromPiece.getPieceType() == PieceType.KING && toPiece.getPieceType() == PieceType.ROOK
                    && fromPiece.getPieceSide() == toPiece.getPieceSide()) {
                int fromFile = from.getFile().ordinal();
                int toFile = to.getFile().ordinal();
                return toFile > fromFile
                        ? context.getoo(getSideToMove())
                        : context.getooo(getSideToMove());
            }
        }
        Piece promotion = Piece.NONE;
        if (uci.length() == 5) {
            promotion = Piece.fromFenSymbol(
                    getSideToMove() == Side.WHITE
                            ? String.valueOf(uci.charAt(4)).toUpperCase()
                            : String.valueOf(uci.charAt(4)).toLowerCase());
        }
        Square from = Square.valueOf(uci.substring(0, 2).toUpperCase());
        Square to = Square.valueOf(uci.substring(2, 4).toUpperCase());
        return new Move(from, to, promotion);
    }

    public List<Move> pseudoLegalMoves() {

        return MoveGenerator.generatePseudoLegalMoves(this);
    }

    public List<Move> pseudoLegalCaptures() {

        return MoveGenerator.generatePseudoLegalCaptures(this);
    }

    @Override
    public boolean equals(Object obj) {

        if (obj instanceof Board) {
            Board board = (Board) obj;
            for (Piece piece : Piece.allPieces) {
                if (piece != Piece.NONE && getBitboard(piece) != board.getBitboard(piece)) {
                    return false;
                }
            }
            return getSideToMove() == board.getSideToMove()
                    && getCastleRight(Side.WHITE) == board.getCastleRight(Side.WHITE)
                    && getCastleRight(Side.BLACK) == board.getCastleRight(Side.BLACK)
                    && getEnPassant() == board.getEnPassant()
                    && getEnPassantTarget() == board.getEnPassantTarget();

        }
        return false;
    }

    public boolean strictEquals(Object obj) {
        if (obj instanceof Board) {
            Board board = (Board) obj;
            return equals(board) && board.getHistory().equals(this.getHistory());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (int) incrementalHashKey;
    }

    public long getZobristKey() {
        long hash = 0;
        if (getCastleRight(Side.WHITE) != CastleRight.NONE) {
            hash ^= getCastleRightKey(Side.WHITE);
        }
        if (getCastleRight(Side.BLACK) != CastleRight.NONE) {
            hash ^= getCastleRightKey(Side.BLACK);
        }
        for (Square sq : Square.values()) {
            Piece piece = getPiece(sq);
            if (!Piece.NONE.equals(piece) && !Square.NONE.equals(sq)) {
                hash ^= getPieceSquareKey(piece, sq);
            }
        }
        hash ^= getSideKey(getSideToMove());

        if (Square.NONE != getEnPassantTarget() &&
                pawnCanBeCapturedEnPassant()) {
            hash ^= getEnPassantKey(getEnPassantTarget());
        }
        return hash;
    }

    private long getCastleRightKey(Side side) {
        return keys.get(3 * getCastleRight(side).ordinal() + 300 + 3 * side.ordinal());
    }

    private long getSideKey(Side side) {
        return keys.get(3 * side.ordinal() + 500);
    }

    private long getEnPassantKey(Square enPassantTarget) {
        return keys.get(3 * enPassantTarget.ordinal() + 400);
    }

    private long getPieceSquareKey(Piece piece, Square square) {
        return keys.get(57 * piece.ordinal() + 13 * square.ordinal());
    }

    public String toStringFromWhiteViewPoint() {
        return toStringFromViewPoint(Side.WHITE);
    }

    public String toStringFromBlackViewPoint() {
        return toStringFromViewPoint(Side.BLACK);
    }

    public String toStringFromViewPoint(Side side) {
        StringBuilder sb = new StringBuilder();

        final Supplier<IntStream> rankIterator = side == Side.WHITE
                ? Board::sevenToZero : Board::zeroToSeven;
        final Supplier<IntStream> fileIterator = side == Side.WHITE
                ? Board::zeroToSeven : Board::sevenToZero;

        rankIterator.get().forEach(i -> {
            Rank r = Rank.allRanks[i];
            fileIterator.get().forEach(n -> {
                File f = File.allFiles[n];
                if (!File.NONE.equals(f) && !Rank.NONE.equals(r)) {
                    Square sq = Square.encode(r, f);
                    Piece piece = getPiece(sq);
                    sb.append(piece.getFenSymbol());
                }
            });
            sb.append("\n");
        });

        return sb.toString();
    }

    @Override
    public String toString() {
        return toStringFromWhiteViewPoint() + "Side: " + getSideToMove();
    }

    @Override
    public Board clone() {
        Board copy = new Board(getContext(), this.updateHistory);
        copy.loadFromFen(this.getFen());
        copy.setEnPassantTarget(this.getEnPassantTarget());
        copy.incrementalHashKey = this.incrementalHashKey;
        copy.getHistory().clear();
        for (long key : getHistory()) {
            copy.getHistory().add(key);
        }
        return copy;
    }

    public long getIncrementalHashKey() {
        return incrementalHashKey;
    }

    public void setIncrementalHashKey(long hashKey) {
        incrementalHashKey = hashKey;
    }

    boolean isChess960Castle(Move move, Side side) {
        if (!context.isCastleMove(move) || getCastleRight(side) == CastleRight.NONE) {
            return false;
        }
        if (move.getFrom() == move.getTo()) {
            return true;
        }
        Piece destPiece = getPiece(move.getTo());
        if (destPiece != Piece.NONE && !destPiece.getPieceSide().equals(side)) {
            return false;
        }
        CastleRight c = context.isKingSideCastle(move) ? CastleRight.KING_SIDE : CastleRight.QUEEN_SIDE;
        Move rookMove = context.getRookCastleMove(side, c);
        Piece expectedRook = Piece.make(side, PieceType.ROOK);
        return rookMove != null && getPiece(rookMove.getFrom()) == expectedRook;
    }

    private boolean pawnCanBeCapturedEnPassant() {
        return
                squareAttackedByPieceType(getEnPassant(), getSideToMove(), PieceType.PAWN) != 0
                        && verifyNotPinnedPiece(getSideToMove().flip(), getEnPassant(), getEnPassantTarget());
    }

    private boolean verifyNotPinnedPiece(Side side, Square enPassant, Square target) {

        long pawns = Bitboard.getPawnAttacks(side, enPassant) & getBitboard(Piece.make(side.flip(), PieceType.PAWN));
        return pawns != 0 && verifyAllPins(pawns, side, enPassant, target);
    }

    private boolean verifyAllPins(long pawns, Side side, Square enPassant, Square target) {

        long onePawn = extractLsb(pawns);
        long otherPawn = pawns ^ onePawn;
        if (onePawn != 0L && verifyKingIsNotAttackedWithoutPin(side, enPassant, target, onePawn)) {
            return true;
        }
        return verifyKingIsNotAttackedWithoutPin(side, enPassant, target, otherPawn);
    }

    private boolean verifyKingIsNotAttackedWithoutPin(Side side, Square enPassant, Square target, long pawns) {

        return squareAttackedBy(getKingSquare(side.flip()), side, removePieces(enPassant, target, pawns)) == 0L;
    }

    private long removePieces(Square enPassant, Square target, long pieces) {

        return (getBitboard() ^ pieces ^ target.getBitboard()) | enPassant.getBitboard();
    }

}
