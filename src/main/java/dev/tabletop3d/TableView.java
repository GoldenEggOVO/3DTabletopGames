package dev.tabletop3d;

import dev.tabletop3d.ui.GameSymbols;

import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.ui.LabelLayout;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Native tabletop rendering, reusable piece entities, bounded animation and private pointers. */
final class TableView implements AutoCloseable {
    final Tabletop3D plugin;
    final Room room;
    final TableGeometry geometry;
    final Location origin;
    private final NamespacedKey tag;
    private final List<Entity> furniture = new ArrayList<>();
    private final Map<String, TokenView> tokens = new LinkedHashMap<>();
    private final Map<String, Cell> cells = new LinkedHashMap<>();
    private dev.tabletop3d.rules.BoardGame renderedBoard;
    private final Set<TokenView> animating = new LinkedHashSet<>();
    private final Map<UUID, Overlay> overlays = new HashMap<>();
    private final List<Entity> lastMove = new ArrayList<>();
    private TextDisplay title;
    private DiceTray diceTray;
    private HandTable handTable;
    private PlayingCardTable playingTable;
    private final List<Entity> handFurniture = new ArrayList<>();
    private ItemDisplay packedTable;
    private boolean buildingHandFurniture;
    private final TableMaps maps;
    private TableAudience boardAudience;
    private final List<Entity> nativeBoardFurniture = new ArrayList<>();
    private ItemDisplay packedBoard;
    private int boardLayers = -1;
    private long revision = -1;
    private int renderedHistory;
    private Component lastTitle;
    private TitleState titleState;
    private boolean wasRolling;
    private String lastDiceKey;
    private boolean pendingTurnSound;

    private record TitleState(
            dev.tabletop3d.rules.BoardGame board,
            long revision,
            Room.Phase phase,
            List<Room.Seat> seats,
            int capacity,
            int table,
            String result,
            long language) {
        TitleState(Room room) {
            this(
                    room.board,
                    room.revision,
                    room.phase,
                    List.copyOf(room.seats),
                    room.capacity,
                    room.table,
                    room.result,
                    Language.generation());
        }

        boolean matches(Room room) {
            return board == room.board
                    && revision == room.revision
                    && phase == room.phase
                    && capacity == room.capacity
                    && table == room.table
                    && result.equals(room.result)
                    && language == Language.generation()
                    && seats.equals(room.seats);
        }
    }

    private String lastDestination;

    private record Token(String id, Cell cell, double stack) {}

    private final class TokenView {
        Token token;
        final List<Entity> parts = new ArrayList<>(), deadMarks = new ArrayList<>();
        Location from, to;
        int frame = 6;
        double height, radius;
        final Map<Display, Matrix4f> flipParts = new LinkedHashMap<>();
        int flipFrame = 10;
        float flipFrom, flipTo;

        TokenView(Token token, Location at) {
            this.token = token;
            this.from = at;
            this.to = at;
            build();
        }

        void build() {
            Cell c = token.cell;
            float scale = (float) geometry.spacing;
            if (boardAudience.needed(true) && PackedBoardModels.supported(room.kind)) {
                ItemDisplay item = packedItem(from,
                        PackedBoardModels.piece(room.kind, c, room.board.publicInfo()),
                        room.kind.equals("connectfour") ? 1 : scale);
                parts.add(item);
                if (room.kind.equals("reversi")) flipParts.put(item,new Matrix4f().scale(scale));
                if ((room.kind.equals("chess") && c.piece().equals(GameSymbols.HORSE)) || room.kind.equals("xiangqi"))
                    item.setRotation(c.owner()==0 ? 180 : 0, 0);
            }
            if (room.kind.equals("connectfour")) {
                if (boardAudience.needed(false)) for (var part : TableModels.connectFour(c.owner()))
                    parts.add(
                            block(
                                    from,
                                    part.material(),
                                    part.x(),
                                    part.y(),
                                    part.z(),
                                    part.w(),
                                    part.h(),
                                    part.d(),
                                    null));
                height = .24;
                radius = .12;
                frame = 12;
                return;
            }
            boolean reversi = room.kind.equals("reversi");
            flipTo = c.owner() == 0 ? 0 : (float) Math.PI;
            for (TableModels.Part part :
                    reversi
                            ? TableModels.reversi()
                            : TableModels.piece(room.kind, c, room.board.publicInfo())) {
                height = Math.max(height, (part.y() + part.h()) * scale);
                radius =
                        Math.max(
                                radius,
                                Math.max(
                                                Math.abs(part.x()) + part.w() / 2,
                                                Math.abs(part.z()) + part.d() / 2)
                                        * scale);
                if (!boardAudience.needed(false)) continue;
                BlockDisplay d =
                        block(
                                from,
                                part.material(),
                                part.x() * scale,
                                part.y() * scale,
                                part.z() * scale,
                                part.w() * scale,
                                part.h() * scale,
                                part.d() * scale,
                                null);
                if (reversi) {
                    flipParts.put(
                            d,
                            new Matrix4f()
                                    .translation(
                                            (float) ((part.x() - part.w() / 2) * scale),
                                            (float) (part.y() * scale),
                                            (float) ((part.z() - part.d() / 2) * scale))
                                    .scale(
                                            (float) (part.w() * scale),
                                            (float) (part.h() * scale),
                                            (float) (part.d() * scale)));
                }
                if (room.kind.equals("chess") && c.piece().equals(GameSymbols.HORSE))
                    d.setRotation(c.owner() == 0 ? 180 : 0, 0);
                if (Set.of("go", "go9", "go13").contains(room.kind)
                        && part.material() == Material.RED_CONCRETE) deadMarks.add(d);
                if (Set.of("aeroplane", "ludo").contains(room.kind))
                    d.setRotation(
                            GameWorld.actualColor(room.board.publicInfo(), c.owner()) * 90, 0);
                parts.add(d);
            }
            if (reversi) poseFlip(flipTo);
            if (boardAudience.needed(false) && (room.kind.equals("xiangqi") || room.kind.equals("aeroplane"))) {
                String glyph =
                        room.kind.equals("xiangqi")
                                ? c.piece()
                                : token.id.substring(token.id.indexOf(':') + 1);
                TextDisplay label =
                        text(
                                from.clone()
                                        .add(
                                                0,
                                                geometry.spacing
                                                        * (room.kind.equals("xiangqi")
                                                                ? .235
                                                                : .39),
                                                0),
                                Component.text(glyph),
                                geometry.spacing * (room.kind.equals("xiangqi") ? 1.50 : 1.0),
                                true,
                                c.owner() == 0 && room.kind.equals("xiangqi")
                                        ? NamedTextColor.DARK_RED
                                        : NamedTextColor.BLACK);
                if (room.kind.equals("xiangqi") && c.owner() == 0) label.setRotation(180, -90);
                parts.add(label);
            }
        }

