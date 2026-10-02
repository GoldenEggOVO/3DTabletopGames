package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class RoomReplayVerifierTest {
    @Test
    void colorEightHistoryRequiresTheNewRulesVersion() {
        var room =
                JsonParser.parseString(
                                """
                                {"id":"cards","kind":"color-eight","capacity":5,"seed":1,"rulesVersion":1,
                                "options":{},"history":[{"seat":0,"action":"pass"}]}
                                """)
                        .getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> Room.readOptions(room));
        room.addProperty("rulesVersion", 2);
        assertTrue(Room.readOptions(room).isEmpty());
        var source = new com.google.gson.JsonObject();
        var array = new com.google.gson.JsonArray();
        array.add(room);
        source.add("rooms", array);
        assertDoesNotThrow(() -> RoomReplayVerifier.verify(source));
        var options =
                dev.tabletop3d.rules.GameOptions.validate("color-eight", Room.readOptions(room));
        assertTrue(options.isEmpty());
    }

    @Test
    void acceptsAValidConnectFourHistory() {
        var source =
                JsonParser.parseString(
                                """
                                {"rooms":[{"id":"a","kind":"connectfour","capacity":2,"seed":1,
                                "history":[{"seat":0,"action":"drop:3"},{"seat":1,"action":"drop:4"}]}]}
                                """)
                        .getAsJsonObject();
        assertDoesNotThrow(() -> RoomReplayVerifier.verify(source));
    }

    @Test
    void identifiesTheFirstUnreplayableLegacyMove() {
        var source =
                JsonParser.parseString(
                                """
                                {"rooms":[{"id":"legacy-flight","kind":"aeroplane","capacity":4,"seed":1003,
                                "history":[{"seat":0,"action":"roll"},{"seat":0,"action":"move:0:to0"},
                                {"seat":0,"action":"roll"},{"seat":0,"action":"move:0:sk7"}]}]}
                                """)
                        .getAsJsonObject();
        var error =
                assertThrows(
                        IllegalArgumentException.class, () -> RoomReplayVerifier.verify(source));
        assertTrue(error.getMessage().contains("legacy-flight"));
        assertTrue(error.getMessage().contains("event 3"));
    }
}
