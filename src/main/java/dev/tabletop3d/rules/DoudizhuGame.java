package dev.tabletop3d.rules;

import java.util.*;

/** Classic three-seat landlord game with deterministic dealing and team scoring. */
public final class DoudizhuGame implements SelectedHandGame {
    private final Random random;
    private final List<List<PlayingCard>> hands = new ArrayList<>();
    private final List<List<Piece>> played = new ArrayList<>();
    private List<PlayingCard> bottom = List.of();
    private int current, firstBidder, bidders, bid, landlord = -1, leader, passes, multiplier = 1;
    private final int[] playCounts = new int[3], scores = new int[3];
    private List<DoudizhuCombination> trick;
    private String result = "ongoing";

    public DoudizhuGame(int players, long seed) {
        if (players != 3)
            throw new RuleViolation("error.doudizhu.players", "Doudizhu requires three players");
        random = new Random(seed);
        for (int i = 0; i < 3; i++) {
            hands.add(new ArrayList<>());
            played.add(List.of());
        }
        deal();
    }

    private void deal() {
        var deck = PlayingCard.deck(random, true);
        for (int seat = 0; seat < 3; seat++) {
            hands.get(seat).clear();
            hands.get(seat).addAll(deck.subList(seat * 17, seat * 17 + 17));
            sort(seat);
        }
        bottom = List.copyOf(deck.subList(51, 54));
        current = firstBidder = random.nextInt(3);
        bidders = 0;
        bid = 0;
        landlord = -1;
    }

    private void sort(int seat) {
        hands.get(seat)
                .sort(
                        Comparator.comparingInt(PlayingCard::landlordRank)
                                .thenComparing(PlayingCard::suit)
                                .thenComparing(PlayingCard::id));
    }

    public String id() {
        return "doudizhu";
    }

    public int playerCount() {
        return 3;
    }

    public int currentPlayer() {
        return current;
    }

    public boolean finished() {
        return !result.equals("ongoing");
    }

    public String outcome() {
        return result;
    }

    public List<Cell> cells() {
        return List.of();
    }

    public List<Piece> hand(int seat) {
        return hands.get(seat).stream().map(PlayingCard::piece).toList();
    }

    public List<Piece> discards(int seat) {
        return played.get(seat);
    }

    public List<Piece> exposed(int seat) {
        return bidders < 3 ? List.of() : bottom.stream().map(PlayingCard::piece).toList();
    }

    public int handSize(int seat) {
        return hands.get(seat).size();
    }

    public int deckSize() {
        return bidders < 3 ? 3 : 0;
    }

    public List<String> controls(int seat) {
        if (finished() || seat != current) return List.of();
        if (bidders < 3) {
            var controls = new ArrayList<String>();
            controls.add("bid:0");
            for (int value = bid + 1; value <= 3; value++) controls.add("bid:" + value);
            return controls;
        }
        return trick == null ? List.of("play", "clear") : List.of("play", "pass", "clear");
    }

    public String selectionAction(int seat, List<String> ids) {
        var selected = PlayingCard.selected(hands.get(seat), ids);
        combinations(selected);
        return "play:" + String.join(",", ids);
    }

    private boolean beatsTrick(DoudizhuCombination combination) {
        return trick == null || trick.stream().anyMatch(combination::beats);
    }

    private List<DoudizhuCombination> combinations(List<PlayingCard> cards) {
        var valid =
                DoudizhuCombination.classify(cards.stream().map(PlayingCard::landlordRank).toList())
                        .stream()
                        .filter(this::beatsTrick)
                        .toList();
        if (valid.isEmpty())
            throw new RuleViolation(
                    "error.doudizhu.combination",
                    "The selected group does not beat the current combination");
        return valid;
    }

    public List<String> legalActions(int seat) {
        var controls = controls(seat);
        if (controls.isEmpty() || controls.getFirst().startsWith("bid:")) return controls;
        var actions = new LinkedHashSet<String>();
        var groups = new TreeMap<Integer, List<PlayingCard>>();
        for (var card : hands.get(seat))
            groups.computeIfAbsent(card.landlordRank(), r -> new ArrayList<>()).add(card);
        for (var entry : groups.entrySet()) {
            var cards = entry.getValue();
            for (int copies = 1; copies <= cards.size(); copies++)
                candidate(actions, cards.subList(0, copies));
            for (int copies : new int[] {3, 4})
                if (cards.size() >= copies) {
                    var body = cards.subList(0, copies);
                    attach(actions, body, groups, copies == 3 ? 1 : 2, 1);
                    attach(actions, body, groups, copies == 3 ? 1 : 2, 2);
                }
        }
        for (int copies = 1; copies <= 3; copies++)
            for (int start = 3; start <= 14; start++) {
                var body = new ArrayList<PlayingCard>();
                for (int rank = start;
                        rank <= 14 && groups.getOrDefault(rank, List.of()).size() >= copies;
                        rank++) {
                    body.addAll(groups.get(rank).subList(0, copies));
                    if (body.size() / copies < (copies == 1 ? 5 : copies == 2 ? 3 : 2)) continue;
                    candidate(actions, body);
                    if (copies == 3) {
                        attach(actions, body, groups, body.size() / 3, 1);
                        attach(actions, body, groups, body.size() / 3, 2);
                    }
                }
            }
        if (groups.containsKey(16) && groups.containsKey(17))
            candidate(actions, List.of(groups.get(16).getFirst(), groups.get(17).getFirst()));
        if (trick != null) actions.add("pass");
        return List.copyOf(actions);
    }

