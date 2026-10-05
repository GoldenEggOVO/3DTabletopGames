package dev.tabletop3d;

import dev.tabletop3d.rules.ColorEightGame;
import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.MahjongGame;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Public backs and public discards are separate from owner-only face entities. */
final class HandTable implements AutoCloseable {
    private final Tabletop3D plugin;
    private final Room room;
    private final Location origin;
    private final NamespacedKey tag;
    private final boolean mahjong;
    private final Map<String, PieceView> publicPieces = new LinkedHashMap<>();
    private final Map<String, PieceView> packPieces = new LinkedHashMap<>();
    final TableAudience audience;
    private boolean buildingPacked;
    private long audienceGeneration = -1;
    private final Map<UUID, PrivateView> privateViews = new HashMap<>();
    private final Map<UUID, MahjongAssist> assistance = new HashMap<>();
    private final List<Entity> furniture = new ArrayList<>();
    private TextDisplay deckLabel;
    private TurnRing turnRing;
    private final List<Entity> packFurniture = new ArrayList<>();
    private ItemDisplay packedRing;
    private double ringAngle;
    private int ringDirection = 1;
    private final MahjongTableHud mahjongHud;
    private final CardTurnIndicator cardTurn;
    private HandGame rendered;
    private long revision = -1, language = -1;
    private boolean closed;
    static final double CARD_LIFT = .085;

    record Pose(double x, double z, float yaw, double lift) {
        Pose(double x, double z, float yaw) {
            this(x, z, yaw, 0);
        }
    }

    private record Spec(
            String id, String face, Pose pose, boolean back, boolean standing, int seat) {}

    private final class PieceView {
        Spec spec;
        final List<Entity> parts = new ArrayList<>();
        final List<Vector> offsets = new ArrayList<>();
        ItemDisplay glint;
        boolean disabled;
        int bodyParts = 1;
        final boolean packed;

        PieceView(Spec spec, Player viewer) {
            this.spec = spec;
            packed = viewer == null ? buildingPacked : audience.packed(viewer);
            build(viewer);
        }

        void add(Entity entity, Vector offset) {
            parts.add(entity);
            offsets.add(offset);
        }

        Vector displacement = new Vector(), start = new Vector(), target = new Vector();
        int frame = 4, duration = 4, delay;
        boolean drawing;

        double width() {
            return mahjong ? .094 : .168;
        }

        double height() {
            return spec.standing() ? (mahjong ? .14 : .25) : .012;
        }

        double depth() {
            return spec.standing() ? (mahjong ? .052 : .008) : (mahjong ? .146 : .245);
        }

        void build(Player viewer) {
            Location at = at(spec.pose());
            String id = viewer == null ? "@board" : "@hand:" + spec.id();
            double w = width(), h = height(), d = depth();
            if (packed) {
                String face =
                        spec.back()
                                ? "back"
                                : spec.face().toLowerCase(Locale.ROOT).replace("draw1", "draw");
                ItemDisplay item =
                        origin.getWorld()
                                .spawn(
                                        at,
                                        ItemDisplay.class,
                                        display -> {
                                            configure(display, id, viewer);
                                            display.setItemDisplayTransform(
                                                    ItemDisplay.ItemDisplayTransform.FIXED);
                                            display.setItemStack(
                                                    plugin.pack.item(
                                                            (mahjong ? "mahjong_" : "card_")
                                                                    + face));
                                            Quaternionf rotation =
                                                    spec.standing()
                                                            ? new Quaternionf()
                                                            : new Quaternionf()
                                                                    .rotateX((float) -Math.PI / 2);
                                            display.setTransformation(
                                                    new Transformation(
                                                            new Vector3f(0, (float) (h / 2), 0),
                                                            rotation,
                                                            new Vector3f(
                                                                    (float) w,
                                                                    (float)
                                                                            (spec.standing()
                                                                                    ? h
                                                                                    : d),
                                                                    (float)
                                                                            (spec.standing()
                                                                                    ? d
                                                                                    : h)),
                                                            new Quaternionf()));
                                        });
                add(item, new Vector());
                if (viewer != null) viewer.showEntity(plugin, item);
                return;
            }
            if (!mahjong) {
                if (spec.back()) {
                    add(block(at, Material.WHITE_CONCRETE, w, h, d, id, viewer), new Vector());
                    Vector inset = spec.standing()
                            ? rotated(0, .006, -d / 2 - .0015, spec.pose())
                            : new Vector(0, h + .0005, 0);
                    add(block(at.clone().add(inset), Material.BLUE_CONCRETE, w - .012,
                            spec.standing() ? h - .012 : .002,
                            spec.standing() ? .002 : d - .012, id, viewer), inset);
                } else {
                    var textures = ColorEightHeads.tiles(spec.face());
                    for (int tile = 0; tile < textures.size(); tile++) {
                        final int index = tile;
                        ItemDisplay head = origin.getWorld().spawn(at, ItemDisplay.class, display -> {
                            configure(display, id, viewer);
                            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                            display.setItemStack(PlayingCardHeads.item(textures.get(index)));
                            display.setTransformation(PlayingCardHeads.pose(w, spec.standing() ? h : d,
                                    spec.standing(), index, 4, 6));
                        });
                        add(head, new Vector());
                        if (viewer != null) viewer.showEntity(plugin, head);
                    }
                }
                bodyParts = parts.size();
                return;
            }
            add(block(at, Material.SMOOTH_QUARTZ, w, h, d, id, viewer), new Vector());
            if (spec.back()) {
                Vector panel =
                        spec.standing()
                                ? rotated(0, .012, d / 2 + .002, spec.pose())
                                : new Vector(0, .014, 0);
                add(
                        block(
                                at.clone().add(panel),
                                Material.GREEN_CONCRETE,
                                w * .84,
                                spec.standing() ? h - .024 : .002,
                                spec.standing() ? .002 : d * .86,
                                id,
                                viewer),
                        panel);
            } else
                for (var part : HandModels.of(spec.face()))
                    modelPart(part, id, viewer);
            if (spec.standing()) {
                Vector back = rotated(0, .012, -d / 2 - .002, spec.pose());
                add(
                        block(
                                at.clone().add(back),
                                Material.GREEN_CONCRETE,
                                w * .84,
                                h - .024,
                                .002,
                                id,
                                viewer),
                        back);
            }
        }

        void modelPart(HandModels.Part part, String id, Player viewer) {
            double w = width(),
                    h = height(),
                    d = depth(),
                    pw = part.w() / 32 * w,
                    ph = part.h() / 48 * (spec.standing() ? h : d);
            double x = (part.x() / 32 - .5) * w, y, z;
            Quaternionf rotation;
            double sy, sz;
            if (spec.standing()) {
                y = h - part.y() / 48 * h;
                z = d / 2 + .003 + part.relief();
                sy = ph;
                sz = .001;
                rotation = new Quaternionf().rotateZ((float) -part.roll());
            } else {
                y = .014 + part.relief();
                z = (part.y() / 48 - .5) * d;
                sy = .002;
                sz = ph;
                rotation = new Quaternionf().rotateY((float) -part.roll());
            }
            // Rotate about the cuboid center, then place it in the card's local plane.
            Vector offset = rotated(x, y, z, spec.pose());
            add(
                    modelBlock(
                            at(spec.pose()).add(offset),
                            part.material(),
                            new Vector3f(),
                            pw,
                            sy,
                            sz,
                            rotation,
                            id,
                            viewer),
                    offset);
        }

        void move(Spec next) {
            if (!spec.pose().equals(next.pose())) {
                float delta = next.pose().yaw() - spec.pose().yaw();
                for (int i = 0; i < offsets.size(); i++) {
                    Vector old = offsets.get(i);
                    offsets.set(
                            i, rotated(old.getX(), old.getY(), old.getZ(), new Pose(0, 0, delta)));
                }
                spec = next;
                position();
            } else spec = next;
        }

