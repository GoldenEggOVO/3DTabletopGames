
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

public enum PieceType {

    PAWN(""),

    KNIGHT("N"),

    BISHOP("B"),

    ROOK("R"),

    QUEEN("Q"),

    KING("K"),

    NONE("NONE");

    private static final Map<String, PieceType> sanToType = new HashMap<>(7);

    static {
        for (final PieceType type : PieceType.values()) {
            sanToType.put(type.getSanSymbol(), type);
        }
    }

    private final String sanSymbol;

    PieceType(String sanSymbol) {
        this.sanSymbol = sanSymbol;
    }

    public static PieceType fromValue(String v) {
        return valueOf(v);
    }

    public static PieceType fromSanSymbol(String sanSymbol) {
        final PieceType pieceType = sanToType.get(sanSymbol);
        if (pieceType == null) {
            throw new IllegalArgumentException(String.format("Unknown piece '%s'", sanSymbol));
        }
        return pieceType;
    }

    public String getSanSymbol() {
        return sanSymbol;
    }

    public String value() {
        return name();
    }
}
