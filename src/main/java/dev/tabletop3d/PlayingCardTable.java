package dev.tabletop3d;

import dev.tabletop3d.rules.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Standard cards share rendering and selection; each pure engine owns its legal controls. */
final class PlayingCardTable implements AutoCloseable {
    private static final double WIDTH = .168, HEIGHT = .25;
    private static final double CARD_LAYER = .012, CARD_LIFT = .085;
    private final Tabletop3D plugin;
    private final Room room;
    private final Location origin;
    private final NamespacedKey tag;
    final TableAudience audience;
    private final Map<String, CardVisual> publicCards = new LinkedHashMap<>();
    private final List<Entity> publicEntities = new ArrayList<>();
    private final Map<UUID, PrivateView> privateViews = new HashMap<>();
    private final List<Entity> nativeFurniture = new ArrayList<>();
    private final List<Entity> pokerMarkers = new ArrayList<>();
    private final List<Entity> dealerMarkers = new ArrayList<>();
    private int markerLayers = -1;
    private ItemDisplay packedFurniture;
    private final List<TextDisplay> seatLabels = new ArrayList<>();
    private final TextDisplay status;
    private final CardTurnIndicator turn;
    private long revision = -1, language = -1, audienceGeneration = -1;
    private BoardGame rendered;

    private record Pose(double x, double z, float yaw, double y) {}

    private record CardSpec(String face, Pose pose, boolean standing, int owner) {}

    private final class CardVisual {
        final List<Display> parts = new ArrayList<>();
        CardSpec spec;

        CardVisual(CardSpec spec, Player viewer, boolean packed) {
            this.spec = spec;
            Location at = at(spec.pose);
            if (packed) {
                var item =
                        origin.getWorld()
                                .spawn(
                                        at,
                                        ItemDisplay.class,
                                        d -> {
                                            configure(d, viewer, true);
                                            d.setItemDisplayTransform(
                                                    ItemDisplay.ItemDisplayTransform.FIXED);
                                            d.setItemStack(
                                                    plugin.pack.item("playing_" + spec.face));
                                            d.setTransformation(
                                                    new Transformation(
                                                            new Vector3f(
                                                                    0,
                                                                    spec.standing
                                                                            ? (float) HEIGHT / 2
                                                                            : .006f,
                                                                    0),
                                                            spec.standing
                                                                    ? new Quaternionf()
                                                                    : new Quaternionf()
                                                                            .rotateX(
                                                                                    (float) -Math.PI
                                                                                            / 2),
                                                            new Vector3f(
                                                                    (float) WIDTH,
                                                                    (float) HEIGHT,
                                                                    .008f),
                                                            new Quaternionf()));
                                        });
                parts.add(item);
                show(item, viewer);
            } else if (spec.face.equals("back")) {
                double cardHeight = WIDTH * 4 / 3;
                parts.add(block(at, Material.WHITE_CONCRETE, WIDTH,
                        spec.standing ? cardHeight : .008,
                        spec.standing ? .008 : cardHeight, viewer));
                double yaw = Math.toRadians(at.getYaw());
                Location inset = spec.standing
                        ? at.clone().add(Math.sin(yaw) * .0055, .006, -Math.cos(yaw) * .0055)
                        : at.clone().add(0, .0085, 0);
                parts.add(block(inset, Material.BLUE_CONCRETE, WIDTH - .012,
                        spec.standing ? cardHeight - .012 : .002,
                        spec.standing ? .002 : cardHeight - .012, viewer));
            } else {
                var textures = PlayingCardHeads.tiles(spec.face);
                for (int tile = 0; tile < textures.size(); tile++) {
                    final int index = tile;
                    var head = origin.getWorld().spawn(at, ItemDisplay.class, d -> {
                        configure(d, viewer, false);
                        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                        d.setItemStack(PlayingCardHeads.item(textures.get(index)));
                        d.setTransformation(PlayingCardHeads.pose(WIDTH, spec.standing, index));
                    });
                    parts.add(head);
                    show(head, viewer);
                }
            }
        }

