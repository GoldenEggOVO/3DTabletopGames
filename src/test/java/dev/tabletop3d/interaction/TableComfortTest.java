package dev.tabletop3d.interaction;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.TabletopTest;
import dev.tabletop3d.room.Room;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TableComfortTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void protectionOnlyAppliesNearAnAuthorizedPlayersOwnTable() throws Exception {
        Fixture f=new Fixture();
        assertTrue(f.hunger());
        when(f.plugin.arena.atTableWorld(f.player,f.room)).thenReturn(false);assertFalse(f.hunger());
        when(f.plugin.arena.atTableWorld(f.player,f.room)).thenReturn(true);
        when(f.player.getLocation()).thenReturn(new Location(f.world,100,80,0));assertFalse(f.hunger());
        when(f.player.getLocation()).thenReturn(new Location(f.world,0,80,2.25));
        when(f.plugin.allowed(f.player)).thenReturn(false);assertFalse(f.hunger());
    }
    @Test void walkingAwayRestoresCollisionAndRepeatedSyncDoesNotResendIt() throws Exception {
        Fixture f=new Fixture();
        try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player));
            f.comfort.sync();f.comfort.sync();verify(f.player,times(1)).setCollidable(false);
            when(f.player.getLocation()).thenReturn(new Location(f.world,100,80,0));f.comfort.sync();
            verify(f.player).setCollidable(true);f.comfort.sync();verify(f.player,times(1)).setCollidable(true);
        }
    }
    @Test void quitAndWorldChangeRestoreCollisionImmediately() throws Exception {
        Fixture f=new Fixture();
        try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player));f.comfort.sync();
            when(f.plugin.arena.atTableWorld(f.player,f.room)).thenReturn(false);
            f.comfort.world(new org.bukkit.event.player.PlayerChangedWorldEvent(f.player,f.world));verify(f.player).setCollidable(true);
            when(f.plugin.arena.atTableWorld(f.player,f.room)).thenReturn(true);f.comfort.sync();
            f.comfort.quit(new org.bukkit.event.player.PlayerQuitEvent(f.player,net.kyori.adventure.text.Component.empty()));verify(f.player,times(2)).setCollidable(true);
        }
    }
    @Test void theRegularRoomTickUpdatesComfortWithoutAnotherMenuAction() throws Exception {
        Fixture f=new Fixture();f.plugin.comfort=f.comfort;
        TabletopTest.set(f.plugin,"loaded",true);TabletopTest.set(f.plugin,"rooms",new LinkedHashMap<UUID,Room>());
        doCallRealMethod().when(f.plugin).tick();
        try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player));f.plugin.tick();verify(f.player).setCollidable(false);
            when(f.player.getLocation()).thenReturn(new Location(f.world,100,80,0));f.plugin.tick();verify(f.player).setCollidable(true);
        }
    }
    @Test void preexistingNonCollidableStateSurvivesLeavingAndClosing() throws Exception {
        Fixture f=new Fixture();when(f.player.isCollidable()).thenReturn(false);
        try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player));
            f.comfort.sync();when(f.plugin.room(f.player)).thenReturn(null);f.comfort.sync();f.comfort.close();
            verify(f.player,never()).setCollidable(true);
        }
    }
    static final class Fixture {
        final Tabletop3D plugin=mock(Tabletop3D.class);final Player player=mock(Player.class);final World world=mock(World.class);
        final Room room=new Room(UUID.randomUUID(),"chess",2,0,0);final TableComfort comfort;
        Fixture(){
            when(plugin.getName()).thenReturn("3dtabletop");when(plugin.isEnabled()).thenReturn(true);when(plugin.getServer()).thenReturn(MockBukkit.getMock());
            when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());
            plugin.arena=mock(GameWorld.class);when(plugin.room(player)).thenReturn(room);when(plugin.allowed(player)).thenReturn(true);
            when(plugin.arena.atTableWorld(player,room)).thenReturn(true);when(plugin.arena.center(0)).thenReturn(new Location(world,0,80,0));
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getLocation()).thenReturn(new Location(world,0,80,2.25));when(player.isCollidable()).thenReturn(true);
            comfort=new TableComfort(plugin);
        }
        boolean hunger(){FoodLevelChangeEvent event=new FoodLevelChangeEvent(player,19);comfort.hunger(event);return event.isCancelled();}
    }
}
