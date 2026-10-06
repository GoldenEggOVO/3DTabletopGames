package dev.tabletop3d.rules.doudizhu;

import dev.tabletop3d.rules.RuleViolation;
import dev.tabletop3d.rules.cards.PlayingCard;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DoudizhuGameTest {
    private List<Integer> ranks(int... values) {
        return Arrays.stream(values).boxed().toList();
    }

    @Test
    void recognizesShapesAndBombHierarchy() {
        int[][] groups = {
            {3},
            {3, 3},
            {3, 3, 3},
            {3, 3, 3, 4},
            {3, 3, 3, 4, 4},
            {3, 4, 5, 6, 7},
            {3, 3, 4, 4, 5, 5},
            {3, 3, 3, 4, 4, 4},
            {3, 3, 3, 4, 4, 4, 5, 5},
            {3, 3, 3, 4, 4, 4, 5, 5, 6, 6},
            {3, 3, 3, 3, 4, 4},
            {3, 3, 3, 3, 4, 4, 5, 5},
            {3, 3, 3, 3},
            {16, 17}
        };
        DoudizhuCombination.Type[] types = DoudizhuCombination.Type.values();
        for (int i = 0; i < groups.length; i++) {
            int index = i;
            assertTrue(
                    DoudizhuCombination.classify(ranks(groups[i])).stream()
                            .anyMatch(c -> c.type() == types[index]),
                    "shape " + i);
        }
        var bomb = DoudizhuCombination.classify(ranks(3, 3, 3, 3)).getFirst();
        assertTrue(bomb.beats(DoudizhuCombination.classify(ranks(15)).getFirst()));
        assertTrue(DoudizhuCombination.classify(ranks(16, 17)).getFirst().beats(bomb));
        assertTrue(
                DoudizhuCombination.classify(ranks(4, 5, 6, 7, 8))
                        .getFirst()
                        .beats(DoudizhuCombination.classify(ranks(3, 4, 5, 6, 7)).getFirst()));
        assertFalse(
                DoudizhuCombination.classify(ranks(4, 4))
                        .getFirst()
                        .beats(DoudizhuCombination.classify(ranks(3)).getFirst()));
    }

    @Test
    void rejectsInvalidSequencesAndWingsAndKeepsAmbiguousDecompositions() {
        for (int[] group :
                new int[][] {
                    {11, 12, 13, 14, 15},
                    {3, 4, 5, 6},
                    {3, 3, 3, 4, 4, 4, 16, 17},
                    {3, 3, 3, 3, 16, 17},
                    {3, 3, 3, 3, 4, 4, 4, 4}
                })
            assertTrue(
                    DoudizhuCombination.classify(ranks(group)).isEmpty(), Arrays.toString(group));
        assertEquals(
                2,
                DoudizhuCombination.classify(ranks(3, 3, 3, 4, 4, 4, 5, 5, 5, 6, 6, 6)).stream()
                        .filter(c -> c.type() == DoudizhuCombination.Type.AIRPLANE_SINGLE)
                        .count());
    }

    @Test
    void biddingRedealAndTrickResetAreDeterministic() {
        var game = new DoudizhuGame(3, 91);
        var replay = new DoudizhuGame(3, 91);
        assertEquals(
                List.of(17, 17, 17),
                java.util.stream.IntStream.range(0, 3).map(game::handSize).boxed().toList());
        assertEquals(
                54,
                java.util.stream.IntStream.range(0, 3)
                                .flatMap(i -> game.hand(i).stream().mapToInt(p -> 1))
                                .sum()
                        + game.deckSize());
        var old = game.hand(0);
        for (int i = 0; i < 3; i++) {
            int seat = game.currentPlayer();
            game.apply(seat, "bid:0");
            replay.apply(seat, "bid:0");
        }
        assertNotEquals(old, game.hand(0));
        assertEquals(replay.hand(0), game.hand(0));
        int landlord = game.currentPlayer();
        game.apply(landlord, "bid:3");
        replay.apply(landlord, "bid:3");
        assertEquals(20, game.handSize(landlord));
        assertFalse(game.legalActions(landlord).contains("pass"));
        String id = game.hand(landlord).getFirst().id();
        assertThrows(RuleViolation.class, () -> game.apply(landlord, "play:" + id + "," + id));
        game.apply(landlord, "play:" + id);
        replay.apply(landlord, "play:" + id);
        for (int i = 0; i < 2; i++) {
            int seat = game.currentPlayer();
            game.apply(seat, "pass");
            replay.apply(seat, "pass");
        }
        assertEquals(landlord, game.currentPlayer());
        assertFalse(game.legalActions(landlord).contains("pass"));
        assertEquals(game.publicInfo(), replay.publicInfo());
        for (int i = 0; i < 3; i++) assertEquals(game.hand(i), replay.hand(i));
    }

    @Test
    void lowBidsDoNotExposeTheBottomUntilBiddingEnds() {
        var game = new DoudizhuGame(3, 13);
        int bidder = game.currentPlayer();
        game.apply(bidder, "bid:1");
        assertEquals("bidding", game.publicInfo().get("phase"));
        assertEquals("-1", game.publicInfo().get("landlord"));
        assertEquals(3, game.deckSize());
        for (int seat = 0; seat < 3; seat++) assertTrue(game.exposed(seat).isEmpty());
        game.apply(game.currentPlayer(), "bid:0");
        game.apply(game.currentPlayer(), "bid:0");
        assertEquals("playing", game.publicInfo().get("phase"));
        assertEquals(Integer.toString(bidder), game.publicInfo().get("landlord"));
        assertEquals(3, game.exposed(bidder).size());
        assertEquals(0, game.deckSize());
    }

    @Test
    void boundedCandidatesCanCompleteGamesWithConservedTeamScores() {
        assertTimeout(
                Duration.ofSeconds(5),
                () -> {
                    for (long seed = 0; seed < 20; seed++) {
                        var game = new DoudizhuGame(3, seed);
                        game.apply(game.currentPlayer(), "bid:3");
                        for (int step = 0; step < 600 && !game.finished(); step++) {
                            var legal = game.legalActions(game.currentPlayer());
                            assertTrue(legal.size() < 400);
                            game.apply(game.currentPlayer(), legal.getFirst());
                        }
                        assertTrue(game.finished());
                        assertEquals(
                                0,
                                java.util.stream.IntStream.range(0, 3)
                                        .map(
                                                i ->
                                                        Integer.parseInt(
                                                                game.publicInfo()
                                                                        .get("score." + i)))
                                        .sum());
                    }
                });
    }

    @Test
    void ambiguousLeadingGroupsKeepEveryValidInterpretation() throws Exception {
        var game = new DoudizhuGame(3, 0);
        int landlord = game.currentPlayer();
        game.apply(landlord, "bid:3");
        var field = DoudizhuGame.class.getDeclaredField("hands");
        field.setAccessible(true);
        var hands = (List<List<PlayingCard>>) field.get(game);
        int opponent = (landlord + 1) % 3;
        hands.get(landlord).clear();
        hands.get(opponent).clear();
        var copies = new HashMap<Integer, Integer>();
        for (int rank : new int[] {3, 3, 3, 4, 4, 4, 5, 5, 5, 6, 6, 6, 14})
            hands.get(landlord)
                    .add(
                            new PlayingCard(
                                    UUID.randomUUID().toString(),
                                    PlayingCard.SUITS.get(copies.merge(rank, 1, Integer::sum) - 1),
                                    rank));
        for (int rank : new int[] {7, 7, 7, 8, 8, 8, 9, 9, 9, 3, 4, 5})
            hands.get(opponent)
                    .add(
                            new PlayingCard(
                                    UUID.randomUUID().toString(),
                                    PlayingCard.SUITS.get(copies.merge(rank, 1, Integer::sum) - 1),
                                    rank));
        game.apply(
                landlord,
                "play:"
                        + String.join(
                                ",",
                                hands.get(landlord).subList(0, 12).stream()
                                        .map(PlayingCard::id)
                                        .toList()));
        String response =
                "play:"
                        + String.join(
                                ",", hands.get(opponent).stream().map(PlayingCard::id).toList());
        assertDoesNotThrow(() -> game.apply(opponent, response));
        assertEquals("winners:" + opponent + "," + (landlord + 2) % 3, game.outcome());
    }
}
