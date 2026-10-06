package dev.tabletop3d.render;

import dev.tabletop3d.render.cards.RoundCardTable;
import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.ui.GameSymbols;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeModelGeometryTest {
    @Test void roundTableUsesFlatAdjoiningRowsWithoutRadialSeams() {
        var parts = RoundCardTable.parts();
        var surface = parts.stream().filter(p -> p.material() == Material.GREEN_TERRACOTTA).toList();
        assertFalse(surface.isEmpty());
        double top = surface.getFirst().y() + surface.getFirst().h();
        for (var part : surface) {
            assertEquals(0, part.yaw(), "Circular surfaces must use adjoining rows");
            assertEquals(top, part.y() + part.h(), 1e-9, "The whole surface must be level");
        }
        for (int i = 0; i < surface.size(); i++) for (int j = i + 1; j < surface.size(); j++) {
            var a = surface.get(i); var b = surface.get(j);
            assertTrue(overlap(a.x(), a.w(), b.x(), b.w()) < 1e-9
                    || overlap(a.z(), a.d(), b.z(), b.d()) < 1e-9,
                    "Rows must meet without overlapping textured top faces");
        }
        double radius = 1.32;
        for (double x = -radius; x <= radius; x += .025) for (double z = -radius; z <= radius; z += .025) {
            if (Math.hypot(x, z) > radius - .055) continue;
            final double px = x, pz = z;
            assertTrue(surface.stream().anyMatch(p -> Math.abs(px - p.x()) <= p.w() / 2 + 1e-9
                    && Math.abs(pz - p.z()) <= p.d() / 2 + 1e-9), "No hole inside the circular top");
        }
        assertTrue(parts.size() <= 54, "Round furniture must keep a bounded entity budget");
    }

    @Test void chessKingCrossAndKnightHaveNoIntersectingCuboids() {
        for (String glyph : List.of(GameSymbols.KING, GameSymbols.HORSE)) {
            var parts = TableModels.piece("chess", new Cell("piece", 0, 0, glyph, 0), Map.of());
            for (int i = 0; i < parts.size(); i++) for (int j = i + 1; j < parts.size(); j++) {
                var a = parts.get(i); var b = parts.get(j);
                assertTrue(overlap(a.x(), a.w(), b.x(), b.w()) < 1e-9
                        || overlap(a.z(), a.d(), b.z(), b.d()) < 1e-9
                        || Math.min(a.y() + a.h(), b.y() + b.h()) - Math.max(a.y(), b.y()) < 1e-9,
                        glyph + " intersects: " + a + " / " + b);
            }
        }
    }

    private static double overlap(double a, double aw, double b, double bw) {
        return Math.min(a + aw / 2, b + bw / 2) - Math.max(a - aw / 2, b - bw / 2);
    }
}
