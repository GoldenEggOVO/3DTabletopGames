package dev.tabletop3d.room;

import dev.tabletop3d.bot.BoardBots;
import dev.tabletop3d.render.mahjong.MahjongAssist;
import dev.tabletop3d.rules.*;
import dev.tabletop3d.rules.coloreight.ColorEightGame;
import dev.tabletop3d.rules.go.GoGame;
import dev.tabletop3d.rules.mahjong.MahjongGame;

import java.util.Random;

/** Chooses automatic actions; submitting and recording them stays in the normal action path. */
public final class TurnPolicy {
    public static final long RIICHI_DISCARD_DELAY_MILLIS = 600;

    static String choose(
            BoardGame game, int seat, boolean bot, long elapsed, long timeout, Random random) {
        return choose(game, seat, bot, elapsed, timeout, random, true);
    }

    static String choose(
            BoardGame game,
            int seat,
            boolean bot,
            long elapsed,
            long timeout,
            Random random,
            boolean present) {
        return choose(game, seat, bot, elapsed, timeout, random, present, null);
    }

    public static String choose(BoardGame game, int seat, boolean bot, long elapsed, long timeout,
            Random random, boolean present, MahjongAssist assistance) {
        if (!bot && present && game instanceof MahjongGame mahjong && assistance != null) {
            String choice = assistance.choose(mahjong, seat, elapsed);
            if (choice != null) return choice;
            if (assistance.enabled(MahjongAssist.Option.DRAW_DISCARD)
                    && mahjong.legalActions(seat).stream().anyMatch(action -> action.equals("ron") || action.equals("tsumo")))
                return null;
        }
        if (!bot && game instanceof MahjongGame mahjong && mahjong.riichiDeclared(seat)) {
            var legal = game.legalActions(seat);
            if (legal.contains("ron") || legal.contains("tsumo")) return null;
            String discard = mahjong.riichiDrawDiscard(seat);
            if (discard != null && legal.contains(discard))
                return present && elapsed >= RIICHI_DISCARD_DELAY_MILLIS ? discard : null;
        }
        if (!bot && game instanceof GoGame go && go.scoring()) return null;
        if (elapsed < timeout) return null;
        return !bot && game instanceof ColorEightGame cards
                ? cards.timeoutAction()
                : BoardBots.choose(game, seat, random);
    }

    private TurnPolicy() {}
}
