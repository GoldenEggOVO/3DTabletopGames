package dev.tabletop3d.render.mahjong;

import dev.tabletop3d.room.TurnPolicy;
import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.mahjong.MahjongGame;

import java.util.*;

/** Optional assistance for one player's current table; game rules remain authoritative. */
public final class MahjongAssist {
    public enum Option {
        SORT("sort"), WIN("win"), NO_CALLS("no-calls"), DRAW_DISCARD("draw-discard");

        public final String id;

        Option(String id) {
            this.id = id;
        }
    }

    private final EnumSet<Option> enabled = EnumSet.of(Option.SORT);

    public boolean enabled(Option option) {
        return enabled.contains(option);
    }

    public boolean toggle(String id) {
        for (Option option : Option.values()) {
            if (!option.id.equals(id)) continue;
            if (!enabled.remove(option)) enabled.add(option);
            return true;
        }
        return false;
    }

    public List<HandGame.Piece> arrange(List<HandGame.Piece> hand, Collection<String> previous) {
        if (enabled(Option.SORT)) return hand;
        Map<String, Integer> positions = new HashMap<>();
        for (String id : previous) positions.put(id, positions.size());
        return hand.stream()
                .sorted(Comparator.comparingInt(tile -> positions.getOrDefault(tile.id(), Integer.MAX_VALUE)))
                .toList();
    }

    public List<String> visibleActions(List<String> legal) {
        return enabled(Option.NO_CALLS)
                ? legal.stream().filter(action -> !isCall(action)).toList()
                : legal;
    }

    public String choose(MahjongGame game, int seat, long elapsed) {
        if (elapsed < TurnPolicy.RIICHI_DISCARD_DELAY_MILLIS) return null;
        List<String> legal = game.legalActions(seat);
        for (String win : List.of("ron", "tsumo"))
            if (legal.contains(win)) return enabled(Option.WIN) ? win : null;
        if (enabled(Option.NO_CALLS) && legal.contains("pass") && legal.stream().anyMatch(MahjongAssist::isCall))
            return "pass";
        if (enabled(Option.DRAW_DISCARD)) {
            String discard = game.drawDiscard(seat);
            if (discard != null && legal.contains(discard)) return discard;
        }
        return null;
    }

    private static boolean isCall(String action) {
        return action.startsWith("chi:") || action.startsWith("pon:") || action.startsWith("kan-open:");
    }
}
