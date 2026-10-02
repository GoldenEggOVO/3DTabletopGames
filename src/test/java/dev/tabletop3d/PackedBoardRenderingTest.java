package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class PackedBoardRenderingTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private void packed(TableViewTest.Fixture f) {
        f.plugin.pack = mock(TabletopPack.class);
        when(f.plugin.pack.item(anyString())).thenReturn(new ItemStack(Material.PAPER));
        when(f.plugin.pack.packed(f.player)).thenReturn(true);
        when(f.player.isOnline()).thenReturn(true);
        when(f.player.getLocation()).thenReturn(new Location(f.world,0,81,2));
        when(f.plugin.allowed(f.player)).thenReturn(true);
        when(f.world.getPlayers()).thenReturn(List.of(f.player));
    }

    @Test void enteringPackedOnlyViewRemovesNativeMapsAndUsesOneModelPerChessPiece() {
        var f = new TableViewTest.Fixture("chess");
        List<Entity> oldParts = List.copyOf(f.entities);
        packed(f);
        f.view.tick();
        for (Entity entity : oldParts) if (entity instanceof BlockDisplay || entity instanceof ItemFrame)
            verify(entity).remove();
        assertEquals(33,f.entities.stream().filter(ItemDisplay.class::isInstance).count());
        when(f.plugin.pack.packed(f.player)).thenReturn(false);
        f.view.tick();
        for (Entity entity : f.entities) if (entity instanceof ItemDisplay) verify(entity).remove();
        assertEquals(8,f.entities.stream().filter(ItemFrame.class::isInstance).count());
        f.view.close();
    }

    @Test void changingLayersDuringADropKeepsItsFrameAndPosition() throws Exception {
        var f = new TableViewTest.Fixture("connectfour");
        f.move("drop:2");
        f.view.tick(); f.view.tick();
        Object token = ((Map<?,?>) field(f.view,"tokens")).get("2,0");
        int before = (int) field(token,"frame");
        packed(f);
        f.view.tick();
        assertEquals(before+1,field(token,"frame"));
        List<?> parts = (List<?>) field(token,"parts");
        assertEquals(1,parts.size());
        assertInstanceOf(ItemDisplay.class,parts.getFirst());
        for (int i=0;i<12;i++) f.view.tick();
        assertEquals(field(token,"to"),((Entity)parts.getFirst()).getLocation());
    }

    @Test void packedReversiStillFlipsAndUsesItsCurrentOwnerAfterLayerChanges() throws Exception {
        var f = new TableViewTest.Fixture("reversi");
        f.move(f.room.board.legalActions(0).getFirst());
        packed(f);
        f.view.tick();
        Map<?,?> tokens=(Map<?,?>)field(f.view,"tokens");
        for (Object token : tokens.values())
            assertEquals(1,((List<?>)field(token,"parts")).size());
        for (int i=0;i<12;i++) f.view.tick();
        for (Object token : tokens.values()) {
            Entity item=(Entity)((List<?>)field(token,"parts")).getFirst();
            assertTrue(f.poses.containsKey(item));
        }
    }

    private Object field(Object object,String name) throws Exception {
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
}