        void pose(Pose pose) {
            if (spec.pose.equals(pose)) return;
            Vector change = at(pose).toVector().subtract(at(spec.pose).toVector());
            for (Display entity : parts) entity.teleport(entity.getLocation().add(change));
            spec = new CardSpec(spec.face, pose, spec.standing, spec.owner);
        }

        void remove() {
            parts.forEach(audience::remove);
            publicEntities.removeAll(parts);
        }
    }

    private final class PrivateView {
        final Player player;
        final int seat;
        final LinkedHashSet<String> selection = new LinkedHashSet<>();
        final Map<String, CardVisual> cards = new LinkedHashMap<>();
        final Map<String, List<Display>> buttons = new LinkedHashMap<>();
        final Map<String, Pose> buttonPoses = new LinkedHashMap<>();
        long revision = -1, language = -1;
        BoardGame board;
        Room.Phase phase;
        boolean packed;
        String hover;

        PrivateView(Player player, int seat) {
            this.player = player;
            this.seat = seat;
        }

        void clear() {
            cards.values().forEach(CardVisual::remove);
            cards.clear();
            buttons.values().forEach(parts -> parts.forEach(audience::remove));
            buttons.clear();
            buttonPoses.clear();
            selection.clear();
        }
    }

    PlayingCardTable(Tabletop3D plugin, Room room, Location origin, NamespacedKey tag) {
        this.plugin = plugin;
        this.room = room;
        this.origin = origin.clone();
        this.tag = tag;
        audience = new TableAudience(plugin, origin);
        status = text(origin.clone().add(0, .52, 0), Component.empty(), .21f, null, false);
        audience.common(status);
        publicEntities.add(status);
        status.setBillboard(Display.Billboard.CENTER);
        for (int seat = 0; seat < room.capacity; seat++) {
            var label = text(at(seatPose(seat, 0, .78, .38)), Component.empty(), .17f, null, false);
            audience.common(label);
            publicEntities.add(label);
            seatLabels.add(label);
        }
        turn = new CardTurnIndicator(room, origin, audience, tag);
        sync();
    }

    private SelectedHandGame game() {
        return (SelectedHandGame) room.board;
    }

    private Pose seatPose(int seat, double tangent, double radius, double height) {
        double angle = 2 * Math.PI * seat / room.capacity;
        return new Pose(
                tangent * Math.cos(angle) + radius * Math.sin(angle),
                -tangent * Math.sin(angle) + radius * Math.cos(angle),
                (float) -Math.toDegrees(angle),
                height);
    }

    private Pose handPose(int seat, int index, int count, double lift) {
        double unit = Math.min(.15, (room.capacity >= 5 ? 1.06 : 1.50) / Math.max(1, count - 1));
        return seatPose(
                seat,
                (index - (count - 1) / 2.0) * unit,
                1.02 + (index - (count - 1) / 2.0) * CARD_LAYER,
                .017 + lift);
    }

    private Location at(Pose pose) {
        Location at = origin.clone().add(pose.x, pose.y, pose.z);
        at.setYaw(pose.yaw);
        return at;
    }

    private void configure(Display display, Player viewer, boolean packed) {
        if (viewer != null) display.setVisibleByDefault(false);
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(.35f);
        display.setTeleportDuration(2);
        display.getPersistentDataContainer()
                .set(tag, PersistentDataType.STRING, room.id + "|@board");
        if (viewer == null) audience.add(display, packed);
    }

    private void show(Entity entity, Player viewer) {
        if (viewer != null) viewer.showEntity(plugin, entity);
    }

