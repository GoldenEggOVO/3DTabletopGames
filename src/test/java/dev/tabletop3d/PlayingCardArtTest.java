package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

class PlayingCardArtTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void close() {
        MockBukkit.unmock();
    }

    @Test
    void allNativeCardsReproduceTheBlackjackBlockArtworkWithoutTextOverlays() throws Exception {
        var fixture = new TableViewTest.Fixture("texas-holdem", 2);
        try (var input = getClass().getResourceAsStream("/blackjack-card-fingerprints.json")) {
            var expected = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (var entry : expected.entrySet()) {
                Object card = card(fixture, entry.getKey(), true);
                var parts = (List<Display>) TableViewTest.field(card, "parts");
                assertEquals(entry.getValue().getAsJsonObject().get("count").getAsInt(), parts.size(), entry.getKey());
                var rectangles = new TreeMap<String, List<long[]>>();
                for (var part : parts) {
                    assertInstanceOf(BlockDisplay.class, part, entry.getKey());
                    var material = ArgumentCaptor.forClass(BlockData.class);
                    verify((BlockDisplay) part).setBlock(material.capture());
                    var transform = fixture.transforms.get(part);
                    var from = transform.getTranslation();
                    var size = transform.getScale();
                    double scale = .168 / 4;
                    boolean back = entry.getKey().equals("back");
                    double[] coordinates = {from.x / scale, (from.y - .112) / scale,
                            (back ? -from.z - size.z : from.z) / scale, (from.x + size.x) / scale,
                            (from.y + size.y - .112) / scale, (back ? -from.z : from.z + size.z) / scale};
                    long[] bounds = new long[6];
                    for (int i = 0; i < coordinates.length; i++) {
                        var edges = entry.getValue().getAsJsonObject().getAsJsonArray("coordinates").get(i % 3).getAsJsonArray();
                        long closest = edges.get(0).getAsLong();
                        for (var edge : edges)
                            if (Math.abs(edge.getAsLong() / 1_000_000.0 - coordinates[i])
                                    < Math.abs(closest / 1_000_000.0 - coordinates[i])) closest = edge.getAsLong();
                        assertEquals(closest / 1_000_000.0, coordinates[i], 1e-6,
                                entry.getKey() + " must preserve the original pixel boundary");
                        bounds[i] = closest;
                    }
                    String layer = material.getValue().getMaterial() + ":" + bounds[2] + ":" + bounds[5];
                    rectangles.computeIfAbsent(layer, key -> new ArrayList<>()).add(bounds);
                }
                var fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(coverage(rectangles).getBytes(StandardCharsets.UTF_8)));
                assertEquals(entry.getValue().getAsJsonObject().get("sha256").getAsString(), fingerprint, entry.getKey());
                if (entry.getKey().equals("back")) {
                    var paper = fixture.transforms.get(parts.getFirst());
                    for (var ink : parts.subList(1, parts.size())) {
                        var print = fixture.transforms.get(ink);
                        assertTrue(print.getTranslation().z + print.getScale().z < paper.getTranslation().z,
                                "Public upright backs must face opponents outside the private face");
                    }
                }
                remove(card);
            }
        }
        fixture.view.close();
    }

    private String coverage(TreeMap<String, List<long[]>> layers) {
        var canonical = new StringBuilder();
        for (var entry : layers.entrySet()) {
            var layer = entry.getKey().split(":");
            var ys = new TreeSet<Long>();
            for (var rectangle : entry.getValue()) { ys.add(rectangle[1]); ys.add(rectangle[4]); }
            var edges = new ArrayList<>(ys);
            List<List<Long>> prior = null;
            long bandStart = 0, bandEnd = 0;
            for (int i = 0; i < edges.size() - 1; i++) {
                long start = edges.get(i), end = edges.get(i + 1);
                var intervals = new ArrayList<long[]>();
                for (var rectangle : entry.getValue())
                    if (rectangle[1] <= start && rectangle[4] >= end)
                        intervals.add(new long[] {rectangle[0], rectangle[3]});
                intervals.sort(Comparator.comparingLong((long[] interval) -> interval[0]).thenComparingLong(interval -> interval[1]));
                var merged = new ArrayList<List<Long>>();
                for (var interval : intervals) {
                    if (!merged.isEmpty() && interval[0] <= merged.getLast().get(1)) {
                        var previous = merged.getLast();
                        merged.set(merged.size() - 1, List.of(previous.get(0), Math.max(previous.get(1), interval[1])));
                    } else merged.add(List.of(interval[0], interval[1]));
                }
                if (merged.equals(prior)) bandEnd = end;
                else {
                    if (prior != null) appendBand(canonical, layer, bandStart, bandEnd, prior);
                    prior = merged;
                    bandStart = start;
                    bandEnd = end;
                }
            }
            if (prior != null) appendBand(canonical, layer, bandStart, bandEnd, prior);
        }
        return canonical.toString();
    }

    private void appendBand(StringBuilder output, String[] layer, long y1, long y2, List<List<Long>> intervals) {
        for (var interval : intervals) {
            if (!output.isEmpty()) output.append(';');
            output.append(layer[0]).append(':').append(interval.get(0)).append(',').append(y1)
                    .append(',').append(layer[1]).append(',').append(interval.get(1)).append(',')
                    .append(y2).append(',').append(layer[2]);
        }
    }

    @Test
    void flatArtworkIsRotatedAboveTheTableWithoutChangingTheCardFace() throws Exception {
        var fixture = new TableViewTest.Fixture("texas-holdem", 2);
        Object card = card(fixture, "hearts_ace", false);
        var parts = (List<Display>) TableViewTest.field(card, "parts");
        assertEquals(58, parts.size());
        var body = fixture.transforms.get(parts.getFirst());
        var paperTop = body.getTranslation().y + body.getScale().y;
        assertEquals(.168, body.getScale().x, 1e-7);
        assertEquals(.224, body.getScale().z, 1e-7);
        for (int i = 1; i < parts.size(); i++) {
            var print = fixture.transforms.get(parts.get(i));
            assertTrue(print.getTranslation().y > paperTop, "Ink must sit above the card body");
            var material = ArgumentCaptor.forClass(BlockData.class);
            verify((BlockDisplay) parts.get(i)).setBlock(material.capture());
            assertEquals(Material.RED_CONCRETE, material.getValue().getMaterial());
        }
        remove(card);
        fixture.view.close();
    }

    private void remove(Object card) throws Exception {
        var method = card.getClass().getDeclaredMethod("remove");
        method.setAccessible(true);
        method.invoke(card);
    }

    private Object card(TableViewTest.Fixture fixture, String face, boolean standing) throws Exception {
        Object table = TableViewTest.field(fixture.view, "playingTable");
        var poseClass = Class.forName("dev.tabletop3d.PlayingCardTable$Pose");
        var poseConstructor = poseClass.getDeclaredConstructor(double.class, double.class, float.class, double.class);
        poseConstructor.setAccessible(true);
        Object pose = poseConstructor.newInstance(0, 0, 0, .017);
        var specClass = Class.forName("dev.tabletop3d.PlayingCardTable$CardSpec");
        var specConstructor = specClass.getDeclaredConstructor(String.class, poseClass, boolean.class, int.class);
        specConstructor.setAccessible(true);
        Object spec = specConstructor.newInstance(face, pose, standing, 0);
        var visualClass = Class.forName("dev.tabletop3d.PlayingCardTable$CardVisual");
        var visualConstructor = visualClass.getDeclaredConstructor(PlayingCardTable.class, specClass, Player.class, boolean.class);
        visualConstructor.setAccessible(true);
        return visualConstructor.newInstance(table, spec, fixture.player, false);
    }
}
