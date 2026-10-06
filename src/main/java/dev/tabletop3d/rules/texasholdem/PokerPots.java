package dev.tabletop3d.rules.texasholdem;

import java.util.*;

public final class PokerPots {
    private PokerPots() {}

    public static int[] awards(
            int[] contributions, boolean[] folded, long[] strengths, int dealer) {
        int[] result = new int[contributions.length];
        int previous = 0;
        for (int level :
                Arrays.stream(contributions)
                        .filter(value -> value > 0)
                        .distinct()
                        .sorted()
                        .toArray()) {
            var contributors = new ArrayList<Integer>();
            var eligible = new ArrayList<Integer>();
            for (int seat = 0; seat < contributions.length; seat++)
                if (contributions[seat] >= level) {
                    contributors.add(seat);
                    if (!folded[seat]) eligible.add(seat);
                }
            int pot = (level - previous) * contributors.size();
            previous = level;
            if (contributors.size() == 1) {
                result[contributors.getFirst()] += pot;
                continue;
            }
            if (eligible.isEmpty())
                for (int seat = 0; seat < folded.length; seat++)
                    if (!folded[seat]) eligible.add(seat);
            long best = eligible.stream().mapToLong(seat -> strengths[seat]).max().orElseThrow();
            var winners =
                    eligible.stream()
                            .filter(seat -> strengths[seat] == best)
                            .sorted(
                                    Comparator.comparingInt(
                                            seat ->
                                                    Math.floorMod(
                                                            seat - dealer - 1,
                                                            contributions.length)))
                            .toList();
            for (int index = 0; index < winners.size(); index++)
                result[winners.get(index)] +=
                        pot / winners.size() + (index < pot % winners.size() ? 1 : 0);
        }
        return result;
    }
}
