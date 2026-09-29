package dev.tabletop3d.rules;

import java.util.List;

/** Private hands are fetched only for their seated owner; public renderers use counts. */
public interface HandGame extends BoardGame {
    record Piece(String id,String face) {}
    List<Piece> hand(int seat);
    List<Piece> discards(int seat);
    List<Piece> exposed(int seat);
    int handSize(int seat);
    int deckSize();
}