        void position() {
            for (int i = 0; i < parts.size(); i++)
                parts.get(i).teleport(at(spec.pose()).add(offsets.get(i)).add(displacement));
        }

        void shift(Vector next) {
            if (drawing) {
                target = next.clone();
                return;
            }
            if (target.distanceSquared(next) < 1e-10) return;
            start = displacement.clone();
            target = next.clone();
            duration = 4;
            frame = 0;
        }

        void drawFromDeck(int delay) {
            this.delay = delay;
            drawing = true;
            duration = 12;
            frame = 0;
            target = new Vector();
            start =
                    origin.clone()
                            .add(-.22, .07, 0)
                            .toVector()
                            .subtract(at(spec.pose()).toVector());
            displacement = start.clone();
            position();
        }

        void tick() {
            if (delay > 0) {
                delay--;
                return;
            }
            if (frame >= duration) return;
            double t = ++frame / (double) duration, eased = t * t * (3 - 2 * t);
            displacement = start.clone().multiply(1 - eased).add(target.clone().multiply(eased));
            if (drawing) displacement.setY(displacement.getY() + Math.sin(Math.PI * t) * .14);
            position();
            if (frame == duration) drawing = false;
        }

        boolean valid() {
            return parts.stream().allMatch(Entity::isValid);
        }

        void remove() {
            parts.forEach(audience::remove);
        }

        void highlight(boolean selected) {
            for (int i = 0; i < bodyParts; i++) {
                ((Display) parts.get(i)).setGlowColorOverride(Color.YELLOW);
                parts.get(i).setGlowing(selected);
            }
        }

        void playable(boolean allowed) {
            if (disabled == !allowed) return;
            disabled = !allowed;
            for (Entity part : parts)
                ((Display) part)
                        .setBrightness(new Display.Brightness(allowed ? 15 : 7, allowed ? 15 : 7));
        }

        void bonus(boolean active, Player viewer) {
            if (packed) {
                ItemDisplay display = (ItemDisplay) parts.getFirst();
                var item = plugin.pack.item("mahjong_" + (spec.back() ? "back" : spec.face()));
                var meta = item.getItemMeta();
                meta.setEnchantmentGlintOverride(active);
                item.setItemMeta(meta);
                display.setItemStack(item);
                return;
            }
            if (!active) {
                if (glint != null) {
                    int index = parts.indexOf(glint);
                    parts.remove(index);
                    offsets.remove(index);
                    glint.remove();
                    glint = null;
                }
                return;
            }
            if (glint != null) return;
            var item = new org.bukkit.inventory.ItemStack(Material.SMOOTH_QUARTZ);
            var meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
            // Use the opaque tile material for native foil, outside the body but behind its
            // strokes.
            Vector offset =
                    spec.standing()
                            ? rotated(0, height() / 2, depth() / 2 + .001, spec.pose())
                            : new Vector(0, .0128, 0);
            Vector3f scale =
                    spec.standing()
                            ? new Vector3f((float) (width() * .96), (float) (height() * .96), .001f)
                            : new Vector3f((float) (width() * .96), .001f, (float) (depth() * .96));
            glint =
                    origin.getWorld()
                            .spawn(
                                    at(spec.pose()).add(offset).add(displacement),
                                    ItemDisplay.class,
                                    d -> {
                                        configure(
                                                d,
                                                viewer == null ? "@board" : "@hand:" + spec.id(),
                                                viewer);
                                        d.setItemDisplayTransform(
                                                ItemDisplay.ItemDisplayTransform.NONE);
                                        d.setItemStack(item);
                                        d.setTransformation(
                                                new Transformation(
                                                        new Vector3f(),
                                                        new Quaternionf(),
                                                        scale,
                                                        new Quaternionf()));
                                        if (disabled) d.setBrightness(new Display.Brightness(7, 7));
                                    });
            add(glint, offset);
            if (viewer != null) viewer.showEntity(plugin, glint);
        }

        BoundingBox bounds(boolean sticky) {
            Location base = at(spec.pose()), at = base.clone().add(displacement);
            double angle = Math.toRadians(spec.pose().yaw()),
                    w = width() / 2 + .003,
                    d = depth() / 2 + .003;
            double rx = Math.abs(Math.cos(angle)) * w + Math.abs(Math.sin(angle)) * d;
            double rz = Math.abs(Math.sin(angle)) * w + Math.abs(Math.cos(angle)) * d;
            BoundingBox box =
                    new BoundingBox(
                            at.getX() - rx,
                            at.getY(),
                            at.getZ() - rz,
                            at.getX() + rx,
                            at.getY() + height(),
                            at.getZ() + rz);
            if (sticky && !drawing)
                box.union(
                        new BoundingBox(
                                base.getX() - rx,
                                base.getY(),
                                base.getZ() - rz,
                                base.getX() + rx,
                                base.getY() + height(),
                                base.getZ() + rz));
            return box;
        }

        double cardHitDistance(Location eye, Vector ray, double reach, boolean sticky) {
            double angle = -Math.toRadians(spec.pose().yaw()),
                    cos = Math.cos(angle),
                    sin = Math.sin(angle);
            Vector offset = eye.toVector().subtract(at(spec.pose()).toVector());
            Vector localEye =
                    new Vector(
                            offset.getX() * cos - offset.getZ() * sin,
                            offset.getY(),
                            offset.getX() * sin + offset.getZ() * cos);
            Vector localRay =
                    new Vector(
                            ray.getX() * cos - ray.getZ() * sin,
                            ray.getY(),
                            ray.getX() * sin + ray.getZ() * cos);
            double x = displacement.getX() * cos - displacement.getZ() * sin,
                    y = displacement.getY(),
                    z = displacement.getX() * sin + displacement.getZ() * cos;
            double w = width() / 2 + .003, d = depth() / 2 + .003;
            BoundingBox box = new BoundingBox(x - w, y, z - d, x + w, y + height(), z + d);
            if (sticky && !drawing) box.union(new BoundingBox(-w, 0, -d, w, height(), d));
            var hit = box.rayTrace(localEye, localRay, reach);
            return hit == null ? Double.POSITIVE_INFINITY : hit.getHitPosition().distance(localEye);
        }
    }

    private static final class PrivateView {
        final Player player;
        final Map<String, PieceView> pieces = new LinkedHashMap<>();
        final Map<String, PieceView> indicators = new LinkedHashMap<>();
        final Map<String, CallView> calls = new LinkedHashMap<>();
        final Map<String, CardButton> cardButtons = new LinkedHashMap<>();
        final Map<String, CallView> assistanceButtons = new LinkedHashMap<>();
        final Map<String, Entity> matches = new LinkedHashMap<>();
        HandGame board;
        long revision = -1, language = -1;
        long assistanceLanguage = -1;
        int assistanceSeat = -1;
        int seat = -1, historySize;
        String hover;
        String callGroup;
        String pressedTile;
        Location pressedEye;
        long pressedAt;
        TextDisplay remaining;
        TextDisplay counters;
        boolean callsEnabled;
        boolean packed;
        long dismissedRevision = -1;

        PrivateView(Player player) {
            this.player = player;
        }

        void remove() {
            pieces.values().forEach(PieceView::remove);
            pieces.clear();
            indicators.values().forEach(PieceView::remove);
            indicators.clear();
            assistanceButtons.values().forEach(CallView::remove);
            assistanceButtons.clear();
            if (counters != null) {
                counters.remove();
                counters = null;
            }
            clearCalls();
            if (remaining != null) {
                remaining.remove();
                remaining = null;
            }
        }

        void clearCalls() {
            calls.values().forEach(CallView::remove);
            calls.clear();
            cardButtons.values().forEach(CardButton::remove);
            cardButtons.clear();
        }
    }

    private final class CardButton {
        final List<Entity> parts;
        final BoundingBox bounds;

