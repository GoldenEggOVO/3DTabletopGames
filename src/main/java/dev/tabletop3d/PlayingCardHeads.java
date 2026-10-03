package dev.tabletop3d;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Six thin heads carry a 16 by 24 pixel card without resource packs or font geometry. */
final class PlayingCardHeads {
    record Texture(String value, String signature) {}

    private static final class Catalog {
        static final Map<String, List<Texture>> FACES = load();
    }
    private static final Map<Texture, ItemStack> ITEMS = new HashMap<>();

    private PlayingCardHeads() {}

    static List<Texture> tiles(String face) {
        return Catalog.FACES.get(face);
    }

    static ItemStack item(Texture texture) {
        return ITEMS.computeIfAbsent(texture, key -> {
            var item = new ItemStack(Material.PLAYER_HEAD);
            var meta = (SkullMeta) item.getItemMeta();
            var profile = Bukkit.createProfileExact(
                    UUID.nameUUIDFromBytes(key.value.getBytes(StandardCharsets.UTF_8)), "TabletopCard");
            profile.setProperty(new ProfileProperty("textures", key.value, key.signature));
            meta.setPlayerProfile(profile);
            item.setItemMeta(meta);
            return item;
        }).clone();
    }

    static Transformation pose(double width, boolean standing, boolean back, int tile) {
        double height = width * 4 / 3;
        double cellWidth = width / 2, cellHeight = height / 3;
        var rotation = standing
                ? (back ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf())
                : new Quaternionf().rotateX((float) -Math.PI / 2);
        var translation = new Vector3f((float) ((tile % 2 - .5) * cellWidth),
                (float) ((standing ? height : height / 2) - (tile / 2) * cellHeight), 0);
        rotation.transform(translation);
        if (!standing) translation.y += .006f;
        // NONE has a half-block skull, top at y=0 and bottom at y=-.5.
        // Cancel ItemDisplay's Y180 so the north skin face points toward the owner.
        return new Transformation(translation, rotation,
                new Vector3f((float) (cellWidth * 2), (float) (cellHeight * 2), .016f),
                new Quaternionf().rotateY((float) Math.PI));
    }

    private static Map<String, List<Texture>> load() {
        try (var input = PlayingCardHeads.class.getResourceAsStream("/playing-card-heads.json")) {
            if (input == null) throw new IllegalStateException("Missing native playing-card head textures");
            return Map.copyOf(new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, List<Texture>>>() {}.getType()));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load native playing-card head textures", exception);
        }
    }
}
