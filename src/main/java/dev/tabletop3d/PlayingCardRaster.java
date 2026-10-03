package dev.tabletop3d;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Cached Casino artwork carried by four native glyph planes, without a resource pack. */
final class PlayingCardRaster {
    static final int COLUMNS = 64, ROWS = 86;
    // The vanilla uniform U+2588 glyph is 4 by 8 font units, with advance 5 and line step 10.
    private static final double ADVANCE = 5, LINE_STEP = 10, TEXT_UNIT = .025;
    private static final Map<String, Component> CACHE = new HashMap<>();
    private static final Map<String, Integer> COLORS = Map.of(
            "WHITE_CONCRETE", 0xf9ffff, "BLACK_CONCRETE", 0x1d1d21,
            "RED_CONCRETE", 0xb02e26, "BLUE_CONCRETE", 0x35399d,
            "LIGHT_BLUE_CONCRETE", 0x3ab3da);

    private PlayingCardRaster() {}

    static Component face(String face) {
        return CACHE.computeIfAbsent(face, key -> {
            var image = Component.text().font(Key.key("minecraft:uniform"))
                    .decoration(TextDecoration.BOLD, false)
                    .decoration(TextDecoration.ITALIC, false);
            for (int y = 0; y < ROWS; y++) {
                if (y > 0) image.append(Component.newline());
                int start = 0;
                while (start < COLUMNS) {
                    int color = pixel(key, start, y), end = start + 1;
                    while (end < COLUMNS && pixel(key, end, y) == color) end++;
                    // EmptyGlyph space advance is 4 plus bold offset 1; it emits no glyph quad.
                    image.append(color == background(key)
                            ? Component.text(" ".repeat(end - start)).decorate(TextDecoration.BOLD)
                            : Component.text("\u2588".repeat(end - start), TextColor.color(color)));
                    start = end;
                }
            }
            return image.build();
        });
    }

    static int background(String face) {
        return COLORS.get(face.equals("back") ? "BLUE_CONCRETE" : "WHITE_CONCRETE");
    }

    static int pixel(String face, int x, int y) {
        double px = -2 + (x + .5) * 4 / COLUMNS;
        double py = 8.0 / 3 - (y + .5) * (16.0 / 3) / ROWS;
        int color = COLORS.get("WHITE_CONCRETE");
        for (var box : PlayingCardArt.boxes(face)) {
            if (px >= box.from()[0] && px < box.to()[0]
                    && py >= box.from()[1] && py < box.to()[1]) color = COLORS.get(box.material());
        }
        return color;
    }

    static Transformation pose(double width, boolean standing, boolean back, int layer) {
        double height = width * 4 / 3;
        double ux = width / (COLUMNS * ADVANCE), uy = height / (ROWS * LINE_STEP);
        double dx = (layer & 1) * ux, dy = (layer / 2) * 2 * uy;
        double depth = .004 + layer * .0006;
        var rotation = standing
                ? (back ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf())
                : new Quaternionf().rotateX((float) -Math.PI / 2);
        var translation = standing
                ? new Vector3f((float) ((back ? 1 : -1) * (ux - dx)),
                        (float) (uy - dy), (float) (back ? -depth : depth))
                : new Vector3f((float) (-ux + dx), (float) (.006 + depth),
                        (float) (height / 2 - uy + dy));
        return new Transformation(translation, rotation,
                new Vector3f((float) (ux / TEXT_UNIT), (float) (uy / TEXT_UNIT), 1),
                new Quaternionf());
    }
}
