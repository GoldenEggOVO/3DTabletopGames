package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.tabletop3d.rules.*;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class ColorEightInteractionTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void everyCardInA54CardHandCanBeAimedAtAndOnlyThatCardRises() {
        var f = new HandTableTest.Fixture("color-eight");
        List<HandGame.Piece> hand = new ArrayList<>();
        for (int i = 0; i < 54; i++) hand.add(new HandGame.Piece("c" + i, "r1"));
        when(f.game.hand(0)).thenReturn(hand);
        when(f.game.handSize(0)).thenReturn(54);
        int start = f.entities.size();
        f.table.show(f.owner);
        for (int i = 0; i < 54; i++) {
            var pose = HandTable.handPose(0, 2, i, 54, false);
            Location eye = f.origin.clone().add(pose.x() - .065, .13, 2.9);
            assertEquals(
                    "c" + i,
                    f.table.hit(f.owner, eye, new Vector(0, 0, -1)),
                    "Dense hand index " + i);
        }
        List<Entity> cards =
                f.entities.subList(start, f.entities.size()).stream()
                        .filter(
                                e ->
                                        Math.abs(
                                                        e.getLocation().getY()
                                                                - f.origin.getY()
                                                                - .017
                                                                - HandTable.handPose(
                                                                                0, 2, 27, 54, false)
                                                                        .lift())
                                                < .000001)
                        .toList();
        f.table.hover(f.owner, "c27");
        for (int i = 0; i < 5; i++) f.table.tick();
        assertTrue(
                cards.stream()
                        .anyMatch(
                                e ->
                                        Math.abs(
                                                        e.getLocation().getY()
                                                                - f.origin.getY()
                                                                - .017
                                                                - HandTable.handPose(
                                                                                0, 2, 27, 54, false)
                                                                        .lift()
                                                                - .085)
                                                < 1e-6));
        var first = HandTable.handPose(0, 2, 0, 54, false);
        assertEquals(
                f.origin.getY() + .017 + first.lift(),
                f.entities.get(start).getLocation().getY(),
                1e-6);
        f.table.hover(f.owner, null);
        for (int i = 0; i < 5; i++) f.table.tick();
        double baseline = f.origin.getY() + .017 + HandTable.handPose(0, 2, 27, 54, false).lift();
        for (Entity part : cards) assertEquals(baseline, part.getLocation().getY(), 1e-6);
    }

    @Test
    void adjacentCardBodiesHaveDifferentTopAndBottomPlanesWithoutSinkingIntoTheTable() {
        for (int players = 2; players <= 5; players++)
            for (int seat = 0; seat < players; seat++)
                for (int count : new int[] {2, 8, 54, 108}) {
                    var first = HandTable.handPose(seat, players, 0, count, false);
                    assertEquals(0, first.lift(), 1e-9);
                    for (int i = 1; i < count; i++) {
                        var previous = HandTable.handPose(seat, players, i - 1, count, false);
                        var next = HandTable.handPose(seat, players, i, count, false);
                        assertTrue(next.lift() - previous.lift() >= .0006 - 1e-9,
                                "Overlapping bodies need separate horizontal surface planes");
                        assertTrue(next.lift() < .125, "The diagonal lift stays below half a card");
                    }
                }
    }

    @Test
    void denseHandBodiesDoNotShareOverlappingHorizontalFaces() {
        record Surface(int card, double x, double y, double z, double width, double depth) {}
        List<Surface> surfaces = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            var pose = HandTable.handPose(0, 2, i, 54, false);
            for (int tile = 0; tile < 24; tile++) {
                var part = PlayingCardHeads.pose(.168, .25, true, tile, 4, 6);
                double width = .168 / 4, height = .25 / 6;
                double x = pose.x() + part.getTranslation().x;
                double center = pose.lift() + part.getTranslation().y - height / 2;
                for (int side : new int[] {-1, 1})
                    surfaces.add(new Surface(i, x, center + side * height / 2,
                            pose.z(), width, .008));
            }
        }
        for (int i = 0; i < surfaces.size(); i++)
            for (int j = i + 1; j < surfaces.size(); j++) {
                var a = surfaces.get(i);
                var b = surfaces.get(j);
                if (a.card() == b.card() || Math.abs(a.y() - b.y()) > 1e-9) continue;
                double x = Math.min(a.x() + a.width() / 2, b.x() + b.width() / 2)
                        - Math.max(a.x() - a.width() / 2, b.x() - b.width() / 2);
                double z = Math.min(a.z() + a.depth() / 2, b.z() + b.depth() / 2)
                        - Math.max(a.z() - a.depth() / 2, b.z() - b.depth() / 2);
                assertTrue(x <= 1e-9 || z <= 1e-9,
                        "Coplanar horizontal card bodies: " + a + " / " + b);
            }
    }

    @Test
    void adjacentBlueBackAndWhiteFrameHaveATenthPixelOfClearance() throws Exception {
        var f = new TableViewTest.Fixture("color-eight", 2);
        var table = TableViewTest.field(f.view, "handTable");
        var pieces = (Map<?, ?>) TableViewTest.field(table, "publicPieces");
        var previous = (List<Entity>) TableViewTest.field(pieces.get("back:0:0"), "parts");
        var next = (List<Entity>) TableViewTest.field(pieces.get("back:0:1"), "parts");
        Entity white = previous.getFirst(), blue = next.getLast();
        double whiteFace = white.getLocation().getZ() + f.transforms.get(white).getTranslation().z;
        double blueFace = blue.getLocation().getZ() + f.transforms.get(blue).getTranslation().z;
        assertEquals(.168 / 32 * .1, blueFace - whiteFace, 1e-7,
                "A blue panel must not share the previous card's white surface plane");
        f.view.close();
    }

    @Test
    void cardsFormOneParallelDiagonalWithEachLaterCardInFront() {
        for (int players = 2; players <= 5; players++)
            for (int seat = 0; seat < players; seat++)
                for (int count : new int[] {2, 8, 54}) {
                    double angle = 2 * Math.PI * seat / players;
                    Vector right = new Vector(Math.cos(angle), 0, -Math.sin(angle)),
                            outward = new Vector(Math.sin(angle), 0, Math.cos(angle));
                    for (int i = 0; i < count; i++) {
                        var pose = HandTable.handPose(seat, players, i, count, false);
                        assertEquals(-Math.toDegrees(angle), pose.yaw(), .0001);
                        assertTrue(pose.lift() >= 0, "Cards cannot sink into the table");
                        if (i > 0) {
                            var previous = HandTable.handPose(seat, players, i - 1, count, false);
                            Vector delta =
                                    new Vector(pose.x() - previous.x(), 0, pose.z() - previous.z());
                            assertTrue(delta.dot(right) > 0);
                            assertEquals(.003025, delta.dot(outward), .00001);
                        }
                    }
                }
    }

    @Test
    void unplayableCardsDimWithoutReplacingTheirColoredModelsAndRecover() throws Exception {
        var f = new HandTableTest.Fixture("color-eight");
        when(f.game.legalActions(0)).thenReturn(List.of("play:a"));
        f.table.show(f.owner);
        Object own =
                ((Map<?, ?>) TableViewTest.field(f.table, "privateViews"))
                        .get(f.owner.getUniqueId());
        Map<?, ?> hand = (Map<?, ?>) TableViewTest.field(own, "pieces");
        for (String id : List.of("a", "b"))
            for (Entity part : (List<Entity>) TableViewTest.field(hand.get(id), "parts")) {
                verify((org.bukkit.entity.ItemDisplay) part, times(1)).setItemStack(any());
                verify((org.bukkit.entity.Display) part, times(id.equals("a") ? 0 : 1))
                        .setBrightness(new org.bukkit.entity.Display.Brightness(7, 7));
            }
        assertNull(f.table.cardAction(f.owner, "b"));
        when(f.game.currentPlayer()).thenReturn(1);
        f.room.revision++;
        f.table.show(f.owner);
        for (Entity part : (List<Entity>) TableViewTest.field(hand.get("b"), "parts")) {
            verify((org.bukkit.entity.ItemDisplay) part, times(1)).setItemStack(any());
            verify((org.bukkit.entity.Display) part, times(2))
                    .setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        }
        f.table.close();
    }

    @Test
    void denseHandsRemainAimableAtEverySeatOfTheFivePlayerTable() {
        for (int seat = 0; seat < 5; seat++) {
            var f = new HandTableTest.Fixture("color-eight");
            when(f.game.playerCount()).thenReturn(5);
            f.room.seats.clear();
            for (int i = 0; i < 5; i++)
                f.room.seats.add(
                        new Room.Seat(
                                i == seat ? f.owner.getUniqueId() : UUID.randomUUID(),
                                "Seat " + i,
                                i != seat));
            List<HandGame.Piece> hand = new ArrayList<>();
            for (int i = 0; i < 54; i++) hand.add(new HandGame.Piece("c" + i, "r1"));
            when(f.game.hand(seat)).thenReturn(hand);
            when(f.game.handSize(seat)).thenReturn(54);
            f.table.show(f.owner);
            double angle = 2 * Math.PI * seat / 5, sin = Math.sin(angle), cos = Math.cos(angle);
            for (int i = 0; i < 54; i++) {
                var pose = HandTable.handPose(seat, 5, i, 54, false);
                Location eye =
                        f.origin
                                .clone()
                                .add(
                                        pose.x() - .075 * cos + 2 * sin,
                                        .13,
                                        pose.z() + .075 * sin + 2 * cos);
                assertEquals(
                        "c" + i,
                        f.table.hit(f.owner, eye, new Vector(-sin, 0, -cos)),
                        "Seat " + seat + " card " + i);
            }
            f.table.close();
        }
    }

    @Test
    void wildSelectionHasFourOwnerOnlyButtonsAndPlaysTheChosenColoredEight() throws Exception {
        var f = new HandTableTest.Fixture("color-eight");
        var game = new ColorEightGame(2, 10);
        TabletopTest.set(
                game,
                "hands",
                new ArrayList<>(
                        List.of(
                                new ArrayList<>(List.of(48, 24, 25)),
                                new ArrayList<>(List.of(12, 13)))));
        f.room.board = game;
        f.room.revision++;
        f.table.sync();
        f.table.show(f.owner);
        assertEquals("choose:48", f.table.cardAction(f.owner, "48"));
        game.apply(0, "choose:48");
        f.room.revision++;
        int start = f.entities.size();
        f.table.show(f.owner);
        var pose = HandTable.handPose(0, 2, 0, 3, false);
        for (int i = 0; i < 4; i++) {
            double tangent = (i - 1.5) * .10, angle = -Math.toRadians(pose.yaw());
            Location eye =
                    f.origin
                            .clone()
                            .add(
                                    pose.x() + tangent * Math.cos(angle),
                                    .017 + pose.lift() + HandTable.CARD_LIFT + .32,
                                    pose.z() - tangent * Math.sin(angle) + .6);
            assertEquals(
                    "card:play:48:" + "rbyp".charAt(i),
                    f.table.callHit(f.owner, eye, new Vector(0, 0, -1)));
        }
        List<Entity> buttons = List.copyOf(f.entities.subList(start, f.entities.size()));
        assertFalse(buttons.isEmpty());
        assertTrue(
                buttons.stream().noneMatch(org.bukkit.entity.TextDisplay.class::isInstance),
                "Color choices contain no digits or text");
        for (Entity button : buttons) {
            verify(f.owner).showEntity(f.plugin, button);
            verify(f.spectator, never()).showEntity(f.plugin, button);
        }
        game.apply(0, "play:48:p");
        f.room.revision++;
        f.table.sync();
        f.table.show(f.owner);
        assertEquals("p8", game.cells().get(1).piece());
        buttons.forEach(b -> assertFalse(b.isValid()));
    }

    @Test
    void humanTurnsStayThirtySecondsOnlineOrOffline() {
        Tabletop3D plugin = mock(Tabletop3D.class, CALLS_REAL_METHODS);
        Room room = new Room(UUID.randomUUID(), "color-eight", 2, 10, 0);
        UUID human = UUID.randomUUID();
        room.join(human, "Human");
        room.fillBots();
        room.board = new ColorEightGame(2, 10);
        assertEquals(30000, plugin.turnWaitMillis(room));
        room.offline.put(human, 0L);
        assertEquals(30000, plugin.turnWaitMillis(room));
    }

    @Test
    void raisedRimKeepsItsTopAboveTheLevelCloth() {
        var parts = RoundCardTable.parts();
        double highestWood = parts.stream()
                .filter(p -> p.material() == org.bukkit.Material.STRIPPED_DARK_OAK_WOOD)
                .mapToDouble(p -> p.y() + p.h()).max().orElseThrow();
        double lowestCloth = parts.stream()
                .filter(p -> p.material() == org.bukkit.Material.GREEN_TERRACOTTA)
                .mapToDouble(p -> p.y() + p.h()).min().orElseThrow();
        assertTrue(highestWood - lowestCloth >= .015,
                "The rim must cover the stepped felt boundary");
        for (var part : parts) {
            if (part.material() == org.bukkit.Material.GREEN_TERRACOTTA)
                assertEquals(lowestCloth, part.y() + part.h(), 1e-9);
        }
    }

    @Test
    void circularRimBoundsItsStairStepInset() throws Exception {
        var rim =
                RoundCardTable.parts().stream()
                        .filter(p -> p.material() == org.bukkit.Material.STRIPPED_DARK_OAK_WOOD)
                        .toList();
        for (int degrees = 0; degrees < 360; degrees++) {
            double angle = Math.toRadians(degrees),
                    x = 1.445 * Math.cos(angle),
                    z = 1.445 * Math.sin(angle);
            boolean covered = false;
            for (var p : rim) {
                if (RoundCardTableTest.contains(p, x, z))
                    covered = true;
            }
            assertTrue(covered, "Rim edge angle " + degrees);
        }
    }

    @Test
    void circularFurnitureFitsTheRadiusAndLeavesTheSquareCornersEmpty() {
        for (var part : RoundCardTable.parts()) {
            assertTrue(part.w() > 0 && part.h() > 0 && part.d() > 0);
            double a = Math.toRadians(part.yaw());
            for (double x : new double[]{-part.w() / 2, part.w() / 2})
                for (double z : new double[]{-part.d() / 2, part.d() / 2})
                    assertTrue(Math.hypot(part.x() + x * Math.cos(a) - z * Math.sin(a),
                            part.z() + x * Math.sin(a) + z * Math.cos(a)) <= RoundCardTable.RADIUS + 1e-7);
            assertFalse(RoundCardTableTest.contains(part, 1.4, 1.4));
        }
        assertTrue(Tabletop3D.capacityValid("color-eight", 5));
        assertFalse(Tabletop3D.capacityValid("color-eight", 6));
    }
}
