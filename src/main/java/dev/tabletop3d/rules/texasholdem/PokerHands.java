package dev.tabletop3d.rules.texasholdem;

import dev.tabletop3d.rules.cards.PlayingCard;

import java.util.List;

/** Comparable five-card strength encoded as category followed by five base-15 kickers. */
public final class PokerHands {
    private static final long CATEGORY_UNIT = 759375; // 15^5

    private PokerHands() {}

    public static int category(long strength) {
        return (int) (strength / CATEGORY_UNIT);
    }

    public static long best(List<PlayingCard> cards) {
        if (cards.size() < 5 || cards.size() > 7)
            throw new IllegalArgumentException("Poker evaluation requires five to seven cards");
        long best = 0;
        for (int a = 0; a < cards.size() - 4; a++)
            for (int b = a + 1; b < cards.size() - 3; b++)
                for (int c = b + 1; c < cards.size() - 2; c++)
                    for (int d = c + 1; d < cards.size() - 1; d++)
                        for (int e = d + 1; e < cards.size(); e++)
                            best =
                                    Math.max(
                                            best,
                                            five(
                                                    cards.get(a),
                                                    cards.get(b),
                                                    cards.get(c),
                                                    cards.get(d),
                                                    cards.get(e)));
        return best;
    }

    static long five(PlayingCard a, PlayingCard b, PlayingCard c, PlayingCard d, PlayingCard e) {
        int[] counts = new int[15];
        counts[a.rank()]++;
        counts[b.rank()]++;
        counts[c.rank()]++;
        counts[d.rank()]++;
        counts[e.rank()]++;
        boolean flush =
                a.suit().equals(b.suit())
                        && a.suit().equals(c.suit())
                        && a.suit().equals(d.suit())
                        && a.suit().equals(e.suit());
        int straight = 0, run = 0;
        for (int rank = 2; rank <= 14; rank++) {
            run = counts[rank] > 0 ? run + 1 : 0;
            if (run >= 5) straight = rank;
        }
        if (straight == 0
                && counts[14] > 0
                && counts[2] > 0
                && counts[3] > 0
                && counts[4] > 0
                && counts[5] > 0) straight = 5;
        if (flush && straight > 0) return encode(8, straight);
        int four = 0, three = 0, pair = 0, secondPair = 0;
        for (int rank = 14; rank >= 2; rank--)
            switch (counts[rank]) {
                case 4 -> four = rank;
                case 3 -> three = rank;
                case 2 -> {
                    if (pair == 0) pair = rank;
                    else secondPair = rank;
                }
                default -> {}
            }
        if (four > 0) return encode(7, four, kickers(counts, 1)[0]);
        if (three > 0 && pair > 0) return encode(6, three, pair);
        if (flush) return encode(5, kickers(counts, 1));
        if (straight > 0) return encode(4, straight);
        int[] singles = kickers(counts, 1);
        if (three > 0) return encode(3, three, singles[0], singles[1]);
        if (secondPair > 0) return encode(2, pair, secondPair, singles[0]);
        if (pair > 0) return encode(1, pair, singles[0], singles[1], singles[2]);
        return encode(0, singles);
    }

    private static int[] kickers(int[] counts, int copies) {
        int[] ranks = new int[5];
        int index = 0;
        for (int rank = 14; rank >= 2; rank--) if (counts[rank] == copies) ranks[index++] = rank;
        return ranks;
    }

    private static long encode(int category, int... ranks) {
        long value = category;
        for (int i = 0; i < 5; i++) value = value * 15 + (i < ranks.length ? ranks[i] : 0);
        return value;
    }
}