        void rebuildLayers() {
            int savedFrame = frame;
            float savedFlipFrom = flipFrom, savedFlipTo = flipTo;
            int savedFlipFrame = flipFrame;
            parts.forEach(boardAudience::remove);
            parts.clear(); deadMarks.clear(); flipParts.clear();
            height = radius = 0;
            build();
            frame = savedFrame; flipFrom = savedFlipFrom; flipTo = savedFlipTo; flipFrame = savedFlipFrame;
            positionParts(position());
            if (room.kind.equals("reversi")) poseFlip(flipAngle());
        }

        void mark(Token next) {
            boolean dead = next.cell.piece().contains("×");
            if (token.cell.piece().contains("×") != dead)
                for (Entity part : parts) if (part instanceof ItemDisplay item)
                    item.setItemStack(plugin.pack.item(PackedBoardModels.piece(room.kind,next.cell,room.board.publicInfo())));
            if (dead && deadMarks.isEmpty() && boardAudience.needed(false))
                for (var part : TableModels.deadStoneMarks()) {
                    double scale = geometry.spacing;
                    BlockDisplay display =
                            block(
                                    position(),
                                    part.material(),
                                    part.x() * scale,
                                    part.y() * scale,
                                    part.z() * scale,
                                    part.w() * scale,
                                    part.h() * scale,
                                    part.d() * scale,
                                    null);
                    deadMarks.add(display);
                    parts.add(display);
                }
            else if (!dead && !deadMarks.isEmpty()) {
                deadMarks.forEach(boardAudience::remove);
                parts.removeAll(deadMarks);
                deadMarks.clear();
            }
            height = geometry.spacing * (dead ? .195 : .13);
            token = next;
        }

        void move(Token next, Location at, boolean animate) {
            from = position();
            to = at;
            token = next;
            frame = animate ? 0 : duration();
            if (animate) animating.add(this);
            if (!animate) positionParts(at);
        }

        int duration() {
            return room.kind.equals("connectfour") ? 12 : 6;
        }

        Location position() {
            if (room.kind.equals("connectfour")) {
                double u = Math.min(1, frame / 10.0);
                double y =
                        frame <= 10
                                ? from.getY() + (to.getY() - from.getY()) * u * u
                                : to.getY() + (frame < 12 ? .025 : 0);
                return to.clone().set(to.getX(), y, to.getZ());
            }
            double u = Math.min(1, frame / 6.0), ease = u * u * (3 - 2 * u);
            return from.clone()
                    .add(to.toVector().subtract(from.toVector()).multiply(ease))
                    .add(0, Math.sin(Math.PI * u) * geometry.spacing * .65, 0);
        }

        float flipAngle() {
            float u = Math.min(1, flipFrame / 10f);
            return flipFrom + (flipTo - flipFrom) * u * u * (3 - 2 * u);
        }

        void flip(Token next) {
            if (token.cell.owner() != next.cell.owner()) {
                flipFrom = flipAngle();
                flipTo = next.cell.owner() == 0 ? 0 : (float) Math.PI;
                flipFrame = 0;
                animating.add(this);
            }
            token = next;
        }

        void poseFlip(float angle) {
            float pivot = (float) (geometry.spacing * .065);
            Matrix4f rotation =
                    new Matrix4f().translation(0, pivot, 0).rotateX(angle).translate(0, -pivot, 0);
            flipParts.forEach(
                    (part, base) -> {
                        part.setInterpolationDelay(0);
                        part.setTransformationMatrix(new Matrix4f(rotation).mul(base));
                    });
        }

        void tick() {
            if (frame < duration()) {
                frame++;
                positionParts(position());
            }
            if (flipFrame < 10) {
                flipFrame++;
                poseFlip(flipAngle());
            }
        }

        boolean moving() {
            return frame < duration() || flipFrame < 10;
        }

        void positionParts(Location at) {
            for (Entity part : parts) {
                Location dest = at.clone();
                if (part instanceof TextDisplay)
                    dest.add(0, geometry.spacing * (room.kind.equals("xiangqi") ? .235 : .39), 0);
                dest.setYaw(part.getLocation().getYaw());
                dest.setPitch(part.getLocation().getPitch());
                part.teleport(dest);
            }
        }

        boolean valid() {
            return parts.stream().allMatch(Entity::isValid);
        }

        void remove() {
            animating.remove(this);
            parts.forEach(boardAudience::remove);
        }
    }

    private static final class Overlay {
        final String signature;
        final List<Entity> entities = new ArrayList<>();
        final List<BlockDisplay> hover = new ArrayList<>();
        String cell;
        Component feedback = Component.empty();
        int ticks;

        Overlay(String signature) {
            this.signature = signature;
        }

        void remove() {
            entities.forEach(Entity::remove);
            hover.forEach(Entity::remove);
        }
    }

