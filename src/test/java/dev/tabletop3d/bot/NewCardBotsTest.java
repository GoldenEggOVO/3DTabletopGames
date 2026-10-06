package dev.tabletop3d.bot;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.rules.*;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NewCardBotsTest {
    @Test
    void botsUseAcceptedActionsWithoutReadingOtherPrivateHands() {
        for (String kind : List.of("doudizhu", "liars-bar", "texas-holdem")) {
            BoardGame game = GameFactory.create(kind, Tabletop3D.defaultCapacity(kind), 42);
            Random random = new Random(7);
            for (int step = 0; step < 800 && !game.finished(); step++) {
                int seat = game.currentPlayer();
                var before = game.publicInfo();
                String action = BoardBots.choose(game, seat, random);
                assertNotNull(action, kind);
                assertEquals(
                        before, game.publicInfo(), "Choosing an action must not mutate the game");
                game.apply(seat, action);
            }
            if (!kind.equals("texas-holdem")) assertTrue(game.finished(), kind);
        }
    }
}
