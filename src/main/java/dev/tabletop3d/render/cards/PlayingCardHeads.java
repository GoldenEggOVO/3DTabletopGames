package dev.tabletop3d.render.cards;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayingCardHeads {
    public record Texture(String value, String signature) {}

    private static final class Catalog {
        static final Map<String, List<Texture>> FACES = load("/playing-card-heads.json");
    }
    private static final Map<Texture, ItemStack> ITEMS = new HashMap<>();

    private PlayingCardHeads() {}

    static List<Texture> tiles(String face) {
        return Catalog.FACES.get(face);
    }

    public static ItemStack item(Texture texture) {
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

    static Transformation pose(double width, boolean standing, int tile) {
        return pose(width, width * 4 / 3, standing, tile, 4, 6);
    }

    public static Transformation pose(double width, double height, boolean standing, int tile, int columns, int rows) {
        double cellWidth = width / columns, cellHeight = height / rows;
        var rotation = standing
                ? new Quaternionf()
                : new Quaternionf().rotateX((float) -Math.PI / 2);
        var translation = new Vector3f((float) ((tile % columns - (columns - 1) / 2.0) * cellWidth),
                (float) ((standing ? height : height / 2) - (tile / columns) * cellHeight), 0);
        rotation.transform(translation);
        if (!standing) translation.y += .006f;

        return new Transformation(translation, rotation,
                new Vector3f((float) (cellWidth * 2), (float) (cellHeight * 2), .016f),
                new Quaternionf().rotateY((float) Math.PI));
    }

    static Map<String, List<Texture>> load(String resource) {
        try (var input = PlayingCardHeads.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing native card head textures: " + resource);
            return Map.copyOf(new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, List<Texture>>>() {}.getType()));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load native playing-card head textures", exception);
        }
    }
}
