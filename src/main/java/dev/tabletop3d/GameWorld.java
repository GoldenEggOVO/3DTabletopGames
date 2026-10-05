package dev.tabletop3d;


import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.rules.RuleViolation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.event.block.Action;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.*;

/** Public boards only: private card views never enter this renderer. */
final class GameWorld implements Listener, AutoCloseable {
    private final Tabletop3D plugin;
    private final NamespacedKey tag;
    private final Map<UUID, TableView> views = new HashMap<>();
    private final TableMaps maps;
    private final Map<UUID, Long> clicks = new HashMap<>();
    private org.bukkit.scheduler.BukkitTask pointerTask;
    private final Map<UUID, Pick> selections = new HashMap<>();
    private final Map<Integer, Set<Chunk>> tableChunks = new HashMap<>();

    record Pick(UUID room, long revision, String source, List<String> actions) {}

    record Layout(double xUnit, double zUnit, double midX, double midY) {
        double x(Cell cell) {
            return (cell.x() - midX) * xUnit;
        }

        double z(Cell cell) {
            return (cell.y() - midY) * zUnit;
        }

    }

    GameWorld(Tabletop3D plugin) {
        this.plugin = plugin;
        maps = new TableMaps(plugin);
        tag = new NamespacedKey("3dtabletop", "board-cell");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void initialize() {
        // Portable rooms own their world anchors; no dedicated dimension is created.
        pointerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pointers, 2, 2);
    }

    Location center(int index) {
        Room r =
                plugin.rooms.values().stream()
                        .filter(v -> v.table == index)
                        .findFirst()
                        .orElse(null);
        if (r != null && r.anchorWorld != null) {
            World w = Bukkit.getWorld(r.anchorWorld);
            if (w == null) throw new IllegalStateException("Table world is not loaded");
            return TablePlacement.snap(new Location(w, r.anchorX, r.anchorY, r.anchorZ), 2);
        }
        throw new IllegalStateException("Room has no usable world coordinates");
    }

    boolean atTableWorld(Player p, Room r) {
        return p.getWorld().equals(center(r.table).getWorld());
    }

    void anchor(Room r, Location location) {
        Location snapped = TablePlacement.snap(location, 2);
        UUID worldId = location.getWorld().getUID();
        for (Room existing : plugin.rooms.values())
            if (existing != r
                    && worldId.equals(existing.anchorWorld)
                    && TablePlacement.overlaps(
                            snapped.getX(),
                            snapped.getY(),
                            snapped.getZ(),
                            r.sideTray,
                            r.kind.equals("yacht") ? 2.45 : Set.of("mahjong", "color-eight", "doudizhu", "liars-bar", "texas-holdem").contains(r.kind) ? 1.5 : 1.125,
                            existing.anchorX,
                            existing.anchorY,
                            existing.anchorZ,
                            existing.sideTray,
                            existing.kind.equals("yacht") ? 2.45 : Set.of("mahjong", "color-eight", "doudizhu", "liars-bar", "texas-holdem").contains(existing.kind) ? 1.5 : 1.125))
                throw new IllegalArgumentException(
                        dev.tabletop3d.ui.MessageText.plain(
                                Language.component("error.table-overlap")));
        if (r.sideTray)
            for (int x = (int) Math.floor(snapped.getX() + 1.30);
                    x <= Math.floor(snapped.getX() + 2.70);
                    x++)
                for (int y = snapped.getBlockY(); y <= Math.floor(snapped.getY() + 1.8); y++)
                    for (int z = (int) Math.floor(snapped.getZ() - .70);
                            z <= Math.floor(snapped.getZ() + .70);
                            z++)
                        if (!snapped.getWorld().getBlockAt(x, y, z).isPassable())
                            throw new IllegalArgumentException(
                                    dev.tabletop3d.ui.MessageText.plain(
                                            Language.component("error.tray-space")));
        r.anchorWorld = worldId;
        r.anchorX = snapped.getX();
        r.anchorY = snapped.getY();
        r.anchorZ = snapped.getZ();
    }

