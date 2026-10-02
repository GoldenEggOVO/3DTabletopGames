package dev.tabletop3d;

import dev.tabletop3d.rules.*;

import java.util.Random;

/** Chooses automatic actions; submitting and recording them stays in the normal action path. */
final class TurnPolicy {
    static final long RIICHI_DISCARD_DELAY_MILLIS = 600;

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
