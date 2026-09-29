package dev.tabletop3d;
import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PortableTableTest {
 @Test void sideTrayHasClearanceAgainstNeighborTablesWithoutMovingLegacyAnchors(){
  assertFalse(TablePlacement.overlaps(0,80,0,false,3,80,0,false));
  assertTrue(TablePlacement.overlaps(0,80,0,true,3,80,0,false));
  assertTrue(TablePlacement.overlaps(3,80,0,false,0,80,0,true));
  assertFalse(TablePlacement.overlaps(0,80,0,true,5,80,0,false));
  assertFalse(TablePlacement.overlaps(0,80,0,true,3,83,0,false));
 }
 @TempDir Path dir;
 @Test void enlargedMahjongEdgeCannotIntersectAnAdjacentSideTray(){
  assertTrue(TablePlacement.overlaps(0,80,0,true,1.125,4.2,80,0,false,1.5));
  assertTrue(TablePlacement.overlaps(4.2,80,0,false,1.5,0,80,0,true,1.125));
  assertFalse(TablePlacement.overlaps(0,80,0,true,1.125,4.5,80,0,false,1.5));
 }
 @BeforeEach void setup(){MockBukkit.mock();}
 @AfterEach void cleanup(){MockBukkit.unmock();}
 @Test void newTablesCannotIntersectExistingModelsButOtherFloorsAndWorldsRemainAvailable()throws Exception{
  var plugin=mock(Tabletop3D.class);TabletopTest.set(plugin,"rooms",new LinkedHashMap<UUID,Room>());
  var arena=mock(GameWorld.class,CALLS_REAL_METHODS);TabletopTest.set(arena,"plugin",plugin);
  World world=MockBukkit.getMock().addSimpleWorld("tables"),other=MockBukkit.getMock().addSimpleWorld("other");
  Room first=new Room(UUID.randomUUID(),"connectfour",2,0,0);arena.anchor(first,new Location(world,0,80,0));plugin.rooms.put(first.id,first);
  Room next=new Room(UUID.randomUUID(),"chess",2,0,1);
  assertThrows(IllegalArgumentException.class,()->arena.anchor(next,new Location(world,0,80,0)));
  assertThrows(IllegalArgumentException.class,()->arena.anchor(next,new Location(world,2,80,2)));
  assertNull(next.anchorWorld,"A rejected placement must not set an anchor");
  assertDoesNotThrow(()->arena.anchor(next,new Location(world,3,80,0)));
  assertDoesNotThrow(()->arena.anchor(next,new Location(world,0,83,0)));
  assertDoesNotThrow(()->arena.anchor(next,new Location(other,0,80,0)));
 }
 @Test void arbitraryWorldAndGridCenterArePreservedAndSeatChecksDoNotEditTerrain() throws Exception {
  var server=MockBukkit.getMock();var w=server.addSimpleWorld("survival");var other=server.addSimpleWorld("creative");
  var plugin=mock(Tabletop3D.class);when(plugin.getName()).thenReturn("3dtabletop");when(plugin.namespace()).thenReturn("3dtabletop");when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());when(plugin.getServer()).thenReturn(server);when(plugin.isEnabled()).thenReturn(true);when(plugin.getDataFolder()).thenReturn(dir.toFile());
  var field=Tabletop3D.class.getDeclaredField("rooms");field.setAccessible(true);field.set(plugin,new LinkedHashMap<UUID,Room>());
  var arena=new GameWorld(plugin);int worlds=server.getWorlds().size();arena.initialize();assertNull(arena.world);assertEquals(worlds,server.getWorlds().size());
  var r=new Room(UUID.randomUUID(),"gomoku",2,1,0);var c=new Location(w,-12.25,83.5,17.75);arena.anchor(r,c);plugin.rooms.put(r.id,r);
  assertEquals(new Location(w,-12,83,18),arena.center(0));
  var p=server.addPlayer();p.teleport(c);assertTrue(arena.atTableWorld(p,r));p.teleport(other.getSpawnLocation());assertFalse(arena.atTableWorld(p,r));
  // MockBukkit does not implement Block.isPassable; use an explicit collision fixture.
  var collisionWorld=spy(w);var block=mock(org.mockbukkit.mockbukkit.block.BlockMock.class);when(block.isPassable()).thenReturn(true);
  doReturn(block).when(collisionWorld).getBlockAt(org.mockito.ArgumentMatchers.any(Location.class));
  try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){
   bukkit.when(()->Bukkit.getWorld(r.anchorWorld)).thenReturn(collisionWorld);
   var seat=arena.seatLocation(r,0);assertEquals(w.getUID(),seat.getWorld().getUID());assertEquals(83,seat.getY());
   when(block.isPassable()).thenReturn(false);assertThrows(IllegalArgumentException.class,()->arena.seatLocation(r,0));
   verify(block,never()).setType(org.mockito.ArgumentMatchers.any(Material.class));
  }
  assertEquals(Material.AIR,w.getBlockAt(c).getType());
  // No runtime initialization or chunk tickets in this fixture; MockBukkit owns teardown.
 }
}

