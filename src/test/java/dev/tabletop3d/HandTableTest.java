package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.tabletop3d.rules.HandGame;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.function.Consumer;

class HandTableTest {
    @Test
    void cardTurnIndicatorMovesWithTheActiveSeatAndIsRemovedAtRoundEnd() {
        Fixture f = new Fixture("color-eight");
        when(f.game.currentPlayer()).thenReturn(0);
        f.room.revision++;
        f.table.sync();
        Entity marker =
                f.entities.stream()
                        .filter(e -> e.getScoreboardTags().contains("tabletop-turn-indicator"))
                        .findFirst()
                        .orElseThrow();
        Location first = marker.getLocation();
        when(f.game.currentPlayer()).thenReturn(1);
        f.room.revision++;
        f.table.sync();
        assertTrue(first.distanceSquared(marker.getLocation()) > 1);
        f.room.phase = Room.Phase.FINISHED;
        f.room.revision++;
        f.table.sync();
        verify(marker).remove();
    }

    @Test
    void passButtonLiesFlatInFrontOfTheHandAndKeepsItsClickableArea() throws Exception {
        Fixture f = new Fixture("color-eight");
        when(f.game.legalActions(0)).thenReturn(List.of("pass"));
        int start = f.entities.size();
        f.table.show(f.owner);
        var views = HandTable.class.getDeclaredField("privateViews");
        views.setAccessible(true);
        Object view = ((Map<?, ?>) views.get(f.table)).get(f.owner.getUniqueId());
        var buttons = view.getClass().getDeclaredField("cardButtons");
        buttons.setAccessible(true);
        Object button = ((Map<?, ?>) buttons.get(view)).get("pass");
        var boxField = button.getClass().getDeclaredField("bounds");
        boxField.setAccessible(true);
        var box = (org.bukkit.util.BoundingBox) boxField.get(button);
        assertTrue(box.getHeight() < .025, "Pass must be horizontal, not standing upright");
        assertTrue(box.getWidthZ() >= .08, "A flat button needs table-depth hit area");
        assertTrue(box.getCenterZ() > 1.28 && box.getCenterZ() < 1.42,
                "Pass must lie between the player's hand and body, near the front edge");
        TextDisplay label = (TextDisplay) f.entities.subList(start, f.entities.size()).stream()
                .filter(TextDisplay.class::isInstance).findFirst().orElseThrow();
        verify(label).setRotation(floatThat(yaw -> Math.abs(yaw) < 1e-5), eq(-90f));
    }

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void publicViewUsesCountsAndOnlyOwnerReceivesHiddenFaces() {
        Fixture f = new Fixture("color-eight");
        verify(f.game, never()).hand(anyInt());
        int before = f.entities.size();
        f.table.show(f.owner);
        List<Entity> privateFaces = List.copyOf(f.entities.subList(before, f.entities.size()));
        assertFalse(privateFaces.isEmpty());
        for (Entity entity : privateFaces) {
            verify(entity).setVisibleByDefault(false);
            verify(f.owner).showEntity(f.plugin, entity);
            verify(f.spectator, never()).showEntity(f.plugin, entity);
        }
        verify(f.game).hand(0);
        verify(f.game, never()).hand(1);
        f.table.show(f.spectator);
        assertEquals(before + privateFaces.size(), f.entities.size());
        f.table.clear(f.owner);
        for (Entity entity : privateFaces) verify(entity).remove();
    }

    @Test
    void unchangedShowsReuseEntitiesAndRemovedCardsAreCleaned() {
        Fixture f = new Fixture("color-eight");
        f.table.show(f.owner);
        int count = f.entities.size();
        clearInvocations(f.game);
        for (int i = 0; i < 25; i++) {
            f.table.sync();
            f.table.show(f.owner);
        }
        assertEquals(count, f.entities.size());
        verify(f.game, never()).hand(anyInt());
        when(f.game.hand(0)).thenReturn(List.of(new HandGame.Piece("a", "r1")));
        when(f.game.handSize(0)).thenReturn(1);
        f.room.revision++;
        f.table.sync();
        f.table.show(f.owner);
        assertEquals(count, f.entities.size(), "Retained card and backs must be reused");
        assertTrue(f.entities.stream().anyMatch(e -> !e.isValid()));
        f.table.close();
        for (Entity entity : f.entities) verify(entity).remove();
    }

