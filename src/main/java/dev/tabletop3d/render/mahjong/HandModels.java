package dev.tabletop3d.render.mahjong;

import dev.tabletop3d.ui.GameSymbols;

import org.bukkit.Material;

import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Original native cuboid artwork. Coordinates are centers on a 32 by 48 face. */
public final class HandModels {
    public record Part(
            double x,
            double y,
            double w,
            double h,
            double roll,
            int layer,
            Material material,
            int plane) {
        Part(double x, double y, double w, double h, double roll, int layer, Material material) {
            this(x, y, w, h, roll, layer, material, 0);
        }

        public double relief() {
            return layer * .0012 + plane * .0002;
        }
    }

    private static final Map<String, List<Part>> CACHE = new ConcurrentHashMap<>();
    private static final Material CREAM = Material.SMOOTH_QUARTZ,
            INK = Material.BLACK_CONCRETE,
            RED = Material.RED_CONCRETE,
            BLUE = Material.BLUE_CONCRETE,
            GREEN = Material.GREEN_CONCRETE;

    public static List<Part> of(String face) {
        return CACHE.computeIfAbsent(
                face.isEmpty() ? "back" : face,
                key -> {
                    List<Part> parts = new ArrayList<>();
                    tile(parts, key);
                    return separatePlanes(parts);
                });
    }

    private static List<Part> separatePlanes(List<Part> source) {
        // Reuse a relief plane for disjoint strokes; intersecting strokes get distinct depths.
        List<Part> parts = new ArrayList<>();
        List<Area> footprints = new ArrayList<>();
        for (Part part : new LinkedHashSet<>(source)) {
            AffineTransform transform = AffineTransform.getTranslateInstance(part.x(), part.y());
            transform.rotate(part.roll());
            Area footprint =
                    new Area(
                            transform.createTransformedShape(
                                    new Rectangle2D.Double(
                                            -part.w() / 2, -part.h() / 2, part.w(), part.h())));
            BitSet occupied = new BitSet();
            for (int index = 0; index < parts.size(); index++) {
                Part previous = parts.get(index);
                if (previous.layer() != part.layer()) {
                    continue;
                }
                Area overlap = new Area(footprint);
                overlap.intersect(footprints.get(index));
                if (!overlap.isEmpty()
                        && overlap.getBounds2D().getWidth() >= .001
                        && overlap.getBounds2D().getHeight() >= .001) {
                    occupied.set(previous.plane());
                }
            }
            parts.add(
                    new Part(
                            part.x(),
                            part.y(),
                            part.w(),
                            part.h(),
                            part.roll(),
                            part.layer(),
                            part.material(),
                            occupied.nextClearBit(0)));
            footprints.add(footprint);
        }
        return List.copyOf(parts);
    }

    private static void box(
            List<Part> parts,
            double x,
            double y,
            double w,
            double h,
            double roll,
            int layer,
            Material material) {
        parts.add(new Part(x, y, w, h, roll, layer, material));
    }

    private static void line(
            List<Part> parts,
            double x1,
            double y1,
            double x2,
            double y2,
            double thickness,
            int layer,
            Material material) {
        box(
                parts,
                (x1 + x2) / 2,
                (y1 + y2) / 2,
                Math.hypot(x2 - x1, y2 - y1) + thickness / 3,
                thickness,
                Math.atan2(y2 - y1, x2 - x1),
                layer,
                material);
    }

    private static void path(
            List<Part> parts,
            double x,
            double y,
            double w,
            double h,
            double thickness,
            int layer,
            Material material,
            double... points) {
        for (int index = 2; index < points.length; index += 2) {
            line(
                    parts,
                    x + points[index - 2] * w,
                    y + points[index - 1] * h,
                    x + points[index] * w,
                    y + points[index + 1] * h,
                    thickness,
                    layer,
                    material);
        }
    }

    private static void circle(
            List<Part> parts, double x, double y, double radius, int layer, Material material) {
        for (int index = 0; index < 4; index++) {
            box(
                    parts,
                    x,
                    y,
                    2 * radius * Math.cos(Math.PI / 8),
                    2 * radius * Math.sin(Math.PI / 8),
                    index * Math.PI / 4,
                    layer,
                    material);
        }
    }

