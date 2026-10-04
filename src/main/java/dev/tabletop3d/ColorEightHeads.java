package dev.tabletop3d;

import java.util.List;
import java.util.Map;

/** Twenty-four thin heads preserve the B-style Color Eight faces without a resource pack. */
final class ColorEightHeads {
    private static final class Catalog {
        static final Map<String, List<PlayingCardHeads.Texture>> FACES =
                PlayingCardHeads.load("/color-eight-heads.json");
    }

    static List<PlayingCardHeads.Texture> tiles(String face) {
        return Catalog.FACES.get(face);
    }

    private ColorEightHeads() {}
}
