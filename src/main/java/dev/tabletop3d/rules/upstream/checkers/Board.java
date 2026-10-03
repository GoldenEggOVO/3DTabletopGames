// Adapted from Stephane Coutant, GPL-3.0-or-later; see META-INF/licenses.
/*
* Copyright (C) 2012- stephane coutant
*
* This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
* without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
* See the GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>
*/

package dev.tabletop3d.rules.upstream.checkers;

public class Board {
    private static final boolean[][] holeJI = {
        { false, false, false, false, false, false, true, false, false, false, false, false, false }, // 0

            { false, false, false, false, false,  true, true, false, false, false, false, false, false }, // 1

        { false, false, false, false, false,  true, true,  true, false, false, false, false, false }, // 2

            { false, false, false, false,  true,  true, true,  true, false, false, false, false, false }, // 3

        {  true,  true,  true,  true,  true,  true, true,  true,  true,  true,  true,  true,  true }, // 4

            {  true,  true,  true,  true,  true,  true, true,  true,  true,  true,  true,  true, false }, // 5

        { false,  true,  true,  true,  true,  true, true,  true,  true,  true,  true,  true, false }, // 6

            { false,  true,  true,  true,  true,  true, true,  true,  true,  true,  true, false, false }, // 7

        { false, false,  true,  true,  true,  true, true,  true,  true,  true,  true, false, false }, // 8 ----------------------

            { false,  true,  true,  true,  true,  true, true,  true,  true,  true,  true, false, false }, // 7

        { false,  true,  true,  true,  true,  true, true,  true,  true,  true,  true,  true, false }, // 6

            {  true,  true,  true,  true,  true,  true, true,  true,  true,  true,  true,  true, false }, // 5

        {  true,  true,  true,  true,  true,  true, true,  true,  true,  true,  true,  true,  true }, // 4

            { false, false, false, false,  true,  true, true,  true, false, false, false, false, false }, // 3

        { false, false, false, false, false,  true, true,  true, false, false, false, false, false }, // 2

            { false, false, false, false, false,  true, true, false, false, false, false, false, false }, // 1

        { false, false, false, false, false, false, true, false, false, false, false, false, false }, // 0
        };

    public static final int sizeI = 13;
    public static final int sizeJ = 17;

    public static boolean hole(Point point) {
        return point.i >= 0 && point.i < sizeI && point.j >= 0 && point.j < sizeJ
                && holeJI[point.j][point.i];
    }

    /**
     * Hop to neighbor hole in provided direction @param d
     *<p>   0  1
     *<p> 5  *  2
     *<p>   4  3
     * @return the new Point identified by a hop in provided direction @param dir.
     * Or null if hopping out of board.
     * <p> Does not check if target happens to be filled with a ball
     */
    public final Point hop(Point p, int d) {
        if (p==null || d<0 || d> 5) return null;
        Point t = p.clone();
        switch (d) {
            case 0: t.decrement();  t.j--;  break;
            case 1: t.increment();  t.j--;  break;
            case 2: t.i++;  break;
            case 3: t.increment();  t.j++;  break;
            case 4: t.decrement();  t.j++;  break;
            case 5: t.i--;  break;
        }
        return hole(t) ? t : null;
    }

}
