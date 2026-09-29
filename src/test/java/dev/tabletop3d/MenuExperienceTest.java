package dev.tabletop3d;

import dev.tabletop3d.rules.LastCardGame;
import dev.tabletop3d.ui.MessageText;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuExperienceTest {
    @Test void setupHighlightsCreationAndKeepsDetailedRuleChangesWhenReturning() throws Exception {
        var f=new MenuFlowTest.Fixture();f.menus.setup(f.player,"ludo");
        assertEquals("start",f.buttons.getFirst().id());
        assertFalse(ids(f).contains("rule-blocking"));
        click(f,"details");click(f,"rule-blocking");click(f,"back");
        assertTrue(ids(f).containsAll(List.of("start","mode","capacity","details")));
        click(f,"start");verify(f.plugin).create(f.player,"ludo",4,Map.of("blocking","on"));
    }
    @Test void mahjongProfileStaysOnBasicSetupWhileAllRegionalRulesRemainReachable() throws Exception {
        var f=new MenuFlowTest.Fixture();f.menus.setup(f.player,"mahjong");
        click(f,"rule-profile");click(f,"rule-profile");
        assertFalse(ids(f).contains("rule-base-flowers"));
        click(f,"details");Set<String> seen=new HashSet<>();
        do {seen.addAll(ids(f));if(!ids(f).contains("next"))break;click(f,"next");}while(true);
        assertTrue(seen.containsAll(List.of("rule-base-flowers","rule-jin-que-multiplier")));
        click(f,"back");click(f,"start");
        verify(f.plugin).create(f.player,"mahjong",4,Map.of("profile","fuzhou"));
    }
    @Test void playingRoomKeepsDetailsAndPublicTableBehindOptions() throws Exception {
        var f=new MenuFlowTest.Fixture();Room r=handRoom(f);f.menus.room(f.player,r);
        assertEquals(List.of("play","controls","options","back","close"),ids(f));
        assertFalse(MessageText.plain(f.description).contains(MessageText.plain(RoomText.options(r.kind,r.options))));
        click(f,"options");assertTrue(ids(f).containsAll(List.of("details","public-table","rules","leave")));
        click(f,"details");assertTrue(MessageText.plain(f.description).contains("Other player"));
        click(f,"back");assertTrue(ids(f).contains("leave"));
    }
    @Test void roomAndBrowserBackReturnToAnExistingRoomOrCatalog() throws Exception {
        var f=new MenuFlowTest.Fixture();Room r=f.addRoom(0);r.join(f.player.getUniqueId(),"Owner");
        f.menus.room(f.player,r);click(f,"back");assertTrue(ids(f).contains("resume"));assertFalse(ids(f).contains("start"));
        f.menus.games(f.player,"chess");click(f,"back");assertTrue(ids(f).contains("ready"));
        f.plugin.rooms.clear();f.menus.games(f.player,"chess");click(f,"back");assertTrue(ids(f).contains("chess"));assertFalse(ids(f).contains("start"));
    }
    @Test void privateHandOmitsRosterAndFullRules() throws Exception {
        var f=new MenuFlowTest.Fixture();Room r=handRoom(f);f.menus.hand(f.player,r,0);
        String text=MessageText.plain(f.description);
        assertFalse(text.contains("Other player"));
        assertFalse(text.contains(MessageText.plain(RoomText.options(r.kind,r.options))));
        assertTrue(ids(f).contains("hand-action"));
        click(f,"back");assertTrue(ids(f).contains("play"));
    }
    @Test void lobbyHasOnlyReadinessStartAndDetailsAsItsPrimaryActions() throws Exception {
        var f=new MenuFlowTest.Fixture();Room r=f.addRoom(0);r.join(f.player.getUniqueId(),"Owner");f.menus.room(f.player,r);
        assertEquals(List.of("ready","bots","options","back","close"),ids(f));
        assertFalse(MessageText.plain(f.description).contains(MessageText.plain(RoomText.options(r.kind,r.options))));
        click(f,"options");assertFalse(ids(f).contains("details"));
        assertTrue(MessageText.plain(f.description).contains(MessageText.plain(RoomText.options(r.kind,r.options))));
    }
    private static Room handRoom(MenuFlowTest.Fixture f){
        Room r=new Room(UUID.randomUUID(),"lastcard",2,0,0);r.board=new LastCardGame(2,0);
        if(r.board.currentPlayer()==1)r.join(UUID.randomUUID(),"Other player");
        r.join(f.player.getUniqueId(),"Owner");if(r.seats.size()==1)r.join(UUID.randomUUID(),"Other player");
        r.phase=Room.Phase.PLAYING;f.plugin.rooms.put(r.id,r);return r;
    }
    private static List<String> ids(MenuFlowTest.Fixture f){return f.buttons.stream().map(GameMenus.Button::id).toList();}
    private static void click(MenuFlowTest.Fixture f,String id){f.buttons.stream().filter(b->b.id().equals(id)).findFirst().orElseThrow().action().run();}
}