    private static void ring(
            List<Part> parts,
            double x,
            double y,
            double rx,
            double ry,
            double thickness,
            int layer,
            Material material,
            int segments) {
        for (int index = 0; index < segments; index++) {
            double startAngle = index * 2 * Math.PI / segments,
                    endAngle = (index + 1) * 2 * Math.PI / segments;
            line(
                    parts,
                    x + rx * Math.cos(startAngle),
                    y + ry * Math.sin(startAngle),
                    x + rx * Math.cos(endAngle),
                    y + ry * Math.sin(endAngle),
                    thickness,
                    layer,
                    material);
        }
    }

    private static void rounded(
            List<Part> parts,
            double x,
            double y,
            double w,
            double h,
            double radius,
            int layer,
            Material material) {
        box(parts, x, y, w - 2 * radius, h, 0, layer, material);
        box(parts, x, y, w, h - 2 * radius, 0, layer, material);
        for (int dx : new int[] {-1, 1}) {
            for (int dy : new int[] {-1, 1}) {
                circle(
                        parts,
                        x + dx * (w / 2 - radius),
                        y + dy * (h / 2 - radius),
                        radius,
                        layer,
                        material);
            }
        }
    }

    /** Hand-drawn character centerlines; each stroke is a rotated native cuboid. */
    private static void glyph(
            List<Part> parts,
            char glyph,
            double x,
            double y,
            double w,
            double h,
            double thickness,
            Material material) {
        double[][] strokes = glyphStrokes(glyph);
        for (double[] stroke : strokes) {
            path(parts, x, y, w / 10, h / 10, thickness, 1, material, stroke);
        }
    }