        CardButton(
                String action,
                Pose pose,
                Material material,
                Component caption,
                Player player,
                double width) {
            Location at = at(pose);
            boolean flat = action.equals("pass");
            if (flat && !audience.packed(player)) at.add(0, RoundCardTable.RIM_TOP + .003 - .017, 0);
            String id = "@call:card:" + action;
            parts = new ArrayList<>();
            if (audience.packed(player)) {
                String model =
                        caption.equals(Component.empty())
                                ? "button_" + action.substring(action.lastIndexOf(':') + 1)
                                : "button_pass";
                ItemDisplay button =
                        origin.getWorld()
                                .spawn(
                                        at,
                                        ItemDisplay.class,
                                        display -> {
                                            configure(display, id, player);
                                            display.setItemDisplayTransform(
                                                    ItemDisplay.ItemDisplayTransform.FIXED);
                                            display.setItemStack(plugin.pack.item(model));
                                            display.setTransformation(
                                                    new Transformation(
                                                            new Vector3f(0, flat ? .007f : .0425f, 0),
                                                            new Quaternionf().rotateX(flat ? (float) -Math.PI / 2 : 0),
                                                            new Vector3f(
                                                                    (float) width, .085f, .014f),
                                                            new Quaternionf()));
                                        });
                parts.add(button);
                player.showEntity(plugin, button);
                if (!caption.equals(Component.empty()))
                    parts.add(
                            text(
                                    at.clone().add(rotated(0, flat ? .016 : .035, flat ? 0 : .011, pose)),
                                    caption,
                                    .15f,
                                    pose.yaw(),
                                    flat,
                                    id,
                                    player));
            } else if (caption.equals(Component.empty())) {
                double radius = width / 2;
                for (int i = 0; i < 4; i++)
                    parts.add(
                            modelBlock(
                                    at,
                                    material,
                                    new Vector3f(0, .0425f, 0),
                                    2 * radius * Math.cos(Math.PI / 8),
                                    2 * radius * Math.sin(Math.PI / 8),
                                    .014,
                                    new Quaternionf().rotateZ((float) (i * Math.PI / 4)),
                                    id,
                                    player));
            } else {
                parts.add(block(at, material, width, flat ? .014 : .085, flat ? .085 : .014, id, player));
                parts.add(
                        text(
                                at.clone().add(rotated(0, flat ? .016 : .035, flat ? 0 : .011, pose)),
                                caption,
                                .15f,
                                pose.yaw(),
                                flat,
                                id,
                                player));
            }
            double depth = flat ? .085 / 2 : .02;
            double angle = Math.toRadians(pose.yaw()),
                    x = Math.abs(Math.cos(angle)) * width / 2 + Math.abs(Math.sin(angle)) * depth,
                    z = Math.abs(Math.sin(angle)) * width / 2 + Math.abs(Math.cos(angle)) * depth;
            bounds =
                    new BoundingBox(
                            at.getX() - x,
                            at.getY(),
                            at.getZ() - z,
                            at.getX() + x,
                            at.getY() + (flat ? .014 : .085),
                            at.getZ() + z);
        }

        boolean valid() {
            return parts.stream().allMatch(Entity::isValid);
        }

        void remove() {
            parts.forEach(Entity::remove);
        }
    }

    private final class CallView {
        final List<Entity> parts;
        final BoundingBox bounds;
        private Boolean enabled;

        CallView(String group, Pose pose, Player player, List<String> faces) {
            this(group, pose, player, faces, Language.component("table.mahjong." + group), .34, null);
        }

        CallView(String group, Pose pose, Player player, List<String> faces,
                Component caption, double width, Boolean enabled) {
            Location at = at(pose);
            String id = "@call:" + group;
            BlockDisplay plate = block(at, enabled == null ? Material.GRAY_CONCRETE : Material.GREEN_CONCRETE, width, .105, .014, id, player);
            var fit =
                    dev.tabletop3d.ui.LabelLayout.fit(
                            caption, (float) (width - .025), .06f);
            TextDisplay label =
                    text(
                            at.clone().add(rotated(0, .047, .010, pose)),
                            fit.text().colorIfAbsent(enabled == null ? NamedTextColor.GOLD : NamedTextColor.WHITE),
                            fit.scale(),
                            pose.yaw(),
                            false,
                            id,
                            player);
            parts = new ArrayList<>(List.of(plate, label));
            if (enabled != null) setEnabled(enabled);
            for (int i = 0; i < faces.size(); i++) {
                Vector offset = rotated((i - (faces.size() - 1) / 2.0) * .101, 0, 0, pose);
                PieceView tile =
                        new PieceView(
                                new Spec(
                                        "preview:" + group + ":" + i,
                                        faces.get(i),
                                        new Pose(
                                                pose.x() + offset.getX(),
                                                pose.z() + offset.getZ(),
                                                pose.yaw(),
                                                pose.lift() + .115),
                                        false,
                                        true,
                                        -1),
                                player);
                parts.addAll(tile.parts);
            }
            double half = Math.max(width / 2, faces.size() * .101 / 2),
                    a = Math.toRadians(pose.yaw()),
                    x = Math.abs(Math.cos(a)) * half + Math.abs(Math.sin(a)) * .03,
                    z = Math.abs(Math.sin(a)) * half + Math.abs(Math.cos(a)) * .03;
            bounds =
                    new BoundingBox(
                            at.getX() - x,
                            at.getY(),
                            at.getZ() - z,
                            at.getX() + x,
                            at.getY() + (faces.isEmpty() ? .105 : .255),
                            at.getZ() + z);
        }

        boolean valid() {
            return parts.stream().allMatch(Entity::isValid);
        }

        void setEnabled(boolean enabled) {
            if (Objects.equals(this.enabled, enabled)) return;
            this.enabled = enabled;
            int brightness = enabled ? 15 : 7;
            for (Entity part : parts) ((Display) part).setBrightness(new Display.Brightness(brightness, brightness));
        }

        void remove() {
            parts.forEach(Entity::remove);
        }
    }

    HandTable(Tabletop3D plugin, Room room, Location surfaceOrigin, NamespacedKey tag) {
        this.plugin = plugin;
        this.room = room;
        this.origin = surfaceOrigin.clone();
        this.tag = tag;
        mahjong = room.kind.equals("mahjong");
        audience = new TableAudience(plugin, origin);
        syncFurniture();
        mahjongHud = mahjong ? new MahjongTableHud(plugin, room, origin, tag, audience) : null;
        cardTurn = mahjong ? null : new CardTurnIndicator(room, origin, audience, tag);
        sync();
    }

    static Pose handPose(int seat, int players, int index, int count, boolean mahjong) {
        if (!mahjong) {
            double centered = index - (count - 1) / 2.0,
                    unit = Math.min(.15, (players == 5 ? 1.18 : 1.50) / Math.max(1, count - 1));
            Pose pose = seatPose(seat, players, centered * unit, 1.02 + index * CardLayout.DEPTH_STEP);
            return new Pose(pose.x(), pose.z(), pose.yaw(), index * CardLayout.HEIGHT_STEP);
        }
        int columns = 17;
        int row = index / columns;
        int inRow = Math.min(columns, Math.max(1, count - row * columns));
        if (inRow % 3 == 2) inRow--;
        double tangent = (index % columns - (inRow - 1) / 2.0) * .101;
        if (index % columns >= inRow) tangent += .075;
        return seatPose(seat, players, tangent, 1.27 - row * .085);
    }

    static Pose riverPose(int seat, int players, int index) {
        return seatPose(seat, players, (index % 6 - 2.5) * .101, .65 + (index / 6) * .16);
    }

    static Pose exposedPose(int seat, int players, int index) {
        return seatPose(seat, players, 1.22 - (index % 7) * .101, 1.10 - (index / 7) * .16);
    }

    private static Pose seatPose(int seat, int players, double tangent, double radius) {
        double angle = 2 * Math.PI * seat / players;
        return new Pose(
                tangent * Math.cos(angle) + radius * Math.sin(angle),
                -tangent * Math.sin(angle) + radius * Math.cos(angle),
                (float) -Math.toDegrees(angle));
    }

