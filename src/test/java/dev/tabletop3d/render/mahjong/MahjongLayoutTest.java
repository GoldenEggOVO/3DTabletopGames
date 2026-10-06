package dev.tabletop3d.render.mahjong;

import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.mahjong.*;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MahjongLayoutTest {
    private static Tiles.Tile tile(int index) {
        return new Tiles.Tile("tile-" + index, 4, false);
    }

    @Test
    void calledAndAddedTilesMustReferToDistinctPhysicalMembers() {
        var tiles = List.of(tile(0), tile(1), tile(2), tile(3));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Meld(Meld.Kind.QUAD, tiles, true, 1, "missing", "tile-3"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Meld(Meld.Kind.QUAD, tiles, true, 1, "tile-2", "tile-2"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Meld(Meld.Kind.QUAD, tiles, true, 1, "tile-2", "missing"));
    }

    @Test
    void calledTileTracksSourceAndAddedKanStacksOnThatTile() {
        var own = List.of(tile(0), tile(1), tile(2));
        for (int source : List.of(1, 2, 3)) {
            var pon = new Meld(Meld.Kind.TRIPLET, own, true, source);
            var layout = MahjongLayout.meld(pon, 0);
            var called =
                    layout.stream().filter(MahjongLayout.Tile::sideways).findFirst().orElseThrow();
            assertEquals("tile-2", called.piece().id());
            assertEquals(source == 1 ? 0 : source == 2 ? 1 : 2, layout.indexOf(called));
            var kan =
                    new Meld(
                            Meld.Kind.QUAD,
                            List.of(tile(0), tile(1), tile(2), tile(3)),
                            true,
                            source,
                            "tile-2",
                            "tile-3");
            var added = MahjongLayout.meld(kan, 0);
            assertEquals(4, added.size());
            assertEquals(called.offset(), added.getLast().offset());
            assertTrue(added.getLast().sideways());
            assertTrue(added.getLast().lift() > 0);
        }
    }

    @Test
    void closedKanShowsBacksAtBothEndsAndKeepsPhysicalIdentity() {
        var closed =
                new Meld(Meld.Kind.QUAD, List.of(tile(0), tile(1), tile(2), tile(3)), false, 0);
        var layout = MahjongLayout.meld(closed, 0);
        assertEquals("back", layout.getFirst().piece().face());
        assertEquals("back", layout.getLast().piece().face());
        assertEquals("m5", layout.get(1).piece().face());
        assertEquals(4, layout.stream().map(t -> t.piece().id()).distinct().count());
        assertTrue(layout.stream().noneMatch(MahjongLayout.Tile::sideways));
    }

    @Test
    void sidewaysRiverReservesWidthWithoutChangingDiscardOrder() {
        var river =
                java.util.stream.IntStream.range(0, 9)
                        .mapToObj(i -> new HandGame.Piece("tile-" + i, "m5"))
                        .toList();
        var layout = MahjongLayout.river(river, "tile-2");
        assertEquals(river, layout.stream().map(MahjongLayout.Tile::piece).toList());
        assertTrue(layout.get(2).sideways());
        assertTrue(layout.get(3).offset() - layout.get(2).offset() > .12);
        assertEquals(layout.get(0).row() + 1, layout.get(6).row());
    }
}