    TableView(Tabletop3D plugin, Room room, Location center, NamespacedKey tag, TableMaps maps) {
        this.plugin = plugin;
        this.room = room;
        this.tag = tag;
        this.maps = maps;
        geometry = new TableGeometry(room.kind, room.board.cells());
        origin = center.clone().add(0, TableGeometry.SURFACE, 0);
        if (room.board instanceof dev.tabletop3d.rules.SelectedHandGame) {
            playingTable = new PlayingCardTable(plugin, room, origin, tag);
            title = text(origin.clone().add(0,1.8,0), "", .4, false, NamedTextColor.GOLD);
            title.setBillboard(Display.Billboard.CENTER);
            furniture.add(title);
            playingTable.audience.common(title);
            sync();
            return;
        }
        if (room.board instanceof dev.tabletop3d.rules.HandGame) {
            handTable = new HandTable(plugin, room, origin, tag);
            syncHandFurniture();
            title = text(origin.clone().add(0, 1.8, 0), "", .4, false, NamedTextColor.GOLD);
            title.setBillboard(Display.Billboard.CENTER);
            furniture.add(title);
            if (handTable.audience.managed()) {
                title.setVisibleByDefault(false);
                handTable.audience.common(title);
            }
            sync();
            return;
        }
        boardAudience = new TableAudience(plugin, origin, PackedBoardModels.supported(room.kind));
        if (!room.kind.equals("connectfour")) {
            Interaction hit = origin.getWorld().spawn(origin.clone().add(0,.012,0), Interaction.class, e -> {
                tag(e,"@board");
                e.setInteractionWidth(2.25f);
                e.setInteractionHeight(.025f);
                e.setResponsive(true);
            });
            furniture.add(hit);
            boardAudience.common(hit);
        }
        title = text(origin.clone().add(0, room.kind.equals("connectfour") ? 2.05 : 1.65, 0),
                "", room.kind.equals("connectfour") ? .38 : .48, false, NamedTextColor.GOLD);
        title.setBillboard(Display.Billboard.CENTER);
        title.setLineWidth(500);
        furniture.add(title);
        boardAudience.common(title);
        if (room.kind.equals("xiangqi")) {
            TextDisplay river = text(origin.clone().add(0,.018,0), Component.text(GameSymbols.XIANGQI_RIVER),
                    .26,true,NamedTextColor.DARK_GRAY);
            furniture.add(river);
            boardAudience.common(river);
        }
        if (room.kind.equals("yacht")) {
            TextDisplay roll = text(origin.clone().add(1.30,.025,0), Language.component("table.roll"),
                    .40,true,NamedTextColor.GOLD);
            furniture.add(roll);
            boardAudience.common(roll);
        }
        if (Set.of("aeroplane","ludo").contains(room.kind))
            diceTray = new DiceTray(plugin,room,center,tag,!room.sideTray);
        syncBoardFurniture();
        sync();
    }

    private void buildNativeBoardFurniture() {
        double width = 2.25, leg = width / 2 - .135, edge = width / 2 - .065;
        furniture.add(block(origin, Material.DARK_OAK_PLANKS, 0, -.19, 0, width, .14, width, null));
        for (double x : new double[] {-leg, leg})
            for (double z : new double[] {-leg, leg})
                furniture.add(
                        block(
                                origin,
                                Material.STRIPPED_DARK_OAK_LOG,
                                x,
                                -TableGeometry.SURFACE,
                                z,
                                .15,
                                TableGeometry.SURFACE - .13,
                                .15,
                                null));
        for (double v : new double[] {-edge, edge}) {
            furniture.add(
                    block(
                            origin,
                            Material.STRIPPED_DARK_OAK_WOOD,
                            v,
                            -.05,
                            0,
                            .10,
                            .11,
                            width - .03,
                            null));
            furniture.add(
                    block(
                            origin,
                            Material.STRIPPED_DARK_OAK_WOOD,
                            0,
                            -.05,
                            v,
                            width - .03,
                            .11,
                            .10,
                            null));
        }
        if (room.kind.equals("connectfour")) {
            for (int x = 0; x <= 7; x++)
                furniture.add(
                        block(
                                origin,
                                Material.BLUE_CONCRETE,
                                (x - 3.5) * .28,
                                .02,
                                0,
                                .035,
                                1.72,
                                .12,
                                null));
            for (int y = 0; y <= 6; y++)
                furniture.add(
                        block(
                                origin,
                                Material.BLUE_CONCRETE,
                                0,
                                .02 + y * .28,
                                0,
                                2,
                                .035,
                                .12,
                                null));
            for (double side : new double[] {-1.06, 1.06}) {
                furniture.add(
                        block(origin, Material.BLUE_CONCRETE, side, -.04, 0, .12, 1.83, .20, null));
                furniture.add(
                        block(
                                origin,
                                Material.POLISHED_DEEPSLATE,
                                side,
                                -.04,
                                0,
                                .26,
                                .10,
                                .65,
                                null));
            }
            furniture.add(block(origin, Material.BLUE_CONCRETE, 0, 1.74, 0, 2.25, .09, .20, null));
            return;
        }
        List<org.bukkit.inventory.ItemStack> images = maps.get(origin.getWorld(), geometry);
        for (int z = 0; z < 2; z++)
            for (int x = 0; x < 2; x++) {
                Location at = origin.clone().add(x - .5, 0, z - .5);
                final int tile = z * 2 + x;
                ItemFrame f =
                        origin.getWorld()
                                .spawn(
                                        at,
                                        ItemFrame.class,
                                        e -> {
                                            tag(e, "@board");
                                            boardAudience.add(e, false);
                                            e.setFacingDirection(BlockFace.UP, true);
                                            e.setFixed(true);
                                            e.setVisible(false);
                                            e.setRotation(Rotation.NONE);
                                            e.setItem(images.get(tile), false);
                                            e.setItemDropChance(0);
                                        });
                // Hanging-entity creation snaps to a block face. Reposition once before any players
                // receive the next frame.
                f.teleport(at);
                f.setFacingDirection(BlockFace.UP, true);
                furniture.add(f);
            }
    }

    private void syncHandFurniture() {
        TableAudience audience = handTable.audience;
        if (audience.needed(false) && handFurniture.isEmpty()) {
            buildingHandFurniture = true;
            try {
                if (room.kind.equals("color-eight"))
                    for (var part : RoundCardTable.parts()) {
                        Location at = origin.clone();
                        at.setYaw(part.yaw());
                        handFurniture.add(
                                block(
                                        at,
                                        part.material(),
                                        part.x(),
                                        part.y(),
                                        part.z(),
                                        part.w(),
                                        part.h(),
                                        part.d(),
                                        null));
                    }
                else {
                    double width = 3, leg = width / 2 - .135, edge = width / 2 - .065;
                    handFurniture.add(
                            block(
                                    origin,
                                    Material.DARK_OAK_PLANKS,
                                    0,
                                    -.19,
                                    0,
                                    width,
                                    .14,
                                    width,
                                    null));
                    for (double x : new double[] {-leg, leg})
                        for (double z : new double[] {-leg, leg})
                            handFurniture.add(
                                    block(
                                            origin,
                                            Material.STRIPPED_DARK_OAK_LOG,
                                            x,
                                            -TableGeometry.SURFACE,
                                            z,
                                            .15,
                                            TableGeometry.SURFACE - .13,
                                            .15,
                                            null));
                    for (double v : new double[] {-edge, edge}) {
                        handFurniture.add(
                                block(
                                        origin,
                                        Material.STRIPPED_DARK_OAK_WOOD,
                                        v,
                                        -.05,
                                        0,
                                        .10,
                                        .11,
                                        width - .03,
                                        null));
                        handFurniture.add(
                                block(
                                        origin,
                                        Material.STRIPPED_DARK_OAK_WOOD,
                                        0,
                                        -.05,
                                        v,
                                        width - .03,
                                        .11,
                                        .10,
                                        null));
                    }
                    handFurniture.add(
                            block(
                                    origin,
                                    Material.GREEN_CONCRETE,
                                    0,
                                    -.05,
                                    0,
                                    width - .21,
                                    .05,
                                    width - .21,
                                    null));
                }
            } finally {
                buildingHandFurniture = false;
            }
        } else if (!audience.needed(false) && !handFurniture.isEmpty()) {
            handFurniture.forEach(audience::remove);
            handFurniture.clear();
        }
        if (audience.needed(true) && packedTable == null)
            packedTable =
                    PackedDisplay.spawn(
                            plugin,
                            room,
                            audience,
                            origin,
                            tag,
                            room.kind.equals("mahjong") ? "mahjong_table" : "card_table",
                            new Vector3f(1),
                            new Quaternionf());
        else if (!audience.needed(true) && packedTable != null) {
            audience.remove(packedTable);
            packedTable = null;
        }
    }

