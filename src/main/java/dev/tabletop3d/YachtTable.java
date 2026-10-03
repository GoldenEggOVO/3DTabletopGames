package dev.tabletop3d;

import dev.tabletop3d.rules.YachtGame;
import java.util.ArrayList;
import java.util.List;
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

/** Five physical dice, fixed keep slots and direct score controls. Rules stay in YachtGame. */
final class YachtTable implements AutoCloseable {
    private static final double SIZE = .18;
    private final Tabletop3D plugin;
    private final Room room;
    private final Location origin;
    private final NamespacedKey tag;
    final TableAudience audience;
    private final List<Entity> common = new ArrayList<>(), nativeTable = new ArrayList<>(), packedTable = new ArrayList<>();
    private final List<YachtDie> nativeDice = new ArrayList<>(), packedDice = new ArrayList<>();
    private final DiceMotion[] throwsByDie = new DiceMotion[5];
    private final DiceMotion.Pose[] poses = new DiceMotion.Pose[5];
    private final TextDisplay[] categories = new TextDisplay[12];
    private final TextDisplay rollLabel, sheetLabel, totalsLabel;
    private YachtGame board;
    private long revision = -1, language = -1;
    private int history, frame;
    private boolean closed;

    YachtTable(Tabletop3D plugin, Room room, Location origin, NamespacedKey tag) {
        this.plugin = plugin; this.room = room; this.origin = origin; this.tag = tag;
        audience = new TableAudience(plugin, origin);
        for (int i = 0; i < 5; i++) poses[i] = rest(i, false, 1);
        for (int i = 0; i < categories.length; i++) {
            double z = -.85 + i * .13;
            common.add(block(-.78, .007, z, .53, .012, .116, Material.POLISHED_BLACKSTONE));
            categories[i] = label(-.78, .032, z, .105f);
        }
        common.add(block(.45, .007, .82, .95, .016, .23, Material.GREEN_CONCRETE));
        rollLabel = label(.45, .038, .82, .16f);
        common.add(block(-.70, .007, .82, .60, .016, .23, Material.POLISHED_BLACKSTONE));
        sheetLabel = label(-.70, .038, .82, .105f);
        totalsLabel = label(.32, .038, -.97, .105f);
        for (Entity entity : common) audience.common(entity);
        sync();
    }

    void sync() {
        if (closed) return;
        layers();
        if (board == room.board && revision == room.revision) {
            if (language != Language.generation()) { language = Language.generation(); refreshLabels(); }
            return;
        }
        YachtGame next = (YachtGame)room.board;
        String action = room.history.isEmpty() ? "" : room.history.get(room.history.size() - 1).getAsJsonObject().get("action").getAsString();
        boolean roll = board == next && room.history.size() == history + 1 && action.equals("roll");
        board = next; revision = room.revision; history = room.history.size(); language = Language.generation();
        int[] values = board.dice();
        for (int i = 0; i < 5; i++) {
            int face = Math.max(1, values[i]);
            if (roll && !board.held(i)) {
                DiceMotion.Pose local = new DiceMotion.Pose(0, poses[i].y(), 0, poses[i].rotation());
                throwsByDie[i] = new DiceMotion(SIZE, .24, local, face, room.seed ^ (history * 31L + i));
            } else {
                throwsByDie[i] = null;
                poses[i] = rest(i, board.held(i), face);
                render(i);
            }
        }
        frame = 0;
        refreshLabels();
    }

    void tick() {
        sync();
        if (!rolling()) return;
        frame++;
        for (int i = 0; i < 5; i++) if (throwsByDie[i] != null) {
            DiceMotion.Pose p = throwsByDie[i].pose(frame);
            // Each throw stays in its own lane; the last frame snaps to the ordered row.
            poses[i] = frame >= DiceMotion.FRAMES ? rest(i, false, board.dice()[i])
                    : new DiceMotion.Pose(x(i) + p.x() * .25, p.y(), .15 + p.z(), p.rotation());
            render(i);
            if (frame >= DiceMotion.FRAMES) throwsByDie[i] = null;
        }
        if (frame == 8 || frame == 13 || frame == 17)
            TableSounds.play(plugin, origin, new TableSounds.Cue(TableSounds.WOOD.sound(), .22f, 1.1f + frame * .01f));
        if (!rolling()) refreshLabels();
    }

    boolean rolling() {
        for (DiceMotion motion : throwsByDie) if (motion != null) return true;
        return false;
    }

