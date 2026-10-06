
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

import dev.tabletop3d.rules.chess.engine.move.Move;

import java.util.ArrayList;
import java.util.List;

public class Constants {

    public static final String startStandardFENPosition = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public static final Move DEFAULT_WHITE_OO = new Move(Square.E1, Square.G1);

    public static final Move DEFAULT_WHITE_OOO = new Move(Square.E1, Square.C1);

    public static final Move DEFAULT_BLACK_OO = new Move(Square.E8, Square.G8);

    public static final Move DEFAULT_BLACK_OOO = new Move(Square.E8, Square.C8);

    public static final Move DEFAULT_WHITE_ROOK_OO = new Move(Square.H1, Square.F1);

    public static final Move DEFAULT_WHITE_ROOK_OOO = new Move(Square.A1, Square.D1);

    public static final Move DEFAULT_BLACK_ROOK_OO = new Move(Square.H8, Square.F8);

    public static final Move DEFAULT_BLACK_ROOK_OOO = new Move(Square.A8, Square.D8);

    public static final List<Square> DEFAULT_WHITE_OO_SQUARES = new ArrayList<Square>();

    public static final List<Square> DEFAULT_WHITE_OOO_SQUARES = new ArrayList<Square>();

    public static final List<Square> DEFAULT_BLACK_OO_SQUARES = new ArrayList<Square>();

    public static final List<Square> DEFAULT_BLACK_OOO_SQUARES = new ArrayList<Square>();

    public static final List<Square> DEFAULT_WHITE_OO_ALL_SQUARES = new ArrayList<Square>();

    public static final List<Square> DEFAULT_WHITE_OOO_ALL_SQUARES = new ArrayList<Square>();

    public static final List<Square> DEFAULT_BLACK_OO_ALL_SQUARES = new ArrayList<Square>();

    public static final List<Square> DEFAULT_BLACK_OOO_ALL_SQUARES = new ArrayList<Square>();

    static {
        DEFAULT_WHITE_OO_SQUARES.add(Square.F1);
        DEFAULT_WHITE_OO_SQUARES.add(Square.G1);
        DEFAULT_WHITE_OOO_SQUARES.add(Square.D1);
        DEFAULT_WHITE_OOO_SQUARES.add(Square.C1);

        DEFAULT_BLACK_OO_SQUARES.add(Square.F8);
        DEFAULT_BLACK_OO_SQUARES.add(Square.G8);
        DEFAULT_BLACK_OOO_SQUARES.add(Square.D8);
        DEFAULT_BLACK_OOO_SQUARES.add(Square.C8);

        DEFAULT_WHITE_OO_ALL_SQUARES.add(Square.F1);
        DEFAULT_WHITE_OO_ALL_SQUARES.add(Square.G1);
        DEFAULT_WHITE_OOO_ALL_SQUARES.add(Square.D1);
        DEFAULT_WHITE_OOO_ALL_SQUARES.add(Square.C1);
        DEFAULT_WHITE_OOO_ALL_SQUARES.add(Square.B1);

        DEFAULT_BLACK_OO_ALL_SQUARES.add(Square.F8);
        DEFAULT_BLACK_OO_ALL_SQUARES.add(Square.G8);
        DEFAULT_BLACK_OOO_ALL_SQUARES.add(Square.D8);
        DEFAULT_BLACK_OOO_ALL_SQUARES.add(Square.C8);
        DEFAULT_BLACK_OOO_ALL_SQUARES.add(Square.B8);

    }

    private Constants() {
    }

}
