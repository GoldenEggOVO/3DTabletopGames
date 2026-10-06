package dev.tabletop3d.menu;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.room.Room;
import dev.tabletop3d.rules.GameFactory;
import dev.tabletop3d.rules.coloreight.ColorEightGame;
import dev.tabletop3d.text.RoomText;
import dev.tabletop3d.ui.MessageText;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuExperienceTest {
    @Test
    void setupHighlightsCreationAndKeepsDetailedRuleChangesWhenReturning() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.setup(f.player, "ludo");
        assertEquals("start", f.buttons.getFirst().id());
        assertFalse(ids(f).contains("rule-blocking"));
        click(f, "details");
        click(f, "rule-blocking");
        click(f, "back");
        assertTrue(ids(f).containsAll(List.of("start", "mode", "capacity", "details")));
        click(f, "start");
        verify(f.plugin).create(f.player, "ludo", 4, Map.of("blocking", "on"));
    }

    @Test
    void mahjongProfileStaysOnBasicSetupWhileAllRegionalRulesRemainReachable() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.setup(f.player, "mahjong");
        click(f, "rule-profile");
        click(f, "rule-profile");
        assertFalse(ids(f).contains("rule-base-points"));
        click(f, "details");
        Set<String> seen = new HashSet<>();
        do {
            seen.addAll(ids(f));
            if (!ids(f).contains("next")) break;
            click(f, "next");
        } while (true);
        assertTrue(seen.containsAll(List.of("rule-base-points", "rule-refund-kong-on-exhaustion")));
        click(f, "back");
        click(f, "start");
        verify(f.plugin).create(f.player, "mahjong", 4, Map.of("profile", "sichuan"));
    }

    @Test
    void playingRoomShowsLeaveDirectlyAndOmitsTheOptionsSubmenu() throws Exception {
        var f = new MenuFlowTest.Fixture();
        Room r = handRoom(f);
        f.menus.room(f.player, r);
        assertEquals(List.of("play", "leave", "back", "close"), ids(f));
        assertFalse(
                MessageText.plain(f.description)
                        .contains(MessageText.plain(RoomText.options(r.kind, r.options))));
        click(f, "leave");
        assertTrue(f.buttons.stream().anyMatch(b -> b.label().equals("Confirm Leave")));
    }

    @Test
    void roomAndBrowserBackReturnToAnExistingRoomOrCatalog() throws Exception {
        var f = new MenuFlowTest.Fixture();
        Room r = f.addRoom(0);
        r.join(f.player.getUniqueId(), "Owner");
        f.menus.room(f.player, r);
        click(f, "back");
        assertTrue(ids(f).contains("resume"));
        assertFalse(ids(f).contains("start"));
        f.menus.games(f.player, "chess");
        click(f, "back");
        assertTrue(ids(f).contains("ready"));
        f.plugin.rooms.clear();
        f.menus.games(f.player, "chess");
        click(f, "back");
        assertTrue(ids(f).contains("chess"));
        assertFalse(ids(f).contains("start"));
    }

    @Test
    void lobbyHasReadinessStartAndRoomOptionsAsItsPrimaryActions() throws Exception {
        var f = new MenuFlowTest.Fixture();
        Room r = f.addRoom(0);
        r.join(f.player.getUniqueId(), "Owner");
        f.menus.room(f.player, r);
        assertEquals(List.of("ready", "bots", "leave", "back", "close"), ids(f));
        assertFalse(
                MessageText.plain(f.description)
                        .contains(MessageText.plain(RoomText.options(r.kind, r.options))));
        assertFalse(ids(f).contains("options"));
        assertFalse(
                MessageText.plain(f.description)
                        .contains(MessageText.plain(RoomText.options(r.kind, r.options))));
    }

    @Test
    void everyGameOmitsHelpAndDetailEntriesThroughoutItsMenus() throws Exception {
        var f = new MenuFlowTest.Fixture();
        for (String kind : Tabletop3D.GAMES) {
            f.menus.setup(f.player, kind);
            assertFalse(ids(f).contains("rules"), kind + " setup");
            f.menus.games(f.player, kind);
            assertFalse(ids(f).contains("rules"), kind + " browser");
            Room r = new Room(UUID.randomUUID(), kind, Tabletop3D.defaultCapacity(kind), 0, 0);
            r.join(f.player.getUniqueId(), "Owner");
            f.plugin.rooms.put(r.id, r);
            for (Room.Phase phase : Room.Phase.values()) {
                r.phase = phase;
                f.menus.room(f.player, r);
                assertTrue(
                        f.buttons.stream()
                                .noneMatch(
                                        b ->
                                                Set.of("rules", "public-table").contains(b.id())
                                                        || b.label().equals("Room Details")),
                        kind + " " + phase);
                assertFalse(ids(f).contains("options"));
                assertTrue(
                        Collections.disjoint(ids(f), List.of("rules", "details", "public-table")),
                        kind + " options " + phase);
            }
            f.plugin.rooms.remove(r.id);
        }
    }

    @Test
    void everyPlayingGameReturnsToTheTableWithoutAFullMoveSelector() throws Exception {
        var f = new MenuFlowTest.Fixture();
        for (String kind : Tabletop3D.GAMES) {
            Room r = playingRoom(f, kind);
            f.menus.room(f.player, r);
            assertEquals(
                    Set.of("go", "go9", "go13").contains(kind)
                            ? List.of("play", "pass", "leave", "back", "close")
                            : List.of("play", "leave", "back", "close"),
                    ids(f),
                    kind);
            click(f, "play");
            verify(f.plugin).enterArena(f.player, r);
            f.plugin.rooms.remove(r.id);
        }
    }

    @Test
    void physicalTargetChoiceReturnsToRoomInsteadOfTheFullMoveSelector() throws Exception {
        var f = new MenuFlowTest.Fixture();
        Room r = playingRoom(f, "chess");
        f.menus.boardChoices(f.player, r, List.of("move:a7:a8:q", "move:a7:a8:n"), 0);
        f.buttons.getFirst().action().run();
        verify(f.plugin)
                .action(f.player, r, r.revision, new com.google.gson.JsonPrimitive("move:a7:a8:q"));
        click(f, "back");
        assertEquals(List.of("play", "leave", "back", "close"), ids(f));
    }

    @Test
    void goRoomOffersOnlyLegalStateActionsAndScoringKeepsBothPlayersChoices() throws Exception {
        for (String kind : List.of("go", "go9", "go13")) {
            var f = new MenuFlowTest.Fixture();
            Room r = playingRoom(f, kind);
            r.revision = 12;
            f.menus.room(f.player, r);
            assertEquals(List.of("play", "pass", "leave", "back", "close"), ids(f), kind);
            click(f, "pass");
            verify(f.plugin).action(f.player, r, 12, new com.google.gson.JsonPrimitive("pass"));
            r.board.apply(0, "pass");
            r.revision++;
            f.menus.room(f.player, r);
            assertEquals(
                    List.of("play", "leave", "back", "close"),
                    ids(f),
                    "Only the current player can pass");
            r.board.apply(1, "pass");
            r.revision++;
            f.menus.room(f.player, r);
            assertEquals(
                    List.of("play", "accept", "resume", "leave", "back", "close"), ids(f), kind);
            click(f, "accept");
            verify(f.plugin).action(f.player, r, 14, new com.google.gson.JsonPrimitive("accept"));
            r.board.apply(0, "accept");
            r.revision++;
            f.menus.room(f.player, r);
            assertEquals(
                    List.of("play", "resume", "leave", "back", "close"),
                    ids(f),
                    "A confirmed player can still dispute scoring");
            click(f, "resume");
            verify(f.plugin).action(f.player, r, 15, new com.google.gson.JsonPrimitive("resume"));
            r.busy = true;
            f.menus.room(f.player, r);
            assertEquals(List.of("play", "leave", "back", "close"), ids(f));
            r.busy = false;
        }
    }

    @Test
    void colorEightRoomHasNoDeclarationOrHandMoveMenu() throws Exception {
        var f = new MenuFlowTest.Fixture();
        Room r = playingRoom(f, "color-eight");
        f.menus.room(f.player, r);
        assertEquals(List.of("play", "leave", "back", "close"), ids(f));
        assertFalse(r.board.legalActions(0).contains("declare"));
    }

    private static Room playingRoom(MenuFlowTest.Fixture f, String kind) {
        Room r = new Room(UUID.randomUUID(), kind, Tabletop3D.defaultCapacity(kind), 0, 0);
        r.join(f.player.getUniqueId(), "Owner");
        r.fillBots();
        r.board = GameFactory.create(kind, r.capacity, 0);
        r.phase = Room.Phase.PLAYING;
        f.plugin.rooms.put(r.id, r);
        return r;
    }

    private static Room handRoom(MenuFlowTest.Fixture f) {
        Room r = new Room(UUID.randomUUID(), "color-eight", 2, 0, 0);
        r.board = new ColorEightGame(2, 0);
        if (r.board.currentPlayer() == 1) r.join(UUID.randomUUID(), "Other player");
        r.join(f.player.getUniqueId(), "Owner");
        if (r.seats.size() == 1) r.join(UUID.randomUUID(), "Other player");
        r.phase = Room.Phase.PLAYING;
        f.plugin.rooms.put(r.id, r);
        return r;
    }

    private static List<String> ids(MenuFlowTest.Fixture f) {
        return f.buttons.stream().map(GameMenus.Button::id).toList();
    }

    private static void click(MenuFlowTest.Fixture f, String id) {
        f.buttons.stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow().action().run();
    }
}
