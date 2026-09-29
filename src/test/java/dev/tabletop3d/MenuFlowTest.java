package dev.tabletop3d;

import dev.tabletop3d.rules.GameFactory;
import dev.tabletop3d.ui.MessageText;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuFlowTest {
    @Test void catalogOpensSetupAndRulesReachCreationWithoutChangingTheDefaultDraft() throws Exception {
        Fixture f=new Fixture();f.menus.main(f.player);
        f.buttons.stream().filter(b->b.id().equals("ludo")).findFirst().orElseThrow().action().run();
        assertTrue(f.buttons.stream().anyMatch(b->b.id().equals("mode")));
        f.buttons.stream().filter(b->b.id().equals("details")).findFirst().orElseThrow().action().run();
        f.buttons.stream().filter(b->b.id().equals("rule-blocking")).findFirst().orElseThrow().action().run();
        assertTrue(f.buttons.stream().anyMatch(b->b.id().equals("rule-blocking")&&b.label().contains("Any Pawn Blocks Passage")));
        f.buttons.stream().filter(b->b.id().equals("back")).findFirst().orElseThrow().action().run();
        f.buttons.stream().filter(b->b.id().equals("start")).findFirst().orElseThrow().action().run();
        verify(f.plugin).create(f.player,"ludo",4,Map.of("blocking","on"));
        f.menus.setup(f.player,"ludo");f.buttons.stream().filter(b->b.id().equals("details")).findFirst().orElseThrow().action().run();
        assertTrue(f.buttons.stream().anyMatch(b->b.id().equals("rule-blocking")&&b.label().contains("Blocking: Off")));
    }
    @Test void botSetupStartsOnlyTheSuccessfullyCreatedRoom() throws Exception {
        Fixture f=new Fixture();f.menus.setup(f.player,"chess");
        f.buttons.stream().filter(b->b.id().equals("mode")).findFirst().orElseThrow().action().run();
        doAnswer(i->{Room r=f.addRoom(0);r.join(f.player.getUniqueId(),"Player");return null;}).when(f.plugin).create(f.player,"chess",2,Map.of());
        f.buttons.stream().filter(b->b.id().equals("start")).findFirst().orElseThrow().action().run();
        verify(f.plugin).startWithBots(f.player,f.plugin.room(f.player));
    }
    @Test void roomBrowserReachesEveryRoomAndKeepsCreateOnEachPage() throws Exception {
        Fixture f=new Fixture();for(int i=0;i<25;i++)f.addRoom(i);
        f.menus.games(f.player,"chess");Set<String> seen=new HashSet<>();
        for(int page=0;page<4;page++) {
            assertTrue(f.buttons.stream().anyMatch(b->b.label().equals("Create Room")));
            for(Room r:f.plugin.rooms.values())if(f.buttons.stream().anyMatch(b->b.label().contains(r.id.toString().substring(0,6))))seen.add(r.id.toString());
            var next=f.buttons.stream().filter(b->b.id().equals("next")).findFirst();
            if(next.isEmpty())break;next.get().action().run();
        }
        assertEquals(25,seen.size(),"Room limit is configurable; browse beyond the first twelve");
        assertTrue(f.buttons.stream().anyMatch(b->b.id().equals("previous")));
    }
    @Test void staleRoomListingCannotJoinOrVisitARemovedTable() throws Exception {
        Fixture f=new Fixture();Room r=f.addRoom(0);f.menus.games(f.player,"chess");
        var entry=f.buttons.stream().filter(b->b.label().contains(r.id.toString().substring(0,6))).findFirst().orElseThrow();
        f.plugin.rooms.remove(r.id);entry.action().run();
        verify(f.plugin,never()).join(any(),any());
        assertFalse(f.buttons.stream().anyMatch(b->b.label().contains(r.id.toString().substring(0,6))));
    }
    @Test void roomReturnsToTheTableAndRulesRemainAvailableFromTheCommand() throws Exception {
        Fixture f=new Fixture();Room r=f.addRoom(0);r.join(f.player.getUniqueId(),"Player");r.fillBots();r.board=GameFactory.create("chess",2,0);r.phase=Room.Phase.PLAYING;
        f.menus.room(f.player,r);
        assertFalse(f.buttons.stream().anyMatch(b->b.id().equals("controls")));
        f.buttons.stream().filter(b->b.id().equals("play")).findFirst().orElseThrow().action().run();
        verify(f.plugin).enterArena(f.player,r);
        f.menus.roomOptions(f.player,r);
        assertFalse(f.buttons.stream().anyMatch(b->b.id().equals("rules")));
        f.menus.rules(f.player,"chess");
        assertTrue(MessageText.plain(f.description).contains("castling"));
    }
    @Test void leaveConfirmationCannotLeaveANewerRoom() throws Exception {
        Fixture f=new Fixture();Room old=f.addRoom(0);old.join(f.player.getUniqueId(),"Player");
        f.menus.confirmLeave(f.player);var confirm=f.buttons.stream().filter(b->b.id().equals("entry")).findFirst().orElseThrow();
        f.plugin.rooms.remove(old.id);Room next=f.addRoom(1);next.join(f.player.getUniqueId(),"Player");
        confirm.action().run();verify(f.plugin,never()).leave(f.player);
    }
    @Test void sourceChoiceRebuildsAfterTheBoardRevisionChanges() throws Exception {
        Fixture f=new Fixture();Room r=f.addRoom(0);r.join(f.player.getUniqueId(),"Player");r.fillBots();r.board=GameFactory.create("chess",2,0);r.phase=Room.Phase.PLAYING;
        f.menus.boardSources(f.player,r,0);var pawn=f.buttons.stream().filter(b->b.label().contains("e2")).findFirst().orElseThrow();
        r.board.apply(0,"move:e2:e4");r.revision++;pawn.action().run();
        assertTrue(f.buttons.stream().noneMatch(b->b.label().contains("e2")),"A stale source must rebuild from current legal moves");
    }
    @Test void goRulesNeverAppendThePlayersCurrentGomokuRules() throws Exception {
        Fixture f=new Fixture();Room r=new Room(UUID.randomUUID(),"gomoku",2,0,0);f.plugin.rooms.put(r.id,r);
        r.join(f.player.getUniqueId(),"Player");r.board=spy(GameFactory.create("gomoku",2,0));
        when(r.board.publicInfo()).thenReturn(Map.of("rules","GOMOKU_ONLY_RULES"));
        f.menus.rules(f.player,"go");assertFalse(MessageText.plain(f.description).contains("GOMOKU_ONLY_RULES"));
    }
    @Test void aDropMenuNamesHumanColumnsAndPlaysWithoutASecondChoiceDialog() throws Exception {
        Fixture f=new Fixture();Room r=new Room(UUID.randomUUID(),"connectfour",2,0,0);f.plugin.rooms.put(r.id,r);
        r.join(f.player.getUniqueId(),"Player");r.fillBots();r.board=GameFactory.create("connectfour",2,0);r.phase=Room.Phase.PLAYING;
        f.menus.boardSources(f.player,r,0);
        var column=f.buttons.stream().filter(b->b.label().equals("Drop in column 1")).findFirst().orElseThrow();
        column.action().run();verify(f.plugin).action(eq(f.player),eq(r),eq(r.revision),eq(new com.google.gson.JsonPrimitive("drop:0")));
    }
    @Test void historicalStockLabelsFollowNamedMessagesButCustomCaptionsStayUntouched() {
        for(String id:List.of("bots","play","options")) {
            String source=switch(id){case "bots"->"&f补齐陪练并开始";case "play"->"&f回到对局";default->"&f房间选项";};
            var config=new YamlConfiguration();config.set("Bottom.buttons."+id+".text",source);
            var button=new GameMenus.Button(id,Component.text("Custom named label"),()->{});
            var result=GameMenuLayouts.render(config,Component.text("Title"),Component.empty(),List.of(button),UUID.randomUUID());
            assertEquals("Custom named label",MessageText.plain((Component)result.config().get("Bottom.buttons.slot0.component")));
            config=new YamlConfiguration();config.set("Bottom.buttons."+id+".text","<red>My caption</red>");
            result=GameMenuLayouts.render(config,Component.text("Title"),Component.empty(),List.of(button),UUID.randomUUID());
            assertEquals("My caption",MessageText.plain((Component)result.config().get("Bottom.buttons.slot0.component")));
        }
    }
    static final class Fixture {
        final Tabletop3D plugin=mock(Tabletop3D.class);final Player player=mock(Player.class);
        final GameMenus menus;List<GameMenus.Button> buttons=List.of();Component description;
        Fixture() throws Exception {
            TabletopTest.set(plugin,"rooms",new LinkedHashMap<UUID,Room>());
            when(plugin.allowed(player)).thenReturn(true);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            World world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());when(player.getWorld()).thenReturn(world);
            when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            when(plugin.room(player)).thenAnswer(i->plugin.rooms.values().stream().filter(r->r.seat(player.getUniqueId())>=0).findFirst().orElse(null));
            var window=mock(BoardWindow.class);
            when(window.render(anyString(),any(Component.class),any(Component.class),anyList(),any())).thenAnswer(i->{
                description=i.getArgument(2);buttons=List.copyOf(i.getArgument(3));
                return GameMenuLayouts.render(new YamlConfiguration(),i.getArgument(1,Component.class),description,buttons,i.getArgument(4));
            });
            when(window.open(any(),any(),anyString())).thenReturn(true);menus=new GameMenus(plugin,window);plugin.menus=menus;
        }
        Room addRoom(int n){Room r=new Room(UUID.randomUUID(),"chess",2,n,n);plugin.rooms.put(r.id,r);return r;}
    }
}
