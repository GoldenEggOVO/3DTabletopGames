package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class PlayingCardTableTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void close() {
        MockBukkit.unmock();
    }

    private void owner(TableViewTest.Fixture f, boolean packed) {
        when(f.player.isOnline()).thenReturn(true);
        when(f.player.getWorld()).thenReturn(f.world);
        when(f.player.getLocation()).thenReturn(f.view.origin.clone().add(0, 0, 2));
        when(f.plugin.allowed(f.player)).thenReturn(true);
        when(f.world.getPlayers()).thenReturn(List.of(f.player));
        if (packed) {
            f.plugin.pack = mock(TabletopPack.class);
            when(f.plugin.pack.packed(f.player)).thenReturn(true);
            when(f.plugin.pack.item(anyString())).thenReturn(new ItemStack(Material.PAPER));
        }
        f.view.tick();
    }

    @Test
    void multiCardSelectionIsPrivateAndCannotSurviveRevisionChanges() {
        var f = new TableViewTest.Fixture("doudizhu", 3);
        owner(f, false);
        while (f.room.board.currentPlayer() != 0) f.move("bid:0");
        f.move("bid:3");
        var card = ((dev.tabletop3d.rules.HandGame) f.room.board).hand(0).getFirst();
        int before = f.entities.size();
        assertNull(f.view.cardHandAction(f.player, card.id()));
        assertEquals("play:" + card.id(), f.view.playingAction(f.player, "play"));
        assertEquals(before, f.entities.size(), "Selection reuses the same private card displays");
        f.move("play:" + card.id());
        assertNull(f.view.playingAction(f.player, "play"));
    }

    @Test
    void packedHandsUseOpaquePublicBackAliasesAndOwnerOnlyFaces() {
        var f = new TableViewTest.Fixture("liars-bar", 4);
        owner(f, true);
        var ownerCards = ((dev.tabletop3d.rules.HandGame) f.room.board).hand(0);
        for (var piece : ownerCards) {
            String model = "playing_" + piece.face();
            verify(f.plugin.pack, atLeastOnce()).item(model);
        }
        Object table;
        try {
            table = TableViewTest.field(f.view, "playingTable");
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        var entities = List.copyOf((List<Entity>) get(table, "publicEntities"));
        var privateViews = (Map<?, ?>) get(table, "privateViews");
        assertEquals(1, privateViews.size());
        for (Entity entity : entities)
            if (entity instanceof ItemDisplay) verify(entity).setVisibleByDefault(false);
        f.view.close();
        for (Entity entity : entities) verify(entity).remove();
    }

    private Object get(Object object, String name) {
        try {
            return TableViewTest.field(object, name);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private PlayingCardTable table(TableViewTest.Fixture fixture) {
        return (PlayingCardTable) get(fixture.view, "playingTable");
    }

    private Map<String, Object> ownCards(TableViewTest.Fixture fixture) {
        var views = (Map<?, ?>) get(table(fixture), "privateViews");
        return (Map<String, Object>) get(views.get(fixture.player.getUniqueId()), "cards");
    }

    @Test
    void nativePrivateCardsKeepTheEntityBudgetAndNeverPublishFacesToOtherPlayers() {
        var f = new TableViewTest.Fixture("liars-bar", 4);
        owner(f, false);
        var spectator = mock(Player.class);
        when(spectator.isOnline()).thenReturn(true);
        when(spectator.getWorld()).thenReturn(f.world);
        when(spectator.getLocation()).thenReturn(f.view.origin.clone().add(0, 0, 2));
        when(f.plugin.allowed(spectator)).thenReturn(true);
        when(f.world.getPlayers()).thenReturn(List.of(f.player, spectator));
        f.view.tick();
        for (Object card : ownCards(f).values()) {
            var parts = (List<Entity>) get(card, "parts");
            assertEquals(24, parts.size(), "Every private face, including a Joker, uses 24 heads");
            for (Entity part : parts) {
                verify(part).setVisibleByDefault(false);
                verify(f.player).showEntity(f.plugin, part);
                verify(spectator, never()).showEntity(f.plugin, part);
            }
        }
        f.view.close();
    }

    @Test
    void publicNativeHandBacksUseTwoBlocksWithAWhiteBorderAndSeparateBlueSurface() {
        var f = new TableViewTest.Fixture("doudizhu", 3);
        owner(f, false);
        var cards = (Map<String, Object>) get(table(f), "publicCards");
        int backs = 0;
        for (var entry : cards.entrySet()) {
            if (!entry.getKey().startsWith("native:back:")) continue;
            var parts = (List<Entity>) get(entry.getValue(), "parts");
            assertEquals(2, parts.size(), entry.getKey());
            var white = assertInstanceOf(BlockDisplay.class, parts.getFirst());
            var blue = assertInstanceOf(BlockDisplay.class, parts.getLast());
            var block = org.mockito.ArgumentCaptor.forClass(org.bukkit.block.data.BlockData.class);
            verify(white).setBlock(block.capture());
            assertEquals(Material.WHITE_CONCRETE, block.getValue().getMaterial());
            verify(blue).setBlock(block.capture());
            assertEquals(Material.BLUE_CONCRETE, block.getValue().getMaterial());
            var body = f.transforms.get(white);
            var inset = f.transforms.get(blue);
            assertEquals(.168, body.getScale().x, 1e-6);
            assertEquals(.224, body.getScale().y, 1e-6);
            assertTrue(inset.getScale().x < body.getScale().x);
            assertTrue(inset.getScale().y < body.getScale().y);
            assertEquals(.006, blue.getLocation().getY() - white.getLocation().getY(), 1e-6);
            double yaw = Math.toRadians(white.getLocation().getYaw());
            assertEquals(Math.sin(yaw) * .0055,
                    blue.getLocation().getX() - white.getLocation().getX(), 1e-6);
            assertEquals(-Math.cos(yaw) * .0055,
                    blue.getLocation().getZ() - white.getLocation().getZ(), 1e-6);
            assertEquals(.0005, .0055 - inset.getScale().z / 2 - body.getScale().z / 2, 1e-6);
            if (entry.getKey().startsWith("native:back:0:")) {
                for (var part : parts) verify(f.player, atLeastOnce()).hideEntity(f.plugin, part);
            }
            backs++;
        }
        assertEquals(51, backs);
        f.view.close();
    }

    @Test
    void concealedLiarCardsLieFlatWithTheBlueBackAboveTheWhiteBody() {
        var f = new TableViewTest.Fixture("liars-bar", 4);
        owner(f, false);
        var game = (dev.tabletop3d.rules.HandGame) f.room.board;
        f.move("play:" + game.hand(f.room.board.currentPlayer()).getFirst().id());
        var cards = (Map<String, Object>) get(table(f), "publicCards");
        var hidden = cards.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("native:play:"))
                .findFirst().orElseThrow().getValue();
        var parts = (List<Entity>) get(hidden, "parts");
        assertEquals(2, parts.size());
        var white = assertInstanceOf(BlockDisplay.class, parts.getFirst());
        var blue = assertInstanceOf(BlockDisplay.class, parts.getLast());
        var body = f.transforms.get(white);
        var inset = f.transforms.get(blue);
        assertEquals(.008, body.getScale().y, 1e-6);
        assertEquals(.224, body.getScale().z, 1e-6);
        assertEquals(.0085, blue.getLocation().getY() - white.getLocation().getY(), 1e-6);
        assertEquals(.0005, .0085 - body.getScale().y, 1e-6);
        assertTrue(inset.getScale().x < body.getScale().x);
        assertTrue(inset.getScale().z < body.getScale().z);
        f.view.close();
    }

    @Test
    void packedPublicHandBacksRemainOneItemDisplayEach() {
        var f = new TableViewTest.Fixture("doudizhu", 3);
        owner(f, true);
        var cards = (Map<String, Object>) get(table(f), "publicCards");
        for (var entry : cards.entrySet()) {
            if (!entry.getKey().startsWith("packed:back:")) continue;
            var parts = (List<Entity>) get(entry.getValue(), "parts");
            assertEquals(1, parts.size());
            assertInstanceOf(ItemDisplay.class, parts.getFirst());
        }
        assertTrue(cards.keySet().stream().noneMatch(key -> key.startsWith("native:back:")));
        f.view.close();
    }

    private Location body(Object card) {
        return ((List<Entity>) get(card, "parts")).getFirst().getLocation();
    }

    @Test
    void aStationaryCursorKeepsItsCardAtTheBottomAndOverlapBoundary() {
        for (String kind : List.of("doudizhu", "liars-bar", "texas-holdem")) {
            var f = new TableViewTest.Fixture(kind, kind.equals("doudizhu") ? 3 : 4);
            owner(f, true);
            var cards = new ArrayList<>(ownCards(f).values());
            Location first = body(cards.getFirst()), second = body(cards.get(1));
            for (double height : List.of(.02, .12)) {
                Location eye = first.clone().add(0, height, 1.5);
                if (height == .12) eye.setX((first.getX() + second.getX()) / 2);
                var direction = new org.bukkit.util.Vector(0, 0, -1);
                String hit = table(f).handHit(f.player, eye, direction);
                assertNotNull(hit);
                for (int tick = 0; tick < 10; tick++) {
                    table(f).hover(f.player, hit);
                    assertEquals(hit, table(f).handHit(f.player, eye, direction), kind);
                }
                table(f).hover(f.player, null);
            }
            f.view.close();
        }
    }

    @Test
    void selectingAHoveredCardDoesNotRaiseItTwice() {
        var f = new TableViewTest.Fixture("doudizhu", 3);
        owner(f, true);
        while (f.room.board.currentPlayer() != 0) f.move("bid:0");
        f.move("bid:3");
        var entry = ownCards(f).entrySet().iterator().next();
        table(f).hover(f.player, entry.getKey());
        double hovered = body(entry.getValue()).getY();
        table(f).cardAction(f.player, entry.getKey());
        assertEquals(hovered, body(entry.getValue()).getY(), 1e-8);
        table(f).hover(f.player, null);
        assertEquals(hovered, body(entry.getValue()).getY(), 1e-8);
        f.view.close();
    }

    @Test
    void overlappingCardsHaveSeparatePhysicalLayersAndControlsStayInFront() {
        for (String kind : List.of("doudizhu", "liars-bar", "texas-holdem")) {
            var f = new TableViewTest.Fixture(kind, kind.equals("doudizhu") ? 3 : 4);
            owner(f, false);
            var cards = new ArrayList<>(ownCards(f).values());
            for (int i = 1; i < cards.size(); i++)
                assertTrue(
                        Math.abs(body(cards.get(i)).getZ() - body(cards.get(i - 1)).getZ() - .003025) < 1e-7,
                        "Adjacent upright cards use the compact Color Eight spacing");
            var views = (Map<?, ?>) get(table(f), "privateViews");
            var buttons = (Map<?, List<Entity>>) get(views.get(f.player.getUniqueId()), "buttons");
            for (var parts : buttons.values()) {
                assertTrue(
                        parts.getFirst().getLocation().getZ() > body(cards.getLast()).getZ() + .085,
                        "Controls must lie between the player and the hand");
                assertTrue(parts.getFirst().getLocation().getY() - f.view.origin.getY() >= .037,
                        "Controls must sit above the raised native rim");
            }
            if (kind.equals("doudizhu")) {
                while (f.room.board.currentPlayer() != 0) f.move("bid:0");
                f.move("bid:3");
                var hand = ((dev.tabletop3d.rules.HandGame) f.room.board).hand(0);
                var pair =
                        hand.stream()
                                .collect(
                                        java.util.stream.Collectors.groupingBy(
                                                p -> p.face().substring(p.face().indexOf('_') + 1)))
                                .values()
                                .stream()
                                .filter(group -> group.size() >= 2)
                                .findFirst()
                                .orElseThrow();
                f.move("play:" + pair.get(0).id() + "," + pair.get(1).id());
                var publicCards = (Map<String, Object>) get(table(f), "publicCards");
                Location a = body(publicCards.get("native:play:0:0"));
                Location b = body(publicCards.get("native:play:0:1"));
                assertEquals(.003025, b.getY() - a.getY(), 1e-6,
                        "Flat faces retain the same compact normal offset as upright hands");
                var bottom =
                        publicCards.entrySet().stream()
                                .filter(e -> e.getKey().startsWith("native:bottom:"))
                                .toList();
                assertEquals(3, bottom.size());
                for (var entry : bottom) assertEquals(0, body(entry.getValue()).getZ(), 1e-8);
                for (int i = 1; i < bottom.size(); i++) {
                    Location previous = body(bottom.get(i - 1).getValue());
                    Location next = body(bottom.get(i).getValue());
                    assertEquals(.15, next.getX() - previous.getX(), 1e-6);
                    assertEquals(.003025, next.getY() - previous.getY(), 1e-6);
                }
            }
            f.view.close();
        }
    }

    @Test
    void nativeFacesUseSignedHeadSkinsWithoutGlyphPlanes() {
        var f = new TableViewTest.Fixture("liars-bar", 4);
        owner(f, false);
        Object card = ownCards(f).values().iterator().next();
        var parts = (List<Entity>) get(card, "parts");
        assertEquals(24, parts.size());
        var item = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
        for (Entity part : parts) {
            var head = assertInstanceOf(ItemDisplay.class, part);
            verify(head).setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            verify(head).setItemStack(item.capture());
            assertEquals(Material.PLAYER_HEAD, item.getValue().getType());
            var meta = assertInstanceOf(org.bukkit.inventory.meta.SkullMeta.class, item.getValue().getItemMeta());
            var property = meta.getPlayerProfile().getProperties().stream()
                    .filter(value -> value.getName().equals("textures")).findFirst().orElseThrow();
            assertNotNull(property.getSignature(), "Generated Mojang signatures must be retained");
            assertFalse(property.getValue().isBlank());
        }
        f.view.close();
    }

    @Test
    void pokerShowdownCardsAlsoHaveSeparateFlatLayers() {
        var f = new TableViewTest.Fixture("texas-holdem", 2);
        owner(f, true);
        f.move("all-in");
        f.move("call");
        var cards = (Map<String, Object>) get(table(f), "publicCards");
        Location first = body(cards.get("packed:exposed:0:0"));
        Location second = body(cards.get("packed:exposed:0:1"));
        assertEquals(.003025, second.getY() - first.getY(), 1e-6);
        assertEquals(.003025, second.getZ() - first.getZ(), 1e-6);
        var community = cards.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("packed:community:"))
                .map(entry -> body(entry.getValue())).toList();
        assertEquals(5, community.size());
        for (int i = 1; i < community.size(); i++) {
            assertEquals(.15, community.get(i).getX() - community.get(i - 1).getX(), 1e-6);
            assertEquals(.003025, community.get(i).getY() - community.get(i - 1).getY(), 1e-6);
        }
        f.view.close();
    }

    @Test
    void concealedLiarCardsUseCompactFlatLayersInBothRenderingModes() {
        for (boolean packed : List.of(false, true)) {
            var f = new TableViewTest.Fixture("liars-bar", 4);
            owner(f, packed);
            var game = (dev.tabletop3d.rules.HandGame) f.room.board;
            int seat = f.room.board.currentPlayer();
            var hand = game.hand(seat);
            f.move("play:" + hand.get(0).id() + "," + hand.get(1).id());
            var cards = (Map<String, Object>) get(table(f), "publicCards");
            String prefix = packed ? "packed:" : "native:";
            Location first = body(cards.get(prefix + "play:" + seat + ":0"));
            Location second = body(cards.get(prefix + "play:" + seat + ":1"));
            assertEquals(.003025, second.getY() - first.getY(), 1e-6);
            assertEquals(.10, Math.hypot(second.getX() - first.getX(), second.getZ() - first.getZ()), 1e-6);
            f.view.close();
        }
    }

    @Test
    void nativeFlatHeadsHaveNoCoplanarGlyphOverlays() {
        var f = new TableViewTest.Fixture("liars-bar", 4);
        owner(f, false);
        var cards = (Map<String, Object>) get(table(f), "publicCards");
        var parts = (List<Entity>) get(cards.get("native:declaration"), "parts");
        assertEquals(24, parts.size());
        for (Entity head : parts) assertInstanceOf(ItemDisplay.class, head);
        f.view.close();
    }

    @Test
    void seatLabelsDoNotShowRemainingHandCountsAndStayClearOfHands() {
        var f = new TableViewTest.Fixture("liars-bar", 4);
        owner(f, false);
        var labels = (List<TextDisplay>) get(table(f), "seatLabels");
        var captured =
                org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(labels.getFirst(), atLeastOnce()).text(captured.capture());
        String text = dev.tabletop3d.ui.MessageText.plain(captured.getValue());
        assertFalse(text.contains("cards"), text);
        assertTrue(
                labels.getFirst().getLocation().getZ() < .9,
                "Roulette information must sit closer to the table centre than the hand");
        f.view.close();
    }

    @Test
    void nativeFacesAndRayTargetsFollowEverySeatRotation() {
        for (int capacity : List.of(3, 4, 6)) {
            String kind = capacity == 3 ? "doudizhu" : capacity == 4 ? "liars-bar" : "texas-holdem";
            for (int seat = 0; seat < capacity; seat++) {
                var fixture = new TableViewTest.Fixture(kind, capacity);
                when(fixture.player.getUniqueId()).thenReturn(fixture.room.seats.get(seat).id());
                owner(fixture, false);
                Object table = get(fixture.view, "playingTable");
                var views = (Map<?, ?>) get(table, "privateViews");
                Object view = views.get(fixture.player.getUniqueId());
                var cards = (Map<?, ?>) get(view, "cards");
                Object card = cards.values().iterator().next();
                var parts = (List<Entity>) get(card, "parts");
                Location body = parts.getFirst().getLocation();
                assertEquals(24, parts.size());
                assertTrue(parts.stream().allMatch(ItemDisplay.class::isInstance));
                for (Entity part : parts) assertEquals(body.getYaw(), part.getLocation().getYaw());
                double angle = Math.PI * 2 * seat / capacity;
                var normal = new org.bukkit.util.Vector(Math.sin(angle), 0, Math.cos(angle));
                var eye = body.clone().add(normal.clone().multiply(1.5)).add(0, .125, 0);
                assertEquals(
                        cards.keySet().iterator().next(),
                        ((PlayingCardTable) table)
                                .handHit(fixture.player, eye, normal.multiply(-1)));
                fixture.view.close();
            }
        }
    }

    @Test
    void startingFromLobbyRefreshesHandsAndButtonsWithoutARevisionChange() {
        var fixture = new TableViewTest.Fixture("texas-holdem", 2);
        fixture.room.phase = Room.Phase.LOBBY;
        owner(fixture, false);
        Object table = get(fixture.view, "playingTable");
        var views = (Map<?, ?>) get(table, "privateViews");
        Object view = views.get(fixture.player.getUniqueId());
        assertTrue(((Map<?, ?>) get(view, "buttons")).isEmpty());
        var priorCards = Set.copyOf(((Map<?, ?>) get(view, "cards")).keySet());
        long revision = fixture.room.revision;
        fixture.room.board = dev.tabletop3d.rules.GameFactory.create("texas-holdem", 2, 8);
        fixture.room.phase = Room.Phase.PLAYING;
        fixture.view.tick();
        assertEquals(revision, fixture.room.revision);
        assertEquals("fold", fixture.view.playingAction(fixture.player, "fold"));
        assertNotEquals(priorCards, ((Map<?, ?>) get(view, "cards")).keySet());
        fixture.view.close();
    }
}
