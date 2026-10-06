package dev.tabletop3d.rules.chinesecheckers;

import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.rules.RuleMessage;
import dev.tabletop3d.rules.RuleViolation;
import dev.tabletop3d.rules.chinesecheckers.engine.Board;
import dev.tabletop3d.rules.chinesecheckers.engine.Point;

import java.util.*;

public final class ChineseCheckersGame implements BoardGame {
    private RuleMessage lastMessage = RuleMessage.of("board.ready");

    @Override
    public List<RuleMessage> messages() {
        return List.of(lastMessage);
    }

    private static final int[][][] CAMPS = {
        {{6, 16}, {5, 15}, {6, 15}, {5, 14}, {6, 14}, {7, 14}, {4, 13}, {5, 13}, {6, 13}, {7, 13}},
        {{0, 12}, {1, 12}, {2, 12}, {3, 12}, {0, 11}, {1, 11}, {2, 11}, {1, 10}, {2, 10}, {1, 9}},
        {{0, 4}, {1, 4}, {2, 4}, {3, 4}, {0, 5}, {1, 5}, {2, 5}, {1, 6}, {2, 6}, {1, 7}},
        {{6, 0}, {5, 1}, {6, 1}, {5, 2}, {6, 2}, {7, 2}, {4, 3}, {5, 3}, {6, 3}, {7, 3}},
        {{9, 4}, {10, 4}, {11, 4}, {12, 4}, {9, 5}, {10, 5}, {11, 5}, {10, 6}, {11, 6}, {10, 7}},
        {
            {9, 12}, {10, 12}, {11, 12}, {12, 12}, {9, 11}, {10, 11}, {11, 11}, {10, 10}, {11, 10},
            {10, 9}
        }
    };
    private final Board geometry = new Board();
    private final int[] colors;
    private final ChineseCheckersOptions options;
    private final List<Integer> ranking = new ArrayList<>();
    private final Map<String, Integer> occupied = new LinkedHashMap<>();
    private final Map<String, Integer> repeats = new HashMap<>();
    private int current, passes;
    private String result = "ongoing", lastAction = "Waiting for the first move";
    private Map<String, List<String>> cached;

    public ChineseCheckersGame(int players) {
        this(players, ChineseCheckersOptions.DEFAULT);
    }

    public ChineseCheckersGame(int players, ChineseCheckersOptions options) {
        this.options = Objects.requireNonNull(options);
        colors =
                switch (players) {
                    case 2 -> new int[] {0, 3};
                    case 3 -> new int[] {0, 2, 4};
                    case 4 -> new int[] {1, 2, 4, 5};
                    case 6 -> new int[] {0, 1, 2, 3, 4, 5};
                    default ->
                            throw new RuleViolation(
                                    "error.chinese-checkers-supports-2-3-4-or-6-players",
                                    "Chinese Checkers supports 2, 3, 4, or 6 players");
                };
        for (int seat = 0; seat < colors.length; seat++)
            for (int[] xy : CAMPS[colors[seat]]) occupied.put(xy[0] + "," + xy[1], seat);
        repeats.put(positionKey(), 1);
    }

