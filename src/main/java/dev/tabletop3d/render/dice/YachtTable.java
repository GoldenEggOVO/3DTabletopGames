package dev.tabletop3d.render.dice;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.audio.TableSounds;
import dev.tabletop3d.render.TableAudience;
import dev.tabletop3d.render.TableGeometry;
import dev.tabletop3d.render.TableView;
import dev.tabletop3d.room.Room;
import dev.tabletop3d.rules.yacht.YachtGame;
import dev.tabletop3d.text.Language;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Five physical dice, fixed keep slots and direct score controls. Rules stay in YachtGame. */
public final class YachtTable implements AutoCloseable {
    private static final double SIZE = .18;
    private final Tabletop3D plugin;
    private final Room room;
    private final Location origin;
    private final NamespacedKey tag;
    public final TableAudience audience;
    private final List<Entity> common = new ArrayList<>(),
            nativeTable = new ArrayList<>(),
            packedTable = new ArrayList<>();
    private final List<YachtDie> nativeDice = new ArrayList<>(), packedDice = new ArrayList<>();
    private final DiceMotion[] throwsByDie = new DiceMotion[5];
    private final DiceMotion.Pose[] poses = new DiceMotion.Pose[5];
    private final TextDisplay[] categories = new TextDisplay[12];
    private final TextDisplay rollLabel, headerLabel;
    private final TextDisplay[][] scores;
    private final TextDisplay[] headers;
    private List<Room.Seat> roster = List.of();
    private final BlockDisplay activeColumn;
    private final TextDisplay[] summaryLabels = new TextDisplay[3];
    static final double SCORE_X = -1.65;
    private static final double SCORE_TOP = -.85, SCORE_HEIGHT = 1.50;
    private static final double COLUMNS_LEFT = -1.625, COLUMNS_WIDTH = .70;

    static double scoreZ(int category) {
        return -.80 + (category < 6 ? category : category + 2) * .10;
    }

    private double columnX(int seat) {
        return COLUMNS_LEFT + (seat + .5) * COLUMNS_WIDTH / room.capacity;
    }

    private YachtGame board;
    private long revision = -1, language = -1;
    private int history, frame;
    private boolean closed;

    private record Focus(
            String target,
            double x,
            double y,
            double z,
            double width,
            double depth,
            List<BlockDisplay> edges) {}

    private final Map<UUID, Focus> focuses = new HashMap<>();

    public YachtTable(Tabletop3D plugin, Room room, Location origin, NamespacedKey tag) {
        this.plugin = plugin;
        this.room = room;
        this.origin = origin;
        this.tag = tag;
        audience = new TableAudience(plugin, origin);
        for (int i = 0; i < 5; i++) {
            poses[i] = rest(i, false, 1);
        }
        for (int i = 0; i < categories.length; i++) {
            categories[i] = label(-1.99, .034, scoreZ(i), .135f);
            categories[i].text(
                    (i < 6
                                    ? Component.text(String.valueOf((char) (0x2680 + i)))
                                            .append(Component.space())
                                    : Component.empty())
                            .append(
                                    Language.component(
                                            "score.category." + YachtGame.CATEGORIES.get(i)))
                            .color(NamedTextColor.BLACK));
        }
        scores = new TextDisplay[room.capacity][15];
        headers = new TextDisplay[room.capacity];
        for (int seat = 0; seat < room.capacity; seat++) {
            headers[seat] = label(columnX(seat), .034, -.94, .09f);
            for (int row = 0; row < 15; row++) {
                scores[seat][row] = label(columnX(seat), .034, -.80 + row * .10, .14f);
            }
        }
        summaryLabels[0] = label(-1.99, .034, -.20, .12f);
        summaryLabels[1] = label(-1.99, .034, -.10, .12f);
        summaryLabels[2] = label(-1.99, .034, .60, .13f);
        for (int seat = 1; seat < room.capacity; seat++) {
            common.add(
                    block(
                            COLUMNS_LEFT + seat * COLUMNS_WIDTH / room.capacity,
                            .017,
                            -.10,
                            .006,
                            .002,
                            SCORE_HEIGHT,
                            Material.BLACK_CONCRETE));
        }
        activeColumn =
                block(
                        columnX(0),
                        .007,
                        -.10,
                        COLUMNS_WIDTH / room.capacity - .008,
                        .004,
                        SCORE_HEIGHT - .008,
                        Material.YELLOW_CONCRETE);
        common.add(activeColumn);
        common.add(block(.45, .007, .82, .95, .016, .23, Material.GREEN_CONCRETE));
        rollLabel = label(.45, .038, .82, .16f);
        headerLabel = label(SCORE_X, .038, -1.025, .11f);
        for (Entity entity : common) {
            audience.common(entity);
        }
        sync();
    }

