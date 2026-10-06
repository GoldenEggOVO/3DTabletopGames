package dev.tabletop3d.render;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.TabletopTest;
import dev.tabletop3d.interaction.GameWorld;
import dev.tabletop3d.interaction.TableComfort;
import dev.tabletop3d.interaction.TableLobby;
import dev.tabletop3d.menu.GameMenus;
import dev.tabletop3d.room.Room;

import org.bukkit.Location;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TableMenuHitTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private GameWorld arena(TableViewTest.Fixture f) throws Exception {
        var arena = mock(GameWorld.class, CALLS_REAL_METHODS);
        TabletopTest.set(arena, "plugin", f.plugin);
        TabletopTest.set(arena, "views", new HashMap<>(Map.of(f.room.id, f.view)));
        TabletopTest.set(arena, "clicks", new HashMap<>());
        TabletopTest.set(arena, "selections", new HashMap<>());
        f.plugin.arena = arena;
        f.plugin.menus = mock(GameMenus.class);
        when(f.plugin.allowed(f.player)).thenReturn(true);
        when(f.plugin.room(f.player)).thenReturn(f.room);
        when(f.player.getWorld()).thenReturn(f.world);
        when(f.player.isSneaking()).thenReturn(true);
        return arena;
    }

    private void aim(TableViewTest.Fixture f, double x, double z) {
        when(f.player.getEyeLocation()).thenReturn(
                f.view.origin.clone().add(x, 2, z).setDirection(new Vector(0, -1, 0)));
    }

    @Test void seatedPlayerCanOpenFromTheEmptyBoardBorderWithoutSelectingACell() throws Exception {
        var f = new TableViewTest.Fixture("chess");
        var arena = arena(f);
        aim(f, 1.08, .60);
        assertTrue(arena.worldClick(f.player, true));
        assertTrue(f.room.history.isEmpty());
        verify(f.plugin.menus).room(f.player, f.room);
    }

    @Test void seatedPlayerCanOpenFromAGapBetweenGomokuCells() throws Exception {
        var f = new TableViewTest.Fixture("gomoku");
        var arena = arena(f);
        aim(f, .06, .06);
        assertTrue(arena.worldClick(f.player, true));
        verify(f.plugin.menus).room(f.player, f.room);
        assertTrue(f.room.history.isEmpty());
    }

    @Test void sneakingSwingCannotConsumeTheRightClickThatOpensTheMenu() throws Exception {
        var f = new TableViewTest.Fixture("gomoku");
        var arena = arena(f);
        aim(f, 0, 0);
        arena.swing(new org.bukkit.event.player.PlayerAnimationEvent(f.player,
                org.bukkit.event.player.PlayerAnimationType.ARM_SWING));
        assertTrue(arena.worldClick(f.player, true));
        assertTrue(arena.worldClick(f.player, true));
        verify(f.plugin.menus, times(1)).room(f.player, f.room);
        assertTrue(f.room.history.isEmpty());
    }

    @Test void yachtPlainFeltAndLeftScoreTableOpenOnceAcrossBothListeners() throws Exception {
        for (double x : new double[] {1.1, -2.30}) {
            var f = new TableViewTest.Fixture("yacht");
            var arena = arena(f);
            aim(f, x, .90);
            var lobby = new TableLobby(f.plugin);
            assertTrue(lobby.request(f.player));
            assertTrue(arena.worldClick(f.player, true));
            verify(f.plugin.menus, times(1)).room(f.player, f.room);
            assertTrue(f.room.history.isEmpty());
        }
    }

    @Test void connectFourTableAndTheUpperRackBothOpenTheirMenu() throws Exception {
        var table = new TableViewTest.Fixture("connectfour");
        var arena = arena(table);
        aim(table, .7, .8);
        assertTrue(arena.worldClick(table.player, true));
        verify(table.plugin.menus).room(table.player, table.room);
        var rack = new TableViewTest.Fixture("connectfour");
        arena = arena(rack);
        when(rack.player.getEyeLocation()).thenReturn(
                rack.view.origin.clone().add(0, 1.79, 2).setDirection(new Vector(0, 0, -1)));
        assertTrue(arena.worldClick(rack.player, true));
        verify(rack.plugin.menus).room(rack.player, rack.room);
    }

    @Test void solidWallAndExcessiveReachPreventTheEmptyTableMenu() throws Exception {
        var f = new TableViewTest.Fixture("chess");
        var arena = arena(f);
        aim(f, 1.08, .60);
        when(f.world.rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean()))
                .thenReturn(new RayTraceResult(new Vector(1.08, 82, .60)));
        assertFalse(arena.worldClick(f.player, true));
        when(f.world.rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean())).thenReturn(null);
        when(f.player.getEyeLocation()).thenReturn(
                f.view.origin.clone().add(1.08, 6, .60).setDirection(new Vector(0, -1, 0)));
        assertFalse(arena.worldClick(f.player, true));
        verifyNoInteractions(f.plugin.menus);
    }

    @Test void ordinaryRightClickAndSneakingLeftClickNeverOpenTheEmptyTableMenu() throws Exception {
        var f = new TableViewTest.Fixture("chess");
        var arena = arena(f);
        aim(f, 1.08, .60);
        when(f.player.isSneaking()).thenReturn(false);
        assertFalse(arena.worldClick(f.player, true));
        when(f.player.isSneaking()).thenReturn(true);
        assertFalse(arena.worldClick(f.player, false));
        verifyNoInteractions(f.plugin.menus);
    }

    @Test void focusedPlayerMustStillAimAtAReachableTableAndSneak() throws Exception {
        var f = new TableViewTest.Fixture("chess");
        var arena = arena(f);
        f.plugin.comfort = mock(TableComfort.class);
        when(f.plugin.comfort.focused(f.player)).thenReturn(true);
        when(f.player.getEyeLocation()).thenReturn(
                f.view.origin.clone().add(0, 2, 0).setDirection(new Vector(0, 1, 0)));
        arena.worldClick(f.player, true);
        aim(f, 1.08, .60);
        when(f.player.isSneaking()).thenReturn(false);
        arena.worldClick(f.player, true);
        verifyNoInteractions(f.plugin.menus);
        when(f.player.isSneaking()).thenReturn(true);
        assertTrue(arena.worldClick(f.player, true));
        verify(f.plugin.menus).room(f.player, f.room);
        verify(f.plugin.comfort).release(f.player);
    }

    @Test void outsiderCanAimAtYachtLeftTableMahjongBorderAndLudoSideTray() throws Exception {
        for (String kind : List.of("yacht", "mahjong", "ludo")) {
            var plugin = mock(Tabletop3D.class);
            plugin.arena = mock(GameWorld.class);
            var room = new Room(UUID.randomUUID(), kind, 2, 0, 0);
            room.sideTray = kind.equals("ludo");
            TabletopTest.set(plugin, "rooms", new LinkedHashMap<>(Map.of(room.id, room)));
            var world = mock(org.bukkit.World.class);
            when(plugin.arena.center(0)).thenReturn(new Location(world, 0, 80, 0));
            var entry = new TableLobby(plugin).collect().getFirst();
            double x = kind.equals("yacht") ? -2.30 : kind.equals("mahjong") ? 1.45 : 2.65;
            assertTrue(Double.isFinite(TableLobby.hit(entry,
                    new Location(world, x, 83, .60), new Vector(0, -1, 0))), kind);
        }
    }

    @Test void lobbyCannotPickAirBesideTheRackOrTheGapBetweenYachtTables() throws Exception {
        for (String kind : List.of("connectfour", "yacht", "color-eight")) {
            var plugin = mock(Tabletop3D.class);
            plugin.arena = mock(GameWorld.class);
            var room = new Room(UUID.randomUUID(), kind, 2, 0, 0);
            TabletopTest.set(plugin, "rooms", new LinkedHashMap<>(Map.of(room.id, room)));
            var world = mock(org.bukkit.World.class);
            when(plugin.arena.center(0)).thenReturn(new Location(world, 0, 80, 0));
            var entry = new TableLobby(plugin).collect().getFirst();
            Location eye = kind.equals("connectfour") ? new Location(world, -2.5, 82.5, .9)
                    : kind.equals("yacht") ? new Location(world, -.765, 83, .5)
                    : new Location(world, 1.4, 83, 1.4);
            Vector direction = kind.equals("connectfour") ? new Vector(1, 0, 0) : new Vector(0, -1, 0);
            assertEquals(Double.POSITIVE_INFINITY, TableLobby.hit(entry, eye, direction), kind);
        }
    }

    @Test void roundTableSideIsReachableWithoutExtendingTheUprightRackIntoAir() throws Exception {
        var plugin = mock(Tabletop3D.class);
        plugin.arena = mock(GameWorld.class);
        var world = mock(org.bukkit.World.class);
        when(plugin.arena.center(0)).thenReturn(new Location(world, 0, 80, 0));
        var room = new Room(UUID.randomUUID(), "color-eight", 2, 0, 0);
        TabletopTest.set(plugin, "rooms", new LinkedHashMap<>(Map.of(room.id, room)));
        var entry = new TableLobby(plugin).collect().getFirst();
        assertEquals(1, TableLobby.hit(entry, new Location(world, 2.5, 81.03, 0),
                new Vector(-1, 0, 0)), 1e-6);
        assertEquals(Double.POSITIVE_INFINITY, TableLobby.hit(entry,
                new Location(world, 7.1, 81.03, 0), new Vector(-1, 0, 0)));
        room = new Room(UUID.randomUUID(), "connectfour", 2, 0, 0);
        TabletopTest.set(plugin, "rooms", new LinkedHashMap<>(Map.of(room.id, room)));
        entry = new TableLobby(plugin).collect().getFirst();
        assertTrue(Double.isFinite(TableLobby.hit(entry, new Location(world, 2.5, 82, .09),
                new Vector(-1, 0, 0))));
        assertTrue(Double.isFinite(TableLobby.hit(entry, new Location(world, 1.15, 82, 2),
                new Vector(0, 0, -1))));
        assertEquals(Double.POSITIVE_INFINITY, TableLobby.hit(entry,
                new Location(world, 2.5, 82, .15), new Vector(-1, 0, 0)));
    }
}
