package dev.tabletop3d;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import dev.tabletop3d.rules.HandGame;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PackRenderingTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void spectatorKeepsNativeLayerUntilLastNativeViewerLeaves(){
        Fixture f=new Fixture();List<Entity> nativeParts=f.entities.stream().filter(BlockDisplay.class::isInstance).toList();
        assertFalse(nativeParts.isEmpty());assertTrue(f.entities.stream().anyMatch(ItemDisplay.class::isInstance));
        for(Entity part:nativeParts){verify(f.peer).showEntity(f.plugin,part);verify(f.owner,never()).showEntity(f.plugin,part);}
        when(f.world.getPlayers()).thenReturn(List.of(f.owner));f.table.tick();
        for(Entity part:nativeParts)verify(part).remove();
        when(f.world.getPlayers()).thenReturn(List.of(f.owner,f.peer));f.table.tick();
        assertTrue(f.entities.stream().filter(BlockDisplay.class::isInstance).anyMatch(Entity::isValid));
    }
    @Test void privatePackedFacesUseSingleItemsAndSwitchWithoutRoomRevision(){
        Fixture f=new Fixture();int initial=f.entities.size();f.table.show(f.owner);
        var privateParts=List.copyOf(f.entities.subList(initial,f.entities.size()));
        assertEquals(2,privateParts.size());assertTrue(privateParts.stream().allMatch(ItemDisplay.class::isInstance));
        for(Entity part:privateParts){verify(part).setVisibleByDefault(false);verify(f.owner).showEntity(f.plugin,part);verify(f.peer,never()).showEntity(f.plugin,part);}
        when(f.plugin.pack.packed(f.owner)).thenReturn(false);f.table.tick();f.table.show(f.owner);
        for(Entity part:privateParts)verify(part).remove();
        assertTrue(f.entities.subList(initial+2,f.entities.size()).stream().anyMatch(BlockDisplay.class::isInstance));
    }
    @Test void everyManagedPublicEntityStartsHiddenAndFarObserversAreExcluded(){
        Fixture f=new Fixture();
        for(Entity part:f.entities)verify(part,atLeastOnce()).setVisibleByDefault(false);
        when(f.peer.getLocation()).thenReturn(new Location(f.world,60,81,0));f.table.tick();
        assertTrue(f.entities.stream().filter(BlockDisplay.class::isInstance).noneMatch(Entity::isValid));
    }
    @Test void tableFurnitureIsDeletedWithLastNativeSpectator(){
        Fixture f=new Fixture();f.table.close();int start=f.entities.size();
        TableView table=new TableView(f.plugin,f.room,new Location(f.world,0,80,0),new NamespacedKey("tabletop3d","board-cell"),mock(TableMaps.class));
        var natives=f.entities.subList(start,f.entities.size()).stream().filter(BlockDisplay.class::isInstance).toList();
        assertFalse(natives.isEmpty());when(f.world.getPlayers()).thenReturn(List.of(f.owner));table.tick();
        for(Entity entity:natives)verify(entity).remove();
        assertTrue(f.entities.stream().filter(Entity::isValid).filter(ItemDisplay.class::isInstance).count()<=8);
        table.close();
    }
    static class Fixture {
        final Tabletop3D plugin=mock(Tabletop3D.class);
        final World world=mock(World.class);
        final Player owner=mock(Player.class),peer=mock(Player.class);
        final List<Entity> entities=new ArrayList<>();
        final Map<Entity,Location> positions=new HashMap<>();
        final HandTable table;
        final Room room;
        Fixture(){
            plugin.pack=mock(TabletopPack.class);when(plugin.pack.item(anyString())).thenReturn(new ItemStack(Material.PAPER));
            when(plugin.pack.packed(owner)).thenReturn(true);when(plugin.pack.canPlay(any(),anyString())).thenReturn(true);
            for(Player p:List.of(owner,peer)){
                when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getWorld()).thenReturn(world);when(p.isOnline()).thenReturn(true);
                when(p.getLocation()).thenReturn(new Location(world,0,81,2));when(plugin.allowed(p)).thenReturn(true);
            }
            when(world.getPlayers()).thenReturn(List.of(owner,peer));
            when(world.spawn(any(Location.class),any(Class.class),any(Consumer.class))).thenAnswer(inv->{
                Entity entity=mock((Class<? extends Entity>)inv.getArgument(1));entities.add(entity);positions.put(entity,((Location)inv.getArgument(0)).clone());
                when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));when(entity.isValid()).thenReturn(true);
                when(entity.getLocation()).thenAnswer(a->positions.get(entity).clone());
                doAnswer(a->{positions.put(entity,((Location)a.getArgument(0)).clone());return true;}).when(entity).teleport(any(Location.class));
                doAnswer(a->{when(entity.isValid()).thenReturn(false);return null;}).when(entity).remove();
                ((Consumer<Entity>)inv.getArgument(2)).accept(entity);return entity;
            });
            room=new Room(UUID.randomUUID(),"lastcard",2,0,0);room.join(owner.getUniqueId(),"Owner");room.fillBots();room.phase=Room.Phase.PLAYING;
            HandGame game=mock(HandGame.class);room.board=game;when(game.playerCount()).thenReturn(2);when(game.handSize(anyInt())).thenReturn(2);
            when(game.hand(0)).thenReturn(List.of(new HandGame.Piece("a","r1"),new HandGame.Piece("b","b2")));
            table=new HandTable(plugin,room,new Location(world,0,81,0),new NamespacedKey("tabletop3d","board-cell"));
        }
    }
}