    private BlockDisplay block(
            Location at,
            Material material,
            double width,
            double height,
            double depth,
            Player viewer) {
        var body =
                origin.getWorld()
                        .spawn(
                                at,
                                BlockDisplay.class,
                                d -> {
                                    configure(d, viewer, false);
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
        show(body, viewer);
        return body;
    }

    private TextDisplay text(
            Location at, Component content, float scale, Player viewer, boolean flat) {
        if (flat) at.setPitch(-90);
        var label =
                origin.getWorld()
                        .spawn(
                                at,
                                TextDisplay.class,
                                d -> {
                                    configure(d, viewer, false);
                                    d.setBackgroundColor(Color.fromARGB(0));
                                    d.setShadowed(false);
                                    d.text(content);
                                    d.setLineWidth(500);
                                    d.setTransformation(
                                            new Transformation(
                                                    new Vector3f(),
                                                    new Quaternionf(),
                                                    new Vector3f(scale),
                                                    new Quaternionf()));
                                });
        show(label, viewer);
        return label;
    }

    void sync() {
        audience.refresh();
        syncFurniture();
        syncPokerMarkers();
        boolean changed =
                revision != room.revision
                        || rendered != room.board
                        || language != Language.generation()
                        || audienceGeneration != audience.generation;
        if (changed) {
            var desired = new HashSet<String>();
            for (boolean packed : new boolean[] {false, true})
                if (audience.needed(packed)) {
                    for (int seat = 0; seat < room.capacity; seat++) {
                        int count = game().handSize(seat);
                        boolean revealed =
                                !game().exposed(seat).isEmpty() && room.kind.equals("texas-holdem");
                        if (!revealed)
                            for (int index = 0; index < count; index++)
                                publicCard(
                                        desired,
                                        packed,
                                        "back:" + seat + ":" + index,
                                        "back",
                                        handPose(seat, index, count, 0),
                                        true,
                                        seat);
                        var cards = game().discards(seat);
                        for (int index = 0; index < cards.size(); index++)
                            publicCard(
                                    desired,
                                    packed,
                                    "play:" + seat + ":" + index,
                                    cards.get(index).face(),
                                    seatPose(
                                            seat,
                                            (index - (cards.size() - 1) / 2.0) * .10,
                                            .52,
                                            .022 + index * CARD_LAYER),
                                    false,
                                    -1);
                        if (room.kind.equals("doudizhu")) continue;
                        var exposed = game().exposed(seat);
                        for (int index = 0; index < exposed.size(); index++)
                            publicCard(
                                    desired,
                                    packed,
                                    "exposed:" + seat + ":" + index,
                                    exposed.get(index).face(),
                                    room.kind.equals("texas-holdem")
                                            ? handPose(
                                                    seat,
                                                    index,
                                                    exposed.size(),
                                                    .015 + index * CARD_LAYER)
                                            : seatPose(
                                                    seat,
                                                    (index - (exposed.size() - 1) / 2.0) * .18,
                                                    .55,
                                                    .032 + index * CARD_LAYER),
                                    false,
                                    -1);
                    }
                    if (room.kind.equals("doudizhu")) {
                        int landlord = Integer.parseInt(game().publicInfo().get("landlord"));
                        var cards =
                                landlord < 0 ? List.<HandGame.Piece>of() : game().exposed(landlord);
                        for (int index = 0; index < cards.size(); index++)
                            publicCard(
                                    desired,
                                    packed,
                                    "bottom:" + index,
                                    cards.get(index).face(),
                                    new Pose((index - 1) * .27, 0, 0, .022),
                                    false,
                                    -1);
                    }
                    for (Cell card : game().cells())
                        publicCard(
                                desired,
                                packed,
                                card.id(),
                                card.piece(),
                                new Pose(
                                        room.kind.equals("texas-holdem") ? (card.x() - 2) * .30 : 0,
                                        0,
                                        0,
                                        .023),
                                false,
                                -1);
                }
            publicCards
                    .entrySet()
                    .removeIf(
                            entry -> {
                                if (desired.contains(entry.getKey())) return false;
                                entry.getValue().remove();
                                return true;
                            });
            status.text(PlayingCardText.status(room));
            for (int seat = 0; seat < room.seats.size(); seat++)
                seatLabels.get(seat).text(PlayingCardText.seat(room, seat));
            revision = room.revision;
            rendered = room.board;
            language = Language.generation();
            audienceGeneration = audience.generation;
        }
        turn.sync();
        for (Player player : List.copyOf(origin.getWorld().getPlayers())) show(player);
        for (var entry : List.copyOf(privateViews.entrySet()))
            if (!eligible(entry.getValue().player)) clear(entry.getValue().player);
    }

    private void publicCard(
            Set<String> desired,
            boolean packed,
            String id,
            String face,
            Pose pose,
            boolean standing,
            int owner) {
        String key = (packed ? "packed:" : "native:") + id;
        desired.add(key);
        CardSpec spec = new CardSpec(face, pose, standing, owner);
        CardVisual old = publicCards.get(key);
        if (old != null && old.spec.face.equals(face) && old.spec.standing == standing) {
            old.pose(pose);
            return;
        }
        if (old != null) old.remove();
        var created = new CardVisual(spec, null, packed);
        publicCards.put(key, created);
        publicEntities.addAll(created.parts);
    }

    private void syncFurniture() {
        if (audience.needed(false) && nativeFurniture.isEmpty())
            for (var part : RoundCardTable.parts()) {
                Location at = origin.clone().add(part.x(), part.y(), part.z());
                at.setYaw(part.yaw());
                nativeFurniture.add(
                        block(
                                at,
                                room.kind.equals("liars-bar")
                                                && part.material() == Material.GREEN_TERRACOTTA
                                        ? Material.BROWN_TERRACOTTA
                                        : part.material(),
                                part.w(),
                                part.h(),
                                part.d(),
                                null));
            }
        if (!audience.needed(false) && !nativeFurniture.isEmpty()) {
            nativeFurniture.forEach(audience::remove);
            nativeFurniture.clear();
        }
        if (audience.needed(true) && packedFurniture == null)
            packedFurniture =
                    origin.getWorld()
                            .spawn(
                                    origin,
                                    ItemDisplay.class,
                                    d -> {
                                        configure(d, null, true);
                                        d.setItemDisplayTransform(
                                                ItemDisplay.ItemDisplayTransform.FIXED);
                                        d.setItemStack(
                                                plugin.pack.item(
                                                        room.kind.replace('-', '_') + "_table"));
                                    });
        if (!audience.needed(true) && packedFurniture != null) {
            audience.remove(packedFurniture);
            packedFurniture = null;
        }
    }

    private void syncPokerMarkers() {
        if (!room.kind.equals("texas-holdem")) return;
        int layers = (audience.needed(false) ? 1 : 0) | (audience.needed(true) ? 2 : 0);
        if (markerLayers == layers) {
            moveDealerMarkers();
            return;
        }
        pokerMarkers.forEach(audience::remove);
        pokerMarkers.clear();
        dealerMarkers.forEach(audience::remove);
        dealerMarkers.clear();
        for (int seat = 0; seat < room.capacity; seat++) {
            Location position = at(seatPose(seat, -.28, .74, .02));
            if (audience.needed(false))
                pokerMarkers.add(block(position, Material.GOLD_BLOCK, .11, .08, .11, null));
            if (audience.needed(true))
                pokerMarkers.add(
                        origin.getWorld()
                                .spawn(
                                        position,
                                        ItemDisplay.class,
                                        d -> {
                                            configure(d, null, true);
                                            d.setItemDisplayTransform(
                                                    ItemDisplay.ItemDisplayTransform.FIXED);
                                            d.setItemStack(plugin.pack.item("poker_chips"));
                                        }));
        }
        Location dealer =
                at(seatPose(Integer.parseInt(game().publicInfo().get("dealer")), .28, .74, .022));
        if (audience.needed(false)) {
            dealerMarkers.add(block(dealer, Material.SMOOTH_QUARTZ, .12, .01, .12, null));
            dealerMarkers.add(
                    text(
                            dealer.clone().add(0, .016, 0),
                            Language.component("cards.poker.dealer-button")
                                    .color(NamedTextColor.DARK_GRAY),
                            .12f,
                            null,
                            true));
        }
        if (audience.needed(true))
            dealerMarkers.add(
                    origin.getWorld()
                            .spawn(
                                    dealer,
                                    ItemDisplay.class,
                                    d -> {
                                        configure(d, null, true);
                                        d.setItemDisplayTransform(
                                                ItemDisplay.ItemDisplayTransform.FIXED);
                                        d.setItemStack(plugin.pack.item("poker_dealer"));
                                    }));
        markerLayers = layers;
    }

    private void moveDealerMarkers() {
        Location dealer =
                at(seatPose(Integer.parseInt(game().publicInfo().get("dealer")), .28, .74, .022));
        for (Entity marker : dealerMarkers) {
            Location position = dealer.clone();
            if (marker instanceof TextDisplay) {
                position.add(0, .016, 0);
                position.setPitch(-90);
            }
            if (!marker.getLocation().equals(position)) marker.teleport(position);
        }
    }

    private boolean eligible(Player player) {
        return player.isOnline()
                && plugin.allowed(player)
                && player.getWorld().equals(origin.getWorld())
                && player.getLocation().distanceSquared(origin) < 24 * 24
                && room.seat(player.getUniqueId()) >= 0;
    }

    private void show(Player player) {
        if (!eligible(player)) return;
        int seat = room.seat(player.getUniqueId());
        PrivateView view = privateViews.get(player.getUniqueId());
        if (view != null && view.seat != seat) {
            clear(player);
            view = null;
        }
        if (view == null) {
            view = new PrivateView(player, seat);
            privateViews.put(player.getUniqueId(), view);
        }
        boolean packed = audience.packed(player);
        if (view.revision != room.revision
                || view.language != Language.generation()
                || view.packed != packed
                || view.board != room.board
                || view.phase != room.phase) {
            view.clear();
            view.packed = packed;
            view.hover = null;
            var hand = game().hand(seat);
            boolean revealed = room.kind.equals("texas-holdem") && !game().exposed(seat).isEmpty();
            if (!revealed)
                for (int index = 0; index < hand.size(); index++) {
                    var card = hand.get(index);
                    view.cards.put(
                            card.id(),
                            new CardVisual(
                                    new CardSpec(
                                            card.face(),
                                            handPose(seat, index, hand.size(), 0),
                                            true,
                                            seat),
                                    player,
                                    packed));
                }
            var controls =
                    room.phase == Room.Phase.PLAYING ? game().controls(seat) : List.<String>of();
            for (int index = 0; index < controls.size(); index++) {
                String control = controls.get(index);
                int columns = Math.min(4, controls.size());
                Pose pose =
                        seatPose(seat, (index % columns - (columns - 1) / 2.0) * .32, 1.27, .03);
                var body =
                        block(
                                at(pose),
                                control.equals("fold")
                                        ? Material.RED_TERRACOTTA
                                        : Material.GREEN_TERRACOTTA,
                                .29,
                                .012,
                                .17,
                                player);
                var label =
                        text(
                                at(pose).add(0, .016, 0),
                                PlayingCardText.control(control),
                                .13f,
                                player,
                                true);
                view.buttons.put(control, List.of(body, label));
                view.buttonPoses.put(control, pose);
            }
            view.revision = room.revision;
            view.language = Language.generation();
            view.board = room.board;
            view.phase = room.phase;
        }
        for (var visual : publicCards.values())
            if (visual.spec.owner == seat)
                visual.parts.forEach(entity -> player.hideEntity(plugin, entity));
    }

    String cardAction(Player player, String id) {
        show(player);
        var view = privateViews.get(player.getUniqueId());
        if (view == null
                || !view.cards.containsKey(id)
                || !game().controls(view.seat).contains("play")) return null;
        if (!view.selection.remove(id)) view.selection.add(id);
        lift(view);
        return null;
    }

    String action(Player player, String control) {
        show(player);
        var view = privateViews.get(player.getUniqueId());
        if (view == null || !view.buttons.containsKey(control) || view.revision != room.revision)
            return null;
        if (control.equals("clear")) {
            view.selection.clear();
            lift(view);
            return null;
        }
        if (control.equals("raise")) {
            plugin.menus.pokerRaise(player, room, view.revision);
            return null;
        }
        if (control.equals("play")) {
            try {
                return game().selectionAction(view.seat, List.copyOf(view.selection));
            } catch (RuleViolation error) {
                player.sendActionBar(Language.error(error).colorIfAbsent(NamedTextColor.RED));
                return null;
            }
        }
        return control;
    }

    void hover(Player player, String id) {
        show(player);
        var view = privateViews.get(player.getUniqueId());
        if (view == null) return;
        if (Objects.equals(view.hover, id)) return;
        view.hover = id;
        lift(view);
    }

    private void lift(PrivateView view) {
        int index = 0;
        for (var entry : view.cards.entrySet()) {
            double lift =
                    view.selection.contains(entry.getKey()) || entry.getKey().equals(view.hover)
                            ? CARD_LIFT
                            : 0;
            entry.getValue().pose(handPose(view.seat, index++, view.cards.size(), lift));
        }
    }

    String handHit(Player player, Location eye, Vector direction) {
        return hit(player, eye, direction, false);
    }

    String callHit(Player player, Location eye, Vector direction) {
        String hit = hit(player, eye, direction, true);
        return hit == null ? null : "playing:" + hit;
    }

    private String hit(Player player, Location eye, Vector direction, boolean controls) {
        if (!eligible(player)) {
            clear(player);
            return null;
        }
        show(player);
        var view = privateViews.get(player.getUniqueId());
        if (view == null || !eye.getWorld().equals(origin.getWorld())) return null;
        double nearest = TableGeometry.REACH;
        String best = null;
        if (controls)
            for (var entry : view.buttonPoses.entrySet()) {
                double distance = ray(eye, direction, entry.getValue(), .29, .17, false);
                if (distance > 0 && distance < nearest) {
                    nearest = distance;
                    best = entry.getKey();
                }
            }
        else {
            int index = 0;
            for (var entry : view.cards.entrySet()) {
                // A visual lift must never move the target and undo its own hover.
                double distance =
                        ray(
                                eye,
                                direction,
                                handPose(view.seat, index++, view.cards.size(), 0),
                                WIDTH,
                                HEIGHT,
                                true);
                if (distance > 0 && distance < nearest) {
                    nearest = distance;
                    best = entry.getKey();
                }
            }
        }
        if (best != null
                && origin.getWorld()
                                .rayTraceBlocks(
                                        eye,
                                        direction,
                                        Math.max(.001, nearest - .015),
                                        FluidCollisionMode.NEVER,
                                        true)
                        != null) return null;
        return best;
    }

    private double ray(
            Location eye, Vector ray, Pose pose, double width, double height, boolean standing) {
        double angle = -Math.toRadians(pose.yaw), c = Math.cos(angle), s = Math.sin(angle);
        Vector offset = eye.toVector().subtract(at(pose).toVector());
        double x = offset.getX() * c - offset.getZ() * s, z = offset.getX() * s + offset.getZ() * c;
        double dx = ray.getX() * c - ray.getZ() * s, dz = ray.getX() * s + ray.getZ() * c;
        double denominator = standing ? dz : ray.getY();
        if (Math.abs(denominator) < 1e-8) return -1;
        double distance = -(standing ? z : offset.getY()) / denominator;
        if (distance <= 0) return -1;
        double across = x + dx * distance,
                along = standing ? offset.getY() + ray.getY() * distance : z + dz * distance;
        return Math.abs(across) <= width / 2
                        && (standing
                                ? along >= 0 && along <= height
                                : Math.abs(along) <= height / 2)
                ? distance
                : -1;
    }

    void clear(Player player) {
        var view = privateViews.remove(player.getUniqueId());
        if (view == null) return;
        view.clear();
        boolean packed = audience.packed(player);
        for (var entry : publicCards.entrySet())
            if (entry.getValue().spec.owner == view.seat
                    && entry.getKey().startsWith(packed ? "packed:" : "native:"))
                entry.getValue().parts.forEach(entity -> player.showEntity(plugin, entity));
    }

    public void close() {
        privateViews.values().forEach(PrivateView::clear);
        privateViews.clear();
        publicCards.values().forEach(CardVisual::remove);
        publicCards.clear();
        publicEntities.forEach(audience::remove);
        publicEntities.clear();
        nativeFurniture.forEach(audience::remove);
        nativeFurniture.clear();
        pokerMarkers.forEach(audience::remove);
        pokerMarkers.clear();
        dealerMarkers.forEach(audience::remove);
        dealerMarkers.clear();
        if (packedFurniture != null) audience.remove(packedFurniture);
        packedFurniture = null;
        turn.close();
    }
}
