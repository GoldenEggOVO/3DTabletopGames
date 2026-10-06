package dev.tabletop3d.render.cards;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.render.TableAudience;
import dev.tabletop3d.room.Room;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public final class TurnRing implements AutoCloseable {
    private final Room room;
    private final Location origin;
    private final List<Part> parts = new ArrayList<>();
    private int direction = 1;
    private double angle;
    private boolean closed;
    private TableAudience audience;

    private record Pose(double x, double z, float yaw, double length) {}

    private record Part(BlockDisplay entity, Pose forward, Pose reverse) {}

    TurnRing(Tabletop3D plugin, Room room, Location origin, NamespacedKey tag) {
        this(plugin, room, origin, tag, null);
    }

    public TurnRing(
            Tabletop3D plugin,
            Room room,
            Location origin,
            NamespacedKey tag,
            TableAudience audience) {
        this.audience = audience;
        this.room = room;
        this.origin = origin.clone();
        for (int arrow = 0; arrow < 4; arrow++) {
            double center = arrow * Math.PI / 2;
            for (int segment = 0; segment < 4; segment++) {
                double start = center - Math.PI / 6 + segment * Math.PI / 12,
                        end = start + Math.PI / 12;
                Pose pose =
                        line(
                                .40 * Math.sin(start),
                                .40 * Math.cos(start),
                                .40 * Math.sin(end),
                                .40 * Math.cos(end));
                add(pose, pose, tag);
            }
            for (int side : new int[] {-1, 1}) {
                add(head(center, side, 1), head(center, side, -1), tag);
            }
        }
    }

    private static Pose head(double center, int side, int direction) {
        double tip = center + direction * Math.PI / 6, sin = Math.sin(tip), cos = Math.cos(tip);
        double x = .40 * sin, z = .40 * cos;
        return line(
                x,
                z,
                x - direction * .058 * cos + side * .035 * sin,
                z + direction * .058 * sin + side * .035 * cos);
    }

    private static Pose line(double x1, double z1, double x2, double z2) {
        return new Pose(
                (x1 + x2) / 2,
                (z1 + z2) / 2,
                (float) Math.toDegrees(Math.atan2(z2 - z1, x2 - x1)),
                Math.hypot(x2 - x1, z2 - z1));
    }

    private void add(Pose forward, Pose reverse, NamespacedKey tag) {
        BlockDisplay entity =
                origin.getWorld()
                        .spawn(
                                at(forward),
                                BlockDisplay.class,
                                display -> {
                                    display.setPersistent(false);
                                    display.setGravity(false);
                                    display.setInvulnerable(true);
                                    display.getPersistentDataContainer()
                                            .set(
                                                    tag,
                                                    PersistentDataType.STRING,
                                                    room.id + "|@board");
                                    display.setBrightness(new Display.Brightness(15, 15));
                                    display.setViewRange(.35f);
                                    display.setTeleportDuration(2);
                                    display.setInterpolationDuration(2);
                                    display.setBlock(Material.WHITE_CONCRETE.createBlockData());
                                    if (audience != null) {
                                        audience.add(display, false);
                                    }
                                    float length = (float) forward.length() + .003f;
                                    display.setTransformation(
                                            new Transformation(
                                                    new Vector3f(-length / 2, 0, -.007f),
                                                    new Quaternionf(),
                                                    new Vector3f(length, .003f, .014f),
                                                    new Quaternionf()));
                                });
        parts.add(new Part(entity, forward, reverse));
    }

    private Location at(Pose pose) {
        double sin = Math.sin(angle), cos = Math.cos(angle);
        Location at =
                origin.clone()
                        .add(
                                pose.x() * cos + pose.z() * sin,
                                .019,
                                -pose.x() * sin + pose.z() * cos);
        at.setYaw(pose.yaw() - (float) Math.toDegrees(angle));
        at.setPitch(0);
        return at;
    }

    private void position() {
        for (Part part : parts) {
            part.entity().teleport(at(direction > 0 ? part.forward() : part.reverse()));
        }
    }

    public void direction(int direction) {
        int next = direction < 0 ? -1 : 1;
        if (closed || this.direction == next) {
            return;
        }
        this.direction = next;
        position();
    }

    public void tick() {
        if (closed || room.phase != Room.Phase.PLAYING) {
            return;
        }

        angle = (angle + direction * .024) % (2 * Math.PI);
        position();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        parts.forEach(
                part -> {
                    if (audience != null) {
                        audience.remove(part.entity());
                    } else {
                        part.entity().remove();
                    }
                });
        parts.clear();
    }
}