    private Location at(Pose pose) {
        Location at = origin.clone().add(pose.x(), .017 + pose.lift(), pose.z());
        at.setYaw(pose.yaw());
        return at;
    }

    private static Vector rotated(double x, double y, double z, Pose pose) {
        double angle = -Math.toRadians(pose.yaw());
        return new Vector(
                x * Math.cos(angle) + z * Math.sin(angle),
                y,
                -x * Math.sin(angle) + z * Math.cos(angle));
    }

    private void configure(Display display, String id, Player viewer) {
        // Set before materials/text are assigned in the spawn callback.
        if (viewer != null) display.setVisibleByDefault(false);
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.getPersistentDataContainer()
                .set(tag, PersistentDataType.STRING, room.id + "|" + id);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(.35f);
        display.setTeleportDuration(2);
        display.setInterpolationDuration(2);
        if (viewer == null) audience.add(display, buildingPacked);
    }

    private BlockDisplay block(
            Location at,
            Material material,
            double width,
            double height,
            double depth,
            String id,
            Player viewer) {
        BlockDisplay entity =
                origin.getWorld()
                        .spawn(
                                at,
                                BlockDisplay.class,
                                d -> {
                                    configure(d, id, viewer);
                                    d.setBlock(material.createBlockData());
                                    d.setTransformation(
                                            new Transformation(
                                                    new Vector3f(
                                                            (float) -width / 2,
                                                            0,
                                                            (float) -depth / 2),
                                                    new Quaternionf(),
                                                    new Vector3f(
                                                            (float) width,
                                                            (float) height,
                                                            (float) depth),
                                                    new Quaternionf()));
                                });
        if (viewer != null) viewer.showEntity(plugin, entity);
        return entity;
    }

    private BlockDisplay modelBlock(
            Location at,
            Material material,
            Vector3f center,
            double width,
            double height,
            double depth,
            Quaternionf rotation,
            String id,
            Player viewer) {
        BlockDisplay entity =
                origin.getWorld()
                        .spawn(
                                at,
                                BlockDisplay.class,
                                d -> {
                                    configure(d, id, viewer);
                                    d.setBlock(material.createBlockData());
                                    Vector3f translation =
                                            rotation.transform(
                                                            new Vector3f(
                                                                    (float) -width / 2,
                                                                    (float) -height / 2,
                                                                    (float) -depth / 2))
                                                    .add(center);
                                    d.setTransformation(
                                            new Transformation(
                                                    translation,
                                                    rotation,
                                                    new Vector3f(
                                                            (float) width,
                                                            (float) height,
                                                            (float) depth),
                                                    new Quaternionf()));
                                });
        if (viewer != null) viewer.showEntity(plugin, entity);
        return entity;
    }

    private TextDisplay text(
            Location at,
            Component value,
            float scale,
            float yaw,
            boolean flat,
            String id,
            Player viewer) {
        TextDisplay entity =
                origin.getWorld()
                        .spawn(
                                at,
                                TextDisplay.class,
                                d -> {
                                    configure(d, id, viewer);
                                    d.setBillboard(Display.Billboard.FIXED);
                                    d.setRotation(yaw, flat ? -90 : 0);
                                    d.text(value);
                                    d.setLineWidth(Integer.MAX_VALUE);
                                    d.setAlignment(TextDisplay.TextAlignment.CENTER);
                                    d.setDefaultBackground(false);
                                    d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                                    d.setShadowed(false);
                                    d.setSeeThrough(false);
                                    d.setTransformation(
                                            new Transformation(
                                                    new Vector3f(0, -.125f * scale, 0),
                                                    new Quaternionf(),
                                                    new Vector3f(scale),
                                                    new Quaternionf()));
                                });
        if (viewer != null) viewer.showEntity(plugin, entity);
        return entity;
    }

    void sync() {
        if (closed) return;
        audience.refresh();
        syncFurniture();
        if (cardTurn != null) cardTurn.sync();
        if (!(room.board instanceof HandGame game)) {
            for (PrivateView view : List.copyOf(privateViews.values())) clear(view.player);
            publicPieces.values().forEach(PieceView::remove);
            publicPieces.clear();
            packPieces.values().forEach(PieceView::remove);
            packPieces.clear();
            rendered = null;
            return;
        }
        for (PrivateView view : List.copyOf(privateViews.values()))
            if (!eligible(view.player)) clear(view.player);
        if (rendered == game
                && revision == room.revision
                && language == Language.generation()
                && audienceGeneration == audience.generation
                && publicPieces.values().stream().allMatch(PieceView::valid)
                && packPieces.values().stream().allMatch(PieceView::valid)) return;
        List<Spec> wanted = new ArrayList<>();
        for (int seat = 0; seat < game.playerCount(); seat++) {
            List<HandGame.Piece> revealed = game instanceof MahjongGame tiles
                    ? tiles.revealedHand(seat) : List.of();
            int count = Math.min(54, game.handSize(seat));
            if (!revealed.isEmpty()) {
                for (int i = 0; i < revealed.size(); i++) {
                    var tile = revealed.get(i);
                    wanted.add(new Spec("winner:" + seat + ":" + tile.id(), tile.face(),
                            handPose(seat, game.playerCount(), i, revealed.size(), true), false, false, seat));
                }
            } else for (int i = 0; i < count; i++)
                wanted.add(
                        new Spec(
                                "back:" + seat + ":" + i,
                                "",
                                handPose(seat, game.playerCount(), i, count, mahjong),
                                true,
                                true,
                                seat));
            if (mahjong) mahjongPublicRows(wanted, (MahjongGame) game, seat);
        }
        if (!mahjong)
            game.cells().stream()
                    .filter(cell -> cell.id().equals("discard") && !cell.piece().isEmpty())
                    .findFirst()
                    .ifPresent(
                            cell ->
                                    wanted.add(
                                            new Spec(
                                                    "discard",
                                                    cell.piece(),
                                                    new Pose(.16, 0, 0),
                                                    false,
                                                    false,
                                                    -1)));
        buildingPacked = false;
        reconcile(publicPieces, audience.needed(false) ? wanted : List.of(), null);
        buildingPacked = true;
        reconcile(packPieces, audience.needed(true) ? wanted : List.of(), null);
        buildingPacked = false;
        if (mahjong) {
            Map<String, String> info = game.publicInfo();
            for (PieceView piece : publicPieces.values())
                piece.bonus(
                        !piece.spec.back() && MahjongPresentation.bonus(info, piece.spec.face()),
                        null);
            for (PieceView piece : packPieces.values())
                piece.bonus(
                        !piece.spec.back() && MahjongPresentation.bonus(info, piece.spec.face()),
                        null);
        }
        if (deckLabel != null)
            deckLabel.text(
                    Language.component("table.hand.deck", "count", game.deckSize())
                            .append(Component.newline())
                            .append(HandText.tableHint(room.kind, game)));
        int direction =
                game.publicInfo().getOrDefault("direction", "Clockwise").equals("Counterclockwise")
                        ? -1
                        : 1;
        if (turnRing != null) turnRing.direction(direction);
        if (packedRing != null && ringDirection != direction)
            packedRing.setItemStack(
                    plugin.pack.item(direction < 0 ? "ring_reverse" : "ring_forward"));
        ringDirection = direction;
        rendered = game;
        revision = room.revision;
        language = Language.generation();
        audienceGeneration = audience.generation;
        for (PrivateView view : privateViews.values()) hideOwnBacks(view.player, view.seat);
    }

