package dev.tabletop3d.rules.texasholdem;

import dev.tabletop3d.rules.cards.PlayingCard;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PokerHandsDistributionTest {
    @Test
    void allStandardFiveCardHandsMatchKnownCategoryAndDistinctStrengthCounts() {
        var deck = PlayingCard.deck(new Random(0), false);
        int[] counts = new int[9];
        var strengths = new HashSet<Long>();
        for (int a = 0; a < 48; a++)
            for (int b = a + 1; b < 49; b++)
                for (int c = b + 1; c < 50; c++)
                    for (int d = c + 1; d < 51; d++)
                        for (int e = d + 1; e < 52; e++) {
                            long value =
                                    PokerHands.five(
                                            deck.get(a),
                                            deck.get(b),
                                            deck.get(c),
                                            deck.get(d),
                                            deck.get(e));
                            counts[PokerHands.category(value)]++;
                            strengths.add(value);
                        }
        assertArrayEquals(
                new int[] {1302540, 1098240, 123552, 54912, 10200, 5108, 3744, 624, 40}, counts);
        assertEquals(7462, strengths.size());
    }
}
