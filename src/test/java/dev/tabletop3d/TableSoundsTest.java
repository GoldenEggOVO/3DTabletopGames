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
            BoardGame game=GameFactory.create(kind,kind.equals("mahjong")?4:2,0);int seat=game.currentPlayer();List<Cell> before=game.cells();String action=game.legalActions(seat).getFirst();game.apply(seat,action);
            var cue=TableSounds.move(kind,seat,action,before,game.cells());
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

    @Test void mahjongDiscardAndFinalPassIncludeOnlyTheDrawThatActuallyHappened()throws Exception{
        MahjongGame game=mahjong("z1 m1 m2 m3 m4 m5 m6 p1 p2 p3 s4 s5 s6 z2",
            "z1 z1 p1 p2 p4 p5 s1 s2 s4 s5 z2 z3 z4","","");
        assertEquals(List.of(TableSounds.TILE_DISCARD),mahjongMove(game,"discard:s0_0"));
        assertEquals(List.of(TableSounds.PASS,TableSounds.TILE_DRAW),mahjongMove(game,"pass"));
        game=mahjong("z1 m1 m2 m3 m4 m5 m6 p1 p2 p3 s4 s5 s6 z2","","","");
        assertEquals(List.of(TableSounds.TILE_DISCARD,TableSounds.TILE_DRAW),mahjongMove(game,"discard:s0_0"));
    }

    @Test void mahjongMeldsAndReplacementDrawsHaveDistinctMaterialCues()throws Exception{
        MahjongGame game=mahjong("z1 m1 m2 m3 m4 m5 m6 p1 p2 p3 s4 s5 s6 z2",
            "z1 z1 p1 p2 p4 p5 s1 s2 s4 s5 z2 z3 z4","","");
        mahjongMove(game,"discard:s0_0");
        assertEquals(List.of(TableSounds.TILE_PON),mahjongMove(game,"pon:s1_0,s1_1"));
        game=mahjongProfile("riichi","m3 m1 m2 m4 m5 m6 p1 p2 p3 s4 s5 s6 z1 z2","m1 m2 z1","","");
        mahjongMove(game,"discard:s0_0");
        assertEquals(List.of(TableSounds.TILE_CHI),mahjongMove(game,"chi:s1_0,s1_1"));
        game=mahjong("m1 m1 m1 m1 m2 m3 p4 p5 p6 s7 s8 s9 z1 z1","","","");
        assertEquals(List.of(TableSounds.TILE_KAN,TableSounds.TILE_DRAW),mahjongMove(game,"kan-closed:s0_0"));
    }

    @Test void mahjongRobbedKongDoesNotSoundLikeACompletedKongAndWinWaitsForResponses()throws Exception{
        MahjongGame game=mahjong("z1 m1 m2 m3 p1 p2 p3 s1 s2 s3 z2",
            "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1",
            "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1","");
        addPon(game);
        assertEquals(List.of(TableSounds.SELECT),mahjongMove(game,"kan-added:s0_0"));
        assertEquals(List.of(TableSounds.CONFIRM),mahjongMove(game,"ron"));
        assertEquals(List.of(TableSounds.MAHJONG_WIN),mahjongMove(game,"pass"));
        assertEquals("ROUND_END",game.publicInfo().get("phase"));
        assertEquals(List.of(TableSounds.START),mahjongMove(game,"next-hand"));
    }

    @Test void mahjongRiichiIncludesTheDiscardAndKongCompletesAfterRobbersPass()throws Exception{
        var game=mahjongProfile("riichi","m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z2","","","");
        assertEquals(List.of(TableSounds.TILE_DISCARD,TableSounds.RIICHI,TableSounds.TILE_DRAW),mahjongMove(game,"riichi:s0_13"));
        game=mahjong("z1 m1 m2 m3 p1 p2 p3 s1 s2 s3 z2","m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1","","");
        addPon(game);assertEquals(List.of(TableSounds.SELECT),mahjongMove(game,"kan-added:s0_0"));
        assertEquals(List.of(TableSounds.TILE_KAN,TableSounds.TILE_DRAW),mahjongMove(game,"pass"));
    }

    @Test void mahjongCommittedActionsPlayOnceAndRejectedActionsAndRestoreStayQuiet()throws Exception{
        var plugin=mock(Tabletop3D.class);plugin.arena=mock(GameWorld.class);
        var room=new Room(UUID.randomUUID(),"mahjong",4,0,0);room.fillBots();room.phase=Room.Phase.PLAYING;
        room.board=mahjong("z1 m1 m2 m3 m4 m5 m6 p1 p2 p3 s4 s5 s6 z2","","","");
        TabletopTest.set(plugin,"rooms",new LinkedHashMap<>(Map.of(room.id,room)));
        doCallRealMethod().when(plugin).apply(any(),anyInt(),any(),any());
        plugin.apply(room,1,new JsonPrimitive("discard:s0_0"),null);
        verify(plugin.arena,never()).sound(any(),any());
        plugin.apply(room,0,new JsonPrimitive("discard:s0_0"),null);
        verify(plugin.arena).sound(room,TableSounds.TILE_DISCARD);verify(plugin.arena).sound(room,TableSounds.TILE_DRAW);
        clearInvocations(plugin.arena);room.restoring=true;
        plugin.apply(room,room.turn(),new JsonPrimitive(room.board.legalActions(room.turn()).getFirst()),null);
        verify(plugin.arena,never()).sound(any(),any());
    }

    @Test void mahjongFinalSelfDrawHasOneWinCueWithoutTheGenericFinishSound()throws Exception{
        var plugin=mock(Tabletop3D.class);plugin.arena=mock(GameWorld.class);
        var room=new Room(UUID.randomUUID(),"mahjong",4,0,0);room.fillBots();room.phase=Room.Phase.PLAYING;
        room.board=mahjong("m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1 z1","","","");
        TabletopTest.set(room.board,"roundLimit",1);TabletopTest.set(plugin,"rooms",new LinkedHashMap<>(Map.of(room.id,room)));
        doCallRealMethod().when(plugin).apply(any(),anyInt(),any(),any());doCallRealMethod().when(plugin).finish(any(),anyString());
        plugin.apply(room,0,new JsonPrimitive("tsumo"),null);
        assertEquals(Room.Phase.FINISHED,room.phase);verify(plugin.arena,times(1)).sound(any(),any());
        verify(plugin.arena).sound(room,TableSounds.MAHJONG_WIN);
    }

    @Test void drawingAndReplacingAFlowerAfterPassingRonIsNotAKong(){
        var game=mock(HandGame.class);when(game.playerCount()).thenReturn(4);
        when(game.exposed(anyInt())).thenReturn(List.of());
        when(game.publicInfo()).thenReturn(Map.of("phase","RON","wall","10","lastWin",""));
        var before=TableSounds.mahjongState(game);
        when(game.exposed(1)).thenReturn(List.of(new HandGame.Piece("flower","f1")));
        when(game.publicInfo()).thenReturn(Map.of("phase","TURN","wall","8","lastWin",""));
        assertEquals(List.of(TableSounds.PASS,TableSounds.TILE_DRAW),TableSounds.mahjong("pass",before,TableSounds.mahjongState(game)));
    }

    private static List<TableSounds.Cue> mahjongMove(MahjongGame game,String action){
        var before=TableSounds.mahjongState(game);game.apply(game.currentPlayer(),action);
        return TableSounds.mahjong(action,before,TableSounds.mahjongState(game));
    }
    @SuppressWarnings("unchecked") private static void addPon(MahjongGame game)throws Exception{
        var melds=(List<List<dev.tabletop3d.rules.mahjong.Meld>>)TableViewTest.field(game,"melds");
        melds.getFirst().add(new dev.tabletop3d.rules.mahjong.Meld(dev.tabletop3d.rules.mahjong.Meld.Kind.TRIPLET,
            List.of(new dev.tabletop3d.rules.mahjong.Tiles.Tile("a",27,false),new dev.tabletop3d.rules.mahjong.Tiles.Tile("b",27,false),new dev.tabletop3d.rules.mahjong.Tiles.Tile("c",27,false)),true,3));
    }
    private static MahjongGame mahjong(String... hands)throws Exception{return mahjongProfile("guangdong",hands);}
    @SuppressWarnings("unchecked") private static MahjongGame mahjongProfile(String profile,String... hands)throws Exception{
        var game=new MahjongGame(4,0,Map.of("profile",profile,"rounds","4"));
        var values=(List<List<dev.tabletop3d.rules.mahjong.Tiles.Tile>>)TableViewTest.field(game,"hands");
        for(int seat=0;seat<4;seat++){values.get(seat).clear();int index=0;for(String face:hands[seat].split(" "))if(!face.isEmpty())
            values.get(seat).add(new dev.tabletop3d.rules.mahjong.Tiles.Tile("s"+seat+"_"+index++,dev.tabletop3d.rules.mahjong.Tiles.type(face),false));}
        return game;
    }

    private TableViewTest.Fixture application(String kind)throws Exception{
        var f=new TableViewTest.Fixture(kind);when(f.plugin.getConfig()).thenReturn(new YamlConfiguration());
        TabletopTest.set(f.plugin,"rooms",new LinkedHashMap<>(Map.of(f.room.id,f.room)));
        f.plugin.arena=mock(GameWorld.class,CALLS_REAL_METHODS);TabletopTest.set(f.plugin.arena,"plugin",f.plugin);
        TabletopTest.set(f.plugin.arena,"views",new HashMap<>(Map.of(f.room.id,f.view)));TabletopTest.set(f.plugin.arena,"selections",new HashMap<>());
        doCallRealMethod().when(f.plugin).apply(any(),anyInt(),any(),any());doCallRealMethod().when(f.plugin).start(any());
        doCallRealMethod().when(f.plugin).prepareSeats(any());
        doCallRealMethod().when(f.plugin).finish(any(),anyString());doCallRealMethod().when(f.plugin).completeUndo(any());return f;
    }
}