    private void mahjongPublicRows(List<Spec> wanted, MahjongGame game, int seat) {
        for (var tile : MahjongLayout.river(game.discards(seat), game.riichiTile(seat)))
            publicTile(
                    wanted, tile, "discard:" + seat, seat, tile.offset(), .65 + tile.row() * .16);
        double edge = 0;
        int group = 0;
        for (var meld : game.melds(seat)) {
            if (group % 2 == 0) edge = 0;
            var tiles = MahjongLayout.meld(meld, seat, game.concealedMeldFacesVisible());
            for (var tile : tiles) {
                publicTile(
                        wanted,
                        tile,
                        "exposed:" + seat,
                        seat,
                        1.22 - edge - tile.offset(),
                        1.10 - (group / 2) * .17);
            }
            edge +=
                    tiles.stream()
                                    .filter(tile -> tile.lift() == 0)
                                    .mapToDouble(
                                            tile ->
                                                    tile.offset()
                                                            + (tile.sideways() ? .153 : .101) / 2)
                                    .max()
                                    .orElse(0)
                            + .04;
            group++;
        }
        var flowers = game.flowers(seat);
        int flowerRow = (group + 1) / 2;
        for (int index = 0; index < flowers.size(); index++) {
            var tile = new MahjongLayout.Tile(flowers.get(index), 0, 0, false, 0);
            publicTile(
                    wanted,
                    tile,
                    "exposed:" + seat,
                    seat,
                    1.22 - index * .101,
                    1.10 - flowerRow * .17);
        }
    }

    private void publicTile(
            List<Spec> wanted,
            MahjongLayout.Tile tile,
            String prefix,
            int seat,
            double tangent,
            double radius) {
        Pose base = seatPose(seat, 4, tangent, radius);
        Pose pose =
                new Pose(base.x(), base.z(), base.yaw() + (tile.sideways() ? 90 : 0), tile.lift());
        var piece = tile.piece();
        wanted.add(
                new Spec(
                        prefix + ":" + piece.id(),
                        piece.face(),
                        pose,
                        piece.face().equals("back"),
                        false,
                        seat));
    }

    private void reconcile(Map<String, PieceView> existing, List<Spec> wanted, Player viewer) {
        Set<String> kept = new HashSet<>();
        for (Spec spec : wanted) {
            kept.add(spec.id());
            PieceView old = existing.get(spec.id());
            if (old != null
                    && old.valid()
                    && old.spec.face().equals(spec.face())
                    && old.spec.back() == spec.back()
                    && old.spec.standing() == spec.standing()) old.move(spec);
            else {
                if (old != null) old.remove();
                existing.put(spec.id(), new PieceView(spec, viewer));
            }
        }
        for (var iterator = existing.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (!kept.contains(entry.getKey())) {
                entry.getValue().remove();
                iterator.remove();
            }
        }
    }

    private boolean eligible(Player player) {
        return !closed
                && (room.phase == Room.Phase.PLAYING || room.phase == Room.Phase.FINISHED)
                && room.board instanceof HandGame
                && plugin.allowed(player)
                && (plugin.pack == null || plugin.pack.canPlay(player, room.kind))
                && room.seat(player.getUniqueId()) >= 0
                && origin.getWorld().equals(player.getWorld());
    }

    void show(Player player) {
        if (!eligible(player)) {
            clear(player);
            return;
        }
        PrivateView old = privateViews.get(player.getUniqueId());
        if (old != null && old.packed != audience.packed(player)) clear(player);
        HandGame game = (HandGame) room.board;
        int seat = room.seat(player.getUniqueId());
        PrivateView view =
                privateViews.computeIfAbsent(player.getUniqueId(), id -> new PrivateView(player));
        view.packed = audience.packed(player);
        boolean callsEnabled = mahjong && room.phase == Room.Phase.PLAYING;
        if (view.board == game
                && view.revision == room.revision
                && view.language == Language.generation()
                && view.seat == seat
                && view.callsEnabled == callsEnabled
                && view.pieces.values().stream().allMatch(PieceView::valid)
                && view.indicators.values().stream().allMatch(PieceView::valid)
                && view.calls.values().stream().allMatch(CallView::valid)
                && view.cardButtons.values().stream().allMatch(CardButton::valid)
                && view.assistanceButtons.values().stream().allMatch(CallView::valid)
                && (view.counters == null || view.counters.isValid())) return;
        if ((view.seat != seat || view.board != game) && view.seat >= 0) {
            restoreBacks(player, view.seat);
            view.remove();
            view.dismissedRevision = -1;
        }
        List<HandGame.Piece> hand = game.hand(seat);
        if (isRiichi(game)) hand = assistance(player).arrange(hand, view.pieces.keySet());
        if (game instanceof MahjongGame tiles && !tiles.revealedHand(seat).isEmpty()) hand = List.of();
        List<Spec> wanted = new ArrayList<>();
        for (int i = 0; i < Math.min(54, hand.size()); i++) {
            HandGame.Piece piece = hand.get(i);
            wanted.add(
                    new Spec(
                            piece.id(),
                            piece.face(),
                            handPose(seat, game.playerCount(), i, hand.size(), mahjong),
                            false,
                            true,
                            seat));
        }
        Set<String> previous = Set.copyOf(view.pieces.keySet());
        boolean animate =
                !mahjong
                        && view.board == game
                        && room.history.size() == view.historySize + 1
                        && !room.restoring;
        reconcile(view.pieces, wanted, player);
        hideOwnBacks(player, seat);
        if (mahjong) {
            Map<String, String> info = game.publicInfo();
            for (PieceView piece : view.pieces.values())
                piece.bonus(MahjongPresentation.bonus(info, piece.spec.face()), player);
            String[] faces = info.getOrDefault("dora", "").split(","),
                    ids = info.getOrDefault("doraIds", "").split(",");
            List<Spec> indicators = new ArrayList<>();
            for (int i = 0; i < (faces[0].isEmpty() ? 0 : 5); i++) {
                    boolean revealed = i < faces.length && !faces[i].isEmpty();
                    String id = revealed && i < ids.length && !ids[i].isEmpty() ? ids[i] : "slot-" + i;
                    Pose frame = seatPose(seat, 4, (i - 2) * .101, 1.477);
                    indicators.add(
                            new Spec(
                                    "dora:" + seat + ":" + id,
                                    revealed ? faces[i] : "back",
                                    new Pose(frame.x(), frame.z(), frame.yaw(), -.197),
                                    !revealed,
                                    true,
                                    seat));
                }
            reconcile(view.indicators, indicators, player);
            for (PieceView piece : view.indicators.values()) piece.highlight(false);
            if (!indicators.isEmpty()) {
                if (view.counters == null) {
                    Pose frame = seatPose(seat, 4, 0, 1.509);
                    view.counters = text(at(new Pose(frame.x(), frame.z(), frame.yaw(), -.265)),
                            Component.empty(), .11f, frame.yaw(), false, "@counters", player);
                }
                view.counters.text(Language.component("table.mahjong.counters",
                        "deposits", info.getOrDefault("riichiSticks", "0"),
                        "honba", info.getOrDefault("honba", "0")));
            } else if (view.counters != null) {
                view.counters.remove();
                view.counters = null;
            }
        }
        clearMatches(view);
        view.hover = null;
        view.callGroup = null;
        int drawn = 0;
        if (mahjong) {
            if (view.remaining == null)
                view.remaining =
                        text(
                                origin,
                                Component.empty(),
                                .13f,
                                (float) (-360.0 * seat / game.playerCount()),
                                false,
                                "@remaining",
                                player);
            else view.remaining.text(Component.empty());
        }
        for (var piece : view.pieces.values()) {
            if (mahjong) piece.highlight(false);
            if (animate && !previous.contains(piece.spec.id()))
                piece.drawFromDeck(Math.min(3, drawn++));
            else piece.shift(new Vector());
        }
        view.historySize = room.history.size();
        view.board = game;
        view.revision = room.revision;
        view.language = Language.generation();
        view.seat = seat;
        updatePlayable(view);
        view.callsEnabled = callsEnabled;
        buildCalls(view);
        buildAssistance(view);
        if (!mahjong) buildCardButtons(view);
    }

    private String pendingCard(PrivateView view) {
        return room.board instanceof ColorEightGame game ? game.pendingCard(view.seat) : null;
    }

    private static boolean isRiichi(HandGame game) {
        return game.publicInfo().getOrDefault("profile", "").equals("riichi");
    }

