package dev.tabletop3d;

import com.google.gson.*;

import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.GameFactory;
import dev.tabletop3d.rules.GameOptions;
import dev.tabletop3d.rules.RuleViolation;

import java.util.*;

/** Main-thread room state. No Minecraft types: identity and replay are independently testable. */
final class Room {
    enum Phase {
        LOBBY,
        STARTING,
        PLAYING,
        PAUSED,
        FINISHED,
        ABORTED
    }

    record Seat(UUID id, String name, boolean bot) {}

    final UUID id;
    final String kind;
    final int capacity;
    long seed;
    final int table;
    final Map<String, String> options;
    UUID owner;
    boolean sideTray;
    UUID anchorWorld;
    double anchorX, anchorY, anchorZ;
    RoundActions.Undo undo;
    final List<Seat> seats = new ArrayList<>();
    final Set<UUID> ready = new HashSet<>();
    final Map<UUID, Long> offline = new HashMap<>();
    final JsonArray history = new JsonArray();
    Phase phase = Phase.LOBBY;
    long revision = 0;
    long changed = System.currentTimeMillis();
    long countdownChanged = -1;
    int countdownSecond = -1;
    boolean busy = false;
    boolean restoring = false;
    boolean completed = false;
    BoardGame board;
    String result = "";

    Room(UUID id, String kind, int capacity, long seed, int table) {
        this(id, kind, capacity, seed, table, Map.of());
    }

    Room(UUID id, String kind, int capacity, long seed, int table, Map<String, String> options) {
        this.id = id;
        this.kind = kind;
        this.capacity = capacity;
        this.seed = seed;
        this.table = table;
        this.options = GameOptions.validate(kind, options);
    }

    BoardGame newBoard() {
        return GameFactory.create(kind, capacity, seed, options);
    }

    void prepareSeats() {
        if (owner == null && !seats.isEmpty()) owner = seats.getFirst().id();
        if (!history.isEmpty() || !options.containsKey("first") || seats.size() != capacity) return;
        int host = seat(owner);
        if (host < 0) return;
        int target =
                options.get("first").equals("random")
                        ? new SplittableRandom(seed).nextInt(capacity)
                        : 1;
        Seat seat = seats.remove(host);
        seats.add(target, seat);
    }

    static Map<String, String> readOptions(JsonObject record) {
        boolean cards =
                record.has("kind") && record.get("kind").getAsString().equals("color-eight");
        int version = record.has("rulesVersion") ? record.get("rulesVersion").getAsInt() : 1;
        if (cards ? version != 2 : version != 1)
            throw new IllegalArgumentException(
                    "Unsupported room rules version; convert saved data before upgrading");
        if (!record.has("options")) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : record.getAsJsonObject("options").entrySet()) {
            if (!entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Room rule options must be strings");
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    int seat(UUID player) {
        for (int i = 0; i < seats.size(); i++) if (seats.get(i).id().equals(player)) return i;
        return -1;
    }

    boolean host(UUID player) {
        return player.equals(
                owner != null ? owner : seats.isEmpty() ? null : seats.getFirst().id());
    }

    void join(UUID player, String name) {
        if (seat(player) >= 0) return;
        if (phase != Phase.LOBBY || seats.size() >= capacity)
            throw new RuleViolation("error.this-room-is-full-or-has-started", "这个房间已经满员或开局");
        if (owner == null) owner = player;
        seats.add(new Seat(player, name, false));
        changed = System.currentTimeMillis();
        revision++;
    }

    void fillBots() {
        while (seats.size() < capacity) {
            int i = seats.size();
            seats.add(
                    new Seat(
                            UUID.nameUUIDFromBytes(
                                    (id + ":" + i)
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            "陪练" + (i + 1),
                            true));
        }
    }

    void requireAction(UUID player, long expected) {
        if (phase != Phase.PLAYING
                || busy
                || undo != null
                || revision != expected
                || seat(player) < 0)
            throw new RuleViolation(
                    "error.game-changed-or-an-undo-is-pending", "对局已更新或正在协商悔棋，请重新打开牌桌");
    }

    int turn() {
        return board != null ? board.currentPlayer() : -1;
    }

    String name() {
        return Tabletop3D.gameName(kind) + " · " + id.toString().substring(0, 6);
    }

    void event(int seat, JsonElement action) {
        JsonObject e = new JsonObject();
        e.addProperty("seat", seat);
        e.add("action", action.deepCopy());
        history.add(e);
    }
}
