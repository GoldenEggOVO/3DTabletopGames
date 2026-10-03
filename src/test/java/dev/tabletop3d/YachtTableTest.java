package dev.tabletop3d;

import dev.tabletop3d.rules.YachtGame;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YachtTableTest {
    @org.junit.jupiter.api.io.TempDir(factory = WorkspaceTempFactory.class) java.nio.file.Path temp;
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void close() { MockBukkit.unmock(); }

    @Test void yachtIsAvailableAndFinalRollStillAllowsMovingDiceBetweenSlots() {
        assertTrue(Tabletop3D.GAMES.contains("yacht"));
        var game = new YachtGame(2, 41);
        for (int i = 0; i < 3; i++) game.apply(0, "roll");
        int[] dice = game.dice();
        game.apply(0, "hold:die2");
        assertTrue(game.held(2));
        assertFalse(game.legalActions(0).contains("roll"));
        game.apply(0, "hold:die2");
        assertFalse(game.held(2));
        assertArrayEquals(dice, game.dice());
        assertTrue(game.legalActions(1).isEmpty());
        game.apply(0, "score:choice");
        assertEquals(1, game.currentPlayer());
        assertEquals(0, game.rolls());
        assertFalse(game.held(2));
        assertFalse(game.legalActions(0).contains("score:choice"));
    }

    @Test void fiveSmallDiceSettleInARowAndKeepSlotsAreReversible() {
        var f = new TableViewTest.Fixture("yacht");
        f.move("roll");
        assertTrue(f.view.rolling());
        for (int i = 0; i < 24; i++) f.view.tick();
        assertFalse(f.view.rolling());
        for (int i = 0; i < 5; i++) assertHit(f, .32 + (i - 2) * .26, .15, "die" + i);
        assertHit(f, .32, -.68, "@menu");
        f.move("hold:die2");
        assertHit(f, .32, -.68, "die2");
        assertHit(f, .32, .15, "@menu");
        int count = f.entities.size();
        f.move("roll");
        for (int i = 0; i < 24; i++) f.view.tick();
        assertHit(f, .32, -.68, "die2");
        assertEquals(count, f.entities.size());
        f.move("hold:die2");
        assertHit(f, .32, .15, "die2");
        assertHit(f, .32, -.68, "@menu");
        f.view.close();
        for (Entity entity : f.entities) verify(entity).remove();
    }

    @Test void controlsHaveBoundedHitsAndIdleTicksDoNotResendDiceTransforms() {
        var f = new TableViewTest.Fixture("yacht", "roll");
        assertHit(f, .45, .82, "@roll");
        assertHit(f, -.78, -.85, "@score:ones");
        assertHit(f, -.70, .82, "@scores");
        assertNull(f.view.hitPiece(f.view.origin.clone().add(0, 8, 0), new Vector(0, -1, 0)));
        for (Entity entity : f.entities) clearInvocations(entity);
        for (int i = 0; i < 100; i++) f.view.tick();
        for (Entity entity : f.entities)
            if (entity instanceof org.bukkit.entity.Display display)
                verify(display, never()).setTransformation(any());
    }

    @Test void allHeldDiceDisableRollUntilOneReturnsAndScoresCannotBeReused() {
        var game = new YachtGame(2, 9);
        game.apply(0, "roll");
        for (int i = 0; i < 5; i++) game.apply(0, "hold:die" + i);
        assertFalse(game.legalActions(0).contains("roll"));
        game.apply(0, "hold:die4");
        assertTrue(game.legalActions(0).contains("roll"));
        game.apply(0, "score:yacht");
        game.apply(1, "roll");
        game.apply(1, "score:choice");
        game.apply(0, "roll");
        assertFalse(game.legalActions(0).contains("score:yacht"));
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "score:yacht"));
    }

    @Test void languageReloadKeepsTheThrowLockedUntilItsFinalFrame() {
        var f = new TableViewTest.Fixture("yacht");
        try {
            f.move("roll");
            for (int i = 0; i < 5; i++) f.view.tick();
            assertTrue(Language.reload(temp, "zh_CN", warning -> fail(warning)));
            f.view.tick();
            assertTrue(f.view.rolling());
            for (int i = 0; i < 18; i++) f.view.tick();
            assertFalse(f.view.rolling());
        } finally {
            Language.reload(temp, "en_US", warning -> fail(warning));
            f.view.close();
        }
    }

    @Test void packedAndNativeLayersSwitchDuringARollWithoutChangingItsResult() {
        var f = new TableViewTest.Fixture("yacht");
        f.view.close(); f.entities.clear();
        var config = f.plugin.getConfig();
        config.set("rendering.mode", "mixed");
        config.set("rendering.resource-pack.url", "https://example.org/tabletop.zip");
        var data = ((org.mockbukkit.mockbukkit.ServerMock)org.bukkit.Bukkit.getServer()).addPlayer().getPersistentDataContainer();
        when(f.player.getPersistentDataContainer()).thenReturn(data);
        when(f.player.isOnline()).thenReturn(true);
        when(f.player.getLocation()).thenReturn(f.view.origin);
        when(f.world.getPlayers()).thenReturn(java.util.List.of(f.player));
        when(f.plugin.allowed(f.player)).thenReturn(true);
        f.plugin.pack = new TabletopPack(f.plugin, () -> true, id -> new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER));
        var table = new YachtTable(f.plugin, f.room, f.view.origin, new org.bukkit.NamespacedKey("servergames", "board-cell"));
        f.room.board.apply(0, "roll"); f.room.event(0, new com.google.gson.JsonPrimitive("roll")); f.room.revision++;
        table.sync(); table.tick(); assertTrue(table.rolling());
        f.plugin.pack.toggle(f.player);
        f.plugin.pack.status(f.player, f.plugin.pack.requestId(f.player), org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        table.tick(); assertTrue(table.rolling());
        assertEquals(6, f.entities.stream().filter(org.bukkit.entity.ItemDisplay.class::isInstance).count());
        for (int i = 0; i < 24; i++) table.tick();
        assertFalse(table.rolling());
        for (int i = 0; i < 5; i++) assertEquals("die" + i, table.hit(f.view.origin.clone().add(.32 + (i-2)*.26,2,.15),new Vector(0,-1,0)).cell());
        f.plugin.pack.toggle(f.player); table.tick();
        assertFalse(table.rolling());
        table.close();
        for (Entity entity : f.entities) verify(entity).remove();
    }

    private static void assertHit(TableViewTest.Fixture f, double x, double z, String expected) {
        var hit = f.view.hitPiece(f.view.origin.clone().add(x, 2, z), new Vector(0, -1, 0));
        assertNotNull(hit);
        assertEquals(expected, hit.cell());
    }
}
