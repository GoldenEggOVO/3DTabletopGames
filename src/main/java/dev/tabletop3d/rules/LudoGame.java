package dev.tabletop3d.rules;

import java.util.*;

/** Deterministic Ludo with immutable room rules and optional full placements. */
public final class LudoGame implements BoardGame {
    private RuleMessage lastMessage = RuleMessage.of("board.ready");

    @Override
    public List<RuleMessage> messages() {
        return List.of(lastMessage);
    }

    private final int[] colors, progress;
    private final SplittableRandom random;
    private final LudoOptions options;
    private final List<Integer> placements = new ArrayList<>();
    private final boolean[] deployed;
    private int current, pendingRoll, lastRoll, startAttempts;
    private String result = "ongoing", lastAction = "Ready to roll";
    private static final Map<String, int[]> GEOMETRY = geometry();

    public LudoGame(int players, long seed) {
        this(players, seed, LudoOptions.DEFAULT);
    }

    public LudoGame(int players, long seed, LudoOptions options) {
        colors =
                switch (players) {
                    case 2 -> new int[] {0, 2};
                    case 3 -> new int[] {0, 1, 2};
                    case 4 -> new int[] {0, 1, 2, 3};
                    default ->
                            throw new RuleViolation(
                                    "error.ludo-requires-2-to-4-players",
                                    "Ludo requires 2 to 4 players");
                };
        this.options = options;
        progress = new int[players * 4];
        Arrays.fill(progress, -1);
        deployed = new boolean[players];
        Arrays.fill(deployed, options.autoFirst());
        if (options.autoFirst()) for (int seat = 0; seat < players; seat++) progress[seat * 4] = 0;
        random = new SplittableRandom(seed);
    }

    @Override
    public String id() {
        return "ludo";
    }

    @Override
    public int playerCount() {
        return colors.length;
    }

    @Override
    public int currentPlayer() {
        return current;
    }

    @Override
    public boolean finished() {
        return !result.equals("ongoing");
    }

    @Override
    public String outcome() {
        return result;
    }

    public List<Integer> placements() {
        return List.copyOf(placements);
    }

    private String cell(int pawn, int step) {
        int color = colors[pawn / 4];
        return step < 0
                ? "ba" + color + "_" + (pawn % 4)
                : step == 56
                        ? "go" + color
                        : step >= 51
                                ? "ld" + color + "_" + (step - 51)
                                : "sk" + ((color * 13 + step) % 52);
    }

    private String cell(int pawn) {
        return cell(pawn, progress[pawn]);
    }

    @Override
    public List<Cell> cells() {
        Map<String, List<Integer>> occupants = new HashMap<>();
        for (int i = 0; i < progress.length; i++)
            occupants.computeIfAbsent(cell(i), ignored -> new ArrayList<>()).add(i);
        List<Cell> cells = new ArrayList<>();
        GEOMETRY.forEach(
                (id, xy) -> {
                    List<Integer> pawns = occupants.getOrDefault(id, List.of());
                    cells.add(
                            new Cell(
                                    id,
                                    xy[0],
                                    xy[1],
                                    pawns.isEmpty()
                                            ? ""
                                            : "●"
                                                    + String.join(
                                                            "",
                                                            pawns.stream()
                                                                    .map(
                                                                            i ->
                                                                                    String.valueOf(
                                                                                            i % 4
                                                                                                    + 1))
                                                                    .toList()),
                                    pawns.isEmpty() ? -1 : pawns.getFirst() / 4));
                });
        return List.copyOf(cells);
    }

    @Override
    public List<String> legalActions(int seat) {
        if (finished() || seat != current) return List.of();
        if (pendingRoll == 0) return List.of("roll");
        List<String> moves = new ArrayList<>();
        for (int i = seat * 4; i < seat * 4 + 4; i++) {
            int step = progress[i];
            if (step == 56 || step < 0 && pendingRoll != 6) continue;
            int next = nextStep(i);
            if (next <= 56 && !blocked(i, next)) moves.add("move:" + i + ":" + cell(i, next));
        }
        return List.copyOf(moves);
    }

    private int nextStep(int pawn) {
        int next = progress[pawn] < 0 ? 0 : progress[pawn] + pendingRoll;
        return options.exactFinish() ? next : Math.min(56, next);
    }

    private boolean blocked(int pawn, int next) {
        if (!options.blocking()) return false;
        for (int step = progress[pawn] + 1; step < next; step++) {
            String intermediate = cell(pawn, step);
            for (int other = 0; other < progress.length; other++)
                if (other != pawn && cell(other).equals(intermediate)) return true;
        }
        return false;
    }

    @Override
    public List<String> actionsForCell(int seat, String id) {
        if (id == null || pendingRoll == 0) return List.of();
        return legalActions(seat).stream()
                .filter(
                        a -> {
                            String[] p = a.split(":");
                            return cell(Integer.parseInt(p[1])).equals(id) || p[2].equals(id);
                        })
                .toList();
    }