    private static double[][] glyphStrokes(char glyph) {
        return switch (String.valueOf(glyph)) {
            case GameSymbols.ONE -> new double[][] {{0, 5.4, 10, 4.8}};
            case GameSymbols.TWO -> new double[][] {{1.3, 2.7, 8.8, 2.3}, {0, 8, 10, 7.7}};
            case GameSymbols.THREE ->
                    new double[][] {{.6, 1.6, 9.4, 1.2}, {1.5, 5, 8.4, 4.6}, {0, 8.7, 10, 8.4}};
            case GameSymbols.FOUR ->
                    new double[][] {
                        {.8, 1, .8, 9.1},
                        {.8, 1, 9.2, .8, 9.2, 9.1},
                        {.8, 9.1, 9.2, 9.1},
                        {3.6, 1.3, 3.5, 4.5, 2.1, 6.5},
                        {6.7, 1.3, 6.7, 6, 8.5, 6}
                    };
            case GameSymbols.FIVE ->
                    new double[][] {
                        {1, 1, 9, 1},
                        {4.9, 1, 3.5, 9},
                        {1.5, 4.8, 8, 4.5, 7.8, 9},
                        {0, 9.1, 10, 8.8}
                    };
            case GameSymbols.SIX ->
                    new double[][] {
                        {4.5, .5, 5.5, 2.2},
                        {0, 3.4, 10, 3.1},
                        {3.6, 5.3, 2.4, 7.8, .7, 9.5},
                        {6.5, 5.2, 8.2, 7.7, 9.3, 9.5}
                    };
            case GameSymbols.SEVEN ->
                    new double[][] {
                        {0, 4.4, 10, 2.8}, {4.2, .3, 4.1, 8.1, 4.7, 9.3, 8.8, 9.3, 9.4, 8.1}
                    };
            case GameSymbols.EIGHT ->
                    new double[][] {
                        {3.4, 1, 3, 4.7, 1.8, 7.7, .3, 9.4},
                        {6.1, 1, 6.7, 4.8, 8, 7.6, 9.8, 9.3}
                    };
            case GameSymbols.NINE ->
                    new double[][] {
                        {.3, 3.1, 7.6, 2.6, 7.5, 8.7, 8.1, 9.4, 9.6, 9.3, 10, 8.1},
                        {3.5, .1, 3.3, 5.6, 2.2, 8.3, .3, 9.8}
                    };
            case GameSymbols.CHARACTERS ->
                    new double[][] {
                        {0, 1, 10, .8},
                        {2.6, 0, 2.6, 1.9},
                        {7.4, 0, 7.4, 1.9},
                        {1.4, 2.6, 8.6, 2.6, 8.6, 5.7, 1.4, 5.7, 1.4, 2.6},
                        {1.4, 4.1, 8.6, 4.1},
                        {5, 2.6, 5, 8.8},
                        {.5, 9.7, .5, 7, 9.5, 7, 9.5, 9.8, 8.4, 9.7},
                        {1.9, 8.8, 8.1, 8.8},
                        {7.9, 7.9, 8.5, 9.4}
                    };
            case GameSymbols.EAST ->
                    new double[][] {
                        {0, 1.8, 10, 1.5},
                        {5, 0, 5, 10},
                        {1.5, 3.3, 8.5, 3.3, 8.5, 6.5, 1.5, 6.5, 1.5, 3.3},
                        {1.5, 4.9, 8.5, 4.9},
                        {4.9, 6.4, 3, 8.3, 0, 9.7},
                        {5.1, 6.4, 7, 8.3, 10, 9.7}
                    };
            case GameSymbols.SOUTH ->
                    new double[][] {
                        {0, 1.6, 10, 1.4},
                        {5, 0, 5, 3.2},
                        {.8, 9.8, .8, 3.2, 9.2, 3.2, 9.2, 9.8, 8, 9.6},
                        {3, 3.7, 3.9, 4.8},
                        {7, 3.6, 6, 4.9},
                        {2.4, 5.1, 7.6, 5.1},
                        {2.4, 7.1, 7.6, 7.1},
                        {5, 5.1, 5, 9.5}
                    };
            case GameSymbols.WEST ->
                    new double[][] {
                        {0, .6, 10, .4},
                        {3.5, .5, 3.4, 5.5, 2.1, 7},
                        {6.5, .5, 6.5, 6.8, 8.4, 6.8},
                        {.7, 3.1, 9.3, 3, 9.3, 9.6, .7, 9.6, .7, 3.1}
                    };
            case GameSymbols.NORTH ->
                    new double[][] {
                        {3.7, 0, 3.7, 10},
                        {0, 3.8, 3.7, 3.5},
                        {0, 8.7, 3.7, 7.3},
                        {6.1, 0, 6.1, 8.8, 6.9, 9.5, 9.4, 9.4, 9.8, 8.2},
                        {6.1, 4.3, 9.6, 2}
                    };
            case GameSymbols.GREEN_DRAGON ->
                    new double[][] {
                        {1, .5, 3.6, .5, 2, 2.3, 0, 3.9},
                        {.7, 1.4, 2.3, 2.4},
                        {6.1, 0, 7.3, 1.5, 10, 3.9},
                        {7.3, 1.5, 8.7, .2},
                        {8.2, 2.7, 9.7, 1.4},
                        {2.7, 3.8, 7.5, 3.8},
                        {.8, 5, 4.3, 5, 4.2, 6.6, 1.2, 6.6, 1.1, 8.5, 4.2, 8.5, 4.1, 9.6, 3, 9.7},
                        {5.9, 4.8, 8.5, 4.8, 8.5, 6, 9.8, 6},
                        {6, 4.8, 5.6, 6.4},
                        {5.2, 7, 9.2, 7, 7.8, 8.6, 5, 10},
                        {5.8, 7.3, 7.3, 8.7, 10, 10}
                    };
            case GameSymbols.RED_DRAGON ->
                    new double[][] {{1, 2.5, 9, 2.3, 9, 6.9, 1, 7.1, 1, 2.5}, {5, .1, 5, 10}};
            case GameSymbols.PLUM ->
                    new double[][] {
                        {2.4, 0, 2.4, 10},
                        {0, 3, 4.3, 2.7},
                        {2.4, 3.4, 0, 7.2},
                        {2.5, 4.2, 4, 6.1},
                        {6, .1, 4.7, 2.6},
                        {5.5, 1.3, 10, 1.1},
                        {5.4, 3, 9.4, 3, 8.8, 9.9, 7.6, 9.7},
                        {5.4, 3, 4.9, 8.2, 9.6, 8.2},
                        {4.3, 5.7, 10, 5.7},
                        {6.7, 3.8, 7.6, 4.7},
                        {6.4, 6.7, 7.4, 7.4}
                    };
            case GameSymbols.ORCHID ->
                    new double[][] {
                        {0, 1, 10, .8},
                        {2.6, 0, 2.6, 2},
                        {7.4, 0, 7.4, 2},
                        {.6, 10, .6, 2.8, 4.1, 2.8, 4.1, 5, .6, 5},
                        {.6, 3.9, 4.1, 3.9},
                        {5.9, 2.8, 9.4, 2.8, 9.4, 10, 8.5, 9.8},
                        {5.9, 2.8, 5.9, 5, 9.4, 5},
                        {5.9, 3.9, 9.4, 3.9},
                        {2.2, 6, 7.8, 6, 7.8, 7.8, 2.2, 7.8, 2.2, 6},
                        {5, 5.7, 5, 9.9},
                        {3.7, 8.4, 1.8, 9.8},
                        {6.2, 8.4, 8.3, 9.8}
                    };
            case GameSymbols.BAMBOO ->
                    new double[][] {
                        {2.2, 0, .2, 3.5},
                        {1.5, 1.6, 4.4, 1.4},
                        {3.1, 1.5, 3.1, 9.8},
                        {4, 2, 4.7, 3.2},
                        {7, 0, 5.4, 3.5},
                        {6.4, 1.6, 10, 1.4},
                        {8.3, 1.5, 8.3, 9.5, 7.3, 9.8}
                    };
            case GameSymbols.CHRYSANTHEMUM ->
                    new double[][] {
                        {0, 1, 10, .8},
                        {2.6, 0, 2.6, 2},
                        {7.4, 0, 7.4, 2},
                        {2.7, 2.6, .1, 5.4},
                        {2, 3.6, 9.3, 3.6, 9.3, 9.7, 8, 9.8},
                        {2.4, 5.3, 3.5, 6.6},
                        {7.1, 5.2, 6.1, 6.7},
                        {1.4, 7.2, 8, 7.2},
                        {4.7, 4.8, 4.7, 9.8},
                        {4.6, 7.4, 1.3, 9.5},
                        {4.9, 7.4, 7.8, 9.5}
                    };
            case GameSymbols.SPRING ->
                    new double[][] {
                        {1.2, 1.4, 8.8, 1.2},
                        {1.2, 3, 8.8, 2.8},
                        {0, 4.6, 10, 4.4},
                        {5, 0, 4.5, 3.6, 2.8, 5.9, 0, 7.1},
                        {6.2, 4.5, 8, 6.1, 10, 7},
                        {2.5, 6.3, 7.5, 6.3, 7.5, 9.8, 2.5, 9.8, 2.5, 6.3},
                        {2.5, 8, 7.5, 8}
                    };
            case GameSymbols.SUMMER ->
                    new double[][] {
                        {0, .5, 10, .3},
                        {5, .4, 4.2, 1.6},
                        {1.8, 1.8, 8.2, 1.8, 8.2, 5.3, 1.8, 5.3, 1.8, 1.8},
                        {1.8, 3, 8.2, 3},
                        {1.8, 4.1, 8.2, 4.1},
                        {3.6, 5.3, 1.9, 6.6, .1, 7.5},
                        {2.9, 6.3, 8.5, 6.3, 6.1, 8.3, 0, 10},
                        {2.6, 6.5, 4.8, 8.3, 10, 10}
                    };
            case GameSymbols.AUTUMN ->
                    new double[][] {
                        {.5, 1.5, 4.4, .1},
                        {2.5, 1, 2.5, 10},
                        {0, 3.8, 5, 3.5},
                        {2.4, 4.1, .1, 8},
                        {2.6, 4.8, 4.2, 6.6},
                        {5.5, 2.6, 5, 4.8},
                        {9.7, 2.6, 8.8, 4.5},
                        {7.3, .1, 7.2, 5.8, 6.3, 8.4, 4.2, 10},
                        {7.2, 5.6, 8.3, 8.3, 10, 10}
                    };
            case GameSymbols.WINTER ->
                    new double[][] {
                        {3.4, 0, 1.8, 2.5, .1, 3.8},
                        {2.6, 1.4, 8.6, 1.2, 6.6, 3.6, 3.8, 5.1, 0, 6.4},
                        {2.1, 2.7, 4.5, 4.7, 7.4, 5.8, 10, 6.2},
                        {3.6, 6.8, 6.2, 7.8},
                        {3.7, 9, 6.1, 9.9}
                    };
            default -> throw new IllegalArgumentException("Unknown tile glyph: " + glyph);
        };
    }

