package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;

import dev.tabletop3d.rules.ColorEightGame;
import java.util.List;
import org.junit.jupiter.api.Test;

class ColorEightHeadsTest {
    @Test void twelveHeadsCoverTheExistingCardSizeAndFaceTheOwnerOrUp() {
        var client = new org.joml.Matrix4f().rotateY((float) Math.PI)
                .translate(0, -.5f, 0).rotateX((float) Math.PI);
        for (boolean standing : List.of(true, false)) for (int tile = 0; tile < 12; tile++) {
            double height = standing ? .25 : .245;
            var pose = PlayingCardHeads.pose(.168, height, standing, tile, 3, 4);
            var matrix = new org.joml.Matrix4f().translate(pose.getTranslation()).rotate(pose.getLeftRotation())
                    .scale(pose.getScale()).rotate(pose.getRightRotation()).mul(client);
            var leftTop = matrix.transformPosition(new org.joml.Vector3f(-.25f, -.5f, -.25f));
            var rightBottom = matrix.transformPosition(new org.joml.Vector3f(.25f, 0, -.25f));
            double x = -.084 + tile % 3 * .056, top = height - tile / 3 * height / 4;
            assertEquals(x, leftTop.x, 1e-6);
            assertEquals(x + .056, rightBottom.x, 1e-6);
            assertEquals(standing ? top : .010, leftTop.y, 1e-6);
            assertEquals(standing ? top - height / 4 : .010, rightBottom.y, 1e-6);
            assertEquals(standing ? .004 : height / 2 - top, leftTop.z, 1e-6);
            assertEquals(standing ? .004 : height / 2 - top + height / 4, rightBottom.z, 1e-6);
            var normal = matrix.transformDirection(new org.joml.Vector3f(0, 0, -1)).normalize();
            assertEquals(standing ? 0 : 1, normal.y, 1e-6);
            assertEquals(standing ? 1 : 0, normal.z, 1e-6);
        }
    }

    @Test void everyPlayableColorEightFaceHasTwelveSignedHeadTiles() {
        var game = new ColorEightGame(2, 0);
        var faces = new java.util.ArrayList<String>();
        for (char color : new char[] {'r', 'b', 'y', 'p'})
            for (String rank : List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "Draw1", "Skip", "Reverse"))
                faces.add(color + rank);
        faces.addAll(List.of("wild", "swap"));
        for (String face : faces) {
            var tiles = ColorEightHeads.tiles(face);
            assertEquals(12, tiles.size(), face);
            for (var tile : tiles) {
                assertFalse(tile.signature().isBlank());
                String value = new String(java.util.Base64.getDecoder().decode(tile.value()), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(value.contains("textures.minecraft.net/texture/"), face);
            }
        }
        assertNull(ColorEightHeads.tiles("back"));
        assertTrue(game.hand(0).stream().allMatch(card -> ColorEightHeads.tiles(card.face()) != null));
    }
}
