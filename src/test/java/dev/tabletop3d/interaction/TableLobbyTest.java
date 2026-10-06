package dev.tabletop3d.interaction;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.TabletopTest;
import dev.tabletop3d.menu.GameMenus;
import dev.tabletop3d.room.BoardOccupancy;
import dev.tabletop3d.room.Room;
import dev.tabletop3d.text.Language;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TableLobbyTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    TableLobby.Entry entry(Location center, List<String> names, int capacity) {
        return new TableLobby.Entry(
                "test",
                center,
                "mahjong",
                false,
                Room.Phase.LOBBY,
                names,
                capacity,
                Set.of(),
                p -> {},
                p -> {});
    }

    @Test
    void rosterIncludesNamesBotsAndClampsEmptySeats() {
        assertEquals(0, entry(null, List.of("A", "B", "C"), 2).empty());
    }

    @Test
    void aimingRejectsMissesWrongWorldAndOutOfReach() {
        World w = mock(World.class), other = mock(World.class);
        var e = entry(new Location(w, 0, 80, 0), List.of(), 4);
        assertEquals(
                1.90375, TableLobby.hit(e, new Location(w, 0, 83, 0), new Vector(0, -1, 0)), 1e-6);
        assertEquals(
                Double.POSITIVE_INFINITY,
                TableLobby.hit(e, new Location(w, 2, 83, 0), new Vector(0, -1, 0)));
        assertEquals(
                Double.POSITIVE_INFINITY,
                TableLobby.hit(e, new Location(other, 0, 83, 0), new Vector(0, -1, 0)));
        assertEquals(
                Double.POSITIVE_INFINITY,
                TableLobby.hit(e, new Location(w, 0, 90, 0), new Vector(0, -1, 0)));
        assertEquals(
                Double.POSITIVE_INFINITY,
                TableLobby.hit(e, new Location(w, 0, 83, 0), new Vector(0, 1, 0)));
    }

    @Test
    void originalReadyPlayerDoesNotNeedToClickAgainToStart() throws Exception {
        var plugin = mock(Tabletop3D.class);
        plugin.menus = mock(GameMenus.class);
        var r = new Room(UUID.randomUUID(), "gomoku", 2, 1, 0);
        Player a = mock(Player.class), b = mock(Player.class);
        when(a.getUniqueId()).thenReturn(UUID.randomUUID());
        when(b.getUniqueId()).thenReturn(UUID.randomUUID());
        TabletopTest.set(plugin, "rooms", new LinkedHashMap<>(Map.of(r.id, r)));
        when(plugin.allowed(any())).thenReturn(true);
        doCallRealMethod().when(plugin).ready(any(), any());
        r.join(a.getUniqueId(), "A");
        plugin.ready(a, r);
        r.join(b.getUniqueId(), "B");
        plugin.ready(b, r);
        assertEquals(Set.of(a.getUniqueId(), b.getUniqueId()), r.ready);
        verify(plugin).start(r);
    }

    @Test
    void leavingLobbyOnlyClearsTheDepartingPlayersReadyState() throws Exception {
        var plugin = mock(Tabletop3D.class);
        plugin.menus = mock(GameMenus.class);
        var field = Tabletop3D.class.getDeclaredField("returns");
        field.setAccessible(true);
        field.set(plugin, new HashMap<UUID, Location>());
        var r = new Room(UUID.randomUUID(), "checkers", 3, 1, 0);
        Player a = mock(Player.class);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        when(a.getUniqueId()).thenReturn(first);
        r.join(first, "A");
        r.join(second, "B");
        r.ready.addAll(List.of(first, second));
        plugin.coordinator = mock(BoardOccupancy.class);
        when(plugin.room(a)).thenReturn(r);
        doCallRealMethod().when(plugin).leave(a);
        plugin.leave(a);
        assertEquals(Set.of(second), r.ready);
        assertEquals(1, r.seats.size());
        verify(plugin, never()).remove(r);
    }

    Tabletop3D plugin() {
        var p = mock(Tabletop3D.class);
        p.coordinator = mock(BoardOccupancy.class);
        when(p.getName()).thenReturn("Tabletop3D");
        when(p.isEnabled()).thenReturn(true);
        when(p.getServer()).thenReturn(MockBukkit.getMock());
        when(p.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());
        when(p.allowed(any())).thenReturn(true);
        return p;
    }

    Player player(World world) {
        var p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.isOnline()).thenReturn(true);
        when(p.isSneaking()).thenReturn(true);
        when(p.getWorld()).thenReturn(world);
        when(p.getEyeLocation()).thenReturn(new Location(world, 0, 83, 0, 0, 90));
        return p;
    }

    void entries(TableLobby lobby, List<TableLobby.Entry> entries) throws Exception {
        var field = TableLobby.class.getDeclaredField("entries");
        field.setAccessible(true);
        field.set(lobby, entries);
        doReturn(entries).when(lobby).collect();
    }

    @Test
    void joinsOnceOnNextTickAndChecksFreshCapacity() throws Exception {
        var plugin = plugin();
        World world = mock(World.class);
        Player p = player(world);
        var lobby = spy(new TableLobby(plugin));
        var joined = new java.util.concurrent.atomic.AtomicInteger();
        var target =
                new TableLobby.Entry(
                        "test",
                        new Location(world, 0, 80, 0),
                        "mahjong",
                        false,
                        Room.Phase.LOBBY,
                        List.of("A"),
                        2,
                        Set.of(),
                        x -> joined.incrementAndGet(),
                        x -> {});
        entries(lobby, List.of(target));
        assertTrue(lobby.request(p));
        assertTrue(lobby.request(p));
        assertEquals(0, joined.get());
        MockBukkit.getMock().getScheduler().performOneTick();
        assertEquals(1, joined.get());
        // Another player takes the last seat before the deferred callback executes.
        Player second = player(world);
        assertTrue(lobby.request(second));
        var full =
                new TableLobby.Entry(
                        "test",
                        target.center(),
                        "mahjong",
                        false,
                        Room.Phase.LOBBY,
                        List.of("A", "B"),
                        2,
                        Set.of(),
                        target.join(),
                        target.menu());
        doReturn(List.of(full)).when(lobby).collect();
        MockBukkit.getMock().getScheduler().performOneTick();
        assertEquals(1, joined.get());
        verify(plugin).tell(second, Language.component("chat.table.full"));
    }

    @Test
    void theUprightConnectFourRackCanOpenItsMenuFromEitherFace() throws Exception {
        var plugin = plugin();
        World world = mock(World.class);
        plugin.arena = mock(GameWorld.class);
        var room = new Room(UUID.randomUUID(), "connectfour", 2, 0, 0);
        TabletopTest.set(plugin, "rooms", new LinkedHashMap<>(Map.of(room.id, room)));
        when(plugin.arena.center(0)).thenReturn(new Location(world, 0, 80, 0));
        var lobby = new TableLobby(plugin);
        var entry = lobby.collect().getFirst();
        for (int side : new int[] {-1, 1})
            assertTrue(
                    Double.isFinite(
                            TableLobby.hit(
                                    entry,
                                    new Location(world, 0, 82, side * 2.25),
                                    new Vector(0, 0, -side))),
                    "The upper rack must be a menu target");
    }

    @Test
    void newTablesAreImmediatelyClickableBeforeThePeriodicRefresh() throws Exception {
        var plugin = plugin();
        World world = mock(World.class);
        Player p = player(world);
        var lobby = spy(new TableLobby(plugin));
        var joined = new java.util.concurrent.atomic.AtomicInteger();
        var target =
                new TableLobby.Entry(
                        "fresh",
                        new Location(world, 0, 80, 0),
                        "mahjong",
                        false,
                        Room.Phase.LOBBY,
                        List.of(),
                        2,
                        Set.of(),
                        x -> joined.incrementAndGet(),
                        x -> {});
        doReturn(List.of(target)).when(lobby).collect();
        assertTrue(lobby.request(p));
        MockBukkit.getMock().getScheduler().performOneTick();
        assertEquals(1, joined.get());
    }

    @Test
    void seatedPlayerOpensMenuAndPlayingTableRejectsOutsider() throws Exception {
        var plugin = plugin();
        World world = mock(World.class);
        Player p = player(world);
        var lobby = spy(new TableLobby(plugin));
        var opened = new java.util.concurrent.atomic.AtomicInteger();
        var joined = new java.util.concurrent.atomic.AtomicInteger();
        var e =
                new TableLobby.Entry(
                        "test",
                        new Location(world, 0, 80, 0),
                        "mahjong",
                        false,
                        Room.Phase.PLAYING,
                        List.of("A"),
                        4,
                        Set.of(p.getUniqueId()),
                        x -> joined.incrementAndGet(),
                        x -> opened.incrementAndGet());
        entries(lobby, List.of(e));
        assertTrue(lobby.request(p));
        MockBukkit.getMock().getScheduler().performOneTick();
        assertEquals(1, opened.get());
        Player stranger = player(world);
        assertTrue(lobby.request(stranger));
        MockBukkit.getMock().getScheduler().performOneTick();
        assertEquals(0, joined.get());
        verify(plugin).tell(stranger, Language.component("chat.table.started"));
    }

    @Test
    void focusedSeatedPlayerCanOpenWaitingTableMenuOnceAfterReleasingFocus() throws Exception {
        var plugin = plugin();
        World world = mock(World.class);
        Player p = player(world);
        var lobby = spy(new TableLobby(plugin));
        plugin.comfort = mock(TableComfort.class);
        when(plugin.comfort.focused(p)).thenReturn(true);
        var opened = new java.util.concurrent.atomic.AtomicInteger();
        var target =
                new TableLobby.Entry(
                        "focus-lobby",
                        new Location(world, 0, 80, 0),
                        "mahjong",
                        false,
                        Room.Phase.LOBBY,
                        List.of("A"),
                        4,
                        Set.of(p.getUniqueId()),
                        x -> fail("Seated player must not join again"),
                        x -> {
                            verify(plugin.comfort).release(p);
                            opened.incrementAndGet();
                        });
        entries(lobby, List.of(target));
        assertTrue(lobby.request(p));
        assertTrue(lobby.request(p));
        verify(plugin.comfort, times(1)).release(p);
        assertEquals(0, opened.get());
        MockBukkit.getMock().getScheduler().performOneTick();
        assertEquals(1, opened.get());
    }

    @Test
    void ordinaryClickAndSolidObstructionDoNotJoin() throws Exception {
        var plugin = plugin();
        World world = mock(World.class);
        Player p = player(world);
        var lobby = spy(new TableLobby(plugin));
        entries(lobby, List.of(entry(new Location(world, 0, 80, 0), List.of(), 4)));
        when(p.isSneaking()).thenReturn(false);
        assertFalse(lobby.request(p));
        when(p.isSneaking()).thenReturn(true);
        when(world.rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean()))
                .thenReturn(new org.bukkit.util.RayTraceResult(new Vector(0, 82, 0)));
        assertFalse(lobby.request(p));
    }

    @Test
    void lobbyRefreshDoesNotDuplicateBoardLabels() throws Exception {
        var plugin = plugin();
        World world = mock(World.class);
        var lobby = spy(new TableLobby(plugin));
        entries(lobby, List.of(entry(new Location(world, 0, 80, 0), List.of("A"), 4)));
        lobby.refresh();
        lobby.refresh();
        lobby.close();
        verify(world, never())
                .spawn(
                        any(Location.class),
                        eq(TextDisplay.class),
                        any(java.util.function.Consumer.class));
    }
}