    private static void pip(
            List<Part> parts, double x, double y, double radius, Material material, int layer) {
        ring(parts, x, y, radius, radius, .85, layer, material, 8);
        ring(parts, x, y, radius * .56, radius * .56, .55, layer, material, 6);
        box(parts, x, y, .9, .9, Math.PI / 4, layer, material);
    }

    private static void bamboo(List<Part> parts, double x, double y, Material material) {
        line(parts, x - .4, y - 4, x + .4, y + 4, 1.9, 1, material);
        for (int index = -1; index <= 1; index++) {
            line(parts, x - 2, y + index * 3 - 1, x + 2, y + index * 3 - .6, 1.1, 1, material);
        }
        line(
                parts,
                x - .3,
                y - 2,
                x - .1,
                y + 2,
                .45,
                2,
                material == RED ? CREAM : Material.LIME_CONCRETE);
    }

    private static void bird(List<Part> parts) {
        circle(parts, 15, 26, 6.5, 1, GREEN);
        circle(parts, 20, 15, 3.3, 1, GREEN);
        path(parts, 0, 0, 1, 1, 2.1, 1, GREEN, 20, 17, 20, 22, 17, 26);
        for (int index = 0; index < 4; index++) {
            line(parts, 13 + index, 27, 6 + index * 2, 39 + index * .8, 1.3, 1, GREEN);
        }
        for (int index = 0; index < 4; index++) {
            line(parts, 11 + index * 2, 25, 15 + index * 1.4, 31, 1.1, 2, BLUE);
        }
        line(parts, 23, 15, 26, 16, 1.1, 2, RED);
        line(parts, 18, 12, 17, 9, 1.1, 2, RED);
        line(parts, 20, 12, 21, 9, 1.1, 2, RED);
        line(parts, 15, 32, 15, 40, 1, 2, RED);
        line(parts, 18, 32, 20, 39, 1, 2, RED);
        line(parts, 12, 40, 17, 40, .8, 2, RED);
        line(parts, 18, 39, 23, 39, .8, 2, RED);
        circle(parts, 21, 14, .9, 2, CREAM);
        box(parts, 21.2, 14, .5, .5, 0, 3, INK);
    }

