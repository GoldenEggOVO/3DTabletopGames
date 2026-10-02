package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonPrimitive;

import dev.tabletop3d.rules.*;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

class TabletopTest {
    static final List<String> KINDS =
            List.of(
                    "chess",
                    "xiangqi",
                    "gomoku",
                    "checkers",
                    "aeroplane",
                    "ludo",
                    "draughts",
                    "reversi",
                    "yacht",
                    "go9",
                    "go13",
                    "go");

    @Test
    void everyCellCenterMapsBackToItsOwnCellAndFitsTheNativeMap() {
        for (String kind : KINDS) {
            BoardGame board = GameFactory.create(kind, 2, 0);
            TableGeometry t = new TableGeometry(kind, board.cells());
            for (Cell c : board.cells()) {
                assertEquals(c.id(), t.hit(t.x(c), t.z(c)), kind + " " + c.id());
                assertTrue(t.px(c) > 10 && t.px(c) < 246);
                assertTrue(t.pz(c) > 10 && t.pz(c) < 246);
            }
            assertNull(t.hit(-1.5, 0));
            assertNull(t.hit(0, -1.5));
            assertNull(t.hit(Double.NaN, 0));
            assertNull(t.hit(20, 20));
            assertEquals("@menu", t.hit(0, 1.27));
        }
    }

    @Test
    void pointerRejectsUpwardParallelBeyondReachAndBehindEye() {
        assertEquals(-1, TableGeometry.intersection(2, 0, 1));
        assertEquals(-1, TableGeometry.intersection(2, 1, 1));
        assertEquals(-1, TableGeometry.intersection(1, -1, 2));
        assertEquals(-1, TableGeometry.intersection(20, -1, 1));
        assertEquals(2, TableGeometry.intersection(2, -.5, 1));
        assertEquals(-1, TableGeometry.intersection(Double.NaN, -1, 1));
    }

    @Test
    void chineseStarCampArtworkMatchesAllSixtyOriginalStartingHoles() {
        BoardGame game = GameFactory.create("checkers", 6, 0);
        int colored = 0;
        for (Cell c : game.cells()) {
            assertEquals(c.owner(), TableArt.camp(c), c.id());
            if (TableArt.camp(c) >= 0) colored++;
        }
        assertEquals(60, colored);
    }

    @Test
    void diceAlwaysFinishesWithTheAuthoritativeFaceUpAndOppositeFacesSumToSeven() {
        for (int face = 1; face <= 6; face++) {
            assertEquals(face, TableView.pipIndices(face).size());
            Vector3f normal = new Vector3f(0, 1, 0).rotate(TableView.faceRotation(face));
            assertEquals(
                    -1,
                    normal.dot(new Vector3f(0, 1, 0).rotate(TableView.faceRotation(7 - face))),
                    1e-5);
            normal.rotate(TableView.faceRotation(face).invert());
            assertEquals(1, normal.y, 1e-5);
        }
    }

    @Test
    void originalModelsHaveBoundedEntityCountsAndFitTheirCells() throws Exception {
        for (String kind : KINDS) {
            BoardGame game = GameFactory.create(kind, kind.equals("checkers") ? 6 : 2, 0);
            if (kind.equals("yacht")) game.apply(0, "roll");
            if (kind.startsWith("go")) game.apply(0, "place:0,0");
            for (Cell c : game.cells())
                if (c.owner() >= 0) {
                    List<TableModels.Part> parts = TableModels.piece(kind, c, game.publicInfo());
                    assertFalse(parts.isEmpty());
                    assertTrue(parts.size() <= 10, kind + " " + c.piece() + " " + parts.size());
                    for (var p : parts) {
                        double limit = kind.equals("yacht") ? 1 : .5;
                        assertTrue(Math.abs(p.x()) + p.w() / 2 < limit);
                        assertTrue(Math.abs(p.z()) + p.d() / 2 < limit);
                        assertTrue(p.y() >= 0 && p.h() > 0 && p.w() > 0 && p.d() > 0);
                    }
                }
        }
    }

    @Test
    void stoneDiscsKeepThreePartsWithoutOverlappingVolumesOrTopFaces() {
        List<TableModels.Part> parts = new ArrayList<>();
        TableModels.disc(parts, Material.BLACK_CONCRETE, .76, .13);
        assertEquals(3, parts.size());
        for (int i = 0; i < parts.size(); i++)
            for (int j = i + 1; j < parts.size(); j++) {
                var a = parts.get(i);
                var b = parts.get(j);
                double x =
                        Math.min(a.x() + a.w() / 2, b.x() + b.w() / 2)
                                - Math.max(a.x() - a.w() / 2, b.x() - b.w() / 2);
                double z =
                        Math.min(a.z() + a.d() / 2, b.z() + b.d() / 2)
                                - Math.max(a.z() - a.d() / 2, b.z() - b.d() / 2);
                assertTrue(
                        x <= 1e-9 || z <= 1e-9,
                        "Disc strips must meet without overlapping top surfaces");
            }
    }

