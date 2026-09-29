package dev.tabletop3d.rules.mahjong;

import dev.tabletop3d.rules.MahjongGame;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionalGameTest {
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
    private static void setHand(MahjongGame game,int seat,String faces)throws Exception{List<List<Tiles.Tile>> hands=get(game,"hands");hands.get(seat).clear();int index=0;for(String face:faces.split(" "))if(!face.isEmpty())hands.get(seat).add(new Tiles.Tile("s"+seat+"_"+index++,Tiles.type(face),false));}
    @SuppressWarnings("unchecked") private static <T>T get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return (T)f.get(o);}
}
