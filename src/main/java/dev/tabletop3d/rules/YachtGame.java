package dev.tabletop3d.rules;

import java.util.*;

/** Casual 12-category Yacht variant. Seeded randomness makes replay and takeback deterministic. */
public final class YachtGame implements BoardGame {
    public static final List<String> CATEGORIES =
            List.of(
                    "ones",
                    "twos",
                    "threes",
                    "fours",
                    "fives",
                    "sixes",
                    "choice",
                    "four-kind",
                    "full-house",
                    "small-straight",
                    "large-straight",
                    "yacht");
    public static final List<String> LABELS =
            List.of("Ones", "Twos", "Threes", "Fours", "Fives", "Sixes", "Choice", "Four of a Kind", "Full House", "Small Straight", "Large Straight", "Yacht");
    private final int[][] sheet;
    private final int[] dice = new int[5];
    private final boolean[] held = new boolean[5];
    private final Random random;
    private int turn, rolls, filled;
    private String result = "ongoing";

    public YachtGame(int players, long seed) {
        if (players < 2 || players > 4)
            throw new RuleViolation("error.yacht-dice-supports-2-4-players", "Yacht Dice supports 2–4 players");
        sheet = new int[players][12];
        for (int[] row : sheet) Arrays.fill(row, -1);
        random = new Random(seed);
    }

    public String id() {
        return "yacht";
    }

    public int playerCount() {
        return sheet.length;
    }

    public int currentPlayer() {
        return turn;
    }

    public boolean finished() {
        return !result.equals("ongoing");
    }

    public String outcome() {
        return result;
    }

    public int rolls() {
        return rolls;
    }

    public int[] dice() {
        return dice.clone();
    }

    public boolean held(int index) {
        return held[index];
    }

    public int written(int seat, int category) {
        return sheet[seat][category];
    }

    public int total(int seat) {
        int upper = 0, total = 0;
        for (int i = 0; i < 12; i++) {
            int v = Math.max(0, sheet[seat][i]);
            total += v;
            if (i < 6) upper += v;
        }
        return total + (upper >= 63 ? 35 : 0);
    }

    public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            cells.add(
                    new Cell(
                            "die" + i,
                            i * 2,
                            0,
                            rolls == 0 ? "" : dice[i] + (held[i] ? "✓" : ""),
                            rolls == 0 ? -1 : turn));
        return List.copyOf(cells);
    }

    public List<String> legalActions(int seat) {
        if (finished() || seat != turn) return List.of();
        List<String> a = new ArrayList<>();
        if (rolls < 3
                && (rolls == 0 || java.util.stream.IntStream.range(0, 5).anyMatch(i -> !held[i])))
            a.add("roll");
        if (rolls > 0) {
            if (rolls < 3) for (int i = 0; i < 5; i++) a.add("hold:die" + i);
            for (int i = 0; i < 12; i++)
                if (sheet[turn][i] < 0) a.add("score:" + CATEGORIES.get(i));
        }
        return List.copyOf(a);
    }

    public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action))
            throw new RuleViolation(
                    "error.choose-available-dice-or-an-empty-score-category", "Choose available dice or an empty score category");
        if (action.equals("roll")) {
            for (int i = 0; i < 5; i++) if (!held[i]) dice[i] = random.nextInt(6) + 1;
            rolls++;
        } else if (action.startsWith("hold:")) {
            int i = Integer.parseInt(action.substring(8));
            held[i] = !held[i];
        } else {
            int category = CATEGORIES.indexOf(action.substring(6));
            sheet[turn][category] = score(category, dice);
            filled++;
            if (filled == sheet.length * 12) {
                int best = -1, winner = -1;
                boolean tied = false;
                for (int i = 0; i < sheet.length; i++) {
                    int s = total(i);
                    if (s > best) {
                        best = s;
                        winner = i;
                        tied = false;
                    } else if (s == best) tied = true;
                }
                result = tied ? "draw:equal-total" : "winner:" + winner;
            } else {
                turn = (turn + 1) % sheet.length;
                rolls = 0;
                Arrays.fill(dice, 0);
                Arrays.fill(held, false);
            }
        }
    }

    public static int score(int category, int[] dice) {
        if (category < 0
                || category >= 12
                || dice.length != 5
                || Arrays.stream(dice).anyMatch(d -> d < 1 || d > 6))
            throw new RuleViolation("error.invalid-score-data", "Invalid score data");
        int[] count = new int[7];
        int sum = 0;
        for (int d : dice) {
            count[d]++;
            sum += d;
        }
        if (category < 6) return count[category + 1] * (category + 1);
        int max = Arrays.stream(count).max().orElse(0), run = 0, longest = 0;
        for (int i = 1; i <= 6; i++) {
            run = count[i] > 0 ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        return switch (category) {
            case 6 -> sum;
            case 7 -> max >= 4 ? sum : 0;
            case 8 -> max == 3 && Arrays.stream(count).anyMatch(c -> c == 2) ? sum : 0;
            case 9 -> longest >= 4 ? 15 : 0;
            case 10 -> longest >= 5 ? 30 : 0;
            case 11 -> max == 5 ? 50 : 0;
            default -> 0;
        };
    }

    public Map<String, String> publicInfo() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("rules", "12-round Yacht variant: up to three rolls per turn with held dice; small straight 15, large straight 30, Yacht 50; upper section of at least 63 earns 35 bonus points; no extra Yacht bonus");
        out.put("rulesVariant", "yacht-12-upper-bonus");
        out.put(
                "phase",
                finished()
                        ? "All twelve rounds finished"
                        : "Round " + (filled / sheet.length + 1) + " / 12 · Rolls " + rolls + " / 3");
        for (int i = 0; i < sheet.length; i++)
            out.put("score" + i, (i + 1) + ": " + total(i) + " points");
        return out;
    }
}
