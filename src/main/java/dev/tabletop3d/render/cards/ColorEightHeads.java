package dev.tabletop3d.render.cards;

import java.util.List;
import java.util.Map;

public final class ColorEightHeads {
    private static final class Catalog {
        static final Map<String, List<PlayingCardHeads.Texture>> FACES =
                PlayingCardHeads.load("/color-eight-heads.json");
    }

    public static List<PlayingCardHeads.Texture> tiles(String face) {
        return Catalog.FACES.get(face);
    }

    private ColorEightHeads() {}
}
