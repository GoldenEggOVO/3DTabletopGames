package dev.tabletop3d;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandArtTest {
    @Test void everyFaceHasOpaqueArtAndDistinctRank() {
        for (char suit : new char[]{'m', 'p', 's', 'z', 'f'}) {
            Set<String> pictures = new HashSet<>();
            int first = suit == 'z' || suit == 'f' ? 1 : 0;
            int last = suit == 'z' ? 7 : suit == 'f' ? 8 : 9;
            for (int rank = first; rank <= last; rank++) {
                BufferedImage art = HandArt.draw(true, "" + suit + rank);
                checkCanvas(art);
                assertTrue(pictures.add(pixels(art)), "Repeated tile: " + suit + rank);
            }
        }
        for (char color : new char[]{'r', 'b', 'y', 'p'}) {
            Set<String> pictures = new HashSet<>();
            for (String value : new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "Skip", "Reverse", "Draw2", "Draw3"}) {
                BufferedImage art = HandArt.draw(false, color + value);
                checkCanvas(art);
                assertTrue(pictures.add(pixels(art)), "Repeated card: " + color + value);
            }
        }
        checkCanvas(HandArt.draw(false, "wild"));
        checkCanvas(HandArt.draw(true, "back"));
        checkCanvas(HandArt.draw(false, "back"));
    }

    @Test void wildShowsAllFourCardColors() {
        BufferedImage wild = HandArt.draw(false, "wild");
        Set<Integer> colors = new HashSet<>();
        for (char color : new char[]{'r', 'b', 'y', 'p'}) {
            BufferedImage card = HandArt.draw(false, color + "1");
            colors.add(card.getRGB(5, 24));
        }
        Set<Integer> wildColors = new HashSet<>();
        for (int pixel : wild.getRGB(0, 0, 32, 48, null, 0, 32)) wildColors.add(pixel);
        assertEquals(4, colors.size());
        assertTrue(wildColors.containsAll(colors));
    }

    @Test void redFivesKeepTheirSuitButUseRedInk() {
        for (char suit : new char[]{'m', 'p', 's'}) {
            BufferedImage normal = HandArt.draw(true, suit + "5");
            BufferedImage red = HandArt.draw(true, suit + "0");
            assertNotEquals(pixels(normal), pixels(red));
            assertTrue(redPixels(red) > redPixels(normal), "Red five: " + suit);
        }
    }

    @Test void oneCircleAndBirdHaveDifferentShapesAndHonorsHaveVisibleInk() {
        assertNotEquals(pixels(HandArt.draw(true, "p1")), pixels(HandArt.draw(true, "s1")));
        for (int i = 1; i <= 7; i++) {
            BufferedImage art = HandArt.draw(true, "z" + i);
            long dark = Arrays.stream(art.getRGB(5, 8, 22, 32, null, 0, 22))
                    .filter(c -> ((c >> 16) & 255) < 150 || ((c >> 8) & 255) < 150).count();
            assertTrue(dark > 40, "Honor glyph must not be empty: " + i);
        }
    }

    private static long redPixels(BufferedImage art) {
        return Arrays.stream(art.getRGB(3, 3, 26, 42, null, 0, 26))
                .filter(c -> ((c >> 16) & 255) > 130 && ((c >> 8) & 255) < 100 && (c & 255) < 110).count();
    }

    private static void checkCanvas(BufferedImage art) {
        assertNotNull(art);
        assertEquals(32, art.getWidth());
        assertEquals(48, art.getHeight());
        Set<Integer> colors = new HashSet<>();
        for (int pixel : art.getRGB(0, 0, 32, 48, null, 0, 32)) {
            assertEquals(255, pixel >>> 24);
            colors.add(pixel);
        }
        assertTrue(colors.size() >= 3, "Artwork must contain a face, edge and ink");
        assertTrue(colors.size() <= 16, "Flat palette keeps native display rows compact");
    }

    private static String pixels(BufferedImage art) {
        return Arrays.toString(art.getRGB(0, 0, 32, 48, null, 0, 32));
    }
}