    @Override
    public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action))
            throw new RuleViolation("error.invalid-ludo-action", "Invalid Ludo action");
        if (action.equals("roll")) {
            pendingRoll = lastRoll = random.nextInt(1, 7);
            lastAction = "Player " + (seat + 1) + " rolled " + lastRoll;
            lastMessage = RuleMessage.of("board.ludo.rolled", "player", seat + 1, "roll", lastRoll);
            if (legalActions(seat).isEmpty()) {
                lastAction += "; no legal move";
                lastMessage =
                        RuleMessage.of("board.ludo.no-move", "player", seat + 1, "roll", lastRoll);
                if (!deployed[seat] && ++startAttempts < options.startingRolls()) pendingRoll = 0;
                else next(lastRoll == 6);
            }
            return;
        }
        int pawn = Integer.parseInt(action.split(":")[1]);
        progress[pawn] = nextStep(pawn);
        deployed[seat] = true;
        int captured = 0;
        if (progress[pawn] <= 50)
            for (int other = 0; other < progress.length; other++) {
                if (other / 4 != seat && cell(other).equals(cell(pawn))) {
                    progress[other] = -1;
                    captured++;
                }
            }
        lastAction =
                "Player "
                        + (seat + 1)
                        + " moved pawn "
                        + (pawn % 4 + 1)
                        + (captured > 0 ? "; captured " + captured : "");
        lastMessage =
                RuleMessage.of(
                        "board.ludo.moved",
                        "player",
                        seat + 1,
                        "pawn",
                        pawn % 4 + 1,
                        "captured",
                        captured);
        boolean won = true;
        for (int i = seat * 4; i < seat * 4 + 4; i++) won &= progress[i] == 56;
        if (won) {
            placements.add(seat);
            if (!options.allPlaces() || placements.size() == colors.length - 1) {
                if (options.allPlaces())
                    for (int other = 0; other < colors.length; other++)
                        if (!placements.contains(other)) placements.add(other);
                result = "winner:" + placements.getFirst();
                pendingRoll = 0;
            } else next(false);
        } else next(pendingRoll == 6);
    }

    private void next(boolean again) {
        if (!again)
            do {
                current = (current + 1) % colors.length;
            } while (placements.contains(current));
        pendingRoll = 0;
        startAttempts = 0;
    }

    @Override
    public Map<String, String> publicInfo() {
        return Map.of(
                "rules",
                "Four pawns; "
                        + (options.autoFirst()
                                ? "first pawn starts out"
                                : "all pawns start in yard")
                        + "; six deploys and rolls again; capture sends rivals to yard; "
                        + (options.blocking() ? "occupied intermediate cells block; " : "")
                        + (options.exactFinish() ? "exact finish" : "overshoot finishes")
                        + (options.allPlaces() ? "; play for all places" : ""),
                "rulesVariant",
                "ludo-"
                        + (options.autoFirst()
                                ? "auto-first"
                                : options.startingRolls() == 3 ? "three-attempts" : "six-required")
                        + "-"
                        + (options.blocking() ? "blocking" : "no-blocking")
                        + "-"
                        + (options.exactFinish() ? "exact" : "over-ok")
                        + (options.allPlaces() ? "-all-places" : "")
                        + "-v1",
                "ruleLimit",
                (options.autoFirst()
                                ? "Automatic deployment is at game start only"
                                : options.startingRolls() == 3
                                        ? "Up to three attempts per turn until the first deployment"
                                        : "All pawns start in yard")
                        + "; captured pawns need six; no triple-six penalty",
                "phase",
                finished() ? "对局结束" : pendingRoll == 0 ? "等待掷骰" : "选择棋子",
                "turn",
                "玩家 " + (current + 1),
                "lastAction",
                lastAction,
                "dice",
                String.valueOf(lastRoll),
                "pendingRoll",
                String.valueOf(pendingRoll),
                "colors",
                Arrays.toString(colors));
    }

    private static Map<String, int[]> geometry() {
        Map<String, int[]> cells = new LinkedHashMap<>();
        List<int[]> ring = new ArrayList<>();
        for (int x = 1; x <= 5; x++) ring.add(new int[] {x, 6});
        for (int y = 5; y >= 0; y--) ring.add(new int[] {6, y});
        ring.add(new int[] {7, 0});
        ring.add(new int[] {8, 0});
        for (int y = 1; y <= 5; y++) ring.add(new int[] {8, y});
        for (int x = 9; x <= 14; x++) ring.add(new int[] {x, 6});
        ring.add(new int[] {14, 7});
        ring.add(new int[] {14, 8});
        for (int x = 13; x >= 9; x--) ring.add(new int[] {x, 8});
        for (int y = 9; y <= 14; y++) ring.add(new int[] {8, y});
        ring.add(new int[] {7, 14});
        ring.add(new int[] {6, 14});
        for (int y = 13; y >= 9; y--) ring.add(new int[] {6, y});
        for (int x = 5; x >= 0; x--) ring.add(new int[] {x, 8});
        ring.add(new int[] {0, 7});
        ring.add(new int[] {0, 6});
        for (int i = 0; i < 52; i++) cells.put("sk" + i, ring.get(i));
        for (int color = 0; color < 4; color++) {
            for (int step = 0; step < 6; step++)
                cells.put(
                        step == 5 ? "go" + color : "ld" + color + "_" + step,
                        rotate(step + 1, 7, color));
            for (int i = 0; i < 4; i++)
                cells.put("ba" + color + "_" + i, rotate(2 + 2 * (i % 2), 2 + 2 * (i / 2), color));
        }
        return Collections.unmodifiableMap(cells);
    }

    private static int[] rotate(int x, int y, int color) {
        for (int i = 0; i < color; i++) {
            int next = 14 - y;
            y = x;
            x = next;
        }
        return new int[] {x, y};
    }
}
