package dev.tabletop3d.render;

import dev.tabletop3d.render.cards.PlayingCardTable;
import dev.tabletop3d.room.Room;
import dev.tabletop3d.rules.GameFactory;

import org.bukkit.entity.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Count the production displays of fixed starting positions, including all private hands. */
class NativeEntityCountTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void everyNativeTableIsCountedAndRemovesAllEntitiesOnClose() throws Exception {
        StringBuilder csv = new StringBuilder("game,players,fixed_surface_models,public_models,private_models,total_models,interaction_entities\n");
        for (String kind : List.of("connectfour", "xiangqi", "gomoku", "chess", "ludo", "checkers", "draughts", "reversi", "go9", "go13", "go", "color-eight", "mahjong", "doudizhu", "liars-bar", "texas-holdem", "yacht")) {
            List<Integer> capacities = switch (kind) {
                case "mahjong" -> List.of(4);
                case "doudizhu" -> List.of(3);
                case "color-eight" -> List.of(2, 5);
                case "texas-holdem", "checkers" -> List.of(2, 6);
                case "ludo", "liars-bar", "yacht" -> List.of(2, 4);
                default -> List.of(2);
            };
            for (int capacity : capacities)
                for (String profile : kind.equals("mahjong") ? List.of("riichi", "guangdong", "sichuan", "taiwan") : List.of("")) {
                    var f = new TableViewTest.Fixture(kind, capacity);
                    if (!profile.isEmpty()) {
                        f.room.board = GameFactory.create(kind, capacity, 0, Map.of("profile", profile));
                        f.room.revision++;
                        f.view.sync();
                    }
                    long publicDisplays = displays(f.entities);
                    Set<Entity> fixed = new HashSet<>();
                    for (String field : List.of("nativeBoardFurniture", "handFurniture"))
                        fixed.addAll((List<Entity>) TableViewTest.field(f.view, field));
                    for (String field : List.of("playingTable", "yachtTable", "diceTray")) {
                        Object child = TableViewTest.field(f.view, field);
                        if (child != null) fixed.addAll((List<Entity>) TableViewTest.field(child, field.equals("playingTable") ? "nativeFurniture" : "nativeTable"));
                        if (child != null && field.equals("diceTray"))
                            fixed.addAll(((List<Entity>) TableViewTest.field(child, "entities")).stream()
                                    .filter(BlockDisplay.class::isInstance).toList());
                    }
                    List<Player> players = new ArrayList<>();
                    for (int seat = 0; seat < capacity; seat++) {
                        Player player = seat == 0 ? f.player : mock(Player.class);
                        UUID id = seat == 0 ? f.player.getUniqueId() : UUID.randomUUID();
                        when(player.getUniqueId()).thenReturn(id);
                        when(player.isOnline()).thenReturn(true);
                        when(player.getWorld()).thenReturn(f.world);
                        when(player.getLocation()).thenReturn(f.view.origin.clone().add(0, 0, 2));
                        when(f.plugin.allowed(player)).thenReturn(true);
                        f.room.seats.set(seat, new Room.Seat(id, "Player " + seat, false));
                        players.add(player);
                    }
                    when(f.world.getPlayers()).thenReturn(players);
                    f.view.tick();
                    Object handTable = TableViewTest.field(f.view, "handTable");
                    Object playingTable = TableViewTest.field(f.view, "playingTable");
                    for (Player player : players) {
                        if (handTable != null) ((HandTable) handTable).show(player);
                        if (playingTable != null) {
                            var show = PlayingCardTable.class.getDeclaredMethod("show", Player.class);
                            show.setAccessible(true);
                            show.invoke(playingTable, player);
                        }
                    }
                    long total = displays(f.entities);
                    long interactions = f.entities.stream().filter(NativeEntityCountTest::alive).filter(Interaction.class::isInstance).count();
                    assertTrue(total >= publicDisplays);
                    assertFalse(fixed.isEmpty(), kind);
                    csv.append(kind).append(profile.isEmpty() ? "" : ":" + profile).append(',').append(capacity).append(',')
                            .append(displays(fixed)).append(',').append(publicDisplays).append(',').append(total - publicDisplays).append(',')
                            .append(total).append(',').append(interactions).append('\n');
                    f.view.close();
                    assertEquals(0, f.entities.stream().filter(NativeEntityCountTest::alive).count(), kind + " cleanup");
                }
        }
        Files.writeString(Path.of("target/native-all-table-counts.csv"), csv);
    }

    private static boolean alive(Entity entity) {
        return mockingDetails(entity).getInvocations().stream().noneMatch(call -> call.getMethod().getName().equals("remove"));
    }

    private static long displays(Collection<Entity> entities) {
        return entities.stream().filter(NativeEntityCountTest::alive).filter(entity -> entity instanceof Display || entity instanceof ItemFrame).count();
    }
}
