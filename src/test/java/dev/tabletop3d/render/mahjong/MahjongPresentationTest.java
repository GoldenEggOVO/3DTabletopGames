package dev.tabletop3d.render.mahjong;

import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.mahjong.MahjongGame;
import dev.tabletop3d.rules.mahjong.Tiles;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MahjongPresentationTest {
    @Test void bonusHintsFollowPublicIndicatorsAndRedFivesOnly(){
        Map<String,String> info=Map.of("profile","riichi","dora","m9,z4,z7,p0","ura","s1");
        for(String face:List.of("m1","z1","z5","p6","m0","p0","s0"))assertTrue(MahjongPresentation.bonus(info,face),face);
        for(String face:List.of("m9","z4","z7","p5","s2","back","f1"))assertFalse(MahjongPresentation.bonus(info,face),face);
        assertFalse(MahjongPresentation.bonus(Map.of("profile","taiwan","dora","m9"),"m1"));
        assertFalse(MahjongPresentation.bonus(Map.of("profile","sichuan"),"m0"));
    }
    @Test void newlyDrawnTileStaysAtRightEndUntilDiscardWhileOtherTilesRemainSorted(){
        MahjongGame game=new MahjongGame(4,42L,Map.of());int checked=0;
        for(int step=0;step<180&&!game.finished();step++){
            List<List<HandGame.Piece>> before=java.util.stream.IntStream.range(0,4).mapToObj(game::hand).toList();
            int seat=game.currentPlayer();var actions=game.legalActions(seat);
            String action=actions.stream().filter(a->a.startsWith("discard:")).findFirst().orElse(actions.contains("pass")?"pass":actions.getFirst());
            game.apply(seat,action);
            if(!game.publicInfo().get("phase").equals("TURN"))continue;
            int next=game.currentPlayer();var hand=game.hand(next);
            var added=hand.stream().filter(tile->before.get(next).stream().noneMatch(old->old.id().equals(tile.id()))).toList();
            if(added.size()!=1)continue;
            assertEquals(added.getFirst(),hand.getLast());checked++;
            for(int i=1;i<hand.size()-1;i++)assertTrue(Tiles.type(hand.get(i-1).face())<=Tiles.type(hand.get(i).face()));
        }
        assertTrue(checked>10,"Exercise successive real draws and hand sorting");
    }
    @Test void remainingUsesOnlyOwnHandAndPublicTiles() {
        HandGame game=visible(Map.of("tileCount","136"),piece("a","m5"),piece("b","m0"));
        assertEquals(2,MahjongPresentation.remaining(game,0,"m5"));
        when(game.hand(0)).thenReturn(List.of(piece("a","m5")));
        when(game.discards(2)).thenReturn(List.of(piece("b","m0")));
        assertEquals(2,MahjongPresentation.remaining(game,0,"m0"));
        verify(game,never()).deckSize();
    }

    @Test void calledDiscardAndIndicatorAreCountedOnceByPhysicalIdentity() {
        HandGame game=visible(Map.of("tileCount","136","dora","m5","doraIds","indicator",
                "lastDiscardTile","m5","lastDiscardBy","2"));
        when(game.exposed(1)).thenReturn(List.of(piece("a","m5"),piece("b","m0"),piece("c","m5")));
        assertEquals(0,MahjongPresentation.remaining(game,0,"m5"));
        when(game.discards(2)).thenReturn(List.of(piece("c","m5")));
        assertEquals(0,MahjongPresentation.remaining(game,0,"m5"));
        when(game.exposed(1)).thenReturn(List.of(piece("indicator","m5")));
        when(game.discards(2)).thenReturn(List.of());
        assertEquals(3,MahjongPresentation.remaining(game,0,"m5"));
    }

    @Test void maskedTilesAreNotInspectedAndFlowersArePublic() {
        HandGame game=visible(Map.of("tileCount","144"));
        when(game.exposed(1)).thenReturn(List.of(piece("hidden","back"),piece("flower","f1")));
        assertEquals(4,MahjongPresentation.remaining(game,0,"z1"));
        assertEquals(0,MahjongPresentation.remaining(game,0,"f1"));
        assertEquals(1,MahjongPresentation.remaining(game,0,"f2"));
    }

    @Test void offeredKongTileCountsAsPublicBeforeItMovesOutOfTheOpponentsHand() {
        HandGame game=visible(Map.of("tileCount","136","offeredTile","m0","offeredId","offered"));
        when(game.exposed(1)).thenReturn(List.of(piece("a","m5"),piece("b","m5"),piece("c","m5")));
        assertEquals(0,MahjongPresentation.remaining(game,0,"m5"));
        when(game.exposed(1)).thenReturn(List.of());
        when(game.discards(1)).thenReturn(List.of(piece("offered","m0")));
        assertEquals(3,MahjongPresentation.remaining(game,0,"m5"));
        assertEquals(0,MahjongPresentation.remaining(game,0,null));
    }

    @Test void supportedTileSetsRespectExcludedHonorsAndSingleFlowers() {
        for(int count:List.of(108,124,136,144)) {
            HandGame game=visible(Map.of("tileCount",Integer.toString(count)));
            for(int type=0;type<42;type++) {
                int tileType=type;
                int expected=Tiles.set(count,0).stream().filter(t->t.type()==tileType).toList().size();
                assertEquals(expected,MahjongPresentation.remaining(game,0,Tiles.face(type)),count+" "+Tiles.face(type));
            }
        }
    }

    @Test void chiAndPonPreviewsKeepExactRedFiveChoice() {
        HandGame game=visible(Map.of("offeredTile","m4"),piece("a","m3"),piece("b","m5"),piece("red","m0"));
        when(game.legalActions(0)).thenReturn(List.of("chi:a,b","chi:a,red"));
        assertEquals(List.of("m3","m4","m5"),MahjongPresentation.choiceFaces(game,0,"chi:a,b"));
        assertEquals(List.of("m3","m4","m0"),MahjongPresentation.choiceFaces(game,0,"chi:a,red"));
        when(game.publicInfo()).thenReturn(Map.of("offeredTile","m5"));
        when(game.legalActions(0)).thenReturn(List.of("pon:b,red"));
        assertEquals(List.of("m5","m5","m0"),MahjongPresentation.choiceFaces(game,0,"pon:b,red"));
    }

    @Test void kanPreviewsShowAllFourPhysicalFaces() {
        HandGame game=visible(Map.of("offeredTile","m5"),piece("a","m5"),piece("b","m5"),piece("red","m0"));
        when(game.legalActions(0)).thenReturn(List.of("kan-open:a,b,red"));
        assertEquals(List.of("m5","m5","m5","m0"),MahjongPresentation.choiceFaces(game,0,"kan-open:a,b,red"));
        when(game.hand(0)).thenReturn(List.of(piece("a","m5"),piece("b","m5"),piece("c","m5"),piece("red","m0")));
        when(game.legalActions(0)).thenReturn(List.of("kan-closed:a"));
        assertEquals(List.of("m5","m5","m5","m0"),MahjongPresentation.choiceFaces(game,0,"kan-closed:a"));
        when(game.hand(0)).thenReturn(List.of(piece("red","m0")));
        when(game.exposed(0)).thenReturn(List.of(piece("a","m5"),piece("b","m5"),piece("c","m5")));
        when(game.legalActions(0)).thenReturn(List.of("kan-added:red"));
        assertEquals(List.of("m5","m5","m5","m0"),MahjongPresentation.choiceFaces(game,0,"kan-added:red"));
    }

    @Test void staleAndUnresolvableChoicesHaveNoPreview() {
        HandGame game=visible(Map.of("offeredTile","m4"),piece("a","m3"));
        assertTrue(MahjongPresentation.choiceFaces(game,0,"chi:a,b").isEmpty());
        when(game.legalActions(0)).thenReturn(List.of("chi:a,b","pass"));
        assertTrue(MahjongPresentation.choiceFaces(game,0,"chi:a,b").isEmpty());
        assertTrue(MahjongPresentation.choiceFaces(game,0,"pass").isEmpty());
    }

    @Test void everyProfilePublishesItsActualTileCountAndIndicatorIds() {
        for(String profile:List.of("guangdong","riichi","taiwan","sichuan")) {
            MahjongGame game=new MahjongGame(4,9,Map.of("profile",profile));
            int count=switch(profile){case "taiwan"->144;case "sichuan"->108;default->136;};
            assertEquals(Integer.toString(count),game.publicInfo().get("tileCount"));
            if(profile.equals("riichi"))assertEquals(game.publicInfo().get("dora").split(",").length,game.publicInfo().get("doraIds").split(",").length);
        }
    }

    @Test void lastDiscardMetadataSurvivesResponsesAndClearsOnNewHand() {
        MahjongGame game=new MahjongGame(4,9,Map.of("profile","guangdong","rounds","4"));
        assertFalse(game.publicInfo().containsKey("lastDiscardTile"));
        var tile=game.hand(0).getFirst();game.apply(0,"discard:"+tile.id());
        assertEquals(tile.face(),game.publicInfo().get("lastDiscardTile"));
        assertEquals("0",game.publicInfo().get("lastDiscardBy"));
        while(game.legalActions(game.currentPlayer()).contains("pass"))game.apply(game.currentPlayer(),"pass");
        assertEquals(tile.face(),game.publicInfo().get("lastDiscardTile"));
        for(int step=0;step<1000&&!game.publicInfo().get("phase").equals("ROUND_END");step++) {
            int seat=game.currentPlayer();List<String> legal=game.legalActions(seat);
            game.apply(seat,legal.contains("pass")?"pass":legal.stream().filter(a->a.startsWith("discard:")).findFirst().orElse(legal.getFirst()));
        }
        assertEquals("ROUND_END",game.publicInfo().get("phase"));
        game.apply(game.currentPlayer(),"next-hand");
        assertFalse(game.publicInfo().containsKey("lastDiscardTile"));
    }

    private static HandGame visible(Map<String,String> info,HandGame.Piece... hand) {
        HandGame game=mock(HandGame.class);when(game.playerCount()).thenReturn(4);
        when(game.publicInfo()).thenReturn(info);when(game.hand(0)).thenReturn(List.of(hand));
        for(int seat=1;seat<4;seat++)when(game.hand(seat)).thenThrow(new AssertionError("Opponent hand accessed"));
        return game;
    }
    private static HandGame.Piece piece(String id,String face){return new HandGame.Piece(id,face);}
}
