package dev.tabletop3d.bot;

import dev.tabletop3d.rules.*;
import dev.tabletop3d.rules.doudizhu.DoudizhuGame;
import dev.tabletop3d.rules.go.GoGame;
import dev.tabletop3d.rules.liarsbar.LiarsBarGame;
import dev.tabletop3d.rules.mahjong.Tiles;
import dev.tabletop3d.rules.reversi.ReversiGame;
import dev.tabletop3d.rules.texasholdem.TexasHoldemGame;
import dev.tabletop3d.rules.yacht.YachtGame;

import java.util.*;

/** Small bounded casual heuristics; never simulate on the live rules engine. */
public final class BoardBots {
    public static String choose(BoardGame board, int seat, Random random) {
        List<String> legalActions = board.legalActions(seat);
        if (legalActions.isEmpty()) {
            return null;
        }
        if (board instanceof DoudizhuGame) {
            return chooseDoudizhuAction(legalActions);
        }
        if (board instanceof LiarsBarGame liar) {
            return chooseLiarsBarAction(liar, seat, legalActions, random);
        }
        if (board instanceof TexasHoldemGame) {
            return choosePokerAction(legalActions, random);
        }
        if (board instanceof HandGame hand) {
            for (String action : List.of("ron", "tsumo", "declare")) {
                if (legalActions.contains(action)) {
                    return action;
                }
            }
            if (board.id().equals("mahjong")) {
                String choice = chooseMahjongAction(hand, seat, legalActions, random);
                if (choice != null) {
                    return choice;
                }
            }
            List<String> plays =
                    legalActions.stream().filter(action -> action.startsWith("play:")).toList();
            if (!plays.isEmpty()) {
                return plays.get(random.nextInt(plays.size()));
            }
        }
        if (board instanceof YachtGame yacht) {
            return chooseYachtAction(yacht, legalActions);
        }
        if (board instanceof GoGame go) {
            return chooseGoAction(go, legalActions, random);
        }
        if (board instanceof ReversiGame) {
            List<String> corners =
                    legalActions.stream()
                            .filter(
                                    action ->
                                            Set.of(
                                                            "place:0,0",
                                                            "place:7,0",
                                                            "place:0,7",
                                                            "place:7,7")
                                                    .contains(action))
                            .toList();
            if (!corners.isEmpty()) {
                return corners.getFirst();
            }
        }
        return legalActions.get(random.nextInt(legalActions.size()));
    }

    private static String chooseDoudizhuAction(List<String> legalActions) {
        List<String> plays =
                legalActions.stream().filter(action -> action.startsWith("play:")).toList();
        if (!plays.isEmpty()) {
            return plays.stream()
                    .max(Comparator.comparingInt(action -> action.split(",").length))
                    .orElseThrow();
        }
        if (legalActions.contains("pass")) {
            return "pass";
        }
        return legalActions.stream()
                .filter(action -> !action.equals("bid:0"))
                .findFirst()
                .orElse("bid:0");
    }

    private static String chooseLiarsBarAction(
            LiarsBarGame liar, int seat, List<String> legalActions, Random random) {
        if (legalActions.contains("challenge") && random.nextInt(4) == 0) {
            return "challenge";
        }
        List<String> honest =
                liar.hand(seat).stream()
                        .filter(
                                card ->
                                        card.face().equals(liar.publicInfo().get("declaration"))
                                                || card.face().startsWith("joker_"))
                        .limit(3)
                        .map(HandGame.Piece::id)
                        .toList();
        if (!honest.isEmpty() && liar.controls(seat).contains("play")) {
            return liar.selectionAction(seat, honest);
        }
        return legalActions.get(random.nextInt(legalActions.size()));
    }

    private static String choosePokerAction(List<String> legalActions, Random random) {
        if (legalActions.contains("continue")) {
            return "continue";
        }
        if (legalActions.contains("check")) {
            return "check";
        }
        if (legalActions.contains("call") && random.nextInt(5) != 0) {
            return "call";
        }
        return "fold";
    }

