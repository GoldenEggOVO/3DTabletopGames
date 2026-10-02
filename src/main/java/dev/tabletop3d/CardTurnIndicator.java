package dev.tabletop3d;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Two shared displays identify the active seat in both rendering modes. */
final class CardTurnIndicator implements AutoCloseable {
    private final Room room;
    private final Location origin;
    private final TableAudience audience;
    private final NamespacedKey tag;
    private BlockDisplay bar;
    private TextDisplay caption;
    private int seat = -1;
    private long language = -1;

    CardTurnIndicator(Room room, Location origin, TableAudience audience, NamespacedKey tag) {
        this.room = room;
        this.origin = origin.clone();
        this.audience = audience;
        this.tag = tag;
    }

    void sync() {
        int active =
                room.phase == Room.Phase.PLAYING && room.undo == null
                        ? room.board.currentPlayer()
                        : -1;
        if (active < 0 || active >= room.seats.size()) {
            close();
            return;
        }
        if (bar == null || !bar.isValid() || !caption.isValid()) {
            close();
            bar =
                    origin.getWorld()
                            .spawn(
                                    position(active, .87, .022),
                                    BlockDisplay.class,
                                    entity -> {
                                        configure(entity);
                                        entity.addScoreboardTag("tabletop-turn-indicator");
                                        entity.setBlock(Material.LIME_CONCRETE.createBlockData());
                                        entity.setTransformation(
                                                new Transformation(
                                                        new Vector3f(-.22f, 0, -.015f),
                                                        new Quaternionf(),
                                                        new Vector3f(.44f, .008f, .03f),
                                                        new Quaternionf()));
                                    });
            caption =
                    origin.getWorld()
                            .spawn(
                                    position(active, 1.03, .32),
                                    TextDisplay.class,
                                    entity -> {
                                        configure(entity);
                                        entity.setBackgroundColor(Color.fromARGB(0));
                                        entity.setShadowed(true);
                                        entity.setTransformation(
                                                new Transformation(
                                                        new Vector3f(),
                                                        new Quaternionf(),
                                                        new Vector3f(.16f),
                                                        new Quaternionf()));
                                    });
        }
        if (seat != active || language != Language.generation()) {
            bar.teleport(position(active, .87, .022));
            caption.teleport(position(active, 1.03, .32));
            caption.text(
                    Language.component(
                                    "color-eight.turn",
                                    "player",
                                    RoomText.player(room.seats.get(active), active + 1))
                            .colorIfAbsent(NamedTextColor.GREEN));
            seat = active;
            language = Language.generation();
        }
    }

    private void configure(Display entity) {
        entity.setPersistent(false);
        entity.setGravity(false);
        entity.setInvulnerable(true);
        entity.setBrightness(new Display.Brightness(15, 15));
        entity.setViewRange(.35f);
        entity.setTeleportDuration(2);
        entity.getPersistentDataContainer()
                .set(tag, PersistentDataType.STRING, room.id + "|@board");
        if (audience.managed()) entity.setVisibleByDefault(false);
        audience.common(entity);
    }

    private Location position(int active, double radius, double height) {
        double angle = 2 * Math.PI * active / room.capacity;
        Location at =
                origin.clone().add(radius * Math.sin(angle), height, radius * Math.cos(angle));
        at.setYaw((float) -Math.toDegrees(angle));
        return at;
    }

    @Override
    public void close() {
        if (bar != null) audience.remove(bar);
        if (caption != null) audience.remove(caption);
        bar = null;
        caption = null;
        seat = -1;
    }
}
