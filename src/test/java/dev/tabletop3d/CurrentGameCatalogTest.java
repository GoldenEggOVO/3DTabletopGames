package dev.tabletop3d;

import dev.tabletop3d.rules.GameFactory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurrentGameCatalogTest {
    @Test
    void everyMenuGameUsesItsCanonicalRuleIdentityAndCapacity() {
        assertEquals(17, Tabletop3D.GAMES.size());
        for (String kind : Tabletop3D.GAMES) {
            int capacity = Tabletop3D.defaultCapacity(kind);
            assertTrue(Tabletop3D.capacityValid(kind, capacity), kind);
            var game = GameFactory.create(kind, capacity, 123);
            assertEquals(kind, game.id());
            assertEquals(capacity, game.playerCount());
        }
    }

    @Test
    void retiredGamesAndAliasesCannotEnterThroughTheRuleFactory() {
        for (String kind : List.of("aeroplane", "flying", "uno", "lastcard", "go19", "chinese-checkers")) {
            assertFalse(Tabletop3D.GAMES.contains(kind));
            assertThrows(IllegalArgumentException.class, () -> GameFactory.create(kind, 2, 123), kind);
        }
    }
}
