package dev.tabletop3d.rules.chinesecheckers;

public record ChineseCheckersOptions(boolean jumpOwn, boolean enterOtherCamps, boolean allPlaces) {
    public static final ChineseCheckersOptions DEFAULT = new ChineseCheckersOptions(true, true, false);
}
