package dev.tabletop3d;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Original Casino Blackjack artwork, copied locally and scaled into the tabletop card envelope. */
final class PlayingCardArt {
    record Box(double[] from, double[] to, String material) {
        Transformation pose(double width, boolean standing, boolean back) {
            double unit = width / 4;
            var translation = standing
                    ? new Vector3f((float) (from[0] * unit),
                            (float) ((from[1] + 8.0 / 3) * unit),
                            (float) ((back ? -to[2] : from[2]) * unit))
                    : new Vector3f((float) (from[0] * unit),
                            (float) (.006 + from[2] * unit), (float) (-to[1] * unit));
            var scale = standing
                    ? new Vector3f((float) ((to[0] - from[0]) * unit),
                            (float) ((to[1] - from[1]) * unit), (float) ((to[2] - from[2]) * unit))
                    : new Vector3f((float) ((to[0] - from[0]) * unit),
                            (float) ((to[2] - from[2]) * unit), (float) ((to[1] - from[1]) * unit));
            return new Transformation(translation, new Quaternionf(), scale, new Quaternionf());
        }
    }

    private static final Map<String, List<Box>> MODELS = load();

    private PlayingCardArt() {}

    static List<Box> boxes(String face) {
        return MODELS.get(face);
    }

    private static Map<String, List<Box>> load() {
        try (var input = PlayingCardArt.class.getResourceAsStream("/playing-card-art.json")) {
            if (input == null) throw new IllegalStateException("Missing native playing-card artwork");
            return Map.copyOf(new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, List<Box>>>() {}.getType()));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load native playing-card artwork", exception);
        }
    }
}
