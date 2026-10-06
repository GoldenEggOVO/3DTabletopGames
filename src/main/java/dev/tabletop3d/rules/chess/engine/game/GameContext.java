
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

package dev.tabletop3d.rules.chess.engine.game;

import dev.tabletop3d.rules.chess.engine.CastleRight;
import dev.tabletop3d.rules.chess.engine.Constants;
import dev.tabletop3d.rules.chess.engine.File;
import dev.tabletop3d.rules.chess.engine.Rank;
import dev.tabletop3d.rules.chess.engine.Side;
import dev.tabletop3d.rules.chess.engine.Square;
import dev.tabletop3d.rules.chess.engine.move.Move;

import java.util.ArrayList;
import java.util.List;

public class GameContext {

    protected Move whiteoo;

    protected Move whiteooo;

    protected Move blackoo;

    protected Move blackooo;

    protected Move whiteRookoo;

    protected Move whiteRookooo;

    protected Move blackRookoo;

    protected Move blackRookooo;

    protected List<Square> whiteooSquares;

    protected List<Square> whiteoooSquares;

    protected List<Square> blackooSquares;

    protected List<Square> blackoooSquares;

    protected long whiteooSquaresBb;

    protected long whiteoooSquaresBb;

    protected long blackooSquaresBb;

    protected long blackoooSquaresBb;

    protected long whiteooAllSquaresBb;

    protected long whiteoooAllSquaresBb;

    protected long blackooAllSquaresBb;

    protected long blackoooAllSquaresBb;

    protected String startFEN;

    protected File whiteRookooFile;

    protected File whiteRookoooFile;

    protected File blackRookooFile;

    protected File blackRookoooFile;

    protected VariationType variationType;

    public GameContext() {
        setVariationType(VariationType.NORMAL);
        loadDefaults();
    }

    private static long squareListToBb(List<Square> list) {
        long r = 0L;
        for (Square s : list) {
            r |= s.getBitboard();
        }
        return r;
    }

    private void loadDefaults() {

        setWhiteoo(Constants.DEFAULT_WHITE_OO);
        setWhiteooo(Constants.DEFAULT_WHITE_OOO);
        setBlackoo(Constants.DEFAULT_BLACK_OO);
        setBlackooo(Constants.DEFAULT_BLACK_OOO);

        setWhiteRookoo(Constants.DEFAULT_WHITE_ROOK_OO);
        setWhiteRookooo(Constants.DEFAULT_WHITE_ROOK_OOO);
        setBlackRookoo(Constants.DEFAULT_BLACK_ROOK_OO);
        setBlackRookooo(Constants.DEFAULT_BLACK_ROOK_OOO);

        setWhiteooSquares(Constants.DEFAULT_WHITE_OO_SQUARES);
        setWhiteoooSquares(Constants.DEFAULT_WHITE_OOO_SQUARES);
        setBlackooSquares(Constants.DEFAULT_BLACK_OO_SQUARES);
        setBlackoooSquares(Constants.DEFAULT_BLACK_OOO_SQUARES);

        setWhiteooSquaresBb(squareListToBb(Constants.DEFAULT_WHITE_OO_SQUARES));
        setWhiteoooSquaresBb(squareListToBb(Constants.DEFAULT_WHITE_OOO_SQUARES));
        setBlackooSquaresBb(squareListToBb(Constants.DEFAULT_BLACK_OO_SQUARES));
        setBlackoooSquaresBb(squareListToBb(Constants.DEFAULT_BLACK_OOO_SQUARES));

        setWhiteooAllSquaresBb(squareListToBb(Constants.DEFAULT_WHITE_OO_ALL_SQUARES));
        setWhiteoooAllSquaresBb(squareListToBb(Constants.DEFAULT_WHITE_OOO_ALL_SQUARES));
        setBlackooAllSquaresBb(squareListToBb(Constants.DEFAULT_BLACK_OO_ALL_SQUARES));
        setBlackoooAllSquaresBb(squareListToBb(Constants.DEFAULT_BLACK_OOO_ALL_SQUARES));

        setStartFEN(Constants.startStandardFENPosition);
    }