    void platform(int index) {

        if (index < 0 || index > 127) throw new RuleViolation("error.invalid-table-number", "Invalid table number");
        Location c = center(index);
        Set<Chunk> chunks = new HashSet<>();
        for (int x = (c.getBlockX() - 4) >> 4; x <= ((c.getBlockX() + 4) >> 4); x++)
            for (int z = (c.getBlockZ() - 4) >> 4; z <= ((c.getBlockZ() + 4) >> 4); z++) {
                c.getWorld().getChunkAt(x, z).addPluginChunkTicket(plugin);
                chunks.add(c.getWorld().getChunkAt(x, z));
            }
        tableChunks.put(index, chunks);
    }

    Location seatLocation(Room room, int seat) {
        Location c = center(room.table);
        double angle = 2 * Math.PI * Math.max(0, seat) / Math.max(2, room.capacity);
        double dx = Math.sin(angle) * 2.25, dz = Math.cos(angle) * 2.25;
        if (room.kind.equals("chess") || room.kind.equals("xiangqi")) {
            dx = -dx;
            dz = -dz;
        }
        if (room.kind.equals("yacht")) {
            c.add(-.55, 0, 0);
            dx = Math.sin(angle) * 3.0;
        }
        if (room.kind.equals("ludo")) {
            int[] colors =
                    room.capacity == 2
                            ? new int[] {0, 2}
                            : room.capacity == 3 ? new int[] {0, 1, 2} : new int[] {0, 1, 2, 3};
            double a = -3 * Math.PI / 4 - colors[Math.floorMod(seat, colors.length)] * Math.PI / 2;
            dx = Math.sin(a) * 2.25;
            dz = Math.cos(a) * 2.25;
        }
        Location location =
                c.add(dx, 0, dz)
                        .setDirection(new Vector(-dx, TableGeometry.SURFACE + .05 - 1.62, -dz));
        if (!location.getBlock().isPassable()
                || !location.clone().add(0, 1, 0).getBlock().isPassable())
            throw new RuleViolation(
                    "error.the-seat-is-blocked-choose-a-more", "The seat is blocked. Choose a more open position.");
        return location;
    }

    static Layout layout(String kind, List<Cell> cells) {
        int minX = cells.stream().mapToInt(Cell::x).min().orElse(0),
                maxX = cells.stream().mapToInt(Cell::x).max().orElse(0);
        int minY = cells.stream().mapToInt(Cell::y).min().orElse(0),
                maxY = cells.stream().mapToInt(Cell::y).max().orElse(0);
        double unit = 1.80 / Math.max(maxX - minX + 1, maxY - minY + 1);
        if (kind.equals("yacht")) unit = .17; // Five wide dice slots stay inside the felt border.
        if (kind.equals("checkers")) {
            double scale =
                    Math.min(
                            1.80 / ((maxX - minX + 2) * .5), 1.80 / ((maxY - minY + 1) * .8660254));
            return new Layout(
                    .5 * scale, .8660254 * scale, (minX + maxX) / 2.0, (minY + maxY) / 2.0);
        }
        return new Layout(unit, unit, (minX + maxX) / 2.0, (minY + maxY) / 2.0);
    }

    void render(Room room) {
        if (room.board == null) {
            removeView(room.id);
            return;
        }
        selections
                .values()
                .removeIf(pick -> pick.room().equals(room.id) && pick.revision() != room.revision);
        TableView view = views.get(room.id);
        if (view == null) {
            view = new TableView(plugin, room, center(room.table), tag, maps);
            views.put(room.id, view);
        } else view.sync();
    }

    void countdown(Room room, long now) {
        TableView view = views.get(room.id);
        if (view != null) TableSounds.countdown(plugin, room, view.origin, now);
    }

    void sound(Room room, TableSounds.Cue cue) {
        TableView view = views.get(room.id);
        if (view != null) TableSounds.play(plugin, view.origin, cue, room.kind);
    }

    boolean rolling(Room room) {
        TableView view = views.get(room.id);
        return view != null && view.rolling();
    }


    MahjongAssist mahjongAssistance(Room room, int seat) {
        TableView view = views.get(room.id);
        return view == null ? null : view.mahjongAssistance(seat);
    }

