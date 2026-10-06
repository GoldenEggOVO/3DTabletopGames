
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

import java.util.HashMap;
import java.util.Map;

public enum Piece {

    WHITE_PAWN(Side.WHITE, PieceType.PAWN, "♙", "P"),

    WHITE_KNIGHT(Side.WHITE, PieceType.KNIGHT, "♘", "N"),

    WHITE_BISHOP(Side.WHITE, PieceType.BISHOP, "♗", "B"),

    WHITE_ROOK(Side.WHITE, PieceType.ROOK, "♖", "R"),

    WHITE_QUEEN(Side.WHITE, PieceType.QUEEN, "♕", "Q"),

    WHITE_KING(Side.WHITE, PieceType.KING, "♔", "K"),

    BLACK_PAWN(Side.BLACK, PieceType.PAWN, "♟", "p"),

    BLACK_KNIGHT(Side.BLACK, PieceType.KNIGHT, "♞", "n"),

    BLACK_BISHOP(Side.BLACK, PieceType.BISHOP, "♝", "b"),

    BLACK_ROOK(Side.BLACK, PieceType.ROOK, "♜", "r"),

    BLACK_QUEEN(Side.BLACK, PieceType.QUEEN, "♛", "q"),

    BLACK_KING(Side.BLACK, PieceType.KING, "♚", "k"),

    NONE(null, null, "NONE", ".");

    public static final Piece[] allPieces = values();
    private static final Map<String, Piece> fenToPiece = new HashMap<>(13);
    private static final Piece[][] pieceMake = {
            {WHITE_PAWN, BLACK_PAWN},
            {WHITE_KNIGHT, BLACK_KNIGHT},
            {WHITE_BISHOP, BLACK_BISHOP},
            {WHITE_ROOK, BLACK_ROOK},
            {WHITE_QUEEN, BLACK_QUEEN},
            {WHITE_KING, BLACK_KING},
            {NONE, NONE},
    };

    static {
        for (final Piece piece : Piece.values()) {
            fenToPiece.put(piece.getFenSymbol(), piece);
        }
    }

    private final Side side;
    private final PieceType type;
    private final String fanSymbol;
    private final String fenSymbol;

    Piece(Side side, PieceType type, String fanSymbol, String fenSymbol) {
        this.side = side;
        this.type = type;
        this.fanSymbol = fanSymbol;
        this.fenSymbol = fenSymbol;
    }

    public static Piece fromValue(String v) {
        return valueOf(v);
    }

    public static Piece make(Side side, PieceType type) {
        return pieceMake[type.ordinal()][side.ordinal()];
    }

    public static Piece fromFenSymbol(String fenSymbol) {
        final Piece piece = fenToPiece.get(fenSymbol);
        if (piece == null) {
            throw new IllegalArgumentException(String.format("Unknown piece '%s'", fenSymbol));
        }
        return piece;
    }

    public String value() {
        return name();
    }

    public PieceType getPieceType() {
        return type;
    }

    public Side getPieceSide() {
        return side;
    }

    public String getSanSymbol() {
        return type.getSanSymbol();
    }

    public String getFanSymbol() {
        return fanSymbol;
    }

    public String getFenSymbol() {
        return fenSymbol;
    }

}