    @Test
    void publicBackCountTracksOnlyCountsAndHasNoPrivateIdentifiers() {
        Fixture f = new Fixture("color-eight");
        int initial = f.entities.size();
        when(f.game.handSize(1)).thenReturn(4);
        f.room.revision++;
        f.table.sync();
        assertTrue(f.entities.size() > initial, "One additional card must add a public back model");
        verify(f.game, never()).hand(anyInt());
        for (Entity entity : f.entities) {
            verify(entity, never()).setVisibleByDefault(false);
            verify(entity.getPersistentDataContainer())
                    .set(
                            any(),
                            eq(org.bukkit.persistence.PersistentDataType.STRING),
                            eq(f.room.id + "|@board"));
        }
    }

    @Test
    void concealedKongUsesPublicBackMeshesWithoutTextAndRemainsVisibleToItsOwner() {
        Fixture f = new Fixture("mahjong");
        int initial = f.entities.size();
        when(f.game.exposed(0))
                .thenReturn(
                        List.of(
                                new HandGame.Piece("k0", "back"),
                                new HandGame.Piece("k1", "back"),
                                new HandGame.Piece("k2", "back"),
                                new HandGame.Piece("k3", "back")));
        f.room.revision++;
        f.table.sync();
        List<Entity> kong = List.copyOf(f.entities.subList(initial, f.entities.size()));
        assertEquals(8, kong.size());
        assertTrue(kong.stream().allMatch(BlockDisplay.class::isInstance));
        for (Entity part : kong) verify(part, never()).setVisibleByDefault(false);
        f.table.show(f.owner);
        for (Entity part : kong) verify(f.owner, never()).hideEntity(f.plugin, part);
    }

    @Test
    void sixteenTileProfilesDisplayAllFiveKongsAndEightFlowers() {
        Fixture f = new Fixture("mahjong");
        int initial = f.entities.size();
        List<HandGame.Piece> exposed = new ArrayList<>();
        for (int i = 0; i < 20; i++) exposed.add(new HandGame.Piece("kong" + i, "m" + (i / 4 + 1)));
        for (int i = 1; i <= 8; i++) exposed.add(new HandGame.Piece("flower" + i, "f" + i));
        when(f.game.exposed(0)).thenReturn(exposed);
        f.room.revision++;
        f.table.sync();
        long bodies =
                f.entities.subList(initial, f.entities.size()).stream()
                        .filter(
                                e ->
                                        Math.abs(e.getLocation().getY() - (f.origin.getY() + .017))
                                                < 1e-6)
                        .count();
        assertEquals(
                28, bodies, "Every public tile has a body, including all flowers after five kongs");
    }

    @Test
    void eachSeatedPlayerReceivesOnlyTheirOwnFaceEntities() {
        Fixture f = new Fixture("color-eight");
        f.table.show(f.owner);
        f.room.seats.set(1, new Room.Seat(f.spectator.getUniqueId(), "Peer", false));
        when(f.game.hand(1))
                .thenReturn(
                        List.of(
                                new HandGame.Piece("c", "b2"),
                                new HandGame.Piece("d", "y3"),
                                new HandGame.Piece("e", "wild")));
        int initial = f.entities.size();
        f.table.show(f.spectator);
        for (Entity face : f.entities.subList(initial, f.entities.size())) {
            verify(face).setVisibleByDefault(false);
            verify(f.spectator).showEntity(f.plugin, face);
            verify(f.owner, never()).showEntity(f.plugin, face);
        }
        verify(f.game).hand(0);
        verify(f.game).hand(1);
    }

    @Test
    void changingBoardWithSameRevisionRefreshesPrivateFacesAndClearsNonHandBoards() {
        Fixture f = new Fixture("color-eight");
        int publicCount = f.entities.size();
        f.table.show(f.owner);
        List<Entity> oldFaces = List.copyOf(f.entities.subList(publicCount, f.entities.size()));
        HandGame replacement = mock(HandGame.class);
        when(replacement.playerCount()).thenReturn(2);
        when(replacement.handSize(0)).thenReturn(1);
        when(replacement.hand(0)).thenReturn(List.of(new HandGame.Piece("a", "b7")));
        f.room.board = replacement;
        f.table.sync();
        f.table.show(f.owner);
        for (Entity face : oldFaces) verify(face).remove();
        f.room.board = new dev.tabletop3d.rules.GomokuGame();
        f.table.sync();
        assertNull(f.table.hit(f.owner, f.origin.clone().add(0, 1, 0), new Vector(0, -1, 0)));
        f.table.close();
        for (Entity entity : f.entities) verify(entity).remove();
    }

