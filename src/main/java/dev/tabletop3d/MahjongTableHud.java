package dev.tabletop3d;

import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.ui.LabelLayout;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Public table information only; concealed hands never enter this renderer. */
final class MahjongTableHud implements AutoCloseable {
    private final Tabletop3D plugin;
    private final Room room;
    private final Location origin;
    private final NamespacedKey tag;
    final List<Entity> entities = new ArrayList<>();
    private final List<TextDisplay> counts = new ArrayList<>(), rounds = new ArrayList<>();
    private final Map<Player, Integer> orientations = new HashMap<>();
    private final TableAudience audience;
    private BlockDisplay panel;
    private ItemDisplay packedPanel;
    private boolean privateText, buildingPanel;
    private final List<TextDisplay> winds = new ArrayList<>(), scores = new ArrayList<>();
    private final List<List<BlockDisplay>> sticks = new ArrayList<>();
    private final boolean[] declared = new boolean[4];
    private final Map<TextDisplay, Component> labels = new HashMap<>();
    private final TextDisplay status, discard;
    private HandGame rendered;
    private long revision = -1;
    private Map<String, String> info = Map.of();
    private int remaining, turn = -1;
    private boolean closed;
    private final BlockDisplay turnEdge;
    private int highlightedTurn = -2;

    MahjongTableHud(Tabletop3D plugin, Room room, Location surfaceOrigin, NamespacedKey tag) {
        this(plugin, room, surfaceOrigin, tag, new TableAudience(plugin, surfaceOrigin));
    }

    MahjongTableHud(
            Tabletop3D plugin,
            Room room,
            Location surfaceOrigin,
            NamespacedKey tag,
            TableAudience audience) {
        this.plugin = plugin;
        this.room = room;
        this.origin = surfaceOrigin.clone();
        this.tag = tag;
        this.audience = audience;
        syncPanel();
        for (int seat = 0; seat < 4; seat++) {
            double angle = seat * Math.PI / 2;
            Location number =
                    origin.clone().add(.075 * Math.sin(angle), .029, .075 * Math.cos(angle));
            number.setYaw(-90 * seat);
            privateText = true;
            counts.add(text(number, true));
            privateText = false;
            Location stick = origin.clone().add(.49 * Math.sin(angle), .029, .49 * Math.cos(angle));
            stick.setYaw(-90 * seat);
            BlockDisplay body = block(stick, Material.WHITE_CONCRETE, .24f, .006f, .018f);
            BlockDisplay dot =
                    block(
                            stick.clone().add(0, .007, 0),
                            Material.RED_CONCRETE,
                            .012f,
                            .002f,
                            .012f);
            sticks.add(List.of(body, dot));
            visible(body, false, .24f, .006f, .018f);
            visible(dot, false, .012f, .002f, .012f);
        }
        status = text(origin.clone().add(0, 1.25, 0), false);
        discard = text(origin.clone().add(0, 1.12, 0), false);
        for (int seat = 0; seat < 4; seat++) {
            double angle = seat * Math.PI / 2;
            Location wind =
                    origin.clone()
                            .add(
                                    .46 * Math.cos(angle) + .46 * Math.sin(angle),
                                    .029,
                                    -.46 * Math.sin(angle) + .46 * Math.cos(angle));
            wind.setYaw(-90 * seat);
            winds.add(text(wind, true));
            Location score = origin.clone().add(.34 * Math.sin(angle), .029, .34 * Math.cos(angle));
            score.setYaw(-90 * seat);
            scores.add(text(score, true));
        }
        for (int seat = 0; seat < 4; seat++) {
            double angle = seat * Math.PI / 2;
            Location round =
                    origin.clone().add(-.15 * Math.sin(angle), .029, -.15 * Math.cos(angle));
            round.setYaw(-90 * seat);
            privateText = true;
            rounds.add(text(round, true));
            privateText = false;
        }
        turnEdge =
                block(origin.clone().add(0, .033, 0), Material.LIME_CONCRETE, .34f, .003f, .014f);
        tick();
    }

    void tick() {
        tick(System.currentTimeMillis());
    }