    public void sync() {
        if (closed) {
            return;
        }
        layers();
        if (!roster.equals(room.seats)) {
            roster = List.copyOf(room.seats);
            for (int seat = 0; seat < headers.length; seat++) {
                String name = seat < roster.size() ? roster.get(seat).name() : "";
                String shortName =
                        name.substring(
                                0,
                                name.offsetByCodePoints(
                                        0, Math.min(5, name.codePointCount(0, name.length()))));
                headers[seat].text(
                        Component.text((seat + 1) + "\n" + shortName).color(NamedTextColor.BLACK));
            }
        }
        if (board == room.board && revision == room.revision) {
            if (language != Language.generation()) {
                language = Language.generation();
                refreshLabels();
            }
            return;
        }
        YachtGame next = (YachtGame) room.board;
        String action =
                room.history.isEmpty()
                        ? ""
                        : room.history
                                .get(room.history.size() - 1)
                                .getAsJsonObject()
                                .get("action")
                                .getAsString();
        boolean roll = board == next && room.history.size() == history + 1 && action.equals("roll");
        board = next;
        revision = room.revision;
        history = room.history.size();
        language = Language.generation();
        int[] values = board.dice();
        for (int i = 0; i < 5; i++) {
            int face = Math.max(1, values[i]);
            if (roll && !board.held(i)) {
                DiceMotion.Pose local =
                        new DiceMotion.Pose(0, poses[i].y(), 0, poses[i].rotation());
                throwsByDie[i] =
                        new DiceMotion(SIZE, .24, local, face, room.seed ^ (history * 31L + i));
            } else {
                throwsByDie[i] = null;
                DiceMotion.Pose resting = rest(i, board.held(i), face);
                if (!resting.equals(poses[i])) {
                    poses[i] = resting;
                    render(i);
                }
            }
        }
        frame = 0;
        if (rolling()) {
            clearFocuses();
        }
        refreshLabels();
    }

    public void tick() {
        sync();
        if (!rolling()) {
            return;
        }
        frame++;
        for (int i = 0; i < 5; i++) {
            if (throwsByDie[i] != null) {
                DiceMotion.Pose p = throwsByDie[i].pose(frame);
                // Each throw stays in its own lane; the last frame snaps to the ordered row.
                poses[i] =
                        frame >= DiceMotion.FRAMES
                                ? rest(i, false, board.dice()[i])
                                : new DiceMotion.Pose(
                                        x(i) + p.x() * .25, p.y(), .15 + p.z(), p.rotation());
                render(i);
                if (frame >= DiceMotion.FRAMES) {
                    throwsByDie[i] = null;
                }
            }
        }
        if (frame == 8 || frame == 13 || frame == 17) {
            TableSounds.play(
                    plugin,
                    origin,
                    new TableSounds.Cue(TableSounds.WOOD.sound(), .22f, 1.1f + frame * .01f));
        }
        if (!rolling()) {
            refreshLabels();
        }
    }

    public boolean rolling() {
        for (DiceMotion motion : throwsByDie) {
            if (motion != null) {
                return true;
            }
        }
        return false;
    }

    private static double x(int index) {
        return .32 + (index - 2) * .26;
    }

    private static DiceMotion.Pose rest(int index, boolean held, int face) {
        DiceMotion.Pose rest = DiceMotion.rest(SIZE, face);
        return new DiceMotion.Pose(
                x(index), rest.y() + (held ? .02 : 0), held ? -.68 : .15, rest.rotation());
    }

    private void render(int index) {
        if (!nativeDice.isEmpty()) {
            nativeDice.get(index).pose(poses[index]);
        }
        if (!packedDice.isEmpty()) {
            packedDice.get(index).pose(poses[index]);
        }
    }

