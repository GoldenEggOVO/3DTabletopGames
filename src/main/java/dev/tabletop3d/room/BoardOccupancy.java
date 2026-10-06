package dev.tabletop3d.room;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Board-owned reservations, including seats restored for offline players. */
public final class BoardOccupancy {
    private final Map<UUID, String> seats = new HashMap<>();

    public synchronized boolean reserve(UUID player, String kind) {
        return seats.putIfAbsent(player, kind) == null;
    }

    public synchronized boolean restoreReservation(UUID player, String kind) {
        return reserve(player, kind);
    }

    public synchronized void release(UUID player, String kind) {
        seats.remove(player, kind);
    }

    public synchronized void close() {
        seats.clear();
    }
}
