package dev.tabletop3d;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** One reusable cube: native six-face pips or a single packed item display. */
final class YachtDie implements AutoCloseable {
    private record Pip(BlockDisplay display, Vector3f center, Quaternionf face) {}
    private final TableAudience audience;
    private final List<Entity> entities = new ArrayList<>();
    private final List<Pip> pips = new ArrayList<>();
    private final double size;
    private final BlockDisplay body;
    private final ItemDisplay packed;

    YachtDie(Tabletop3D plugin, Room room, Location origin, NamespacedKey tag,
            TableAudience audience, boolean resourcePack, double size, String id) {
        this.audience = audience;
        this.size = size;
        if (resourcePack) {
            body = null;
            packed = origin.getWorld().spawn(origin, ItemDisplay.class, display -> {
                init(display, room, tag, id);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                display.setItemStack(plugin.pack.item("yacht_die"));
            });
            entities.add(packed);
        } else {
            packed = null;
            body = block(origin, Material.SMOOTH_QUARTZ, room, tag, id);
            int[][] dots = {{0,0}, {-1,-1}, {1,1}, {-1,1}, {1,-1}, {-1,0}, {1,0}};
            for (int face = 1; face <= 6; face++) {
                Quaternionf rotation = TableView.faceRotation(face);
                for (int index : TableView.pipIndices(face)) {
                    Vector3f center = new Vector3f((float)(dots[index][0] * size * .28),
                            (float)(size / 2 + .002), (float)(dots[index][1] * size * .28)).rotate(rotation);
                    pips.add(new Pip(block(origin, Material.BLACK_CONCRETE, room, tag, id), center, rotation));
                }
            }
        }
        entities.forEach(entity -> audience.add(entity, resourcePack));
    }

    void pose(DiceMotion.Pose pose) {
        Quaternionf rotation = pose.rotation();
        Vector3f center = new Vector3f((float)pose.x(), (float)pose.y(), (float)pose.z());
        if (packed != null) {
            transform(packed, center, new Quaternionf(rotation).rotateY((float)Math.PI), new Vector3f((float)size));
            return;
        }
        transform(body, new Vector3f((float)(-size / 2)).rotate(rotation).add(center), rotation, new Vector3f((float)size));
        for (Pip pip : pips) {
            Quaternionf orientation = new Quaternionf(rotation).mul(pip.face());
            Vector3f corner = new Vector3f((float)(size * .065), .002f, (float)(size * .065)).rotate(orientation);
            Vector3f at = new Vector3f(pip.center()).rotate(rotation).add(center).sub(corner);
            transform(pip.display(), at, orientation, new Vector3f((float)(size * .13), .004f, (float)(size * .13)));
        }
    }

    private BlockDisplay block(Location origin, Material material, Room room, NamespacedKey tag, String id) {
        BlockDisplay display = origin.getWorld().spawn(origin, BlockDisplay.class, d -> {
            init(d, room, tag, id);
            d.setBlock(material.createBlockData());
        });
        entities.add(display);
        return display;
    }

    private static void init(Display display, Room room, NamespacedKey tag, String id) {
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.getPersistentDataContainer().set(tag, PersistentDataType.STRING, room.id + "|" + id);
        display.setBrightness(new Display.Brightness(15,15));
        display.setViewRange(.35f);
        display.setInterpolationDuration(2);
        display.setTeleportDuration(2);
    }

    private static void transform(Display display, Vector3f at, Quaternionf rotation, Vector3f scale) {
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(at, new Quaternionf(rotation), scale, new Quaternionf()));
    }

    @Override public void close() { entities.forEach(audience::remove); entities.clear(); }
}
