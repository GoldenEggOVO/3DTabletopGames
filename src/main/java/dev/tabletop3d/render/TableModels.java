package dev.tabletop3d.render;

import dev.tabletop3d.interaction.GameWorld;
import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.ui.GameSymbols;

import org.bukkit.Material;

import java.util.*;

public final class TableModels {
    public record Part(double x, double y, double z, double w, double h, double d, Material material) {}

    static final Material[] COLORS = {
        Material.RED_CONCRETE,
        Material.LIGHT_BLUE_CONCRETE,
        Material.GREEN_CONCRETE,
        Material.YELLOW_CONCRETE,
        Material.PURPLE_CONCRETE,
        Material.PINK_CONCRETE
    };

    public static List<Part> piece(String kind, Cell cell, Map<String, String> publicInfo) {
        List<Part> parts = new ArrayList<>();
        int owner = cell.owner();
        Material color = COLORS[Math.floorMod(GameWorld.actualColor(publicInfo, owner), 6)];
        switch (kind) {
            case "gomoku", "go", "go9", "go13", "reversi" -> {
                Material stone = owner == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE;
                disc(parts, stone, .76, .13);
                if (cell.piece().contains("×")) {
                    parts.addAll(deadStoneMarks());
                }
            }
            case "draughts" -> {
                Material stone = owner == 0 ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_QUARTZ;
                disc(parts, stone, .76, .19);
                if (cell.piece().equals(GameSymbols.KING)) {
                    disc(parts, Material.GOLD_BLOCK, .54, .30);
                    box(parts, 0, .31, 0, .18, .07, .18, stone);
                }
            }
            case "xiangqi" -> {
                roundedDisc(parts, Material.STRIPPED_BIRCH_WOOD, .78, .19);
            }
            case "checkers" -> {
                roundedDisc(parts, color, .68, .18);
                box(parts, 0, .18, 0, .38, .20, .38, color);
                box(parts, -.07, .38, -.07, .15, .035, .15, Material.WHITE_CONCRETE);
            }
            case "ludo" -> {
                roundedDisc(parts, color, .64, .12);
                box(parts, 0, .12, 0, .34, .27, .34, color);
                box(parts, 0, .39, 0, .24, .08, .24, color);
                box(parts, 0, .47, 0, .36, .30, .46, color);
                for (double side : new double[] {-.21, .21}) {
                    box(parts, side, .51, 0, .06, .22, .34, color);
                }
            }
            case "chess" -> {
                Material body = owner == 0 ? Material.SMOOTH_QUARTZ : Material.POLISHED_BLACKSTONE;
                Material trim = owner == 0 ? Material.GOLD_BLOCK : Material.COPPER_BLOCK;
                disc(parts, body, .76, .12);
                box(parts, 0, .13, 0, .49, .10, .49, body);
                box(parts, 0, .23, 0, .28, .28, .28, body);
                chessCrown(parts, cell, body, trim);
            }
            default -> throw new IllegalArgumentException(kind);
        }
        return List.copyOf(parts);
    }

    private static void chessCrown(List<Part> parts, Cell cell, Material body, Material trim) {
        switch (cell.piece()) {
            case GameSymbols.PAWN -> {
                box(parts, 0, .49, 0, .39, .24, .39, body);
            }
            case GameSymbols.ROOK -> {
                box(parts, 0, .50, 0, .57, .14, .57, body);
                for (double x : new double[] {-.20, .20}) {
                    for (double z : new double[] {-.20, .20}) {
                        box(parts, x, .64, z, .16, .16, .16, body);
                    }
                }
            }
            case GameSymbols.HORSE -> {
                box(parts, 0, .51, .04, .28, .19, .30, body);
                box(parts, 0, .70, -.15, .28, .22, .45, body);
                box(parts, 0, .92, .02, .25, .12, .11, body);
            }
            case GameSymbols.ELEPHANT -> {
                box(parts, 0, .51, 0, .46, .08, .46, trim);
                box(parts, 0, .59, 0, .33, .25, .33, body);
                box(parts, 0, .84, 0, .16, .13, .16, body);
            }
            case GameSymbols.QUEEN -> {
                box(parts, 0, .51, 0, .42, .25, .42, body);
                box(parts, 0, .76, 0, .55, .08, .55, trim);
                for (double x : new double[] {-.19, .19}) {
                    box(parts, x, .84, 0, .13, .15, .34, body);
                }
            }
            case GameSymbols.KING -> {
                box(parts, 0, .51, 0, .44, .28, .44, body);
                box(parts, 0, .79, 0, .15, .32, .15, trim);
                for (double side : new double[] {-.1375, .1375}) {
                    box(parts, side, .93, 0, .125, .10, .15, trim);
                }
            }
            default -> throw new IllegalArgumentException("Unknown chess piece " + cell.piece());
        }
    }

    public static void disc(List<Part> parts, Material material, double width, double height) {

        box(parts, 0, 0, 0, width * .68, height, width, material);
        for (double side : new double[] {-.42, .42}) {
            box(parts, side * width, 0, 0, width * .16, height, width * .68, material);
        }
    }

    static void roundedDisc(List<Part> parts, Material material, double width, double height) {
        box(parts, 0, 0, 0, width * .4, height, width, material);
        for (double side : new double[] {-1, 1}) {
            box(parts, side * width * .3, 0, 0, width * .2, height, width * .88, material);
            box(parts, side * width * .45, 0, 0, width * .1, height, width * .60, material);
        }
    }

    public static List<Part> deadStoneMarks() {
        return List.of(
                new Part(0, .16, 0, .65, .035, .10, Material.RED_CONCRETE),
                new Part(0, .16, -.1875, .10, .035, .275, Material.RED_CONCRETE),
                new Part(0, .16, .1875, .10, .035, .275, Material.RED_CONCRETE));
    }

    static List<Part> connectFour(int owner) {
        List<Part> parts = new ArrayList<>();
        Material material = owner == 0 ? Material.RED_CONCRETE : Material.YELLOW_CONCRETE;
        double[] widths = {.13, .21, .24, .21, .13};
        for (int strip = 0; strip < widths.length; strip++) {
            box(parts, 0, strip * .048, 0, widths[strip], .048, .085, material);
        }
        return List.copyOf(parts);
    }

    public static List<Part> reversi() {
        List<Part> parts = new ArrayList<>();
        for (int side = 0; side < 2; side++) {
            Material material = side == 0 ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE;
            double y = side * .065;
            box(parts, 0, y, 0, .52, .065, .76, material);
            for (double x : new double[] {-.32, .32}) {
                box(parts, x, y, 0, .12, .065, .52, material);
            }
        }
        return List.copyOf(parts);
    }

    static void box(
            List<Part> parts,
            double x,
            double y,
            double z,
            double w,
            double h,
            double d,
            Material material) {
        parts.add(new Part(x, y, z, w, h, d, material));
    }
}
