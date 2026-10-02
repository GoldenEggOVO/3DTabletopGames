package dev.tabletop3d;


import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Completion for public commands; internal dialog callback tokens are never exposed. */
final class CommandSuggestions {
    static void hideDuplicateRoot(Collection<String> commands) {
        commands.remove("3dtabletop:3dtabletop");
    }

    static List<String> complete(
            String[] args, Collection<Room> rooms, Room own, boolean console, boolean authorized) {
        if (args.length == 0) return List.of();
        if (console) return args.length == 1 ? filter(Stream.of("status"), args[0]) : List.of();
        if (!authorized) return List.of();
        if (args.length == 1) {
            Stream<String> base = Stream.of("menu", "create", "join", "rules");
            if (own != null) base = Stream.concat(base, Stream.of("resume", "leave"));
            if (own != null && own.phase == Room.Phase.LOBBY)
                base = Stream.concat(base, Stream.of("ready", "bots"));
            if (own != null && own.phase == Room.Phase.PLAYING)
                base = Stream.concat(base, Stream.of("move"));
            if (own != null && own.phase == Room.Phase.FINISHED)
                base = Stream.concat(base, Stream.of("rematch"));
            return filter(base, args[0]);
        }
        if (args.length == 2)
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create", "rules" -> filter(Tabletop3D.GAMES.stream(), args[1]);
                case "join" ->
                        filter(
                                rooms.stream()
                                        .filter(
                                                r ->
                                                        r.phase == Room.Phase.LOBBY
                                                                && r.seats.size() < r.capacity)
                                        .map(r -> r.id.toString()),
                                args[1]);
                case "move" ->
                        own == null || own.board == null || own.phase != Room.Phase.PLAYING
                                ? List.of()
                                : filter(
                                        own.board.legalActions(own.board.currentPlayer()).stream(),
                                        args[1]);
                default -> List.of();
            };
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            Stream<String> sizes =
                    switch (args[1].toLowerCase(Locale.ROOT)) {
                        case "checkers" -> Stream.of("2", "3", "4", "6");
                        case "ludo" -> Stream.of("2", "3", "4");
                        case "color-eight" -> Stream.of("2", "3", "4", "5");
                        case "mahjong" -> Stream.of("4");
                        case "gomoku",
                                "xiangqi",
                                "chess",
                                "draughts",
                                "reversi",
                                "go",
                                "go9",
                                "go13",
                                "connectfour" ->
                                Stream.of("2");
                        default -> Stream.empty();
                    };
            return filter(sizes, args[2]);
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> values, String prefix) {
        String typed = prefix.toLowerCase(Locale.ROOT);
        return values.filter(value -> value.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }

    private CommandSuggestions() {}
}
