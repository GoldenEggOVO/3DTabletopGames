package dev.tabletop3d.rules.mahjong;

import dev.tabletop3d.rules.MahjongGame;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionalGameTest {
    @Test void fixedWildcardKeepsTheWallTileAndCannotBeDiscarded()throws Exception {
        var game=new MahjongGame(4,3,Map.of("profile","qinhuangdao","rounds","1","wildcard-mode","FIXED","fixed-wildcard","z4"));
        passGold(game);assertEquals(71,game.deckSize());assertFalse(game.publicInfo().containsKey("indicator"));assertEquals("z4",game.publicInfo().get("wildcard"));
        setHand(game,0,"z4 m1 m2 m3 m4 m5 m6 p1 p2 p3 s1 s2 s3 z1");assertFalse(game.legalActions(0).contains("discard:s0_0"));
    }
    @Test void ponCannotLeaveOnlyUndiscardableWildcardsAndLockTheTable()throws Exception {
        var game=new MahjongGame(4,3,Map.of("profile","qinhuangdao","rounds","1","wildcard-mode","FIXED","fixed-wildcard","z4"));passGold(game);
        setHand(game,0,"m1 m2 m3 m4 p1 p3 p5 s1 s3 s5 s7 z1 z2 z3");setHand(game,1,"m1 m1 z4 z4");setHand(game,2,"");setHand(game,3,"");
        List<List<Meld>> melds=get(game,"melds");for(int type:List.of(9,12,18))melds.get(1).add(meld(type,1));
        game.apply(0,"discard:s0_0");if(game.publicInfo().get("phase").equals("RON"))game.apply(1,"pass");
        assertFalse(game.legalActions(game.currentPlayer()).isEmpty());assertTrue(game.legalActions(1).stream().noneMatch(a->a.startsWith("pon:")));
    }
    @Test void fuzhouOpeningThreeGoldWinsBeforeTheDealerExtraDraw()throws Exception {
        var game=new MahjongGame(4,3,Map.of("profile","fuzhou","rounds","1"));passGold(game);
        set(game,"wildcard",30);setHand(game,1,"z4 z4 z4 m1 m2 m4 m5 m7 m8 p1 p3 p5 p7 s1 s3 s5");
        setHand(game,0,"");setHand(game,2,"");setHand(game,3,"");set(game,"goldStage",0);
        var opening=MahjongGame.class.getDeclaredMethod("openingGold");opening.setAccessible(true);opening.invoke(game);
        assertEquals("GOLD",game.publicInfo().get("phase"));assertEquals(1,game.currentPlayer());int wall=game.deckSize();game.apply(1,"tsumo");
        assertTrue(game.finished());assertEquals(wall,game.deckSize());assertEquals("SAN_JIN_DAO",game.publicInfo().get("winningPatterns"));
    }
    @Test void taiwanNearestRonWinsByDefaultAndMultipleRonCanBeEnabled()throws Exception {
        for(String mode:List.of("NEAREST_ONLY","MULTIPLE")) {
            var game=new MahjongGame(4,0,Map.of("profile","taiwan","rounds","1","ron-mode",mode));
            setHand(game,0,"z1 m1 m2 m3 m4 m5 m6 p1 p2 p3 s4 s5 s6 z2 z3 z4 z5");
            for(int seat=1;seat<=2;seat++)setHand(game,seat,"m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 p6 p7 p8 z1");setHand(game,3,"");
            game.apply(0,"discard:s0_0");assertEquals(1,game.currentPlayer());game.apply(1,"ron");
            if(mode.equals("MULTIPLE")){assertFalse(game.finished());game.apply(2,"ron");assertEquals("1,2",game.publicInfo().get("winners"));}
            else {assertTrue(game.finished());assertEquals("1",game.publicInfo().get("winners"));}
        }
    }
    @Test void hiddenRegionalKongsExposeNeitherTheFaceNorItsPhysicalId()throws Exception {
        var game=new MahjongGame(4,0,Map.of("profile","guangdong"));List<List<Meld>> melds=get(game,"melds");
        melds.get(0).add(new Meld(Meld.Kind.QUAD,List.of(new Tiles.Tile("t0",0,false),new Tiles.Tile("t1",0,false),new Tiles.Tile("t2",0,false),new Tiles.Tile("t3",0,false)),false,0));
        assertTrue(game.exposed(0).stream().allMatch(t->t.face().equals("back")&&t.id().startsWith("masked-")));
    }
    @Test void fuzhouRobGoldUsesOnlyTheOpeningIndicatorAndDiscardedGoldCannotBeRonned()throws Exception {
        var game=new MahjongGame(4,3,Map.of("profile","fuzhou","rounds","1"));passGold(game);set(game,"wildcard",30);set(game,"indicator",new Tiles.Tile("indicator",30,false));
        setHand(game,1,"m1 m2 m3 m4 m5 m6 p1 p2 p3 s1 s2 s3 s7 s8 s9 z1");for(int seat:List.of(0,2,3))setHand(game,seat,"");set(game,"goldStage",1);
        var opening=MahjongGame.class.getDeclaredMethod("openingGold");opening.setAccessible(true);opening.invoke(game);assertEquals(1,game.currentPlayer());assertTrue(game.legalActions(1).contains("rob-gold"));game.apply(1,"rob-gold");
        assertTrue(game.finished());assertEquals("QIANG_JIN",game.publicInfo().get("winningPatterns"));assertEquals("z4",game.publicInfo().get("winningTile"));
        game=new MahjongGame(4,3,Map.of("profile","fuzhou","rounds","1"));passGold(game);set(game,"wildcard",30);
        setHand(game,0,"z4 m1 m2 m3 m4 m5 m6 p1 p2 p3 s4 s5 s6 z1 z2 z3 z5");setHand(game,1,"m1 m2 m3 m4 m5 m6 p1 p2 p3 s1 s2 s3 s7 s8 s9 z1");setHand(game,2,"");setHand(game,3,"");
        game.apply(0,"discard:s0_0");assertNotEquals("RON",game.publicInfo().get("phase"));assertFalse(game.legalActions(game.currentPlayer()).contains("rob-gold"));
    }
    private static Meld meld(int type,int owner){List<Tiles.Tile> tiles=new ArrayList<>();for(int i=0;i<3;i++)tiles.add(new Tiles.Tile("meld"+type+"_"+i,type,false));return new Meld(Meld.Kind.TRIPLET,tiles,true,owner);}
    private static void passGold(MahjongGame game){while(game.publicInfo().get("phase").equals("GOLD"))game.apply(game.currentPlayer(),"pass");}
    private static void setHand(MahjongGame game,int seat,String faces)throws Exception{List<List<Tiles.Tile>> hands=get(game,"hands");hands.get(seat).clear();int index=0;for(String face:faces.split(" "))if(!face.isEmpty())hands.get(seat).add(new Tiles.Tile("s"+seat+"_"+index++,Tiles.type(face),false));}
    @SuppressWarnings("unchecked") private static <T>T get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return (T)f.get(o);}
    private static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
}