    private static void tile(List<Part> parts, String face) {
        if (face.equals("back")) {
            rounded(parts, 16, 24, 27, 43, 2, 0, GREEN);
            return;
        }
        char suit = face.charAt(0);
        int number = face.charAt(1) - '0';
        boolean red = number == 0;
        if (red) {
            number = 5;
        }
        if (suit == 'm') {
            glyph(
                    parts,
                    GameSymbols.NUMERALS.charAt(number - 1),
                    6,
                    5,
                    20,
                    14,
                    1.25,
                    red ? RED : INK);
            glyph(parts, GameSymbols.CHARACTERS.charAt(0), 5, 24, 22, 20, 1.15, RED);
            return;
        }
        if (suit == 'z' && number != 5) {
            glyph(
                    parts,
                    GameSymbols.HONORS.charAt(number - 1),
                    5,
                    9,
                    22,
                    30,
                    1.35,
                    number == 6 ? GREEN : number == 7 ? RED : INK);
            return;
        }
        if (suit == 'p' && number == 1) {
            pip(parts, 16, 24, 11, BLUE, 1);
            ring(parts, 16, 24, 8.2, 8.2, .7, 1, BLUE, 16);
            pip(parts, 16, 24, 4.7, RED, 2);
            for (int index = 0; index < 8; index++) {
                double startAngle = index * Math.PI / 4;
                box(
                        parts,
                        16 + 6.5 * Math.cos(startAngle),
                        24 + 6.5 * Math.sin(startAngle),
                        1.2,
                        2.7,
                        startAngle,
                        1,
                        BLUE);
            }
        } else if (suit == 's' && number == 1) {
            bird(parts);
        } else if (suit == 'p' || suit == 's') {
            int[][] points = pipPositions(number);
            for (int index = 0; index < points.length; index++) {
                Material material =
                        red
                                ? RED
                                : suit == 'p'
                                        ? (number == 5 && index == 4 ? RED : BLUE)
                                        : (number == 7 && index == 0 ? RED : GREEN);
                if (suit == 'p') {
                    pip(
                            parts,
                            points[index][0],
                            points[index][1],
                            number >= 7 ? 3.2 : 4.2,
                            material,
                            1);
                } else {
                    bamboo(parts, points[index][0], points[index][1], material);
                }
            }
        } else if (suit == 'z') {
            path(parts, 0, 0, 1, 1, 1.4, 1, BLUE, 7, 9, 25, 9, 25, 39, 7, 39, 7, 9);
            path(parts, 0, 0, 1, 1, .7, 1, BLUE, 10, 12, 22, 12, 22, 36, 10, 36, 10, 12);
            for (int y = 13; y <= 34; y += 5) {
                line(parts, 7, y, 10, y + 2, .6, 1, BLUE);
                line(parts, 22, y, 25, y + 2, .6, 1, BLUE);
            }
        } else if (suit == 'f') {
            glyph(
                    parts,
                    GameSymbols.FLOWERS.charAt(number - 1),
                    5,
                    5,
                    22,
                    22,
                    .9,
                    number <= 4 ? BLUE : RED);
            line(parts, 15, 43, 18, 32, 1, 1, GREEN);
            box(parts, 12, 37, 6, 2.1, .5, 1, GREEN);
            box(parts, 21, 39, 6, 2.1, -.5, 1, GREEN);
            Material petals = number <= 4 ? Material.PURPLE_CONCRETE : Material.YELLOW_CONCRETE;
            for (int index = 0; index < 5; index++) {
                double startAngle = index * 2 * Math.PI / 5;
                box(
                        parts,
                        18 + 3 * Math.cos(startAngle),
                        31 + 3 * Math.sin(startAngle),
                        3.5,
                        2.2,
                        startAngle,
                        2,
                        petals);
            }
            circle(parts, 18, 31, 1.2, 3, RED);
        }
    }

    private static int[][] pipPositions(int number) {
        return switch (number) {
            case 2 -> new int[][] {{11, 14}, {21, 34}};
            case 3 -> new int[][] {{9, 12}, {16, 24}, {23, 36}};
            case 4 -> new int[][] {{9, 13}, {23, 13}, {9, 35}, {23, 35}};
            case 5 -> new int[][] {{9, 12}, {23, 12}, {9, 36}, {23, 36}, {16, 24}};
            case 6 -> new int[][] {{9, 11}, {23, 11}, {9, 24}, {23, 24}, {9, 37}, {23, 37}};
            case 7 ->
                    new int[][] {{8, 9}, {16, 15}, {24, 21}, {9, 29}, {23, 29}, {9, 39}, {23, 39}};
            case 8 ->
                    new int[][] {
                        {9, 9}, {23, 9}, {9, 19}, {23, 19}, {9, 29}, {23, 29}, {9, 39}, {23, 39}
                    };
            default ->
                    new int[][] {
                        {8, 11}, {16, 11}, {24, 11}, {8, 24}, {16, 24}, {24, 24}, {8, 37}, {16, 37},
                        {24, 37}
                    };
        };
    }

    private HandModels() {}
}
