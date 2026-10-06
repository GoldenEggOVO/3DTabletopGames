package dev.tabletop3d.room;

import com.google.gson.*;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.GameFactory;
import dev.tabletop3d.rules.GameOptions;
import dev.tabletop3d.rules.RuleViolation;

import java.util.*;

/** Main-thread room state. No Minecraft types: identity and replay are independently testable. */
public final class Room {
    public enum Phase {
        LOBBY,
        STARTING,
        PLAYING,
        PAUSED,
        FINISHED,
        ABORTED
    }

    public record Seat(UUID id, String name, boolean bot) {}

    public final UUID id;
    public final String kind;
    public final int capacity;
    public long seed;
    public final int table;
    public final Map<String, String> options;
    public UUID owner;
    public boolean sideTray;
    public UUID anchorWorld;
    public double anchorX, anchorY, anchorZ;
    public final List<Seat> seats = new ArrayList<>();
    public final Set<UUID> ready = new HashSet<>();
    public final Map<UUID, Long> offline = new HashMap<>();
    public final RoomHistory history = new RoomHistory();
    public Phase phase = Phase.LOBBY;
    public long revision = 0;
    public long changed = System.currentTimeMillis();
    public long countdownChanged = -1;
    public int countdownSecond = -1;
    public boolean busy = false;
    public boolean restoring = false;
    public boolean completed = false;
    public BoardGame board;
    public String result = "";

    public Room(UUID id, String kind, int capacity, long seed, int table) {
        this(id, kind, capacity, seed, table, Map.of());
    }

    public Room(UUID id, String kind, int capacity, long seed, int table, Map<String, String> options) {
        this.id = id;
        this.kind = kind;
        this.capacity = capacity;
        this.seed = seed;
        this.table = table;
        this.options = GameOptions.validate(kind, options);
    }

    public BoardGame newBoard() {
        return GameFactory.create(kind, capacity, seed, options);
    }

    public void prepareSeats() {
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

    public static Map<String, String> readOptions(JsonObject record) {
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

    public int seat(UUID player) {
        for (int i = 0; i < seats.size(); i++) if (seats.get(i).id().equals(player)) return i;
        return -1;
    }

    public boolean host(UUID player) {
        return player.equals(
                owner != null ? owner : seats.isEmpty() ? null : seats.getFirst().id());
    }

    public void join(UUID player, String name) {
        if (seat(player) >= 0) return;
        if (phase != Phase.LOBBY || seats.size() >= capacity)
            throw new RuleViolation("error.this-room-is-full-or-has-started", "This room is full or has started");
        if (owner == null) owner = player;
        seats.add(new Seat(player, name, false));
        changed = System.currentTimeMillis();
        revision++;
    }

    public void fillBots() {
        while (seats.size() < capacity) {
            int i = seats.size();
            seats.add(
                    new Seat(
                            UUID.nameUUIDFromBytes(
                                    (id + ":" + i)
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            "Bot " + (i + 1),
                            true));
        }
    }

    public void requireAction(UUID player, long expected) {
        if (phase != Phase.PLAYING
                || busy
                || revision != expected
                || seat(player) < 0)
            throw new RuleViolation(
                    "error.game-changed", "Game changed; reopen the table");
    }

    public int turn() {
        return board != null ? board.currentPlayer() : -1;
    }

    String name() {
        return Tabletop3D.gameName(kind) + " · " + id.toString().substring(0, 6);
    }

    public void event(int seat, JsonElement action) {
        history.record(seat, action);
    }
}
