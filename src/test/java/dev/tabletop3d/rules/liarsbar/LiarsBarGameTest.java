package dev.tabletop3d.rules.liarsbar;

import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.RuleViolation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class LiarsBarGameTest {
    @Test
    void onlyTheLastGroupCanBeChallengedAndItsFacesStayPrivate() {
        var game = new LiarsBarGame(4, 33);
        assertEquals(5, game.handSize(0));
        assertFalse(game.legalActions(game.currentPlayer()).contains("challenge"));
        int seat = game.currentPlayer();
        var card = game.hand(seat).getFirst();
        game.apply(seat, "play:" + card.id());
        assertEquals("back", game.discards(seat).getFirst().face());
        assertFalse(game.discards(seat).getFirst().id().contains(card.id()));
        assertFalse(game.publicInfo().toString().contains(card.id()));
        int challenger = game.currentPlayer();
        game.apply(challenger, "challenge");
        boolean truthful =
                card.face().equals(game.publicInfo().get("challengedRank"))
                        || card.face().startsWith("joker");
        int loser = truthful ? challenger : seat;
        assertEquals("1", game.publicInfo().get("attempts." + loser));
        assertEquals(List.of(card), game.exposed(seat));
        for (int i = 0; i < 4; i++)
            if (game.publicInfo().get("alive." + i).equals("true"))
                assertEquals(5, game.handSize(i));
    }

    @Test
    void duplicateAndOversizedSelectionsDoNotChangeState() {
        var game = new LiarsBarGame(2, 5);
        int seat = game.currentPlayer();
        var cards = game.hand(seat);
        var before = game.publicInfo();
        assertThrows(
                RuleViolation.class,
                () ->
                        game.apply(
                                seat,
                                "play:" + cards.getFirst().id() + "," + cards.getFirst().id()));
        assertThrows(
                RuleViolation.class,
                () ->
                        game.selectionAction(
                                seat,
                                cards.subList(0, 4).stream().map(HandGame.Piece::id).toList()));
        assertEquals(before, game.publicInfo());
        assertEquals(cards, game.hand(seat));
    }

    @Test
    void forcedChallengesAndPersistentChambersFinishASeededMatch() {
        for (long seed = 0; seed < 30; seed++) {
            var game = new LiarsBarGame(4, seed);
            var replay = new LiarsBarGame(4, seed);
            for (int step = 0; step < 300 && !game.finished(); step++) {
                int seat = game.currentPlayer();
                var legal = game.legalActions(seat);
                String action = legal.getFirst();
                if (step % 3 == 0 && legal.contains("challenge")) action = "challenge";
                game.apply(seat, action);
                replay.apply(seat, action);
                assertEquals(game.publicInfo(), replay.publicInfo());
                for (int i = 0; i < 4; i++) assertEquals(game.hand(i), replay.hand(i));
                assertTrue(
                        java.util.stream.IntStream.range(0, 4)
                                .allMatch(
                                        i ->
                                                Integer.parseInt(
                                                                game.publicInfo()
                                                                        .get("attempts." + i))
                                                        <= 6));
            }
            assertTrue(game.finished(), "seed " + seed);
            assertEquals(
                    1,
                    java.util.stream.IntStream.range(0, 4)
                            .filter(i -> game.publicInfo().get("alive." + i).equals("true"))
                            .count());
        }
    }
}
