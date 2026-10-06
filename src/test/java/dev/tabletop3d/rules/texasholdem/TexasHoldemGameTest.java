package dev.tabletop3d.rules.texasholdem;

import dev.tabletop3d.rules.RuleViolation;
import dev.tabletop3d.rules.cards.PlayingCard;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class TexasHoldemGameTest {
    private List<PlayingCard> cards(String ranks, String suit) {
        return ranks.chars()
                .mapToObj(
                        c ->
                                new PlayingCard(
                                        UUID.randomUUID().toString(),
                                        suit,
                                        c == 'A'
                                                ? 14
                                                : c == 'K'
                                                        ? 13
                                                        : c == 'Q'
                                                                ? 12
                                                                : c == 'J'
                                                                        ? 11
                                                                        : c == 'T' ? 10 : c - '0'))
                .toList();
    }

    @Test
    void comparesEveryHandCategoryKickersAndWheel() {
        var high = new ArrayList<>(cards("AKJ83", "spades"));
        high.set(1, new PlayingCard("x", "hearts", 13));
        assertEquals(0, PokerHands.category(PokerHands.best(high)));
        var wheel = new ArrayList<>(cards("A2345", "spades"));
        wheel.set(0, new PlayingCard("x", "hearts", 14));
        assertEquals(4, PokerHands.category(PokerHands.best(wheel)));
        var six = new ArrayList<>(cards("23456", "spades"));
        six.set(0, new PlayingCard("y", "hearts", 2));
        assertTrue(PokerHands.best(six) > PokerHands.best(wheel));
        assertEquals(8, PokerHands.category(PokerHands.best(cards("TJQKA", "hearts"))));
        for (int category = 1; category <= 7; category++) {
            String ranks =
                    switch (category) {
                        case 1 -> "AAKJ8";
                        case 2 -> "AAKK8";
                        case 3 -> "AAAK8";
                        case 4 -> "23456";
                        case 5 -> "AKJ83";
                        case 6 -> "AAAKK";
                        default -> "AAAAK";
                    };
            var hand = new ArrayList<PlayingCard>();
            for (int i = 0; i < 5; i++)
                hand.add(
                        new PlayingCard(
                                "c" + i,
                                category == 5 ? "spades" : PlayingCard.SUITS.get(i % 4),
                                ranks.charAt(i) == 'A'
                                        ? 14
                                        : ranks.charAt(i) == 'K'
                                                ? 13
                                                : ranks.charAt(i) == 'J'
                                                        ? 11
                                                        : ranks.charAt(i) - '0'));
            assertEquals(category, PokerHands.category(PokerHands.best(hand)));
        }
    }

    @Test
    void sidePotsTiesOddChipsAndUncalledContributionsConserveChips() {
        assertArrayEquals(
                new int[] {150, 100, 150},
                PokerPots.awards(
                        new int[] {50, 100, 250},
                        new boolean[] {false, false, false},
                        new long[] {30, 20, 10},
                        2));
        assertArrayEquals(
                new int[] {5, 6, 0},
                PokerPots.awards(
                        new int[] {4, 4, 3},
                        new boolean[] {false, false, true},
                        new long[] {20, 20, 0},
                        0));
        assertArrayEquals(
                new int[] {0, 300, 0},
                PokerPots.awards(
                        new int[] {100, 100, 100},
                        new boolean[] {true, false, true},
                        new long[] {30, 10, 40},
                        0));
    }

    @Test
    void headsUpBlindsTurnOrderAndReplayRemainPrivate() {
        var game = new TexasHoldemGame(2, 41);
        var replay = new TexasHoldemGame(2, 41);
        int dealer = Integer.parseInt(game.publicInfo().get("dealer"));
        assertEquals(dealer, game.currentPlayer());
        assertEquals("5", game.publicInfo().get("bet." + dealer));
        assertEquals("10", game.publicInfo().get("bet." + (1 - dealer)));
        for (int i = 0; i < 2; i++) {
            assertEquals(2, game.handSize(i));
            assertTrue(game.exposed(i).isEmpty());
            assertFalse(game.publicInfo().toString().contains(game.hand(i).getFirst().id()));
        }
        for (String action : List.of("call", "check")) {
            int seat = game.currentPlayer();
            game.apply(seat, action);
            replay.apply(seat, action);
        }
        assertEquals("flop", game.publicInfo().get("phase"));
        assertEquals(1 - dealer, game.currentPlayer());
        assertEquals(3, game.cells().size());
        assertEquals(game.publicInfo(), replay.publicInfo());
        for (int i = 0; i < 2; i++) assertEquals(game.hand(i), replay.hand(i));
    }

    @Test
    void shortBlindWithNoOutstandingOpponentBetRunsOutWithoutAnotherAction() {
        var headsUp = new TexasHoldemGame(2, 0, new int[] {100, 3});
        assertEquals("showdown", headsUp.publicInfo().get("phase"));
        assertEquals(5, headsUp.cells().size());
        var three = new TexasHoldemGame(3, 0, new int[] {100, 2, 3});
        assertEquals("3", three.publicInfo().get("currentBet"));
        assertEquals(List.of("fold", "call"), three.controls(0));
        three.apply(0, "call");
        assertEquals("showdown", three.publicInfo().get("phase"));
        assertEquals(
                105,
                java.util.stream.IntStream.range(0, 3)
                        .map(i -> Integer.parseInt(three.publicInfo().get("chips." + i)))
                        .sum());
    }

    @Test
    void shortAllInDoesNotReopenButCumulativeFullIncreaseDoes() {
        var game = new TexasHoldemGame(4, 0, new int[] {100, 100, 16, 20});
        // Dealer 0, small blind 1, big blind 2; seat 3 calls first.
        assertEquals(3, game.currentPlayer());
        game.apply(3, "call");
        game.apply(0, "call");
        game.apply(1, "call");
        game.apply(2, "all-in");
        assertFalse(game.controls(3).contains("raise"));
        assertThrows(RuleViolation.class, () -> game.apply(3, "raise:20"));
        assertFalse(game.controls(3).contains("all-in"));
        var cumulative = new TexasHoldemGame(5, 0, new int[] {100, 26, 30, 100, 100});
        cumulative.apply(3, "call");
        cumulative.apply(4, "call");
        cumulative.apply(0, "raise:20");
        cumulative.apply(1, "all-in");
        cumulative.apply(2, "all-in");
        cumulative.apply(3, "call");
        cumulative.apply(4, "call");
        assertTrue(cumulative.controls(0).contains("raise"));
        cumulative.apply(0, "raise:40");
        assertEquals("40", cumulative.publicInfo().get("currentBet"));
    }

    @Test
    void completeMatchesNeverCreateOrLoseChipsAndRejectStaleRaises() {
        for (long seed = 0; seed < 15; seed++) {
            var game = new TexasHoldemGame(6, seed);
            var replay = new TexasHoldemGame(6, seed);
            for (int step = 0; step < 1000 && !game.finished(); step++) {
                var legal = game.legalActions(game.currentPlayer());
                String action = legal.contains("all-in") ? "all-in" : legal.getFirst();
                int seat = game.currentPlayer();
                game.apply(seat, action);
                replay.apply(seat, action);
                var info = game.publicInfo();
                assertEquals(
                        6000,
                        Integer.parseInt(info.get("pot"))
                                + java.util.stream.IntStream.range(0, 6)
                                        .map(i -> Integer.parseInt(info.get("chips." + i)))
                                        .sum());
                assertEquals(info, replay.publicInfo());
            }
            assertTrue(game.finished(), "seed " + seed);
        }
    }
}