    void tick(long now) {
        if (closed) return;
        syncPanel();
        syncOrientation();
        // Rules mutate on the room executor. Keep the last public snapshot while it runs.
        if (!room.busy
                && room.board instanceof HandGame game
                && (rendered != game || revision != room.revision)) {
            rendered = game;
            revision = room.revision;
            info = Map.copyOf(game.publicInfo());
            remaining = game.deckSize();
            turn = game.currentPlayer();
        }
        for (TextDisplay count : counts)
            label(
                    count,
                    Component.text(
                            remaining, remaining == 0 ? NamedTextColor.RED : NamedTextColor.AQUA),
                    .46f,
                    .20f);
        int dealer = Integer.parseInt(info.getOrDefault("dealer", "0"));
        for (int seat = 0; seat < 4; seat++) {
            int wind = Math.floorMod(seat - dealer, 4);
            label(
                    winds.get(seat),
                    Component.text(
                            List.of("東", "南", "西", "北").get(wind),
                            wind == 0 ? NamedTextColor.RED : NamedTextColor.WHITE),
                    .12f,
                    .12f);
            label(
                    scores.get(seat),
                    Component.text(info.getOrDefault("score." + seat, ""), NamedTextColor.GOLD),
                    .30f,
                    .075f);
        }
        boolean lobby = room.phase == Room.Phase.LOBBY || room.phase == Room.Phase.STARTING;
        int active =
                room.phase == Room.Phase.PLAYING
                                && !room.busy
                                && room.undo == null
                                && turn >= 0
                                && turn < 4
                        ? turn
                        : -1;
        if (highlightedTurn != active) {
            highlightedTurn = active;
            if (active >= 0) {
                double angle = active * Math.PI / 2;
                Location edge =
                        origin.clone().add(.39 * Math.sin(angle), .033, .39 * Math.cos(angle));
                edge.setYaw(-90 * active);
                turnEdge.teleport(edge);
            }
            visible(turnEdge, active >= 0, .34f, .003f, .014f);
        }
        for (int seat = 0; seat < 4; seat++) {
            boolean next =
                    !lobby && Boolean.parseBoolean(info.getOrDefault("riichi." + seat, "false"));
            if (declared[seat] == next) continue;
            declared[seat] = next;
            visible(sticks.get(seat).get(0), next, .24f, .006f, .018f);
            visible(sticks.get(seat).get(1), next, .012f, .002f, .012f);
        }
        if (lobby) {
            label(status, Component.empty(), .95f, .105f);
            label(discard, Component.empty(), .95f, .042f);
            return;
        }
        int round = Integer.parseInt(info.getOrDefault("round", "1"));
        if (room.phase == Room.Phase.FINISHED)
            round =
                    Math.min(
                            round,
                            Integer.parseInt(info.getOrDefault("rounds", Integer.toString(round))));
        Component heading =
                (info.getOrDefault("profile", "riichi").equals("riichi")
                                ? Language.component(
                                        "table.mahjong.round",
                                        "wind",
                                        Language.component(
                                                "table.mahjong.wind."
                                                        + Math.floorMod((round - 1) / 4, 4)),
                                        "hand",
                                        Math.floorMod(round - 1, 4) + 1)
                                : Language.component("table.mahjong.round-number", "round", round))
                        .colorIfAbsent(NamedTextColor.GOLD);
        for (TextDisplay roundDisplay : rounds) label(roundDisplay, heading, .46f, .075f);
        Component clock;
        if (room.phase == Room.Phase.FINISHED) clock = Language.component("table.mahjong.finished");
        else if (room.phase != Room.Phase.PLAYING || room.busy || room.undo != null)
            clock = Language.component("table.mahjong.paused");
        else {
            long left = Math.max(0, plugin.turnWaitMillis(room) - Math.max(0, now - room.changed));
            clock = Language.component("table.mahjong.timer", "seconds", (left + 999) / 1000);
        }
        Component current = Language.component("room.current-turn", "player", seatName(turn));
        Component summary =
                heading.append(Component.text(" · "))
                        .append(current)
                        .append(Component.newline())
                        .append(clock.colorIfAbsent(NamedTextColor.WHITE));
        int deposits = Integer.parseInt(info.getOrDefault("riichiSticks", "0"));
        if (deposits > 0)
            summary =
                    summary.append(Component.text(" · "))
                            .append(
                                    Language.component(
                                            "table.mahjong.deposits", "count", deposits));
        label(status, summary, .95f, .105f);
        String tile = info.getOrDefault("lastDiscardTile", "");
        Component last =
                tile.isEmpty()
                        ? Language.component("table.mahjong.no-discard")
                        : Language.component(
                                "table.mahjong.last-discard",
                                "player",
                                seatName(
                                        Integer.parseInt(info.getOrDefault("lastDiscardBy", "-1"))),
                                "tile",
                                HandText.piece("mahjong", tile));
        label(discard, last.colorIfAbsent(NamedTextColor.WHITE), .95f, .042f);
    }

