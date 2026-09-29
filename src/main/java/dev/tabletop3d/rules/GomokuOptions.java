package dev.tabletop3d.rules;

public record GomokuOptions(boolean forbidDoubleThree, boolean forbidDoubleFour, boolean forbidOverline) {
    public static final GomokuOptions DEFAULT = new GomokuOptions(false, false, false);
}
