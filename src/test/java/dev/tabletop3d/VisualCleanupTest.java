package dev.tabletop3d;

import dev.tabletop3d.ui.GameSymbols;

import dev.tabletop3d.ui.MessageText;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisualCleanupTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void close(){MockBukkit.unmock();}

    @Test void xiangqiKeepsChinesePieceGlyphsAndRiverOnAnEnglishTable() throws Exception {
        var f=new TableViewTest.Fixture("xiangqi");
        Map<?,?> tokens=(Map<?,?>)TableViewTest.field(f.view,"tokens");
        int blackCannons=0;
        for(var cell:f.room.board.cells())if(cell.owner()>=0) {
            List<?> parts=(List<?>)TableViewTest.field(tokens.get(cell.id()),"parts");
            TextDisplay label=(TextDisplay)parts.stream().filter(TextDisplay.class::isInstance).findFirst().orElseThrow();
            assertEquals(cell.piece(),text(label));
            if(cell.owner()==1&&cell.piece().equals(GameSymbols.BLACK_CANNON))blackCannons++;
        }
        assertEquals(2,blackCannons);
        List<?> furniture=(List<?>)TableViewTest.field(f.view,"furniture");
        assertTrue(furniture.stream().filter(TextDisplay.class::isInstance).map(TextDisplay.class::cast)
            .map(VisualCleanupTest::text).anyMatch(GameSymbols.XIANGQI_RIVER::equals));
        assertTrue(text((TextDisplay)TableViewTest.field(f.view,"title")).contains("Xiangqi"));
        f.view.close();
    }

    @Test void ludoPawnsHaveNoNumberDisplays() throws Exception {
        for(String kind:List.of("ludo")) {
            var f=new TableViewTest.Fixture(kind);
            Map<?,?> tokens=(Map<?,?>)TableViewTest.field(f.view,"tokens");
            assertFalse(tokens.isEmpty());
            for(var entry:tokens.entrySet()) {
                List<?> parts=(List<?>)TableViewTest.field(entry.getValue(),"parts");
                List<?> labels=parts.stream().filter(TextDisplay.class::isInstance).toList();
                assertEquals(0,labels.size());
            }
            f.view.close();
            for(Entity entity:f.entities)verify(entity).remove();
        }
    }

    @Test void diceTraysKeepTheirGreenSurfaceWithoutASquareShadow() {
        var f=new TableViewTest.Fixture("chess");
        for(boolean compact:new boolean[]{false,true}) {
            int initial=f.entities.size();
            var tray=new DiceTray(f.plugin,f.room,new Location(f.world,0,80,0),new NamespacedKey("3dtabletop","board-cell"),compact);
            List<Entity> parts=List.copyOf(f.entities.subList(initial,f.entities.size()));
            int surfaces=0;
            for(Entity part:parts)if(part instanceof BlockDisplay block) {
                var capture=ArgumentCaptor.forClass(BlockData.class);
                verify(block).setBlock(capture.capture());
                Material material=capture.getValue().getMaterial();
                assertNotEquals(Material.GREEN_TERRACOTTA,material);
                if(material==Material.GREEN_CONCRETE)surfaces++;
            }
            assertEquals(1,surfaces);
            tray.roll(6,91);
            for(int i=0;i<DiceMotion.FRAMES;i++)tray.tick();
            assertFalse(tray.rolling());
            assertEquals(initial+parts.size(),f.entities.size());
            tray.close();
            for(Entity part:parts)verify(part).remove();
        }
        f.view.close();
    }

    private static String text(TextDisplay display) {
        var capture=ArgumentCaptor.forClass(Component.class);
        verify(display,atLeastOnce()).text(capture.capture());
        return MessageText.plain(capture.getValue());
    }
}
