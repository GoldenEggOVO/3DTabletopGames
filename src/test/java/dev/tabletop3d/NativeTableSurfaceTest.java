package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class NativeTableSurfaceTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void squareTableRimCornersDoNotShareTexturedTopFaces() throws Exception {
        for (String kind : List.of("chess", "mahjong")) {
            var f = new TableViewTest.Fixture(kind, kind.equals("mahjong") ? 4 : 2);
            var furniture = (List<Entity>) TableViewTest.field(f.view,
                    kind.equals("mahjong") ? "handFurniture" : "nativeBoardFurniture");
            var rim = furniture.stream().filter(e -> f.transforms.containsKey(e)
                    && Math.abs(f.transforms.get(e).getScale().y - .11) < 1e-6).toList();
            assertEquals(4, rim.size(), kind);
            for (int i = 0; i < rim.size(); i++) {
                var a = f.transforms.get(rim.get(i));
                var ap = rim.get(i).getLocation();
                for (int j = i + 1; j < rim.size(); j++) {
                    var b = f.transforms.get(rim.get(j));
                    var bp = rim.get(j).getLocation();
                    double overlapX = Math.min(ap.getX() + a.getTranslation().x + a.getScale().x,
                            bp.getX() + b.getTranslation().x + b.getScale().x)
                            - Math.max(ap.getX() + a.getTranslation().x, bp.getX() + b.getTranslation().x);
                    double overlapZ = Math.min(ap.getZ() + a.getTranslation().z + a.getScale().z,
                            bp.getZ() + b.getTranslation().z + b.getScale().z)
                            - Math.max(ap.getZ() + a.getTranslation().z, bp.getZ() + b.getTranslation().z);
                    assertTrue(overlapX < 1e-6 || overlapZ < 1e-6, kind + " rim corners must meet without coplanar overlap");
                }
            }
            f.view.close();
        }
    }
}
