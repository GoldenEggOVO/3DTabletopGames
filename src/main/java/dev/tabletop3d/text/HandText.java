package dev.tabletop3d.text;

import dev.tabletop3d.room.Room;
import dev.tabletop3d.rules.HandGame;

import net.kyori.adventure.text.Component;

import java.util.*;

public final class HandText {
    public static Component status(String kind, HandGame game) {
        var info = game.publicInfo();
        if (kind.equals("color-eight")) {
            Component top =
                    game.cells().stream()
                            .filter(c -> c.id().equals("discard") && !c.piece().isEmpty())
                            .findFirst()
                            .map(c -> piece(kind, c.piece()))
                            .orElseGet(() -> Language.component("hand.no-discard"));
            return Language.component(
                    "hand.color-eight-state",
                    "top",
                    top,
                    "color",
                    color(info.getOrDefault("color", "")),
                    "direction",
                    Language.component(
                            "hand.direction."
                                    + (info.getOrDefault("direction", "").equals("Clockwise")
                                            ? "clockwise"
                                            : "counterclockwise")),
                    "penalty",
                    info.getOrDefault("drawPenalty", "0"),
                    "remaining",
                    game.deckSize());
        }
        Component result =
                Language.component(
                        "hand.mahjong-state",
                        "phase",
                        Language.component("hand.phase." + info.getOrDefault("phase", "TURN")),
                        "round",
                        info.getOrDefault("round", "1"),
                        "remaining",
                        game.deckSize());
        String dora = info.getOrDefault("dora", "");
        if (!dora.isBlank())
            result =
                    result.append(Component.newline())
                            .append(
                                    Language.component(
                                            "hand.public.dora", "tiles", faces(kind, dora)));
        String patterns = info.getOrDefault("winningPatterns", "");
        if (!patterns.isBlank()) {
            Component names = Component.empty();
            for (String pattern : patterns.split(",")) {
                if (!names.equals(Component.empty())) names = names.append(Component.text(" · "));
                names = names.append(Language.component("mahjong.pattern." + pattern));
            }
            result =
                    result.append(Component.newline())
                            .append(Language.component("hand.winning-patterns", "patterns", names));
            if (info.containsKey("han"))
                result =
                        result.append(Component.space())
                                .append(
                                        Language.component(
                                                "hand.riichi-score",
                                                "han",
                                                info.get("han"),
                                                "fu",
                                                info.getOrDefault("fu", "0")));
        }
        return result;
    }

    public static Component tableHint(String kind, HandGame game) {
        var info = game.publicInfo();
        if (kind.equals("color-eight"))
            return Language.component(
                    "hand.color-eight-table", "color", color(info.getOrDefault("color", "")));
        return Language.component("hand.phase." + info.getOrDefault("phase", "TURN"));
    }

    private static Component color(String color) {
        return Language.component(color.isEmpty() ? "card.color.any" : "card.color." + color);
    }

    private static Component faces(String kind, String faces) {
        Component result = Component.empty();
        for (String face : faces.split(",")) {
            if (!result.equals(Component.empty())) result = result.append(Component.text(" · "));
            result = result.append(piece(kind, face.trim()));
        }
        return result;
    }

    public static Component piece(String kind, String face) {
        if (face.equals("back")) return Language.component("tile.concealed");
        if (kind.equals("color-eight")) {
            if (face.equals("wild")) return Language.component("card.wild");
            if (face.equals("swap")) return Language.component("card.swap");
            return Language.component(
                    "card.face",
                    "color",
                    Language.component("card.color." + face.charAt(0)),
                    "rank",
                    Language.component("card.rank." + face.substring(1)));
        }
        if (face.length() < 2) return Component.text(face);
        char suit = face.charAt(0);
        String rank = face.substring(1);
        if (suit == 'z') return Language.component("tile.honor." + rank);
        if (suit == 'f') return Language.component("tile.flower", "number", rank);
        return Language.component(
                "tile.face",
                "rank",
                rank.equals("0") ? Language.component("tile.red-five") : Component.text(rank),
                "suit",
                Language.component("tile.suit." + suit));
    }

    public static Component action(Room room, int seat, String action) {
        String[] parts = action.split(":");
        Component tiles = Component.empty();
        if (parts.length > 1) {
            Map<String, String> hand = new HashMap<>();
            ((HandGame) room.board).hand(seat).forEach(p -> hand.put(p.id(), p.face()));
            for (String id : parts[1].split(","))
                if (hand.containsKey(id)) {
                    if (!tiles.equals(Component.empty()))
                        tiles = tiles.append(Component.text(", "));
                    tiles = tiles.append(piece(room.kind, hand.get(id)));
                }
        }
        Component choice =
                parts.length > 1 && parts[0].equals("missing")
                        ? Language.component("tile.suit." + parts[1])
                        : Component.text(parts.length > 1 ? parts[1] : "");
        Component result =
                Language.component("hand.action." + parts[0], "tiles", tiles, "choice", choice);
        if (parts.length == 3 && parts[0].equals("play"))
            result =
                    result.append(
                            Language.component(
                                    "hand.choose-color",
                                    "color",
                                    Language.component("card.color." + parts[2])));
        return result;
    }

    private HandText() {}
}