    private MahjongAssist assistance(Player player) {
        return assistance.computeIfAbsent(player.getUniqueId(), id -> new MahjongAssist());
    }

    MahjongAssist assistance(int seat) {
        if (!(room.board instanceof MahjongGame game) || !isRiichi(game)) return null;
        return seat >= 0 && seat < room.seats.size() ? assistance.get(room.seats.get(seat).id()) : null;
    }

    private List<String> visibleMahjongActions(PrivateView view) {
        List<String> legal = room.board.legalActions(view.seat);
        return isRiichi((HandGame) room.board) ? assistance(view.player).visibleActions(legal) : legal;
    }

    private void buildAssistance(PrivateView view) {
        boolean visible = mahjong && isRiichi((HandGame) room.board) && view.callsEnabled;
        MahjongAssist preferences = assistance(view.player);
        if (visible && view.assistanceSeat == view.seat
                && view.assistanceLanguage == Language.generation()
                && view.assistanceButtons.size() == MahjongAssist.Option.values().length
                && view.assistanceButtons.values().stream().allMatch(CallView::valid)) {
            for (MahjongAssist.Option option : MahjongAssist.Option.values())
                view.assistanceButtons.get(option.id).setEnabled(preferences.enabled(option));
            return;
        }
        view.assistanceButtons.values().forEach(CallView::remove);
        view.assistanceButtons.clear();
        if (!visible) return;
        int index = 0;
        for (MahjongAssist.Option option : MahjongAssist.Option.values()) {
            Pose frame = seatPose(view.seat, 4, .70 + index++ * .16, 1.525);
            view.assistanceButtons.put(option.id, new CallView("assist:" + option.id,
                    new Pose(frame.x(), frame.z(), frame.yaw(), -.16), view.player, List.of(),
                    Language.component("table.mahjong.assist." + option.id), .12, preferences.enabled(option)));
        }
        view.assistanceSeat = view.seat;
        view.assistanceLanguage = Language.generation();
    }

    boolean toggleAssistance(Player player, String option) {
        if (!eligible(player) || !isRiichi((HandGame) room.board) || room.phase != Room.Phase.PLAYING) return false;
        if (!assistance(player).toggle(option)) return false;
        PrivateView view = privateViews.get(player.getUniqueId());
        if (view != null) view.revision = -1;
        show(player);
        return true;
    }

    void assistanceHint(Player player, String selected) {
        PrivateView view = privateViews.get(player.getUniqueId());
        if (view == null) return;
        String prefix = "@call:assist:";
        if (selected != null && selected.startsWith(prefix)) {
            String option = selected.substring(prefix.length());
            for (MahjongAssist.Option candidate : MahjongAssist.Option.values())
                if (candidate.id.equals(option))
                    player.sendActionBar(Language.component("hint.mahjong.assist." + option)
                            .colorIfAbsent(NamedTextColor.YELLOW));
        }
    }

    private void buildCardButtons(PrivateView view) {
        if (room.phase != Room.Phase.PLAYING) return;
        List<String> legal = room.board.legalActions(view.seat);
        String pending = pendingCard(view);
        PieceView piece = pending == null ? null : view.pieces.get(pending);
        if (piece != null) {
            piece.highlight(true);
            piece.shift(new Vector(0, CARD_LIFT, 0));
            String[] colors = {"r", "b", "y", "p"};
            Material[] materials = {
                Material.RED_CONCRETE,
                Material.BLUE_CONCRETE,
                Material.YELLOW_CONCRETE,
                Material.PURPLE_CONCRETE
            };
            for (int i = 0; i < 4; i++) {
                Vector delta = rotated((i - 1.5) * .10, 0, 0, piece.spec.pose());
                Pose base = piece.spec.pose();
                String action = "play:" + pending + ":" + colors[i];
                if (legal.contains(action))
                    view.cardButtons.put(
                            action,
                            new CardButton(
                                    action,
                                    new Pose(
                                            base.x() + delta.getX(),
                                            base.z() + delta.getZ(),
                                            base.yaw(),
                                            base.lift() + CARD_LIFT + .28),
                                    materials[i],
                                    Component.empty(),
                                    view.player,
                                    .085));
            }
        } else if (legal.contains("pass"))
            view.cardButtons.put(
                    "pass",
                    new CardButton(
                            "pass",
                            seatPose(view.seat, room.board.playerCount(), 0, 1.35),
                            Material.GRAY_CONCRETE,
                            Language.component("table.card.pass"),
                            view.player,
                            .25));
    }

    String cardAction(Player player, String id) {
        show(player);
        PrivateView view = privateViews.get(player.getUniqueId());
        if (view == null || mahjong) return null;
        List<String> legal = room.board.legalActions(view.seat);
        if (legal.contains("choose:" + id)) return "choose:" + id;
        return legal.contains("play:" + id) ? "play:" + id : null;
    }

    private void buildCalls(PrivateView view) {
        view.clearCalls();
        if (!view.callsEnabled || view.dismissedRevision == room.revision) return;
        HandGame game = (HandGame) room.board;
        var groups = MahjongControls.groups(visibleMahjongActions(view));
        Map<String, String> buttons = new LinkedHashMap<>();
        if ("riichi".equals(view.callGroup)) {
            buttons.put("back", "back");
            buttons.put("dismiss", "dismiss");
        } else if (view.callGroup != null) {
            for (String choice : groups.getOrDefault(view.callGroup, List.of()))
                buttons.put("choice:" + choice, view.callGroup);
            buttons.put("back", "back");
            if (groups.containsKey("pass")) buttons.put("pass", "pass");
        } else for (String group : groups.keySet()) buttons.put(group, group);
        int i = 0;
        for (var button : buttons.entrySet()) {
            int row = i / 3, count = Math.min(3, buttons.size() - row * 3);
            Pose base =
                    seatPose(view.seat, game.playerCount(), (i % 3 - (count - 1) / 2.0) * .43, .79);
            i++;
            String action =
                    button.getKey().startsWith("choice:")
                            ? button.getKey().substring(7)
                            : groups.getOrDefault(button.getKey(), List.of()).stream()
                                    .findFirst()
                                    .orElse("");
            List<String> faces =
                    button.getKey().startsWith("choice:")
                                    && Set.of("chi", "pon", "kan").contains(button.getValue())
                            ? MahjongPresentation.choiceFaces(game, view.seat, action)
                            : List.of();
            view.calls.put(
                    button.getKey(),
                    new CallView(
                            button.getValue(),
                            new Pose(base.x(), base.z(), base.yaw(), .30 + row * .30),
                            view.player,
                            faces));
        }
    }

    boolean expandCall(Player player, String group) {
        show(player);
        PrivateView view = privateViews.get(player.getUniqueId());
        if (view == null || !view.callsEnabled) return false;
        if (group.equals("back")) {
            view.callGroup = null;
            updatePlayable(view);
            buildCalls(view);
            return true;
        }
        if (!Set.of("chi", "pon", "kan", "riichi").contains(group)) return false;
        if (MahjongControls.groups(visibleMahjongActions(view))
                .getOrDefault(group, List.of())
                .isEmpty()) return false;
        view.callGroup = group;
        updatePlayable(view);
        buildCalls(view);
        if (group.equals("riichi"))
            player.sendActionBar(
                    Language.component("hint.mahjong.riichi").colorIfAbsent(NamedTextColor.GOLD));
        return true;
    }

