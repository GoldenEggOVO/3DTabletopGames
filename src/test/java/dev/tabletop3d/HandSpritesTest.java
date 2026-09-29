package dev.tabletop3d;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandSpritesTest {
    @Test void adjacentRunsMergeWithoutCrossingTransparentRowsOrOtherColors() {
        BufferedImage image=new BufferedImage(5,4,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<2;y++) {
            image.setRGB(0,y,0xff8e2020);image.setRGB(1,y,0xff8e2020);
            image.setRGB(2,y,0xff2b318b);image.setRGB(4,y,0xff8e2020);
        }
        image.setRGB(0,3,0xff8e2020);image.setRGB(1,3,0xff8e2020);
        assertEquals(Set.of(
            new HandSprites.Rect(0,0,2,2,Material.RED_CONCRETE),
            new HandSprites.Rect(2,0,1,2,Material.BLUE_CONCRETE),
            new HandSprites.Rect(4,0,1,2,Material.RED_CONCRETE),
            new HandSprites.Rect(0,3,2,1,Material.RED_CONCRETE)),Set.copyOf(HandSprites.rectangles(image)));
    }

    @Test void differentRunWidthsKeepTheirExactVisibleAreaWithoutOverlap() {
        BufferedImage image=new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB);
        for(int x=0;x<3;x++)image.setRGB(x,0,0xff080a0f);
        image.setRGB(0,1,0xff080a0f);image.setRGB(1,1,0xff080a0f);
        for(int x=0;x<3;x++)image.setRGB(x,2,0xff080a0f);
        List<HandSprites.Rect> rectangles=HandSprites.rectangles(image);
        assertEquals(List.of(
            new HandSprites.Rect(0,0,3,1,Material.BLACK_CONCRETE),
            new HandSprites.Rect(0,1,2,1,Material.BLACK_CONCRETE),
            new HandSprites.Rect(0,2,3,1,Material.BLACK_CONCRETE)),rectangles);
        assertEquals(8,rectangles.stream().mapToInt(rect->rect.width()*rect.height()).sum());
    }

    @Test void nearbyCreamShadesUseOneQuartzRectangleAndResultsAreImmutable() {
        BufferedImage image=new BufferedImage(32,48,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<48;y++)for(int x=0;x<32;x++)image.setRGB(x,y,x%2==0?0xfff4eddd:0xffe8e4dc);
        List<HandSprites.Rect> rectangles=HandSprites.rectangles(image);
        assertEquals(List.of(new HandSprites.Rect(0,0,32,48,Material.SMOOTH_QUARTZ)),rectangles);
        image.setRGB(0,0,0);
        assertEquals(32*48,rectangles.getFirst().width()*rectangles.getFirst().height());
        assertThrows(UnsupportedOperationException.class,()->rectangles.clear());
        assertTrue(HandSprites.rectangles(new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB)).isEmpty());
    }

    @Test void legalFaceSpritesAreCachedAsImmutableLists() {
        for(boolean mahjong:new boolean[]{false,true}) {
            List<HandSprites.Rect> rectangles=HandSprites.of(mahjong,"back");
            assertFalse(rectangles.isEmpty());
            assertSame(rectangles,HandSprites.of(mahjong,"back"));
            assertSame(rectangles,HandSprites.of(mahjong,""));
            assertThrows(UnsupportedOperationException.class,()->rectangles.clear());
        }
    }
}
