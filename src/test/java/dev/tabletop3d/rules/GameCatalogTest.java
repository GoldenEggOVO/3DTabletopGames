package dev.tabletop3d.rules;



import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameCatalogTest {
    @Test
    void offeredPlayerCountsMatchTheRulesForEveryGame() {
        for (String kind : GameCatalog.IDS) {
            for (int players = 1; players <= 7; players++) {
                int count = players;
                if (GameCatalog.capacityValid(kind, count))
                    assertEquals(count, GameFactory.create(kind, count, 123).playerCount(), kind);
                else assertThrows(IllegalArgumentException.class,
                        () -> GameFactory.create(kind, count, 123), kind + " " + count);
            }
        }
    }

    @Test
    void everyMenuGameUsesItsCanonicalRuleIdentityAndCapacity() {
        assertEquals(17, GameCatalog.IDS.size());
        for (String kind : GameCatalog.IDS) {
            int capacity = GameCatalog.defaultCapacity(kind);
            assertTrue(GameCatalog.capacityValid(kind, capacity), kind);
            var game = GameFactory.create(kind, capacity, 123);
            assertEquals(kind, game.id());
            assertEquals(capacity, game.playerCount());
        }
    }

    @Test
    void retiredGamesAndAliasesCannotEnterThroughTheRuleFactory() {
        for (String kind : List.of("aeroplane", "flying", "uno", "lastcard", "go19", "chinese-checkers")) {
            assertFalse(GameCatalog.IDS.contains(kind));
            assertThrows(IllegalArgumentException.class, () -> GameFactory.create(kind, 2, 123), kind);
        }
    }
}
