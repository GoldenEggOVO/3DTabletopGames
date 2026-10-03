package dev.tabletop3d;

import com.google.gson.*;

import dev.tabletop3d.rules.*;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

public final class Tabletop3D extends JavaPlugin
        implements Listener, CommandExecutor, TabCompleter {
    static final List<String> GAMES =
            List.of(
                    "xiangqi",
                    "gomoku",
                    "chess",
                    "ludo",
                    "checkers",
                    "draughts",
                    "reversi",
                    "go",
                    "go9",
                    "go13",
                    "connectfour",
                    "color-eight",
                    "mahjong",
                    "doudizhu",
                    "liars-bar",
                    "texas-holdem",
                    "yacht");

    static String gameName(String kind) {
        return dev.tabletop3d.ui.MessageText.plain(RoomText.game(kind));
    }

    final Map<UUID, Room> rooms = new LinkedHashMap<>();
    final Map<UUID, Location> returns = new HashMap<>();
    final SecureRandom random = new SecureRandom();
    GameMenus menus;
    GameWorld arena;
    TableComfort comfort;
    TableLobby tableLobby;
    TabletopPack pack;
    BoardOccupancy coordinator;
    boolean stopping = false;
    private boolean loaded = false;
    private int pulse = 0;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            Language.load(this);
            pack = new TabletopPack(this);
            Bukkit.getPluginManager().registerEvents(pack, this);
            menus = new GameMenus(this, new BoardWindow(this));
            arena = new GameWorld(this);
            coordinator = new BoardOccupancy();
            comfort = new TableComfort(this);
            tableLobby = new TableLobby(this);
            tableLobby.start();
            Objects.requireNonNull(getCommand("3dtabletop")).setExecutor(this);
            getCommand("3dtabletop").setTabCompleter(this);
            Bukkit.getPluginManager().registerEvents(this, this);
            Bukkit.getScheduler()
                    .runTask(
                            this,
                            () -> {
                                try {
                                    arena.initialize();
                                    restore();
                                    loaded = true;
                                } catch (Exception ex) {
                                    getLogger()
                                            .log(
                                                    java.util.logging.Level.SEVERE,
                                                    "Could not restore the board world or rooms;"
                                                        + " original records retained",
                                                    ex);
                                    Bukkit.getPluginManager().disablePlugin(this);
                                }
                            });
            Bukkit.getScheduler().runTaskTimer(this, this::tick, 20, 20);
            getLogger().info("3dtabletop enabled.");
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not initialize table rooms", ex);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        stopping = true;
        save();
        if (tableLobby != null) tableLobby.close();
        if (coordinator != null) coordinator.close();
        if (comfort != null) comfort.close();
        if (menus != null) menus.close();
        if (arena != null) arena.close();
        if (pack != null) pack.close();
    }

    public void suspendView(Player player) {
        if (comfort != null) comfort.release(player);
        if (menus != null) menus.forget(player);
        if (arena != null) arena.clearSelection(player);
    }

    boolean mainMenuAvailable() {
        return Bukkit.getPluginCommand("servermenu:servermenu") != null;
    }

    boolean allowed(Player player) {
        return player.isOnline() && player.hasPermission("3dtabletop.use");
    }

    Room room(Player p) {
        return room(p.getUniqueId());
    }

    Room room(UUID p) {
        return rooms.values().stream()
                .filter(r -> r.seat(p) >= 0 && r.phase != Room.Phase.ABORTED)
                .findFirst()
                .orElse(null);
    }

    void tell(Player p, Component message) {
        p.sendMessage(Language.component("chat.prefix", "message", message));
    }

    void announce(Room r, Component message) {
        for (Room.Seat s : r.seats) {
            Player p = Bukkit.getPlayer(s.id());
            if (p != null && !s.bot()) tell(p, message);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload-language")) {
            if (sender instanceof Player player
                    && (!allowed(player) || !player.hasPermission("3dtabletop.admin"))) {
                tell(player, Language.component("chat.permission"));
                return true;
            }
            try {
                var config = new org.bukkit.configuration.file.YamlConfiguration();
                config.load(new File(getDataFolder(), "config.yml"));
                boolean changed =
                        Language.reload(
                                getDataFolder().toPath().resolve("languages"),
                                config.getString("language", "en_US"),
                                getLogger()::warning);
                if (changed && menus != null) menus.languageChanged();
                sender.sendMessage(
                        Language.component(changed ? "language.reloaded" : "language.failed"));
            } catch (Exception ex) {
                getLogger().warning("Cannot reload languages: " + ex.getMessage());
                sender.sendMessage(Language.component("language.failed"));
            }
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Language.component("chat.console"));
            if (args.length > 0 && args[0].equals("status"))
                sender.sendMessage(
                        Language.component(
                                "chat.status",
                                "rooms",
                                rooms.size()));
            return true;
        }
        if (!allowed(p)) {
            tell(p, Language.component("chat.permission"));
            return true;
        }
        try {
            String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "menu" -> menus.main(p);
                case "click" -> {
                    if (args.length == 3) menus.handle(p, args[1] + " " + args[2]);
                }
                case "move" -> {
                    if (args.length == 2) {
                        Room r = requireRoom(p);
                        action(p, r, r.revision, new JsonPrimitive(args[1]));
                    }
                }
                case "resume" -> {
                    Room r = room(p);
                    if (r == null) {
                        menus.main(p);
                    } else resume(p, r);
                }
                case "create" -> {
                    if (args.length < 2) menus.main(p);
                    else
                        create(
                                p,
                                args[1],
                                args.length > 2
                                        ? Integer.parseInt(args[2])
                                        : defaultCapacity(args[1]));
                }
                case "join" -> {
                    if (args.length < 2) menus.main(p);
                    else join(p, find(args[1]));
                }
                case "ready" -> {
                    Room r = requireRoom(p);
                    ready(p, r);
                }
                case "bots" -> {
                    Room r = requireRoom(p);
                    startWithBots(p, r);
                }
                case "leave" -> {
                    menus.confirmLeave(p);
                }
                case "rematch" -> rematch(p, requireRoom(p));
                case "rules" -> {
                    Room r = room(p);
                    menus.rules(
                            p,
                            args.length > 1 && GAMES.contains(args[1])
                                    ? args[1]
                                    : r == null ? "gomoku" : r.kind);
                }
                default -> menus.main(p);
            }
        } catch (IllegalArgumentException ex) {
            tell(p, Language.error(ex));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1
                && "reload-language".startsWith(args[0].toLowerCase(Locale.ROOT))
                && (!(sender instanceof Player player)
                        || allowed(player) && player.hasPermission("3dtabletop.admin"))) {
            var suggestions =
                    new ArrayList<>(
                            sender instanceof Player player
                                    ? CommandSuggestions.complete(
                                            args, rooms.values(), room(player), false, true)
                                    : CommandSuggestions.complete(
                                            args, rooms.values(), null, true, true));
            suggestions.add("reload-language");
            return suggestions;
        }
        if (sender instanceof Player player) {
            Room own = room(player);
            if (args.length == 2
                    && args[0].equalsIgnoreCase("move")
                    && own != null
                    && own.board instanceof HandGame
                    && own.seat(player.getUniqueId()) != own.turn()) return List.of();
            return CommandSuggestions.complete(args, rooms.values(), own, false, allowed(player));
        }
        return CommandSuggestions.complete(args, rooms.values(), null, true, true);
    }

    @EventHandler
    public void commandSuggestions(PlayerCommandSendEvent event) {
        CommandSuggestions.hideDuplicateRoot(event.getCommands());
    }

    private void requireLiveRoom(Room room) {
        if (rooms.get(room.id) != room) throw new RuleViolation("error.room-closed", "Room closed");
    }

    Room requireRoom(Player p) {
        Room r = room(p);
        if (r == null) throw new RuleViolation("error.you-have-not-joined-a-room", "You have not joined a room");
        return r;
    }

    Room find(String text) {
        return rooms.values().stream()
                .filter(r -> r.id.toString().startsWith(text))
                .findFirst()
                .orElseThrow(() -> new RuleViolation("error.room-closed", "Room closed"));
    }

    static int defaultCapacity(String kind) {
        return switch (kind) {
            case "checkers", "texas-holdem" -> 6;
            case "doudizhu" -> 3;
            case "liars-bar" -> 4;
            case "ludo", "color-eight", "mahjong" -> 4;
            default -> 2;
        };
    }

    static boolean capacityValid(String kind, int n) {
        return switch (kind) {
            case "mahjong" -> n == 4;
            case "doudizhu" -> n == 3;
            case "liars-bar" -> n >= 2 && n <= 4;
            case "texas-holdem" -> n >= 2 && n <= 6;
            case "checkers" -> Set.of(2, 3, 4, 6).contains(n);
            case "color-eight" -> n >= 2 && n <= 5;
            case "ludo", "yacht" -> n >= 2 && n <= 4;
            case "gomoku",
                    "xiangqi",
                    "chess",
                    "draughts",
                    "reversi",
                    "go",
                    "go9",
                    "go13",
                    "connectfour" ->
                    n == 2;
            default -> false;
        };
    }

    void create(Player p, String kind, int capacity) {
        create(p, kind, capacity, Map.of());
    }

    void create(Player p, String kind, int capacity, Map<String, String> options) {
        if (!allowed(p))
            throw new RuleViolation("error.board-game-permission-required", "Board game permission required");
        if (!coordinator.reserve(p.getUniqueId(), kind))
            throw new RuleViolation("error.leave-your-current-game-first", "Leave your current game first");
        try {
            createReserved(p, kind, capacity, options);
        } catch (RuntimeException | Error ex) {
            try {
                Room partial = room(p);
                if (partial != null) remove(partial);
                save();
            } finally {
                coordinator.release(p.getUniqueId(), kind);
            }
            throw ex;
        }
    }

    void createReserved(Player p, String kind, int capacity) {
        createReserved(p, kind, capacity, Map.of());
    }

    void createReserved(Player p, String kind, int capacity, Map<String, String> options) {
        if (pack != null) pack.require(p, kind);
        if (!capacityValid(kind, capacity))
            throw new RuleViolation("error.unsupported-game-or-player-count", "Unsupported game or player count");
        if (room(p) != null)
            throw new RuleViolation("error.leave-your-current-game-first", "Leave your current game first");
        if (rooms.size() >= getConfig().getInt("max-rooms", 12))
            throw new RuleViolation(
                    "error.room-limit-reached-join-an-existing-room", "Room limit reached; join an existing room");
        int index = 0;
        Set<Integer> used = new HashSet<>();
        rooms.values().forEach(r -> used.add(r.table));
        while (used.contains(index)) index++;
        Room r = new Room(UUID.randomUUID(), kind, capacity, random.nextLong(), index, options);
        r.sideTray = kind.equals("ludo");
        arena.anchor(r, p.getLocation());
        r.join(p.getUniqueId(), p.getName());
        rooms.put(r.id, r);
        try {
            for (int seat = 0; seat < r.capacity; seat++) arena.seatLocation(r, seat);
            arena.platform(index);
            r.board = r.newBoard();
            arena.render(r);
        } catch (RuntimeException ex) {
            arena.remove(r);
            rooms.remove(r.id);
            throw ex;
        }
        if (!enterArena(p, r)) {
            remove(r);
            returns.remove(p.getUniqueId());
            throw new RuleViolation(
                    "error.teleport-cancelled-room-was-not-created-remove",
                    "Teleport cancelled; room was not created. Remove the teleport restriction and try again.");
        }
        save();
        if (comfort != null) comfort.sync();
        menus.room(p, r);
    }

    void join(Player p, Room r) {
        if (!allowed(p)) return;
        requireLiveRoom(r);
        if (r.seat(p.getUniqueId()) >= 0) {
            resume(p, r);
            return;
        }
        if (!coordinator.reserve(p.getUniqueId(), r.kind))
            throw new RuleViolation("error.leave-your-current-game-first", "Leave your current game first");
        try {
            joinReserved(p, r);
        } catch (RuntimeException | Error ex) {
            try {
                if (r.seats.removeIf(seat -> seat.id().equals(p.getUniqueId()))) {
                    r.offline.remove(p.getUniqueId());
                    r.ready.remove(p.getUniqueId());
                    r.revision++;
                    save();
                }
            } finally {
                coordinator.release(p.getUniqueId(), r.kind);
            }
            throw ex;
        }
    }

    void joinReserved(Player p, Room r) {
        if (!allowed(p)) return;
        requireLiveRoom(r);
        if (pack != null) pack.require(p, r.kind);
        Room old = room(p);
        if (old != null && old != r)
            throw new RuleViolation("error.leave-your-current-game-first", "Leave your current game first");
        boolean already = r.seat(p.getUniqueId()) >= 0;
        r.join(p.getUniqueId(), p.getName());
        if (!enterArena(p, r)) {
            if (!already) {
                r.seats.removeIf(s -> s.id().equals(p.getUniqueId()));
                r.offline.remove(p.getUniqueId());
                r.revision++;
                returns.remove(p.getUniqueId());
                save();
            }
            throw new RuleViolation(
                    "error.teleport-cancelled-could-not-take-a-seat", "Teleport cancelled; could not take a seat. Remove the restriction and try again.");
        }
        announce(r, Language.component("chat.joined", "player", p.getName()));
        save();
        if (comfort != null) comfort.sync();
        menus.room(p, r);
    }

    boolean enterArena(Player p, Room r) {
        if (!allowed(p)) return false;
        requireLiveRoom(r);
        if (pack != null) pack.require(p, r.kind);
        if (r.seat(p.getUniqueId()) < 0)
            throw new RuleViolation("error.you-have-not-joined-a-room", "You have not joined a room");
        if (!arena.atTableWorld(p, r))
            returns.putIfAbsent(p.getUniqueId(), p.getLocation().clone());
        if (!p.teleport(arena.seatLocation(r, r.seat(p.getUniqueId())))) {
            r.offline.putIfAbsent(p.getUniqueId(), System.currentTimeMillis());
            return false;
        }
        r.offline.remove(p.getUniqueId());
        return true;
    }

    void resume(Player p, Room r) {
        if (!allowed(p)) return;
        requireLiveRoom(r);
        if (!enterArena(p, r)) {
            tell(p, Language.component("chat.resume.cancelled"));
            menus.room(p, r);
            return;
        }
        menus.room(p, r);
    }

    void ready(Player p, Room r) {
        if (!allowed(p)) return;
        requireLiveRoom(r);
        if (pack != null) pack.require(p, r.kind);
        if (r.phase != Room.Phase.LOBBY || r.seat(p.getUniqueId()) < 0) return;
        if (!r.ready.add(p.getUniqueId())) r.ready.remove(p.getUniqueId());
        r.revision++;
        if (r.seats.size() == r.capacity
                && r.seats.stream().allMatch(s -> s.bot() || r.ready.contains(s.id()))) start(r);
        else {
            save();
            menus.room(p, r);
        }
    }

    void startWithBots(Player p, Room r) {
        if (!allowed(p)) return;
        requireLiveRoom(r);
        if (pack != null) pack.require(p, r.kind);
        if (r.phase != Room.Phase.LOBBY || !r.host(p.getUniqueId()))
            throw new RuleViolation("error.only-the-host-can-add-bots", "Only the host can add bots");
        if (r.seats.stream()
                .filter(s -> !s.bot() && !s.id().equals(p.getUniqueId()))
                .anyMatch(s -> !r.ready.contains(s.id())))
            throw new RuleViolation("error.wait-for-the-other-players-to-ready", "Wait for the other players to ready");
        r.fillBots();
        start(r);
    }

    void start(Room r) {
        if (!r.restoring && pack != null)
            for (Room.Seat seat : r.seats)
                if (!seat.bot()) {
                    Player player = Bukkit.getPlayer(seat.id());
                    if (player != null) pack.require(player, r.kind);
                }
        if (!prepareSeats(r)) return;
        r.phase = Room.Phase.STARTING;
        r.busy = true;
        r.changed = System.currentTimeMillis();

        try {
            r.board = r.newBoard();
            for (JsonElement e : r.history) {
                JsonObject j = e.getAsJsonObject();
                r.board.apply(j.get("seat").getAsInt(), j.get("action").getAsString());
            }
            r.phase = r.completed || r.board.finished() ? Room.Phase.FINISHED : Room.Phase.PLAYING;
            r.busy = false;
            arena.render(r);
            save();
            announce(r, Language.component("chat.start"));
            if (!r.restoring && r.phase == Room.Phase.PLAYING) {
                arena.sound(r, TableSounds.START);
                arena.turnSound(r);
            }
        } catch (RuntimeException ex) {
            r.busy = false;
            if (r.restoring) throw ex;
            pause(r, "chat.pause.rules");
            getLogger().log(java.util.logging.Level.WARNING, "Board start failed", ex);
        }
    }

    boolean prepareSeats(Room room) {
        List<Room.Seat> previous = List.copyOf(room.seats);
        room.prepareSeats();
        if (room.restoring || previous.equals(room.seats)) return true;
        Map<Player, Location> moved = new LinkedHashMap<>();
        boolean success = false;
        try {
            for (int seat = 0; seat < room.seats.size(); seat++) {
                Room.Seat member = room.seats.get(seat);
                if (member.bot() || previous.indexOf(member) == seat) continue;
                Player player = Bukkit.getPlayer(member.id());
                if (player == null || !allowed(player) || !arena.atTableWorld(player, room))
                    continue;
                Location target = arena.seatLocation(room, seat);
                suspendView(player);
                Location before = player.getLocation().clone();
                if (!player.teleport(target)) return false;
                moved.put(player, before);
            }
            success = true;
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        } finally {
            if (!success) {
                room.seats.clear();
                room.seats.addAll(previous);
                moved.forEach(Player::teleport);
                room.ready.clear();
                room.revision++;
                if (room.board == null) room.board = room.newBoard();
                arena.render(room);
                save();
                announce(room, Language.component("chat.seat-change-blocked"));
                showRoomToHumans(room);
            }
        }
    }

    void action(Player p, Room r, long revision, JsonElement action) {
        if (!allowed(p)) return;
        requireLiveRoom(r);
        if (pack != null) pack.require(p, r.kind);
        if (!arena.atTableWorld(p, r))
            throw new RuleViolation(
                    "error.use-3dtabletop-resume-to-return-to-the",
                    "Use /3dtabletop resume to return to the table world first");
        r.requireAction(p.getUniqueId(), revision);
        apply(r, r.seat(p.getUniqueId()), action, p);
    }

    void apply(Room r, int seat, JsonElement action, Player source) {
        requireLiveRoom(r);
        if (r.phase != Room.Phase.PLAYING || r.busy) return;
        if (arena.rolling(r)) {
            if (source != null) tell(source, Language.component("hint.roll.wait"));
            return;
        }
        if (!ReplayBudget.allows(r.history, action)) {
            finish(r, "result.limit");
            save();
            return;
        }

        try {
            List<Cell> before = r.board.cells();
            var mahjongBefore =
                    r.kind.equals("mahjong") ? TableSounds.mahjongState((HandGame) r.board) : null;
            boolean cardGame = Set.of("doudizhu", "liars-bar", "texas-holdem").contains(r.kind);
            var cardBefore = cardGame ? ((HandGame) r.board).publicInfo() : Map.<String, String>of();
            boolean canBeat = !r.kind.equals("doudizhu") || !action.getAsString().equals("pass")
                    || r.board.legalActions(seat).stream().anyMatch(a -> a.startsWith("play:"));
            int previousTurn = r.turn();
            r.board.apply(seat, action.getAsString());
            r.event(seat, action);
            r.revision++;
            if (!r.kind.equals("color-eight")
                    || !Set.of("draw", "choose").contains(action.getAsString().split(":")[0])
                    || r.turn() != previousTurn) r.changed = System.currentTimeMillis();
            arena.render(r);
            if (!r.restoring) {
                if (mahjongBefore != null)
                    for (var cue :
                            TableSounds.mahjong(
                                    action.getAsString(),
                                    mahjongBefore,
                                    TableSounds.mahjongState((HandGame) r.board)))
                        arena.sound(r, cue);
                else if (cardGame)
                    for (var cue : TableSounds.cards(r.kind, action.getAsString(), cardBefore,
                            ((HandGame) r.board).publicInfo(), canBeat)) arena.sound(r, cue);
                else
                    arena.sound(
                            r,
                            TableSounds.move(
                                    r.kind, seat, action.getAsString(), before, r.board.cells()));
            }
            if (r.board.finished()) finish(r, r.board.outcome());
            else if (!r.restoring && r.turn() != previousTurn) arena.turnSound(r);
            save();
            if (source != null && source.isOnline()) menus.room(source, r);
        } catch (IllegalArgumentException ex) {
            if (source != null) tell(source, Language.error(ex));
        }
    }

    void finish(Room r, String result) {
        boolean first = r.phase != Room.Phase.FINISHED;
        r.phase = Room.Phase.FINISHED;
        r.completed = true;
        r.result = result;
        r.ready.clear();
        r.changed = System.currentTimeMillis();
        if (first && !r.restoring && !(r.kind.equals("mahjong") && r.board.finished()))
            arena.sound(r, result.startsWith("winner:") ? TableSounds.WIN : TableSounds.DRAW);
        announce(r, Language.component("chat.finished", "result", RoomText.outcome(r, result)));
        onMain(() -> showRoomToHumans(r));
    }

    void showRoomToHumans(Room r) {
        if (!rooms.containsKey(r.id)) return;
        for (Room.Seat s : r.seats) {
            Player p = Bukkit.getPlayer(s.id());
            if (!s.bot() && p != null && allowed(p) && arena.atTableWorld(p, r)) menus.room(p, r);
        }
    }

    void rematch(Player p, Room r) {
        if (!allowed(p)) return;
        requireLiveRoom(r);
        if (!arena.atTableWorld(p, r)) return;
        if (RoundActions.rematchReady(r, p.getUniqueId())) {
            for (Room.Seat s : r.seats) {
                Player other = Bukkit.getPlayer(s.id());
                if (!s.bot() && other != null) suspendView(other);
            }
            arena.remove(r);
            RoundActions.fresh(r, random.nextLong());
            arena.platform(r.table);
            start(r);
            showRoomToHumans(r);
        } else {
            announce(r, Language.component("chat.rematch.ready", "player", p.getName()));
            save();
            showRoomToHumans(r);
        }
    }

    void leave(Player p) {
        Room r = room(p);
        suspendView(p);
        if (r != null) {
            if (r.phase == Room.Phase.PLAYING
                    || r.phase == Room.Phase.STARTING
                    || r.phase == Room.Phase.PAUSED) {
                abort(
                        r,
                        "result.left",
                        Language.component("result.left", "player", p.getName()));
            } else if (r.phase == Room.Phase.FINISHED) {
                remove(r);
            } else {
                r.seats.removeIf(s -> s.id().equals(p.getUniqueId()));
                if (p.getUniqueId().equals(r.owner))
                    r.owner = r.seats.isEmpty() ? null : r.seats.getFirst().id();
                r.offline.remove(p.getUniqueId());
                r.ready.remove(p.getUniqueId());
                r.revision++;
                if (r.seats.stream().noneMatch(s -> !s.bot())) remove(r);
            }
        }
        if (r != null) coordinator.release(p.getUniqueId(), r.kind);
        returns.remove(p.getUniqueId());
        save();
        if (comfort != null) comfort.sync();
        menus.main(p);
    }

    void abort(Room r, String reason) {
        abort(r, reason, Language.component(reason));
    }

    void abort(Room r, String reason, Component message) {
        r.phase = Room.Phase.ABORTED;
        r.result = reason;
        announce(r, message);
        remove(r);
        save();
    }

    void pause(Room r, String reason) {
        r.phase = Room.Phase.PAUSED;
        r.busy = false;
        r.result = reason;
        announce(r, Language.component(reason));
        save();
    }

    void remove(Room r) {
        for (Room.Seat seat : r.seats) if (!seat.bot()) coordinator.release(seat.id(), r.kind);
        arena.remove(r);
        rooms.remove(r.id);
        for (Room.Seat s : r.seats) {
            Player p = Bukkit.getPlayer(s.id());
            if (p != null) suspendView(p);
        }
    }

    void onMain(Runnable action) {
        if (!stopping && isEnabled()) Bukkit.getScheduler().runTask(this, action);
    }

    long turnWaitMillis(Room room) {
        int turn = room.turn();
        if (turn < 0 || turn >= room.seats.size()) return 0;
        Room.Seat seat = room.seats.get(turn);
        return seat.bot()
                ? 2000
                : room.kind.equals("color-eight")
                        ? 30000
                        : room.offline.containsKey(seat.id())
                                ? 5000
                                : getConfig().getLong("turn-seconds", 60) * 1000L;
    }

    void tick() {
        if (!loaded) return;
        if (comfort != null) comfort.sync();
        long now = System.currentTimeMillis();
        pulse++;
        for (Room r : new ArrayList<>(rooms.values())) {
            if (r.phase == Room.Phase.PAUSED) continue;
            for (Room.Seat s : r.seats)
                if (!s.bot()) {
                    Player p = Bukkit.getPlayer(s.id());
                    boolean present =
                            p != null
                                    && allowed(p)
                                    && arena.atTableWorld(p, r)
                                    && (pack == null || pack.canPlay(p, r.kind));
                    if (present) r.offline.remove(s.id());
                    else r.offline.putIfAbsent(s.id(), now);
                }
            if (r.offline.values().stream()
                    .anyMatch(
                            t -> now - t > getConfig().getLong("reconnect-seconds", 120) * 1000L)) {
                abort(r, "chat.abort.offline");
                continue;
            }
            if (r.phase == Room.Phase.LOBBY
                    && now - r.changed > getConfig().getLong("idle-room-minutes", 30) * 60_000L) {
                abort(r, "chat.abort.idle");
                continue;
            }
            if (r.phase == Room.Phase.FINISHED && now - r.changed > 600_000L) {
                remove(r);
                continue;
            }
            if (r.phase != Room.Phase.PLAYING || r.busy) continue;
            int turn = r.turn();
            if (turn < 0 || turn >= r.seats.size()) continue;
            Room.Seat s = r.seats.get(turn);
            long wait = turnWaitMillis(r);
            arena.countdown(r, now);
            if (!s.bot()
                    && pack != null
                    && pack.mode == TabletopPack.Mode.RESOURCE_PACK
                    && TabletopPack.supported(r.kind)) {
                Player player = Bukkit.getPlayer(s.id());
                if (player == null || !pack.canPlay(player, r.kind)) continue;
            }
            String choice =
                    TurnPolicy.choose(
                            r.board,
                            turn,
                            s.bot(),
                            now - r.changed,
                            wait,
                            random,
                            !r.offline.containsKey(s.id()),
                            arena.mahjongAssistance(r, turn));
            if (choice != null) apply(r, turn, new JsonPrimitive(choice), null);
        }
        if (pulse % 30 == 0) save();
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        suspendView(e.getPlayer());
        Room r = room(e.getPlayer());
        if (r != null)
            r.offline.putIfAbsent(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        save();
    }

    @EventHandler
    public void world(PlayerChangedWorldEvent e) {
        suspendView(e.getPlayer());
        Room r = room(e.getPlayer());
        if (r != null && !arena.atTableWorld(e.getPlayer(), r))
            r.offline.putIfAbsent(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void joined(PlayerJoinEvent e) {
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            if (room(e.getPlayer()) != null)
                                tell(e.getPlayer(), Language.component("chat.seat-held"));
                        },
                        60);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent e) {
        String c = e.getMessage().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (Set.of(
                        "/servermenu",
                        "/servermenu:servermenu",
                        "/menu",
                        "/skin",
                        "/skins",
                        "/skinsrestorer:skin",
                        "/skinsrestorer:skins")
                .contains(c)) suspendView(e.getPlayer());
    }

    void save() {
        if (!loaded || !getDataFolder().isDirectory()) return;
        Map<UUID, RoomStore.ReturnPoint> points = new LinkedHashMap<>();
        returns.forEach(
                (id, location) ->
                        points.put(
                                id,
                                new RoomStore.ReturnPoint(
                                        location.getWorld().getName(),
                                        location.getX(),
                                        location.getY(),
                                        location.getZ(),
                                        location.getYaw(),
                                        location.getPitch())));
        try {
            RoomStore.write(getDataFolder().toPath().resolve("rooms.json"), rooms.values(), points);
        } catch (IOException exception) {
            getLogger()
                    .warning(
                            "Could not save room records: " + exception.getClass().getSimpleName());
        }
    }

    void restore() {
        Path file = getDataFolder().toPath().resolve("rooms.json");
        if (!Files.exists(file)) return;
        try {
            RoomStore.Snapshot snapshot = RoomStore.read(file);
            snapshot.returns()
                    .forEach(
                            (id, point) -> {
                                World world = Bukkit.getWorld(point.world());
                                if (world != null)
                                    returns.put(
                                            id,
                                            new Location(
                                                    world,
                                                    point.x(),
                                                    point.y(),
                                                    point.z(),
                                                    point.yaw(),
                                                    point.pitch()));
                            });
            for (Room room : snapshot.rooms()) {
                for (Room.Seat seat : room.seats)
                    if (!seat.bot()) {
                        if (!coordinator.restoreReservation(seat.id(), room.kind))
                            throw new IllegalStateException(
                                    "Restored seat conflicts with another game");
                        room.offline.put(seat.id(), System.currentTimeMillis());
                    }
                rooms.put(room.id, room);
                arena.platform(room.table);
                if (room.phase == Room.Phase.LOBBY) {
                    room.board = room.newBoard();
                    arena.render(room);
                } else {
                    room.restoring = true;
                    start(room);
                }
            }
            getLogger().info("Restored " + rooms.size() + " board rooms.");
        } catch (Exception exception) {
            try {
                Files.copy(
                        file,
                        file.resolveSibling(
                                "rooms.unreadable-" + System.currentTimeMillis() + ".json"));
            } catch (IOException backupFailure) {
                exception.addSuppressed(backupFailure);
            }
            throw new IllegalStateException(
                    "Room records could not be restored; original file retained", exception);
        }
    }
}