    private void syncPanel() {
        if (audience.needed(false) && panel == null) {
            buildingPanel = true;
            panel =
                    block(
                            origin.clone().add(0, .015, 0),
                            Material.BLACK_CONCRETE,
                            1.10f,
                            .012f,
                            1.10f);
            buildingPanel = false;
        } else if (!audience.needed(false) && panel != null) {
            entities.remove(panel);
            audience.remove(panel);
            panel = null;
        }
        if (audience.needed(true) && packedPanel == null) {
            packedPanel =
                    PackedDisplay.spawn(
                            plugin,
                            room,
                            audience,
                            origin.clone().add(0, .015, 0),
                            tag,
                            "mahjong_panel",
                            new Vector3f(1),
                            new Quaternionf());
            entities.add(packedPanel);
        } else if (!audience.needed(true) && packedPanel != null) {
            entities.remove(packedPanel);
            audience.remove(packedPanel);
            packedPanel = null;
        }
    }

    private void syncOrientation() {
        Set<Player> viewers =
                audience.managed() ? audience.all() : new HashSet<>(origin.getWorld().getPlayers());
        Map<Player, Integer> next = new HashMap<>();
        for (Player player : viewers)
            if (player.isOnline()
                    && plugin.allowed(player)
                    && player.getLocation().distanceSquared(origin) <= 24 * 24) {
                int seat = room.seat(player.getUniqueId());
                if (seat < 0) {
                    Location at = player.getLocation();
                    seat =
                            Math.floorMod(
                                    (int)
                                            Math.round(
                                                    Math.atan2(
                                                                    at.getX() - origin.getX(),
                                                                    at.getZ() - origin.getZ())
                                                            / (Math.PI / 2)),
                                    4);
                }
                next.put(player, seat);
                if (!Objects.equals(orientations.get(player), seat))
                    for (int i = 0; i < 4; i++) {
                        if (i == seat) {
                            player.showEntity(plugin, counts.get(i));
                            player.showEntity(plugin, rounds.get(i));
                        } else {
                            player.hideEntity(plugin, counts.get(i));
                            player.hideEntity(plugin, rounds.get(i));
                        }
                    }
            }
        for (Player player : orientations.keySet())
            if (!next.containsKey(player))
                for (int i = 0; i < 4; i++) {
                    player.hideEntity(plugin, counts.get(i));
                    player.hideEntity(plugin, rounds.get(i));
                }
        orientations.clear();
        orientations.putAll(next);
    }

    private String seatName(int seat) {
        return seat >= 0 && seat < room.seats.size() ? room.seats.get(seat).name() : "—";
    }

    private void configure(Display display) {
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.getPersistentDataContainer()
                .set(tag, PersistentDataType.STRING, room.id + "|@board");
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(.35f);
    }

    private BlockDisplay block(
            Location at, Material material, float width, float height, float depth) {
        BlockDisplay display =
                origin.getWorld()
                        .spawn(
                                at,
                                BlockDisplay.class,
                                d -> {
                                    if (audience.managed()) d.setVisibleByDefault(false);
                                    configure(d);
                                    d.setBlock(material.createBlockData());
                                    visible(d, true, width, height, depth);
                                });
        entities.add(display);
        if (buildingPanel) audience.add(display, false);
        else audience.common(display);
        return display;
    }

    private static void visible(
            BlockDisplay display, boolean visible, float width, float height, float depth) {
        display.setTransformation(
                new Transformation(
                        new Vector3f(-width / 2, 0, -depth / 2),
                        new Quaternionf(),
                        visible ? new Vector3f(width, height, depth) : new Vector3f(),
                        new Quaternionf()));
    }

    private TextDisplay text(Location at, boolean flat) {
        return text(at, flat, flat);
    }

    private TextDisplay text(Location at, boolean flat, boolean fixed) {
        TextDisplay display =
                origin.getWorld()
                        .spawn(
                                at,
                                TextDisplay.class,
                                d -> {
                                    if (privateText || audience.managed())
                                        d.setVisibleByDefault(false);
                                    configure(d);
                                    if (!privateText) audience.common(d);
                                    d.setBillboard(
                                            fixed
                                                    ? Display.Billboard.FIXED
                                                    : Display.Billboard.CENTER);
                                    d.setRotation(at.getYaw(), flat ? -90 : 0);
                                    d.setAlignment(TextDisplay.TextAlignment.CENTER);
                                    d.setLineWidth(Integer.MAX_VALUE);
                                    d.setDefaultBackground(false);
                                    d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                                    d.setShadowed(!flat);
                                    d.setSeeThrough(false);
                                });
        entities.add(display);
        return display;
    }

    private void label(TextDisplay display, Component value, float width, float height) {
        if (value.equals(labels.get(display))) return;
        labels.put(display, value);
        var fit = LabelLayout.fit(value, width, height);
        display.text(fit.text());
        display.setTransformation(
                new Transformation(
                        new Vector3f(0, -.125f * fit.scale(), 0),
                        new Quaternionf(),
                        new Vector3f(fit.scale()),
                        new Quaternionf()));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        entities.forEach(audience::remove);
        entities.clear();
        labels.clear();
        orientations.clear();
    }
}