    private String aimed(Player player, TableView view) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        String call = view.handCallHit(player, eye, direction);
        if (call != null) return "@call:" + call;
        String hand = view.handHit(player, eye, direction);
        if (hand != null) return (hand.startsWith("public:") ? "@tile:" : "@hand:") + hand;
        if (view.deckHit(eye, direction)) return "@draw";
        if (view.room.board instanceof dev.tabletop3d.rules.HandGame) {
            double distance =
                    TableGeometry.intersection(
                            eye.getY(), direction.getY(), view.origin.getY() + .03);
            if (distance < 0
                    || eye.getWorld()
                                    .rayTraceBlocks(
                                            eye,
                                            direction,
                                            Math.max(.001, distance - .035),
                                            FluidCollisionMode.NEVER,
                                            true)
                            != null) return null;
            Vector point =
                    eye.toVector()
                            .add(direction.clone().multiply(distance))
                            .subtract(view.origin.toVector());
            double half = 1.5;
            return Math.abs(point.getX()) < half && Math.abs(point.getZ()) < half ? "@menu" : null;
        }
        if (view.geometry.kind.equals("connectfour")) return view.verticalHit(eye, direction);
        double distance =
                TableGeometry.intersection(eye.getY(), direction.getY(), view.origin.getY() + .03);
        TableView.Hit piece = view.hitPiece(eye, direction);
        String cell = null;
        if (piece != null && (view.room.kind.equals("yacht") || distance < 0 || piece.distance() < distance)) {
            distance = piece.distance();
            cell = piece.cell();
        }
        if (distance < 0) return null;
        var obstacle =
                eye.getWorld()
                        .rayTraceBlocks(
                                eye,
                                direction,
                                Math.max(.001, distance - .035),
                                FluidCollisionMode.NEVER,
                                true);
        if (obstacle != null) return null;
        if (cell != null) return cell;
        if (view.room.kind.equals("yacht")) return null;
        Vector hit = eye.toVector().add(direction.multiply(distance));
        return view.geometry.hit(hit.getX() - view.origin.getX(), hit.getZ() - view.origin.getZ());
    }

    private void pointers() {
        views.values().forEach(TableView::tick);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Room room = plugin.room(player);
            TableView view = room == null ? null : views.get(room.id);
            if (view == null || !player.getWorld().equals(view.origin.getWorld())) continue;
            if (!plugin.allowed(player)
                    || player.getEyeLocation().distanceSquared(view.origin) > 36) {
                view.clear(player);
                continue;
            }
            Pick pick = selections.get(player.getUniqueId());
            if (pick != null
                    && (!pick.room().equals(room.id) || pick.revision() != room.revision)) {
                selections.remove(player.getUniqueId());
                pick = null;
            }
            if (plugin.comfort != null && plugin.comfort.focused(player))
                view.cursor(player, null, null);
            else view.cursor(player, pick, aimed(player, view));
        }
    }

    static int actualColor(Map<String, String> info, int owner) {
        if (owner < 0) return -1;
        try {
            String[] colors =
                    info.getOrDefault("colors", "").replace("[", "").replace("]", "").split(",");
            if (owner < colors.length && !colors[owner].isBlank())
                return Integer.parseInt(colors[owner].trim());
        } catch (NumberFormatException ignored) {
        }
        return owner;
    }

    static String coordinate(String kind, Cell cell) {
        if (kind.equals("ludo")) {
            if (cell.id().startsWith("sk"))
                return dev.tabletop3d.ui.MessageText.plain(
                        Language.component(
                                "board.track",
                                "number",
                                Integer.parseInt(cell.id().substring(2)) + 1));
            if (cell.id().startsWith("ld"))
                return dev.tabletop3d.ui.MessageText.plain(
                        Language.component(
                                "board.home-lane",
                                "number",
                                Character.digit(cell.id().charAt(4), 10) + 1));
            return dev.tabletop3d.ui.MessageText.plain(
                    Language.component(cell.id().startsWith("go") ? "board.finish" : "board.yard"));
        }
        if (kind.equals("chess")) return cell.id().toUpperCase(Locale.ROOT);
        if (Set.of("gomoku", "xiangqi", "draughts", "reversi", "go", "go9", "go13").contains(kind))
            return String.valueOf((char) ('A' + cell.x())) + (cell.y() + 1);
        if (kind.equals("yacht"))
            return dev.tabletop3d.ui.MessageText.plain(
                    Language.component("board.die", "number", cell.x() / 2 + 1));
        return cell.id();
    }

    static List<String> sourceActions(BoardGame board, int seat, String source) {
        if (seat < 0 || source == null) return List.of();
        Cell cell =
                board.cells().stream()
                        .filter(c -> c.id().equals(source) && c.owner() == seat)
                        .findFirst()
                        .orElse(null);
        if (cell == null) return List.of();
        return board.actionsForCell(seat, source).stream()
                .filter(
                        action -> {
                            String[] parts = action.split(":");
                            if (parts.length < 3 || !parts[0].equals("move")) return false;
                            if (!board.id().equals("ludo"))
                                return parts[1].equals(source);
                            try {
                                int plane = Integer.parseInt(parts[1]);
                                return plane / 4 == seat
                                        && cell.piece().contains(String.valueOf(plane % 4 + 1));
                            } catch (NumberFormatException ex) {
                                return false;
                            }
                        })
                .toList();
    }

    static List<String> destinationActions(Pick pick, String destination) {
        if (pick == null || destination == null) return List.of();
        return pick.actions().stream()
                .filter(
                        action -> {
                            String[] parts = action.split(":");
                            return parts.length >= 3 && parts[2].equals(destination);
                        })
                .toList();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void click(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getRightClicked().getPersistentDataContainer().has(tag)) {
            event.setCancelled(true);
            worldClick(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void use(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.LEFT_CLICK_BLOCK)
            if (worldClick(event.getPlayer(), event.getAction().isRightClick()))
                event.setCancelled(true);
    }

    @EventHandler
    public void swing(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING)
            worldClick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void attack(EntityDamageByEntityEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(tag)) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player player) worldClick(player);
        }
    }

    @EventHandler
    public void hanging(org.bukkit.event.hanging.HangingBreakEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(tag)) event.setCancelled(true);
    }

    private boolean worldClick(Player player) {
        return worldClick(player, false);
    }

    boolean worldClick(Player player, boolean rightClick) {
        if (!plugin.allowed(player)) return false;
        boolean focused = plugin.comfort != null && plugin.comfort.focused(player);
        Room room = plugin.room(player);
        TableView view = room == null ? null : views.get(room.id);
        if (view == null || room.board == null || !player.getWorld().equals(view.origin.getWorld()))
            return focused;
        if (player.isSneaking() && rightClick) {
            Location eye = player.getEyeLocation();
            if (!Double.isFinite(view.menuHit(eye, eye.getDirection()))) return focused;
            long now = System.nanoTime(), last = clicks.getOrDefault(player.getUniqueId(), 0L);
            if (now - last < 180_000_000L) return true;
            clicks.put(player.getUniqueId(), now);
            if (plugin.comfort != null && (focused || room.kind.equals("mahjong")))
                plugin.comfort.release(player);
            plugin.menus.room(player, room);
            return true;
        }
        if (focused) return true;
        String cell = aimed(player, view);
        if (room.kind.equals("mahjong")
                && room.phase == Room.Phase.PLAYING
                && (cell == null
                        || !cell.startsWith("@hand:")
                        || room.seat(player.getUniqueId()) != room.board.currentPlayer()))
            view.maintainMahjongPress(player);
        if (cell == null) return false;
        if (player.isSneaking()) return true;
        long now = System.nanoTime(), last = clicks.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 180_000_000L) return true;
        clicks.put(player.getUniqueId(), now);
        if (cell.equals("@menu") || cell.startsWith("@tile:")) return true;
        if (room.phase != Room.Phase.PLAYING) return true;
        if (cell.startsWith("@call:assist:")) {
            view.toggleMahjongAssistance(player, cell.substring("@call:assist:".length()));
            return true;
        }
        int seat = room.seat(player.getUniqueId());
        if (seat != room.board.currentPlayer()
                && !(room.board instanceof dev.tabletop3d.rules.GoGame go && go.scoring())) {
            player.sendActionBar(
                    Language.component("hint.not-turn").colorIfAbsent(NamedTextColor.GRAY));
            return true;
        }
        if (view.rolling()) {
            player.sendActionBar(
                    Language.component("hint.roll.wait").colorIfAbsent(NamedTextColor.GOLD));
            return true;
        }
        if (cell.equals("@call:dismiss")) {
            view.dismissHandCalls(player);
            return true;
        }
        pickCell(player, room, seat, cell);
        return true;
    }

    private void pickCell(Player player, Room room, int seat, String cell) {
        if (room.kind.equals("yacht")) {
            if (cell.startsWith("@score:")) {
                String action = "score:" + cell.substring(7);
                if (room.board.legalActions(seat).contains(action)) execute(player, room, List.of(action));
            } else if (cell.startsWith("die")) {
                String action = "hold:" + cell;
                if (room.board.legalActions(seat).contains(action)) execute(player, room, List.of(action));
            } else if (cell.equals("@roll") && room.board.legalActions(seat).contains("roll"))
                execute(player, room, List.of("roll"));
            return;
        }
        if (cell.startsWith("@call:playing:")) {
            TableView view=views.get(room.id);
            String action=view==null ? null : view.playingAction(player,cell.substring(14));
            if (action!=null) execute(player,room,List.of(action));
            return;
        }
        if (cell.startsWith("@call:card:") && room.kind.equals("color-eight")) {
            String action = cell.substring(11);
            if (room.board.legalActions(seat).contains(action))
                execute(player, room, List.of(action));
            return;
        }
        if (cell.startsWith("@call:") && room.kind.equals("mahjong")) {
            String group = cell.substring(6);
            TableView view = views.get(room.id);
            MahjongAssist assistance = view == null ? null : view.mahjongAssistance(seat);
            List<String> legal = room.board.legalActions(seat);
            if (assistance != null) legal = assistance.visibleActions(legal);
            if (view != null && view.expandHandCall(player, group)) return;
            if (group.startsWith("choice:")) {
                String action = group.substring(7);
                if (legal.contains(action))
                    execute(player, room, List.of(action));
                return;
            }
            List<String> choices =
                    MahjongControls.groups(legal)
                            .getOrDefault(group, List.of());
            if (!choices.isEmpty()) execute(player, room, choices);
            return;
        }
        if (cell.equals("@draw")) {
            if (room.board.legalActions(seat).contains("draw"))
                execute(player, room, List.of("draw"));
            else
                player.sendActionBar(
                        Language.component("hint.hand.draw-unavailable")
                                .colorIfAbsent(NamedTextColor.GRAY));
            return;
        }
        if (cell.startsWith("@hand:") && room.board instanceof dev.tabletop3d.rules.HandGame hand) {
            String id = cell.substring(6);
            if (hand.hand(seat).stream().noneMatch(piece -> piece.id().equals(id))) return;
            if (room.kind.equals("mahjong")) {
                TableView view = views.get(room.id);
                String action = view == null ? null : view.mahjongHandAction(player, id);
                if (action != null) execute(player, room, List.of(action));
                else
                    player.sendActionBar(
                            Language.component("hint.unavailable")
                                    .colorIfAbsent(NamedTextColor.GRAY));
                return;
            }
            TableView view = views.get(room.id);
            String action = view == null ? null : view.cardHandAction(player, id);
            if (room.board instanceof dev.tabletop3d.rules.SelectedHandGame) return;
            if (action != null) execute(player, room, List.of(action));
            else
                player.sendActionBar(
                        Language.component("hint.unavailable").colorIfAbsent(NamedTextColor.GRAY));
            return;
        }
        if (cell.equals("@roll")) {
            if (room.board.legalActions(seat).contains("roll"))
                execute(player, room, List.of("roll"));
            else
                player.sendActionBar(
                        Language.component("hint.roll.unavailable")
                                .colorIfAbsent(NamedTextColor.GOLD));
            return;
        }
        List<String> related = room.board.actionsForCell(seat, cell);
        if (related.size() == 1
                && (related.getFirst().startsWith("drop:")
                        || related.getFirst().startsWith("place:")
                        || related.getFirst().startsWith("dead:")
                        || related.getFirst().startsWith("hold:"))) {
            execute(player, room, related);
            return;
        }
        Pick pick = selections.get(player.getUniqueId());
        if (pick != null && (!pick.room().equals(room.id) || pick.revision() != room.revision)) {
            selections.remove(player.getUniqueId());
            pick = null;
        }
        List<String> destinations = destinationActions(pick, cell);
        if (!destinations.isEmpty()) {
            execute(player, room, destinations);
            return;
        }
        List<String> sources = sourceActions(room.board, seat, cell);
        if (room.kind.equals("ludo") && !sources.isEmpty()) {
            execute(player, room, sources);
            return;
        }
        if (!sources.isEmpty()) {
            if (pick != null && pick.source().equals(cell)) {
                selections.remove(player.getUniqueId());
                player.sendActionBar(
                        Language.component("hint.cancelled").colorIfAbsent(NamedTextColor.GRAY));
                return;
            }
            selections.put(
                    player.getUniqueId(),
                    new Pick(room.id, room.revision, cell, List.copyOf(sources)));
            render(room);
            TableSounds.select(plugin, player);
            player.sendActionBar(
                    Language.component("hint.selected").colorIfAbsent(NamedTextColor.GREEN));
        } else if (room.kind.equals("ludo")
                && room.board.legalActions(seat).contains("roll"))
            plugin.tell(player, Language.component("chat.roll-first"));
        else if (Set.of("gomoku", "go", "go9", "go13", "reversi", "connectfour")
                .contains(room.kind))
            player.sendActionBar(
                    Language.component(
                                    room.kind.equals("connectfour")
                                            ? "hint.column.unavailable"
                                            : "hint.position.unavailable")
                            .colorIfAbsent(NamedTextColor.RED));
        else
            player.sendActionBar(
                    Language.component(pick == null ? "chat.select-first" : "chat.destination")
                            .colorIfAbsent(NamedTextColor.GRAY));
    }

    private void execute(Player player, Room room, List<String> choices) {
        selections.remove(player.getUniqueId());
        if (choices.size() > 1) {
            render(room);
            plugin.menus.boardChoices(player, room, choices, 0);
            return;
        }
        try {
            if (!plugin.allowed(player)) return;
            room.requireAction(player.getUniqueId(), room.revision);
            // Keep the same identity/revision gate as menu actions, but do not
            // force a menu to cover the board after each successful world click.
            plugin.apply(
                    room,
                    room.seat(player.getUniqueId()),
                    new com.google.gson.JsonPrimitive(choices.getFirst()),
                    null);
        } catch (IllegalArgumentException ex) {
            plugin.tell(player, Language.component("chat.changed"));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        clearSelection(e.getPlayer());
    }

    @EventHandler
    public void changeWorld(PlayerChangedWorldEvent e) {
        clearSelection(e.getPlayer());
    }

    void clearSelection(Player player) {
        clicks.remove(player.getUniqueId());
        views.values().forEach(view -> view.clear(player));
        Pick old = selections.remove(player.getUniqueId());
        if (old != null) {
            Room room = plugin.rooms.get(old.room());
            if (room != null) render(room);
        }
    }

    private void removeView(UUID room) {
        TableView old = views.remove(room);
        if (old != null) old.close();
    }

    void remove(Room room) {
        removeView(room.id);
        selections.values().removeIf(pick -> pick.room().equals(room.id));
        Set<Chunk> chunks = tableChunks.remove(room.table);
        if (chunks != null)
            for (Chunk chunk : chunks)
                if (tableChunks.values().stream().noneMatch(set -> set.contains(chunk)))
                    chunk.removePluginChunkTicket(plugin);
    }

    @Override
    public void close() {
        if (pointerTask != null) pointerTask.cancel();
        views.values().forEach(TableView::close);
        views.clear();
        selections.clear();
        clicks.clear();
        tableChunks.clear();
        for (World w : Bukkit.getWorlds()) w.removePluginChunkTickets(plugin);
    }
}