    private static String chooseYachtAction(YachtGame yacht, List<String> legalActions) {
        if (yacht.rolls() == 0) {
            return "roll";
        }
        if (yacht.rolls() < 3) {
            int[] dice = yacht.dice();
            int[] counts = new int[7];
            for (int value : dice) {
                counts[value]++;
            }
            int mostFrequentFace = 1;
            for (int value = 2; value <= 6; value++) {
                if (counts[value] >= counts[mostFrequentFace]) {
                    mostFrequentFace = value;
                }
            }
            for (int dieIndex = 0; dieIndex < 5; dieIndex++) {
                if ((dice[dieIndex] == mostFrequentFace) != yacht.held(dieIndex)) {
                    return "hold:die" + dieIndex;
                }
            }
            if (legalActions.contains("roll")) {
                return "roll";
            }
        }
        return legalActions.stream()
                .filter(action -> action.startsWith("score:"))
                .max(
                        Comparator.comparingInt(
                                action ->
                                        YachtGame.score(
                                                YachtGame.CATEGORIES.indexOf(action.substring(6)),
                                                yacht.dice())))
                .orElseThrow();
    }

    private static String chooseGoAction(GoGame go, List<String> legalActions, Random random) {
        if (go.scoring()) {
            return legalActions.contains("accept") ? "accept" : "resume";
        }
        if (legalActions.size() == 1
                || go.cells().stream().filter(cell -> cell.owner() >= 0).count()
                        > go.size() * go.size() * .78) {
            return "pass";
        }
        List<String> placements =
                legalActions.stream().filter(action -> action.startsWith("place:")).toList();
        return placements.get(random.nextInt(placements.size()));
    }

    private static String chooseMahjongAction(
            HandGame game, int seat, List<String> legalActions, Random random) {
        if (legalActions.contains("exchange-confirm")) {
            return "exchange-confirm";
        }
        if (legalActions.contains("riichi")) {
            return "riichi";
        }
        List<String> missing =
                legalActions.stream().filter(action -> action.startsWith("missing:")).toList();
        List<String> candidates = List.of();
        for (String prefix : List.of("exchange-add:", "riichi:", "discard:")) {
            candidates = legalActions.stream().filter(action -> action.startsWith(prefix)).toList();
            if (!candidates.isEmpty()) {
                break;
            }
        }
        if (missing.isEmpty() && candidates.isEmpty()) {
            return null;
        }
        List<HandGame.Piece> hand = game.hand(seat);
        int[] counts = new int[34], suits = new int[3];
        Map<String, HandGame.Piece> pieces = new HashMap<>();
        for (var piece : hand) {
            int type = Tiles.type(piece.face());
            counts[type]++;
            if (type < 27) {
                suits[type / 9]++;
            }
            pieces.put(piece.id(), piece);
        }
        if (!missing.isEmpty()) {
            return lowestScore(missing, action -> suits["mps".indexOf(action.charAt(8))], random);
        }
        return lowestScore(
                candidates,
                action ->
                        tileRetentionScore(
                                pieces.get(action.substring(action.indexOf(':') + 1)), counts),
                random);
    }

    /** Keep pairs/triplets and nearby suited tiles; isolated honors and terminals leave first. */
    private static int tileRetentionScore(HandGame.Piece piece, int[] counts) {
        int type = Tiles.type(piece.face()), value = (counts[type] - 1) * 8;
        if (type < 27) {
            value += type % 9 == 0 || type % 9 == 8 ? 1 : 2;
            for (int distance : new int[] {-2, -1, 1, 2}) {
                int next = type + distance;
                if (next >= 0 && next < 27 && next / 9 == type / 9 && counts[next] > 0) {
                    value += Math.abs(distance) == 1 ? 4 : 2;
                }
            }
        }
        return value + (piece.face().charAt(1) == '0' ? 1 : 0);
    }

    private static String lowestScore(
            List<String> actions, java.util.function.ToIntFunction<String> score, Random random) {
        int best = Integer.MAX_VALUE;
        List<String> choices = new ArrayList<>();
        for (String action : actions) {
            int value = score.applyAsInt(action);
            if (value < best) {
                best = value;
                choices.clear();
            }
            if (value == best) {
                choices.add(action);
            }
        }
        return choices.get(random.nextInt(choices.size()));
    }
}