    private void layers() {
        audience.refresh();
        if (audience.needed(false) && nativeTable.isEmpty()) {
            for (double center : new double[] {.32, SCORE_X}) {
                double width = center == SCORE_X ? 1.60 : 2.00;
                nativeTable.add(block(center, -.19, 0, width, .14, 2.25, Material.DARK_OAK_PLANKS));
                nativeTable.add(
                        block(
                                center,
                                -.045,
                                0,
                                width - .15,
                                .045,
                                2.10,
                                center == SCORE_X
                                        ? Material.WHITE_CONCRETE
                                        : Material.RED_CONCRETE));
                if (center != SCORE_X) {
                    for (double side : new double[] {-1, 1}) {
                        nativeTable.add(
                                block(
                                        center + side * (width / 2 - .04),
                                        -.045,
                                        0,
                                        .08,
                                        .10,
                                        2.25,
                                        Material.STRIPPED_DARK_OAK_WOOD));
                        nativeTable.add(
                                block(
                                        center,
                                        -.045,
                                        side * 1.085,
                                        width - .16,
                                        .10,
                                        .08,
                                        Material.STRIPPED_DARK_OAK_WOOD));
                    }
                }
                for (double x : new double[] {center - width / 2 + .15, center + width / 2 - .15}) {
                    for (double z : new double[] {-.98, .98}) {
                        nativeTable.add(
                                block(
                                        x,
                                        -TableGeometry.SURFACE,
                                        z,
                                        .13,
                                        TableGeometry.SURFACE - .19,
                                        .13,
                                        Material.STRIPPED_DARK_OAK_LOG));
                    }
                }
            }
            for (int row = 0; row <= 15; row++) {
                nativeTable.add(
                        block(
                                SCORE_X,
                                .013,
                                -.85 + row * .10,
                                1.45,
                                .003,
                                .006,
                                Material.BLACK_CONCRETE));
            }
            nativeTable.add(
                    block(
                            COLUMNS_LEFT,
                            .013,
                            -.1,
                            .008,
                            .003,
                            SCORE_HEIGHT,
                            Material.BLACK_CONCRETE));
            for (double z : new double[] {-.20, -.10, .60}) {
                nativeTable.add(block(-1.99, .004, z, .71, .009, .094, Material.GRAY_CONCRETE));
            }
            for (int i = 0; i < 5; i++) {
                nativeTable.add(
                        block(x(i), .002, -.68, .235, .012, .235, Material.LIGHT_GRAY_CONCRETE));
                nativeTable.add(block(x(i), .015, -.68, .21, .004, .21, Material.GRAY_CONCRETE));
            }
            nativeTable.forEach(entity -> audience.add(entity, false));
        } else if (!audience.needed(false) && !nativeTable.isEmpty()) {
            nativeTable.forEach(audience::remove);
            nativeTable.clear();
        }
        if (audience.needed(true) && packedTable.isEmpty()) {
            ItemDisplay item =
                    origin.getWorld()
                            .spawn(
                                    origin,
                                    ItemDisplay.class,
                                    d -> {
                                        init(d, "@menu");
                                        d.setItemDisplayTransform(
                                                ItemDisplay.ItemDisplayTransform.FIXED);
                                        d.setItemStack(plugin.pack.item("yacht_table"));
                                        d.setTransformation(
                                                new Transformation(
                                                        new Vector3f(),
                                                        new Quaternionf(),
                                                        new Vector3f(2),
                                                        new Quaternionf()));
                                    });
            packedTable.add(item);
            audience.add(item, true);
        } else if (!audience.needed(true) && !packedTable.isEmpty()) {
            packedTable.forEach(audience::remove);
            packedTable.clear();
        }
        diceLayer(nativeDice, false);
        diceLayer(packedDice, true);
    }

    private void diceLayer(List<YachtDie> dice, boolean packed) {
        if (audience.needed(packed) && dice.isEmpty()) {
            for (int i = 0; i < 5; i++) {
                var die =
                        new YachtDie(plugin, room, origin, tag, audience, packed, SIZE, "die" + i);
                dice.add(die);
                die.pose(poses[i]);
            }
        } else if (!audience.needed(packed) && !dice.isEmpty()) {
            dice.forEach(YachtDie::close);
            dice.clear();
        }
    }

