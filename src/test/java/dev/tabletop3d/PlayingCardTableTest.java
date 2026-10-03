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
                Location face = parts.get(1).getLocation();
                double angle = Math.PI * 2 * seat / capacity;
                var normal = new org.bukkit.util.Vector(Math.sin(angle), 0, Math.cos(angle));
                assertEquals(.007, face.toVector().subtract(body.toVector()).dot(normal), 1e-8);
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
