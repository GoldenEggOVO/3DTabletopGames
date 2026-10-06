package dev.tabletop3d.room;

import dev.tabletop3d.rules.RuleViolation;

import java.util.*;

/** Readiness and state transitions for a fresh round. */
public final class RoundActions {
    public static boolean rematchReady(Room r, UUID player) {
        if (r.phase != Room.Phase.FINISHED || r.busy || r.seat(player) < 0)
            throw new RuleViolation(
                    "error.finish-this-game-before-starting-a-rematch", "Finish this game before starting a rematch");
        r.ready.add(player);
        r.revision++;
        r.changed = System.currentTimeMillis();
        return r.seats.stream().allMatch(s -> s.bot() || r.ready.contains(s.id()));
    }

    public static void fresh(Room r, long seed) {
        if (r.phase != Room.Phase.FINISHED
                || r.seats.stream().anyMatch(s -> !s.bot() && !r.ready.contains(s.id())))
            throw new RuleViolation("error.waiting-for-tablemates-to-ready-for-a", "Waiting for tablemates to ready for a rematch");
        r.history.clear();
        r.seed = seed;
        r.board = null;
        r.result = "";
        r.completed = false;
        r.restoring = false;
        r.ready.clear();
        r.phase = Room.Phase.LOBBY;
        r.revision++;
        r.changed = System.currentTimeMillis();
    }
}
