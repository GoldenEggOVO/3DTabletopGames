package dev.tabletop3d;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiceTrayTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void close(){MockBukkit.unmock();}

    @Test void fullAndCompactTraysStayOutsideTheMainBoardAndHaveReachLimitedHits() {
        var f=new TableViewTest.Fixture("chess");
        for(boolean compact:new boolean[]{false,true}) {
            var tray=tray(f,compact);Location center=tray.center();double width=compact?.5:1.4;
            assertTrue(center.getX()-width/2>1.125,"The side table must clear the main board");
            assertEquals(80+TableGeometry.SURFACE,center.getY(),1e-6);
            assertTrue(tray.hit(center.clone().add(0,2,0),new Vector(0,-1,0))>=0);
            assertEquals(-1,tray.hit(center.clone().add(width,2,0),new Vector(0,-1,0)));
            assertEquals(-1,tray.hit(center.clone().add(0,8,0),new Vector(0,-1,0)));
            assertEquals(-1,tray.hit(center.clone().add(0,2,0),new Vector(0,1,0)));
            center.add(9,9,9);assertNotEquals(center,tray.center(),"Callers cannot move the tray anchor");tray.close();
        }
    }

    @Test void throwsReuseTheirEntitiesSettleAndBecomeQuietUntilNextAction() {
        var f=new TableViewTest.Fixture("chess");when(f.plugin.getConfig()).thenReturn(new YamlConfiguration());
        int initial=f.entities.size();var tray=tray(f,false);List<Entity> parts=List.copyOf(f.entities.subList(initial,f.entities.size()));
        int count=f.entities.size();tray.roll(6,91);assertTrue(tray.rolling());
        for(int i=0;i<DiceMotion.FRAMES;i++)tray.tick();assertFalse(tray.rolling());assertEquals(count,f.entities.size());
        for(Entity part:parts)clearInvocations(part);
        for(int i=0;i<30;i++)tray.tick();
        for(Entity part:parts)if(part instanceof Display display)verify(display,never()).setTransformation(any());
        tray.roll(2,7);tray.tick();tray.settle(4);assertFalse(tray.rolling());assertEquals(count,f.entities.size());
        tray.close();tray.close();for(Entity part:parts)verify(part).remove();
    }

    @Test void repeatedLabelsDoNotResendUnchangedText() {
        var f=new TableViewTest.Fixture("chess");int initial=f.entities.size();var tray=tray(f,false);
        TextDisplay label=(TextDisplay)f.entities.subList(initial,f.entities.size()).stream().filter(TextDisplay.class::isInstance).findFirst().orElseThrow();
        clearInvocations(label);Component text=Component.text("Roll the dice");tray.label(text);tray.label(text);
        verify(label).text(text);tray.close();
    }

    private static DiceTray tray(TableViewTest.Fixture f,boolean compact) {
        return new DiceTray(f.plugin,f.room,new Location(f.world,0,80,0),new NamespacedKey("servergames","board-cell"),compact);
    }
}
