package dev.tabletop3d.rules.mahjong;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

class RiichiGameTest {
    @Test
    void riichiAutomationOnlyDiscardsTheDrawAndWaitsForWinChoices() throws Exception {
        var game = game();
        setHand(game, 0, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z2");
        List<List<Tiles.Tile>> hands = get(game, "hands");
        set(game, "drawnTile", hands.get(0).getLast());
        assertNull(game.riichiAutoDiscard(0));
        boolean[] declared = get(game, "riichi");
        declared[0] = true;
        assertEquals("discard:s0_13", game.riichiAutoDiscard(0));
        assertNull(game.riichiAutoDiscard(1));
        setHand(game, 0, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z1");
        set(game, "drawnTile", hands.get(0).getLast());
        assertTrue(game.legalActions(0).contains("tsumo"));
        assertNull(game.riichiAutoDiscard(0));
        assertEquals("discard:s0_13", game.riichiDrawDiscard(0));
    }

    @Test
    void defaultProfileUsesDeadWallAndSeededHandsConserveTilesAndDeposits() {
        for (int seed = 0; seed < 15; seed++) {
            var game = new MahjongGame(4, seed, Map.of("rounds", "1"));
            assertEquals("riichi", game.publicInfo().get("profile"));
            assertEquals(69, game.deckSize());
            for (int step = 0; step < 650 && !game.finished(); step++) {
                int tiles = game.deckSize() + 14,
                        points = Integer.parseInt(game.publicInfo().get("riichiSticks")) * 1000;
                for (int seat = 0; seat < 4; seat++) {
                    tiles +=
                            game.handSize(seat)
                                    + game.exposed(seat).size()
                                    + game.discards(seat).size();
                    points += Integer.parseInt(game.publicInfo().get("score." + seat));
                }
                assertEquals(136, tiles);
                assertEquals(100000, points);
                var actions = game.legalActions(game.currentPlayer());
                assertFalse(actions.isEmpty());
                game.apply(game.currentPlayer(), actions.getFirst());
            }
            assertTrue(game.finished());
        }
    }

    @Test
    void riichiAcceptsOneLiveTileAndLocksTheHandAfterAValidDeclaration() throws Exception {
        var game = game();
        setHand(game, 0, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z2");
        for (int seat = 1; seat < 4; seat++) setHand(game, seat, "");
        Wall wall = get(game, "wall");
        int front = get(wall, "front");
        set(wall, "back", front + 1);
        assertTrue(game.legalActions(0).contains("riichi:s0_13"));
        game.apply(0, "riichi:s0_13");
        assertEquals("24000", game.publicInfo().get("score.0"));
        assertEquals("1", game.publicInfo().get("riichiSticks"));
        assertEquals("s0_13", game.riichiTile(0));
        set(game, "current", 0);
        List<List<Tiles.Tile>> hands = get(game, "hands");
        Tiles.Tile drawn = new Tiles.Tile("extra", 2, false);
        hands.get(0).add(drawn);
        set(game, "drawnTile", drawn);
        assertEquals(List.of("discard:extra"), game.legalActions(0));
    }

    @Test
    void aDeclarationDiscardRonDoesNotChargeAStick() throws Exception {
        var game = game();
        setHand(game, 0, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z2");
        setHand(game, 1, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z2");
        setHand(game, 2, "");
        setHand(game, 3, "");
        boolean[] riichi = get(game, "riichi");
        riichi[1] = true;
        game.apply(0, "riichi:s0_13");
        assertEquals("RON", game.publicInfo().get("phase"));
        assertEquals("25000", game.publicInfo().get("score.0"));
        game.apply(1, "ron");
        int sum = 0;
        for (int seat = 0; seat < 4; seat++)
            sum += Integer.parseInt(game.publicInfo().get("score." + seat));
        assertEquals(100000, sum);
        assertEquals("0", game.publicInfo().get("riichiSticks"));
    }

    @Test
    void selfDiscardFuritenBlocksEveryWaitAndPassingRonCreatesTemporaryFuriten() throws Exception {
        var game = game();
        setHand(game, 0, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z2");
        setHand(game, 1, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1");
        setHand(game, 2, "");
        setHand(game, 3, "");
        boolean[] riichi = get(game, "riichi");
        riichi[1] = true;
        List<Set<Integer>> discards = get(game, "ownDiscards");
        discards.get(1).add(27);
        game.apply(0, "discard:s0_12");
        assertNotEquals("RON", game.publicInfo().get("phase"));
        game = game();
        setHand(game, 0, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z2");
        setHand(game, 1, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1");
        setHand(game, 2, "");
        setHand(game, 3, "");
        riichi = get(game, "riichi");
        riichi[1] = true;
        game.apply(0, "discard:s0_12");
        game.apply(1, "pass");
        boolean[] permanent = get(game, "riichiFuriten");
        assertTrue(permanent[1]);
    }

    @Test
    void closedKongAddsAnIndicatorAndReplacementAndBreaksIppatsu() throws Exception {
        var game = game();
        setHand(game, 0, "m1 m1 m1 m1 m2 m3 p4 p5 p6 s7 s8 s9 z1 z1");
        for (int seat = 1; seat < 4; seat++) setHand(game, seat, "");
        boolean[] ippatsu = get(game, "ippatsu");
        Arrays.fill(ippatsu, true);
        int remaining = game.deckSize();
        game.apply(0, "kan-closed:s0_0");
        assertEquals(remaining - 1, game.deckSize());
        assertEquals(2, game.publicInfo().get("dora").split(",").length);
        assertEquals(11, game.handSize(0));
        for (boolean value : ippatsu) assertFalse(value);
        assertTrue(game.exposed(0).stream().allMatch(t -> t.face().equals("m1")));
    }

    @Test
    void exhaustiveDrawPaysThreeThousandAndOnlyReadyDealerContinues() throws Exception {
        var game = new MahjongGame(4, 0, Map.of("profile", "riichi", "rounds", "4"));
        set(game, "anyCalls", true);
        setHand(game, 0, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1");
        setHand(game, 1, "m1 m3 m5 m7 m9 p1 p3 p5 p7 p9 s1 s3 z1 z2");
        setHand(game, 2, "");
        setHand(game, 3, "");
        set(game, "current", 1);
        Wall wall = get(game, "wall");
        set(wall, "back", get(wall, "front"));
        game.apply(1, "discard:s1_13");
        assertEquals("ROUND_END", game.publicInfo().get("phase"));
        assertEquals("0", game.publicInfo().get("dealer"));
        assertEquals("28000", game.publicInfo().get("score.0"));
        assertEquals("1", game.publicInfo().get("honba"));
        for (int seat = 1; seat < 4; seat++)
            assertEquals("24000", game.publicInfo().get("score." + seat));
    }

    @Test
    void completedShapeNeedsOneYakuAndMultipleRonPaysEachWinner() throws Exception {
        var game = game();
        setHand(game, 0, "z1 m1 m2 m3 m4 m5 m6 p1 p2 p3 s4 s5 s6 z2");
        setHand(game, 1, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1");
        setHand(game, 2, "");
        setHand(game, 3, "");
        game.apply(0, "discard:s0_0");
        assertNotEquals("RON", game.publicInfo().get("phase"));
        game = game();
        setHand(game, 0, "z1 m1 m2 m3 m4 m5 m6 p1 p2 p3 s4 s5 s6 z2");
        for (int seat = 1; seat < 3; seat++)
            setHand(game, seat, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1");
        setHand(game, 3, "");
        boolean[] riichi = get(game, "riichi");
        riichi[1] = true;
        riichi[2] = true;
        game.apply(0, "discard:s0_0");
        game.apply(1, "ron");
        assertFalse(game.finished());
        game.apply(2, "ron");
        assertEquals("1,2", game.publicInfo().get("winners"));
        int paid1 = Integer.parseInt(game.publicInfo().get("score.1")) - 25000,
                paid2 = Integer.parseInt(game.publicInfo().get("score.2")) - 25000;
        assertTrue(paid1 > 0 && paid2 > 0);
        assertEquals(25000 - paid1 - paid2, Integer.parseInt(game.publicInfo().get("score.0")));
    }

    @Test
    void noYakuReportsOnlyTheCurrentDrawAndLeavesStateUnchanged() throws Exception {
        var game = game();
        setHand(game, 0, "m4 m5 m6 p2 p3 p4 s7 s8 s9 z2 z2");
        List<List<Meld>> melds = get(game, "melds");
        melds.get(0)
                .add(
                        new Meld(
                                Meld.Kind.SEQUENCE,
                                List.of(
                                        new Tiles.Tile("open0", 0, false),
                                        new Tiles.Tile("open1", 1, false),
                                        new Tiles.Tile("open2", 2, false)),
                                true,
                                1));
        List<List<Tiles.Tile>> hands = get(game, "hands");
        set(game, "drawnTile", hands.get(0).getLast());
        var info = game.publicInfo();
        var hand = game.hand(0);
        var actions = game.legalActions(0);
        assertFalse(actions.contains("tsumo"));
        assertTrue(game.noYaku(0, "s0_10"));
        assertFalse(game.noYaku(0, "s0_9"));
        assertFalse(game.noYaku(1, "s0_10"));
        assertFalse(game.noYaku(-1, "s0_10"));
        assertFalse(game.noYaku(4, "s0_10"));
        assertFalse(game.noYaku(0, null));
        assertEquals(info, game.publicInfo());
        assertEquals(hand, game.hand(0));
        assertEquals(actions, game.legalActions(0));
        setHand(game, 0, "m4 m5 m6 p2 p3 p4 s7 s8 s9 z2 z3");
        hands = get(game, "hands");
        set(game, "drawnTile", hands.get(0).getLast());
        assertFalse(game.noYaku(0, "s0_10"));
        set(game, "drawnTile", null);
        assertFalse(game.noYaku(0, "s0_10"));
    }

    @Test
    void noYakuRecognizesACompleteDiscardShapeForObserversWithoutRevealingOtherTiles()
            throws Exception {
        var game = discardNoYakuGame();
        assertEquals("CALL", game.publicInfo().get("phase"));
        assertTrue(game.noYaku(1, "s0_0"));
        assertFalse(game.noYaku(0, "s0_0"));
        assertFalse(game.noYaku(1, "s0_1"));
        assertFalse(game.noYaku(1, "s1_12"));
        assertFalse(game.noYaku(2, "s0_0"));
        game.apply(2, "pass");
        assertFalse(game.noYaku(1, "s0_0"));
    }

    @Test
    void noYakuDoesNotConfuseFuritenOrExistingYakuWithMissingYaku() throws Exception {
        var game = discardNoYakuGame();
        boolean[] riichi = get(game, "riichi");
        riichi[1] = true;
        List<Set<Integer>> discards = get(game, "ownDiscards");
        discards.get(1).add(27);
        assertFalse(game.noYaku(1, "s0_0"));
        riichi[1] = false;
        boolean[] temporary = get(game, "temporaryFuriten");
        temporary[1] = true;
        assertTrue(game.noYaku(1, "s0_0"));
        setHand(game, 1, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z2");
        assertFalse(game.noYaku(1, "s0_0"));
        game = game();
        setHand(game, 0, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z1");
        List<List<Tiles.Tile>> hands = get(game, "hands");
        set(game, "drawnTile", hands.get(0).getLast());
        assertTrue(game.legalActions(0).contains("tsumo"));
        assertFalse(game.noYaku(0, "s0_13"));
    }

    @Test
    void noYakuIsDisabledForOtherMahjongProfiles() throws Exception {
        for (String profile : List.of("guangdong", "taiwan", "sichuan")) {
            var game = new MahjongGame(4, 0, Map.of("profile", profile, "rounds", "1"));
            setHand(game, 0, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z1");
            List<List<Tiles.Tile>> hands = get(game, "hands");
            set(game, "drawnTile", hands.get(0).getLast());
            assertFalse(game.noYaku(0, "s0_13"));
        }
    }

    @Test
    void riichiKongCannotChangeAnExistingSequenceInterpretation() throws Exception {
        var game = game();
        setHand(game, 0, "m1 m1 m1 m1 m2 m2 m2 m3 m3 m3 p4 p5 z1 z1");
        boolean[] riichi = get(game, "riichi");
        riichi[0] = true;
        List<List<Tiles.Tile>> hands = get(game, "hands");
        set(game, "drawnTile", hands.get(0).getFirst());
        assertFalse(game.legalActions(0).contains("kan-closed:s0_0"));
        setHand(game, 0, "m1 m1 m1 m1 p2 p3 p4 p5 p6 p7 s7 s8 s9 z1");
        hands = get(game, "hands");
        set(game, "drawnTile", hands.get(0).getFirst());
        assertTrue(game.legalActions(0).contains("kan-closed:s0_0"));
    }

    @Test
    void responsibilityPaysAllOfATsumoAndSplitsRonWithTheDiscarder() throws Exception {
        for (boolean tsumo : List.of(true, false)) {
            var game = game();
            for (int seat = 0; seat < 4; seat++) setHand(game, seat, "");
            setHand(game, 1, tsumo ? "m1 m2 m3 z1 z1" : "m1 m2 m3 z1");
            List<List<Meld>> melds = get(game, "melds");
            for (int type = 31; type <= 33; type++) {
                List<Tiles.Tile> group = new ArrayList<>();
                for (int i = 0; i < 3; i++)
                    group.add(new Tiles.Tile("dragon" + type + "_" + i, type, false));
                melds.get(1).add(new Meld(Meld.Kind.TRIPLET, group, true, type == 33 ? 2 : 0));
            }
            set(game, "honba", 1);
            if (tsumo) {
                set(game, "current", 1);
                List<List<Tiles.Tile>> hands = get(game, "hands");
                set(game, "drawnTile", hands.get(1).getLast());
                game.apply(1, "tsumo");
                assertEquals("25000", game.publicInfo().get("score.0"));
                assertEquals("-7300", game.publicInfo().get("score.2"));
            } else {
                setHand(game, 0, "z1");
                game.apply(0, "discard:s0_0");
                game.apply(1, "ron");
                assertEquals("8700", game.publicInfo().get("score.0"));
                assertEquals("9000", game.publicInfo().get("score.2"));
            }
            assertEquals("57300", game.publicInfo().get("score.1"));
            assertTrue(game.publicInfo().get("winningPatterns").contains("DAISANGEN"));
        }
    }

    @Test
    void unclaimedFinalDepositsGoToTheHighestFinalScore() throws Exception {
        var game = game();
        for (int seat = 0; seat < 4; seat++) setHand(game, seat, "");
        setHand(game, 0, "m1");
        int[] scores = get(game, "scores");
        scores[0] = 26000;
        scores[1] = 23000;
        set(game, "riichiSticks", 1);
        Wall wall = get(game, "wall");
        set(wall, "back", get(wall, "front"));
        game.apply(0, "discard:s0_0");
        assertTrue(game.finished());
        assertEquals("27000", game.publicInfo().get("score.0"));
        assertEquals("0", game.publicInfo().get("riichiSticks"));
    }

    @Test
    void chiAndPonCannotSwapTheClaimedTileOrTheOtherSequenceEnd() throws Exception {
        var game = game();
        setHand(game, 0, "m2");
        setHand(game, 1, "m2 m3 m4 m5 p1 p3 p5 p7 s1 s3 s5 z1 z2");
        setHand(game, 2, "");
        setHand(game, 3, "");
        game.apply(0, "discard:s0_0");
        assertFalse(game.kuikaeForbidden(1, "s1_0"));
        game.apply(1, "chi:s1_1,s1_2");
        assertTrue(game.kuikaeForbidden(1, "s1_0"));
        assertTrue(game.kuikaeForbidden(1, "s1_3"));
        assertFalse(game.kuikaeForbidden(1, "s1_4"));
        assertFalse(game.kuikaeForbidden(0, "s1_0"));
        assertFalse(game.kuikaeForbidden(1, "s1_1"));
        assertFalse(game.legalActions(1).contains("discard:s1_0"));
        assertFalse(game.legalActions(1).contains("discard:s1_3"));
        assertTrue(game.legalActions(1).contains("discard:s1_4"));
        game.apply(1, "discard:s1_4");
        assertFalse(game.kuikaeForbidden(1, "s1_0"));
        assertFalse(game.kuikaeForbidden(1, "s1_3"));
        game = game();
        setHand(game, 0, "m5");
        setHand(game, 1, "m5 m5 m5 p1 p3 p5 p7 s1 s3 s5 z1 z2 z3");
        setHand(game, 2, "");
        setHand(game, 3, "");
        game.apply(0, "discard:s0_0");
        game.apply(1, "pon:s1_0,s1_1");
        assertTrue(game.kuikaeForbidden(1, "s1_2"));
        assertFalse(game.kuikaeForbidden(1, "s1_3"));
        assertFalse(game.legalActions(1).contains("discard:s1_2"));
        assertTrue(game.legalActions(1).stream().noneMatch(a -> a.startsWith("kan-")));
        assertTrue(game.legalActions(1).contains("discard:s1_3"));
    }

    @Test
    void settledMultipleRonHandsIncludeTheWinningTileWithoutRevealingPendingClaims() throws Exception {
        var game = game();
        setHand(game, 0, "z1");
        for (int seat : List.of(1, 2)) setHand(game, seat, "m1 m2 m3 p1 p2 p3 s1 s2 s3 z5 z5 z5 z1");
        setHand(game, 3, "");
        assertTrue(game.revealedHand(1).isEmpty());
        game.apply(0, "discard:s0_0");
        assertTrue(game.legalActions(1).contains("ron"));
        game.apply(1, "ron");
        assertTrue(game.revealedHand(1).isEmpty());
        game.apply(2, "ron");
        for (int seat : List.of(1, 2)) {
            assertEquals(13, game.hand(seat).size());
            assertEquals(14, game.revealedHand(seat).size());
            assertEquals("s0_0", game.revealedHand(seat).getLast().id());
        }
        assertTrue(game.revealedHand(0).isEmpty());
    }

    @Test
    void selfDrawRevealsExactlyFourteenTilesEvenWhenThePreviousDiscardHasTheSameFace() throws Exception {
        var game = game();
        setHand(game, 0, "m1 m2 m3 p1 p2 p3 s1 s2 s3 z5 z5 z5 z1 z1");
        List<List<Tiles.Tile>> hands = get(game, "hands");
        set(game, "drawnTile", hands.get(0).getLast());
        set(game, "offered", new Tiles.Tile("old-discard", Tiles.type("z1"), false));
        assertEquals("discard:s0_13", game.drawDiscard(0));
        game.apply(0, "tsumo");
        assertEquals(14, game.revealedHand(0).size());
        assertTrue(game.revealedHand(0).stream().noneMatch(tile -> tile.id().equals("old-discard")));
    }

    private static MahjongGame game() throws Exception {
        var game = new MahjongGame(4, 0, Map.of("profile", "riichi", "rounds", "1"));
        set(game, "anyCalls", true);
        set(game, "discardCount", 8);
        return game;
    }

    private static MahjongGame discardNoYakuGame() throws Exception {
        var game = game();
        setHand(game, 0, "z1 m9");
        setHand(game, 1, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1");
        setHand(game, 2, "z1 z1 m9");
        setHand(game, 3, "");
        game.apply(0, "discard:s0_0");
        return game;
    }

    private static void setHand(MahjongGame game, int seat, String faces) throws Exception {
        List<List<Tiles.Tile>> hands = get(game, "hands");
        hands.get(seat).clear();
        int index = 0;
        for (String face : faces.split(" "))
            if (!face.isEmpty())
                hands.get(seat)
                        .add(new Tiles.Tile("s" + seat + "_" + index++, Tiles.type(face), false));
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(o);
    }

    private static void set(Object o, String name, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }
}