    public void loadChess960(Square whiteKing, Square whiteRookOO, Square whiteRookOOO,
                             Square blackKing, Square blackRookOO, Square blackRookOOO) {
        setVariationType(VariationType.CHESS960);

        setWhiteoo(new Move(whiteKing, Square.G1));
        setWhiteooo(new Move(whiteKing, Square.C1));
        setBlackoo(new Move(blackKing, Square.G8));
        setBlackooo(new Move(blackKing, Square.C8));

        if (whiteRookOO != null) {
            setWhiteRookoo(new Move(whiteRookOO, Square.F1));
            whiteRookooFile = whiteRookOO.getFile();
        }
        if (whiteRookOOO != null) {
            setWhiteRookooo(new Move(whiteRookOOO, Square.D1));
            whiteRookoooFile = whiteRookOOO.getFile();
        }
        if (blackRookOO != null) {
            setBlackRookoo(new Move(blackRookOO, Square.F8));
            blackRookooFile = blackRookOO.getFile();
        }
        if (blackRookOOO != null) {
            setBlackRookooo(new Move(blackRookOOO, Square.D8));
            blackRookoooFile = blackRookOOO.getFile();
        }

        setWhiteooSquares(computeKingPassSquares(whiteKing, Square.G1));
        setWhiteoooSquares(computeKingPassSquares(whiteKing, Square.C1));
        setBlackooSquares(computeKingPassSquares(blackKing, Square.G8));
        setBlackoooSquares(computeKingPassSquares(blackKing, Square.C8));

        setWhiteooSquaresBb(squareListToBb(getWhiteooSquares()));
        setWhiteoooSquaresBb(squareListToBb(getWhiteoooSquares()));
        setBlackooSquaresBb(squareListToBb(getBlackooSquares()));
        setBlackoooSquaresBb(squareListToBb(getBlackoooSquares()));

        setWhiteooAllSquaresBb(computeAllEmptySquaresBb(whiteKing, Square.G1, whiteRookOO, Square.F1));
        setWhiteoooAllSquaresBb(computeAllEmptySquaresBb(whiteKing, Square.C1, whiteRookOOO, Square.D1));
        setBlackooAllSquaresBb(computeAllEmptySquaresBb(blackKing, Square.G8, blackRookOO, Square.F8));
        setBlackoooAllSquaresBb(computeAllEmptySquaresBb(blackKing, Square.C8, blackRookOOO, Square.D8));
    }

    private static List<Square> computeKingPassSquares(Square kingFrom, Square kingTo) {
        List<Square> squares = new ArrayList<>();
        if (kingFrom == null || kingTo == null) return squares;
        int fromFile = kingFrom.getFile().ordinal();
        int toFile = kingTo.getFile().ordinal();
        Rank rank = kingFrom.getRank();
        if (fromFile == toFile) {

            squares.add(kingTo);
            return squares;
        }
        int step = fromFile < toFile ? 1 : -1;

        for (int f = fromFile + step; ; f += step) {
            squares.add(Square.encode(rank, File.allFiles[f]));
            if (f == toFile) break;
        }
        return squares;
    }

    private static long computeAllEmptySquaresBb(Square kingFrom, Square kingTo,
                                                  Square rookFrom, Square rookTo) {
        if (kingFrom == null || kingTo == null || rookFrom == null || rookTo == null) return 0L;
        long bb = 0L;
        Rank rank = kingFrom.getRank();

        int kf1 = Math.min(kingFrom.getFile().ordinal(), kingTo.getFile().ordinal());
        int kf2 = Math.max(kingFrom.getFile().ordinal(), kingTo.getFile().ordinal());
        for (int f = kf1; f <= kf2; f++) {
            bb |= Square.encode(rank, File.allFiles[f]).getBitboard();
        }

        int rf1 = Math.min(rookFrom.getFile().ordinal(), rookTo.getFile().ordinal());
        int rf2 = Math.max(rookFrom.getFile().ordinal(), rookTo.getFile().ordinal());
        for (int f = rf1; f <= rf2; f++) {
            bb |= Square.encode(rank, File.allFiles[f]).getBitboard();
        }

        bb &= ~kingFrom.getBitboard();
        bb &= ~rookFrom.getBitboard();

        return bb;
    }

    public File getWhiteRookooFile() { return whiteRookooFile; }

    public File getWhiteRookoooFile() { return whiteRookoooFile; }

    public File getBlackRookooFile() { return blackRookooFile; }

    public File getBlackRookoooFile() { return blackRookoooFile; }

    public Move getKingCastleMove(Side side, CastleRight castleRight) {
        Move move = null;
        if (Side.WHITE.equals(side)) {
            if (CastleRight.KING_SIDE.equals(castleRight)) {
                move = getWhiteoo();
            } else if (CastleRight.QUEEN_SIDE.equals(castleRight)) {
                move = getWhiteooo();
            }
        } else {
            if (CastleRight.KING_SIDE.equals(castleRight)) {
                move = getBlackoo();
            } else if (CastleRight.QUEEN_SIDE.equals(castleRight)) {
                move = getBlackooo();
            }
        }
        return move;
    }

