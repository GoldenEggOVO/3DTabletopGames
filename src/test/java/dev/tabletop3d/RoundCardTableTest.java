package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class RoundCardTableTest {
    @Test void circularFurnitureKeepsTwoFineSurfacesWithinNinetyFourDisplays() {
        var parts = RoundCardTable.parts();
        assertEquals(94, parts.size());
        assertEquals(46, parts.stream().filter(p -> p.material() == Material.GREEN_TERRACOTTA).count());
        assertEquals(46, parts.stream().filter(p -> p.material() == Material.STRIPPED_DARK_OAK_WOOD).count());
        assertEquals(1, parts.stream().filter(p -> p.material() == Material.STRIPPED_DARK_OAK_LOG).count());
        assertEquals(1, parts.stream().filter(p -> p.material() == Material.DARK_OAK_PLANKS).count());
    }
}
