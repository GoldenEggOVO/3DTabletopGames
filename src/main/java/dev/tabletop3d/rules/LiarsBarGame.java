package dev.tabletop3d.rules;

import java.util.*;

/** Bluffing card adaptation. Concealed groups and fatal chambers never enter public state. */
public final class LiarsBarGame implements SelectedHandGame {
    private static final List<Integer> TABLE_RANKS = List.of(14, 13, 12);
    private final Random random;
    private final List<List<PlayingCard>> hands = new ArrayList<>();
    private final boolean[] alive;
    private final int[] chambers, attempts;
    private int current, tableRank, previous = -1, round = 0, challengedSeat = -1, challengedRank;
    private List<PlayingCard> group = List.of();
    private List<Piece> revealed = List.of();
    private String result = "ongoing";

    public LiarsBarGame(int players, long seed) {
        if (players < 2 || players > 4)
            throw new RuleViolation(
                    "error.liars-bar.players", "Liar's Bar requires 2 to 4 players");
        random = new Random(seed);
        alive = new boolean[players];
        chambers = new int[players];
        attempts = new int[players];
        Arrays.fill(alive, true);
        for (int seat = 0; seat < players; seat++) {
            hands.add(new ArrayList<>());
            chambers[seat] = random.nextInt(6) + 1;
        }
        deal(random.nextInt(players));
    }

    private void deal(int starter) {
        var deck = new ArrayList<PlayingCard>();
        for (int rank : TABLE_RANKS)
            for (int copy = 0; copy < 6; copy++)
                deck.add(new PlayingCard(PlayingCard.identity(random), "spades", rank));
        for (int rank = 16; rank <= 17; rank++)
            deck.add(new PlayingCard(PlayingCard.identity(random), "joker", rank));
        Collections.shuffle(deck, random);
        int index = 0;
        for (int seat = 0; seat < playerCount(); seat++) {
            hands.get(seat).clear();
            if (alive[seat])
                for (int count = 0; count < 5; count++) hands.get(seat).add(deck.get(index++));
        }
        tableRank = TABLE_RANKS.get(random.nextInt(TABLE_RANKS.size()));
        current = starter;
        if (!alive[current]) current = nextAlive(current);
        previous = -1;
        group = List.of();
        round++;
    }

    public String id() {
        return "liars-bar";
    }

    public int playerCount() {
        return alive.length;
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

    public List<Piece> hand(int seat) {
        return hands.get(seat).stream().map(PlayingCard::piece).toList();
    }

    public int handSize(int seat) {
        return hands.get(seat).size();
    }

    public int deckSize() {
        return 0;
    }

    public List<Piece> discards(int seat) {
        if (seat != previous) return List.of();
        return java.util.stream.IntStream.range(0, group.size())
                .mapToObj(i -> new Piece("hidden:" + round + ":" + i, "back"))
                .toList();
    }

    public List<Piece> exposed(int seat) {
        return seat == challengedSeat ? revealed : List.of();
    }

    public List<Cell> cells() {
        return List.of(
                new Cell(
                        "declaration",
                        0,
                        0,
                        new PlayingCard("declaration", "spades", tableRank).face(),
                        -1));
    }

    public List<String> controls(int seat) {
        if (finished() || seat != current || !alive[seat]) return List.of();
        long handsRemaining =
                java.util.stream.IntStream.range(0, playerCount())
                        .filter(i -> alive[i] && !hands.get(i).isEmpty())
                        .count();
        if (previous >= 0 && handsRemaining <= 1) return List.of("challenge");
        return previous < 0 ? List.of("play", "clear") : List.of("play", "challenge", "clear");
    }

    public String selectionAction(int seat, List<String> ids) {
        if (!controls(seat).contains("play"))
            throw new RuleViolation(
                    "error.liars-bar.challenge-required", "You must challenge the last group");
        if (ids.size() > 3)
            throw new RuleViolation(
                    "error.liars-bar.selection", "Play between one and three cards");
        PlayingCard.selected(hands.get(seat), ids);
        return "play:" + String.join(",", ids);
    }

    public List<String> legalActions(int seat) {
        var controls = controls(seat);
        var legal = new ArrayList<String>();
        if (controls.contains("play")) {
            for (var card : hands.get(seat)) legal.add("play:" + card.id());
            for (int count = 2; count <= Math.min(3, hands.get(seat).size()); count++)
                legal.add(
                        "play:"
                                + String.join(
                                        ",",
                                        hands.get(seat).subList(0, count).stream()
                                                .map(PlayingCard::id)
                                                .toList()));
        }
        if (controls.contains("challenge")) legal.add("challenge");
        return List.copyOf(legal);
    }

    public void apply(int seat, String action) {
        if (finished() || seat != current || !alive[seat])
            throw new RuleViolation("error.not-your-turn", "Not your turn");
        if (action.equals("challenge")) {
            if (!controls(seat).contains(action))
                throw new RuleViolation("error.cards.action", "There is no group to challenge");
            boolean truthful =
                    group.stream().allMatch(c -> c.rank() == tableRank || c.suit().equals("joker"));
            int loser = truthful ? seat : previous;
            revealed = group.stream().map(PlayingCard::piece).toList();
            challengedSeat = previous;
            challengedRank = tableRank;
            if (++attempts[loser] == chambers[loser]) {
                alive[loser] = false;
                hands.get(loser).clear();
            }
            if (java.util.stream.IntStream.range(0, playerCount()).filter(i -> alive[i]).count()
                    == 1) {
                current = nextAlive(loser);
                result = "winner:" + current;
            } else deal(loser);
            return;
        }
        if (!action.startsWith("play:"))
            throw new RuleViolation("error.cards.action", "Invalid card action");
        var ids = Arrays.asList(action.substring(5).split(","));
        selectionAction(seat, ids);
        group = List.copyOf(PlayingCard.selected(hands.get(seat), ids));
        hands.get(seat).removeAll(group);
        previous = seat;
        revealed = List.of();
        challengedSeat = -1;
        current = nextAlive(seat);
        while (hands.get(current).isEmpty()) current = nextAlive(current);
    }

    private int nextAlive(int seat) {
        do {
            seat = (seat + 1) % playerCount();
        } while (!alive[seat]);
        return seat;
    }

    public Map<String, String> publicInfo() {
        var info = new LinkedHashMap<String, String>();
        info.put("phase", finished() ? "finished" : "playing");
        info.put("round", Integer.toString(round));
        info.put("declaration", new PlayingCard("declaration", "spades", tableRank).face());
        info.put("previous", Integer.toString(previous));
        info.put("count", Integer.toString(group.size()));
        if (challengedSeat >= 0)
            info.put(
                    "challengedRank",
                    new PlayingCard("declaration", "spades", challengedRank).face());
        for (int seat = 0; seat < playerCount(); seat++) {
            info.put("alive." + seat, Boolean.toString(alive[seat]));
            info.put("attempts." + seat, Integer.toString(attempts[seat]));
        }
        return Map.copyOf(info);
    }
}