    private void updatePlayable(PrivateView view) {
        Set<String> legal = Set.copyOf(((HandGame) room.board).legalActions(view.seat));
        if (!mahjong) {
            for (PieceView piece : view.pieces.values())
                piece.playable(
                        view.seat != room.board.currentPlayer()
                                || room.phase != Room.Phase.PLAYING
                                || legal.stream()
                                        .anyMatch(
                                                a ->
                                                        a.equals("play:" + piece.spec.id())
                                                                || a.startsWith(
                                                                        "play:"
                                                                                + piece.spec.id()
                                                                                + ":")));
            return;
        }
        for (PieceView piece : view.pieces.values()) {
            String id = piece.spec.id();
            boolean selected = legal.contains("exchange-remove:" + id);
            boolean kuikae =
                    room.phase == Room.Phase.PLAYING
                            && room.board instanceof MahjongGame game
                            && game.kuikaeForbidden(view.seat, id);
            piece.playable(!kuikae);
            if (selected) {
                piece.highlight(true);
                piece.shift(new Vector(0, .035, 0));
            }
        }
    }

    void keepHandPress(Player player) {
        keepHandPress(player, System.nanoTime());
    }

    void keepHandPress(Player player, long now) {
        PrivateView view = privateViews.get(player.getUniqueId());
        if (view != null && view.pressedTile != null) view.pressedAt = now;
    }

    String handAction(Player player, String id) {
        return handAction(player, id, System.nanoTime());
    }

    String handAction(Player player, String id, long now) {
        show(player);
        PrivateView view = privateViews.get(player.getUniqueId());
        if (view == null || !mahjong) return null;
        String action = ("riichi".equals(view.callGroup) ? "riichi:" : "discard:") + id;
        List<String> legal = ((HandGame) room.board).legalActions(view.seat);
        if (legal.contains("exchange-remove:" + id)) return "exchange-remove:" + id;
        if (legal.contains("exchange-add:" + id)) return "exchange-add:" + id;
        if (!view.pieces.containsKey(id) || !legal.contains(action)) {
            keepHandPress(player, now);
            return null;
        }
        Location eye = player.getEyeLocation();
        if (view.pressedEye != null
                && eye != null
                && !Objects.equals(id, view.pressedTile)
                && now - view.pressedAt < 600_000_000L
                && eye.getWorld().equals(view.pressedEye.getWorld())
                && eye.distanceSquared(view.pressedEye) < .0064
                && eye.getDirection().dot(view.pressedEye.getDirection())
                        > Math.cos(Math.toRadians(2))) {
            view.pressedAt = now;
            return null;
        }
        view.pressedTile = id;
        view.pressedEye = eye == null ? null : eye.clone();
        view.pressedAt = now;
        return action;
    }

    void hover(Player player, String selected) {
        show(player);
        PrivateView view = privateViews.get(player.getUniqueId());
        if (view == null) return;
        PieceView target =
                selected != null && selected.startsWith("public:")
                        ? publicFor(player).get(selected.substring(7))
                        : view.pieces.get(selected);
        if (target == null && selected != null && selected.startsWith("public:"))
            target = view.indicators.get(selected.substring(7));
        if (target == null || target.spec.back()) selected = null;
        if (Objects.equals(view.hover, selected)) return;
        view.hover = selected;
        List<PieceView> pieces = new ArrayList<>(view.pieces.values());
        int chosen = -1;
        List<String> legal = mahjong ? ((HandGame) room.board).legalActions(view.seat) : List.of();
        for (int i = 0; i < pieces.size(); i++)
            if (pieces.get(i).spec.id().equals(selected)) chosen = i;
        for (int i = 0; i < pieces.size(); i++) {
            if (mahjong) {
                boolean exchanging = legal.contains("exchange-remove:" + pieces.get(i).spec.id());
                pieces.get(i)
                        .highlight(
                                exchanging
                                        || selected != null
                                                && MahjongPresentation.sameType(
                                                        pieces.get(i).spec.face(),
                                                        target.spec.face()));
                pieces.get(i).shift(new Vector(0, i == chosen ? .065 : exchanging ? .035 : 0, 0));
                continue;
            }
            PieceView piece = pieces.get(i);
            boolean raised = i == chosen || Objects.equals(piece.spec.id(), pendingCard(view));
            piece.highlight(raised);
            piece.shift(new Vector(0, raised ? CARD_LIFT : 0, 0));
        }
        if (mahjong && view.remaining != null) {
            for (PieceView piece : view.indicators.values())
                piece.highlight(
                        selected != null
                                && MahjongPresentation.sameType(
                                        piece.spec.face(), target.spec.face()));
            clearMatches(view);
            if (selected == null) view.remaining.text(Component.empty());
            else {
                view.remaining.teleport(at(target.spec.pose()).add(0, .24, 0));
                int remaining =
                        MahjongPresentation.remaining(
                                (HandGame) room.board, view.seat, target.spec.face());
                Component hint =
                        Language.component("table.mahjong.remaining", "count", remaining)
                                .colorIfAbsent(
                                        remaining == 0
                                                ? NamedTextColor.RED
                                                : NamedTextColor.YELLOW);
                if (room.board instanceof MahjongGame game
                        && game.noYaku(
                                view.seat, target.spec.id().replaceFirst("^discard:[0-3]:", "")))
                    hint =
                            hint.append(Component.newline())
                                    .append(
                                            Language.component("table.mahjong.no-yaku")
                                                    .color(NamedTextColor.RED));
                view.remaining.text(hint);
                for (var entry : publicFor(player).entrySet()) {
                    PieceView piece = entry.getValue();
                    if (piece.spec.back()
                            || !MahjongPresentation.sameType(piece.spec.face(), target.spec.face()))
                        continue;
                    // Replace only this viewer's public body with an identical private glowing
                    // body.
                    Entity body = piece.parts.getFirst();
                    player.hideEntity(plugin, body);
                    Display copy;
                    if (piece.packed) {
                        PieceView clone = new PieceView(piece.spec, player);
                        copy = (Display) clone.parts.getFirst();
                        clone.bonus(
                                MahjongPresentation.bonus(
                                        ((HandGame) room.board).publicInfo(), piece.spec.face()),
                                player);
                    } else
                        copy =
                                block(
                                        at(piece.spec.pose()),
                                        Material.SMOOTH_QUARTZ,
                                        piece.width(),
                                        piece.height(),
                                        piece.depth(),
                                        "@board",
                                        player);
                    copy.setGlowColorOverride(Color.YELLOW);
                    copy.setGlowing(true);
                    view.matches.put(entry.getKey(), copy);
                }
            }
        }
    }

    void dismissCalls(Player player) {
        show(player);
        PrivateView view = privateViews.get(player.getUniqueId());
        if (view != null
                && view.calls.containsKey("dismiss")
                && room.board instanceof MahjongGame game
                && game.legalActions(view.seat).contains("tsumo")) {
            String discard = game.riichiDrawDiscard(view.seat);
            if (discard != null) {
                plugin.action(
                        player, room, room.revision, new com.google.gson.JsonPrimitive(discard));
                return;
            }
        }
        if (view != null && view.calls.containsKey("dismiss")) {
            view.dismissedRevision = room.revision;
            view.callGroup = null;
            updatePlayable(view);
            view.clearCalls();
        }
    }

    String callHit(Player player, Location eye, Vector direction) {
        if (!eligible(player)) {
            clear(player);
            return null;
        }
        if (!origin.getWorld().equals(eye.getWorld()) || direction.lengthSquared() < 1e-12)
            return null;
        show(player);
        PrivateView view = privateViews.get(player.getUniqueId());
        if (view == null) return null;
        Vector ray = direction.clone().normalize();
        double nearest = TableGeometry.REACH;
        String selected = null;
        for (var button : view.cardButtons.entrySet()) {
            var hit = button.getValue().bounds.rayTrace(eye.toVector(), ray, nearest);
            if (hit != null) {
                nearest = hit.getHitPosition().distance(eye.toVector());
                selected = "card:" + button.getKey();
            }
        }
        for (var call : view.calls.entrySet()) {
            var hit = call.getValue().bounds.rayTrace(eye.toVector(), ray, nearest);
            if (hit != null) {
                nearest = hit.getHitPosition().distance(eye.toVector());
                selected = call.getKey();
            }
        }
        for (var button : view.assistanceButtons.entrySet()) {
            var hit = button.getValue().bounds.rayTrace(eye.toVector(), ray, nearest);
            if (hit != null) {
                nearest = hit.getHitPosition().distance(eye.toVector());
                selected = "assist:" + button.getKey();
            }
        }
        if (selected == null
                || origin.getWorld()
                                .rayTraceBlocks(
                                        eye,
                                        ray,
                                        Math.max(.001, nearest - .015),
                                        FluidCollisionMode.NEVER,
                                        true)
                        != null) return null;
        return selected;
    }

