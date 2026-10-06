package dev.tabletop3d.text;

import dev.tabletop3d.room.Room;

import net.kyori.adventure.text.Component;

import java.util.*;

/** Locale-neutral card data is translated only at the presentation boundary. */
public final class PlayingCardText {
    private PlayingCardText() {}

    static Component face(String face) {
        if (face.startsWith("joker_")) return Language.component("playing-card." + face);
        String[] parts = face.split("_");
        return Language.component(
                "playing-card.face",
                "rank",
                Language.component("playing-card.rank." + parts[1]),
                "suit",
                Language.component("playing-card.suit." + parts[0]));
    }

    public static Component control(String action) {
        if (action.equals("bid:0")) return Language.component("cards.control.bid-pass");
        if (action.startsWith("bid:"))
            return Language.component("cards.control.bid", "points", action.substring(4));
        return Language.component("cards.control." + action);
    }

    public static Component status(Room room) {
        var info = room.board.publicInfo();
        return switch (room.kind) {
            case "doudizhu" ->
                    Language.component(
                            "cards.doudizhu.status",
                            "phase",
                            Language.component("cards.phase." + info.get("phase")),
                            "bid",
                            info.get("bid"),
                            "multiplier",
                            info.get("multiplier"));
            case "liars-bar" ->
                    Language.component(
                            "cards.liars-bar.status",
                            "rank",
                            face(info.get("declaration")),
                            "round",
                            info.get("round"));
            case "texas-holdem" ->
                    Language.component(
                            "cards.poker.status",
                            "phase",
                            Language.component("cards.phase." + info.get("phase")),
                            "pot",
                            info.get("pot"),
                            "bet",
                            info.get("currentBet"),
                            "hand",
                            info.get("hand"));
            default -> throw new IllegalArgumentException("Unknown playing-card game");
        };
    }

    public static Component seat(Room room, int seat) {
        var info = room.board.publicInfo();
        var label =
                Language.component(
                        "cards.seat", "player", RoomText.player(room.seats.get(seat), seat + 1));
        if (room.kind.equals("texas-holdem"))
            return label.append(Component.newline())
                    .append(
                            Language.component(
                                    "cards.poker.stack",
                                    "chips",
                                    info.get("chips." + seat),
                                    "bet",
                                    info.get("bet." + seat)))
                    .append(
                            info.get("dealer").equals(Integer.toString(seat))
                                    ? Language.component("cards.poker.dealer")
                                    : Component.empty())
                    .append(
                            info.get("smallBlind").equals(Integer.toString(seat))
                                    ? Language.component("cards.poker.small-blind")
                                    : Component.empty())
                    .append(
                            info.get("bigBlind").equals(Integer.toString(seat))
                                    ? Language.component("cards.poker.big-blind")
                                    : Component.empty())
                    .append(
                            info.get("phase").equals("showdown")
                                            && !info.get("award." + seat).equals("0")
                                    ? Language.component(
                                            "cards.poker.award", "chips", info.get("award." + seat))
                                    : Component.empty());
        if (room.kind.equals("liars-bar"))
            return label.append(Component.newline())
                    .append(
                            Language.component(
                                    info.get("alive." + seat).equals("true")
                                            ? "cards.liars-bar.attempts"
                                            : "cards.liars-bar.eliminated",
                                    "count",
                                    info.get("attempts." + seat)));
        return label.append(
                info.get("landlord").equals(Integer.toString(seat))
                        ? Language.component("cards.doudizhu.landlord")
                        : Component.empty());
    }
}
