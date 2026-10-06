package dev.tabletop3d.rules.cards;

import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.RuleViolation;

import java.util.*;

/** Physical identity is opaque; rank and suit are disclosed only when a card is visible. */
public record PlayingCard(String id, String suit, int rank) {
    public static final List<String> SUITS = List.of("spades", "hearts", "diamonds", "clubs");

    public String face() {
        if (suit.equals("joker")) return "joker_" + (rank == 16 ? "small" : "big");
        return suit
                + "_"
                + switch (rank) {
                    case 11 -> "jack";
                    case 12 -> "queen";
                    case 13 -> "king";
                    case 14 -> "ace";
                    default -> Integer.toString(rank);
                };
    }

    public HandGame.Piece piece() {
        return new HandGame.Piece(id, face());
    }

    public int landlordRank() {
        return rank == 2 ? 15 : rank;
    }

    public static List<PlayingCard> deck(Random random, boolean jokers) {
        var cards = new ArrayList<PlayingCard>();
        for (String suit : SUITS)
            for (int rank = 2; rank <= 14; rank++)
                cards.add(new PlayingCard(identity(random), suit, rank));
        if (jokers)
            for (int rank = 16; rank <= 17; rank++)
                cards.add(new PlayingCard(identity(random), "joker", rank));
        Collections.shuffle(cards, random);
        return cards;
    }

    public static String identity(Random random) {
        return new UUID(random.nextLong(), random.nextLong()).toString();
    }

    public static List<PlayingCard> selected(List<PlayingCard> hand, List<String> ids) {
        if (ids.isEmpty() || new HashSet<>(ids).size() != ids.size())
            throw new RuleViolation(
                    "error.cards.selection", "Select distinct cards from your hand");
        var selected = new ArrayList<PlayingCard>();
        for (String id : ids)
            selected.add(
                    hand.stream()
                            .filter(c -> c.id.equals(id))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new RuleViolation(
                                                    "error.cards.selection",
                                                    "Selected card is no longer in your hand")));
        return selected;
    }
}
