package dev.tabletop3d.render.cards;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class PlayingCardSourceArtTest {
    private record Box(double[] from, double[] to, String material) {}
    @Test
    void casinoSourceArtworkRetainsEveryOriginalPixelBoundary() throws Exception {
        try (var input = getClass().getResourceAsStream("/blackjack-card-fingerprints.json")) {
            var expected = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            var source = java.nio.file.Files.readString(java.nio.file.Path.of("tools/assets/playing-card-art.json"));
            java.util.Map<String, List<Box>> artwork = new com.google.gson.Gson().fromJson(source,
                    new com.google.gson.reflect.TypeToken<java.util.Map<String, List<Box>>>() {}.getType());
            for (var entry : expected.entrySet()) {
                var boxes = artwork.get(entry.getKey());
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

}
