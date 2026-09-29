package dev.tabletop3d;

import dev.tabletop3d.rules.*;
import dev.tabletop3d.ui.MessageText;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HandMenuTest {
    @Test void wildColorAndStackedDrawPenaltyArePublicWithoutExposingAnyHand(){
        HandGame game=mock(HandGame.class);when(game.publicInfo()).thenReturn(Map.of("color","b","direction","Counterclockwise","drawPenalty","6"));
        when(game.cells()).thenReturn(List.of(new Cell("discard",0,0,"wild",-1)));
        String text=MessageText.plain(HandText.status("lastcard",game));
        assertTrue(text.contains("Blue"));assertTrue(text.contains("Counterclockwise"));assertTrue(text.contains("penalty: 6"));assertTrue(text.contains("Wild"));
        assertTrue(MessageText.plain(HandText.tableHint("lastcard",game)).contains("Blue"));verify(game,never()).hand(anyInt());
    }
    @Test void spectatorMenuOmitsPublicTableDetailsAndNeverReadsConcealedHands()throws Exception{
        var fixture=new MenuFlowTest.Fixture();Room room=new Room(UUID.randomUUID(),"lastcard",2,0,0);
        room.join(UUID.randomUUID(),"Owner");room.fillBots();room.board=spy(new LastCardGame(2,0));room.phase=Room.Phase.PLAYING;fixture.plugin.rooms.put(room.id,room);
        fixture.menus.observe(fixture.player,room);
        verify((HandGame)room.board,never()).hand(anyInt());
        assertTrue(fixture.buttons.stream().noneMatch(b->Set.of("public-table","rules","details").contains(b.id())));
        assertTrue(MessageText.plain(fixture.description).contains("Owner"));
    }
    @Test void ownHandMenuReadsOnlyTheRequestingSeatAndStaleMoveIsRevisionBound()throws Exception{
        var fixture=new MenuFlowTest.Fixture();Room room=new Room(UUID.randomUUID(),"lastcard",2,0,0);room.board=spy(new LastCardGame(2,0));
        room.join(UUID.randomUUID(),"Other");room.join(fixture.player.getUniqueId(),"Owner");room.phase=Room.Phase.PLAYING;fixture.plugin.rooms.put(room.id,room);
        fixture.menus.hand(fixture.player,room,0);verify((HandGame)room.board,atLeastOnce()).hand(1);verify((HandGame)room.board,never()).hand(0);
        if(room.turn()==1){var button=fixture.buttons.stream().filter(b->b.id().equals("hand-action")).findFirst().orElseThrow();long revision=room.revision;room.revision++;button.action().run();verify(fixture.plugin).action(eq(fixture.player),eq(room),eq(revision),any());}
    }
}