    @Override
    public String id() {
        return "checkers";
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

    public ChineseCheckersOptions options() {
        return options;
    }

    @Override
    public List<Cell> cells() {
        List<Cell> out = new ArrayList<>(121);
        for (int j = 0; j < Board.sizeJ; j++)
            for (int i = 0; i < Board.sizeI; i++) {
                if (!Board.hole(new Point(i, j))) continue;
                String id = i + "," + j;
                int owner = occupied.getOrDefault(id, -1);

                out.add(new Cell(id, 2 * i + j % 2, j, owner < 0 ? "" : "●", owner));
            }
        return List.copyOf(out);
    }

    private Set<String> target(int seat) {
        Set<String> target = new HashSet<>();
        for (int[] xy : CAMPS[(colors[seat] + 3) % 6]) target.add(xy[0] + "," + xy[1]);
        return target;
    }

    private static Point point(String id) {
        String[] parts = id.split(",");
        return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private static String id(Point p) {
        return p.i + "," + p.j;
    }

    private boolean allowedCamp(String destination) {
        if (options.enterOtherCamps()) return true;
        for (int camp = 0; camp < CAMPS.length; camp++) {
            if (camp == colors[current] || camp == (colors[current] + 3) % 6) continue;
            for (int[] xy : CAMPS[camp]) if (destination.equals(xy[0] + "," + xy[1])) return false;
        }
        return true;
    }

    private Map<String, List<String>> moves() {
        if (cached != null) return cached;
        Map<String, List<String>> out = new TreeMap<>();
        Set<String> goal = target(current);
        for (var entry : occupied.entrySet()) {
            if (entry.getValue() != current) continue;
            String origin = entry.getKey();
            Point start = point(origin);
            for (int d = 0; d < 6; d++) {
                Point to = geometry.hop(start, d);
                if (to != null
                        && !occupied.containsKey(id(to))
                        && allowedCamp(id(to))
                        && (!goal.contains(origin) || goal.contains(id(to))))
                    out.put("move:" + origin + ":" + id(to), List.of(origin, id(to)));
            }

            ArrayDeque<List<String>> queue = new ArrayDeque<>();
            Set<String> seen = new HashSet<>();
            seen.add(origin);
            queue.add(List.of(origin));
            while (!queue.isEmpty()) {
                List<String> path = queue.remove();
                String from = path.getLast();
                for (int d = 0; d < 6; d++) {
                    Point middle = geometry.hop(point(from), d);
                    if (middle == null
                            || id(middle).equals(origin)
                            || !occupied.containsKey(id(middle))) continue;
                    if (!options.jumpOwn() && occupied.get(id(middle)) == current) continue;
                    Point to = geometry.hop(middle, d);
                    if (to == null) continue;
                    String destination = id(to);
                    if (occupied.containsKey(destination) || seen.contains(destination)) continue;
                    if (!allowedCamp(destination)) continue;
                    if (goal.contains(from) && !goal.contains(destination)) continue;
                    seen.add(destination);
                    List<String> next = new ArrayList<>(path);
                    next.add(destination);
                    List<String> frozen = List.copyOf(next);
                    out.put("move:" + origin + ":" + destination, frozen);
                    queue.add(frozen);
                }
            }
        }
        if (out.isEmpty()) out.put("pass", List.of());
        cached = Collections.unmodifiableMap(out);
        return cached;
    }

    @Override
    public List<String> legalActions(int seat) {
        return finished() || seat != current ? List.of() : List.copyOf(moves().keySet());
    }

    @Override
    public void apply(int seat, String action) {
        if (finished() || seat != current || action == null || !moves().containsKey(action))
            throw new RuleViolation("error.invalid-chinese-checkers-move", "Invalid Chinese Checkers move");
        if (action.equals("pass")) {
            passes++;
            lastAction = "Player " + (seat + 1) + " has no legal move; passed";
            lastMessage = RuleMessage.of("board.passed", "player", seat + 1);
        } else {
            List<String> path = moves().get(action);
            occupied.remove(path.getFirst());
            occupied.put(path.getLast(), seat);
            passes = 0;
            lastAction = "Player " + (seat + 1) + " " + String.join(" → ", path);
            lastMessage =
                    RuleMessage.of(
                            "board.move", "player", seat + 1, "move", String.join(" → ", path));
            if (target(seat).stream().allMatch(p -> occupied.getOrDefault(p, -1) == seat)) {
                ranking.add(seat);
                if (!options.allPlaces()) result = "winner:" + seat;
                else if (ranking.size() == colors.length - 1) {
                    for (int other = 0; other < colors.length; other++)
                        if (!ranking.contains(other)) ranking.add(other);
                    result = "winner:" + ranking.getFirst();
                }
            }
        }
        cached = null;
        if (finished()) return;
        do {
            current = (current + 1) % colors.length;
        } while (ranking.contains(current));
        if (passes >= colors.length - ranking.size()) result = "draw:blocked";
        else if (repeats.merge(positionKey(), 1, Integer::sum) >= 3)
            result = "draw:threefold-repetition";
    }

    private String positionKey() {
        return current + ":" + ranking + ":" + new TreeMap<>(occupied);
    }

    @Override
    public Map<String, String> publicInfo() {
        String rules =
                options.equals(ChineseCheckersOptions.DEFAULT)
                        ? "121-hole star, ten pegs each; adjacent steps or consecutive short jumps; first player to fill the opposite camp wins"
                        : "121-hole star, ten pegs each; adjacent steps or consecutive short jumps;"
                              + " "
                                + (options.allPlaces()
                                        ? "finish all places"
                                        : "first player home wins");
        String limits =
                options.equals(ChineseCheckersOptions.DEFAULT)
                        ? "Cannot leave the target camp; may pass through other camps; threefold repetition draws"
                        : "Cannot leave the target camp; "
                                + (options.jumpOwn()
                                        ? "may jump own pegs; "
                                        : "cannot jump own pegs; ")
                                + (options.enterOtherCamps()
                                        ? "may enter other camps; "
                                        : "cannot enter other camps; ")
                                + "threefold repetition draws";
        return Map.of(
                "rules",
                rules,
                "rulesVariant",
                "standard-star-short-jumps",
                "ruleLimit",
                limits,
                "phase",
                finished() ? "Game finished" : "Waiting for a move",
                "turn",
                "Player " + (current + 1),
                "lastAction",
                lastAction,
                "colors",
                Arrays.toString(colors),
                "coordinateSystem",
                "x=2*column+row%2,y=row",
                "ranking",
                String.join(",", ranking.stream().map(String::valueOf).toList()));
    }
}