    private void refreshLabels() {
        int seat = board.currentPlayer();
        for (int row = 0; row < summaryLabels.length; row++) {
            summaryLabels[row].text(
                    Language.component(
                                    "table.yacht."
                                            + List.of("subtotal", "bonus", "total-label").get(row))
                            .color(NamedTextColor.WHITE));
        }
        int completed = 0;
        for (int i = 0; i < 12; i++) {
            if (board.written(seat, i) >= 0) {
                completed++;
            }
        }
        headerLabel.text(
                Language.component("table.yacht.round", "round", Math.min(12, completed + 1))
                        .color(NamedTextColor.BLACK));
        activeColumn.setTransformation(
                new Transformation(
                        new Vector3f(
                                (float)
                                        (columnX(seat)
                                                - (COLUMNS_WIDTH / room.capacity - .008) / 2),
                                .007f,
                                (float) (SCORE_TOP + .004)),
                        new Quaternionf(),
                        new Vector3f(
                                (float) (COLUMNS_WIDTH / room.capacity - .008),
                                .004f,
                                (float) (SCORE_HEIGHT - .008)),
                        new Quaternionf()));
        for (int player = 0; player < board.playerCount(); player++) {
            int upper = 0;
            for (int category = 0; category < 12; category++) {
                int written = board.written(player, category);
                if (category < 6) {
                    upper += Math.max(0, written);
                }
                String value =
                        written >= 0
                                ? Integer.toString(written)
                                : player == seat && board.rolls() > 0
                                        ? Integer.toString(YachtGame.score(category, board.dice()))
                                        : "—";
                scores[player][category < 6 ? category : category + 2].text(
                        Component.text(value)
                                .color(
                                        written < 0
                                                ? NamedTextColor.DARK_GRAY
                                                : NamedTextColor.BLACK));
            }
            scores[player][6].text(Component.text(upper + "/63").color(NamedTextColor.BLACK));
            scores[player][7].text(
                    Component.text(upper >= 63 ? "+35" : "+0").color(NamedTextColor.BLACK));
            scores[player][14].text(
                    Component.text(board.total(player)).color(NamedTextColor.BLACK));
        }
        rollLabel.text(
                Language.component(
                        rolling()
                                ? "hint.rolling"
                                : board.legalActions(seat).contains("roll")
                                        ? "menu.yacht.roll"
                                        : "menu.yacht.choose-score",
                        "count",
                        3 - board.rolls()));
        for (int i = 0; i < categories.length; i++) {
            categories[i].text(
                    (i < 6
                                    ? Component.text(String.valueOf((char) (0x2680 + i)))
                                            .append(Component.space())
                                    : Component.empty())
                            .append(
                                    Language.component(
                                            "score.category." + YachtGame.CATEGORIES.get(i)))
                            .color(
                                    board.written(seat, i) >= 0
                                            ? NamedTextColor.DARK_GRAY
                                            : NamedTextColor.BLACK));
        }
    }

    public TableView.Hit hit(Location eye, Vector direction) {
        if (closed || !origin.getWorld().equals(eye.getWorld())) {
            return null;
        }
        TableView.Hit nearest = null;
        for (int i = 0; i < 5; i++) {
            if (board.rolls() > 0) {
                DiceMotion.Pose p = poses[i];
                nearest =
                        hit(
                                nearest,
                                eye,
                                direction,
                                "die" + i,
                                p.x(),
                                p.y() - SIZE / 2,
                                p.z(),
                                SIZE,
                                SIZE,
                                SIZE);
            }
        }
        nearest = hit(nearest, eye, direction, "@roll", .45, .007, .82, .95, .035, .23);
        for (int i = 0; i < 12; i++) {
            nearest =
                    hit(
                            nearest,
                            eye,
                            direction,
                            "@score:" + YachtGame.CATEGORIES.get(i),
                            SCORE_X,
                            .007,
                            scoreZ(i),
                            1.45,
                            .035,
                            .094);
        }
        nearest = hit(nearest, eye, direction, "@menu", SCORE_X, -.01, 0, 1.60, .015, 2.25);
        return hit(nearest, eye, direction, "@menu", .32, -.01, 0, 2.00, .015, 2.25);
    }

    private TableView.Hit hit(
            TableView.Hit nearest,
            Location eye,
            Vector direction,
            String id,
            double x,
            double y,
            double z,
            double w,
            double h,
            double d) {
        var box =
                new BoundingBox(
                        origin.getX() + x - w / 2,
                        origin.getY() + y,
                        origin.getZ() + z - d / 2,
                        origin.getX() + x + w / 2,
                        origin.getY() + y + h,
                        origin.getZ() + z + d / 2);
        var hit = box.rayTrace(eye.toVector(), direction, TableGeometry.REACH);
        if (hit == null) {
            return nearest;
        }
        double distance = hit.getHitPosition().distance(eye.toVector());
        return nearest == null || distance < nearest.distance()
                ? new TableView.Hit(id, distance)
                : nearest;
    }

    public void cursor(Player player, String hover) {
        focus(player, rolling() ? null : hover);
        Component hint = Component.empty();
        if (hover != null) {
            if (rolling()) {
                hint = Language.component("hint.rolling");
            } else if (hover.startsWith("die")) {
                int index = Integer.parseInt(hover.substring(3));
                hint =
                        Language.component(
                                board.held(index) ? "table.yacht.unkeep" : "table.yacht.keep");
            } else if (hover.startsWith("@score:")) {
                int category = YachtGame.CATEGORIES.indexOf(hover.substring(7));
                hint =
                        Language.component(
                                "menu.yacht.score",
                                "category",
                                Language.component(
                                        "score.category." + YachtGame.CATEGORIES.get(category)),
                                "score",
                                board.rolls() == 0 ? 0 : YachtGame.score(category, board.dice()));
            } else {
                hint = Language.component(hover.equals("@roll") ? "hint.roll" : "hint.menu");
            }
        }
        player.sendActionBar(hint.colorIfAbsent(NamedTextColor.GOLD));
    }

