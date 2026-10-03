package dev.tabletop3d.rules;

import java.util.*;

/** Every valid decomposition of a submitted group, including ambiguous airplane bodies. */
public record DoudizhuCombination(Type type, int rank, int size) {
    public enum Type {
        SINGLE,
        PAIR,
        TRIPLE,
        TRIPLE_SINGLE,
        TRIPLE_PAIR,
        STRAIGHT,
        PAIR_STRAIGHT,
        TRIPLE_STRAIGHT,
        AIRPLANE_SINGLE,
        AIRPLANE_PAIR,
        FOUR_SINGLE,
        FOUR_PAIR,
        BOMB,
        ROCKET
    }

    public boolean beats(DoudizhuCombination previous) {
        if (previous == null) return true;
        if (previous.type == Type.ROCKET) return false;
        if (type == Type.ROCKET) return true;
        if (type == Type.BOMB && previous.type != Type.BOMB) return true;
        return type == previous.type && size == previous.size && rank > previous.rank;
    }

    public static List<DoudizhuCombination> classify(List<Integer> cards) {
        int[] count = new int[18];
        for (int rank : cards) {
            if (rank < 3 || rank > 17 || ++count[rank] > (rank >= 16 ? 1 : 4)) return List.of();
        }
        int size = cards.size();
        var result = new LinkedHashSet<DoudizhuCombination>();
        if (size == 2 && count[16] == 1 && count[17] == 1)
            result.add(new DoudizhuCombination(Type.ROCKET, 17, 2));
        for (int rank = 3; rank <= 17; rank++) {
            if (count[rank] == size && size >= 1 && size <= 4)
                result.add(
                        new DoudizhuCombination(
                                switch (size) {
                                    case 1 -> Type.SINGLE;
                                    case 2 -> Type.PAIR;
                                    case 3 -> Type.TRIPLE;
                                    default -> Type.BOMB;
                                },
                                rank,
                                size));
            if (count[rank] == 3) {
                if (size == 4) result.add(new DoudizhuCombination(Type.TRIPLE_SINGLE, rank, size));
                if (size == 5 && distinctGroups(count, rank, 2) == 1)
                    result.add(new DoudizhuCombination(Type.TRIPLE_PAIR, rank, size));
            }
            if (count[rank] == 4 && !(count[16] > 0 && count[17] > 0)) {
                if (size == 6) result.add(new DoudizhuCombination(Type.FOUR_SINGLE, rank, size));
                if (size == 8 && distinctGroups(count, rank, 2) == 2)
                    result.add(new DoudizhuCombination(Type.FOUR_PAIR, rank, size));
            }
        }
        for (int copies = 1; copies <= 3; copies++) {
            int length = size / copies;
            if (size % copies == 0 && length >= (copies == 1 ? 5 : copies == 2 ? 3 : 2))
                for (int start = 3; start + length - 1 <= 14; start++) {
                    boolean matches = true;
                    for (int rank = 3; rank <= 17; rank++)
                        if (count[rank] != (rank >= start && rank < start + length ? copies : 0))
                            matches = false;
                    if (matches)
                        result.add(
                                new DoudizhuCombination(
                                        copies == 1
                                                ? Type.STRAIGHT
                                                : copies == 2
                                                        ? Type.PAIR_STRAIGHT
                                                        : Type.TRIPLE_STRAIGHT,
                                        start + length - 1,
                                        size));
                }
        }
        for (int wing = 1; wing <= 2; wing++) {
            int length = size / (3 + wing);
            if (length < 2 || size % (3 + wing) != 0) continue;
            for (int start = 3; start + length - 1 <= 14; start++) {
                boolean matches = true;
                int pairs = 0;
                for (int rank = 3; rank <= 17; rank++) {
                    if (rank >= start && rank < start + length) {
                        if (count[rank] != 3) matches = false;
                    } else if (wing == 2 && count[rank] > 0) {
                        if (count[rank] != 2) matches = false;
                        pairs++;
                    }
                }
                if (wing == 1 && count[16] > 0 && count[17] > 0) matches = false;
                if (wing == 2 && pairs != length) matches = false;
                if (matches)
                    result.add(
                            new DoudizhuCombination(
                                    wing == 1 ? Type.AIRPLANE_SINGLE : Type.AIRPLANE_PAIR,
                                    start + length - 1,
                                    size));
            }
        }
        return List.copyOf(result);
    }

    private static int distinctGroups(int[] counts, int except, int copies) {
        int groups = 0;
        for (int rank = 3; rank < counts.length; rank++)
            if (rank != except && counts[rank] > 0) {
                if (counts[rank] != copies) return -1;
                groups++;
            }
        return groups;
    }
}
