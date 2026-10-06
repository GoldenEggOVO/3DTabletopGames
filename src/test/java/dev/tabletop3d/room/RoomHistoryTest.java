package dev.tabletop3d.room;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RoomHistoryTest {
    @Test
    void multibyteAndEscapedActionsCountTheirEncodedJsonBytes() {
        var history = new RoomHistory();
        assertTrue(history.allows(new JsonPrimitive("\u4e2d".repeat(260_000))));
        assertFalse(history.allows(new JsonPrimitive("\u4e2d".repeat(270_000))));
        assertFalse(history.allows(new JsonPrimitive("\ud83c\udc04".repeat(200_000))));
        assertFalse(history.allows(new JsonPrimitive("\n".repeat(400_000))));
        history.record(0, new JsonPrimitive("\u4e2d".repeat(260_000)));
        assertFalse(history.allows(new JsonPrimitive("\u4e2d".repeat(10_000))));
    }

    @Test
    void limitsStayStrictAfterRecordingRestoringAndClearing() {
        var history = new RoomHistory();
        var move = new JsonPrimitive("draw");
        for (int index = 0; index < 3_999; index++) history.record(9, move);
        assertTrue(history.allows(move));
        history.record(9, move);
        assertFalse(history.allows(move));
        var restored = new RoomHistory();
        restored.restore(history.snapshot());
        assertFalse(restored.allows(move));
        restored.clear();
        assertTrue(restored.isEmpty());
        assertTrue(restored.allows(move));
        assertFalse(restored.allows(null));
        assertFalse(restored.allows(JsonNull.INSTANCE));
    }

    @Test
    void utf8BudgetMatchesCompleteJsonThroughoutGrowthAndRestore() {
        var history = new RoomHistory();
        for (int index = 0; index < 50; index++) {
            var action = new JsonPrimitive("\u4e2d\ud83c\udc04\n\"".repeat(index * 20));
            assertBoundary(history);
            history.record(index % 10, action);
            var restored = new RoomHistory();
            restored.restore(history.snapshot());
            assertEquals(history.snapshot(), restored.snapshot());
            assertBoundary(restored);
        }
        assertBoundary(history);
    }

    @Test
    void callersCannotChangeRecordedEventsOrTheirCachedSize() {
        var history = new RoomHistory();
        var action = new JsonObject();
        action.addProperty("type", "draw");
        history.record(0, action);
        var expected = history.snapshot();
        action.addProperty("type", "changed");
        history.get(0).getAsJsonObject().addProperty("seat", 99);
        history.iterator().next().getAsJsonObject().addProperty("seat", 98);
        history.snapshot().get(0).getAsJsonObject().addProperty("seat", 97);
        var input = expected.deepCopy();
        history.restore(input);
        input.get(0).getAsJsonObject().addProperty("seat", 96);
        assertEquals(expected, history.snapshot());
        assertBoundary(history);
    }

    private static void assertBoundary(RoomHistory history) {
        var envelope = new JsonObject();
        envelope.addProperty("seat", Integer.MIN_VALUE);
        envelope.addProperty("action", "");
        int fixed = bytes(history.snapshot()) + bytes(envelope)
                + (history.isEmpty() ? 0 : 1) + 4_096;
        assertTrue(history.allows(new JsonPrimitive("a".repeat(800_000 - fixed - 1))));
        assertFalse(history.allows(new JsonPrimitive("a".repeat(800_000 - fixed))));
    }

    private static int bytes(JsonElement value) {
        return value.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
