package dev.tabletop3d.rules.texasholdem;

import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.rules.RuleViolation;
import dev.tabletop3d.rules.SelectedHandGame;
import dev.tabletop3d.rules.cards.PlayingCard;

import java.util.*;

public final class TexasHoldemGame implements SelectedHandGame {
    private final Random random;
    private final int[] chips, contributions, bets, lastActedBet;
    private final boolean[] folded, inHand;
    private final List<List<PlayingCard>> hands = new ArrayList<>();
    private final List<PlayingCard> community = new ArrayList<>();
    private List<PlayingCard> deck;
    private int dealer, current, currentBet, minRaise = 10, handNumber, smallBlind, bigBlind;
    private String phase = "preflop", result = "ongoing";
    private boolean reveal;
    private int[] awards;

    public TexasHoldemGame(int players, long seed) {
        this(players, seed, startingChips(players));
    }

    TexasHoldemGame(int players, long seed, int[] stacks) {
        if (players < 2
                || players > 6
                || stacks.length != players
                || Arrays.stream(stacks).anyMatch(n -> n <= 0))
            throw new RuleViolation(
                    "error.texas-holdem.players", "Texas Hold'em requires 2 to 6 funded players");
        random = new Random(seed);
        chips = stacks.clone();
        contributions = new int[players];
        bets = new int[players];
        lastActedBet = new int[players];
        folded = new boolean[players];
        inHand = new boolean[players];
        awards = new int[players];
        for (int i = 0; i < players; i++) hands.add(new ArrayList<>());
        startHand();
    }

    private static int[] startingChips(int players) {
        int[] chips = new int[Math.max(0, players)];
        Arrays.fill(chips, 1000);
        return chips;
    }

    private void startHand() {
        phase = "preflop";
        currentBet = 10;
        minRaise = 10;
        reveal = false;
        handNumber++;
        Arrays.fill(contributions, 0);
        Arrays.fill(bets, 0);
        Arrays.fill(lastActedBet, -1);
        Arrays.fill(awards, 0);
        community.clear();
        deck = PlayingCard.deck(random, false);
        for (int seat = 0; seat < playerCount(); seat++) {
            inHand[seat] = chips[seat] > 0;
            folded[seat] = !inHand[seat];
            hands.get(seat).clear();
        }
        for (int count = 0; count < 2; count++)
            for (int offset = 1; offset <= playerCount(); offset++) {
                int seat = (dealer + offset) % playerCount();
                if (inHand[seat]) hands.get(seat).add(draw());
            }
        int small = fundedCount() == 2 ? dealer : nextFunded(dealer), big = nextFunded(small);
        smallBlind = small;
        bigBlind = big;
        put(small, Math.min(5, chips[small]));
        put(big, Math.min(10, chips[big]));
        current = nextActing(big);
        progress();
    }

    private PlayingCard draw() {
        return deck.removeLast();
    }

    private int fundedCount() {
        return (int) Arrays.stream(chips).filter(n -> n > 0).count();
    }

    private int nextFunded(int seat) {
        do {
            seat = (seat + 1) % playerCount();
        } while (chips[seat] <= 0);
        return seat;
    }

    private int nextActing(int seat) {
        for (int offset = 1; offset <= playerCount(); offset++) {
            int next = (seat + offset) % playerCount();
            if (!folded[next] && chips[next] > 0) return next;
        }
        return -1;
    }

    private void put(int seat, int amount) {
        chips[seat] -= amount;
        bets[seat] += amount;
        contributions[seat] += amount;
    }

    public String id() {
        return "texas-holdem";
    }

