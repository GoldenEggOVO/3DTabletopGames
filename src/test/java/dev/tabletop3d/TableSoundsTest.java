package dev.tabletop3d;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import dev.tabletop3d.rules.*;
import com.google.gson.JsonPrimitive;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TableSoundsTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void close(){MockBukkit.unmock();}

    @Test void refreshingMetadataOrReplayingABoardDoesNotMakeAMoveSound()throws Exception{
        var f=new TableViewTest.Fixture("gomoku","place:0,0");
        f.room.revision++;f.view.sync();
        RoundActions.request(f.room,f.room.seats.getFirst().id(),0);RoundActions.apply(f.room);f.view.sync();
        verify(f.world,never()).playSound(any(Location.class),any(Sound.class),anyFloat(),anyFloat());
        verify(f.world,never()).playSound(any(Location.class),any(Sound.class),any(SoundCategory.class),anyFloat(),anyFloat());
        f.view.close();
    }

    @Test void everyCatalogGameAndLegacyFlightHasABoundedNativeMoveSound(){
        Set<Sound> palette=new HashSet<>();
        var kinds=new ArrayList<>(Tabletop3D.NAMES.keySet());kinds.add("aeroplane");
        for(String kind:kinds){
            BoardGame game=GameFactory.create(kind,2,0);List<Cell> before=game.cells();String action=game.legalActions(0).getFirst();game.apply(0,action);
            var cue=TableSounds.move(kind,0,action,before,game.cells());
            assertNotNull(cue.sound(),kind);assertTrue(cue.volume()>0&&cue.volume()<=.4f,kind);assertTrue(cue.pitch()>=.5f&&cue.pitch()<=2f,kind);palette.add(cue.sound());
        }
        assertTrue(palette.size()>=6,"Materials and dice should sound different");
    }

    @Test void capturesUseRuleChangesIncludingEnPassantAndLudoStacks()throws Exception{
        var chess=GameFactory.create("chess",2,0);
        for(String action:List.of("move:e2:e4","move:a7:a6","move:e4:e5","move:d7:d5"))chess.apply(chess.currentPlayer(),action);
        var before=chess.cells();chess.apply(0,"move:e5:d6");
        assertEquals(TableSounds.CAPTURE,TableSounds.move("chess",0,"move:e5:d6",before,chess.cells()));
        var ludo=GameFactory.create("ludo",2,0);
        int[] positions=(int[])TableViewTest.field(ludo,"progress");positions[0]=24;positions[4]=0;positions[5]=0;TabletopTest.set(ludo,"pendingRoll",2);
        before=ludo.cells();ludo.apply(0,"move:0:sk26");
        assertEquals(TableSounds.CAPTURE,TableSounds.move("ludo",0,"move:0:sk26",before,ludo.cells()));
        positions[0]=55;TabletopTest.set(ludo,"current",0);TabletopTest.set(ludo,"pendingRoll",1);before=ludo.cells();ludo.apply(0,"move:0:go0");
        assertEquals(TableSounds.HOME,TableSounds.move("ludo",0,"move:0:go0",before,ludo.cells()));
    }

    @Test void goScoringAndReversiFlipsDoNotSoundLikeCaptures(){
        var go=GameFactory.create("go9",2,0);var before=go.cells();go.apply(0,"place:0,0");
        assertEquals(TableSounds.STONE,TableSounds.move("go9",0,"place:0,0",before,go.cells()));
        assertEquals(TableSounds.PASS,TableSounds.move("go9",0,"pass",before,before));
        assertEquals(TableSounds.SELECT,TableSounds.move("go9",0,"dead:0,0",before,before));
        assertEquals(TableSounds.CONFIRM,TableSounds.move("go9",0,"accept",before,before));
        var reversi=GameFactory.create("reversi",2,0);before=reversi.cells();String action=reversi.legalActions(0).getFirst();reversi.apply(0,action);
        assertEquals(TableSounds.FLIP,TableSounds.move("reversi",0,action,before,reversi.cells()));
    }

    @Test void validBotAndPlayerMovesSoundOnceWhileRejectedActionsAndRefreshesStayQuiet()throws Exception{
        var f=application("gomoku");
        f.plugin.menus=mock(GameMenus.class);when(f.player.isOnline()).thenReturn(true);
        f.plugin.apply(f.room,0,new JsonPrimitive("place:0,0"),f.player);verify(f.plugin.menus).room(f.player,f.room);
        for(int i=0;i<10;i++)f.view.sync();
        f.plugin.apply(f.room,0,new JsonPrimitive("place:0,1"),null);
        f.plugin.apply(f.room,1,new JsonPrimitive("place:0,0"),null);
        verify(f.world,times(1)).playSound(eq(f.view.origin),eq(TableSounds.STONE.sound()),eq(SoundCategory.BLOCKS),anyFloat(),anyFloat());
        f.plugin.apply(f.room,1,new JsonPrimitive("place:1,0"),null);
        verify(f.world,times(2)).playSound(eq(f.view.origin),eq(TableSounds.STONE.sound()),eq(SoundCategory.BLOCKS),anyFloat(),anyFloat());
        assertEquals(2,f.room.history.size());f.view.close();
    }

    @Test void restoreIsSilentButNewRoundsUndoAndResultsHaveDedicatedCues()throws Exception{
        var f=application("gomoku");f.room.restoring=true;f.plugin.start(f.room);
        verify(f.world,never()).playSound(any(Location.class),any(Sound.class),any(SoundCategory.class),anyFloat(),anyFloat());
        f.room.restoring=false;f.plugin.start(f.room);
        verify(f.world).playSound(any(Location.class),eq(TableSounds.START.sound()),eq(SoundCategory.BLOCKS),anyFloat(),eq(TableSounds.START.pitch()));
        f.plugin.apply(f.room,0,new JsonPrimitive("place:0,0"),null);
        RoundActions.request(f.room,f.room.seats.getFirst().id(),0);f.plugin.completeUndo(f.room);
        verify(f.world).playSound(any(Location.class),eq(TableSounds.UNDO.sound()),eq(SoundCategory.BLOCKS),anyFloat(),eq(TableSounds.UNDO.pitch()));
        f.plugin.finish(f.room,"winner:0");f.plugin.finish(f.room,"winner:0");
        verify(f.world,times(1)).playSound(any(Location.class),eq(TableSounds.WIN.sound()),eq(SoundCategory.BLOCKS),anyFloat(),anyFloat());
        f.view.close();
    }

    @Test void winningMoveAndDrawFinishSoundOnceWithoutRepeatingOnRender()throws Exception{
        var f=application("connectfour");
        for(String action:List.of("drop:0","drop:1","drop:0","drop:1","drop:0","drop:1","drop:0"))f.plugin.apply(f.room,f.room.turn(),new JsonPrimitive(action),null);
        assertEquals(Room.Phase.FINISHED,f.room.phase);f.view.sync();f.view.sync();
        verify(f.world,times(7)).playSound(any(Location.class),eq(TableSounds.DROP.sound()),eq(SoundCategory.BLOCKS),anyFloat(),anyFloat());
        verify(f.world,times(1)).playSound(any(Location.class),eq(TableSounds.WIN.sound()),eq(SoundCategory.BLOCKS),anyFloat(),anyFloat());
        f.view.close();var draw=application("chess");draw.plugin.finish(draw.room,"draw:threefold-repetition");
        verify(draw.world).playSound(any(Location.class),eq(TableSounds.DRAW.sound()),eq(SoundCategory.BLOCKS),anyFloat(),eq(TableSounds.DRAW.pitch()));draw.view.close();
    }

    @Test void settingsMuteAllFeedbackAndClampVolume(){
        var plugin=mock(Tabletop3D.class);var config=new YamlConfiguration();when(plugin.getConfig()).thenReturn(config);
        var world=mock(World.class);var at=new Location(world,0,80,0);var player=mock(org.bukkit.entity.Player.class);when(player.getLocation()).thenReturn(at);
        config.set("sounds.enabled",false);TableSounds.play(plugin,at,TableSounds.WIN);TableSounds.select(plugin,player);verifyNoInteractions(world);
        verify(player,never()).playSound(any(Location.class),any(Sound.class),any(SoundCategory.class),anyFloat(),anyFloat());
        config.set("sounds.enabled",true);config.set("sounds.volume",.5);TableSounds.play(plugin,at,TableSounds.STONE);
        verify(world).playSound(eq(at),eq(TableSounds.STONE.sound()),eq(SoundCategory.BLOCKS),eq(TableSounds.STONE.volume()*.5f),eq(TableSounds.STONE.pitch()));
        clearInvocations(world);config.set("sounds.volume",999);TableSounds.play(plugin,at,TableSounds.STONE);
        verify(world).playSound(eq(at),eq(TableSounds.STONE.sound()),eq(SoundCategory.BLOCKS),eq(TableSounds.STONE.volume()),anyFloat());
        clearInvocations(world);for(double volume:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}){config.set("sounds.volume",volume);TableSounds.play(plugin,at,TableSounds.STONE);}verifyNoInteractions(world);
    }

    @Test void turnPromptOnlyReachesTheNearbyEligibleHuman(){
        var server=MockBukkit.getMock();var world=server.addSimpleWorld("sound-table");var player=server.addPlayer();player.teleport(new Location(world,0,80,0));
        var plugin=mock(Tabletop3D.class);when(plugin.getConfig()).thenReturn(new YamlConfiguration());when(plugin.allowed(player)).thenReturn(true);
        var room=new Room(UUID.randomUUID(),"gomoku",2,0,0);room.join(player.getUniqueId(),"Player");room.fillBots();room.board=GameFactory.create("gomoku",2,0);room.phase=Room.Phase.PLAYING;
        TableSounds.turn(plugin,room,player.getLocation());assertEquals(1,player.getHeardSounds().size());
        TableSounds.turn(plugin,room,new Location(world,20,80,0));assertEquals(1,player.getHeardSounds().size());
        TableSounds.turn(plugin,room,new Location(server.addSimpleWorld("elsewhere"),0,80,0));assertEquals(1,player.getHeardSounds().size());
        when(plugin.allowed(player)).thenReturn(false);TableSounds.turn(plugin,room,player.getLocation());assertEquals(1,player.getHeardSounds().size());
        when(plugin.allowed(player)).thenReturn(true);room.board.apply(0,"place:0,0");TableSounds.turn(plugin,room,player.getLocation());assertEquals(1,player.getHeardSounds().size());
    }

    private TableViewTest.Fixture application(String kind)throws Exception{
        var f=new TableViewTest.Fixture(kind);when(f.plugin.getConfig()).thenReturn(new YamlConfiguration());
        TabletopTest.set(f.plugin,"rooms",new LinkedHashMap<>(Map.of(f.room.id,f.room)));
        f.plugin.arena=mock(GameWorld.class,CALLS_REAL_METHODS);TabletopTest.set(f.plugin.arena,"plugin",f.plugin);
        TabletopTest.set(f.plugin.arena,"views",new HashMap<>(Map.of(f.room.id,f.view)));TabletopTest.set(f.plugin.arena,"selections",new HashMap<>());
        doCallRealMethod().when(f.plugin).apply(any(),anyInt(),any(),any());doCallRealMethod().when(f.plugin).start(any());
        doCallRealMethod().when(f.plugin).finish(any(),anyString());doCallRealMethod().when(f.plugin).completeUndo(any());return f;
    }
}
