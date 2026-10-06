package dev.tabletop3d.room;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public final class RoomHistory implements Iterable<JsonElement> {
    private static final int MAX_EVENTS = 4_000;
    private static final int MAX_ROOM_BYTES = 800_000;
    private static final int RESERVED_ROOM_BYTES = 4_096;
    private JsonArray events = new JsonArray();
    private long bytes = 2;

    public boolean allows(JsonElement action) {
        if (action == null || action.isJsonNull() || size() >= MAX_EVENTS) return false;

        long nextBytes = utf8Length(event(Integer.MIN_VALUE, action)) + (isEmpty() ? 0 : 1);
        return bytes + nextBytes + RESERVED_ROOM_BYTES < MAX_ROOM_BYTES;
    }

    void record(int seat, JsonElement action) {
        append(event(seat, action.deepCopy()));
    }

    void restore(JsonArray stored) {
        clear();
        for (JsonElement event : stored) append(event.deepCopy());
    }

    void clear() {
        events = new JsonArray();
        bytes = 2;
    }

    public int size() {
        return events.size();
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public JsonElement get(int index) {
        return events.get(index).deepCopy();
    }

    JsonArray snapshot() {
        return events.deepCopy();
    }

    @Override
    public Iterator<JsonElement> iterator() {
        return events.asList().stream().map(JsonElement::deepCopy).iterator();
    }

    private void append(JsonElement event) {
        bytes += utf8Length(event) + (isEmpty() ? 0 : 1);
        events.add(event);
    }

    private static JsonObject event(int seat, JsonElement action) {
        var event = new JsonObject();
        event.addProperty("seat", seat);
        event.add("action", action);
        return event;
    }

    private static int utf8Length(JsonElement value) {
        return value.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
