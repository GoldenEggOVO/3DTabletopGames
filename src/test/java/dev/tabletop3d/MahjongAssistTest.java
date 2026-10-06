package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.mahjong.MahjongGame;
import org.junit.jupiter.api.Test;
import java.util.*;

class MahjongAssistTest {
    @Test
    void onlySortingIsEnabledInitiallyAndNewTilesKeepTheRightmostPositionWhenDisabled() {
        var assistance = new MahjongAssist();
        assertTrue(assistance.enabled(MahjongAssist.Option.SORT));
        assertFalse(assistance.enabled(MahjongAssist.Option.WIN));
        assertFalse(assistance.enabled(MahjongAssist.Option.NO_CALLS));
        assertFalse(assistance.enabled(MahjongAssist.Option.DRAW_DISCARD));
        var hand = List.of(new HandGame.Piece("new", "m1"), new HandGame.Piece("b", "p1"), new HandGame.Piece("a", "s1"));
        assistance.toggle("sort");
        assertEquals(List.of("a", "b", "new"), assistance.arrange(hand, List.of("a", "b")).stream().map(HandGame.Piece::id).toList());
        assistance.toggle("sort");
        assertSame(hand, assistance.arrange(hand, List.of("a", "b")));
        assertFalse(assistance.toggle("invalid"));
    }

    @Test
    void noCallsSuppressesOnlyOpponentMeldsAndPreservesLegalWinsAndConcealedKans() {
        var assistance = new MahjongAssist();
        assistance.toggle("no-calls");
        List<String> legal = List.of("chi:a,b", "pon:a,b", "kan-open:a", "ron", "kan-closed:a", "kan-added:b", "pass");
        assertEquals(List.of("ron", "kan-closed:a", "kan-added:b", "pass"), assistance.visibleActions(legal));
        var game = mock(MahjongGame.class);
        when(game.legalActions(0)).thenReturn(List.of("pon:a,b", "pass"));
        assertNull(assistance.choose(game, 0, 0));
        assertEquals("pass", assistance.choose(game, 0, 1000));
        when(game.legalActions(0)).thenReturn(List.of("ron", "pon:a,b", "pass"));
        assertNull(assistance.choose(game, 0, 1000));
    }

    @Test
    void autoWinHasPriorityOverDrawDiscardAndNeverBypassesLegalActions() {
        var assistance = new MahjongAssist();
        assistance.toggle("draw-discard");
        var game = mock(MahjongGame.class);
        when(game.drawDiscard(0)).thenReturn("discard:new");
        when(game.legalActions(0)).thenReturn(List.of("tsumo", "discard:new"));
        assertNull(TurnPolicy.choose(game, 0, false, 120000, 60000, new Random(0), true, assistance));
        assistance.toggle("win");
        assertEquals("tsumo", assistance.choose(game, 0, 1000));
        assertNull(assistance.choose(game, 0, 0));
        when(game.legalActions(0)).thenReturn(List.of("discard:old", "discard:new"));
        assertEquals("discard:new", assistance.choose(game, 0, 1000));
        when(game.legalActions(0)).thenReturn(List.of("discard:old"));
        assertNull(assistance.choose(game, 0, 1000));
        assertNull(TurnPolicy.choose(game, 0, false, 1000, 60000, new Random(0), false, assistance));
    }
}