    private void focus(Player player, String target) {
        if (closed
                || target == null
                || (!target.startsWith("die") && !target.startsWith("@score:"))) {
            clear(player);
            return;
        }
        double x, y, z, width, depth;
        if (target.startsWith("die")) {
            var pose = poses[Integer.parseInt(target.substring(3))];
            x = pose.x();
            y = pose.y() + SIZE / 2 + .003;
            z = pose.z();
            width = depth = SIZE + .016;
        } else {
            x = columnX(board.currentPlayer());
            y = .022;
            z = scoreZ(YachtGame.CATEGORIES.indexOf(target.substring(7)));
            width = COLUMNS_WIDTH / room.capacity - .008;
            depth = .094;
        }
        Focus old = focuses.get(player.getUniqueId());
        if (old != null
                && old.target().equals(target)
                && old.x() == x
                && old.y() == y
                && old.z() == z
                && old.width() == width
                && old.depth() == depth) {
            return;
        }
        clear(player);
        List<BlockDisplay> edges = new ArrayList<>(4);
        double stroke = .006;
        for (double side : new double[] {-1, 1}) {
            edges.add(
                    block(
                            x,
                            y,
                            z + side * (depth - stroke) / 2,
                            width,
                            .003,
                            stroke,
                            Material.LIME_CONCRETE,
                            player));
            edges.add(
                    block(
                            x + side * (width - stroke) / 2,
                            y,
                            z,
                            stroke,
                            .003,
                            depth - stroke * 2,
                            Material.LIME_CONCRETE,
                            player));
        }
        focuses.put(player.getUniqueId(), new Focus(target, x, y, z, width, depth, edges));
    }

    public void clear(Player player) {
        Focus old = focuses.remove(player.getUniqueId());
        if (old != null) {
            old.edges().forEach(Entity::remove);
        }
    }

    private void clearFocuses() {
        focuses.values().forEach(focus -> focus.edges().forEach(Entity::remove));
        focuses.clear();
    }

    private void init(Display display, String id) {
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.getPersistentDataContainer()
                .set(tag, PersistentDataType.STRING, room.id + "|" + id);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(.35f);
    }

    private BlockDisplay block(
            double x, double y, double z, double w, double h, double d, Material material) {
        return block(x, y, z, w, h, d, material, null);
    }

    private BlockDisplay block(
            double x,
            double y,
            double z,
            double w,
            double h,
            double d,
            Material material,
            Player viewer) {
        BlockDisplay entity =
                origin.getWorld()
                        .spawn(
                                origin,
                                BlockDisplay.class,
                                display -> {
                                    init(display, "@menu");
                                    display.setBlock(material.createBlockData());
                                    if (viewer != null) {
                                        display.setVisibleByDefault(false);
                                    }
                                    display.setTransformation(
                                            new Transformation(
                                                    new Vector3f(
                                                            (float) (x - w / 2),
                                                            (float) y,
                                                            (float) (z - d / 2)),
                                                    new Quaternionf(),
                                                    new Vector3f((float) w, (float) h, (float) d),
                                                    new Quaternionf()));
                                });
        if (viewer != null) {
            viewer.showEntity(plugin, entity);
        }
        return entity;
    }

    private TextDisplay label(double x, double y, double z, float scale) {
        TextDisplay label =
                origin.getWorld()
                        .spawn(
                                origin.clone().add(x, y, z),
                                TextDisplay.class,
                                display -> {
                                    init(display, "@menu");
                                    display.setRotation(0, -90);
                                    display.setBillboard(Display.Billboard.FIXED);
                                    display.setDefaultBackground(false);
                                    display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                                    display.setShadowed(false);
                                    display.setLineWidth(Integer.MAX_VALUE);
                                    display.setTransformation(
                                            new Transformation(
                                                    new Vector3f(),
                                                    new Quaternionf(),
                                                    new Vector3f(scale),
                                                    new Quaternionf()));
                                });
        common.add(label);
        return label;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        clearFocuses();
        nativeDice.forEach(YachtDie::close);
        packedDice.forEach(YachtDie::close);
        nativeTable.forEach(audience::remove);
        packedTable.forEach(audience::remove);
        common.forEach(audience::remove);
    }
}
