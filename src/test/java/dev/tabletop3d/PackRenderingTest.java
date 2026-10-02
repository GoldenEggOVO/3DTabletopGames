package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.tabletop3d.rules.ColorEightGame;
import dev.tabletop3d.rules.HandGame;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.function.Consumer;

class PackRenderingTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void packedHandsPublicCardsAndFurnitureUseReadableModelTransform() {
        Fixture f = new Fixture();
        f.table.show(f.owner);
        var items =
                f.entities.stream()
                        .filter(ItemDisplay.class::isInstance)
                        .map(ItemDisplay.class::cast)
                        .toList();
        assertFalse(items.isEmpty());
        for (ItemDisplay item : items)
            verify(item).setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        f.table.close();
    }

    @Test
    void fixedFullTablesCountEachBackendAndDeleteAllDisplaysOnClose() throws Exception {
        StringBuilder csv =
                new StringBuilder(
                        "players,mode,public_displays,private_displays,server_live,player_0_visible,player_1_visible\n");
        for (int size : new int[] {2, 5})
            for (String mode : List.of("vanilla", "mixed", "resource-pack")) {
                Fixture f = new Fixture();
                f.table.close();
                List<Player> players = new ArrayList<>(List.of(f.owner, f.peer));
                while (players.size() < size) players.add(mock(Player.class));
                Map<Player, Set<Entity>> shown = new HashMap<>(), hidden = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    Player p = players.get(i);
                    shown.put(p, new HashSet<>());
                    hidden.put(p, new HashSet<>());
                    when(p.getUniqueId()).thenReturn(UUID.randomUUID());
                    when(p.getWorld()).thenReturn(f.world);
                    when(p.isOnline()).thenReturn(true);
                    when(p.getLocation()).thenReturn(new Location(f.world, 0, 81, 2));
                    when(f.plugin.allowed(p)).thenReturn(true);
                    when(f.plugin.pack.packed(p))
                            .thenReturn(
                                    mode.equals("resource-pack") || mode.equals("mixed") && i == 0);
                    doAnswer(
                                    a -> {
                                        Entity e = a.getArgument(1);
                                        shown.get(p).add(e);
                                        hidden.get(p).remove(e);
                                        return null;
                                    })
                            .when(p)
                            .showEntity(eq(f.plugin), any(Entity.class));
                    doAnswer(
                                    a -> {
                                        Entity e = a.getArgument(1);
                                        hidden.get(p).add(e);
                                        shown.get(p).remove(e);
                                        return null;
                                    })
                            .when(p)
                            .hideEntity(eq(f.plugin), any(Entity.class));
                }
                if (mode.equals("vanilla")) f.plugin.pack = null;
                when(f.world.getPlayers()).thenReturn(players);
                Room room = new Room(UUID.randomUUID(), "color-eight", size, 0, 0);
                for (int i = 0; i < size; i++)
                    room.join(players.get(i).getUniqueId(), "Player " + i);
                room.board = new ColorEightGame(size, 20260929);
                room.phase = Room.Phase.PLAYING;
                TableView table =
                        new TableView(
                                f.plugin,
                                room,
                                new Location(f.world, 0, 80, 0),
                                new NamespacedKey("test", "table"),
                                mock(TableMaps.class));
                var field = TableView.class.getDeclaredField("handTable");
                field.setAccessible(true);
                HandTable hands = (HandTable) field.get(table);
                long publicCount = f.entities.stream().filter(Entity::isValid).count();
                for (Player p : players) hands.show(p);
                long total = f.entities.stream().filter(Entity::isValid).count();
                long[] visible = new long[size];
                for (int i = 0; i < size; i++) {
                    Player p = players.get(i);
                    visible[i] =
                            f.entities.stream()
                                    .filter(Entity::isValid)
                                    .filter(
                                            e ->
                                                    shown.get(p).contains(e)
                                                            || f.defaultVisible.getOrDefault(
                                                                            e, true)
                                                                    && !hidden.get(p).contains(e))
                                    .count();
                }
                if (mode.equals("resource-pack"))
                    assertTrue(total < 100, "All-packed full tables must remain bounded");
                assertTrue(total > publicCount);
                assertTrue(visible[0] < total, "Other owners' private cards must be hidden");
                csv.append(size)
                        .append(',')
                        .append(mode)
                        .append(',')
                        .append(publicCount)
                        .append(',')
                        .append(total - publicCount)
                        .append(',')
                        .append(total)
                        .append(',')
                        .append(visible[0])
                        .append(',')
                        .append(visible[1])
                        .append('\n');
                table.close();
                assertEquals(0, f.entities.stream().filter(Entity::isValid).count());
            }
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/full-table-counts.csv"), csv);
    }

    @Test
    void spectatorKeepsNativeLayerUntilLastNativeViewerLeaves() {
        Fixture f = new Fixture();
        List<Entity> nativeParts =
                f.entities.stream()
                        .filter(BlockDisplay.class::isInstance)
                        .filter(e -> !e.getScoreboardTags().contains("tabletop-turn-indicator"))
                        .toList();
        assertFalse(nativeParts.isEmpty());
        assertTrue(f.entities.stream().anyMatch(ItemDisplay.class::isInstance));
        for (Entity part : nativeParts) {
            verify(f.peer).showEntity(f.plugin, part);
            verify(f.owner, never()).showEntity(f.plugin, part);
        }
        when(f.world.getPlayers()).thenReturn(List.of(f.owner));
        f.table.tick();
        for (Entity part : nativeParts) verify(part).remove();
        when(f.world.getPlayers()).thenReturn(List.of(f.owner, f.peer));
        f.table.tick();
        assertTrue(
                f.entities.stream()
                        .filter(BlockDisplay.class::isInstance)
                        .filter(e -> !e.getScoreboardTags().contains("tabletop-turn-indicator"))
                        .anyMatch(Entity::isValid));
    }

    @Test
    void privatePackedFacesUseSingleItemsAndSwitchWithoutRoomRevision() {
        Fixture f = new Fixture();
        int initial = f.entities.size();
        f.table.show(f.owner);
        var privateParts = List.copyOf(f.entities.subList(initial, f.entities.size()));
        assertEquals(2, privateParts.size());
        assertTrue(privateParts.stream().allMatch(ItemDisplay.class::isInstance));
        for (Entity part : privateParts) {
            verify(part).setVisibleByDefault(false);
            verify(f.owner).showEntity(f.plugin, part);
            verify(f.peer, never()).showEntity(f.plugin, part);
        }
        when(f.plugin.pack.packed(f.owner)).thenReturn(false);
        f.table.tick();
        f.table.show(f.owner);
        for (Entity part : privateParts) verify(part).remove();
        assertTrue(
                f.entities.subList(initial + 2, f.entities.size()).stream()
                        .anyMatch(BlockDisplay.class::isInstance));
    }

    @Test
    void everyManagedPublicEntityStartsHiddenAndFarObserversAreExcluded() {
        Fixture f = new Fixture();
        for (Entity part : f.entities) verify(part, atLeastOnce()).setVisibleByDefault(false);
        when(f.peer.getLocation()).thenReturn(new Location(f.world, 60, 81, 0));
        f.table.tick();
        assertTrue(
                f.entities.stream()
                        .filter(BlockDisplay.class::isInstance)
                        .filter(e -> !e.getScoreboardTags().contains("tabletop-turn-indicator"))
                        .noneMatch(Entity::isValid));
    }

    @Test
    void tableFurnitureIsDeletedWithLastNativeSpectator() {
        Fixture f = new Fixture();
        f.table.close();
        int start = f.entities.size();
        TableView table =
                new TableView(
                        f.plugin,
                        f.room,
                        new Location(f.world, 0, 80, 0),
                        new NamespacedKey("tabletop3d", "board-cell"),
                        mock(TableMaps.class));
        var natives =
                f.entities.subList(start, f.entities.size()).stream()
                        .filter(BlockDisplay.class::isInstance)
                        .filter(e -> !e.getScoreboardTags().contains("tabletop-turn-indicator"))
                        .toList();
        assertFalse(natives.isEmpty());
        when(f.world.getPlayers()).thenReturn(List.of(f.owner));
        table.tick();
        for (Entity entity : natives) verify(entity).remove();
        assertTrue(
                f.entities.stream()
                                .filter(Entity::isValid)
                                .filter(ItemDisplay.class::isInstance)
                                .count()
                        <= 8);
        table.close();
    }

    static class Fixture {
        final Tabletop3D plugin = mock(Tabletop3D.class);
        final World world = mock(World.class);
        final Player owner = mock(Player.class), peer = mock(Player.class);
        final List<Entity> entities = new ArrayList<>();
        final Map<Entity, Location> positions = new HashMap<>();
        final Map<Entity, Boolean> defaultVisible = new HashMap<>();
        final HandTable table;
        final Room room;

        Fixture() {
            plugin.pack = mock(TabletopPack.class);
            when(plugin.pack.item(anyString())).thenReturn(new ItemStack(Material.PAPER));
            when(plugin.pack.packed(owner)).thenReturn(true);
            when(plugin.pack.canPlay(any(), anyString())).thenReturn(true);
            for (Player p : List.of(owner, peer)) {
                when(p.getUniqueId()).thenReturn(UUID.randomUUID());
                when(p.getWorld()).thenReturn(world);
                when(p.isOnline()).thenReturn(true);
                when(p.getLocation()).thenReturn(new Location(world, 0, 81, 2));
                when(plugin.allowed(p)).thenReturn(true);
            }
            when(world.getPlayers()).thenReturn(List.of(owner, peer));
            when(world.spawn(any(Location.class), any(Class.class), any(Consumer.class)))
                    .thenAnswer(
                            inv -> {
                                Entity entity = mock((Class<? extends Entity>) inv.getArgument(1));
                                entities.add(entity);
                                Set<String> tags = new HashSet<>();
                                when(entity.getScoreboardTags()).thenReturn(tags);
                                doAnswer(a -> tags.add(a.getArgument(0)))
                                        .when(entity)
                                        .addScoreboardTag(anyString());
                                positions.put(entity, ((Location) inv.getArgument(0)).clone());
                                when(entity.getPersistentDataContainer())
                                        .thenReturn(mock(PersistentDataContainer.class));
                                when(entity.isValid()).thenReturn(true);
                                when(entity.getLocation())
                                        .thenAnswer(a -> positions.get(entity).clone());
                                doAnswer(
                                                a -> {
                                                    defaultVisible.put(entity, a.getArgument(0));
                                                    return null;
                                                })
                                        .when(entity)
                                        .setVisibleByDefault(anyBoolean());
                                doAnswer(
                                                a -> {
                                                    positions.put(
                                                            entity,
                                                            ((Location) a.getArgument(0)).clone());
                                                    return true;
                                                })
                                        .when(entity)
                                        .teleport(any(Location.class));
                                doAnswer(
                                                a -> {
                                                    when(entity.isValid()).thenReturn(false);
                                                    return null;
                                                })
                                        .when(entity)
                                        .remove();
                                ((Consumer<Entity>) inv.getArgument(2)).accept(entity);
                                return entity;
                            });
            room = new Room(UUID.randomUUID(), "color-eight", 2, 0, 0);
            room.join(owner.getUniqueId(), "Owner");
            room.fillBots();
            room.phase = Room.Phase.PLAYING;
            HandGame game = mock(HandGame.class);
            room.board = game;
            when(game.playerCount()).thenReturn(2);
            when(game.handSize(anyInt())).thenReturn(2);
            when(game.hand(0))
                    .thenReturn(
                            List.of(new HandGame.Piece("a", "r1"), new HandGame.Piece("b", "b2")));
            table =
                    new HandTable(
                            plugin,
                            room,
                            new Location(world, 0, 81, 0),
                            new NamespacedKey("tabletop3d", "board-cell"));
        }
    }
}