    void tick() {
        sync();
        if (turnRing != null) turnRing.tick();
        if (packedRing != null && room.phase == Room.Phase.PLAYING) {
            ringAngle += ringDirection * .024;
            packedRing.setRotation((float) Math.toDegrees(-ringAngle), 0);
        }
        if (mahjongHud != null) mahjongHud.tick();
        for (PrivateView view : privateViews.values())
            for (PieceView piece : view.pieces.values()) piece.tick();
    }

    private Map<String, PieceView> publicFor(Player player) {
        return audience.packed(player) ? packPieces : publicPieces;
    }

    private void syncFurniture() {
        if (audience.needed(false) && furniture.isEmpty()) {
            buildingPacked = false;
            if (mahjong)
                furniture.add(
                        block(
                                origin.clone().add(0, .003, 0),
                                Material.GREEN_TERRACOTTA,
                                2.77,
                                .008,
                                2.77,
                                "@board",
                                null));
            else {
                Location deck = origin.clone().add(-.22, .015, 0);
                furniture.add(block(deck, Material.SMOOTH_QUARTZ, .18, .045, .27, "@board", null));
                furniture.add(
                        block(
                                deck.clone().add(0, .046, 0),
                                Material.BLACK_CONCRETE,
                                .16,
                                .003,
                                .25,
                                "@board",
                                null));
                turnRing = new TurnRing(plugin, room, origin, tag, audience);
            }
        } else if (!audience.needed(false) && !furniture.isEmpty()) {
            furniture.forEach(audience::remove);
            furniture.clear();
            if (turnRing != null) {
                turnRing.close();
                turnRing = null;
            }
        }
        if (!mahjong && audience.needed(true) && packFurniture.isEmpty()) {
            packFurniture.add(
                    PackedDisplay.spawn(
                            plugin,
                            room,
                            audience,
                            origin.clone().add(-.22, .04, 0),
                            tag,
                            "card_back",
                            new Vector3f(.18f, .27f, .045f),
                            new Quaternionf().rotateX((float) -Math.PI / 2)));
            packedRing =
                    PackedDisplay.spawn(
                            plugin,
                            room,
                            audience,
                            origin.clone().add(0, .019, 0),
                            tag,
                            ringDirection < 0 ? "ring_reverse" : "ring_forward",
                            new Vector3f(.87f, .004f, .87f),
                            new Quaternionf());
            packFurniture.add(packedRing);
        } else if (!audience.needed(true) && !packFurniture.isEmpty()) {
            packFurniture.forEach(audience::remove);
            packFurniture.clear();
            packedRing = null;
        }
        if (!mahjong && deckLabel == null) {
            deckLabel =
                    text(
                            origin.clone().add(-.22, .058, -.14),
                            Component.empty(),
                            .16f,
                            0,
                            true,
                            "@board",
                            null);
            audience.common(deckLabel);
        }
    }

    boolean deckHit(Location eye, Vector direction) {
        if (mahjong
                || closed
                || !origin.getWorld().equals(eye.getWorld())
                || direction.lengthSquared() < 1e-12) return false;
        Vector ray = direction.clone().normalize();
        BoundingBox box =
                new BoundingBox(
                        origin.getX() - .32,
                        origin.getY(),
                        origin.getZ() - .145,
                        origin.getX() - .12,
                        origin.getY() + .09,
                        origin.getZ() + .145);
        var hit = box.rayTrace(eye.toVector(), ray, TableGeometry.REACH);
        if (hit == null) return false;
        double distance = hit.getHitPosition().distance(eye.toVector());
        return origin.getWorld()
                        .rayTraceBlocks(
                                eye,
                                ray,
                                Math.max(.001, distance - .015),
                                FluidCollisionMode.NEVER,
                                true)
                == null;
    }

    private void hideOwnBacks(Player player, int seat) {
        publicFor(player).values().stream()
                .filter(piece -> piece.spec.id().startsWith("back:") && piece.spec.seat() == seat)
                .forEach(piece -> piece.parts.forEach(entity -> player.hideEntity(plugin, entity)));
    }

    private void restoreBacks(Player player, int seat) {
        publicFor(player).values().stream()
                .filter(piece -> piece.spec.id().startsWith("back:") && piece.spec.seat() == seat)
                .forEach(piece -> piece.parts.forEach(entity -> player.showEntity(plugin, entity)));
    }

    void clear(Player player) {
        PrivateView view = privateViews.remove(player.getUniqueId());
        if (view != null) {
            clearMatches(view);
            view.remove();
            restoreBacks(player, view.seat);
        }
    }

    private void clearMatches(PrivateView view) {
        for (var entry : view.matches.entrySet()) {
            PieceView piece = publicFor(view.player).get(entry.getKey());
            if (piece != null) view.player.showEntity(plugin, piece.parts.getFirst());
            entry.getValue().remove();
        }
        view.matches.clear();
    }

    String hit(Player player, Location eye, Vector direction) {
        if (!eligible(player)) {
            clear(player);
            return null;
        }
        if (!origin.getWorld().equals(eye.getWorld()) || direction.lengthSquared() < 1e-12)
            return null;
        show(player);
        PrivateView view = privateViews.get(player.getUniqueId());
        if (view == null) return null;
        Vector ray = direction.clone().normalize();
        String selected = null;
        double nearest = TableGeometry.REACH;
        for (PieceView piece : view.pieces.values()) {
            if (!mahjong) {
                double distance =
                        piece.cardHitDistance(
                                eye, ray, nearest, piece.spec.id().equals(view.hover));
                if (distance <= nearest) {
                    nearest = distance;
                    selected = piece.spec.id();
                }
                continue;
            }
            var hit =
                    piece.bounds(piece.spec.id().equals(view.hover))
                            .rayTrace(eye.toVector(), ray, nearest);
            if (hit != null) {
                nearest = hit.getHitPosition().distance(eye.toVector());
                selected = piece.spec.id();
            }
        }
        if (mahjong)
            for (PieceView piece : publicFor(player).values()) {
                if (piece.spec.back()) continue;
                var hit = piece.bounds(false).rayTrace(eye.toVector(), ray, nearest);
                if (hit != null) {
                    nearest = hit.getHitPosition().distance(eye.toVector());
                    selected = "public:" + piece.spec.id();
                }
            }
        if (mahjong)
            for (PieceView piece : view.indicators.values()) {
                var hit = piece.bounds(false).rayTrace(eye.toVector(), ray, nearest);
                if (hit != null) {
                    nearest = hit.getHitPosition().distance(eye.toVector());
                    selected = "public:" + piece.spec.id();
                }
            }
        if (selected == null
                || origin.getWorld()
                                .rayTraceBlocks(
                                        eye,
                                        ray,
                                        Math.max(.001, nearest - .015),
                                        FluidCollisionMode.NEVER,
                                        true)
                        != null) return null;
        return selected;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (turnRing != null) turnRing.close();
        if (mahjongHud != null) mahjongHud.close();
        if (cardTurn != null) cardTurn.close();
        privateViews
                .values()
                .forEach(
                        view -> {
                            clearMatches(view);
                            view.remove();
                        });
        privateViews.clear();
        assistance.clear();
        publicPieces.values().forEach(PieceView::remove);
        publicPieces.clear();
        furniture.forEach(Entity::remove);
        furniture.clear();
        packPieces.values().forEach(PieceView::remove);
        packPieces.clear();
        packFurniture.forEach(audience::remove);
        packFurniture.clear();
        if (deckLabel != null) audience.remove(deckLabel);
    }
}