    @Test
    void staticArtExportsFromExactlyTheGeometryUsedByTheServer() throws Exception {
        String export = System.getProperty("tabletop.export");
        for (String kind : KINDS) {
            BoardGame game =
                    GameFactory.create(
                            kind,
                            kind.equals("checkers") ? 6 : kind.equals("aeroplane") ? 4 : 2,
                            0);
            TableGeometry t = new TableGeometry(kind, game.cells());
            var image = TableArt.draw(t);
            assertEquals(256, image.getWidth());
            assertEquals(256, image.getHeight());
            assertNotEquals(image.getRGB(0, 0), image.getRGB(128, 128));
            if (export != null) {
                Path dir = Path.of(export);
                Files.createDirectories(dir);
                ImageIO.write(image, "png", dir.resolve(kind + "-board.png").toFile());
                var json = new com.google.gson.JsonObject();
                json.addProperty("kind", kind);
                var pieces = new com.google.gson.JsonArray();
                for (Cell c : game.cells())
                    if (c.owner() >= 0) {
                        var piece = new com.google.gson.JsonObject();
                        piece.addProperty("x", t.x(c));
                        piece.addProperty("z", t.z(c));
                        piece.addProperty("scale", t.spacing);
                        piece.addProperty("label", kind.equals("xiangqi") ? c.piece() : "");
                        piece.addProperty("owner", c.owner());
                        piece.add(
                                "parts",
                                new com.google.gson.Gson()
                                        .toJsonTree(TableModels.piece(kind, c, game.publicInfo())));
                        pieces.add(piece);
                    }
                json.add("pieces", pieces);
                Files.writeString(dir.resolve(kind + "-models.json"), json.toString());
            }
        }
    }

    @Test
    void pointerClickKeepsIdentityTurnAndDuplicatePacketGates() throws Exception {
        Fixture f = new Fixture();
        assertTrue(f.click());
        verify(f.plugin).apply(eq(f.room), eq(0), eq(new JsonPrimitive("place:7,7")), isNull());
        assertTrue(f.click());
        verify(f.plugin, times(1)).apply(any(), anyInt(), any(), any());
        f.clicks.clear();
        when(f.plugin.allowed(f.player)).thenReturn(false);
        assertFalse(f.click());
        when(f.plugin.allowed(f.player)).thenReturn(true);
        when(f.plugin.room(f.player)).thenReturn(null);
        assertFalse(f.click());
        when(f.plugin.room(f.player)).thenReturn(f.room);
        f.room.board.apply(0, "place:7,7");
        f.clicks.clear();
        assertTrue(f.click());
        verify(f.plugin, times(1)).apply(any(), anyInt(), any(), any());
    }

