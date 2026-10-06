
package dev.tabletop3d.rules.xiangqi.engine.model;

import dev.tabletop3d.rules.xiangqi.engine.utility.Point;

import java.util.List;

public abstract class Piece {

    protected int color;

    protected int code;

    public Piece(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    public int getCode() {
        return code;
    }

    public abstract boolean canMove(Board board, Point start, Point end);

    public abstract List<Point> validMoves(Board board, Point start);

    public abstract Piece copy();
}
