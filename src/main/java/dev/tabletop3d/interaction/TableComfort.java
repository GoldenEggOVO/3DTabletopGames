package dev.tabletop3d.interaction;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.render.TableGeometry;
import dev.tabletop3d.room.Room;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;

/** Owns table collision and the temporary, seat-relative Mahjong focus position. */
public final class TableComfort implements Listener, AutoCloseable {
    private final Tabletop3D plugin;
    private final Map<UUID, Boolean> previousCollision = new HashMap<>();

    private record Focus(
            Room room,
            int seat,
            Location original,
            Location anchor,
            boolean gravity,
            boolean invisible,
            GameMode mode,
            boolean allowFlight,
            boolean flying,
            ArmorStand camera) {}

    private final Map<UUID, Focus> focusedPlayers = new HashMap<>();
    private final Map<UUID, Location> internalTeleports = new HashMap<>();
    private final Set<UUID> sprintHeld = new HashSet<>();
    private final Set<UUID> closingMenus = new HashSet<>();
    private final Set<UUID> internalModeChanges = new HashSet<>();
    private static final NamespacedKey FOCUS_RETURN_KEY =
            new NamespacedKey("3dtabletop", "focus-return");

    public TableComfort(Tabletop3D plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            recover(player);
        }
    }

    private boolean active(Player player) {
        Room room = plugin.room(player);
        return room != null
                && plugin.allowed(player)
                && plugin.arena.atTableWorld(player, room)
                && player.getLocation().distanceSquared(plugin.arena.center(room.table)) <= 36;
    }

    public void sync() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sync(player);
        }
    }

    private void sync(Player player) {
        Focus current = focusedPlayers.get(player.getUniqueId());
        if (current != null
                && (!sprintHeld.contains(player.getUniqueId())
                        || !valid(player, current)
                        || !atAnchor(player, current))) {
            finish(player, atAnchor(player, current));
        }
        if (focused(player)) {
            hideFocusedPlayer(player);
        }
        if (!active(player)) {
            restore(player);
            return;
        }
        if (!previousCollision.containsKey(player.getUniqueId())) {
            boolean collidable = player.isCollidable();
            previousCollision.put(player.getUniqueId(), collidable);
            if (collidable) {
                player.setCollidable(false);
            }
        }
    }

    public boolean focused(Player player) {
        return focusedPlayers.containsKey(player.getUniqueId());
    }

    public void release(Player player) {
        finish(player, true);
    }

    public void menuClosed(Player player) {
        if (sprintHeld.contains(player.getUniqueId())) {
            closingMenus.add(player.getUniqueId());
        }
    }

    private boolean valid(Player player, Focus state) {
        return !player.isDead()
                && !player.isInsideVehicle()
                && player.getGameMode() == GameMode.SPECTATOR
                && state.camera().isValid()
                && player.getSpectatorTarget() == state.camera()
                && plugin.room(player) == state.room()
                && state.room().seat(player.getUniqueId()) == state.seat()
                && active(player);
    }

    private static boolean atAnchor(Player player, Focus state) {
        Location now = player.getLocation();
        return now.getWorld().equals(state.anchor().getWorld())
                && now.distanceSquared(state.anchor()) < .01;
    }

    private void begin(Player player) {
        Room room = plugin.room(player);
        if (room == null
                || !room.kind.equals("mahjong")
                || room.seat(player.getUniqueId()) < 0
                || !active(player)
                || player.isDead()
                || player.isInsideVehicle()
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        int seat = room.seat(player.getUniqueId());
        double angle = 2 * Math.PI * seat / room.capacity;
        Location anchor =
                plugin.arena
                        .center(room.table)
                        .clone()
                        .add(Math.sin(angle) * .65, .88 + 1.27, Math.cos(angle) * .65);
        // A marker's eye height is zero: preserve the previous close view's eye position.
        anchor.setDirection(
                new Vector(
                        -Math.sin(angle) * .65,
                        TableGeometry.SURFACE + .05 - .88 - 1.27,
                        -Math.cos(angle) * .65));
        if (!clearCamera(anchor)) {
            return;
        }
        ArmorStand camera =
                anchor.getWorld()
                        .spawn(
                                anchor,
                                ArmorStand.class,
                                cameraEntity -> {
                                    cameraEntity.setVisibleByDefault(false);
                                    cameraEntity.setPersistent(false);
                                    cameraEntity.setGravity(false);
                                    cameraEntity.setInvulnerable(true);
                                    cameraEntity.setMarker(true);
                                    cameraEntity.setVisible(false);
                                    cameraEntity.setSilent(true);
                                    cameraEntity.setRotation(anchor.getYaw(), anchor.getPitch());
                                });
        Focus state =
                new Focus(
                        room,
                        seat,
                        player.getLocation().clone(),
                        anchor,
                        player.hasGravity(),
                        player.isInvisible(),
                        player.getGameMode(),
                        player.getAllowFlight(),
                        player.isFlying(),
                        camera);
        remember(player, state);
        sync(player);
        focusedPlayers.put(player.getUniqueId(), state);
        player.setGravity(false);
        player.setInvisible(true);
        player.setVelocity(new Vector());
        player.setFallDistance(0);
        hideFocusedPlayer(player);
        try {
            changeMode(player, GameMode.SPECTATOR);
            if (player.getGameMode() != GameMode.SPECTATOR) {
                finish(player, false);
                return;
            }
            if (!teleport(player, anchor) || !atAnchor(player, state)) {
                finish(player, false);
                return;
            }
            player.showEntity(plugin, camera);
            internalTeleports.put(player.getUniqueId(), anchor);
            try {
                player.setSpectatorTarget(camera);
            } finally {
                internalTeleports.remove(player.getUniqueId());
            }
            if (player.getSpectatorTarget() != camera) {
                finish(player, true);
            }
        } catch (RuntimeException exception) {
            finish(player, true);
            throw exception;
        }
    }

    private static boolean clearCamera(Location target) {
        if (!target.getBlock().isPassable()) {
            return false;
        }
        for (double x : new double[] {-.3, .3}) {
            for (double z : new double[] {-.3, .3}) {
                if (!target.clone().add(x, 0, z).getBlock().isPassable()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean clear(Location target) {
        for (double x : new double[] {-.3, .3}) {
            for (double z : new double[] {-.3, .3}) {
                for (double y : new double[] {0, .9, 1.8}) {
                    if (!target.clone().add(x, y, z).getBlock().isPassable()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean teleport(Player player, Location target) {
        internalTeleports.put(player.getUniqueId(), target);
        try {
            return player.teleport(target);
        } finally {
            internalTeleports.remove(player.getUniqueId());
        }
    }

    private void finish(Player player, boolean returnToSeat) {
        finish(player, returnToSeat, true, returnToSeat && !player.isDead());
    }

    private void finish(Player player, boolean returnToSeat, boolean restoreMode) {
        finish(player, returnToSeat, restoreMode, false);
    }

    private void finish(Player player, boolean returnToSeat, boolean restoreMode, boolean retry) {
        Focus state = focusedPlayers.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(null);
            if (restoreMode) {
                changeMode(player, state.mode());
                if (player.getGameMode() == GameMode.SPECTATOR && retry) {
                    focusedPlayers.put(player.getUniqueId(), state);
                    internalTeleports.put(player.getUniqueId(), state.anchor());
                    try {
                        player.setSpectatorTarget(state.camera());
                    } finally {
                        internalTeleports.remove(player.getUniqueId());
                    }
                    return;
                }
                if (player.getGameMode() == state.mode()) {
                    player.setAllowFlight(state.allowFlight());
                    player.setFlying(state.flying());
                }
            }
        }
        state.camera().remove();
        player.setGravity(state.gravity());
        player.setInvisible(state.invisible());
        player.setVelocity(new Vector());
        player.setFallDistance(0);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                viewer.showPlayer(plugin, player);
            }
        }
        if (returnToSeat
                && !player.isDead()
                && atAnchor(player, state)
                && clear(state.original())) {
            teleport(player, state.original());
        }
        if (!restoreMode || player.getGameMode() != GameMode.SPECTATOR) {
            player.getPersistentDataContainer().remove(FOCUS_RETURN_KEY);
        }
    }

    private void remember(Player player, Focus state) {
        var data = new com.google.gson.JsonObject();
        Location originalLocation = state.original();
        data.addProperty("mode", state.mode().name());
        data.addProperty("gravity", state.gravity());
        data.addProperty("invisible", state.invisible());
        data.addProperty("allowFlight", state.allowFlight());
        data.addProperty("flying", state.flying());
        data.addProperty(
                "collidable",
                previousCollision.getOrDefault(player.getUniqueId(), player.isCollidable()));
        data.addProperty("world", originalLocation.getWorld().getUID().toString());
        data.addProperty("x", originalLocation.getX());
        data.addProperty("y", originalLocation.getY());
        data.addProperty("z", originalLocation.getZ());
        data.addProperty("yaw", originalLocation.getYaw());
        data.addProperty("pitch", originalLocation.getPitch());
        player.getPersistentDataContainer()
                .set(FOCUS_RETURN_KEY, PersistentDataType.STRING, data.toString());
    }

    private void recover(Player player) {
        if (focused(player)) {
            return;
        }
        String saved =
                player.getPersistentDataContainer()
                        .get(FOCUS_RETURN_KEY, PersistentDataType.STRING);
        if (saved == null) {
            return;
        }
        try {
            var data = com.google.gson.JsonParser.parseString(saved).getAsJsonObject();
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGravity(data.get("gravity").getAsBoolean());
                player.setInvisible(data.get("invisible").getAsBoolean());
                player.setCollidable(data.get("collidable").getAsBoolean());
                player.getPersistentDataContainer().remove(FOCUS_RETURN_KEY);
                return;
            }
            GameMode mode = GameMode.valueOf(data.get("mode").getAsString());
            var world = Bukkit.getWorld(UUID.fromString(data.get("world").getAsString()));
            Location back =
                    new Location(
                            world,
                            data.get("x").getAsDouble(),
                            data.get("y").getAsDouble(),
                            data.get("z").getAsDouble(),
                            data.get("yaw").getAsFloat(),
                            data.get("pitch").getAsFloat());
            player.setSpectatorTarget(null);
            changeMode(player, mode);
            if (player.getGameMode() != mode) {
                return;
            }
            player.setAllowFlight(data.get("allowFlight").getAsBoolean());
            player.setFlying(data.get("flying").getAsBoolean());
            player.setGravity(data.get("gravity").getAsBoolean());
            player.setInvisible(data.get("invisible").getAsBoolean());
            player.setCollidable(data.get("collidable").getAsBoolean());
            if (world != null && clear(back)) {
                teleport(player, back);
            }
            player.getPersistentDataContainer().remove(FOCUS_RETURN_KEY);
        } catch (IllegalArgumentException
                | IllegalStateException
                | NullPointerException exception) {
            plugin.getLogger()
                    .warning(
                            "Could not restore saved Mahjong camera state for "
                                    + player.getUniqueId());
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        recover(event.getPlayer());
    }

    private void changeMode(Player player, GameMode mode) {
        internalModeChanges.add(player.getUniqueId());
        try {
            player.setGameMode(mode);
        } finally {
            internalModeChanges.remove(player.getUniqueId());
        }
    }

    private void hideFocusedPlayer(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                viewer.hidePlayer(plugin, player);
            }
        }
    }

    @EventHandler
    public void input(PlayerInputEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        // Dialogs reset client key bindings; keep the sprint latch until the menu closes.
        if (plugin.menus != null && plugin.menus.active(player)) {
            release(player);
            return;
        }
        if (event.getInput().isSprint()) {
            if (sprintHeld.add(id)) {
                begin(player);
            }
        } else {
            sprintHeld.remove(id);
            release(player);
        }
    }

    @EventHandler
    public void clientTick(io.papermc.paper.event.packet.ClientTickEndEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!closingMenus.remove(id) || plugin.menus != null && plugin.menus.active(player)) {
            return;
        }
        if (!player.getCurrentInput().isSprint()) {
            sprintHeld.remove(id);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }
        Player player = event.getPlayer();
        Focus state = focusedPlayers.get(player.getUniqueId());
        if (state == null || event.getTo() == null) {
            return;
        }
        if (!valid(player, state)) {
            release(player);
            event.setTo(player.getLocation());
            return;
        }
        // Cancellation uses Paper's internal correction; setTo would trigger a plugin teleport and
        // end focus.
        event.setCancelled(true);
        player.setVelocity(new Vector());
        player.setFallDistance(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void stopCamera(
            com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent event) {
        if (focused(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void switchCamera(
            com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent event) {
        Focus state = focusedPlayers.get(event.getPlayer().getUniqueId());
        if (state != null && !event.getNewSpectatorTarget().equals(state.camera())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void gameMode(PlayerGameModeChangeEvent event) {
        if (internalModeChanges.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        // Another plugin owns this mode change; clean up without restoring our previous game mode.
        finish(event.getPlayer(), false, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spectatorTeleport(PlayerTeleportEvent event) {
        if (!focused(event.getPlayer())
                || event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        Location expected = internalTeleports.get(event.getPlayer().getUniqueId());
        if (expected == null || !expected.equals(event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void externalTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location expected = internalTeleports.get(player.getUniqueId());
        if (expected != null && expected.equals(event.getTo())) {
            return;
        }
        finish(player, false);
        restore(player);
    }

    private void restore(Player player) {
        Boolean value = previousCollision.remove(player.getUniqueId());
        if (value != null) {
            player.setCollidable(value);
        }
    }

    @EventHandler
    public void hunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && active(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void world(PlayerChangedWorldEvent event) {
        finish(event.getPlayer(), false);
        restore(event.getPlayer());
        sync(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        finish(event.getPlayer(), true, true, false);
        restore(event.getPlayer());
        sprintHeld.remove(event.getPlayer().getUniqueId());
        closingMenus.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        finish(event.getEntity(), false);
        restore(event.getEntity());
        sprintHeld.remove(event.getEntity().getUniqueId());
    }

    public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            finish(player, true, true, false);
            restore(player);
        }
        focusedPlayers.clear();
        sprintHeld.clear();
        closingMenus.clear();
        previousCollision.clear();
    }
}
