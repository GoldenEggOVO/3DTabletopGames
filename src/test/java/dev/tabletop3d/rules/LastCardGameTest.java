package dev.tabletop3d.rules;

import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LastCardGameTest {
    Object field(LastCardGame game,String name)throws Exception{Field f=LastCardGame.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    void set(LastCardGame game,String name,Object value)throws Exception{Field f=LastCardGame.class.getDeclaredField(name);f.setAccessible(true);f.set(game,value);}
    @SuppressWarnings("unchecked") List<Integer> cards(LastCardGame game,String name)throws Exception{return (List<Integer>)field(game,name);}
    @SuppressWarnings("unchecked") List<List<Integer>> hands(LastCardGame game)throws Exception{return (List<List<Integer>>)field(game,"hands");}
    LastCardGame position(boolean allPlaces,int current,int top,int[]... cards)throws Exception{
        LastCardGame game=new LastCardGame(cards.length,98,allPlaces);List<List<Integer>> hands=hands(game);
        Set<Integer> used=new HashSet<>();for(int seat=0;seat<cards.length;seat++){
            hands.get(seat).clear();for(int card:cards[seat]){assertTrue(used.add(card));hands.get(seat).add(card);}
        }
        cards(game,"pile").clear();if(top>=0){assertTrue(used.add(top));cards(game,"pile").add(top);}
        List<Integer> deck=cards(game,"deck");deck.clear();for(int card=0;card<52;card++)if(!used.contains(card))deck.add(card);
        set(game,"current",current);set(game,"activeColor",top<0?"":"rybp".substring(top/12,top/12+1));return game;
    }
    void nextDraw(LastCardGame game,int card)throws Exception{List<Integer> deck=cards(game,"deck");assertTrue(deck.remove(Integer.valueOf(card)));deck.add(card);}

    @Test void dealsFivePrivateCardsFromACompleteDeckAndChoosesASeededFirstPlayer(){
        Set<Integer> starters=new HashSet<>();
        for(long seed=0;seed<32;seed++){
            LastCardGame g=new LastCardGame(4,seed);starters.add(g.currentPlayer());Set<String> ids=new HashSet<>();
            for(int seat=0;seat<4;seat++){assertEquals(5,g.handSize(seat));for(var card:g.hand(seat))assertTrue(ids.add(card.id()));}
            assertEquals(32,g.deckSize());assertTrue(g.cells().stream().filter(c->c.id().equals("discard")).findFirst().orElseThrow().piece().isEmpty());
            assertFalse(g.legalActions(g.currentPlayer()).contains("draw"));assertTrue(g.legalActions((g.currentPlayer()+1)%4).isEmpty());
        }
        assertEquals(Set.of(0,1,2,3),starters);
        assertThrows(IllegalArgumentException.class,()->new LastCardGame(1,0));assertThrows(IllegalArgumentException.class,()->new LastCardGame(5,0));
    }
    @Test void firstPlayerCanPlayAnyCardIncludingEveryWildColorChoice()throws Exception{
        LastCardGame g=position(false,0,-1,new int[]{0,22,48},new int[]{12});
        assertEquals(List.of("play:0","play:22","play:48:r","play:48:y","play:48:b","play:48:p"),g.legalActions(0));
    }
    @Test void matchesColorOrSymbolAndAllowsWildToChooseTheNextColor()throws Exception{
        LastCardGame g=position(false,0,12,new int[]{0,5,13,24,48},new int[]{14});
        assertTrue(g.legalActions(0).containsAll(List.of("play:0","play:13","play:24","play:48:r","play:48:y","play:48:b","play:48:p")));
        assertFalse(g.legalActions(0).contains("play:5"));g.apply(0,"play:48:b");assertEquals("b",g.publicInfo().get("color"));
        assertEquals(List.of("draw"),g.legalActions(1));
    }
    @Test void drawingEndsTheTurnEvenWhenTheDrawnCardIsPlayable()throws Exception{
        LastCardGame g=position(false,0,25,new int[]{0},new int[]{12});nextDraw(g,26);
        assertEquals(List.of("draw"),g.legalActions(0));assertEquals(List.of("draw"),g.actionsForCell(0,"deck"));g.apply(0,"draw");
        assertEquals(List.of(new HandGame.Piece("0","r1"),new HandGame.Piece("26","b3")),g.hand(0));
        assertEquals(1,g.currentPlayer());assertTrue(g.legalActions(0).isEmpty());
    }
    @Test void drawPenaltiesStackOnlyWithTheSameSymbol()throws Exception{
        for(int rank:new int[]{10,11}){
            LastCardGame g=position(false,0,2,new int[]{rank,0},new int[]{12+rank,rank==10?11:10,48},new int[]{1});
            g.apply(0,"declare");g.apply(0,"play:"+rank);assertEquals(List.of("play:"+(12+rank),"draw"),g.legalActions(1));
            g.apply(1,"play:"+(12+rank));int count=rank==10?4:6;
            assertEquals(String.valueOf(count),g.publicInfo().get("drawPenalty"));assertEquals(List.of("draw"),g.legalActions(2));
            g.apply(2,"draw");assertEquals(1+count,g.handSize(2));assertEquals("0",g.publicInfo().get("drawPenalty"));assertEquals(0,g.currentPlayer());
        }
    }
    @Test void reverseChangesDirectionWithThreePlayers()throws Exception{
        LastCardGame g=position(false,0,0,new int[]{9,2,4},new int[]{12},new int[]{24});g.apply(0,"play:9");
        assertEquals(2,g.currentPlayer());assertEquals("Counterclockwise",g.publicInfo().get("direction"));
    }
    @Test void reverseWithTwoPlayersStillPassesToTheOtherPlayer()throws Exception{
        LastCardGame g=position(false,0,0,new int[]{9,2,4},new int[]{12});g.apply(0,"play:9");assertEquals(1,g.currentPlayer());
    }
    @Test void skipWithTwoPlayersReturnsTheTurnToItsOwner()throws Exception{
        LastCardGame g=position(false,0,0,new int[]{8,2,4},new int[]{12});g.apply(0,"play:8");assertEquals(0,g.currentPlayer());
    }
    @Test void missingDeclarationAddsFiveCardsAfterThePenultimatePlay()throws Exception{
        LastCardGame g=position(false,0,2,new int[]{0,1},new int[]{12});g.apply(0,"play:0");
        assertEquals(6,g.handSize(0));assertEquals(1,g.currentPlayer());assertTrue(g.publicInfo().get("lastAction").contains("penalty"));
    }
    @Test void declarationProtectsOnePlayAndCannotRepeatWithinThatTurn()throws Exception{
        LastCardGame g=position(false,0,2,new int[]{0,1},new int[]{12});List<HandGame.Piece> before=g.hand(0);
        g.apply(0,"declare");assertEquals(0,g.currentPlayer());assertFalse(g.legalActions(0).contains("declare"));
        assertThrows(IllegalArgumentException.class,()->g.apply(0,"declare"));g.apply(0,"play:0");
        assertEquals(1,g.handSize(0));assertEquals(2,before.size());assertThrows(UnsupportedOperationException.class,()->before.clear());
    }
    @Test void declarationIsUnavailableWithoutALegalPlay()throws Exception{
        LastCardGame g=position(false,0,26,new int[]{0,1},new int[]{12});assertEquals(List.of("draw"),g.legalActions(0));
    }
    @Test void reshufflesOnlyOlderDiscardsAndPreservesTheTopCard()throws Exception{
        LastCardGame g=position(false,0,0,new int[]{25},new int[]{2});List<Integer> pile=cards(g,"pile");pile.addFirst(12);pile.addFirst(1);
        List<Integer> deck=cards(g,"deck");deck.removeAll(List.of(1,12));hands(g).get(1).addAll(deck);deck.clear();
        g.apply(0,"draw");assertEquals(List.of(0),pile);assertEquals(1,g.deckSize());assertEquals(2,g.handSize(0));
        assertTrue(Set.of("1","12").contains(g.hand(0).getLast().id()));assertEquals("r",g.publicInfo().get("color"));
        assertEquals("r1",g.cells().stream().filter(c->c.id().equals("discard")).findFirst().orElseThrow().piece());
        assertEquals(52,g.handSize(0)+g.handSize(1)+g.deckSize()+pile.size());
    }
    @Test void publicViewsDoNotChangeWhenOnlyHiddenCardsChange()throws Exception{
        LastCardGame g=position(false,0,0,new int[]{7,18},new int[]{29,40});var publicInfo=g.publicInfo();var cells=g.cells();
        Collections.swap(hands(g),0,1);assertEquals(publicInfo,g.publicInfo());assertEquals(cells,g.cells());
        for(int seat=0;seat<2;seat++){assertTrue(g.discards(seat).isEmpty());assertTrue(g.exposed(seat).isEmpty());}
        assertThrows(UnsupportedOperationException.class,()->g.hand(0).add(new HandGame.Piece("51","wild")));
    }
    @Test void allPlacesSkipsFinishedPlayersAndAppendsTheRemainingPlayer()throws Exception{
        LastCardGame g=position(true,1,0,new int[]{4,5},new int[]{2},new int[]{6},new int[]{3});g.apply(1,"play:2");
        assertFalse(g.finished());assertEquals(List.of(1),g.placements());assertEquals(2,g.currentPlayer());List<Integer> first=g.placements();
        set(g,"current",3);g.apply(3,"play:3");assertEquals(0,g.currentPlayer());g.apply(0,"declare");g.apply(0,"play:4");assertEquals(2,g.currentPlayer());
        g.apply(2,"play:6");assertEquals(List.of(1,3,2,0),g.placements());assertEquals("winner:1",g.outcome());assertTrue(g.finished());
        assertEquals(List.of(1),first);assertThrows(UnsupportedOperationException.class,()->first.add(0));
    }
    @Test void aFinishingSkipStillSkipsTheNextRemainingPlayer()throws Exception{
        LastCardGame g=position(true,0,3,new int[]{8},new int[]{0},new int[]{1},new int[]{2});g.apply(0,"play:8");
        assertEquals(List.of(0),g.placements());assertEquals(2,g.currentPlayer());assertFalse(g.finished());
    }
    @Test void aFinishingDrawCardStillPenalizesRemainingPlayers()throws Exception{
        LastCardGame g=position(true,0,3,new int[]{10},new int[]{22,1},new int[]{2});g.apply(0,"play:10");
        assertEquals(List.of(0),g.placements());assertEquals(1,g.currentPlayer());assertEquals("2",g.publicInfo().get("drawPenalty"));
        g.apply(1,"declare");g.apply(1,"play:22");g.apply(2,"draw");assertEquals(5,g.handSize(2));assertEquals(1,g.currentPlayer());
    }
    @Test void firstPlaceModeStopsImmediatelyAndTwoPlayerAllPlacesAddsRunnerUp()throws Exception{
        for(boolean allPlaces:new boolean[]{false,true}){
            LastCardGame g=position(allPlaces,1,0,new int[]{3},new int[]{1});g.apply(1,"play:1");
            assertTrue(g.finished());assertEquals("winner:1",g.outcome());assertEquals(allPlaces?List.of(1,0):List.of(1),g.placements());assertTrue(g.legalActions(1).isEmpty());
        }
    }
    @Test void identicalSeedsReplayHandsDeckAndPublicStateThroughCompletion(){
        for(int players=2;players<=4;players++)for(boolean allPlaces:new boolean[]{false,true}){
            LastCardGame a=new LastCardGame(players,98,allPlaces),b=new LastCardGame(players,98,allPlaces);
            assertThrows(IllegalArgumentException.class,()->a.apply((a.currentPlayer()+1)%a.playerCount(),"draw"));
            for(int i=0;i<5000&&!a.finished();i++){
                int seat=a.currentPlayer();List<String> actions=a.legalActions(seat);assertFalse(actions.isEmpty());assertEquals(actions,b.legalActions(seat));
                String action=actions.contains("declare")?"declare":actions.get(i%actions.size());a.apply(seat,action);b.apply(seat,action);
                assertEquals(a.publicInfo(),b.publicInfo());assertEquals(a.cells(),b.cells());assertEquals(a.deckSize(),b.deckSize());assertEquals(a.outcome(),b.outcome());
                for(int player=0;player<players;player++)assertEquals(a.hand(player),b.hand(player));
            }
            assertTrue(a.finished());
        }
    }
}