    private void tag(Entity entity, String id) {
        entity.setPersistent(false);
        entity.setGravity(false);
        entity.setInvulnerable(true);
        entity.getPersistentDataContainer().set(tag, PersistentDataType.STRING, room.id + "|" + id);
    }

    private void display(Display d, Player viewer) {
        tag(d, "@model");
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(.35f);
        d.setTeleportDuration(2);
        d.setInterpolationDuration(2);
        if (viewer != null) d.setVisibleByDefault(false);
        else if (boardAudience != null) boardAudience.add(d, false);
        if (buildingHandFurniture) handTable.audience.add(d, false);
    }

    private BlockDisplay block(
            Location at,
            Material mat,
            double x,
            double y,
            double z,
            double w,
            double h,
            double depth,
            Player viewer) {
        BlockDisplay result =
                origin.getWorld()
                        .spawn(
                                at,
                                BlockDisplay.class,
                                d -> {
                                    display(d, viewer);
                                    d.setBlock(mat.createBlockData());
                                    d.setTransformation(
                                            new Transformation(
                                                    new Vector3f(
                                                            (float) (x - w / 2),
                                                            (float) y,
                                                            (float) (z - depth / 2)),
                                                    new Quaternionf(),
                                                    new Vector3f(
                                                            (float) w, (float) h, (float) depth),
                                                    new Quaternionf()));
                                });
        if (viewer != null) viewer.showEntity(plugin, result);
        return result;
    }

    private TextDisplay text(
            Location at, String value, double scale, boolean flat, NamedTextColor color) {
        return text(at, Component.text(value), scale, flat, color);
    }

    private TextDisplay text(
            Location at, Component value, double scale, boolean flat, NamedTextColor color) {
        return origin.getWorld()
                .spawn(
                        at,
                        TextDisplay.class,
                        d -> {
                            display(d, null);
                            d.setBillboard(Display.Billboard.FIXED);
                            if (flat) d.setRotation(0, -90);
                            d.text(value.colorIfAbsent(color));
                            d.setLineWidth(200);
                            d.setAlignment(TextDisplay.TextAlignment.CENTER);
                            d.setDefaultBackground(false);
                            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                            d.setShadowed(false);
                            d.setSeeThrough(false);
                            d.setTransformation(
                                    new Transformation(
                                            new Vector3f(0, flat ? (float) (-.125 * scale) : 0, 0),
                                            new Quaternionf(),
                                            new Vector3f((float) scale),
                                            new Quaternionf()));
                        });
    }

    private List<Token> desired() {
        List<Token> list = new ArrayList<>();
        for (Cell c : cells.values())
            if (c.owner() >= 0) {
                if (Set.of("aeroplane", "ludo").contains(room.kind)) {
                    int stack = 0;
                    for (char digit : c.piece().toCharArray())
                        if (digit >= '1' && digit <= '4')
                            list.add(
                                    new Token(
                                            c.owner() + ":" + digit,
                                            c,
                                            stack++ * (room.kind.equals("ludo") ? .82 : .19)));
                } else list.add(new Token(c.id(), c, 0));
            }
        return list;
    }

    private Location at(Token t) {
        if (room.kind.equals("connectfour"))
            return origin.clone().add((t.cell.x() - 3) * .28, .05 + t.cell.y() * .28, 0);
        return origin.clone()
                .add(geometry.x(t.cell), .03 + t.stack * geometry.spacing, geometry.z(t.cell));
    }

    private boolean same(Token a, Token b) {
        return a.cell.owner() == b.cell.owner() && a.cell.piece().equals(b.cell.piece());
    }

