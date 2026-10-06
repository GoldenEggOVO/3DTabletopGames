package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.tabletop3d.rules.mahjong.MahjongGame;

import org.junit.jupiter.api.Test;

import java.util.*;

class TurnPolicyTest {
    @Test
    void humanRiichiWinWaitsEvenAfterTheNormalTimeout() {
        var game = mock(MahjongGame.class);
        when(game.riichiDeclared(0)).thenReturn(true);
        for (String win : List.of("ron", "tsumo")) {
            when(game.legalActions(0)).thenReturn(List.of(win, "discard:draw"));
            assertNull(TurnPolicy.choose(game, 0, false, 120_000, 60_000, new Random(0)));
        }
    }

    @Test
    void automaticDrawUsesAShortDelayAndNeverChoosesAnOlderTile() {
        var game = mock(MahjongGame.class);
        when(game.riichiDeclared(0)).thenReturn(true);
        when(game.legalActions(0)).thenReturn(List.of("discard:draw"));
        when(game.riichiDrawDiscard(0)).thenReturn("discard:draw");
        assertNull(TurnPolicy.choose(game, 0, false, 0, 60_000, new Random(0)));
        assertEquals(
                "discard:draw", TurnPolicy.choose(game, 0, false, 1_000, 60_000, new Random(0)));
        assertNull(TurnPolicy.choose(game, 0, false, 1_000, 60_000, new Random(0), false));
    }
}
