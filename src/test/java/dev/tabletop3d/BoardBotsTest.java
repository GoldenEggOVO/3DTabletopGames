package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;

import dev.tabletop3d.rules.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class BoardBotsTest {
    @Test
    void winningActionsComeBeforeOtherChoicesWithoutReadingHands() {
        for (String win : List.of("ron", "tsumo")) {
            PrivateGame game = new PrivateGame(List.of(win, "pass"), "m1");
            for (int seed = 0; seed < 20; seed++)
                assertEquals(win, BoardBots.choose(game, 2, new Random(seed)));
            assertEquals(0, game.reads);
        }
    }

    @Test
    void exchangeConfirmsWhenReadyAndOtherwiseNeverRemovesSelectedTiles() {
        PrivateGame ready = new PrivateGame(List.of("exchange-confirm", "exchange-remove:0"), "m1");
        for (int seed = 0; seed < 20; seed++)
            assertEquals("exchange-confirm", BoardBots.choose(ready, 2, new Random(seed)));
        assertEquals(0, ready.reads);
        PrivateGame adding =
                new PrivateGame(
                        List.of("exchange-remove:0", "exchange-add:1", "exchange-add:2"),
                        "m1 m2 m9");
        for (int seed = 0; seed < 20; seed++)
            assertTrue(BoardBots.choose(adding, 2, new Random(seed)).startsWith("exchange-add:"));
    }

    @Test
    void realSichuanPreparationCompletesWithoutExchangeLoops() {
        for (int seed = 0; seed < 6; seed++) {
            MahjongGame game = new MahjongGame(4, seed, Map.of("profile", "sichuan"));
            Random choices = new Random(seed);
            for (int i = 0; i < 20; i++) {
                int seat = game.currentPlayer();
                String action = BoardBots.choose(game, seat, choices);
                assertTrue(game.legalActions(seat).contains(action));
                assertFalse(action.startsWith("exchange-remove:"));
                game.apply(seat, action);
            }
            assertEquals("TURN", game.publicInfo().get("phase"));
        }
    }

    @Test
    void missingSuitUsesOnlyOwnSuitCountsAndLegalChoices() {
        PrivateGame game =
                new PrivateGame(
                        List.of("missing:m", "missing:p", "missing:s"),
                        "m1 m2 m3 p4 s2 s3 s4 s5 z1");
        for (int seed = 0; seed < 20; seed++)
            assertEquals("missing:p", BoardBots.choose(game, 2, new Random(seed)));
        game.actions = List.of("missing:m", "missing:s");
        assertEquals("missing:m", BoardBots.choose(game, 2, new Random(0)));
    }

    @Test
    void legalRiichiComesBeforeOrdinaryDiscardAndUsesItsCandidateTiles() {
        PrivateGame game =
                new PrivateGame(List.of("discard:0", "riichi:1", "riichi:2"), "z1 m1 m5 m6");
        assertEquals("riichi:1", BoardBots.choose(game, 2, new Random(0)));
    }

    @Test
    void isolatedHonorIsDiscardedBeforePairsAndConnectedTiles() {
        PrivateGame game = discards("m2 m3 m4 p5 p5 s7 s8 z1");
        for (int seed = 0; seed < 20; seed++)
            assertEquals("discard:7", BoardBots.choose(game, 2, new Random(seed)));
    }

    @Test
    void isolatedTerminalIsDiscardedBeforeHonorTripletPairAndGapWait() {
        PrivateGame game = discards("z1 z1 z1 p4 p4 m3 m5 s9");
        for (int seed = 0; seed < 20; seed++)
            assertEquals("discard:7", BoardBots.choose(game, 2, new Random(seed)));
    }

    @Test
    void redFiveIsKeptWhenEquivalentNormalFiveCanBeDiscarded() {
        PrivateGame game = new PrivateGame(List.of("discard:1", "discard:0"), "m0 m5");
        assertEquals("discard:1", BoardBots.choose(game, 2, new Random(0)));
    }

    @Test
    void restrictedDiscardCannotChooseAnIllegalIsolatedTile() {
        PrivateGame game = new PrivateGame(List.of("discard:0", "discard:1"), "m2 m3 z1");
        for (int seed = 0; seed < 20; seed++)
            assertTrue(game.actions.contains(BoardBots.choose(game, 2, new Random(seed))));
        game.actions = List.of();
        int reads = game.reads;
        assertNull(BoardBots.choose(game, 2, new Random(0)));
        assertEquals(reads, game.reads);
    }

    @Test
    void lastCardKeepsDeclarationAndPlayableCardPriorityWithoutMahjongParsing() {
        PrivateGame game = new PrivateGame(List.of("declare", "play:0", "draw"), "wild");
        game.kind = "color-eight";
        assertEquals("declare", BoardBots.choose(game, 2, new Random(0)));
        game.actions = List.of("draw", "play:0:r");
        assertEquals("play:0:r", BoardBots.choose(game, 2, new Random(0)));
        assertEquals(0, game.reads);
    }

    private static PrivateGame discards(String faces) {
        PrivateGame game = new PrivateGame(List.of(), faces);
        game.actions = game.pieces.stream().map(p -> "discard:" + p.id()).toList();
        return game;
    }

    private static final class PrivateGame implements HandGame {
        List<String> actions;
        final List<Piece> pieces;
        int reads;
        String kind = "mahjong";

        PrivateGame(List<String> actions, String faces) {
            this.actions = actions;
            List<Piece> list = new ArrayList<>();
            for (String face : faces.split(" "))
                list.add(new Piece(String.valueOf(list.size()), face));
            pieces = List.copyOf(list);
        }

        public String id() {
            return kind;
        }

        public int playerCount() {
            return 4;
        }

        public int currentPlayer() {
            return 2;
        }

        public boolean finished() {
            return false;
        }

        public String outcome() {
            return "ongoing";
        }

        public List<String> legalActions(int seat) {
            assertEquals(2, seat);
            return actions;
        }

        public List<Piece> hand(int seat) {
            assertEquals(2, seat, "Bot must never inspect another seat's hand");
            reads++;
            return pieces;
        }

        public void apply(int seat, String action) {
            fail("Choosing must not mutate the live game");
        }

        public Map<String, String> publicInfo() {
            throw new AssertionError("No hidden metadata is needed");
        }

        public List<Cell> cells() {
            throw new AssertionError("No other player tiles are needed");
        }

        public List<Piece> discards(int seat) {
            throw new AssertionError("Only own hand is needed");
        }

        public List<Piece> exposed(int seat) {
            throw new AssertionError("Only own hand is needed");
        }

        public int handSize(int seat) {
            throw new AssertionError("Only own hand is needed");
        }

        public int deckSize() {
            return 0;
        }
    }
}