    void sync() {
        if (boardAudience != null) syncBoardFurniture();
        if (renderedBoard != room.board) pendingTurnSound = false;
        if (playingTable != null) {
            playingTable.sync(); renderedBoard=room.board; revision=room.revision; updateTitle(); return;
        }
        if (handTable != null) {
            handTable.sync();
            renderedBoard = room.board;
            revision = room.revision;
            updateTitle();
            return;
        }
        boolean changed =
                revision >= 0 && (revision != room.revision || renderedBoard != room.board);
        if (revision == room.revision
                && renderedBoard == room.board
                && tokens.values().stream().allMatch(TokenView::valid)) {
            updateTitle();
            return;
        }
        cells.clear();
        room.board.cells().forEach(cell -> cells.put(cell.id(), cell));
        if (changed) {
            overlays.values().forEach(Overlay::remove);
            overlays.clear();
        }
        Map<String, TokenView> old = new LinkedHashMap<>(tokens), next = new LinkedHashMap<>();
        List<Token> pending = new ArrayList<>();
        for (Token want : desired()) {
            TokenView existing = old.get(want.id);
            if (existing != null
                    && existing.valid()
                    && Set.of("go", "go9", "go13").contains(room.kind)
                    && existing.token.cell.owner() == want.cell.owner()) {
                old.remove(want.id);
                existing.mark(want);
                next.put(want.id, existing);
                continue;
            }
            if (existing != null && existing.valid() && room.kind.equals("reversi")) {
                old.remove(want.id);
                existing.flip(want);
                next.put(want.id, existing);
                continue;
            }
            if (existing != null
                    && existing.valid()
                    && (Set.of("aeroplane", "ludo").contains(room.kind)
                            || same(existing.token, want))) {
                old.remove(want.id);
                boolean moved =
                        !existing.token.cell.id().equals(want.cell.id())
                                || existing.token.stack != want.stack;
                if (moved) existing.move(want, at(want), changed);
                else existing.token = want;
                next.put(want.id, existing);
            } else pending.add(want);
        }
        String action = lastAction();
        String[] move = action.split(":");
        for (Token want : pending) {
            TokenView source = null;
            String sourceId = null;
            if (changed
                    && move.length >= 3
                    && move[0].equals("move")
                    && move[2].equals(want.cell.id())) {
                source = old.get(move[1]);
                sourceId = move[1];
                if (source != null && (!source.valid() || !same(source.token, want))) {
                    source = null;
                    sourceId = null;
                }
            }
            if (source == null
                    && changed
                    && !Set.of("aeroplane", "ludo", "reversi", "connectfour").contains(room.kind))
                for (var e : old.entrySet())
                    if (e.getValue().valid()
                            && same(e.getValue().token, want)
                            && !e.getValue().token.cell.id().equals(want.cell.id())) {
                        source = e.getValue();
                        sourceId = e.getKey();
                        break;
                    }
            if (source != null) {
                old.remove(sourceId);
                source.move(want, at(want), true);
                next.put(want.id, source);
            } else {
                Location pos = at(want);
                Location start = pos.clone();
                if (changed)
                    start.setY(
                            room.kind.equals("connectfour")
                                    ? origin.getY() + 1.9
                                    : pos.getY() + geometry.spacing * .8);
                TokenView created = new TokenView(want, start);
                if (changed) created.move(want, pos, true);
                next.put(want.id, created);
            }
        }
        old.values().forEach(TokenView::remove);
        tokens.clear();
        tokens.putAll(next);
        if (changed) {
            lastMove.forEach(boardAudience::remove);
            lastMove.clear();
            lastDestination = move.length >= 3 ? move[2] : move.length == 2 ? move[1] : null;
            if (lastDestination != null && geometry.byId.containsKey(lastDestination))
                ring(lastMove, lastDestination, Material.GOLD_BLOCK, null, .90);
        }
        if (diceTray != null) {
            int value =
                    Math.max(
                            1, Integer.parseInt(room.board.publicInfo().getOrDefault("dice", "1")));
            if (renderedBoard == room.board
                    && room.history.size() == renderedHistory + 1
                    && action.equals("roll")) diceTray.roll(value, room.seed ^ room.history.size());
            else if (renderedBoard != room.board) diceTray.settle(value);
        }
        renderedHistory = room.history.size();
        renderedBoard = room.board;
        revision = room.revision;
        updateTitle();
    }

    private void syncBoardFurniture() {
        boardAudience.refresh();
        if (boardAudience.needed(false) && nativeBoardFurniture.isEmpty()) {
            Set<Entity> before = new HashSet<>(furniture);
            buildNativeBoardFurniture();
            for (Entity entity : furniture) if (!before.contains(entity)) {
                if (entity instanceof TextDisplay || entity instanceof Interaction) boardAudience.common(entity);
                else nativeBoardFurniture.add(entity);
            }
        } else if (!boardAudience.needed(false) && !nativeBoardFurniture.isEmpty()) {
            nativeBoardFurniture.forEach(boardAudience::remove);
            furniture.removeAll(nativeBoardFurniture);
            nativeBoardFurniture.clear();
        }
        if (PackedBoardModels.supported(room.kind) && boardAudience.needed(true) && packedBoard == null) {
            packedBoard = packedItem(origin, PackedBoardModels.table(room.kind), room.kind.equals("connectfour") ? 2 : 1);
        } else if (!boardAudience.needed(true) && packedBoard != null) {
            boardAudience.remove(packedBoard);
            packedBoard = null;
        }
        int neededLayers = (boardAudience.needed(false) ? 1 : 0) | (boardAudience.needed(true) ? 2 : 0);
        if (boardLayers != neededLayers) {
            for (TokenView token : tokens.values()) token.rebuildLayers();
            boardLayers = neededLayers;
        }
    }

    private ItemDisplay packedItem(Location at, String model, float scale) {
        return origin.getWorld().spawn(at, ItemDisplay.class, entity -> {
            tag(entity, "@model");
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setViewRange(.35f);
            entity.setTeleportDuration(2);
            entity.setInterpolationDuration(2);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setItemStack(plugin.pack.item(model));
            entity.setTransformationMatrix(new Matrix4f().scale(scale));
            boardAudience.add(entity, true);
        });
    }

