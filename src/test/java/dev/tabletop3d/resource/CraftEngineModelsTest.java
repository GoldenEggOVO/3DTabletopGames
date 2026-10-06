package dev.tabletop3d.resource;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems.Definition;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;

import org.bukkit.Material;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

class CraftEngineModelsTest {
    private CraftEngineModels models;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
        MockBukkit.createMockPlugin("CraftEngine");
        CraftEngineItems.REGISTRY.clear();
        CraftEngineItems.builds = 0;
        for (String id : CraftEngineModels.IDS)
            CraftEngineItems.REGISTRY.put("tabletop3d:" + id, new Definition(Material.PAPER));
        models = new CraftEngineModels();
    }

    @AfterEach
    void cleanup() {
        CraftEngineItems.REGISTRY.clear();
        MockBukkit.unmock();
    }

    private void poll() throws Exception {
        var checked = CraftEngineModels.class.getDeclaredField("checked");
        checked.setAccessible(true);
        checked.setLong(models, 0);
    }

    @Test
    void unchangedRegistryReusesBuiltItemsAndReturnsIndependentCopies() throws Exception {
        assertTrue(models.ready());
        int initial = CraftEngineItems.builds;
        models.item("mahjong_back").setAmount(12);
        assertEquals(1, models.item("mahjong_back").getAmount());
        poll();
        assertTrue(models.ready());
        assertEquals(initial, CraftEngineItems.builds);
    }

    @Test
    void replacingOneDefinitionRebuildsOnlyItsItem() throws Exception {
        assertTrue(models.ready());
        int initial = CraftEngineItems.builds;
        CraftEngineItems.REGISTRY.put("tabletop3d:mahjong_back", new Definition(Material.BOOK));
        poll();
        assertTrue(models.ready());
        assertEquals(Material.BOOK, models.item("mahjong_back").getType());
        assertEquals(initial + 1, CraftEngineItems.builds);
    }

    @Test
    void incompleteRegistryFailsClosedAndRecoversOnTheNextPoll() throws Exception {
        assertTrue(models.ready());
        CraftEngineItems.REGISTRY.remove("tabletop3d:mahjong_back");
        poll();
        assertFalse(models.ready());
        assertThrows(IllegalStateException.class, () -> models.item("mahjong_back"));
        CraftEngineItems.REGISTRY.put("tabletop3d:mahjong_back", new Definition(Material.BOOK));
        poll();
        assertTrue(models.ready());
        assertEquals(Material.BOOK, models.item("mahjong_back").getType());
    }
}
