package dev.tabletop3d.rules;

import java.util.List;

public final class GameCatalog {
    private record Game(String id, int defaultPlayers, List<Integer> playerCounts) {}

    private static final List<Game> GAMES = List.of(
            new Game("xiangqi", 2, List.of(2)),
            new Game("gomoku", 2, List.of(2)),
            new Game("chess", 2, List.of(2)),
            new Game("ludo", 4, List.of(2, 3, 4)),
            new Game("checkers", 6, List.of(2, 3, 4, 6)),
            new Game("draughts", 2, List.of(2)),
            new Game("reversi", 2, List.of(2)),
            new Game("go", 2, List.of(2)),
            new Game("go9", 2, List.of(2)),
            new Game("go13", 2, List.of(2)),
            new Game("connectfour", 2, List.of(2)),
            new Game("color-eight", 4, List.of(2, 3, 4, 5)),
            new Game("mahjong", 4, List.of(4)),
            new Game("doudizhu", 3, List.of(3)),
            new Game("liars-bar", 4, List.of(2, 3, 4)),
            new Game("texas-holdem", 6, List.of(2, 3, 4, 5, 6)),
            new Game("yacht", 2, List.of(2, 3, 4)));

    public static final List<String> IDS = GAMES.stream().map(Game::id).toList();

    private GameCatalog() {}

    public static int defaultCapacity(String kind) {
        for (Game game : GAMES) if (game.id().equals(kind)) return game.defaultPlayers();
        return 2;
    }

    public static List<Integer> playerCounts(String kind) {
        for (Game game : GAMES) if (game.id().equals(kind)) return game.playerCounts();
        return List.of();
    }

    public static boolean capacityValid(String kind, int players) {
        return playerCounts(kind).contains(players);
    }
}
