package dev.tabletop3d.rules;

import dev.tabletop3d.rules.chess.ChessGame;
import dev.tabletop3d.rules.chinesecheckers.ChineseCheckersGame;
import dev.tabletop3d.rules.chinesecheckers.ChineseCheckersOptions;
import dev.tabletop3d.rules.coloreight.ColorEightGame;
import dev.tabletop3d.rules.connectfour.ConnectFourGame;
import dev.tabletop3d.rules.doudizhu.DoudizhuGame;
import dev.tabletop3d.rules.draughts.DraughtsGame;
import dev.tabletop3d.rules.go.GoGame;
import dev.tabletop3d.rules.gomoku.GomokuGame;
import dev.tabletop3d.rules.gomoku.GomokuOptions;
import dev.tabletop3d.rules.liarsbar.LiarsBarGame;
import dev.tabletop3d.rules.ludo.LudoGame;
import dev.tabletop3d.rules.mahjong.MahjongGame;
import dev.tabletop3d.rules.reversi.ReversiGame;
import dev.tabletop3d.rules.texasholdem.TexasHoldemGame;
import dev.tabletop3d.rules.xiangqi.XiangqiGame;
import dev.tabletop3d.rules.yacht.YachtGame;

import java.util.Locale;
import java.util.Map;

public final class GameFactory {
    private GameFactory() {}

    public static BoardGame create(String game, int players, long seed) {
        return create(game, players, seed, Map.of());
    }

    public static BoardGame create(
            String game, int players, long seed, Map<String, String> options) {
        if (game == null) throw new RuleViolation("error.game-type-cannot-be-empty", "Game type cannot be empty");
        game = game.toLowerCase(Locale.ROOT);
        options = GameOptions.validate(game, options);
        return switch (game) {
            case "mahjong" -> new MahjongGame(players, seed, options);
            case "doudizhu" -> new DoudizhuGame(players, seed);
            case "liars-bar" -> new LiarsBarGame(players, seed);
            case "texas-holdem" -> new TexasHoldemGame(players, seed);
            case "color-eight" -> new ColorEightGame(players, seed);
            case "ludo" -> new LudoGame(players, seed, GameOptions.ludo(options));
            case "connectfour" -> {
                requireTwo(players);
                yield new ConnectFourGame();
            }
            case "gomoku" -> {
                requireTwo(players);
                yield new GomokuGame(
                        new GomokuOptions(
                                options.getOrDefault("double-three", "allow").equals("forbid"),
                                options.getOrDefault("double-four", "allow").equals("forbid"),
                                options.getOrDefault("overline", "allow").equals("forbid")));
            }
            case "xiangqi" -> {
                requireTwo(players);
                yield new XiangqiGame();
            }
            case "chess" -> {
                requireTwo(players);
                yield new ChessGame();
            }
            case "draughts" -> {
                requireTwo(players);
                yield new DraughtsGame();
            }
            case "reversi" -> {
                requireTwo(players);
                yield new ReversiGame();
            }
            case "go" -> {
                requireTwo(players);
                yield new GoGame(19);
            }
            case "go9" -> {
                requireTwo(players);
                yield new GoGame(9);
            }
            case "go13" -> {
                requireTwo(players);
                yield new GoGame(13);
            }
            case "yacht" -> new YachtGame(players, seed);
            case "checkers" ->
                    new ChineseCheckersGame(
                            players,
                            new ChineseCheckersOptions(
                                    !options.getOrDefault("jump-own", "allow").equals("forbid"),
                                    !options.getOrDefault("other-camps", "allow").equals("forbid"),
                                    options.getOrDefault("finish", "first").equals("all")));
            default ->
                    throw new RuleViolation(
                            "error.game.unknown", "Unknown game: " + game, "game", game);
        };
    }

    private static void requireTwo(int players) {
        if (players != 2)
            throw new RuleViolation("error.this-game-requires-two-players", "This game requires two players");
    }
}
