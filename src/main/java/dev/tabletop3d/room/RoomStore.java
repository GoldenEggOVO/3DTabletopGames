package dev.tabletop3d.room;

import com.google.gson.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class RoomStore {
    private static final int SCHEMA = 1;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public record ReturnPoint(String world, double x, double y, double z, float yaw, float pitch) {}

    public record Snapshot(List<Room> rooms, Map<UUID, ReturnPoint> returns) {}

    public static void write(Path file, Collection<Room> rooms, Map<UUID, ReturnPoint> returns)
            throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        JsonArray records = new JsonArray();
        for (Room room : rooms) if (room.phase != Room.Phase.ABORTED) records.add(record(room));
        root.add("rooms", records);
        JsonObject points = new JsonObject();
        returns.forEach((id, point) -> points.add(id.toString(), JSON.toJsonTree(point)));
        root.add("returns", points);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Snapshot read(Path file) throws IOException {
        JsonObject root =
                JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
        if (root.has("schema") && root.get("schema").getAsInt() != SCHEMA)
            throw new IllegalArgumentException("Unsupported room file schema");
        List<Room> rooms = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("rooms"))
            rooms.add(room(element.getAsJsonObject()));
        Map<UUID, ReturnPoint> returns = new LinkedHashMap<>();
        for (var entry : root.getAsJsonObject("returns").entrySet())
            returns.put(
                    UUID.fromString(entry.getKey()),
                    JSON.fromJson(entry.getValue(), ReturnPoint.class));
        return new Snapshot(List.copyOf(rooms), Map.copyOf(returns));
    }

    private static JsonObject record(Room room) {
        JsonObject record = new JsonObject();
        record.addProperty("id", room.id.toString());
        record.addProperty("kind", room.kind);
        record.addProperty("capacity", room.capacity);
        record.addProperty("seed", room.seed);
        record.addProperty("table", room.table);
        record.addProperty("rulesVersion", room.kind.equals("color-eight") ? 2 : 1);
        record.add("options", JSON.toJsonTree(room.options));
        if (room.owner != null) record.addProperty("owner", room.owner.toString());
        record.addProperty("sideTray", room.sideTray);
        if (room.anchorWorld != null) {
            record.addProperty("anchorWorld", room.anchorWorld.toString());
            record.addProperty("anchorX", room.anchorX);
            record.addProperty("anchorY", room.anchorY);
            record.addProperty("anchorZ", room.anchorZ);
        }
        record.addProperty("phase", room.phase.name());
        record.addProperty("completed", room.completed);
        record.addProperty("result", room.result);
        record.addProperty("revision", room.revision);
        record.add("seats", JSON.toJsonTree(room.seats));
        record.add("history", room.history.snapshot());
        return record;
    }

    private static Room room(JsonObject record) {
        Room room =
                new Room(
                        UUID.fromString(record.get("id").getAsString()),
                        record.get("kind").getAsString(),
                        record.get("capacity").getAsInt(),
                        record.get("seed").getAsLong(),
                        record.get("table").getAsInt(),
                        Room.readOptions(record));
        room.owner =
                record.has("owner") ? UUID.fromString(record.get("owner").getAsString()) : null;
        room.sideTray = record.has("sideTray") && record.get("sideTray").getAsBoolean();
        if (record.has("anchorWorld")) {
            room.anchorWorld = UUID.fromString(record.get("anchorWorld").getAsString());
            room.anchorX = record.get("anchorX").getAsDouble();
            room.anchorY = record.get("anchorY").getAsDouble();
            room.anchorZ = record.get("anchorZ").getAsDouble();
        }
        for (JsonElement seat : record.getAsJsonArray("seats"))
            room.seats.add(JSON.fromJson(seat, Room.Seat.class));
        if (room.owner == null && !room.seats.isEmpty()) room.owner = room.seats.getFirst().id();
        room.history.restore(record.getAsJsonArray("history"));
        room.revision = record.get("revision").getAsLong();
        room.phase = Room.Phase.valueOf(record.get("phase").getAsString());
        room.completed = record.has("completed") && record.get("completed").getAsBoolean();
        room.result = record.has("result") ? record.get("result").getAsString() : "";
        return room;
    }

    private RoomStore() {}
}