    private static double x(int index) { return .32 + (index - 2) * .26; }
    private static DiceMotion.Pose rest(int index, boolean held, int face) {
        DiceMotion.Pose rest = DiceMotion.rest(SIZE, face);
        return new DiceMotion.Pose(x(index), rest.y() + (held ? .02 : 0), held ? -.68 : .15, rest.rotation());
    }

    private void render(int index) {
        if (!nativeDice.isEmpty()) nativeDice.get(index).pose(poses[index]);
        if (!packedDice.isEmpty()) packedDice.get(index).pose(poses[index]);
    }

    private void layers() {
        audience.refresh();
        if (audience.needed(false) && nativeTable.isEmpty()) {
            nativeTable.add(block(0, -.19, 0, 2.25, .14, 2.25, Material.DARK_OAK_PLANKS));
            nativeTable.add(block(0, -.045, 0, 2.10, .045, 2.10, Material.RED_CONCRETE));
            for (double side : new double[]{-1,1}) {
                nativeTable.add(block(side * 1.085, -.045, 0, .08, .10, 2.25, Material.STRIPPED_DARK_OAK_WOOD));
                nativeTable.add(block(0, -.045, side * 1.085, 2.09, .10, .08, Material.STRIPPED_DARK_OAK_WOOD));
            }
            for (double x : new double[]{-.98,.98}) for (double z : new double[]{-.98,.98})
                nativeTable.add(block(x, -TableGeometry.SURFACE, z, .13, TableGeometry.SURFACE - .19, .13, Material.STRIPPED_DARK_OAK_LOG));
            for (int i = 0; i < 5; i++) {
                nativeTable.add(block(x(i), .002, -.68, .235, .012, .235, Material.POLISHED_BLACKSTONE));
                nativeTable.add(block(x(i), .015, -.68, .21, .004, .21, Material.GRAY_CONCRETE));
            }
            nativeTable.forEach(entity -> audience.add(entity, false));
        } else if (!audience.needed(false) && !nativeTable.isEmpty()) {
            nativeTable.forEach(audience::remove); nativeTable.clear();
        }
        if (audience.needed(true) && packedTable.isEmpty()) {
            ItemDisplay item = origin.getWorld().spawn(origin, ItemDisplay.class, d -> {
                init(d, "@menu"); d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setItemStack(plugin.pack.item("yacht_table"));
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY((float)Math.PI), new Vector3f(1), new Quaternionf()));
            });
            packedTable.add(item); audience.add(item, true);
        } else if (!audience.needed(true) && !packedTable.isEmpty()) {
            packedTable.forEach(audience::remove); packedTable.clear();
        }
        diceLayer(nativeDice, false);
        diceLayer(packedDice, true);
    }

    private void diceLayer(List<YachtDie> dice, boolean packed) {
        if (audience.needed(packed) && dice.isEmpty()) {
            for (int i = 0; i < 5; i++) {
                var die = new YachtDie(plugin, room, origin, tag, audience, packed, SIZE, "die" + i);
                dice.add(die); die.pose(poses[i]);
            }
        } else if (!audience.needed(packed) && !dice.isEmpty()) {
            dice.forEach(YachtDie::close); dice.clear();
        }
    }

    private void refreshLabels() {
        int seat = board.currentPlayer();
        sheetLabel.text(Language.component("menu.yacht.score-sheet"));
        Component totals = Component.empty();
        for (int i = 0; i < board.playerCount(); i++) {
            if (i > 0) totals = totals.append(Component.text("  |  "));
            totals = totals.append(Language.component("table.yacht.total", "number", i + 1, "score", board.total(i)));
        }
        totalsLabel.text(totals.color(NamedTextColor.WHITE));
        rollLabel.text(Language.component(rolling() ? "hint.rolling" : board.legalActions(seat).contains("roll")
                ? "menu.yacht.roll" : "menu.yacht.choose-score", "count", 3 - board.rolls()));
        for (int i = 0; i < categories.length; i++) {
            int written = board.written(seat, i);
            String value = written >= 0 ? Integer.toString(written) : board.rolls() == 0 ? "—" : Integer.toString(YachtGame.score(i, board.dice()));
            categories[i].text(Language.component("table.yacht.category", "category",
                    Language.component("score.category." + YachtGame.CATEGORIES.get(i)), "score", value)
                    .color(written >= 0 ? NamedTextColor.GRAY : NamedTextColor.GOLD));
            categories[i].setBrightness(new Display.Brightness(written >= 0 ? 7 : 15, written >= 0 ? 7 : 15));
        }
    }

    TableView.Hit hit(Location eye, Vector direction) {
        if (closed || !origin.getWorld().equals(eye.getWorld())) return null;
        TableView.Hit nearest = null;
        for (int i = 0; i < 5; i++) if (board.rolls() > 0) {
            DiceMotion.Pose p = poses[i];
            nearest = hit(nearest, eye, direction, "die" + i, p.x(), p.y() - SIZE / 2, p.z(), SIZE, SIZE, SIZE);
        }
        nearest = hit(nearest, eye, direction, "@roll", .45, .007, .82, .95, .035, .23);
        nearest = hit(nearest, eye, direction, "@scores", -.70, .007, .82, .60, .035, .23);
        for (int i = 0; i < 12; i++) nearest = hit(nearest, eye, direction, "@score:" + YachtGame.CATEGORIES.get(i), -.78, .007, -.85 + i * .13, .53, .035, .116);
        return hit(nearest, eye, direction, "@menu", 0, -.01, 0, 2.25, .015, 2.25);
    }

    private TableView.Hit hit(TableView.Hit nearest, Location eye, Vector direction, String id, double x, double y, double z, double w, double h, double d) {
        var box = new BoundingBox(origin.getX()+x-w/2, origin.getY()+y, origin.getZ()+z-d/2,
                origin.getX()+x+w/2, origin.getY()+y+h, origin.getZ()+z+d/2);
        var hit = box.rayTrace(eye.toVector(), direction, TableGeometry.REACH);
        if (hit == null) return nearest;
        double distance = hit.getHitPosition().distance(eye.toVector());
        return nearest == null || distance < nearest.distance() ? new TableView.Hit(id, distance) : nearest;
    }

    void cursor(Player player, String hover) {
        Component hint = Component.empty();
        if (hover != null) {
            if (rolling()) hint = Language.component("hint.rolling");
            else if (hover.startsWith("die")) {
                int index = Integer.parseInt(hover.substring(3));
                hint = Language.component(board.held(index) ? "table.yacht.unkeep" : "table.yacht.keep");
            } else if (hover.startsWith("@score:")) {
                int category = YachtGame.CATEGORIES.indexOf(hover.substring(7));
                hint = Language.component("menu.yacht.score", "category", Language.component("score.category." + YachtGame.CATEGORIES.get(category)),
                        "score", board.rolls() == 0 ? 0 : YachtGame.score(category, board.dice()));
            } else hint = Language.component(hover.equals("@scores") ? "menu.yacht.view-score" : hover.equals("@roll") ? "hint.roll" : "hint.menu");
        }
        player.sendActionBar(hint.colorIfAbsent(NamedTextColor.GOLD));
    }

    private void init(Display display, String id) {
        display.setPersistent(false); display.setGravity(false); display.setInvulnerable(true);
        display.getPersistentDataContainer().set(tag, PersistentDataType.STRING, room.id + "|" + id);
        display.setBrightness(new Display.Brightness(15,15)); display.setViewRange(.35f);
    }

    private BlockDisplay block(double x, double y, double z, double w, double h, double d, Material material) {
        return origin.getWorld().spawn(origin, BlockDisplay.class, display -> {
            init(display, "@menu"); display.setBlock(material.createBlockData());
            display.setTransformation(new Transformation(new Vector3f((float)(x-w/2), (float)y, (float)(z-d/2)), new Quaternionf(),
                    new Vector3f((float)w,(float)h,(float)d), new Quaternionf()));
        });
    }

    private TextDisplay label(double x, double y, double z, float scale) {
        TextDisplay label = origin.getWorld().spawn(origin.clone().add(x,y,z), TextDisplay.class, display -> {
            init(display, "@menu"); display.setRotation(0,-90); display.setBillboard(Display.Billboard.FIXED);
            display.setDefaultBackground(false); display.setBackgroundColor(Color.fromARGB(0,0,0,0)); display.setShadowed(false);
            display.setLineWidth(Integer.MAX_VALUE); display.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(scale),new Quaternionf()));
        });
        common.add(label); return label;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        nativeDice.forEach(YachtDie::close); packedDice.forEach(YachtDie::close);
        nativeTable.forEach(audience::remove); packedTable.forEach(audience::remove); common.forEach(audience::remove);
    }
}
