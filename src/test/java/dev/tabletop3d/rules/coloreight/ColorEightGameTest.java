package dev.tabletop3d.rules.coloreight;

import dev.tabletop3d.rules.HandGame;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ColorEightGameTest {
    Object field(ColorEightGame g, String name) throws Exception {
        Field f = ColorEightGame.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(g);
    }

    void set(ColorEightGame g, String name, Object value) throws Exception {
        Field f = ColorEightGame.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(g, value);
    }

    @SuppressWarnings("unchecked")
    List<Integer> cards(ColorEightGame g, String name) throws Exception {
        return (List<Integer>) field(g, name);
    }

    @SuppressWarnings("unchecked")
    List<List<Integer>> hands(ColorEightGame g) throws Exception {
        return (List<List<Integer>>) field(g, "hands");
    }

    ColorEightGame position(int top, int[]... cards) throws Exception {
        ColorEightGame g = new ColorEightGame(cards.length, 98);
        Set<Integer> used = new HashSet<>();
        for (int s = 0; s < cards.length; s++) {
            hands(g).get(s).clear();
            for (int c : cards[s]) {
                assertTrue(used.add(c));
                hands(g).get(s).add(c);
            }
        }
        cards(g, "pile").clear();
        assertTrue(used.add(top));
        cards(g, "pile").add(top);
        List<Integer> deck = cards(g, "deck");
        deck.clear();
        for (int c = 0; c < 54; c++) if (!used.contains(c)) deck.add(c);
        set(g, "current", 0);
        set(g, "activeColor", "rybp".substring(top / 12, top / 12 + 1));
        set(g, "activeRank", top % 12);
        return g;
    }

    void nextDraw(ColorEightGame g, int card) throws Exception {
        assertTrue(cards(g, "deck").remove(Integer.valueOf(card)));
        cards(g, "deck").add(card);
    }

    @Test
    void seededDeckHas54CardsFiveEachAndAnOpeningDiscard() throws Exception {
        Set<String> faces = new HashSet<>();
        for (int players = 2; players <= 5; players++)
            for (int seed = 0; seed < 60; seed++) {
                var g = new ColorEightGame(players, seed);
                assertEquals(0, g.currentPlayer());
                assertEquals(53 - players * 5, g.deckSize());
                assertEquals(1, cards(g, "pile").size());
                assertTrue(cards(g, "pile").getFirst() < 48);
                Set<String> ids = new HashSet<>();
                for (int s = 0; s < players; s++) {
                    assertEquals(5, g.handSize(s));
                    for (var c : g.hand(s)) {
                        assertTrue(ids.add(c.id()));
                        faces.add(c.face());
                    }
                }
                assertEquals(g.hand(0), new ColorEightGame(players, seed).hand(0));
            }
        assertTrue(
                faces.containsAll(List.of("r10", "rDraw1", "rSkip", "rReverse", "wild", "swap")));
        assertThrows(IllegalArgumentException.class, () -> new ColorEightGame(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ColorEightGame(6, 0));
    }

    @Test
    void drawIsOptionalOnceAndAnyLegalCardCanFollow() throws Exception {
        var g = position(0, new int[] {1, 13}, new int[] {24});
        nextDraw(g, 25);
        assertTrue(g.legalActions(0).containsAll(List.of("draw", "pass", "play:1")));
        g.apply(0, "draw");
        assertEquals(0, g.currentPlayer());
        assertFalse(g.legalActions(0).contains("draw"));
        assertTrue(g.legalActions(0).contains("play:1"));
        g.apply(0, "pass");
        assertEquals(3, g.handSize(0));
        assertEquals(1, g.currentPlayer());
    }

    @Test
    void passDrawsOnceAndDrawingWithoutAnyLegalCardPasses() throws Exception {
        var g = position(0, new int[] {13}, new int[] {24});
        nextDraw(g, 14);
        g.apply(0, "draw");
        assertEquals(1, g.currentPlayer());
        g = position(0, new int[] {1}, new int[] {24});
        g.apply(0, "pass");
        assertEquals(2, g.handSize(0));
        assertEquals(1, g.currentPlayer());
    }

    @Test
    void wildSetsColoredEightAndSwapPreservesMatchingState() throws Exception {
        var g = position(0, new int[] {48, 1}, new int[] {52, 13}, new int[] {24});
        g.apply(0, "play:48:b");
        assertEquals("b8", g.cells().get(1).piece());
        g.apply(1, "play:52");
        assertEquals("b", g.publicInfo().get("color"));
        assertEquals(List.of("draw", "pass"), g.legalActions(2));
        assertEquals(List.of("13"), g.hand(2).stream().map(HandGame.Piece::id).toList());
    }

    @Test
    void reverseAndSkipAreEquivalentInTwoPlayersButReverseFlipsFivePlayers() throws Exception {
        var g = position(10, new int[] {23, 1}, new int[] {24});
        assertTrue(g.legalActions(0).contains("play:23"));
        g.apply(0, "play:23");
        assertEquals(0, g.currentPlayer());
        g = position(11, new int[] {22, 1}, new int[] {24});
        assertTrue(g.legalActions(0).contains("play:22"));
        g =
                position(
                        0,
                        new int[] {11, 1},
                        new int[] {12},
                        new int[] {24},
                        new int[] {36},
                        new int[] {48});
        g.apply(0, "play:11");
        assertEquals(4, g.currentPlayer());
        assertEquals("Counterclockwise", g.publicInfo().get("direction"));
    }

    @Test
    void quickDrawHitsEveryOpponentWithoutSkippingOrStacking() throws Exception {
        var g =
                position(
                        0,
                        new int[] {9, 1},
                        new int[] {12},
                        new int[] {24},
                        new int[] {36},
                        new int[] {48});
        g.apply(0, "play:9");
        assertEquals(1, g.currentPlayer());
        for (int s = 1; s < 5; s++) assertEquals(2, g.handSize(s));
        assertTrue(g.legalActions(1).contains("draw"));
    }

    @Test
    void lastSpecialWinsBeforeItsEffectAndNoDeclarationIsRequired() throws Exception {
        for (int card : new int[] {9, 10, 11, 48, 52}) {
            var g = position(0, new int[] {card}, new int[] {12, 13});
            g.apply(0, "play:" + card + (card == 48 ? ":p" : ""));
            assertEquals("winner:0", g.outcome());
            assertEquals(2, g.handSize(1));
            assertEquals(List.of(0), g.placements());
        }
        var g = position(0, new int[] {1, 2}, new int[] {12});
        g.apply(0, "play:1");
        assertEquals(1, g.handSize(0));
        assertFalse(g.legalActions(1).contains("declare"));
    }

    @Test
    void recycleBeforeDrawingAndKeepTop() throws Exception {
        var g = position(0, new int[] {13}, new int[] {24});
        List<Integer> pile = cards(g, "pile"), deck = cards(g, "deck");
        pile.addAll(0, deck);
        deck.clear();
        g.apply(0, "draw");
        assertEquals(2, g.handSize(0));
        assertEquals(List.of(0), pile);
    }

    @Test
    void exhaustedDrawSupplyRequiresAPlayableHandCardAndTimeoutPlaysIt() throws Exception {
        var g = position(0, new int[] {1, 13}, new int[] {24});
        hands(g).get(1).addAll(cards(g, "deck"));
        cards(g, "deck").clear();
        assertEquals(List.of("play:1"), g.legalActions(0));
        assertThrows(IllegalArgumentException.class, () -> g.apply(0, "pass"));
        assertThrows(IllegalArgumentException.class, () -> g.apply(0, "draw"));
        assertEquals("play:1", g.timeoutAction());
        g.apply(0, g.timeoutAction());
        assertEquals(1, g.currentPlayer());
    }

    @Test
    void exhaustedDrawSupplyStillLetsAnUnplayableHandPassWithoutDrawing() throws Exception {
        var g = position(0, new int[] {13}, new int[] {24});
        hands(g).get(1).addAll(cards(g, "deck"));
        cards(g, "deck").clear();
        assertEquals(List.of("pass"), g.legalActions(0));
        assertEquals("pass", g.timeoutAction());
        g.apply(0, "pass");
        assertEquals(1, g.currentPlayer());
        assertEquals(1, g.handSize(0));
    }

    @Test
    void drawingTheLastAvailableCardRequiresPlayingIfTheHandCanPlay() throws Exception {
        var g = position(0, new int[] {1, 13}, new int[] {24});
        List<Integer> deck = cards(g, "deck");
        deck.remove(Integer.valueOf(25));
        hands(g).get(1).addAll(deck);
        deck.clear();
        deck.add(25);
        g.apply(0, "draw");
        assertEquals(List.of("play:1"), g.legalActions(0));
        assertEquals("play:1", g.timeoutAction());
    }

    @Test
    void exhaustedDrawsAndPassesCannotBypassLegalActions() throws Exception {
        var g = position(0, new int[] {1, 13}, new int[] {24});
        hands(g).get(1).addAll(cards(g, "deck"));
        cards(g, "deck").clear();
        assertThrows(IllegalArgumentException.class, () -> g.apply(0, "pass"));
        assertThrows(IllegalArgumentException.class, () -> g.apply(1, "pass"));
        assertThrows(IllegalArgumentException.class, () -> g.apply(0, "draw"));
        assertEquals(0, g.currentPlayer());
        assertFalse(g.legalActions(0).contains("pass"));
        assertEquals(2, g.handSize(0));
    }

    @Test
    void colorSelectionIsPrivateReplayableAndTimeoutUsesHandColor() throws Exception {
        var g = position(0, new int[] {48, 24, 25}, new int[] {12});
        g.apply(0, "choose:48");
        assertEquals("48", g.pendingCard(0));
        assertNull(g.pendingCard(1));
        assertEquals(3, g.handSize(0));
        assertEquals(
                List.of("play:48:r", "play:48:y", "play:48:b", "play:48:p"), g.legalActions(0));
        assertEquals("play:48:b", g.timeoutAction());
        g.apply(0, g.timeoutAction());
        assertEquals("b8", g.cells().get(1).piece());
    }

    @Test
    void repeatedSeededGamesConserveAll54CardsAndFinish() throws Exception {
        for (int players = 2; players <= 5; players++)
            for (int seed = 0; seed < 12; seed++) {
                var g = new ColorEightGame(players, seed);
                int step = 0;
                while (!g.finished() && step++ < 4000) {
                    int seat = g.currentPlayer();
                    List<String> actions = g.legalActions(seat);
                    g.apply(
                            seat,
                            actions.stream()
                                    .filter(a -> a.startsWith("play:"))
                                    .findFirst()
                                    .orElse(actions.contains("draw") ? "draw" : "pass"));
                    Set<Integer> ids = new HashSet<>(cards(g, "deck"));
                    for (int c : cards(g, "pile")) assertTrue(ids.add(c));
                    for (var hand : hands(g)) for (int c : hand) assertTrue(ids.add(c));
                    assertEquals(54, ids.size());
                }
                assertTrue(g.finished(), "seed=" + seed + " players=" + players);
            }
    }
}