    @Test
    void colorEightFeltMenuRequiresSneakingAndRightClick() throws Exception {
        Fixture f = new Fixture();
        set(f.room, "kind", "color-eight");
        f.plugin.menus = mock(GameMenus.class);
        f.room.board = mock(HandGame.class);
        TableView view =
                ((Map<UUID, TableView>) TableViewTest.field(f.arena, "views")).get(f.room.id);
        set(view, "geometry", new TableGeometry("color-eight", List.of()));
        when(f.player.getEyeLocation())
                .thenAnswer(
                        a ->
                                new Location(f.world, 0, 1.62, 2.25)
                                        .setDirection(new Vector(0, -.74, -2.25)));
        assertTrue(f.click());
        verify(f.plugin.menus, never()).room(any(), any());
        f.clicks.clear();
        when(f.player.isSneaking()).thenReturn(true);
        assertTrue(f.click());
        verify(f.plugin.menus, never()).room(any(), any());
        f.clicks.clear();
        Method method =
                GameWorld.class.getDeclaredMethod("worldClick", Player.class, boolean.class);
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(f.arena, f.player, true));
        verify(f.plugin.menus).room(f.player, f.room);
        verify(f.plugin, never()).apply(any(), anyInt(), any(), any());
    }

    @Test
    void focusedMahjongRightClickOpensMenuAndReleasesFocusBeforeTileActions() throws Exception {
        for (boolean tile : new boolean[] {false, true}) {
            Fixture f = new Fixture();
            set(f.room, "kind", "mahjong");
            f.room.board = mock(HandGame.class);
            f.plugin.menus = mock(GameMenus.class);
            f.plugin.comfort = mock(TableComfort.class);
            when(f.plugin.comfort.focused(f.player)).thenReturn(true);
            when(f.player.isSneaking()).thenReturn(true);
            TableView view =
                    ((Map<UUID, TableView>) TableViewTest.field(f.arena, "views")).get(f.room.id);
            if (tile)
                when(view.handHit(eq(f.player), any(), any())).thenReturn("public:discard:1:a");
            Method method =
                    GameWorld.class.getDeclaredMethod("worldClick", Player.class, boolean.class);
            method.setAccessible(true);
            assertTrue((boolean) method.invoke(f.arena, f.player, true));
            var order = inOrder(f.plugin.comfort, f.plugin.menus);
            order.verify(f.plugin.comfort).release(f.player);
            order.verify(f.plugin.menus).room(f.player, f.room);
            verify(f.plugin, never()).apply(any(), anyInt(), any(), any());
        }
    }

    @Test
    void mahjongOrdinaryFeltClicksDoNotOpenMenu() throws Exception {
        Fixture f = new Fixture();
        set(f.room, "kind", "mahjong");
        f.room.board = mock(HandGame.class);
        f.plugin.menus = mock(GameMenus.class);
        for (boolean rightClick : new boolean[] {false, true}) {
            f.clicks.clear();
            Method method =
                    GameWorld.class.getDeclaredMethod("worldClick", Player.class, boolean.class);
            method.setAccessible(true);
            assertTrue((boolean) method.invoke(f.arena, f.player, rightClick));
        }
        verify(f.plugin.menus, never()).room(any(), any());
        verify(f.plugin, never()).apply(any(), anyInt(), any(), any());
    }

    @Test
    void focusedMahjongLeftClickOnlyObservesAndNeverPlaysTheHandTile() throws Exception {
        Fixture f = new Fixture();
        set(f.room, "kind", "mahjong");
        HandGame game = mock(HandGame.class);
        f.room.board = game;
        when(game.hand(0)).thenReturn(List.of(new HandGame.Piece("a", "1m")));
        when(game.legalActions(0)).thenReturn(List.of("discard:a"));
        f.plugin.menus = mock(GameMenus.class);
        f.plugin.comfort = mock(TableComfort.class);
        when(f.plugin.comfort.focused(f.player)).thenReturn(true);
        when(f.player.isSneaking()).thenReturn(true);
        TableView view =
                ((Map<UUID, TableView>) TableViewTest.field(f.arena, "views")).get(f.room.id);
        when(view.handHit(eq(f.player), any(), any())).thenReturn("a");
        when(view.mahjongHandAction(f.player, "a")).thenReturn("discard:a");
        assertTrue(f.click());
        verify(f.plugin, never()).apply(any(), anyInt(), any(), any());
        verify(f.plugin.comfort, never()).release(any());
        verify(f.plugin.menus, never()).room(any(), any());
    }

    @Test
    void blockInteractionRoutesRightClickToTheColorEightMenuButNotLeftClick() throws Exception {
        Fixture f = new Fixture();
        set(f.room, "kind", "color-eight");
        f.room.board = mock(HandGame.class);
        f.plugin.menus = mock(GameMenus.class);
        when(f.player.isSneaking()).thenReturn(true);
        for (var action :
                List.of(
                        org.bukkit.event.block.Action.LEFT_CLICK_BLOCK,
                        org.bukkit.event.block.Action.RIGHT_CLICK_AIR)) {
            f.clicks.clear();
            var event =
                    new org.bukkit.event.player.PlayerInteractEvent(
                            f.player,
                            action,
                            null,
                            null,
                            org.bukkit.block.BlockFace.SELF,
                            org.bukkit.inventory.EquipmentSlot.HAND);
            f.arena.use(event);
            assertTrue(event.isCancelled());
            verify(f.plugin.menus, times(action.isRightClick() ? 1 : 0)).room(f.player, f.room);
        }
    }

    @Test
    void anOccupiedPlacementReportsTheCellProblemWithoutChatSpam() throws Exception {
        Fixture f = new Fixture();
        f.room.board.apply(0, "place:7,7");
        f.room.board.apply(1, "place:8,7");
        assertTrue(f.click());
        verify(f.plugin, never()).tell(any(), any(net.kyori.adventure.text.Component.class));
        verify(f.plugin, never()).apply(any(), anyInt(), any(), any());
        verify(f.player)
                .sendActionBar(
                        Language.component("hint.position.unavailable")
                                .colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    @Test
    void continuousMahjongPressesDuringOtherTurnsMaintainTheReplacementGuard() throws Exception {
        Fixture f = new Fixture();
        set(f.room, "kind", "mahjong");
        HandGame game = mock(HandGame.class);
        when(game.currentPlayer()).thenReturn(1);
        f.room.board = game;
        TableView view =
                ((Map<UUID, TableView>) TableViewTest.field(f.arena, "views")).get(f.room.id);
        when(view.handHit(eq(f.player), any(), any())).thenReturn("other-tile");
        assertTrue(f.click());
        verify(view).maintainMahjongPress(f.player);
        verify(f.plugin, never()).apply(any(), anyInt(), any(), any());
    }

    @Test
    void suspendingTheViewAlsoClearsPrivateWorldSelection() throws Exception {
        Fixture f = new Fixture();
        f.plugin.arena = f.arena;
        f.plugin.menus = mock(GameMenus.class);
        doCallRealMethod().when(f.plugin).suspendView(f.player);
        var selections = (Map<UUID, GameWorld.Pick>) TableViewTest.field(f.arena, "selections");
        selections.put(
                f.player.getUniqueId(),
                new GameWorld.Pick(f.room.id, f.room.revision, "7,7", List.of()));
        TabletopTest.set(f.plugin, "rooms", new HashMap<UUID, Room>());
        TableView view =
                ((Map<UUID, TableView>) TableViewTest.field(f.arena, "views")).get(f.room.id);
        f.plugin.suspendView(f.player);
        assertTrue(selections.isEmpty());
        verify(view).clear(f.player);
        verify(f.plugin.menus).forget(f.player);
    }

    @Test
    void pointerDoesNotClickThroughWallsOrOutsideItsTable() throws Exception {
        Fixture f = new Fixture();
        when(f.world.rayTraceBlocks(
                        any(), any(), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true)))
                .thenReturn(new RayTraceResult(new Vector(0, 1, 1)));
        assertFalse(f.click());
        verify(f.plugin, never()).apply(any(), anyInt(), any(), any());
        when(f.world.rayTraceBlocks(
                        any(), any(), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true)))
                .thenReturn(null);
        when(f.player.getEyeLocation())
                .thenReturn(new Location(f.world, 0, 1.62, 2.25).setDirection(new Vector(0, 1, 0)));
        assertFalse(f.click());
    }

    @Test
    void emptyMahjongDrawSlotKeepsRepeatedPressProtectionDuringOtherTurns() throws Exception {
        for (boolean overTable : new boolean[] {false, true}) {
            Fixture f = new Fixture();
            set(f.room, "kind", "mahjong");
            HandGame game = mock(HandGame.class);
            when(game.currentPlayer()).thenReturn(1);
            f.room.board = game;
            TableView view =
                    ((Map<UUID, TableView>) TableViewTest.field(f.arena, "views")).get(f.room.id);
            f.plugin.comfort = mock(TableComfort.class);
            when(f.player.getEyeLocation())
                    .thenReturn(
                            new Location(f.world, 0, 1.62, 2.25)
                                    .setDirection(new Vector(0, overTable ? -.74 : 1, -2.25)));
            assertEquals(overTable, f.click());
            verify(view).maintainMahjongPress(f.player);
            verify(f.plugin, never()).apply(any(), anyInt(), any(), any());
        }
    }

    static void set(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(object, value);
    }

    static final class Fixture {
        final Tabletop3D plugin = mock(Tabletop3D.class);
        final GameWorld arena = mock(GameWorld.class, CALLS_REAL_METHODS);
        final World world = mock(World.class);
        final Player player = mock(Player.class);
        final Room room = new Room(UUID.randomUUID(), "gomoku", 2, 0, 0);
        final Map<UUID, Long> clicks = new HashMap<>();

        Fixture() throws Exception {
            UUID id = UUID.randomUUID();
            room.join(id, "测试玩家");
            room.fillBots();
            room.board = GameFactory.create("gomoku", 2, 0);
            room.phase = Room.Phase.PLAYING;
            TableView view = mock(TableView.class);
            set(view, "room", room);
            set(view, "origin", new Location(world, 0, .85, 0));
            set(view, "geometry", new TableGeometry("gomoku", room.board.cells()));
            set(arena, "plugin", plugin);
            set(arena, "views", new HashMap<>(Map.of(room.id, view)));
            set(arena, "clicks", clicks);
            set(arena, "selections", new HashMap<>());
            arena.world = world;
            when(plugin.allowed(player)).thenReturn(true);
            when(plugin.room(player)).thenReturn(room);
            when(player.getUniqueId()).thenReturn(id);
            when(player.getWorld()).thenReturn(world);
            when(player.getEyeLocation())
                    .thenAnswer(
                            a ->
                                    new Location(world, 0, 1.62, 2.25)
                                            .setDirection(new Vector(0, -.74, -2.25)));
        }

        boolean click() throws Exception {
            Method method = GameWorld.class.getDeclaredMethod("worldClick", Player.class);
            method.setAccessible(true);
            return (boolean) method.invoke(arena, player);
        }
    }
}
