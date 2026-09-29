package dev.tabletop3d;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TableComfortFocusTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}

    @Test void everySeatLooksTowardTableAndReleaseRestoresExactPose(){
        for(int seat=0;seat<4;seat++){
            Fixture f=new Fixture(seat);Location original=f.location.get().clone();
            f.input(true);assertTrue(f.comfort.focused(f.player));Location focus=f.location.get();
            assertTrue(focus.getY()>original.getY());
            double angle=2*Math.PI*seat/4;
            assertEquals(Math.sin(angle)*1.45,focus.getX(),.0001);
            assertEquals(Math.cos(angle)*1.45,focus.getZ(),.0001);
            var hand=HandTable.handPose(seat,4,8,17,true);
            assertTrue(Math.hypot(focus.getX(),focus.getZ())>Math.hypot(hand.x(),hand.z())+.026,"Camera stays on the front side of the standing hand");
            assertEquals(original.getY()+1.35,focus.getY(),.0001);
            assertTrue(focus.getPitch()>45&&focus.getPitch()<80);
            assertTrue(focus.getDirection().dot(new org.bukkit.util.Vector(-focus.getX(),0,-focus.getZ()))>0);
            verify(f.player).setGravity(false);verify(f.player).setInvisible(true);f.input(false);
            verify(f.player).setInvisible(false);
            assertEquals(original,f.location.get());assertFalse(f.comfort.focused(f.player));verify(f.player).setGravity(true);
        }
    }
    @Test void movementIsAnchoredButAimRemainsAvailable(){
        Fixture f=new Fixture(0);f.input(true);Location anchor=f.location.get().clone();
        Location attempted=anchor.clone().add(4,2,4);attempted.setYaw(57);attempted.setPitch(73);
        PlayerMoveEvent event=new PlayerMoveEvent(f.player,anchor,attempted);MockBukkit.getMock().getPluginManager().callEvent(event);
        assertEquals(anchor.getX(),event.getTo().getX());assertEquals(anchor.getY(),event.getTo().getY());assertEquals(anchor.getZ(),event.getTo().getZ());
        assertEquals(57,event.getTo().getYaw());assertEquals(73,event.getTo().getPitch());
    }
    @Test void externalTeleportWinsAndHoldingShiftDoesNotReenter(){
        Fixture f=new Fixture(0);f.input(true);Location external=new Location(f.world,20,85,20);
        assertTrue(f.player.teleport(external));f.input(true);f.input(false);
        assertEquals(external,f.location.get());assertFalse(f.comfort.focused(f.player));verify(f.player).setGravity(true);
    }
    @Test void cancelledExternalTeleportLeavesFocusUntilRelease(){
        Fixture f=new Fixture(0);Location original=f.location.get().clone();f.input(true);
        PlayerTeleportEvent event=new PlayerTeleportEvent(f.player,f.location.get(),new Location(f.world,20,85,20));event.setCancelled(true);
        MockBukkit.getMock().getPluginManager().callEvent(event);assertTrue(f.comfort.focused(f.player));f.input(false);assertEquals(original,f.location.get());
    }
    @Test void roomOrPermissionLossRestoresOriginalLocation(){
        for(boolean permission:List.of(false,true)){
            Fixture f=new Fixture(0);Location original=f.location.get().clone();f.input(true);
            if(permission)when(f.plugin.allowed(f.player)).thenReturn(false);else when(f.plugin.room(f.player)).thenReturn(null);
            f.sync();assertEquals(original,f.location.get());verify(f.player).setGravity(true);verify(f.player).setCollidable(true);
        }
    }
    @Test void invalidRoomPermissionVehicleAndBlockedSpaceCannotStart(){
        for(int reason=0;reason<7;reason++){
            Fixture f=new Fixture(0);Location original=f.location.get().clone();
            switch(reason){case 0->when(f.plugin.room(f.player)).thenReturn(null);case 1->when(f.plugin.allowed(f.player)).thenReturn(false);case 2->when(f.player.isInsideVehicle()).thenReturn(true);case 3->when(f.block.isPassable()).thenReturn(false);case 4->f.room.seats.clear();case 5->when(f.plugin.arena.atTableWorld(f.player,f.room)).thenReturn(false);case 6->f.location.set(new Location(f.world,100,80,100));}
            original=f.location.get().clone();
            f.input(true);assertEquals(original,f.location.get());verify(f.player,never()).setGravity(false);
        }
    }
    @Test void rejectedFocusTeleportRestoresGravityWithoutMovingPlayer(){
        Fixture f=new Fixture(0);Location original=f.location.get().clone();when(f.player.teleport(any(Location.class))).thenReturn(false);
        f.input(true);assertFalse(f.comfort.focused(f.player));assertEquals(original,f.location.get());verify(f.player).setGravity(true);
    }
    @Test void occupiedOriginalPositionIsNotUsedWhenReleasingFocus(){
        Fixture f=new Fixture(0);f.input(true);Location anchor=f.location.get().clone();
        when(f.block.isPassable()).thenReturn(false);f.input(false);
        assertFalse(f.comfort.focused(f.player));assertEquals(anchor,f.location.get());
        verify(f.player).setGravity(true);verify(f.player,times(1)).teleport(any(Location.class));
    }
    @Test void redirectedFocusTeleportDoesNotRewindTheOtherPluginsDestination(){
        Fixture f=new Fixture(0);Location destination=new Location(f.world,20,85,20);
        doAnswer(inv->{PlayerTeleportEvent event=new PlayerTeleportEvent(f.player,f.location.get().clone(),destination,PlayerTeleportEvent.TeleportCause.PLUGIN);MockBukkit.getMock().getPluginManager().callEvent(event);f.location.set(destination);return true;}).when(f.player).teleport(any(Location.class));
        f.input(true);f.input(false);assertFalse(f.comfort.focused(f.player));assertEquals(destination,f.location.get());verify(f.player).setGravity(true);
    }
    @Test void worldChangeAndDeathClearFocusWithoutTeleportingBack(){
        for(boolean death:List.of(false,true)){
            Fixture f=new Fixture(0);f.input(true);Location anchor=f.location.get().clone();
            if(death){org.bukkit.event.entity.PlayerDeathEvent event=mock(org.bukkit.event.entity.PlayerDeathEvent.class);when(event.getEntity()).thenReturn(f.player);f.comfort.death(event);}
            else{World other=mock(World.class);f.location.set(new Location(other,12,70,12));when(f.plugin.arena.atTableWorld(f.player,f.room)).thenReturn(false);f.comfort.world(new PlayerChangedWorldEvent(f.player,f.world));}
            assertFalse(f.comfort.focused(f.player));verify(f.player).setGravity(true);verify(f.player,times(1)).teleport(any(Location.class));
            if(death)assertEquals(anchor,f.location.get());
        }
    }
    @Test void quitReturnsToTheOriginalPoseAndRestoresFlags(){
        Fixture f=new Fixture(0);Location original=f.location.get().clone();f.input(true);
        f.comfort.quit(new PlayerQuitEvent(f.player,net.kyori.adventure.text.Component.empty()));
        assertFalse(f.comfort.focused(f.player));assertEquals(original,f.location.get());verify(f.player).setGravity(true);verify(f.player).setCollidable(true);
    }
    @Test void closeRestoresPreexistingGravityAndCollisionFlags(){
        Fixture f=new Fixture(0);when(f.player.hasGravity()).thenReturn(false);when(f.player.isCollidable()).thenReturn(false);
        Location original=f.location.get().clone();f.input(true);
        try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player));f.comfort.close();}
        assertEquals(original,f.location.get());verify(f.player,never()).setGravity(true);verify(f.player,never()).setCollidable(true);
    }
    @Test void focusHidesWholePlayerFromPeersAndRestoresPreexistingInvisibility(){
        Fixture f=new Fixture(0);Player peer=mock(Player.class);when(peer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(f.player.isInvisible()).thenReturn(true);
        try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player,peer));
            f.input(true);verify(peer).hidePlayer(f.plugin,f.player);f.input(false);
            verify(peer).showPlayer(f.plugin,f.player);verify(f.player,never()).setInvisible(false);
        }
    }
    static final class Fixture {
        final Tabletop3D plugin=mock(Tabletop3D.class);final Player player=mock(Player.class);final World world=mock(World.class);final Block block=mock(Block.class);
        final Room room=new Room(UUID.randomUUID(),"mahjong",4,0,0);final AtomicReference<Location> location=new AtomicReference<>();final TableComfort comfort;
        Fixture(int seat){
            when(plugin.getName()).thenReturn("3dtabletop");when(plugin.isEnabled()).thenReturn(true);when(plugin.getServer()).thenReturn(MockBukkit.getMock());
            when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());
            plugin.arena=mock(GameWorld.class);when(plugin.room(player)).thenReturn(room);when(plugin.allowed(player)).thenReturn(true);
            when(plugin.arena.atTableWorld(player,room)).thenReturn(true);when(plugin.arena.center(0)).thenAnswer(inv->new Location(world,0,80,0));
            when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);when(world.getBlockAt(any(Location.class))).thenReturn(block);when(block.isPassable()).thenReturn(true);
            UUID id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);when(player.getWorld()).thenReturn(world);
            for(int i=0;i<seat;i++)room.join(UUID.randomUUID(),"other");room.join(id,"player");
            double angle=2*Math.PI*seat/4;location.set(new Location(world,Math.sin(angle)*2.25,80,Math.cos(angle)*2.25,33,21));
            when(player.getLocation()).thenAnswer(inv->location.get().clone());when(player.hasGravity()).thenReturn(true);when(player.isCollidable()).thenReturn(true);
            when(player.teleport(any(Location.class))).thenAnswer(inv->{Location target=((Location)inv.getArgument(0)).clone();PlayerTeleportEvent event=new PlayerTeleportEvent(player,location.get().clone(),target,PlayerTeleportEvent.TeleportCause.PLUGIN);MockBukkit.getMock().getPluginManager().callEvent(event);if(event.isCancelled())return false;location.set(event.getTo().clone());return true;});
            comfort=new TableComfort(plugin);
        }
        void input(boolean held){Input input=mock(Input.class);when(input.isSneak()).thenReturn(held);MockBukkit.getMock().getPluginManager().callEvent(new PlayerInputEvent(player,input));}
        void sync(){try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));comfort.sync();}}
    }
}