    private void attach(
            Set<String> actions,
            List<PlayingCard> body,
            Map<Integer, List<PlayingCard>> groups,
            int count,
            int copies) {
        var ranks = body.stream().map(PlayingCard::landlordRank).toList();
        var wings = new ArrayList<PlayingCard>();
        for (var entry : groups.entrySet())
            if (!ranks.contains(entry.getKey())) {
                var available = entry.getValue();
                if (copies == 1) wings.addAll(available);
                else if (available.size() >= 2) wings.addAll(available.subList(0, 2));
            }
        if (wings.size() >= count * copies) {
            var selected = new ArrayList<>(body);
            selected.addAll(wings.subList(0, count * copies));
            candidate(actions, selected);
        }
    }

    private void candidate(Set<String> actions, List<PlayingCard> cards) {
        if (DoudizhuCombination.classify(cards.stream().map(PlayingCard::landlordRank).toList())
                .stream()
                .anyMatch(this::beatsTrick))
            actions.add("play:" + String.join(",", cards.stream().map(PlayingCard::id).toList()));
    }

    public void apply(int seat, String action) {
        if (finished() || seat != current)
            throw new RuleViolation("error.not-your-turn", "Not your turn");
        if (controls(seat).getFirst().startsWith("bid:")) {
            if (!controls(seat).contains(action))
                throw new RuleViolation("error.cards.action", "Invalid card action");
            int value = Integer.parseInt(action.substring(4));
            bidders++;
            if (value > bid) {
                bid = value;
                landlord = seat;
            }
            if (value == 3 || bidders == 3) {
                if (landlord < 0) {
                    deal();
                    return;
                }
                hands.get(landlord).addAll(bottom);
                sort(landlord);
                current = leader = landlord;
                bidders = 3;
            } else current = (firstBidder + bidders) % 3;
            return;
        }
        if (action.equals("pass")) {
            if (trick == null)
                throw new RuleViolation("error.cards.must-play", "You must lead a card group");
            current = (current + 1) % 3;
            if (++passes == 2) {
                current = leader;
                trick = null;
                passes = 0;
            }
            return;
        }
        if (!action.startsWith("play:"))
            throw new RuleViolation("error.cards.action", "Invalid card action");
        var selected =
                PlayingCard.selected(
                        hands.get(seat), Arrays.asList(action.substring(5).split(",")));
        var next = combinations(selected);
        hands.get(seat).removeAll(selected);
        played.set(seat, selected.stream().map(PlayingCard::piece).toList());
        playCounts[seat]++;
        trick = next;
        leader = seat;
        passes = 0;
        if (next.stream()
                .anyMatch(
                        c ->
                                c.type() == DoudizhuCombination.Type.BOMB
                                        || c.type() == DoudizhuCombination.Type.ROCKET))
            multiplier *= 2;
        if (hands.get(seat).isEmpty()) {
            boolean landlordWins = seat == landlord;
            if (landlordWins
                    ? playCounts[(landlord + 1) % 3] + playCounts[(landlord + 2) % 3] == 0
                    : playCounts[landlord] == 1) multiplier *= 2;
            for (int player = 0; player < 3; player++)
                scores[player] =
                        (player == landlord ? 2 : -1) * bid * multiplier * (landlordWins ? 1 : -1);
            result =
                    landlordWins
                            ? "winner:" + seat
                            : "winners:" + (landlord + 1) % 3 + "," + (landlord + 2) % 3;
        } else current = (current + 1) % 3;
    }

    public Map<String, String> publicInfo() {
        var info = new LinkedHashMap<String, String>();
        info.put("phase", finished() ? "finished" : bidders < 3 ? "bidding" : "playing");
        info.put("landlord", Integer.toString(bidders < 3 ? -1 : landlord));
        info.put("bid", Integer.toString(bid));
        info.put("multiplier", Integer.toString(multiplier));
        info.put("combination", trick == null ? "" : trick.getFirst().type().name());
        for (int seat = 0; seat < 3; seat++)
            info.put("score." + seat, Integer.toString(scores[seat]));
        return Map.copyOf(info);
    }
}