    public Move getRookCastleMove(Side side, CastleRight castleRight) {
        Move move = null;
        if (Side.WHITE.equals(side)) {
            if (CastleRight.KING_SIDE.equals(castleRight)) {
                move = getWhiteRookoo();
            } else if (CastleRight.QUEEN_SIDE.equals(castleRight)) {
                move = getWhiteRookooo();
            }
        } else {
            if (CastleRight.KING_SIDE.equals(castleRight)) {
                move = getBlackRookoo();
            } else if (CastleRight.QUEEN_SIDE.equals(castleRight)) {
                move = getBlackRookooo();
            }
        }
        return move;
    }

    public boolean isCastleMove(final Move move) {
        if (move.equals(getWhiteoo()) ||
                move.equals(getWhiteooo()) ||
                move.equals(getBlackoo()) ||
                move.equals(getBlackooo())) {
            return true;
        }

        if (getVariationType() == VariationType.CHESS960) {
            if (whiteRookoo != null && move.getFrom().equals(getWhiteoo().getFrom())
                    && move.getTo().equals(whiteRookoo.getFrom())) {
                return true;
            }
            if (whiteRookooo != null && move.getFrom().equals(getWhiteooo().getFrom())
                    && move.getTo().equals(whiteRookooo.getFrom())) {
                return true;
            }
            if (blackRookoo != null && move.getFrom().equals(getBlackoo().getFrom())
                    && move.getTo().equals(blackRookoo.getFrom())) {
                return true;
            }
            if (blackRookooo != null && move.getFrom().equals(getBlackooo().getFrom())
                    && move.getTo().equals(blackRookooo.getFrom())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasCastleRight(final Move move, final CastleRight castleRight) {

        final CastleRight r = castleRight;

        return (CastleRight.KING_AND_QUEEN_SIDE.equals(r)) ||
                (move.equals(getWhiteoo()) && CastleRight.KING_SIDE.equals(r)) ||
                (move.equals(getBlackoo()) && CastleRight.KING_SIDE.equals(r)) ||
                (move.equals(getWhiteooo()) && CastleRight.QUEEN_SIDE.equals(r)) ||
                (move.equals(getBlackooo()) && CastleRight.QUEEN_SIDE.equals(r));
    }

    public boolean isKingSideCastle(Move move) {
        if (move.equals(getWhiteoo()) || move.equals(getBlackoo())) {
            return true;
        }

        if (getVariationType() == VariationType.CHESS960) {
            if (whiteRookoo != null && move.getFrom().equals(getWhiteoo().getFrom())
                    && move.getTo().equals(whiteRookoo.getFrom())) {
                return true;
            }
            if (blackRookoo != null && move.getFrom().equals(getBlackoo().getFrom())
                    && move.getTo().equals(blackRookoo.getFrom())) {
                return true;
            }
        }
        return false;
    }

    public boolean isQueenSideCastle(Move move) {
        return move.equals(getWhiteooo()) ||
                move.equals(getBlackooo());
    }

    public Move getWhiteoo() {
        return whiteoo;
    }

    public void setWhiteoo(Move whiteoo) {
        this.whiteoo = whiteoo;
    }

    public Move getWhiteooo() {
        return whiteooo;
    }

    public void setWhiteooo(Move whiteooo) {
        this.whiteooo = whiteooo;
    }

    public Move getBlackoo() {
        return blackoo;
    }

    public void setBlackoo(Move blackoo) {
        this.blackoo = blackoo;
    }

    public Move getBlackooo() {
        return blackooo;
    }

    public void setBlackooo(Move blackooo) {
        this.blackooo = blackooo;
    }

    public Move getWhiteRookoo() {
        return whiteRookoo;
    }

    public void setWhiteRookoo(Move whiteRookoo) {
        this.whiteRookoo = whiteRookoo;
    }

    public Move getWhiteRookooo() {
        return whiteRookooo;
    }

    public void setWhiteRookooo(Move whiteRookooo) {
        this.whiteRookooo = whiteRookooo;
    }

    public Move getBlackRookoo() {
        return blackRookoo;
    }

    public void setBlackRookoo(Move blackRookoo) {
        this.blackRookoo = blackRookoo;
    }

    public Move getBlackRookooo() {
        return blackRookooo;
    }

    public void setBlackRookooo(Move blackRookooo) {
        this.blackRookooo = blackRookooo;
    }

    public String getStartFEN() {
        return startFEN;
    }

    public void setStartFEN(String startFEN) {
        this.startFEN = startFEN;
    }

    public VariationType getVariationType() {
        return variationType;
    }

    public void setVariationType(VariationType variationType) {
        this.variationType = variationType;
    }

    public List<Square> getWhiteooSquares() {
        return whiteooSquares;
    }

    public void setWhiteooSquares(List<Square> whiteooSquares) {
        this.whiteooSquares = whiteooSquares;
    }

    public List<Square> getWhiteoooSquares() {
        return whiteoooSquares;
    }

    public void setWhiteoooSquares(List<Square> whiteoooSquares) {
        this.whiteoooSquares = whiteoooSquares;
    }

    public List<Square> getBlackooSquares() {
        return blackooSquares;
    }

    public void setBlackooSquares(List<Square> blackooSquares) {
        this.blackooSquares = blackooSquares;
    }

    public List<Square> getBlackoooSquares() {
        return blackoooSquares;
    }

    public void setBlackoooSquares(List<Square> blackoooSquares) {
        this.blackoooSquares = blackoooSquares;
    }

    public long getWhiteooSquaresBb() {
        return whiteooSquaresBb;
    }

    public void setWhiteooSquaresBb(long whiteooSquaresBb) {
        this.whiteooSquaresBb = whiteooSquaresBb;
    }

    public long getWhiteoooSquaresBb() {
        return whiteoooSquaresBb;
    }

    public void setWhiteoooSquaresBb(long whiteoooSquaresBb) {
        this.whiteoooSquaresBb = whiteoooSquaresBb;
    }

    public long getBlackooSquaresBb() {
        return blackooSquaresBb;
    }

    public void setBlackooSquaresBb(long blackooSquaresBb) {
        this.blackooSquaresBb = blackooSquaresBb;
    }

    public long getBlackoooSquaresBb() {
        return blackoooSquaresBb;
    }

    public void setBlackoooSquaresBb(long blackoooSquaresBb) {
        this.blackoooSquaresBb = blackoooSquaresBb;
    }

    public long getWhiteooAllSquaresBb() {
        return whiteooAllSquaresBb;
    }

    public GameContext setWhiteooAllSquaresBb(long whiteooAllSquaresBb) {
        this.whiteooAllSquaresBb = whiteooAllSquaresBb;
        return this;
    }

    public long getWhiteoooAllSquaresBb() {
        return whiteoooAllSquaresBb;
    }

    public GameContext setWhiteoooAllSquaresBb(long whiteoooAllSquaresBb) {
        this.whiteoooAllSquaresBb = whiteoooAllSquaresBb;
        return this;
    }

    public long getBlackooAllSquaresBb() {
        return blackooAllSquaresBb;
    }

    public GameContext setBlackooAllSquaresBb(long blackooAllSquaresBb) {
        this.blackooAllSquaresBb = blackooAllSquaresBb;
        return this;
    }

    public long getBlackoooAllSquaresBb() {
        return blackoooAllSquaresBb;
    }

    public GameContext setBlackoooAllSquaresBb(long blackoooAllSquaresBb) {
        this.blackoooAllSquaresBb = blackoooAllSquaresBb;
        return this;
    }

    public Move getoo(Side side) {
        return Side.WHITE.equals(side) ? getWhiteoo() : getBlackoo();
    }

    public Move getooo(Side side) {
        return Side.WHITE.equals(side) ? getWhiteooo() : getBlackooo();
    }

    public Move getRookoo(Side side) {
        return Side.WHITE.equals(side) ? getWhiteRookoo() : getBlackRookoo();
    }

    public Move getRookooo(Side side) {
        return Side.WHITE.equals(side) ? getWhiteRookooo() : getBlackRookooo();
    }

    public List<Square> getooSquares(Side side) {
        return Side.WHITE.equals(side) ?
                getWhiteooSquares() : getBlackooSquares();
    }

    public List<Square> getoooSquares(Side side) {
        return Side.WHITE.equals(side) ?
                getWhiteoooSquares() : getBlackoooSquares();
    }

    public long getooSquaresBb(Side side) {
        return Side.WHITE.equals(side) ?
                getWhiteooSquaresBb() : getBlackooSquaresBb();
    }

    public long getoooSquaresBb(Side side) {
        return Side.WHITE.equals(side) ?
                getWhiteoooSquaresBb() : getBlackoooSquaresBb();
    }

    public long getooAllSquaresBb(Side side) {
        return Side.WHITE.equals(side) ?
                getWhiteooAllSquaresBb() : getBlackooAllSquaresBb();
    }

    public long getoooAllSquaresBb(Side side) {
        return Side.WHITE.equals(side) ?
                getWhiteoooAllSquaresBb() : getBlackoooAllSquaresBb();
    }

}