    @Test
    void authLossLobbyAndWorldChangeClearPrivateFaces() {
        Fixture f = new Fixture("mahjong");
        f.table.show(f.owner);
        int total = f.entities.size();
        when(f.plugin.allowed(f.owner)).thenReturn(false);
        f.table.show(f.owner);
        assertTrue(f.entities.stream().anyMatch(e -> !e.isValid()));
        when(f.plugin.allowed(f.owner)).thenReturn(true);
        f.room.phase = Room.Phase.LOBBY;
        f.table.show(f.owner);
        assertEquals(total, f.entities.size());
        f.room.phase = Room.Phase.PLAYING;
        when(f.owner.getWorld()).thenReturn(mock(World.class));
        f.table.show(f.owner);
        assertEquals(total, f.entities.size());
    }

    @Test
    void ownerHitChecksReachWorldAndBlockOcclusion() {
        Fixture f = new Fixture("color-eight");
        f.table.show(f.owner);
        var pose = HandTable.handPose(0, 2, 0, 2, false);
        Location eye = f.origin.clone().add(pose.x(), 1, pose.z());
        assertEquals("a", f.table.hit(f.owner, eye, new Vector(0, -1, 0)));
        assertNull(f.table.hit(f.spectator, eye, new Vector(0, -1, 0)));
        assertNull(f.table.hit(f.owner, eye.clone().add(0, 6, 0), new Vector(0, -1, 0)));
        when(f.world.rayTraceBlocks(
                        any(Location.class),
                        any(Vector.class),
                        anyDouble(),
                        eq(FluidCollisionMode.NEVER),
                        eq(true)))
                .thenReturn(
                        new org.bukkit.util.RayTraceResult(eye.clone().add(0, -.1, 0).toVector()));
        assertNull(f.table.hit(f.owner, eye, new Vector(0, -1, 0)));
    }

    @Test
    void largeHandsStayOnTheTable() {
        for (int players : new int[] {2, 3, 4, 5})
            for (int seat = 0; seat < players; seat++)
                for (int i = 0; i < 54; i++) {
                    var pose = HandTable.handPose(seat, players, i, 54, false);
                    assertTrue(Math.hypot(pose.x(), pose.z()) + .09 < RoundCardTable.RADIUS);
                }
    }

    @Test
    void fourRiversAndMeldRowsHaveNonOverlappingPhysicalTiles() {
        List<org.bukkit.util.BoundingBox> tiles = new ArrayList<>();
        tiles.add(new org.bukkit.util.BoundingBox(-.15, 0, -.09, -.02, .04, .09));
        for (int seat = 0; seat < 4; seat++) {
            for (int i = 0; i < 18; i++) tiles.add(tileBounds(HandTable.riverPose(seat, 4, i)));
            for (int i = 0; i < 28; i++) {
                var pose = HandTable.exposedPose(seat, 4, i);
                assertTrue(Math.max(Math.abs(pose.x()), Math.abs(pose.z())) <= 1.23 + .00001);
                tiles.add(tileBounds(pose));
            }
        }
        for (int i = 0; i < tiles.size(); i++)
            for (int j = i + 1; j < tiles.size(); j++)
                assertFalse(
                        tiles.get(i).overlaps(tiles.get(j)),
                        "Public tile collision: " + i + " / " + j);
    }

    private static org.bukkit.util.BoundingBox tileBounds(HandTable.Pose pose) {
        double angle = Math.toRadians(pose.yaw()), w = .094 / 2, d = .146 / 2;
        double x = Math.abs(Math.cos(angle)) * w + Math.abs(Math.sin(angle)) * d;
        double z = Math.abs(Math.sin(angle)) * w + Math.abs(Math.cos(angle)) * d;
        return new org.bukkit.util.BoundingBox(
                pose.x() - x, 0, pose.z() - z, pose.x() + x, .02, pose.z() + z);
    }

