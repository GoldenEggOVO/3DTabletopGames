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
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

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
    void casinoSourceArtworkRetainsEveryOriginalPixelBoundary() throws Exception {
        try (var input = getClass().getResourceAsStream("/blackjack-card-fingerprints.json")) {
            var expected = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (var entry : expected.entrySet()) {
                var boxes = PlayingCardArt.boxes(entry.getKey());
                assertEquals(entry.getValue().getAsJsonObject().get("count").getAsInt(), boxes.size());
                var rectangles = new TreeMap<String, List<long[]>>();
                for (var box : boxes) {
                    long[] bounds = new long[6];
                    for (int i = 0; i < 3; i++) {
                        bounds[i] = Math.round(box.from()[i] * 1_000_000);
                        bounds[i + 3] = Math.round(box.to()[i] * 1_000_000);
                    }
                    String layer = box.material() + ":" + bounds[2] + ":" + bounds[5];
                    rectangles.computeIfAbsent(layer, key -> new ArrayList<>()).add(bounds);
                }
                var fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(coverage(rectangles).getBytes(StandardCharsets.UTF_8)));
                assertEquals(entry.getValue().getAsJsonObject().get("sha256").getAsString(), fingerprint, entry.getKey());
            }
        }
    }

    @Test
    void nativeRasterPreservesBothCornersCentralArtAndBackPattern() throws Exception {
        try (var input = getClass().getResourceAsStream("/playing-card-raster-fingerprints.json")) {
            var expected = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (var entry : expected.entrySet()) {
                var digest = MessageDigest.getInstance("SHA-256");
                int columns = 0, rows = 1;
                for (var child : PlayingCardRaster.face(entry.getKey()).children()) {
                    var run = assertInstanceOf(net.kyori.adventure.text.TextComponent.class, child);
                    if (run.content().equals("\n")) {
                        assertEquals(64, columns, "Every explicit line must retain its full text width");
                        columns = 0;
                        rows++;
                        continue;
                    }
                    int color = run.content().charAt(0) == ' '
                            ? (entry.getKey().equals("back") ? 0x35399d : 0xf9ffff)
                            : java.util.Objects.requireNonNull(run.color()).value();
                    for (char character : run.content().toCharArray()) {
                        assertTrue(character == ' ' || character == '\u2588');
                        digest.update((byte) (color >> 16));
                        digest.update((byte) (color >> 8));
                        digest.update((byte) color);
                        columns++;
                    }
                }
                assertEquals(64, columns);
                assertEquals(86, rows);
                assertEquals(entry.getValue().getAsString(), HexFormat.of().formatHex(digest.digest()), entry.getKey());
            }
        }
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
    void flatArtworkKeepsTheCardEnvelopeAndSitsAboveItsBody() throws Exception {
        var fixture = new TableViewTest.Fixture("texas-holdem", 2);
        Object card = card(fixture, "hearts_ace", false);
        var parts = (List<Display>) TableViewTest.field(card, "parts");
        assertEquals(5, parts.size());
        var body = fixture.transforms.get(parts.getFirst());
        var paperTop = body.getTranslation().y + body.getScale().y;
        assertEquals(.168, body.getScale().x, 1e-7);
        assertEquals(.224, body.getScale().z, 1e-7);
        for (int i = 1; i < parts.size(); i++) {
            assertInstanceOf(org.bukkit.entity.TextDisplay.class, parts.get(i));
            var print = fixture.transforms.get(parts.get(i));
            assertTrue(print.getTranslation().y > paperTop + .001, "Glyph planes must clear the body");
            var normal = print.getLeftRotation().transform(new org.joml.Vector3f(0, 0, 1));
            assertEquals(1, normal.y, 1e-6, "Played card ink must face upward");
        }
        remove(card);
        fixture.view.close();
    }

    @Test
    void backgroundCellsUseInvisibleEqualAdvanceSpacesInsteadOfGlyphQuads() {
        for (String face : List.of("hearts_ace", "spades_king", "back")) {
            var image = PlayingCardRaster.face(face);
            int drawn = 0, skipped = 0;
            for (var child : image.children()) {
                var run = assertInstanceOf(net.kyori.adventure.text.TextComponent.class, child);
                if (run.content().isBlank() && !run.content().equals("\n")) {
                    assertEquals(net.kyori.adventure.text.format.TextDecoration.State.TRUE,
                            run.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD),
                            "A vanilla space advances 4 plus the bold offset 1, matching a uniform block's 5");
                    skipped += run.content().length();
                } else if (run.content().startsWith("\u2588")) drawn += run.content().length();
            }
            assertTrue(skipped > drawn, "The body must cover most pixels without font quads: " + face);
            assertEquals(64 * 86, skipped + drawn);
        }
    }

    @Test
    void fourGlyphPlanesFillEveryCellWithoutCrossingIntoAdjacentColors() {
        double ux = .168 / 320, uy = .224 / 860;
        // Independently reproduce the verified 26.2 TextDisplayRenderer font-space matrix.
        var client = new org.joml.Matrix4f().rotateY((float) Math.PI).scale(-.025f)
                .translate(1 - 320 / 2f, -859, 0);
        for (boolean standing : List.of(false, true)) {
            for (boolean back : List.of(false, true)) {
                var covered = new boolean[10][5];
                double minAcross = Double.POSITIVE_INFINITY, maxAcross = Double.NEGATIVE_INFINITY;
                double minAlong = Double.POSITIVE_INFINITY, maxAlong = Double.NEGATIVE_INFINITY;
                for (int layer = 0; layer < 4; layer++) {
                    var transform = PlayingCardRaster.pose(.168, standing, back, layer);
                    var actual = new org.joml.Matrix4f().translate(transform.getTranslation())
                            .rotate(transform.getLeftRotation()).scale(transform.getScale())
                            .rotate(transform.getRightRotation()).mul(client);
                    var first = actual.transformPosition(new org.joml.Vector3f(0, 0, 0));
                    var firstEnd = actual.transformPosition(new org.joml.Vector3f(4, 8, 0));
                    double x1 = standing && back ? (.084 - first.x) / ux : (first.x + .084) / ux;
                    double x2 = standing && back ? (.084 - firstEnd.x) / ux : (firstEnd.x + .084) / ux;
                    double y1 = standing ? (.224 - first.y) / uy : (first.z + .112) / uy;
                    double y2 = standing ? (.224 - firstEnd.y) / uy : (firstEnd.z + .112) / uy;
                    assertTrue(Math.min(x1, x2) >= -1e-3 && Math.max(x1, x2) <= 5.001,
                            "A glyph must not cross its pixel's horizontal color boundary");
                    assertTrue(Math.min(y1, y2) >= -1e-3 && Math.max(y1, y2) <= 10.001,
                            "A glyph must not cross its pixel's vertical color boundary");
                    for (int y = 0; y < 10; y++) for (int x = 0; x < 5; x++)
                        if (x + .5 >= Math.min(x1, x2) && x + .5 < Math.max(x1, x2)
                                && y + .5 >= Math.min(y1, y2) && y + .5 < Math.max(y1, y2)) covered[y][x] = true;
                    for (var point : List.of(new org.joml.Vector3f(0, 0, 0),
                            new org.joml.Vector3f(319, 858, 0))) {
                        actual.transformPosition(point);
                        minAcross = Math.min(minAcross, point.x); maxAcross = Math.max(maxAcross, point.x);
                        double along = standing ? point.y : point.z;
                        minAlong = Math.min(minAlong, along); maxAlong = Math.max(maxAlong, along);
                    }
                    var normal = transform.getLeftRotation().transform(new org.joml.Vector3f(0, 0, 1));
                    assertEquals(standing ? (back ? -1 : 1) : 0, normal.z, 1e-6);
                    assertEquals(standing ? 0 : 1, normal.y, 1e-6);
                }
                for (var row : covered) for (boolean cell : row) assertTrue(cell, "No font spacing gap may remain");
                assertEquals(-.084, minAcross, 1e-6); assertEquals(.084, maxAcross, 1e-6);
                assertEquals(standing ? 0 : -.112, minAlong, 1e-6);
                assertEquals(standing ? .224 : .112, maxAlong, 1e-6);
            }
        }
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
