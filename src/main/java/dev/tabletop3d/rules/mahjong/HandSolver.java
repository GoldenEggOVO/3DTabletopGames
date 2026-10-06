package dev.tabletop3d.rules.mahjong;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class HandSolver {
    public record Group(boolean sequence, int type, int wildcards) {}
    public record Shape(int pair, int pairWildcards, List<Group> groups) {
        public Shape { groups = List.copyOf(groups); }
    }
    private HandSolver() {}
    public static List<Shape> solve(int[] input, int wildcards, int groupsNeeded) {
        if (!valid(input, wildcards) || groupsNeeded < 0 || groupsNeeded > 5
                || sum(input) + wildcards != groupsNeeded * 3 + 2) return List.of();
        int[] counts = input.clone();
        Set<Shape> found = new LinkedHashSet<>();
        for (int pair = 0; pair < 34; pair++) {
            for (int natural = Math.min(2, counts[pair]); natural >= 0; natural--) {
                int missing = 2 - natural;
                if (missing > wildcards) continue;
                counts[pair] -= natural;
                List<List<Group>> groups = new ArrayList<>();
                groups(counts, wildcards - missing, groupsNeeded, new ArrayList<>(), groups);
                for (List<Group> group : groups) found.add(new Shape(pair, missing, group));
                counts[pair] += natural;
            }
        }
        return List.copyOf(found);
    }
    private static void groups(int[] counts, int wildcards, int groupsLeft,
            List<Group> path, List<List<Group>> found) {
        if (groupsLeft == 0) {
            if (wildcards == 0 && sum(counts) == 0) found.add(List.copyOf(path));
            return;
        }
        int first = 0;
        while (first < 34 && counts[first] == 0) first++;
        if (first == 34) {
            if (wildcards != groupsLeft * 3) return;
            for (int type = 0; type < 34; type++) {
                path.add(new Group(false, type, 3));
                groups(counts, wildcards - 3, groupsLeft - 1, path, found);
                path.removeLast();
                if (type < 27 && type % 9 <= 6) {
                    path.add(new Group(true, type, 3));
                    groups(counts, wildcards - 3, groupsLeft - 1, path, found);
                    path.removeLast();
                }
            }
            return;
        }
        for (int natural = Math.min(3, counts[first]); natural >= 1; natural--) {
            int missing = 3 - natural;
            if (missing > wildcards) continue;
            counts[first] -= natural;
            path.add(new Group(false, first, missing));
            groups(counts, wildcards - missing, groupsLeft - 1, path, found);
            path.removeLast();
            counts[first] += natural;
        }
        if (first >= 27) return;
        for (int start = Math.max(first / 9 * 9, first - 2); start <= first && start % 9 <= 6; start++) {

            for (int wildcardMask = 0; wildcardMask < 8; wildcardMask++) {
                int missing = Integer.bitCount(wildcardMask);
                if (missing > wildcards || (wildcardMask & (1 << (first - start))) != 0) continue;
                boolean possible = true;
                for (int offset = 0; offset < 3; offset++) {
                    if ((wildcardMask & (1 << offset)) == 0 && counts[start + offset] == 0) possible = false;
                }
                if (!possible) continue;
                for (int offset = 0; offset < 3; offset++) {
                    if ((wildcardMask & (1 << offset)) == 0) counts[start + offset]--;
                }
                path.add(new Group(true, start, missing));
                groups(counts, wildcards - missing, groupsLeft - 1, path, found);
                path.removeLast();
                for (int offset = 0; offset < 3; offset++) {
                    if ((wildcardMask & (1 << offset)) == 0) counts[start + offset]++;
                }
            }
        }
    }
    public static boolean sevenPairs(int[] counts, int wildcards, boolean allowQuads) {
        if (!valid(counts, wildcards) || sum(counts) + wildcards != 14) return false;
        int singles = 0, pairs = 0;
        for (int count : counts) {
            if (!allowQuads && count > 2) return false;
            pairs += count / 2;
            singles += count % 2;
        }
        return singles <= wildcards && pairs + singles + (wildcards - singles) / 2 == 7;
    }
    public static boolean orphans(int[] counts, int wildcards) {
        if (!valid(counts, wildcards) || sum(counts) + wildcards != 14) return false;
        int missing = 0, pairs = 0;
        for (int type = 0; type < 34; type++) {
            if (!Tiles.terminalOrHonor(type)) {
                if (counts[type] > 0) return false;
                continue;
            }
            if (counts[type] > 2) return false;
            if (counts[type] == 0) missing++;
            if (counts[type] == 2) pairs++;
        }
        return pairs <= 1 && missing <= wildcards && wildcards - missing == 1 - pairs;
    }
    public static Set<Integer> waits(int[] input, int wildcardType, int groupsNeeded,
            boolean allowSevenPairs, boolean allowThirteenOrphans, boolean allowPairQuads) {
        if (input.length != 34) return Set.of();
        Set<Integer> waits = new LinkedHashSet<>();
        for (int type = 0; type < 34; type++) {
            if (input[type] >= 4) continue;
            int[] counts = input.clone();
            counts[type]++;
            int wildcards = wildcardType < 0 ? 0 : counts[wildcardType];
            if (wildcardType >= 0) counts[wildcardType] = 0;
            boolean standard = !solve(counts, wildcards, groupsNeeded).isEmpty();
            boolean special = !standard && groupsNeeded == 4
                    && (allowSevenPairs && sevenPairs(counts, wildcards, allowPairQuads)
                    || allowThirteenOrphans && orphans(counts, wildcards));
            if (standard || special) waits.add(type);
        }
        return Set.copyOf(waits);
    }
    private static boolean valid(int[] counts, int wildcards) {
        if (counts.length != 34 || wildcards < 0 || wildcards > 4) return false;
        for (int count : counts) if (count < 0 || count > 4) return false;
        return true;
    }
    private static int sum(int[] counts) {
        int total = 0;
        for (int count : counts) total += count;
        return total;
    }
}
