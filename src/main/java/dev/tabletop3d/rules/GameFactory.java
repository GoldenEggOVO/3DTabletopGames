package dev.tabletop3d.rules;

import java.util.Locale;
import java.util.Map;

public final class GameFactory {
    private GameFactory() { }
    public static BoardGame create(String game, int players, long seed) {
        return create(game,players,seed,Map.of());
    }
    public static BoardGame create(String game, int players, long seed,Map<String,String> options) {
        if (game == null) throw new IllegalArgumentException("游戏类型不能为空");
        game=game.toLowerCase(Locale.ROOT);
        options=GameOptions.validate(game,options);
        return switch (game.toLowerCase(Locale.ROOT)) {
            case "mahjong" -> new MahjongGame(players,seed,options);
            case "lastcard" -> new LastCardGame(players,seed,options.getOrDefault("finish","first").equals("all"));
            case "ludo" -> new LudoGame(players,seed,GameOptions.ludo(options));
            case "connectfour" -> { requireTwo(players); yield new ConnectFourGame(); }
            case "gomoku" -> { requireTwo(players); yield new GomokuGame(new GomokuOptions(options.getOrDefault("double-three","allow").equals("forbid"),options.getOrDefault("double-four","allow").equals("forbid"),options.getOrDefault("overline","allow").equals("forbid"))); }
            case "xiangqi" -> { requireTwo(players); yield new XiangqiGame(); }
            case "chess" -> { requireTwo(players); yield new ChessGame(); }
            case "draughts" -> { requireTwo(players); yield new DraughtsGame(); }
            case "reversi" -> { requireTwo(players); yield new ReversiGame(); }
            case "go", "go19" -> { requireTwo(players); yield new GoGame(19); }
            case "go9" -> { requireTwo(players); yield new GoGame(9); }
            case "go13" -> { requireTwo(players); yield new GoGame(13); }
            case "yacht" -> new YachtGame(players,seed);
            case "aeroplane", "flying" -> new AeroplaneGame(players, seed);
            case "checkers", "chinese-checkers" -> new ChineseCheckersGame(players,new ChineseCheckersOptions(!options.getOrDefault("jump-own","allow").equals("forbid"),!options.getOrDefault("other-camps","allow").equals("forbid"),options.getOrDefault("finish","first").equals("all")));
            default -> throw new IllegalArgumentException("未知游戏：" + game);
        };
    }
    private static void requireTwo(int players) {
        if (players != 2) throw new IllegalArgumentException("此游戏需要两人");
    }
}