    public int playerCount() {
        return chips.length;
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

    public int deckSize() {
        return deck.size();
    }

    public int handSize(int seat) {
        return hands.get(seat).size();
    }

    public List<Piece> hand(int seat) {
        return hands.get(seat).stream().map(PlayingCard::piece).toList();
    }

    public List<Piece> discards(int seat) {
        return List.of();
    }

    public List<Piece> exposed(int seat) {
        return reveal && !folded[seat] ? hand(seat) : List.of();
    }

    public List<Cell> cells() {
        return java.util.stream.IntStream.range(0, community.size())
                .mapToObj(i -> new Cell("community:" + i, i, 0, community.get(i).face(), -1))
                .toList();
    }

    public String selectionAction(int seat, List<String> ids) {
        throw new RuleViolation("error.cards.action", "Poker cards are not played from the hand");
    }

    private boolean raiseReopened(int seat) {
        return lastActedBet[seat] < 0 || currentBet - lastActedBet[seat] >= minRaise;
    }

    public List<String> controls(int seat) {
        if (finished() || seat != current) return List.of();
        if (phase.equals("showdown")) return List.of("continue");
        if (folded[seat] || chips[seat] == 0) return List.of();
        var actions = new ArrayList<String>();
        actions.add("fold");
        actions.add(bets[seat] == currentBet ? "check" : "call");
        int maximum = bets[seat] + chips[seat];
        boolean otherCanBet =
                java.util.stream.IntStream.range(0, playerCount())
                        .anyMatch(i -> i != seat && !folded[i] && chips[i] > 0);
        if (maximum > currentBet
                && otherCanBet
                && raiseReopened(seat)
                && maximum >= currentBet + minRaise) actions.add("raise");
        if (maximum <= currentBet || otherCanBet && raiseReopened(seat)) actions.add("all-in");
        return List.copyOf(actions);
    }

    public List<String> legalActions(int seat) {
        var actions = new ArrayList<>(controls(seat));
        if (actions.remove("raise")) actions.add("raise:" + (currentBet + minRaise));
        return List.copyOf(actions);
    }

    public void validateRaise(int seat, int target) {
        if (!controls(seat).contains("raise")
                || target < currentBet + minRaise
                || target > bets[seat] + chips[seat])
            throw new RuleViolation(
                    "error.poker.raise", "Raise is outside the current minimum or stack");
    }

    public void apply(int seat, String action) {
        if (finished() || seat != current)
            throw new RuleViolation("error.not-your-turn", "Not your turn");
        var controls = controls(seat);
        if (action.equals("continue") && controls.contains(action)) {
            dealer = nextFunded(dealer);
            startHand();
            return;
        }
        if (action.startsWith("raise:")) {
            int target;
            try {
                target = Integer.parseInt(action.substring(6));
            } catch (NumberFormatException ex) {
                throw new RuleViolation("error.poker.raise", "Enter a valid street total");
            }
            validateRaise(seat, target);
            raise(seat, target);
        } else {
            if (!controls.contains(action))
                throw new RuleViolation("error.cards.action", "Invalid poker action");
            switch (action) {
                case "fold" -> folded[seat] = true;
                case "check" -> lastActedBet[seat] = currentBet;
                case "call" -> {
                    put(seat, Math.min(chips[seat], currentBet - bets[seat]));
                    lastActedBet[seat] = currentBet;
                }
                case "all-in" -> {
                    int target = bets[seat] + chips[seat];
                    if (target > currentBet) raise(seat, target);
                    else {
                        put(seat, chips[seat]);
                        lastActedBet[seat] = currentBet;
                    }
                }
                default -> throw new RuleViolation("error.cards.action", "Invalid poker action");
            }
        }
        current = nextActing(seat);
        progress();
    }

    private void raise(int seat, int target) {
        int increase = target - currentBet;
        if (increase >= minRaise) minRaise = increase;
        put(seat, target - bets[seat]);
        currentBet = target;
        lastActedBet[seat] = target;
    }

    private void progress() {
        int live = 0, acting = 0;
        boolean complete = true;
        for (int seat = 0; seat < playerCount(); seat++)
            if (!folded[seat]) {
                live++;
                if (chips[seat] > 0) {
                    acting++;
                    if (lastActedBet[seat] < 0 || bets[seat] != currentBet) complete = false;
                }
            }
        if (live == 1) {
            settle(false);
            return;
        }

        if (acting <= 1) {
            int remaining = -1, opponentBet = 0;
            for (int seat = 0; seat < playerCount(); seat++)
                if (!folded[seat]) {
                    if (chips[seat] > 0) remaining = seat;
                    else opponentBet = Math.max(opponentBet, bets[seat]);
                }
            if (remaining < 0 || bets[remaining] >= opponentBet) complete = true;
            else currentBet = opponentBet;
        }
        if (!complete) return;
        if (phase.equals("river")) {
            settle(true);
            return;
        }
        Arrays.fill(bets, 0);
        Arrays.fill(lastActedBet, -1);
        currentBet = 0;
        minRaise = 10;
        draw();
        int count = phase.equals("preflop") ? 3 : 1;
        for (int i = 0; i < count; i++) community.add(draw());
        phase =
                switch (phase) {
                    case "preflop" -> "flop";
                    case "flop" -> "turn";
                    default -> "river";
                };
        current = nextActing(dealer);
        if (acting <= 1) progress();
    }

    private void settle(boolean showdown) {
        long[] strengths = new long[playerCount()];
        if (showdown)
            for (int seat = 0; seat < playerCount(); seat++)
                if (!folded[seat]) {
                    var cards = new ArrayList<>(hands.get(seat));
                    cards.addAll(community);
                    strengths[seat] = PokerHands.best(cards);
                }
        awards = PokerPots.awards(contributions, folded, strengths, dealer);
        for (int seat = 0; seat < playerCount(); seat++) chips[seat] += awards[seat];
        Arrays.fill(contributions, 0);
        reveal = showdown;
        phase = "showdown";
        if (fundedCount() == 1) {
            current = nextFunded(dealer);
            result = "winner:" + current;
        } else current = nextFunded(dealer);
    }

    public Map<String, String> publicInfo() {
        var info = new LinkedHashMap<String, String>();
        info.put("phase", phase);
        info.put("dealer", Integer.toString(dealer));
        info.put("hand", Integer.toString(handNumber));
        info.put("pot", Integer.toString(Arrays.stream(contributions).sum()));
        info.put("currentBet", Integer.toString(currentBet));
        info.put("minimumRaise", Integer.toString(currentBet + minRaise));
        info.put("smallBlind", Integer.toString(smallBlind));
        info.put("bigBlind", Integer.toString(bigBlind));
        for (int seat = 0; seat < playerCount(); seat++) {
            info.put("chips." + seat, Integer.toString(chips[seat]));
            info.put("bet." + seat, Integer.toString(bets[seat]));
            info.put("folded." + seat, Boolean.toString(folded[seat]));
            info.put("award." + seat, Integer.toString(awards[seat]));
        }
        return Map.copyOf(info);
    }
}