    private String lastAction() {
        if (room.history.isEmpty()) return "";
        var e = room.history.get(room.history.size() - 1).getAsJsonObject().get("action");
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private void updateTitle() {
        if (room.kind.equals("mahjong")
                && room.phase != Room.Phase.LOBBY
                && room.phase != Room.Phase.STARTING) {
            if (!Component.empty().equals(lastTitle)) {
                title.text(Component.empty());
                lastTitle = Component.empty();
            }
            titleState = null;
            return;
        }
        boolean changed = titleState == null || !titleState.matches(room);
        if (changed) {
            int turn = room.board.currentPlayer();
            Component status =
                    room.board.finished()
                            ? RoomText.outcome(room, room.board.outcome())
                            : Language.component(
                                    "table.turn",
                                    "player",
                                    turn >= 0 && turn < room.seats.size()
                                            ? RoomText.player(room.seats.get(turn), turn + 1)
                                            : Language.component("room.player"));
            if (room.phase == Room.Phase.LOBBY) status = Language.component("table.waiting");
            else if (room.phase == Room.Phase.FINISHED && !room.result.isEmpty())
                status = RoomText.outcome(room, room.result);
            else if (room.phase == Room.Phase.PAUSED || room.phase == Room.Phase.ABORTED)
                status = RoomText.phase(room);
            Component value =
                    Language.component(
                                    "table.title",
                                    "game",
                                    RoomText.game(room.kind),
                                    "number",
                                    room.table + 1)
                            .append(Component.newline())
                            .append(status.colorIfAbsent(NamedTextColor.WHITE))
                            .append(Component.newline())
                            .append(
                                    RoomText.roster(room.seats, room.capacity)
                                            .colorIfAbsent(NamedTextColor.WHITE))
                            .append(Component.newline())
                            .append(
                                    Language.component(
                                                    handTable != null
                                                            ? "table.hand.hint"
                                                            : room.kind.equals("yacht")
                                                                    ? "table.yacht.hint"
                                                                    : room.kind.equals("ludo")
                                                                            ? "table.ludo.hint"
                                                                            : "table.hint")
                                            .colorIfAbsent(NamedTextColor.GRAY))
                            .append(Component.newline())
                            .append(
                                    Language.component("table.join")
                                            .colorIfAbsent(NamedTextColor.GRAY));
            if (!value.equals(lastTitle)) {
                var fit = LabelLayout.fit(value, 2.8f, .48f);
                title.text(fit.text());
                // LabelLayout owns wrapping; prevent the client from wrapping a fitted line again.
                title.setLineWidth(Integer.MAX_VALUE);
                title.setTransformation(
                        new Transformation(
                                new Vector3f(),
                                new Quaternionf(),
                                new Vector3f(fit.scale()),
                                new Quaternionf()));
                lastTitle = value;
            }
            titleState = new TitleState(room);
        }
        if (diceTray != null && (changed || wasRolling != rolling())) {
            String key =
                    rolling()
                            ? "table.dice.rolling"
                            : room.board.publicInfo().getOrDefault("pendingRoll", "0").equals("0")
                                    ? "table.dice.roll"
                                    : room.kind.equals("ludo")
                                            ? "table.ludo.choose"
                                            : "table.dice.choose";
            if (changed || !key.equals(lastDiceKey)) {
                diceTray.label(Language.component(key).colorIfAbsent(NamedTextColor.GOLD));
                lastDiceKey = key;
            }
            wasRolling = rolling();
        }
    }

    void tick() {
        if (boardAudience != null) syncBoardFurniture();
        for (var iterator = animating.iterator(); iterator.hasNext(); ) {
            TokenView token = iterator.next();
            token.tick();
            if (!token.moving()) iterator.remove();
        }
        if (diceTray != null) diceTray.tick();
        if (playingTable != null) playingTable.sync();
        if (handTable != null) {
            handTable.tick();
            syncHandFurniture();
        }
        updateTitle();
        if (pendingTurnSound && !rolling()) {
            pendingTurnSound = false;
            TableSounds.turn(plugin, room, origin);
        }
    }

    void turnSound() {
        if (rolling()) pendingTurnSound = true;
        else TableSounds.turn(plugin, room, origin);
    }

    static Set<Integer> pipIndices(int value) {
        return switch (value) {
            case 1 -> Set.of(0);
            case 2 -> Set.of(1, 2);
            case 3 -> Set.of(0, 1, 2);
            case 4 -> Set.of(1, 2, 3, 4);
            case 5 -> Set.of(0, 1, 2, 3, 4);
            case 6 -> Set.of(1, 2, 3, 4, 5, 6);
            default -> throw new IllegalArgumentException("dice face");
        };
    }

    static Quaternionf faceRotation(int face) {
        float half = (float) (Math.PI / 2);
        return switch (face) {
            case 1 -> new Quaternionf();
            case 2 -> new Quaternionf().rotateZ(-half);
            case 3 -> new Quaternionf().rotateX(half);
            case 4 -> new Quaternionf().rotateX(-half);
            case 5 -> new Quaternionf().rotateZ(half);
            case 6 -> new Quaternionf().rotateX(half * 2);
            default -> throw new IllegalArgumentException("dice face");
        };
    }

    boolean rolling() {
        return diceTray != null && diceTray.rolling();
    }

    String handHit(Player player, Location eye, org.bukkit.util.Vector direction) {
        return playingTable != null ? playingTable.handHit(player,eye,direction) : handTable == null ? null : handTable.hit(player, eye, direction);
    }

    boolean deckHit(Location eye, org.bukkit.util.Vector direction) {
        return handTable != null && handTable.deckHit(eye, direction);
    }

    String handCallHit(Player player, Location eye, org.bukkit.util.Vector direction) {
        return playingTable != null ? playingTable.callHit(player,eye,direction) : handTable == null ? null : handTable.callHit(player, eye, direction);
    }

    void dismissHandCalls(Player player) {
        if (handTable != null) handTable.dismissCalls(player);
    }

    boolean toggleMahjongAssistance(Player player, String option) {
        return handTable != null && handTable.toggleAssistance(player, option);
    }

    MahjongAssist mahjongAssistance(int seat) {
        return handTable == null ? null : handTable.assistance(seat);
    }

    boolean expandHandCall(Player player, String group) {
        return handTable != null && handTable.expandCall(player, group);
    }

    String mahjongHandAction(Player player, String id) {
        return handTable == null ? null : handTable.handAction(player, id);
    }

    String cardHandAction(Player player, String id) {
        return playingTable != null ? playingTable.cardAction(player,id) : handTable == null ? null : handTable.cardAction(player, id);
    }

    String playingAction(Player player,String control) {
        return playingTable == null ? null : playingTable.action(player,control);
    }

    void maintainMahjongPress(Player player) {
        if (handTable != null) handTable.keepHandPress(player);
    }

    record Hit(String cell, double distance) {}

    Hit hitPiece(Location eye, org.bukkit.util.Vector direction) {
        Hit nearest = null;
        for (TokenView token : tokens.values()) {
            Location p = token.position();
            var box =
                    new org.bukkit.util.BoundingBox(
                            p.getX() - token.radius,
                            p.getY(),
                            p.getZ() - token.radius,
                            p.getX() + token.radius,
                            p.getY() + token.height,
                            p.getZ() + token.radius);
            var hit = box.rayTrace(eye.toVector(), direction, TableGeometry.REACH);
            if (hit != null) {
                double distance = hit.getHitPosition().distance(eye.toVector());
                if (nearest == null || distance < nearest.distance)
                    nearest = new Hit(token.token.cell.id(), distance);
            }
        }
        if (diceTray != null) {
            double distance = diceTray.hit(eye, direction);
            if (distance >= 0 && (nearest == null || distance < nearest.distance))
                nearest = new Hit("@roll", distance);
        }
        return nearest;
    }

    String verticalHit(Location eye, org.bukkit.util.Vector direction) {
        if (Math.abs(direction.getZ()) < 1e-6) return null;
        double distance = (origin.getZ() - eye.getZ()) / direction.getZ();
        if (distance < 0 || distance > TableGeometry.REACH) return null;
        if (eye.getWorld()
                        .rayTraceBlocks(
                                eye,
                                direction,
                                Math.max(.001, distance - .035),
                                FluidCollisionMode.NEVER,
                                true)
                != null) return null;
        var point = eye.toVector().add(direction.clone().multiply(distance));
        double x = point.getX() - origin.getX(), y = point.getY() - origin.getY();
        int col = (int) Math.floor(x / .28 + 3.5), row = (int) Math.floor((y - .02) / .28);
        return col >= 0 && col < 7 && row >= 0 && row < 6 ? col + "," + row : null;
    }

    void cursor(Player p, GameWorld.Pick pick, String hover) {
        if (playingTable != null) {
            playingTable.hover(p,hover!=null && hover.startsWith("@hand:") ? hover.substring(6) : null);
            return;
        }
        if (handTable != null) {
            handTable.hover(
                    p,
                    hover != null && (hover.startsWith("@hand:") || hover.startsWith("@tile:"))
                            ? hover.substring(6)
                            : null);
            handTable.assistanceHint(p, hover);
            return;
        }
        boolean turn =
                room.phase == Room.Phase.PLAYING
                        && !room.busy
                        && room.seat(p.getUniqueId()) >= 0
                        && (room.seat(p.getUniqueId()) == room.board.currentPlayer()
                                || room.board instanceof dev.tabletop3d.rules.GoGame go
                                        && go.scoring());
        String signature =
                room.revision
                        + "/"
                        + room.phase
                        + "/"
                        + room.busy
                        + "/"
                        + turn
                        + "/"
                        + rolling()
                        + "/"
                        + Language.generation()
                        + "/"
                        + (pick == null ? "" : pick.source());
        Overlay old = overlays.get(p.getUniqueId());
        boolean reset = old == null || !old.signature.equals(signature);
        if (reset) {
            if (old != null) old.remove();
            old = new Overlay(signature);
            overlays.put(p.getUniqueId(), old);
        }
        if (room.kind.equals("connectfour")) {
            columnCursor(p, old, reset, turn, hover);
            return;
        }
        if (room.kind.equals("ludo")) {
            ludoCursor(p, old, reset, turn && !rolling(), hover);
            return;
        }
        if (reset && turn && pick != null) {
            ring(old.entities, pick.source(), Material.LIME_CONCRETE, p, 1.0);
            Set<String> destinations = new HashSet<>();
            for (String action : pick.actions()) {
                String[] parts = action.split(":");
                if (parts.length >= 3) destinations.add(parts[2]);
            }
            for (String id : destinations) {
                Cell cell = cells.get(id);
                if (cell != null) {
                    if (cell.owner() >= 0)
                        ring(old.entities, id, Material.LIGHT_BLUE_CONCRETE, p, .96);
                    else
                        old.entities.add(
                                block(
                                        origin.clone()
                                                .add(geometry.x(cell), .023, geometry.z(cell)),
                                        Material.LIGHT_BLUE_CONCRETE,
                                        0,
                                        0,
                                        0,
                                        geometry.spacing * .20,
                                        .018,
                                        geometry.spacing * .20,
                                        p));
                }
            }
        }
        if (!reset && Objects.equals(old.cell, hover)) {
            if (++old.ticks % 10 == 0) p.sendActionBar(old.feedback);
            return;
        }
        old.cell = hover;
        old.ticks = 0;
        if (hover != null && cells.containsKey(hover)) {
            Cell cell = cells.get(hover);
            boolean direct =
                    turn
                            && room.board.actionsForCell(room.seat(p.getUniqueId()), hover).stream()
                                    .anyMatch(
                                            a ->
                                                    a.startsWith("place:")
                                                            || a.startsWith("dead:")
                                                            || a.startsWith("hold:"));
            boolean legal =
                    turn
                            && (direct
                                    || pick != null
                                            && !GameWorld.destinationActions(pick, hover).isEmpty()
                                    || !GameWorld.sourceActions(
                                                    room.board, room.seat(p.getUniqueId()), hover)
                                            .isEmpty());
            Material material =
                    !turn
                            ? Material.GRAY_CONCRETE
                            : legal ? Material.YELLOW_CONCRETE : Material.RED_CONCRETE;
            if (old.hover.isEmpty()) {
                List<Entity> list = new ArrayList<>();
                ring(list, hover, material, p, .82);
                list.forEach(e -> oldHoverAdd(overlays.get(p.getUniqueId()), e));
            } else {
                Location at = origin.clone().add(geometry.x(cell), .025, geometry.z(cell));
                for (BlockDisplay d : old.hover) {
                    d.teleport(at);
                    d.setBlock(material.createBlockData());
                }
            }
            String hint =
                    !turn
                            ? blockedHint()
                            : legal
                                    ? direct
                                            ? room.kind.equals("yacht")
                                                    ? "hint.hold"
                                                    : cell.piece().contains("×")
                                                            ? "hint.restore"
                                                            : "hint.act"
                                            : pick == null ? "hint.select" : "hint.place"
                                    : pick == null ? "hint.unavailable" : "hint.invalid";
            old.feedback =
                    Language.component(
                                    "hint.cell",
                                    "coordinate",
                                    Component.text(GameWorld.coordinate(room.kind, cell)),
                                    "piece",
                                    cell.owner() < 0
                                            ? Language.component("hint.empty")
                                            : Component.text(Language.glyph(cell.piece())),
                                    "action",
                                    Language.component(hint))
                            .colorIfAbsent(legal ? NamedTextColor.YELLOW : NamedTextColor.GRAY);
        } else {
            old.hover.forEach(Entity::remove);
            old.hover.clear();
            old.feedback =
                    ("@roll".equals(hover)
                                    ? Language.component(
                                            rolling()
                                                    ? "hint.rolling"
                                                    : turn ? "hint.roll" : blockedHint())
                                    : "@menu".equals(hover)
                                            ? Language.component("hint.menu")
                                            : Component.empty())
                            .colorIfAbsent(NamedTextColor.GOLD);
        }
        p.sendActionBar(old.feedback);
    }

    private String blockedHint() {
        return switch (room.phase) {
            case LOBBY -> "hint.lobby";
            case STARTING -> "hint.starting";
            case FINISHED, ABORTED -> "hint.finished";
            case PAUSED -> "hint.paused";
            case PLAYING -> room.busy ? "hint.busy" : "hint.wait";
        };
    }

    private void ludoCursor(
            Player player, Overlay overlay, boolean reset, boolean turn, String hover) {
        int seat = room.seat(player.getUniqueId());
        if (reset && turn)
            for (Cell cell : cells.values())
                if (cell.owner() == seat
                        && !GameWorld.sourceActions(room.board, seat, cell.id()).isEmpty())
                    ring(overlay.entities, cell.id(), Material.YELLOW_CONCRETE, player, .92);
        if (!reset && Objects.equals(overlay.cell, hover)) {
            if (++overlay.ticks % 10 == 0) player.sendActionBar(overlay.feedback);
            return;
        }
        overlay.cell = hover;
        overlay.ticks = 0;
        overlay.hover.forEach(Entity::remove);
        overlay.hover.clear();
        List<String> choices = turn ? GameWorld.sourceActions(room.board, seat, hover) : List.of();
        if (!choices.isEmpty()) {
            String[] action = choices.getFirst().split(":");
            Cell destination = cells.get(action[2]);
            List<Entity> preview = new ArrayList<>();
            ring(preview, destination.id(), Material.LIME_CONCRETE, player, .96);
            preview.forEach(entity -> overlay.hover.add((BlockDisplay) entity));
            String effect =
                    destination.owner() >= 0 && destination.owner() != seat
                            ? "hint.ludo.capture"
                            : destination.id().startsWith("go")
                                    ? "hint.ludo.finish"
                                    : "hint.ludo.step";
            overlay.feedback =
                    Language.component(
                                    choices.size() > 1 ? "hint.ludo.stack" : "hint.ludo.move",
                                    "count",
                                    choices.size(),
                                    "pawn",
                                    Integer.parseInt(action[1]) % 4 + 1,
                                    "destination",
                                    GameWorld.coordinate("ludo", destination),
                                    "effect",
                                    Language.component(effect))
                            .colorIfAbsent(NamedTextColor.GREEN);
        } else {
            String key =
                    rolling()
                            ? "hint.rolling"
                            : !turn
                                    ? blockedHint()
                                    : room.board.legalActions(seat).contains("roll")
                                            ? "hint.roll"
                                            : "table.ludo.choose";
            if ("@menu".equals(hover)) key = "hint.menu";
            overlay.feedback = Language.component(key).colorIfAbsent(NamedTextColor.GOLD);
        }
        player.sendActionBar(overlay.feedback);
    }

    private void columnCursor(
            Player player, Overlay overlay, boolean reset, boolean turn, String hover) {
        Cell aimed = hover == null ? null : cells.get(hover);
        String column = aimed == null ? null : Integer.toString(aimed.x());
        if (!reset && Objects.equals(overlay.cell, column)) {
            if (++overlay.ticks % 10 == 0) player.sendActionBar(overlay.feedback);
            return;
        }
        overlay.cell = column;
        overlay.ticks = 0;
        Cell landing =
                aimed == null
                        ? null
                        : cells.values().stream()
                                .filter(c -> c.x() == aimed.x() && c.owner() < 0)
                                .min(Comparator.comparingInt(Cell::y))
                                .orElse(null);
        boolean legal =
                turn
                        && landing != null
                        && room.board
                                .legalActions(room.seat(player.getUniqueId()))
                                .contains("drop:" + column);
        if (legal) {
            Location at =
                    origin.clone().add((landing.x() - 3) * .28, .05 + landing.y() * .28 + .12, 0);
            if (overlay.hover.isEmpty()) {
                // Project past both .12-deep rack faces so the opaque frame cannot hide the
                // preview.
                for (double side : new double[] {-.132, .132}) {
                    overlay.hover.add(
                            block(
                                    at,
                                    Material.LIME_CONCRETE,
                                    side,
                                    -.132,
                                    0,
                                    .014,
                                    .278,
                                    .16,
                                    player));
                    overlay.hover.add(
                            block(
                                    at,
                                    Material.LIME_CONCRETE,
                                    0,
                                    side - .007,
                                    0,
                                    .25,
                                    .014,
                                    .16,
                                    player));
                }
            } else overlay.hover.forEach(entity -> entity.teleport(at));
        } else {
            overlay.hover.forEach(Entity::remove);
            overlay.hover.clear();
        }
        overlay.feedback =
                (!turn
                                ? Language.component(blockedHint())
                                : aimed == null
                                        ? Language.component("hint.column.aim")
                                        : Language.component(
                                                legal ? "hint.column" : "hint.column.full",
                                                "column",
                                                aimed.x() + 1))
                        .colorIfAbsent(legal ? NamedTextColor.GREEN : NamedTextColor.GRAY);
        player.sendActionBar(overlay.feedback);
    }

    private static void oldHoverAdd(Overlay overlay, Entity entity) {
        overlay.hover.add((BlockDisplay) entity);
    }

    private void ring(
            List<Entity> list, String id, Material material, Player viewer, double fraction) {
        Cell c = geometry.byId.get(id);
        if (c == null) return;
        double width = geometry.spacing * fraction, stroke = geometry.spacing * .065;
        Location at = origin.clone().add(geometry.x(c), .025, geometry.z(c));
        for (double side : new double[] {-width / 2, width / 2}) {
            list.add(block(at, material, side, 0, 0, stroke, .012, width, viewer));
            list.add(block(at, material, 0, 0, side, width, .012, stroke, viewer));
            if (viewer == null && boardAudience != null) {
                boardAudience.common(list.get(list.size()-2));
                boardAudience.common(list.getLast());
            }
        }
    }

    void clear(Player player) {
        if (handTable != null) handTable.clear(player);
        if (playingTable != null) playingTable.clear(player);
        Overlay old = overlays.remove(player.getUniqueId());
        if (old != null) old.remove();
    }

    @Override
    public void close() {
        pendingTurnSound = false;
        if (handTable != null) handTable.close();
        if (playingTable != null) playingTable.close();
        if (diceTray != null) diceTray.close();
        tokens.values().forEach(TokenView::remove);
        tokens.clear();
        handFurniture.forEach(Entity::remove);
        if (packedTable != null) packedTable.remove();
        if (packedBoard != null) boardAudience.remove(packedBoard);
        furniture.forEach(entity -> { if (playingTable != null) playingTable.audience.remove(entity); else if (boardAudience == null) entity.remove(); else boardAudience.remove(entity); });
        overlays.values().forEach(Overlay::remove);
        overlays.clear();
        if (boardAudience != null) lastMove.forEach(boardAudience::remove);
        else lastMove.forEach(Entity::remove);
    }
}
