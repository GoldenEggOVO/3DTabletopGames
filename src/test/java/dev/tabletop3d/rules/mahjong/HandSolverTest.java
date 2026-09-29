package dev.tabletop3d.rules.mahjong;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandSolverTest {
    @Test void standardFourAndFiveMeldHandsRequireExactlyOnePair() {
        assertFalse(HandSolver.solve(counts("m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z1"),0,4).isEmpty());
        assertTrue(HandSolver.solve(counts("m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z2"),0,4).isEmpty());
        int[] sixteen=counts("m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z1 z1 z2 z2");
        assertFalse(HandSolver.solve(sixteen,0,5).isEmpty());assertTrue(HandSolver.solve(sixteen,0,4).isEmpty());
    }
    @Test void decompositionKeepsAlternativePairAndSequenceChoicesForScoring() {
        var shapes=HandSolver.solve(counts("m1 m1 m2 m2 m3 m3 m4 m4 m5 m5 m6 m6 m7 m7"),0,4);
        assertTrue(shapes.stream().map(HandSolver.Shape::pair).distinct().count()>1);
        assertTrue(HandSolver.sevenPairs(counts("m1 m1 m2 m2 m3 m3 m4 m4 m5 m5 m6 m6 m7 m7"),0,false));
    }
    @Test void regionalSevenPairsAcceptQuadsButRiichiRequiresSevenDifferentPairs() {
        int[] tiles=counts("m1 m1 m1 m1 m2 m2 m3 m3 p4 p4 p5 p5 s9 s9");
        assertTrue(HandSolver.sevenPairs(tiles,0,true));assertFalse(HandSolver.sevenPairs(tiles,0,false));
    }
    @Test void wildcardsFillMissingSequenceStartsAndPairsWithoutCrossingSuits() {
        assertFalse(HandSolver.solve(counts("m2 m3 p1 p2 p3 s4 s5 s6 z1 z1 z1 z2"),2,4).isEmpty());
        assertTrue(HandSolver.solve(counts("m8 m9 p1 p2 p3 s4 s5 s6 z1 z1 z1 z2 z3"),1,4).isEmpty());
        assertTrue(HandSolver.solve(counts("m1 m2 m3 p1 p2 p3 s1 s2 s3 z1 z1 z1"),2,4).stream().anyMatch(s->s.pairWildcards()==2));
    }
    @Test void orphansNeedEveryTerminalAndHonorAndRejectSimpleTiles() {
        assertTrue(HandSolver.orphans(counts("m1 m9 p1 p9 s1 s9 z1 z2 z3 z4 z5 z6 z7 z7"),0));
        assertFalse(HandSolver.orphans(counts("m1 m9 p1 p9 s1 s9 z1 z2 z3 z4 z5 z6 z6 m2"),0));
    }
    @Test void waitsRespectFourCopyLimitsAndExposeAllWinningTiles() {
        int[] tiles=counts("m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1");
        assertEquals(Set.of(27),HandSolver.waits(tiles,-1,4,true,true,false));
        assertTrue(HandSolver.waits(counts("m1 m1 m1 m1 m2 m3 p4 p5 p6 s7 s8 s9 z1"),-1,4,true,true,false).stream().noneMatch(t->t==0));
    }
    static int[] counts(String codes){int[] counts=new int[34];for(String code:codes.split(" "))if(!code.isEmpty())counts[Tiles.type(code)]++;return counts;}
}
