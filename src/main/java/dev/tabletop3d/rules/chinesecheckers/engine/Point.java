// Adapted from Stephane Coutant, GPL-3.0-or-later; see META-INF/licenses.
package dev.tabletop3d.rules.chinesecheckers.engine;

public class Point {
    public int i;
    public int j;

    public Point(int i, int j){
        this.i = i ;
        this.j = j;
    }
    public Point clone() {
        return new Point(i,j);
    }

    public String toString(){
        return String.format("(%d, %d)", i, j);
    }

    /** --> */
    public void increment() {
        if ( odd(j)) i++;
    }
    /** <-- */
    public void decrement() {
        if ( even(j)) i--;
    }

    private static boolean odd(int value) {
        return (value % 2 > 0);
    }
    private static boolean even(int value) {
        return (value % 2 == 0);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Point other)) return false;
        return (i==other.i) && (j==other.j);
    }

    @Override public int hashCode() { return 31 * i + j; }

}