    @Test
    @SuppressWarnings("unchecked")
    void realRegionalClosedKansStayHiddenAndAreNotMistakenForFlowers() throws Exception {
        Fixture fixture = new Fixture("mahjong");
        var publicRows =
                HandTable.class.getDeclaredMethod(
                        "mahjongPublicRows",
                        List.class,
                        dev.tabletop3d.rules.MahjongGame.class,
                        int.class);
        publicRows.setAccessible(true);
        for (String profile : List.of("guangdong", "sichuan", "taiwan")) {
            var game = new dev.tabletop3d.rules.MahjongGame(4, 0, Map.of("profile", profile));
            var meldField = game.getClass().getDeclaredField("melds");
            meldField.setAccessible(true);
            var melds = (List<List<dev.tabletop3d.rules.mahjong.Meld>>) meldField.get(game);
            var tiles =
                    java.util.stream.IntStream.range(0, 4)
                            .mapToObj(
                                    i ->
                                            new dev.tabletop3d.rules.mahjong.Tiles.Tile(
                                                    "closed-" + i, 3, false))
                            .toList();
            melds.get(0)
                    .add(
                            new dev.tabletop3d.rules.mahjong.Meld(
                                    dev.tabletop3d.rules.mahjong.Meld.Kind.QUAD, tiles, false, 0));
            var wanted = new ArrayList<>();
            publicRows.invoke(fixture.table, wanted, game, 0);
            assertEquals(4, wanted.size(), profile + " must render exactly four public tiles");
            for (Object spec : wanted) {
                var face = spec.getClass().getDeclaredMethod("face");
                face.setAccessible(true);
                assertEquals(
                        "back", face.invoke(spec), profile + " must keep closed-kan faces private");
            }
            var flowerField = game.getClass().getDeclaredField("flowers");
            flowerField.setAccessible(true);
            ((List<List<dev.tabletop3d.rules.mahjong.Tiles.Tile>>) flowerField.get(game))
                    .get(0)
                    .add(new dev.tabletop3d.rules.mahjong.Tiles.Tile("flower", 34, false));
            wanted.clear();
            publicRows.invoke(fixture.table, wanted, game, 0);
            assertEquals(5, wanted.size(), profile + " flower is separate from the concealed meld");
        }
    }

    static final class Fixture {
        final Tabletop3D plugin = mock(Tabletop3D.class);
        final World world = mock(World.class);
        final Player owner = mock(Player.class), spectator = mock(Player.class);
        final HandGame game = mock(HandGame.class);
        final List<Entity> entities = new ArrayList<>();
        final Map<Entity, Location> positions = new HashMap<>();
        final Room room;
        final Location origin = new Location(world, 0, 81.03125, 0);
        final HandTable table;

        Fixture(String kind) {
            when(world.spawn(any(Location.class), any(Class.class), any(Consumer.class)))
                    .thenAnswer(
                            inv -> {
                                Entity e = mock((Class<? extends Entity>) inv.getArgument(1));
                                entities.add(e);
                                Set<String> tags = new HashSet<>();
                                when(e.getScoreboardTags()).thenReturn(tags);
                                when(e.addScoreboardTag(anyString()))
                                        .thenAnswer(a -> tags.add(a.getArgument(0)));
                                positions.put(e, ((Location) inv.getArgument(0)).clone());
                                when(e.getPersistentDataContainer())
                                        .thenReturn(mock(PersistentDataContainer.class));
                                when(e.isValid()).thenReturn(true);
                                when(e.getLocation()).thenAnswer(a -> positions.get(e).clone());
                                doAnswer(
                                                a -> {
                                                    positions.put(
                                                            e,
                                                            ((Location) a.getArgument(0)).clone());
                                                    return true;
                                                })
                                        .when(e)
                                        .teleport(any(Location.class));
                                doAnswer(
                                                a -> {
                                                    when(e.isValid()).thenReturn(false);
                                                    return null;
                                                })
                                        .when(e)
                                        .remove();
                                ((Consumer<Entity>) inv.getArgument(2)).accept(e);
                                return e;
                            });
            when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
            when(spectator.getUniqueId()).thenReturn(UUID.randomUUID());
            when(owner.getWorld()).thenReturn(world);
            when(spectator.getWorld()).thenReturn(world);
            when(plugin.allowed(any(Player.class))).thenReturn(true);
            room = new Room(UUID.randomUUID(), kind, 2, 0, 0);
            room.join(owner.getUniqueId(), "Owner");
            room.fillBots();
            room.board = game;
            room.phase = Room.Phase.PLAYING;
            when(game.playerCount()).thenReturn(2);
            when(game.handSize(0)).thenReturn(2);
            when(game.handSize(1)).thenReturn(3);
            when(game.hand(0))
                    .thenReturn(
                            List.of(
                                    new HandGame.Piece("a", kind.equals("mahjong") ? "m1" : "r1"),
                                    new HandGame.Piece("b", "p1")));
            when(game.deckSize()).thenReturn(30);
            table =
                    new HandTable(
                            plugin, room, origin, new NamespacedKey("serverboards", "board-cell"));
        }
    }
}
