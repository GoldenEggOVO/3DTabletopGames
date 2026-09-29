package dev.tabletop3d;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MahjongTableControlsTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void matchingHandAndPublicTilesGlowPrivatelyFromEitherTargetAndZeroIsRed() throws Exception {
        var f=new HandTableTest.Fixture("mahjong");
        when(f.game.hand(0)).thenReturn(List.of(new dev.tabletop3d.rules.HandGame.Piece("a","m0"),new dev.tabletop3d.rules.HandGame.Piece("b","m5")));
        when(f.game.discards(1)).thenReturn(List.of(new dev.tabletop3d.rules.HandGame.Piece("c","m5"),new dev.tabletop3d.rules.HandGame.Piece("d","m5")));
        f.room.revision++;f.table.sync();f.table.hover(f.owner,"a");
        Object own=((Map<?,?>)TableViewTest.field(f.table,"privateViews")).get(f.owner.getUniqueId());
        Map<?,?> pieces=(Map<?,?>)TableViewTest.field(own,"pieces"),matches=(Map<?,?>)TableViewTest.field(own,"matches");
        for(Object piece:pieces.values())verify((Entity)((List<?>)TableViewTest.field(piece,"parts")).getFirst()).setGlowing(true);
        assertEquals(2,matches.size());
        for(Object value:matches.values()){
            Entity e=(Entity)value;verify(e).setVisibleByDefault(false);verify(f.owner).showEntity(f.plugin,e);verify(f.spectator,never()).showEntity(f.plugin,e);
        }
        var label=(org.bukkit.entity.TextDisplay)TableViewTest.field(own,"remaining");
        verify(label).text(Language.component("table.mahjong.remaining","count",0).colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.RED));
        String publicId=String.valueOf(matches.keySet().iterator().next());
        Map<?,?> publicPieces=(Map<?,?>)TableViewTest.field(f.table,"publicPieces");
        Entity body=(Entity)((List<?>)TableViewTest.field(publicPieces.get(publicId),"parts")).getFirst();
        Location eye=body.getLocation().clone().add(0,.8,0);
        assertEquals("public:"+publicId,f.table.hit(f.owner,eye,new Vector(0,-1,0)));
        f.table.hover(f.owner,"public:"+publicId);assertEquals(2,matches.size());
        f.table.hover(f.owner,null);assertTrue(matches.isEmpty());verify(f.owner,atLeastOnce()).showEntity(f.plugin,body);
        verify(f.game,never()).hand(1);f.table.close();
    }
    @Test void mahjongHandOrderRunsTowardTheOwnersRightAndMeldsStayAtTheirRightCorner(){
        for(int seat=0;seat<4;seat++){
            double angle=seat*Math.PI/2;
            Vector towardCenter=new Vector(-Math.sin(angle),0,-Math.cos(angle));
            Vector right=towardCenter.crossProduct(new Vector(0,1,0));
            var first=HandTable.handPose(seat,4,0,14,true);var last=HandTable.handPose(seat,4,13,14,true);
            assertTrue(new Vector(last.x()-first.x(),0,last.z()-first.z()).dot(right)>1);
            var meld=HandTable.exposedPose(seat,4,0);
            assertTrue(new Vector(meld.x(),0,meld.z()).dot(right)>1);
        }
    }
    @Test void drawingKeepsOldTilesStillAndLeavesTheNewTileSeparateOnTheRight(){
        for(int seat=0;seat<4;seat++)for(int count:new int[]{1,4,7,10,13,16}){
            for(int i=0;i<count;i++)assertEquals(HandTable.handPose(seat,4,i,count,true),HandTable.handPose(seat,4,i,count+1,true));
            var last=HandTable.handPose(seat,4,count-1,count+1,true);var drawn=HandTable.handPose(seat,4,count,count+1,true);
            assertTrue(Math.hypot(drawn.x()-last.x(),drawn.z()-last.z())>.16,"The drawn tile needs a visible gap");
        }
    }
    @Test void evenASinglePonCombinationIsShownOnlyAfterSelectingPon() throws Exception {
        var f=new HandTableTest.Fixture("mahjong");
        when(f.game.hand(0)).thenReturn(List.of(new dev.tabletop3d.rules.HandGame.Piece("a","m1"),new dev.tabletop3d.rules.HandGame.Piece("b","m1")));
        when(f.game.publicInfo()).thenReturn(Map.of("offeredTile","m1"));
        when(f.game.legalActions(0)).thenReturn(List.of("pon:a,b","pass"));f.table.show(f.owner);
        Object own=((Map<?,?>)TableViewTest.field(f.table,"privateViews")).get(f.owner.getUniqueId());
        Map<?,?> calls=(Map<?,?>)TableViewTest.field(own,"calls");
        assertEquals(2,((List<?>)TableViewTest.field(calls.get("pon"),"parts")).size(),"First stage has only the action button");
        assertTrue(f.table.expandCall(f.owner,"pon"));
        assertEquals(Set.of("choice:pon:a,b","back","pass"),calls.keySet());
        assertTrue(((List<?>)TableViewTest.field(calls.get("choice:pon:a,b"),"parts")).size()>5);
        f.table.close();
    }
    @Test void illegalTilesAreGrayAndRiichiIsArmedByItsButton() throws Exception {
        var f=new HandTableTest.Fixture("mahjong");
        when(f.game.legalActions(0)).thenReturn(List.of("riichi:b","discard:a","discard:b"));f.table.show(f.owner);
        Object own=((Map<?,?>)TableViewTest.field(f.table,"privateViews")).get(f.owner.getUniqueId());
        Map<?,?> pieces=(Map<?,?>)TableViewTest.field(own,"pieces");
        assertTrue(f.table.expandCall(f.owner,"riichi"));
        Entity disabled=(Entity)((List<?>)TableViewTest.field(pieces.get("a"),"parts")).getFirst();
        verify((org.bukkit.entity.BlockDisplay)disabled).setBlock(org.bukkit.Material.GRAY_CONCRETE.createBlockData());
        assertNull(f.table.handAction(f.owner,"a"));
        assertEquals("riichi:b",f.table.handAction(f.owner,"b"));
        assertTrue(f.table.expandCall(f.owner,"back"));
        verify((org.bukkit.entity.BlockDisplay)disabled,atLeastOnce()).setBlock(org.bukkit.Material.SMOOTH_QUARTZ.createBlockData());
        when(f.game.legalActions(0)).thenReturn(List.of("discard:b"));f.room.revision++;f.table.show(f.owner);
        verify((org.bukkit.entity.BlockDisplay)disabled,atLeast(2)).setBlock(org.bukkit.Material.GRAY_CONCRETE.createBlockData());
        f.table.close();
    }
    @Test void repeatedDiscardPressCannotHitAReplacementTileUntilAimChangesOrClicksStop(){
        var f=new HandTableTest.Fixture("mahjong");
        when(f.owner.getEyeLocation()).thenReturn(f.origin.clone().add(0,1,1.8).setDirection(new Vector(0,-1,-1)));
        when(f.game.legalActions(0)).thenReturn(List.of("discard:a","discard:b"));
        assertEquals("discard:a",f.table.handAction(f.owner,"a",1_000_000_000L));
        assertNull(f.table.handAction(f.owner,"b",1_250_000_000L));
        assertNull(f.table.handAction(f.owner,"b",1_800_000_000L),"Each rapid rejected click keeps the guard active");
        when(f.game.legalActions(0)).thenReturn(List.of());
        f.table.keepHandPress(f.owner,2_200_000_000L);f.table.keepHandPress(f.owner,2_700_000_000L);
        when(f.game.legalActions(0)).thenReturn(List.of("discard:a","discard:b"));
        assertNull(f.table.handAction(f.owner,"b",3_200_000_000L),"Continuous presses during the other seats' turns must still protect the next draw");
        when(f.owner.getEyeLocation()).thenReturn(f.origin.clone().add(0,1,1.8).setDirection(new Vector(.15,-1,-1)));
        assertEquals("discard:b",f.table.handAction(f.owner,"b",3_350_000_000L));
        assertEquals("discard:a",f.table.handAction(f.owner,"a",4_050_000_000L));
        f.table.close();
    }
    @Test void sichuanPreparationCanSelectRemoveConfirmAndChooseAMissingSuitAtTheTable() throws Exception {
        var f=new HandTableTest.Fixture("mahjong");
        when(f.game.legalActions(0)).thenReturn(List.of("exchange-add:a","exchange-remove:b","exchange-confirm"));f.table.show(f.owner);
        assertEquals("exchange-add:a",f.table.handAction(f.owner,"a"));
        assertEquals("exchange-remove:b",f.table.handAction(f.owner,"b"));
        Object own=((Map<?,?>)TableViewTest.field(f.table,"privateViews")).get(f.owner.getUniqueId());
        Map<?,?> calls=(Map<?,?>)TableViewTest.field(own,"calls");assertTrue(calls.containsKey("exchange-confirm"));
        Map<?,?> pieces=(Map<?,?>)TableViewTest.field(own,"pieces");
        Entity selected=(Entity)((List<?>)TableViewTest.field(pieces.get("b"),"parts")).getFirst();verify(selected).setGlowing(true);
        when(f.game.legalActions(0)).thenReturn(List.of("missing:m","missing:p","missing:s"));f.room.revision++;f.table.show(f.owner);
        assertEquals(Set.of("missing-m","missing-p","missing-s"),calls.keySet());
        assertEquals(List.of("missing:p"),MahjongControls.groups(f.game.legalActions(0)).get("missing-p"));
        f.table.close();
    }
    @Test void doraIndicatorsUseFullSizeNativeTilesOnTheMiddleOfTheFrame() throws Exception {
        var f=new HandTableTest.Fixture("mahjong");
        when(f.game.publicInfo()).thenReturn(Map.of("dora","m1,p2,s3","doraIds","d1,d2,d3"));f.room.revision++;f.table.sync();
        Map<?,?> pieces=(Map<?,?>)TableViewTest.field(f.table,"publicPieces");
        for(int i=0;i<3;i++){
            Object tile=pieces.get("dora:d"+(i+1));assertNotNull(tile);
            Entity body=(Entity)((List<?>)TableViewTest.field(tile,"parts")).getFirst();
            assertEquals(1.44,Math.abs(body.getLocation().getZ()-f.origin.getZ()),.00001);
            assertTrue(Math.abs(body.getLocation().getX()-f.origin.getX())<.15);
            assertTrue(body.getLocation().getY()>f.origin.getY()+.157,"Frame indicators must clear the concealed standing row from the lowered Shift view");
            var transforms=org.mockito.ArgumentCaptor.forClass(org.bukkit.util.Transformation.class);
            verify((org.bukkit.entity.BlockDisplay)body).setTransformation(transforms.capture());
            assertEquals(.094,transforms.getValue().getScale().x,.00001);
        }
        f.table.close();
    }
    @Test void mahjongHoverLiftsOnlySelectedTileAndRestoresIt() throws Exception {
        var f=new HandTableTest.Fixture("mahjong");int start=f.entities.size();f.table.show(f.owner);
        Map<Entity,Location> positions=new HashMap<>();
        Object own=((Map<?,?>)TableViewTest.field(f.table,"privateViews")).get(f.owner.getUniqueId());
        for(Object piece:((Map<?,?>)TableViewTest.field(own,"pieces")).values())
            for(Entity e:(List<Entity>)TableViewTest.field(piece,"parts"))positions.put(e,e.getLocation());
        Entity first=f.entities.get(start);f.table.hover(f.owner,"a");
        for(int i=0;i<5;i++)f.table.tick();
        assertEquals(positions.get(first).getY()+.065,first.getLocation().getY(),.00001);
        for(var e:positions.entrySet()){
            assertEquals(e.getValue().getX(),e.getKey().getLocation().getX(),.00001,"No horizontal spreading");
            assertEquals(e.getValue().getZ(),e.getKey().getLocation().getZ(),.00001);
        }
        verify(first).setGlowing(true);
        f.table.hover(f.owner,null);verify(first,atLeastOnce()).setGlowing(false);
        for(int i=0;i<5;i++)f.table.tick();
        for(var e:positions.entrySet())assertEquals(e.getValue(),e.getKey().getLocation());
        f.table.close();
    }
    @Test void selectedTileShowsPrivateRemainingCountAndClearsWithHover() throws Exception {
        var f=new HandTableTest.Fixture("mahjong");
        when(f.game.hand(0)).thenReturn(List.of(new dev.tabletop3d.rules.HandGame.Piece("a","m1"),new dev.tabletop3d.rules.HandGame.Piece("b","m1")));
        f.table.hover(f.owner,"a");
        Object own=((Map<?,?>)TableViewTest.field(f.table,"privateViews")).get(f.owner.getUniqueId());
        var label=(org.bukkit.entity.TextDisplay)TableViewTest.field(own,"remaining");
        verify(label).text(Language.component("table.mahjong.remaining","count",2).colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        verify(label).setVisibleByDefault(false);verify(f.spectator,never()).showEntity(f.plugin,label);
        f.table.hover(f.owner,null);verify(label,atLeastOnce()).text(net.kyori.adventure.text.Component.empty());
        f.table.clear(f.owner);verify(label).remove();f.table.close();
    }
    @Test void chiExpandsIntoClickableExactCombinationsAndClearsAfterRevision() throws Exception {
        var f=new HandTableTest.Fixture("mahjong");
        when(f.game.hand(0)).thenReturn(List.of(new dev.tabletop3d.rules.HandGame.Piece("a","m1"),new dev.tabletop3d.rules.HandGame.Piece("b","m2"),new dev.tabletop3d.rules.HandGame.Piece("c","m4")));
        when(f.game.publicInfo()).thenReturn(Map.of("offeredTile","m3"));
        when(f.game.legalActions(0)).thenReturn(List.of("chi:a,b","chi:b,c","pass"));
        assertTrue(f.table.expandCall(f.owner,"chi"));
        Object own=((Map<?,?>)TableViewTest.field(f.table,"privateViews")).get(f.owner.getUniqueId());
        Map<?,?> calls=(Map<?,?>)TableViewTest.field(own,"calls");
        assertEquals(Set.of("choice:chi:a,b","choice:chi:b,c","back","pass"),calls.keySet());
        Object choice=calls.get("choice:chi:a,b");
        var bounds=(org.bukkit.util.BoundingBox)TableViewTest.field(choice,"bounds");
        Location eye=bounds.getCenter().toLocation(f.world).add(0,0,1);
        assertEquals("choice:chi:a,b",f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        assertTrue(((List<?>)TableViewTest.field(choice,"parts")).size()>5,"Preview includes original native tile models");
        assertFalse(f.table.expandCall(f.spectator,"chi"));
        when(f.game.legalActions(0)).thenReturn(List.of("discard:a"));f.room.revision++;f.table.show(f.owner);
        assertTrue(calls.isEmpty());f.table.close();
    }
    @Test void seventeenTileHandsStayInOneRowAndClearAdjacentSeats(){
        List<org.bukkit.util.BoundingBox> boxes=new ArrayList<>();
        for(int seat=0;seat<4;seat++)for(int i=0;i<17;i++){
            var pose=HandTable.handPose(seat,4,i,17,true);assertEquals(0,pose.lift());
            double a=Math.toRadians(pose.yaw()),x=Math.abs(Math.cos(a))*.047+Math.abs(Math.sin(a))*.026,
                z=Math.abs(Math.sin(a))*.047+Math.abs(Math.cos(a))*.026;
            boxes.add(new org.bukkit.util.BoundingBox(pose.x()-x,0,pose.z()-z,pose.x()+x,.14,pose.z()+z));
        }
        for(int i=0;i<boxes.size();i++)for(int j=i+1;j<boxes.size();j++)assertFalse(boxes.get(i).overlaps(boxes.get(j)));
    }
    @Test void groupsKeepAllChoicesAndOnlyPermitRuleLegalSkipping(){
        var groups=MahjongControls.groups(List.of("chi:a,b","chi:a,c","kan-open:a,b,c","pon:a,b","pass"));
        assertEquals(List.of("kan","pon","chi","pass"),new ArrayList<>(groups.keySet()));
        assertEquals(List.of("chi:a,b","chi:a,c"),groups.get("chi"));
        assertEquals(List.of("ron"),new ArrayList<>(MahjongControls.groups(List.of("ron")).keySet()));
        assertEquals(List.of("kan","dismiss"),new ArrayList<>(MahjongControls.groups(List.of("kan-closed:a","discard:a")).keySet()));
        assertEquals(List.of("missing-m","dismiss"),new ArrayList<>(MahjongControls.groups(List.of("exchange:a,b,c","missing:m","discard:a")).keySet()));
    }
    @Test void callButtonsAreOwnerOnlyReachableAndDisappearAfterResponse(){
        var f=new HandTableTest.Fixture("mahjong");
        when(f.game.legalActions(0)).thenReturn(List.of("chi:a,b","chi:a,c","kan-open:a,b,c","pass"));
        int start=f.entities.size();f.table.show(f.owner);
        for(Entity e:f.entities.subList(start,f.entities.size())){
            verify(e).setVisibleByDefault(false);verify(f.owner).showEntity(f.plugin,e);
            verify(f.spectator,never()).showEntity(f.plugin,e);
        }
        Location eye=f.origin.clone().add(0,.32,1.8);
        assertEquals("chi",f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        assertNull(f.table.callHit(f.spectator,eye,new Vector(0,0,-1)));
        assertNull(f.table.callHit(f.owner,eye.clone().add(0,0,6),new Vector(0,0,-1)));
        when(f.world.rayTraceBlocks(any(),any(),anyDouble(),any(),eq(true)))
            .thenReturn(new org.bukkit.util.RayTraceResult(eye.toVector()));
        assertNull(f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        when(f.world.rayTraceBlocks(any(),any(),anyDouble(),any(),eq(true))).thenReturn(null);
        List<Entity> calls=List.copyOf(f.entities.subList(f.entities.size()-6,f.entities.size()));
        when(f.game.legalActions(0)).thenReturn(List.of());f.room.revision++;
        assertNull(f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        calls.forEach(e->verify(e).remove());f.table.close();
    }
    @Test void skipOnOwnTurnOnlyDismissesThePromptAndItReturnsOnTheNextRevision(){
        var f=new HandTableTest.Fixture("mahjong");
        when(f.game.legalActions(0)).thenReturn(List.of("kan-closed:a","discard:b"));f.table.show(f.owner);
        Location eye=f.origin.clone().add(.125,.32,1.8);
        assertEquals("dismiss",f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        f.table.dismissCalls(f.owner);f.table.show(f.owner);
        assertNull(f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        verify(f.game,never()).apply(anyInt(),anyString());
        f.room.revision++;assertEquals("dismiss",f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        f.room.phase=Room.Phase.FINISHED;assertNull(f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        f.table.close();
    }
    @Test void cardTablesIgnoreMahjongButtonHitChecksWithoutRemovingTheHand(){
        var f=new HandTableTest.Fixture("lastcard");int start=f.entities.size();f.table.show(f.owner);
        assertNull(f.table.callHit(f.owner,f.origin.clone().add(0,.32,1.8),new Vector(0,0,-1)));
        for(Entity e:f.entities.subList(start,f.entities.size()))verify(e,never()).remove();f.table.close();
    }
    @Test void tableCallsExecuteSingleChoicesAndKeepMultipleCombinationsSelectable() throws Exception {
        var f=new HandTableTest.Fixture("mahjong");
        var arena=mock(GameWorld.class,CALLS_REAL_METHODS);
        doNothing().when(arena).render(f.room);
        TabletopTest.set(arena,"plugin",f.plugin);TabletopTest.set(arena,"selections",new HashMap<>());
        TabletopTest.set(arena,"views",new HashMap<>());f.plugin.menus=mock(GameMenus.class);
        var click=GameWorld.class.getDeclaredMethod("pickCell",org.bukkit.entity.Player.class,Room.class,int.class,String.class);click.setAccessible(true);
        when(f.game.legalActions(0)).thenReturn(List.of("kan-open:a,b,c","chi:a,b","chi:a,c","pass"));
        click.invoke(arena,f.owner,f.room,0,"@call:kan");
        verify(f.plugin).apply(eq(f.room),eq(0),eq(new com.google.gson.JsonPrimitive("kan-open:a,b,c")),isNull());
        click.invoke(arena,f.owner,f.room,0,"@call:chi");
        verify(f.plugin.menus).boardChoices(f.owner,f.room,List.of("chi:a,b","chi:a,c"),0);
        clearInvocations(f.plugin);when(f.game.legalActions(0)).thenReturn(List.of("discard:a"));
        click.invoke(arena,f.owner,f.room,0,"@call:kan");verify(f.plugin,never()).apply(any(),anyInt(),any(),any());
        f.table.close();
    }
    @Test void pendingUndoRemovesCallsWithoutRequiringARevisionChange(){
        var f=new HandTableTest.Fixture("mahjong");when(f.game.legalActions(0)).thenReturn(List.of("pon:a,b","pass"));
        Location eye=f.origin.clone().add(-.125,.32,1.8);
        assertEquals("pon",f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        f.room.undo=new RoundActions.Undo(f.owner.getUniqueId(),0,30_000,Set.of());
        assertNull(f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        f.room.undo=null;assertEquals("pon",f.table.callHit(f.owner,eye,new Vector(0,0,-1)));
        f.table.close();
    }
}
