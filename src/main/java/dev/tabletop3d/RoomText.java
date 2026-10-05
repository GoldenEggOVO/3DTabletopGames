package dev.tabletop3d;

import dev.tabletop3d.rules.ColorEightGame;
import dev.tabletop3d.rules.GameOptions;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared presentation for menus, chat and physical tables; never changes stored rule data. */
final class RoomText {
    private RoomText() {}

    static Component game(String kind) {
        return Tabletop3D.GAMES.contains(kind)
                ? Language.component("game." + kind)
                : Component.text(kind);
    }

    static Component name(Room room) {
        return Language.component(
                "room.name", "game", game(room.kind), "id", room.id.toString().substring(0, 6));
    }

    static Component option(String kind, GameOptions.Option option, Map<String, String> settings) {
        if (option.key().equals("first")) kind = "common";
        String value = option.value(settings);
        Component displayed =
                value.matches("[0-9]+") && !option.key().equals("rounds")
                        ? Component.text(value)
                        : value.equals("true") || value.equals("false")
                                ? Language.component("option.boolean." + value)
                                : Language.component(
                                        "option." + kind + "." + option.key() + "." + value);
        return Language.component(
                "setup.option",
                "rule",
                Language.component("option." + kind + "." + option.key()),
                "value",
                displayed);
    }

    static Component options(String kind, Map<String, String> settings) {
        var available = GameOptions.forGame(kind, settings);
        Component result = Component.empty();
        for (var option : available) {
            if (kind.equals("mahjong")
                    && !Set.of("profile", "rounds").contains(option.key())
                    && !settings.containsKey(option.key())) continue;
            if (!result.equals(Component.empty())) result = result.append(Component.newline());
            result = result.append(option(kind, option, settings));
        }
        return available.isEmpty()
                ? Language.component("setup.standard")
                : result;
    }

    static Component ranking(Room room) {
        if (room.board == null) return Component.empty();
        java.util.List<Integer> seats =
                room.board instanceof dev.tabletop3d.rules.LudoGame ludo
                        ? ludo.placements()
                        : room.board instanceof ColorEightGame cards
                                ? cards.placements()
                                : java.util.Arrays.stream(
                                                room.board
                                                        .publicInfo()
                                                        .getOrDefault("ranking", "")
                                                        .split(","))
                                        .filter(s -> !s.isBlank())
                                        .map(Integer::parseInt)
                                        .toList();
        Component result = Component.empty();
        for (int i = 0; i < seats.size(); i++) {
            int seat = seats.get(i);
            if (seat >= 0 && seat < room.seats.size())
                result =
                        result.append(Component.newline())
                                .append(
                                        Language.component(
                                                "room.ranking",
                                                "place",
                                                i + 1,
                                                "player",
                                                player(room.seats.get(seat), seat + 1)));
        }
        return result;
    }

    static Component scores(Room room) {
        if (!Set.of("mahjong","doudizhu").contains(room.kind) || room.board == null) return Component.empty();
        var info = room.board.publicInfo();
        Component result = Component.empty();
        for (int seat = 0; seat < room.seats.size(); seat++)
            if (info.containsKey("score." + seat))
                result =
                        result.append(Component.newline())
                                .append(
                                        Language.component(
                                                "hand.score",
                                                "player",
                                                player(room.seats.get(seat), seat + 1),
                                                "points",
                                                info.get("score." + seat)));
        return result;
    }

    static Component player(Room.Seat seat, int number) {
        return seat.bot()
                ? Language.component("room.bot", "number", number)
                : Component.text(seat.name());
    }

    static Component phase(Room room) {
        return Language.component("status." + room.phase.name().toLowerCase(java.util.Locale.ROOT));
    }

    static Component roster(List<Room.Seat> seats, int capacity) {
        Component names = Component.empty();
        for (int i = 0; i < seats.size(); i++) {
            if (i > 0) names = names.append(Language.component("room.roster.separator"));
            names = names.append(player(seats.get(i), i + 1));
        }
        return rosterNames(
                seats.isEmpty() ? Language.component("room.roster.empty") : names,
                seats.size(),
                capacity);
    }

    static Component rosterNames(Component names, int size, int capacity) {
        return Language.component(
                "room.roster",
                "players",
                names,
                "empty",
                Math.max(0, capacity - size),
                "capacity",
                capacity);
    }

    static Component outcome(Room room, String outcome) {
        if (outcome.startsWith("winners:")) {
            Component names=Component.empty();
            for(String value:outcome.substring(8).split(",")) {
                int seat=Integer.parseInt(value);
                if(!names.equals(Component.empty())) names=names.append(Language.component("room.roster.separator"));
                names=names.append(player(room.seats.get(seat),seat+1));
            }
            return Language.component("result.team-winners","players",names);
        }
        if (outcome.startsWith("winner:")) {
            try {
                int seat = Integer.parseInt(outcome.substring(7));
                return Language.component(
                        "result.winner", "player", player(room.seats.get(seat), seat + 1));
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
            }
        }
        return outcome.startsWith("draw:")
                ? Language.component(
                        "result.draw",
                        "reason",
                        Language.component("result.reason." + outcome.substring(5)))
                : Language.component(outcome);
    }
}
